package com.n1global.asts;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLEngineResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.ByteMessage;

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

        if (ctx.getSelectionKey().isValid()) {//maybe invalid if we close connection in onSend or we have write exception in _sendMessages()
            checkQueueState();
        }
    }

    @SuppressWarnings("incomplete-switch")
    private void _sendMessages() {
        T msg;
        
        boolean complete = false;
        
        try {
            while (!complete) {
                while ((msg = sendQueue.peek()) != null) {
                    if (ctx.getProtocol().putNextMsg(msg)) {
                        sendQueue.remove();    
                    } else {
                        break;
                    }
                }
            
                ByteBuffer appBuf = ctx.getProtocol().getOutgoingBuf();
                ByteBuffer sndBuf = ctx.getProtocol().getEncryptedOutgoingBuf();
    
                appBuf.flip();//теперь читаем
                
                boolean wrapSuccess = false;
                
                while (!wrapSuccess) {            
                    SSLEngineResult res = ctx.getSslEngine().wrap(appBuf, sndBuf);
                
                    switch (res.getStatus()) {
                        case OK:
                            appBuf.compact();//теперь снова готовим к записи
                            sndBuf.flip();//готовимся читать
                            
                            while(sndBuf.hasRemaining()) {
                                if (((SocketChannel) ctx.getSelectionKey().channel()).write(sndBuf) == 0) {
                                    complete = true;
                                    
                                    break;
                                }
                            }
                            
                            sndBuf.compact();//снова готовим к записи
                            wrapSuccess = true;
                        
                            break;
                        case CLOSED: throw new EOFException();
                        case BUFFER_OVERFLOW:
                            sndBuf.flip();//т.к. далее планируется только чтение из данного буфера
                            ByteBuffer b = ByteBuffer.allocate(ctx.getSslEngine().getSession().getPacketBufferSize() + sndBuf.limit()).put(sndBuf);
                            sndBuf = b;
                            ctx.getProtocol().setEncryptedOutgoingBuf(sndBuf);
                            
                            break;
                    }
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

    private void checkQueueState() {
        if (sendQueue.isEmpty()) {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ);

            canWriteNow = true;
        } else {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            canWriteNow = false;
        }
    }
}
