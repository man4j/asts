package com.n1global.asts.protocol;

import java.nio.ByteBuffer;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.n1global.asts.RecvState;
import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.util.BufUtils;

/**
 * The main target of FrameProtocol is convert messages to frames.
 */
abstract public class AbstractFrameProtocol<T extends ByteMessage> {
    private static TypeResolver typeResolver = new TypeResolver();
    
    private Class<T> msgType;
    
    private int msgOffset;

    private ByteBuffer outgoingBuf;
    
    private ByteBuffer encryptedOutgoingBuf;
    
    private ByteBuffer incomingBuf;
    
    private ByteBuffer encryptedIncomingBuf;
    
    private RecvState state = RecvState.IDLE;
    
    abstract public boolean putNextMsg(T msg);

    abstract public T getNextMsg();
    
    @SuppressWarnings("unchecked")
    public AbstractFrameProtocol() {
        ResolvedType rt = typeResolver.resolve(getClass()).typeParametersFor(AbstractFrameProtocol.class).get(0);

        msgType = (Class<T>) rt.getErasedType();
    }
    
    public void initBuffers(int capacity) {
        outgoingBuf = ByteBuffer.allocateDirect(capacity);   
        incomingBuf = ByteBuffer.allocateDirect(capacity);
    }
    
    public void initEncryptedBuffers(int capacity) {
        encryptedIncomingBuf = ByteBuffer.allocateDirect(capacity);
        encryptedOutgoingBuf = ByteBuffer.allocate(capacity);
    }
    
    public void destroyBuffers() {
        BufUtils.destroyDirect(outgoingBuf);
        BufUtils.destroyDirect(incomingBuf);
        BufUtils.destroyDirect(encryptedIncomingBuf);
        BufUtils.destroyDirect(encryptedOutgoingBuf);
    }
    
    public T createMessage(byte[] value) {
        try {
            T msg = msgType.newInstance();
            
            msg.setValue(value);
            
            return msg;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public int getMsgOffset() {
        return msgOffset;
    }

    public void setMsgOffset(int msgOffset) {
        this.msgOffset = msgOffset;
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
