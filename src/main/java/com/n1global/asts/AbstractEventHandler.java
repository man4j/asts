package com.n1global.asts;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.n1global.asts.message.ByteMessage;

public abstract class AbstractEventHandler<T extends ByteMessage> {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private EndpointContext<T> endpointContext;

    public EndpointContext<T> getEndpointContext() {
        return endpointContext;
    }

    void setEndpointContext(EndpointContext<T> endpointContext) {
        this.endpointContext = endpointContext;
    }

    public void onConnect() {/* empty */}

    public void onDisconnect(@SuppressWarnings("unused") Exception e) {/* empty */}

    public void onReceive(@SuppressWarnings("unused") List<T> messages) {/* empty */}

    public void onIdle() {/* empty */}

    public void onTimeout() {/* empty */}

    public void closeConnection() {
        closeConnection(null);
    }

    public void closeConnection(Exception ex) {
        getEndpointContext().getProtocol().destroyBuffers();
        
        if (getEndpointContext().getSelectionKey().isValid()) {
            try {
                onDisconnect(ex);
            } catch (Exception e) {
                logger.error("", e);
            }

            try {
                getEndpointContext().getSelectionKey().channel().close();
            } catch (Exception e) {
                logger.error("", e);
            }
            
            try {
                getEndpointContext().getSelectionKey().cancel();
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    }

    public void send(T msg) {
        getEndpointContext().getSender().send(msg);
    }
}
