/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.broadcast;

import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 *
 * @author denijal
 */
@Component
public class WebSocketBroadcastGateway implements BroadcastGateway {

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
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing TV", payload);
            return;
        }
        try {
            tvSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.WARNING, null, payload);
        }
    }

    @Override
    public void toAdmin(String code, String payload) {
        WebSocketSession adminSession = registry.getAdminSession(code);
        if(adminSession == null){
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing Admin", payload);
            return;
        }
        try {
            adminSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.WARNING, null, payload);
        }
    }
    
    public void sendToSomeone(WebSocketSession socket, String payload){
        if(socket == null || !socket.isOpen()){
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing socket", payload);
            return;
        }
        try {
            socket.sendMessage(new TextMessage(payload));
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.INFO, "Sent {0} to {1}", new String[]{payload,socket.getUri().toString()});
        } catch (IOException ex) {
            Logger.getLogger(WebSocketBroadcastGateway.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
