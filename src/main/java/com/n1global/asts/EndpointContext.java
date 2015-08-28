package com.n1global.asts;

import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLEngine;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.EndpointContextContainer;

public class EndpointContext<T extends ByteMessage> {
    private AbstractEventHandler<T> eventHandler;

    private AbstractFrameProtocol<T> protocol;

    private SelectionKey selectionKey;

    private MessageSender<T> sender;
    
    private SSLEngine sslEngine;
    
    private long lastRecv = System.currentTimeMillis();

    private EndpointContextContainer<T> readNode;

    private EndpointContextContainer<T> idleNode;

    private Object payload;

    public MessageSender<T> getSender() {
        return sender;
    }

    void setSender(MessageSender<T> sender) {
        this.sender = sender;
    }
    
    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    AbstractEventHandler<T> getEventHandler() {
        return eventHandler;
    }

    void setEventHandler(AbstractEventHandler<T> eventHandler) {
        this.eventHandler = eventHandler;

        eventHandler.setEndpointContext(this);
    }

    AbstractFrameProtocol<T> getProtocol() {
        return protocol;
    }

    void setProtocol(AbstractFrameProtocol<T> protocol) {
        this.protocol = protocol;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    long getLastRecv() {
        return lastRecv;
    }

    void setLastRecv(long lastRecv) {
        this.lastRecv = lastRecv;
    }

    public EndpointContextContainer<T> getReadNode() {
        return readNode;
    }

    public void setReadNode(EndpointContextContainer<T> readNode) {
        this.readNode = readNode;
    }

    public EndpointContextContainer<T> getIdleNode() {
        return idleNode;
    }

    public void setIdleNode(EndpointContextContainer<T> idleNode) {
        this.idleNode = idleNode;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
