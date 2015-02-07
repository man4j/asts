package com.n1global.asts.protocol.lv;

import java.nio.ByteBuffer;

import com.n1global.asts.RecvState;
import com.n1global.asts.message.ByteMessage;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;

public class LvByteMessageFrameProtocol<T extends ByteMessage> extends AbstractFrameProtocol<T> {
    private ByteBuffer currentIncomingBuf;

    private ByteBuffer lengthBuf = ByteBuffer.allocate(Character.SIZE >> 3);

    @Override
    public ByteBuffer msgToBuf(T frame) {
        return ByteBuffer.allocate(frame.getValue().length + (Character.SIZE >> 3))
            .putChar((char) frame.getValue().length).put(frame.getValue());
    }

    @Override
    public T bufToMsg(ByteBuffer buf) {
        if (lengthBuf.hasRemaining()) {
            BufUtils.copy(buf, lengthBuf);

            if (!lengthBuf.hasRemaining()) { //if we have length
                lengthBuf.clear();

                int length = lengthBuf.getChar();

                currentIncomingBuf = ByteBuffer.allocate(length);
            } else {
                return null;
            }
        }

        BufUtils.copy(buf, currentIncomingBuf);

        if (!currentIncomingBuf.hasRemaining()) {
            T frame = createMessage();

            frame.setValue(currentIncomingBuf.array());

            currentIncomingBuf = null;

            lengthBuf.clear();

            return frame;
        }

        return null;
    }

    @Override
    public RecvState getState() {
        return currentIncomingBuf == null && lengthBuf.position() == 0 ? RecvState.IDLE : RecvState.READ;
    }
}
