package com.n1global.asts.test.websockets;

import java.util.concurrent.ExecutionException;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketUpgradeHandler;

public class TestWebSocketClient {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        AsyncHttpClient c = new AsyncHttpClient();

        WebSocket webSocket = c.prepareGet("ws://127.0.0.1:9999").execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {
            @Override
            public void onMessage(String message) {
                System.out.println(message);
            }
        }).build()).get();

        webSocket.sendMessage("Hello!");

        Thread.sleep(1000);

        webSocket.close();

        c.close();
    }
}
