package com.n1global.asts.protocol;

import java.nio.ByteBuffer;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.n1global.asts.message.ByteMessage;

/**
 * The main target of FrameProtocol is convert messages to frames.
 */
abstract public class AbstractFrameProtocol<T extends ByteMessage> {
    private static TypeResolver typeResolver = new TypeResolver();
    
    private Class<T> msgType;
    
    private int msgOffset;
    
    abstract public boolean putNextMsg(ByteBuffer buf, T msg);

    abstract public T getNextMsg(ByteBuffer buf);
    
    @SuppressWarnings("unchecked")
    public AbstractFrameProtocol() {
        ResolvedType rt = typeResolver.resolve(getClass()).typeParametersFor(AbstractFrameProtocol.class).get(0);

        msgType = (Class<T>) rt.getErasedType();
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
}
