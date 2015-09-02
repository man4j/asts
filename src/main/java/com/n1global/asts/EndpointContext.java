package com.n1global.asts;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import javax.net.ssl.SSLEngine;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;
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
    
    private ByteBuffer outgoingBuf;
    
    private ByteBuffer encryptedOutgoingBuf;
    
    private ByteBuffer incomingBuf;
    
    private ByteBuffer encryptedIncomingBuf;
    
    private RecvState state = RecvState.IDLE;
    
    public void initBuffers(int capacity) {
        outgoingBuf = ByteBuffer.allocateDirect(capacity);
        incomingBuf = ByteBuffer.allocateDirect(capacity);
    }
    
    public void initEncryptedBuffers(int capacity) {
        encryptedIncomingBuf = ByteBuffer.allocateDirect(capacity);
        encryptedOutgoingBuf = ByteBuffer.allocateDirect(capacity);
    }
    
    public void destroyBuffers() {
        BufUtils.destroyDirect(outgoingBuf);
        BufUtils.destroyDirect(incomingBuf);
        BufUtils.destroyDirect(encryptedIncomingBuf);
        BufUtils.destroyDirect(encryptedOutgoingBuf);
    }

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

    public ByteBuffer getOutgoingBuf() {
        return outgoingBuf;
    }

    public void setOutgoingBuf(ByteBuffer outgoingBuf) {
        this.outgoingBuf = outgoingBuf;
    }

    public ByteBuffer getEncryptedOutgoingBuf() {
        return encryptedOutgoingBuf;
    }

    public void setEncryptedOutgoingBuf(ByteBuffer encryptedOutgoingBuf) {
        this.encryptedOutgoingBuf = encryptedOutgoingBuf;
    }

    public ByteBuffer getIncomingBuf() {
        return incomingBuf;
    }

    public void setIncomingBuf(ByteBuffer incomingBuf) {
        this.incomingBuf = incomingBuf;
    }

    public ByteBuffer getEncryptedIncomingBuf() {
        return encryptedIncomingBuf;
    }

    public void setEncryptedIncomingBuf(ByteBuffer encryptedIncomingBuf) {
        this.encryptedIncomingBuf = encryptedIncomingBuf;
    }

    public RecvState getState() {
        return state;
    }

    public void setState(RecvState state) {
        this.state = state;
    }
}
