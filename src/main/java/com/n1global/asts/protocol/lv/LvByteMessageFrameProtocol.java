package com.n1global.asts.protocol.lv;

import java.nio.ByteBuffer;

import com.n1global.asts.RecvState;
import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;

public class LvByteMessageFrameProtocol<T extends ByteMessage> extends AbstractFrameProtocol<T> {
    private static final int CHAR_SIZE = (Character.SIZE >> 3);
    
    @Override
    public void msgToBuf(ByteBuffer buf, T frame) {
        buf.putChar((char) frame.getValue().length).put(frame.getValue()).limit(CHAR_SIZE + frame.getValue().length);
    }
    
    @Override
    public T bufToMsg(ByteBuffer buf) {
        if (getIncomingBuf().position() < CHAR_SIZE) {
            getIncomingBuf().limit(CHAR_SIZE);
            
            BufUtils.copy(buf, getIncomingBuf());
            
            if (getIncomingBuf().position() == CHAR_SIZE) {
                int length = ((ByteBuffer) getIncomingBuf().position(0)).getChar();
                
                getIncomingBuf().limit(length + CHAR_SIZE);
            } else {
                return null;
            }
        }
        
        BufUtils.copy(buf, getIncomingBuf());
        
        if (!getIncomingBuf().hasRemaining()) {
            byte[] msg = new byte[getIncomingBuf().limit() - CHAR_SIZE];
            
            ((ByteBuffer)getIncomingBuf().position(CHAR_SIZE)).get(msg);
            
            T frame = createMessage(msg);

            getIncomingBuf().clear();

            return frame;
        }

        return null;
    }

    @Override
    public RecvState getState() {
        return getIncomingBuf().position() == 0 ? RecvState.IDLE : RecvState.READ;
    }
}
