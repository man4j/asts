package com.n1global.asts;
import java.util.List;

import javax.net.ssl.SSLException;

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

    public void onClosedByPeer(@SuppressWarnings("unused") Exception e) {/* empty */}

    public void onReceive(@SuppressWarnings("unused") List<T> messages) {/* empty */}

    public void onIdle() {/* empty */}

    public void onTimeout() {/* empty */}
    
    public void send(T msg) {
        getEndpointContext().getSender().send(msg);
    }
    
    @SuppressWarnings("unchecked")
    public void closeConnection() {
        try {
            getEndpointContext().setCloseRequested(true);
            send((T) new ByteMessage());
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    void closedByPeer(Exception ex) {
        try {
            onClosedByPeer(ex);
        } catch (Exception e) {
            logger.error("", e);
        }
        
        try {
            getEndpointContext().getSslEngine().closeInbound();
        } catch (SSLException e) {
            logger.error("", e);
        }
        
        closeContext();
    }
    
    void closeContext() {
        getEndpointContext().destroyBuffers();
        
        if (getEndpointContext().getSelectionKey().isValid()) {
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
}
