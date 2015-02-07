package com.n1global.asts;

import java.nio.channels.Selector;

public interface SelectHandler {
    void onSelect(Selector selector);
}
