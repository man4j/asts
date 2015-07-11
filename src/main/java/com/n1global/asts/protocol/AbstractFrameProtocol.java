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
    
    private T currentMessage;

    private ByteBuffer outgoingBuf;
    
    private ByteBuffer incomingBuf;
    
    abstract public void msgToBuf(ByteBuffer buf, T msg);

    abstract public T bufToMsg(ByteBuffer buf);
    
    abstract public RecvState getState();
    
    @SuppressWarnings("unchecked")
    public AbstractFrameProtocol() {
        ResolvedType rt = typeResolver.resolve(getClass()).typeParametersFor(AbstractFrameProtocol.class).get(0);

        msgType = (Class<T>) rt.getErasedType();
    }
    
    public void initBuffers(int capacity) {
        outgoingBuf = ByteBuffer.allocateDirect(capacity);   
        incomingBuf = ByteBuffer.allocateDirect(capacity);
    }
    
    public void destroyBuffers() {
        BufUtils.destroyDirect(outgoingBuf);
        BufUtils.destroyDirect(incomingBuf);
    }
    
    public ByteBuffer getBuffer(T msg) {
        if (msg == currentMessage) return outgoingBuf;
        
        outgoingBuf.clear();

        msgToBuf(outgoingBuf, msg);
        
        outgoingBuf.position(0);
        
        currentMessage = msg;

        return outgoingBuf;
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
    
    public ByteBuffer getIncomingBuf() {
        return incomingBuf;
    }
}
