package com.n1global.asts.protocol.websocket;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.n1global.asts.RecvState;
import com.n1global.asts.message.websocket.WebSocketMessage;
import com.n1global.asts.message.websocket.WebSocketMessageType;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.BufUtils;

abstract public class WebSocketProtocol extends AbstractFrameProtocol<WebSocketMessage> {
    /*
    private ByteBuffer currentIncomingBuf;

    private ByteBuffer header1Buf = ByteBuffer.allocate(2);

    private ByteBuffer header2Buf;

    private boolean handshakeReceived;

    private long dataLength;

    public ByteBuffer msgToBuf(WebSocketMessage msg) {
        if (msg.getType() == WebSocketMessageType.HANDSHAKE) return ByteBuffer.wrap(msg.getValue());

        byte[] lengthBuffer = createLengthBuf(msg.getValue().length);

        return ByteBuffer.allocate(1 + lengthBuffer.length + msg.getValue().length)
            .put((byte)((msg.isLast() ? 128 : 0) | msg.getType().getTypeCode())).put(lengthBuffer).put(msg.getValue());
    }

    private byte[] createLengthBuf(long length) {
        if (length < 126)
            return ByteBuffer.allocate(1).put((byte) length).array();
        else if (length < 65536)
            return ByteBuffer.allocate(3).put((byte) 126).putChar((char) length).array();
        else
            throw new IllegalStateException("Maximum length exceeded");
    }

    @Override
    public WebSocketMessage nextMsg(ByteBuffer buf) {
        if (!handshakeReceived) {
            if (currentIncomingBuf == null) //new frame
                currentIncomingBuf = ByteBuffer.allocate(buf.limit()).put(buf);
            else
                currentIncomingBuf = BufUtils.concat(currentIncomingBuf, buf);

            byte[] arr = currentIncomingBuf.array();

            if (arr.length > 4096) throw new IllegalStateException("Maximum length exceeded");

            if (arr.length >= 4 && new String(Arrays.copyOfRange(arr, arr.length - 4, arr.length)).equals("\r\n\r\n")) {
                WebSocketMessage msg = new WebSocketMessage(arr, WebSocketMessageType.HANDSHAKE);

                currentIncomingBuf = null;

                handshakeReceived = true;

                return msg;
            }
        } else {
            if (header1Buf.hasRemaining()) {
                BufUtils.copy(buf, header1Buf);

                if (!header1Buf.hasRemaining()) { //if we have full header1
                    boolean masked = (header1Buf.array()[1] & 0b1000_0000) != 0 ? true : false;

                    dataLength = header1Buf.array()[1] & 0b0111_1111;

                    int header2Length = 0;

                    if (masked) header2Length += 4;//for masking key value
                    if (dataLength == 126) header2Length += 2;//for extended payload length
                    if (dataLength == 127) throw new IllegalStateException("Maximum length exceeded");

                    header2Buf = ByteBuffer.allocate(header2Length);
                } else {
                    return null;
                }
            }

            if (header2Buf.hasRemaining()) {
                BufUtils.copy(buf, header2Buf);

                if (!header2Buf.hasRemaining()) { //if we have full header2
                    header2Buf.clear();

                    currentIncomingBuf = ByteBuffer.allocate(dataLength == 126 ? header2Buf.getChar() : (int)dataLength);
                } else {
                    return null;
                }
            }

            BufUtils.copy(buf, currentIncomingBuf);

            if (!currentIncomingBuf.hasRemaining()) {
                int code = 0x0F & header1Buf.array()[0];

                boolean last = true;

                if (code == WebSocketMessageType.BYTES.getTypeCode() || code == WebSocketMessageType.TEXT.getTypeCode()) {
                    byte[] maskingKey = new byte[4];

                    header2Buf.get(maskingKey);

                    unmask(currentIncomingBuf.array(), maskingKey);

                    last = (header1Buf.array()[0] & 0b1000_0000) == 128;
                }

                WebSocketMessage msg = new WebSocketMessage(currentIncomingBuf.array(), last, WebSocketMessageType.byTypeCode(code));

                currentIncomingBuf = null;

                header1Buf.clear();

                header2Buf = null;

                return msg;
            }
        }

        return null;
    }

    @Override
    public RecvState getState() {
        return currentIncomingBuf == null && header1Buf.position() == 0 && header2Buf == null ? RecvState.IDLE : RecvState.READ;
    }

    private void unmask(byte[] data, byte[] maskingKey) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (data[i] ^ maskingKey[i % 4]);
        }
    }

    @Override
    public void msgToBuf(ByteBuffer buf, WebSocketMessage msg) {
        // TODO Auto-generated method stub
        
    }*/
}
