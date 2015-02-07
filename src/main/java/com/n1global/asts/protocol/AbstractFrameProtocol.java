package com.n1global.asts.protocol;

import java.nio.ByteBuffer;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.n1global.asts.RecvState;
import com.n1global.asts.message.Message;

/**
 * The main target of FrameProtocol is convert messages to frames.
 */
abstract public class AbstractFrameProtocol<T extends Message> {
    private T currentMessage;

    private TypeResolver typeResolver = new TypeResolver();

    private ByteBuffer currentOutgoingBuf;
    
    abstract public ByteBuffer msgToBuf(T msg);

    abstract public T bufToMsg(ByteBuffer buf);
    
    abstract public RecvState getState();

    public ByteBuffer getBuffer(T msg) {
        if (msg == currentMessage) return currentOutgoingBuf;

        currentOutgoingBuf = (ByteBuffer) msgToBuf(msg).clear();

        currentMessage = msg;

        return currentOutgoingBuf;
    }

    public void tryShrinkBuffer() {
        int half = currentOutgoingBuf.capacity() / 2;

        if (currentOutgoingBuf.position() >= half) {
            ByteBuffer shrinkedBuf = ByteBuffer.allocate(currentOutgoingBuf.remaining());

            shrinkedBuf.put(currentOutgoingBuf);

            shrinkedBuf.clear();

            currentOutgoingBuf = shrinkedBuf;
        }
    }

    @SuppressWarnings("unchecked")
    public T createMessage() {
        try {
            ResolvedType rt = typeResolver.resolve(getClass()).typeParametersFor(AbstractFrameProtocol.class).get(0);

            return (T) rt.getErasedType().newInstance();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
