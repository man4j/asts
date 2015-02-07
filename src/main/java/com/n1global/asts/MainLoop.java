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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.Message;
import com.n1global.asts.util.CustomLinkedList;

public class MainLoop {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Selector selector;

    private ByteBuffer recvBuf;

    private MainLoopConfig config;

    private long lastTimeoutsCheck = System.currentTimeMillis();

    private CustomLinkedList<EndpointContext<Message>> idleEndpoints = new CustomLinkedList<>();

    private CustomLinkedList<EndpointContext<Message>> readEndpoints = new CustomLinkedList<>();

    private CustomLinkedList<EndpointContext<Message>> writeEndpoints = new CustomLinkedList<>();

    public MainLoop(MainLoopConfig config) {
        try {
            this.config = config;

            recvBuf = ByteBuffer.allocate(config.getAppRecvBufSize());

            selector = Selector.open();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MainLoop() {
        this(new MainLoopConfig.Builder().build());
    }

    public <T extends Message> void addServer(EndpointConfig<T> config) {
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

    public <T extends Message> void addClient(EndpointConfig<T> config, SocketAddress remote) {
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
    private <T extends Message> void register(SelectableChannel channel, EndpointConfig<T> config) throws InstantiationException, IllegalAccessException {
        EndpointContext<T> ctx = new EndpointContext<>();

        ctx.setEventHandler(config.getHandlerClass().newInstance());
        ctx.setProtocol(config.getProtocolClass().newInstance());
        ctx.setSelectionKey(channel.keyFor(selector));
        ctx.setSender((MessageSender<T>) new MessageSender<>(writeEndpoints, (EndpointContext<Message>) ctx));

        channel.keyFor(selector).attach(ctx);

        ctx.getEventHandler().onConnect();

        ctx.setIdleNode(idleEndpoints.addX((EndpointContext<Message>) ctx)); //if everything is ok
    }

    public void loop() {
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

                if (key.isReadable()) processRead(key);

                if (key.isValid() && key.isWritable()) processWrite(key);

                if (key.isValid() && key.isConnectable()) processConnect(key);

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
                processTimeouts();

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

                register(key.channel(), (EndpointConfig<Message>) key.attachment());
            }
        } catch (Exception e) {//if connection unsuccessfully key.cancel() invoked automatically
            logger.error("", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processAccept(SelectionKey key) {
        try {
            SocketChannel ch = ((ServerSocketChannel) key.channel()).accept();

            EndpointConfig<Message> endpointConfig = (EndpointConfig<Message>) key.attachment();

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

        EndpointContext<Message> ctx = (EndpointContext<Message>) key.attachment();

        List<Message> messages = new ArrayList<>();

        try {
            int count;

            while((count = ch.read(recvBuf)) > 0) {
                recvBuf.flip();

                try {
                    while (recvBuf.hasRemaining()) {
                        Message msg = ctx.getProtocol().bufToMsg(recvBuf);

                        if (msg != null) {
                            messages.add(msg);
                        }
                    }
                } finally {
                    recvBuf.clear();
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

    private void onReceive(List<Message> messages, EndpointContext<Message> ctx) {
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
        EndpointContext<Message> ctx = (EndpointContext<Message>) key.attachment();

        idleEndpoints.remove(ctx.getIdleNode());
        readEndpoints.remove(ctx.getReadNode());

        ctx.setIdleNode(null);
        ctx.setReadNode(null);

        ctx.setLastRecv(System.currentTimeMillis());

        if (ctx.getProtocol().getState() == RecvState.IDLE) ctx.setIdleNode(idleEndpoints.addX(ctx));
        if (ctx.getProtocol().getState() == RecvState.READ) ctx.setReadNode(readEndpoints.addX(ctx));
    }

    @SuppressWarnings("unchecked")
    private void processWrite(SelectionKey key) {
        EndpointContext<Message> ctx = (EndpointContext<Message>) key.attachment();

        ctx.getSender().sendMessages();
    }

    private void processTimeouts() {
        processQueue(readEndpoints, true);
        processQueue(writeEndpoints, false);

        checkIdle();
    }

    private void checkIdle() {
        while (!idleEndpoints.isEmpty()) {
            EndpointContext<Message> ctx = idleEndpoints.peek();

            if (System.currentTimeMillis() - ctx.getLastRecv() > config.getIdleTimeout()) {
                if (ctx.getSelectionKey().isValid()) {
                    try {
                        ctx.getEventHandler().onIdle();
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                }

                idleEndpoints.remove();

                ctx.setIdleNode(null);
            } else {
                break;
            }
        }
    }

    private void processQueue(CustomLinkedList<EndpointContext<Message>> queue, boolean recv) {
        while (!queue.isEmpty()) {
            EndpointContext<Message> ctx = queue.peek();

            long timeout = recv ? ctx.getLastRecv() : ctx.getLastSend();

            long delay = System.currentTimeMillis() - timeout;

            if (delay > config.getSoTimeout()) {
                if (ctx.getSelectionKey().isValid()) {
                    try {
                        ctx.getEventHandler().onTimeout();
                    } catch (Exception e) {
                        logger.error("", e);
                    }

                    ctx.getEventHandler().closeConnection(null);
                }

                queue.remove();
            } else {
                break;
            }
        }
    }
}
