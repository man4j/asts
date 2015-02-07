package com.n1global.asts;

import java.nio.channels.SelectionKey;

import com.n1global.asts.message.Message;
import com.n1global.asts.protocol.AbstractFrameProtocol;
import com.n1global.asts.util.CustomLinkedList.Node;

public class EndpointContext<T extends Message> {
    private AbstractEventHandler<T> eventHandler;

    private AbstractFrameProtocol<T> protocol;

    private SelectionKey selectionKey;

    private MessageSender<T> sender;

    private long lastRecv;

    private long lastSend;

    private Node<EndpointContext<Message>> readNode;

    private Node<EndpointContext<Message>> idleNode;

    private Node<EndpointContext<Message>> writeNode;

    private Object payload;

    public MessageSender<T> getSender() {
        return sender;
    }

    void setSender(MessageSender<T> sender) {
        this.sender = sender;
    }

    AbstractEventHandler<T> getEventHandler() {
        return eventHandler;
    }

    void setEventHandler(AbstractEventHandler<T> eventHandler) {
        this.eventHandler = eventHandler;

        eventHandler.setEndpointContext(this);
    }

    AbstractFrameProtocol<T> getProtocol() {
        return protocol;
    }

    void setProtocol(AbstractFrameProtocol<T> protocol) {
        this.protocol = protocol;
    }

    SelectionKey getSelectionKey() {
        return selectionKey;
    }

    void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    long getLastRecv() {
        return lastRecv;
    }

    void setLastRecv(long lastRecv) {
        this.lastRecv = lastRecv;
    }

    long getLastSend() {
        return lastSend;
    }

    void setLastSend(long lastSend) {
        this.lastSend = lastSend;
    }

    Node<EndpointContext<Message>> getReadNode() {
        return readNode;
    }

    void setReadNode(Node<EndpointContext<Message>> readNode) {
        this.readNode = readNode;
    }

    Node<EndpointContext<Message>> getIdleNode() {
        return idleNode;
    }

    void setIdleNode(Node<EndpointContext<Message>> idleNode) {
        this.idleNode = idleNode;
    }

    Node<EndpointContext<Message>> getWriteNode() {
        return writeNode;
    }

    void setWriteNode(Node<EndpointContext<Message>> writeNode) {
        this.writeNode = writeNode;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
