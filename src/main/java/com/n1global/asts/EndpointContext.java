package com.n1global.asts;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

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
    
    private boolean closeRequested;
    
    @SuppressWarnings("unchecked")
    public EndpointContext(SSLContext sslContext, boolean client, AbstractEventHandler<T> eventHandler, AbstractFrameProtocol<T> protocol, SelectionKey selectionKey) throws SSLException, NoSuchAlgorithmException {
        sender = (MessageSender<T>) new MessageSender<>((EndpointContext<ByteMessage>) this);
        idleNode = new EndpointContextContainer<T>(this);
        readNode = new EndpointContextContainer<T>(this);
        
        initSSL(sslContext, client);
        
        this.eventHandler = eventHandler;
        this.protocol = protocol;
        this.selectionKey = selectionKey;
        
        eventHandler.setEndpointContext(this);
    }
    
    private void initSSL(SSLContext sslContext, boolean client) throws SSLException, NoSuchAlgorithmException {
        sslEngine = sslContext.createSSLEngine();
        
        sslEngine.setUseClientMode(client);
        sslEngine.beginHandshake();//need for process initial marker message (getHandshakeStatus)
        
        SSLSession s = sslEngine.getSession();
        
        initApplicationBuffers(s.getApplicationBufferSize());
        initEncryptedBuffers(s.getPacketBufferSize());
    }
    
    private void initApplicationBuffers(int capacity) {
        outgoingBuf = ByteBuffer.allocateDirect(capacity);
        incomingBuf = ByteBuffer.allocateDirect(capacity);
    }
    
    private void initEncryptedBuffers(int capacity) {
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

    public SSLEngine getSslEngine() {
        return sslEngine;
    }

    AbstractEventHandler<T> getEventHandler() {
        return eventHandler;
    }

    AbstractFrameProtocol<T> getProtocol() {
        return protocol;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
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

    public EndpointContextContainer<T> getIdleNode() {
        return idleNode;
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

    public boolean isCloseRequested() {
        return closeRequested;
    }

    public void setCloseRequested(boolean closeRequested) {
        this.closeRequested = closeRequested;
    }
}
