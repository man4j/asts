package com.n1global.asts;

import gnu.trove.list.linked.TLinkedList;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;
import com.n1global.asts.util.EndpointContextContainer;

public class MainLoop {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MainLoopConfig config;

    private Selector selector;
    
    private SSLEngine sslEngine;

    private TLinkedList<EndpointContextContainer<ByteMessage>> idleEndpoints  = new TLinkedList<>();
    private TLinkedList<EndpointContextContainer<ByteMessage>> readEndpoints  = new TLinkedList<>();

    public MainLoop(MainLoopConfig config) {
        try {
            this.config = config;

            selector = Selector.open();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MainLoop() {
        this(new MainLoopConfig.Builder().build());
    }

    public <T extends ByteMessage> void addServer(EndpointConfig<T> config) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) ServerSocketChannel.open()
                                                                                               .bind(new InetSocketAddress(config.getLocalAddr(), config.getLocalPort()), 50)
                                                                                               .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                                                                                               .setOption(StandardSocketOptions.SO_RCVBUF, config.getSocketRecvBufSize())
                                                                                               .configureBlocking(false)                                                                                               ;

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends ByteMessage> void addClient(EndpointConfig<T> config, SocketAddress remote) {
        try {
            SocketChannel socketChannel = (SocketChannel) SocketChannel.open().configureBlocking(false);

            socketChannel.bind(new InetSocketAddress(config.getLocalAddr(), config.getLocalPort()))
                         .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                         .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                         .setOption(StandardSocketOptions.TCP_NODELAY, true)
                         .setOption(StandardSocketOptions.SO_RCVBUF, config.getSocketRecvBufSize())
                         .setOption(StandardSocketOptions.SO_SNDBUF, config.getSocketSendBufSize())
                         .register(selector, SelectionKey.OP_CONNECT, config);

            socketChannel.connect(remote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends ByteMessage> void register(SelectableChannel channel, EndpointConfig<T> config) throws InstantiationException, IllegalAccessException {
        EndpointContext<T> ctx = new EndpointContext<>();

        ctx.setEventHandler(config.getHandlerClass().newInstance());
        
        AbstractFrameProtocol<T> prot = config.getProtocolClass().newInstance();
        
        ctx.initBuffers(config.getMaxMsgSize());
        ctx.setProtocol(prot);
        ctx.setSelectionKey(channel.keyFor(selector));
        ctx.setSender((MessageSender<T>) new MessageSender<>((EndpointContext<ByteMessage>) ctx));

        channel.keyFor(selector).attach(ctx);

        ctx.getEventHandler().onConnect();

        ctx.setIdleNode(new EndpointContextContainer<T>(ctx));
        ctx.setReadNode(new EndpointContextContainer<T>(ctx));
        
        idleEndpoints.add((EndpointContextContainer<ByteMessage>) ctx.getIdleNode());
    }

    public void loop() {
        long lastTimeoutsCheck = System.currentTimeMillis();
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                selector.select(1000);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();

                if (key.isAcceptable()) processAccept(key);
                if (key.isConnectable()) processConnect(key);
                if (key.isReadable()) processRead(key);
                if (key.isValid() && key.isWritable()) processWrite(key);

                iter.remove();
            }

            if (config.getSelectHandler() != null) {
                try {
                    config.getSelectHandler().onSelect(selector);
                } catch (Exception e) {
                    logger.error("", e);
                }
            }

            if (System.currentTimeMillis() - lastTimeoutsCheck > 1000) {
                checkRead();
                checkIdle();

                lastTimeoutsCheck = System.currentTimeMillis();
            }
        }

        try {
            selector.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processConnect(SelectionKey key) {
        try {
            if (((SocketChannel) key.channel()).finishConnect()) {
                key.interestOps(SelectionKey.OP_READ);

                register(key.channel(), (EndpointConfig<ByteMessage>) key.attachment());
            }
        } catch (Exception e) {//if connection unsuccessfully key.cancel() invoked automatically
            logger.error("", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processAccept(SelectionKey key) {
        try {
            SocketChannel ch = ((ServerSocketChannel) key.channel()).accept();

            EndpointConfig<ByteMessage> endpointConfig = (EndpointConfig<ByteMessage>) key.attachment();

            ch.setOption(StandardSocketOptions.SO_KEEPALIVE, true)
              .setOption(StandardSocketOptions.TCP_NODELAY, true)
              .setOption(StandardSocketOptions.SO_SNDBUF, endpointConfig.getSocketSendBufSize());

            ch.configureBlocking(false).register(key.selector(), SelectionKey.OP_READ);

            register(ch, endpointConfig);
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    private void processRead(SelectionKey key) {
        bytes2Messages(key);

        updateTimeouts(key);
    }

    @SuppressWarnings("unchecked")
    private void bytes2Messages(SelectionKey key) {
        SocketChannel ch = (SocketChannel) key.channel();

        EndpointContext<ByteMessage> ctx = (EndpointContext<ByteMessage>) key.attachment();

        List<ByteMessage> messages = new ArrayList<>();
        
        ByteBuffer recvBuf = ctx.getEncryptedIncomingBuf();
        ByteBuffer appBuf  = ctx.getIncomingBuf();

        try {
            int count;

            while((count = ch.read(recvBuf)) > 0) {
                recvBuf.flip();//т.к. далее планируется только чтение из данного буфера
                
                boolean exit = false;
                while (!exit) {
                    SSLEngineResult result = sslEngine.unwrap(recvBuf, appBuf);
                    
                    switch(result.getStatus()) {
                        case OK:
                            recvBuf.compact();//т.к. далее планируется только запись в этот буфер
                            
                            if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                                appBuf.clear();
                            } else if (result.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                                appBuf.clear();
                                
                                ctx.getSender().send(new ByteMessage());
                            } else if (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                                appBuf.flip();//т.к. далее планируется только чтение из данного буфера
                                
                                ByteMessage msg;
                                
                                do {
                                    appBuf.mark();//запоминаем позицию начала сообщения
                                    
                                    if ((msg = ctx.getProtocol().getNextMsg(appBuf)) != null) {
                                        messages.add(msg);
                                        ctx.setState(RecvState.IDLE);
                                    } else {
                                        appBuf.reset();//если сообщение считано не полностью, возвращаемся к позиции начала сообщения
                                        ctx.setState(RecvState.READ);
                                    }
                                } while (msg != null && appBuf.hasRemaining());
                                
                                appBuf.compact();//удаляем считанные данные, далее планируется запись
                            }
                            exit = true;
                            break;
                        case CLOSED: throw new EOFException();
                        case BUFFER_OVERFLOW:
                            appBuf.flip();//т.к. далее планируется только чтение из данного буфера
                            ByteBuffer b = ByteBuffer.allocateDirect(sslEngine.getSession().getApplicationBufferSize() + appBuf.limit()).put(appBuf);
                            BufUtils.destroyDirect(appBuf);
                            appBuf = b;//далее планируется только запись в данный буфер
                            ctx.setIncomingBuf(appBuf);
                            
                            break;
                        case BUFFER_UNDERFLOW:
                            if (sslEngine.getSession().getPacketBufferSize() > recvBuf.capacity()) {
                                b = ByteBuffer.allocateDirect(sslEngine.getSession().getPacketBufferSize()).put(recvBuf);
                                BufUtils.destroyDirect(recvBuf);
                                recvBuf = b;//далее планируется только запись в данный буфер
                                ctx.setEncryptedIncomingBuf(recvBuf);
                            }
                            
                            exit = true;
                            break;
                    }
                }
            }

            if (count == -1) {
                throw new EOFException();
            }

            onReceive(messages, ctx);
        } catch (Exception e) {//maybe "IOException: Connection reset by peer" in case when other peer start reading input stream but not fully. In this case other peer send RST.
            if (e instanceof EOFException) {
                e = null; //normally disconnect
            } else {
                logger.error("", e);
            }

            onReceive(messages, ctx);

            ctx.getEventHandler().closeConnection(e);
        }
    }

    private void onReceive(List<ByteMessage> messages, EndpointContext<ByteMessage> ctx) {
        if (!messages.isEmpty()) {
            try {
                ctx.getEventHandler().onReceive(messages);
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateTimeouts(SelectionKey key) {
        EndpointContext<ByteMessage> ctx = (EndpointContext<ByteMessage>) key.attachment();

        readEndpoints.remove(ctx.getReadNode());
        idleEndpoints.remove(ctx.getIdleNode());

        if (ctx.getState() == RecvState.IDLE) idleEndpoints.add(ctx.getIdleNode());
        if (ctx.getState() == RecvState.READ) readEndpoints.add(ctx.getReadNode());
        
        ctx.setLastRecv(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private void processWrite(SelectionKey key) {
        EndpointContext<ByteMessage> ctx = (EndpointContext<ByteMessage>) key.attachment();

        ctx.getSender().sendMessages();
    }

    private void checkIdle() {
        while (!idleEndpoints.isEmpty()) {
            EndpointContext<ByteMessage> ctx = idleEndpoints.getFirst().getValue();

            if (System.currentTimeMillis() - ctx.getLastRecv() > config.getIdleTimeout()) {
                if (ctx.getSelectionKey().isValid()) {
                    try {
                        ctx.getEventHandler().onIdle();
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }

                idleEndpoints.removeFirst();
            } else {
                break;
            }
        }
    }

    private void checkRead() {
        while (!readEndpoints.isEmpty()) {
            EndpointContext<ByteMessage> ctx = readEndpoints.getFirst().getValue();

            if (System.currentTimeMillis() - ctx.getLastRecv() > config.getSoTimeout()) {
                if (ctx.getSelectionKey().isValid()) {
                    try {
                        ctx.getEventHandler().onTimeout();
                    } catch (Exception e) {
                        logger.error("", e);
                    }

                    ctx.getEventHandler().closeConnection(null);
                }

                readEndpoints.removeFirst();
            } else {
                break;
            }
        }
    }
}
