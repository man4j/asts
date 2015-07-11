package com.n1global.asts.test.lv;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.n1global.asts.AbstractEventHandler;
import com.n1global.asts.message.StringMessage;

public class ServerHandler extends AbstractEventHandler<StringMessage> {
    private LongAdder adder = new LongAdder();
    
    public ServerHandler() {
        new Thread() {
            @Override
            public void run() {
                int prev = 0;
                while (true) {
                    try {
                        int current = adder.intValue();
                        
                        System.out.println(current - prev);
                        
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
    public void onReceive(List<StringMessage> messages) {
        adder.increment();
        
        for (StringMessage message : messages) {
            send(new StringMessage(message.getStringValue()));
        }
    }
}
