package com.n1global.asts.protocol.lv;

import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;

public class LvByteMessageFrameProtocol<T extends ByteMessage> extends AbstractFrameProtocol<T> {
    private static final int CHAR_SIZE = (Character.SIZE >> 3);
    
    @Override
    public boolean putNextMsg(T frame) {
        if (getMsgOffset() == 0) {
            if (getOutgoingBuf().remaining() >= CHAR_SIZE) {//значит можно записать размер
                getOutgoingBuf().putChar((char)frame.getValue().length);
                
                setMsgOffset(CHAR_SIZE);
            } else {
                return false;
            }
        }
        
        int l = BufUtils.copy(frame.getValue(), getMsgOffset() - CHAR_SIZE, getOutgoingBuf());
            
        if (l == frame.getValue().length) {
            setMsgOffset(0);
                
            return true;
        } else {
            setMsgOffset(l);
            
            return false;
        }
    }
    
    @Override
    public T getNextMsg() {
        int availableDataLength = getIncomingBuf().limit() - getIncomingBuf().position();
        
        if (availableDataLength >= CHAR_SIZE) {//значит у нас есть размер
            int length = getIncomingBuf().getChar();
            
            if (availableDataLength >= CHAR_SIZE + length) {//значит у нас есть минимум одно сообщение
                byte[] msg = new byte[length];
                
                getIncomingBuf().get(msg);
                
                return createMessage(msg);
            }
        }
        
        return null;
    }
}
