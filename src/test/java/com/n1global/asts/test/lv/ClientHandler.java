package com.n1global.asts.test.lv;

import java.util.List;

import com.n1global.asts.AbstractEventHandler;
import com.n1global.asts.message.StringMessage;

public class ClientHandler extends AbstractEventHandler<StringMessage> {
    @Override
    public void onConnect() {
        send(new StringMessage(Message.MESSAGE));
    }

    @Override
    public void onReceive(List<StringMessage> messages) {
        send(new StringMessage(Message.MESSAGE));
    }
}
