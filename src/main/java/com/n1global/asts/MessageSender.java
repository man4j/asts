package com.n1global.asts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private void _sendMessages() {
        T msg;

        while ((msg = sendQueue.peek()) != null) {
            ByteBuffer buf = ctx.getProtocol().getBuffer(msg);

            try {
                ((SocketChannel) ctx.getSelectionKey().channel()).write(buf);
            } catch (IOException e) {
                logger.error("", e);

                ctx.getEventHandler().closeConnection(e);
            }

            if (!buf.hasRemaining()) {
                sendQueue.remove();

                try {
                    ctx.getEventHandler().onSend(msg);
                } catch (Exception e) {
                    logger.error("", e);
                }
            }
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
