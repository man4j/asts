package com.n1global.asts.test.lv;

import java.util.List;

import com.n1global.asts.AbstractEventHandler;
import com.n1global.asts.message.StringMessage;

public class ServerHandler extends AbstractEventHandler<StringMessage> {
    @Override
    public void onReceive(List<StringMessage> messages) {
        for (StringMessage message : messages) {
            System.out.println("Receive: " + message.getStringValue());

            send(new StringMessage("Echo: " + message.getStringValue()));
        }
    }
}
