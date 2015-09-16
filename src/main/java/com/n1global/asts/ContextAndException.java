package com.n1global.asts;

import com.n1global.asts.message.ByteMessage;

public class ContextAndException {
    private EndpointContext<ByteMessage> ctx;
    
    private Exception e;

    public ContextAndException(EndpointContext<ByteMessage> ctx, Exception e) {
        this.ctx = ctx;
        this.e = e;
    }

    public ContextAndException(EndpointContext<ByteMessage> ctx) {
        this.ctx = ctx;
    }

    public EndpointContext<ByteMessage> getCtx() {
        return ctx;
    }

    public Exception getE() {
        return e;
    }
}
