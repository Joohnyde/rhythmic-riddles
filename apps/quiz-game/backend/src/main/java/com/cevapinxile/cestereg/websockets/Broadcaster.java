/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.websockets;

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
public class Broadcaster {

    private final SessionRegistry registry;

    public Broadcaster(SessionRegistry registry) {
        this.registry = registry;
    }
    
    public void broadcast(String code, String payload){
        sendToTv(code, payload);
        sendToAdmin(code, payload);
    }

    public void sendToTv(String code, String payload) {
        WebSocketSession tvSession = registry.getTvSession(code);
        if(tvSession == null){
            Logger.getLogger(Broadcaster.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing TV", payload);
            return;
        }
        try {
            tvSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            Logger.getLogger(Broadcaster.class.getName()).log(Level.WARNING, null, payload);
        }
    }

    public void sendToAdmin(String code, String payload) {
        WebSocketSession adminSession = registry.getAdminSession(code);
        if(adminSession == null){
            Logger.getLogger(Broadcaster.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing Admin", payload);
            return;
        }
        try {
            adminSession.sendMessage(new TextMessage(payload));
        } catch (IOException ex) {
            Logger.getLogger(Broadcaster.class.getName()).log(Level.WARNING, null, payload);
        }
    }
    
    public void setToSomeone(WebSocketSession socket, String payload){
        if(socket == null || !socket.isOpen()){
            Logger.getLogger(Broadcaster.class.getName()).log(Level.WARNING, "Tried sending this {0} to non-existing socket", payload);
            return;
        }
        try {
            socket.sendMessage(new TextMessage(payload));
            Logger.getLogger(Broadcaster.class.getName()).log(Level.INFO, "Sent {0} to {1}", new String[]{payload,socket.getUri().toString()});
        } catch (IOException ex) {
            Logger.getLogger(Broadcaster.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
