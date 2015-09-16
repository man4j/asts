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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

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
    
    private long lastTimeoutsCheck = System.currentTimeMillis();
    
    private TLinkedList<EndpointContextContainer<ByteMessage>> idleEndpoints  = new TLinkedList<>();
    private TLinkedList<EndpointContextContainer<ByteMessage>> readEndpoints  = new TLinkedList<>();
    
    private LinkedBlockingQueue<ContextAndException> completedHandshakeTasks = new LinkedBlockingQueue<>();

    public MainLoop(MainLoopConfig config) {
        try {
            this.config = config;
            
            createSslContext(config);

            selector = Selector.open();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createSslContext(MainLoopConfig config) throws Exception {
        sslContext = SSLContext.getInstance("TLSv1.2");
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        kmf.init(config.getKeyStore(), config.getKeyStorePassword().toCharArray());
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(config.getTrustStore());
        
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    }

    public <T extends ByteMessage> void addServer(EndpointConfig<T> config) {
        try {
            ServerSocketChannel.open()
                               .bind(new InetSocketAddress(config.getLocalAddr(), config.getLocalPort()), 50)
                               .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                               .setOption(StandardSocketOptions.SO_RCVBUF, config.getSocketRecvBufSize())
                               .configureBlocking(false)
                               .register(selector, SelectionKey.OP_ACCEPT, config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends ByteMessage> void addClient(EndpointConfig<T> config, SocketAddress remote) {
        try {
            ((SocketChannel) SocketChannel.open()
                                          .bind(new InetSocketAddress(config.getLocalAddr(), config.getLocalPort()))
                                          .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                                          .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                                          .setOption(StandardSocketOptions.TCP_NODELAY, true)
                                          .setOption(StandardSocketOptions.SO_RCVBUF, config.getSocketRecvBufSize())
                                          .setOption(StandardSocketOptions.SO_SNDBUF, config.getSocketSendBufSize())
                                          .configureBlocking(false)
                                          .register(selector, SelectionKey.OP_CONNECT, config)
                                          .channel())
                                          .connect(remote);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            select();
            processEvents();
            invokeSelectHandler();
            checkTimeouts();
            checkHandshakeTasks();
        }

        closeAll();
    }
    
    private void select() {
        try {
            selector.select(1000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private void processEvents() {
        Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

        while (iter.hasNext()) {
            SelectionKey key = iter.next();

            if (key.isAcceptable()) processAccept(key);
            if (key.isConnectable()) processConnect(key);
            if (key.isReadable()) processRead(key);
            if (key.isValid() && key.isWritable()) processWrite(key);

            iter.remove();
        }
    }
    
    private void invokeSelectHandler() {
        if (config.getSelectHandler() != null) {
            try {
                config.getSelectHandler().onSelect(selector);
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }
    
    private void checkTimeouts() {
        if (System.currentTimeMillis() - lastTimeoutsCheck > 1000) {
            checkRead();
            checkIdle();

            lastTimeoutsCheck = System.currentTimeMillis();
        }
    }
    
    private void checkHandshakeTasks() {
        ContextAndException ctxAndEx;
        
        while ((ctxAndEx = completedHandshakeTasks.poll()) != null) {
            EndpointContext<ByteMessage> ctx = ctxAndEx.getCtx();
            
            if (ctxAndEx.getE() != null) {
                logger.error("", ctxAndEx.getE());
                
                ctx.getEventHandler().closedByPeer(ctxAndEx.getE());
            } else {
                switch (ctx.getSslEngine().getHandshakeStatus()) {
                    case NEED_TASK:
                        processTask(ctx);
                        
                        break;
                    case NEED_WRAP:
                        ctx.getSender().send(new ByteMessage());
                        
                        break;
                    case NEED_UNWRAP:
                        try {
                            unwrap(ctx);
                        } catch (EOFException e) {
                            ctx.getEventHandler().closedByPeer(null);
                        } catch (Exception e) {
                            logger.error("", e);
                            
                            ctx.getEventHandler().closedByPeer(e);
                        }
                        
                        break;
                    default:
                        throw new IllegalStateException("Unexpected handshake status: " + ctx.getSslEngine().getHandshakeStatus());
                }
            }
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
        
        return ctx;
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
            EndpointConfig<ByteMessage> endpointConfig = (EndpointConfig<ByteMessage>) key.attachment();
            
            SocketChannel ch = (SocketChannel) ((ServerSocketChannel) key.channel()).accept()
                                                                                    .setOption(StandardSocketOptions.SO_KEEPALIVE, true)
                                                                                    .setOption(StandardSocketOptions.TCP_NODELAY, true)
                                                                                    .setOption(StandardSocketOptions.SO_SNDBUF, endpointConfig.getSocketSendBufSize())
                                                                                    .configureBlocking(false)
                                                                                    .register(key.selector(), SelectionKey.OP_READ)
                                                                                    .channel();

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
        EndpointContext<ByteMessage> ctx = (EndpointContext<ByteMessage>) key.attachment();

        ByteBuffer recvBuf = ctx.getEncryptedIncomingBuf();
        
        try {
            int count;
            
            HandshakeStatus s = null;

            while(((count = ((SocketChannel) key.channel()).read(recvBuf)) > 0) && s != HandshakeStatus.NEED_TASK) {
                s = unwrap(ctx);
            }

            if (count == -1) {
                throw new EOFException();
            }
        } catch (EOFException e) {
            ctx.getEventHandler().closedByPeer(null);
        } catch (Exception e) {//maybe "IOException: Connection reset by peer" in case when other peer start reading input stream but not fully. In this case other peer send RST.
            logger.error("", e);
            
            ctx.getEventHandler().closedByPeer(e);
        }
    }
    
    private HandshakeStatus unwrap(EndpointContext<ByteMessage> ctx) throws SSLException, EOFException {
        ByteBuffer recvBuf = ctx.getEncryptedIncomingBuf();
        ByteBuffer appBuf  = ctx.getIncomingBuf();
        
        List<ByteMessage> messages = new ArrayList<>();
        
        recvBuf.flip();//т.к. далее планируется только чтение из данного буфера
        
        boolean exit = false;
        
        SSLEngineResult result = null;
        
        while (!exit) {
            result = ctx.getSslEngine().unwrap(recvBuf, appBuf);//appBuf при хендшейке не модифицируется
            
            switch(result.getStatus()) {
                case OK:
                    recvBuf.compact();//т.к. далее планируется только запись в этот буфер
                    
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) processTask(ctx);
                    if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) ctx.getEventHandler().onConnect();
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) ctx.getSender().send(new ByteMessage());
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP) unwrap(ctx);
                    if (result.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
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
                    recvBuf.compact();//т.к. далее планируется только запись в этот буфер
                    
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
        
        onReceive(messages, ctx);
        
        return result.getHandshakeStatus();
    }

    private void processTask(EndpointContext<ByteMessage> ctx) {
        List<Future<?>> futures = new ArrayList<>();
        
        Runnable task;
        
        while ((task = ctx.getSslEngine().getDelegatedTask()) != null) {
            futures.add(CompletableFuture.runAsync(task));
        }
        
        CompletableFuture.runAsync(() -> {
            Exception ex = null;
            
            try {
                for (Future<?> f : futures) f.get();
            } catch (Exception e) {
                ex = e;
            }
            
            completedHandshakeTasks.add(new ContextAndException(ctx, ex));
            selector.wakeup();
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
    
    private void closeAll() {
        try {
            selector.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
