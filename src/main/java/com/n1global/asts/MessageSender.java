package com.n1global.asts;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.util.BufUtils;

public class MessageSender<T extends ByteMessage> {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Queue<T> sendQueue = new LinkedBlockingQueue<>();

    private EndpointContext<T> ctx;

    private boolean canWriteNow = true;

    MessageSender(EndpointContext<T> ctx) {
        this.ctx = ctx;
    }

    public void send(T msg) {
        if (ctx.getSelectionKey().isValid()) {//maybe invalid if peer close connection before onReceive (f.e. in onConnect)
            sendQueue.add(msg);

            if (canWriteNow) {
                sendMessages();
            } else {
                ctx.getSelectionKey().interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }
    }

    void sendMessages() {
        _sendMessages();

        if (ctx.getSelectionKey().isValid()) {//maybe invalid if we have write exception in _sendMessages()
            checkQueueState();
        }
    }

    private void _sendMessages() {
        boolean socketIsFull = false;
        
        ByteBuffer appBuf = ctx.getOutgoingBuf();
        ByteBuffer sndBuf = ctx.getEncryptedOutgoingBuf();
        
        try {
            while (!socketIsFull) {
                if (sndBuf.position() != 0) {
                    socketIsFull = writeToSocket(sndBuf);
                } else if (!sendQueue.isEmpty()) {
                    fillUpAppBuf(appBuf);
                    
                    encode(appBuf, sndBuf);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            if (e instanceof EOFException) {
                e = null; //normally disconnect
            } else {
                logger.error("", e);
            }
            
            ctx.getEventHandler().closeConnection(e);
        }
    }

    private void fillUpAppBuf(ByteBuffer appBuf) throws SSLException, EOFException {
        T msg;
        
        while ((msg = sendQueue.peek()) != null) {
            if (ctx.getSslEngine().getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                if (ctx.getProtocol().putNextMsg(appBuf, msg)) {
                    sendQueue.remove();
                } else {
                    break;
                }
            } else {
                sendQueue.remove();//удаляем маркер-сообщение
            }
        }
            
        appBuf.flip();//теперь читаем
    }

    @SuppressWarnings({ "unchecked", "incomplete-switch" })
    private void encode(ByteBuffer appBuf, ByteBuffer sndBuf) throws SSLException, EOFException {
        boolean wrapSuccess = false;
        
        while (!wrapSuccess) {  
            SSLEngineResult res = ctx.getSslEngine().wrap(appBuf, sndBuf);
            
            if (res.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                sendQueue.add((T) new ByteMessage());//страхуемся на случай, если предыдущий wrap записал в sndBuf не всё, что нужно для отправки
                //при этом, если это "не всё" уйдет в сокет полностью, то цикл завершится и не будет возможности вызвать wrap еще раз
            }
            
            switch (res.getStatus()) {
                case OK:
                    appBuf.compact();//теперь снова готовим к записи
                    
                    wrapSuccess = true;
                
                    break;
                case CLOSED: throw new EOFException();
                case BUFFER_OVERFLOW:
                    sndBuf.flip();//т.к. далее планируется только чтение из данного буфера
                    ByteBuffer b = ByteBuffer.allocateDirect(ctx.getSslEngine().getSession().getPacketBufferSize() + sndBuf.limit()).put(sndBuf);
                    BufUtils.destroyDirect(b);
                    sndBuf = b;
                    ctx.setEncryptedOutgoingBuf(sndBuf);
                    
                    break;
            }
        }
    }

    private boolean writeToSocket(ByteBuffer sndBuf) throws IOException {
        sndBuf.flip();//готовимся читать

        boolean socketIsFull = false;
        
        while (sndBuf.hasRemaining()) {
            if (((SocketChannel) ctx.getSelectionKey().channel()).write(sndBuf) == 0) {
                socketIsFull = true;
                
                break;
            }
        }
        
        sndBuf.compact();//снова готовим к записи
        
        return socketIsFull;
    }

    private void checkQueueState() {
        if (!ctx.getEncryptedOutgoingBuf().hasRemaining()) {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ);

            canWriteNow = true;
        } else {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            canWriteNow = false;
        }
    }
}
