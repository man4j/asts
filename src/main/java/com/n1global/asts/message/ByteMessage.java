package com.n1global.asts.message;

public class ByteMessage {
    private byte[] value = new byte[0];

    public ByteMessage(byte[] value) {
        this.value = value;
    }

    public ByteMessage() {
        //empty
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }
}
