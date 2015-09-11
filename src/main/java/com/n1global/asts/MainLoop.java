package com.n1global.asts;

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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.util.BufUtils;
import com.n1global.asts.util.EndpointContextContainer;

import gnu.trove.list.linked.TLinkedList;

public class MainLoop {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MainLoopConfig config;

    private Selector selector;
    
    private SSLContext sslContext;
    
    private TLinkedList<EndpointContextContainer<ByteMessage>> idleEndpoints  = new TLinkedList<>();
    private TLinkedList<EndpointContextContainer<ByteMessage>> readEndpoints  = new TLinkedList<>();
    
    private ExecutorService executorService = Executors.newCachedThreadPool();
    
    private CompletionService<EndpointContext<ByteMessage>> completionService = new ExecutorCompletionService<>(executorService);

    public MainLoop(MainLoopConfig config) {
        try {
            this.config = config;
            
            sslContext = SSLContext.getInstance("TLSv1.2");
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            kmf.init(config.getKeyStore(), config.getKeyStorePassword().toCharArray());
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(config.getTrustStore());
            
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

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
    private <T extends ByteMessage> EndpointContext<T> attachEndpointContext(SelectableChannel channel, EndpointConfig<T> config, boolean client) throws InstantiationException, IllegalAccessException, SSLException, NoSuchAlgorithmException {
        EndpointContext<T> ctx = new EndpointContext<>();

        ctx.initSSL(sslContext, client);
        ctx.setEventHandler(config.getHandlerClass().newInstance());
        ctx.setProtocol(config.getProtocolClass().newInstance());
        ctx.setSelectionKey(channel.keyFor(selector));

        channel.keyFor(selector).attach(ctx);

        idleEndpoints.add((EndpointContextContainer<ByteMessage>) ctx.getIdleNode());
        
        ctx.getEventHandler().onConnect();
        
        return ctx;
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
            
            Future<EndpointContext<ByteMessage>> f;
            
            while ((f = completionService.poll()) != null) {
                try {
                    EndpointContext<ByteMessage> ctx = f.get();
                    
                    switch (ctx.getSslEngine().getHandshakeStatus()) {
                        case NEED_TASK:
                            processTask(ctx);
                            break;
                        case NEED_WRAP:
                            ctx.getSender().send(new ByteMessage());
                            break;
                        default:
                            throw new IllegalStateException("Unexpected handshake status: " + ctx.getSslEngine().getHandshakeStatus());
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        try {
            selector.close();
            
            executorService.shutdownNow();
            executorService.awaitTermination(1, TimeUnit.DAYS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processConnect(SelectionKey key) {
        try {
            if (((SocketChannel) key.channel()).finishConnect()) {
                key.interestOps(SelectionKey.OP_READ);

                EndpointContext<ByteMessage> ctx = attachEndpointContext(key.channel(), (EndpointConfig<ByteMessage>) key.attachment(), true);
                
                ctx.getSender().send(new ByteMessage());//marker message for init handshake
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
              .setOption(StandardSocketOptions.SO_SNDBUF, endpointConfig.getSocketSendBufSize())
              .configureBlocking(false)
              .register(key.selector(), SelectionKey.OP_READ);

            attachEndpointContext(ch, endpointConfig, false);
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
                    SSLEngineResult result = ctx.getSslEngine().unwrap(recvBuf, appBuf);
                    
                    switch(result.getStatus()) {
                        case OK:
                            recvBuf.compact();//т.к. далее планируется только запись в этот буфер
                            
                            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                                processTask(ctx);
                            } else if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
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
                            ByteBuffer b = ByteBuffer.allocateDirect(ctx.getSslEngine().getSession().getApplicationBufferSize() + appBuf.limit()).put(appBuf);
                            BufUtils.destroyDirect(appBuf);
                            appBuf = b;//далее планируется только запись в данный буфер
                            ctx.setIncomingBuf(appBuf);
                            
                            break;
                        case BUFFER_UNDERFLOW:
                            if (ctx.getSslEngine().getSession().getPacketBufferSize() > recvBuf.capacity()) {
                                b = ByteBuffer.allocateDirect(ctx.getSslEngine().getSession().getPacketBufferSize()).put(recvBuf);
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

            ctx.getEventHandler().closedByPeer(e);
        }
    }

    private void processTask(EndpointContext<ByteMessage> ctx) {
        List<Future<?>> futures = new ArrayList<>();
        
        Runnable task;
        
        while ((task = ctx.getSslEngine().getDelegatedTask()) != null) {
            futures.add(executorService.submit(task));
        }
        
        completionService.submit(() -> {
            for (Future<?> f : futures) f.get();
            
            return ctx;
        });
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

                    ctx.getEventHandler().closeContext();
                }

                readEndpoints.removeFirst();
            } else {
                break;
            }
        }
    }
}
