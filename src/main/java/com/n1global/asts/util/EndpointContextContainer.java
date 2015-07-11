package com.n1global.asts.util;

import com.n1global.asts.EndpointContext;
import com.n1global.asts.message.ByteMessage;

import gnu.trove.list.TLinkableAdapter;

public class EndpointContextContainer<T extends ByteMessage> extends TLinkableAdapter<EndpointContextContainer<T>> {
    private final EndpointContext<T> value;

    public EndpointContextContainer(EndpointContext<T> value) {
        this.value = value;
    }
    
    public EndpointContext<T> getValue() {
        return value;
    }
}
