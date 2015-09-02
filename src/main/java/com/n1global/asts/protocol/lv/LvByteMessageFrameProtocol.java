package com.n1global.asts.protocol.lv;

import java.nio.ByteBuffer;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;

public class LvByteMessageFrameProtocol<T extends ByteMessage> extends AbstractFrameProtocol<T> {
    private static final int CHAR_SIZE = (Character.SIZE >> 3);
    
    @Override
    public boolean putNextMsg(ByteBuffer buf, T frame) {
        if (getMsgOffset() == 0) {
            if (buf.remaining() >= CHAR_SIZE) {//значит можно записать размер
                buf.putChar((char)frame.getValue().length);
                
                setMsgOffset(CHAR_SIZE);
            } else {
                return false;
            }
        }
        
        int l = BufUtils.copy(frame.getValue(), getMsgOffset() - CHAR_SIZE, buf);
            
        if (l == frame.getValue().length) {
            setMsgOffset(0);
                
            return true;
        } else {
            setMsgOffset(getMsgOffset() + l);
            
            return false;
        }
    }
    
    @Override
    public T getNextMsg(ByteBuffer buf) {
        int availableDataLength = buf.limit() - buf.position();
        
        if (availableDataLength >= CHAR_SIZE) {//значит у нас есть размер
            int length = buf.getChar();
            
            if (availableDataLength >= CHAR_SIZE + length) {//значит у нас есть минимум одно сообщение
                byte[] msg = new byte[length];
                
                buf.get(msg);
                
                return createMessage(msg);
            }
        }
        
        return null;
    }
}
