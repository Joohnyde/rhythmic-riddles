/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.broadcast;

import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 *
 * @author denijal
 */
@Component
public class WebSocketBroadcastGateway implements BroadcastGateway {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcastGateway.class);
    
    private final SessionRegistry registry;

    public WebSocketBroadcastGateway(SessionRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void broadcast(String code, String payload){
        toTv(code, payload);
        toAdmin(code, payload);
    }

    @Override
    public void toTv(String code, String payload) {
        WebSocketSession tvSession = registry.getTvSession(code);
        if(tvSession == null){
            log.warn("Tried sending this {} to non-existing TV", payload);
            return;
        }
        try {
            tvSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            log.warn("An IO exception occured", payload);
        }
    }

    @Override
    public void toAdmin(String code, String payload) {
        WebSocketSession adminSession = registry.getAdminSession(code);
        if(adminSession == null){
            log.warn("Tried sending this {} to non-existing Admin", payload);
            return;
        }
        try {
            adminSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            log.warn("An IO exception occured", payload);
        }
    }
    
    public void sendToSomeone(WebSocketSession socket, String payload){
        if(socket == null || !socket.isOpen()){
            log.warn("Tried sending this {} to non-existing socket", payload);
            return;
        }
        try {
            socket.sendMessage(new TextMessage(payload));
            log.info("Sent {} to {}", payload, socket.getUri());
        } catch (IOException ex) {
            log.error(null, ex);
        }
    }
}
