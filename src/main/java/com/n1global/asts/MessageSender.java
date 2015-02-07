package com.n1global.asts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.Message;
import com.n1global.asts.util.CustomLinkedList;

public class MessageSender<T extends Message> {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private CustomLinkedList<EndpointContext<Message>> writeClients;

    private LinkedBlockingQueue<T> sendQueue = new LinkedBlockingQueue<>();

    private EndpointContext<T> ctx;

    private boolean canWriteNow = true;

    MessageSender(CustomLinkedList<EndpointContext<Message>> writeClients, EndpointContext<T> ctx) {
        this.writeClients = writeClients;
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

    @SuppressWarnings("unchecked")
    private void _sendMessages() {
        if (ctx.getWriteNode() == null) {
            ctx.setWriteNode(writeClients.addX((EndpointContext<Message>) ctx));
        }

        T msg;

        while ((msg = sendQueue.peek()) != null) {
            ByteBuffer buf = ctx.getProtocol().getBuffer(msg);

            try {
                if (((SocketChannel) ctx.getSelectionKey().channel()).write(buf) > 0) updateTimeout(ctx);
            } catch (IOException e) {
                logger.error("", e);

                ctx.getEventHandler().closeConnection(e);
            }

            if (buf.hasRemaining()) {
                ctx.getProtocol().tryShrinkBuffer();

                return;
            }

            sendQueue.remove();

            try {
                ctx.getEventHandler().onSend(msg);
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateTimeout(EndpointContext<T> ctx) {
        ctx.setLastSend(System.currentTimeMillis());

        writeClients.remove(ctx.getWriteNode());

        ctx.setWriteNode(writeClients.addX((EndpointContext<Message>) ctx));
    }

    private void checkQueueState() {
        if (sendQueue.isEmpty()) {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ);

            canWriteNow = true;

            removeFromQueue(ctx);
        } else {
            ctx.getSelectionKey().interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            canWriteNow = false;
        }
    }

    private void removeFromQueue(EndpointContext<T> ctx) {
        writeClients.remove(ctx.getWriteNode());

        ctx.setWriteNode(null);
    }
}
