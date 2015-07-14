package com.n1global.asts.test.lv;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.n1global.asts.AbstractEventHandler;
import com.n1global.asts.message.StringMessage;

public class ServerHandler extends AbstractEventHandler<StringMessage> {
    private static LongAdder messageAdder = new LongAdder();
    
    private static LongAdder clientAdder = new LongAdder();

    static {
        new Thread() {
            @Override
            public void run() {
                int prev = 0;
                while (true) {
                    try {
                        int current = messageAdder.intValue();
                        
                        System.out.println("Total messages: " + (current - prev) + " per 5 sec");
                        System.out.println("Connected clients: " + clientAdder.intValue());
                        
                        prev = current;
                        
                        sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }.start();
    }
    
    @Override
    public void onConnect() {
        clientAdder.increment();
    }
    
    @Override
    public void onDisconnect(Exception e) {
        clientAdder.decrement();
    }
    
    @Override
    public void onReceive(List<StringMessage> messages) {
        messageAdder.increment();
        
        for (StringMessage message : messages) {
            send(new StringMessage(message.getStringValue()));
        }
    }
}
