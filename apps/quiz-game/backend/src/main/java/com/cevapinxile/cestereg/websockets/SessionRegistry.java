/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.websockets;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 *
 * @author denijal
 */
@Component
public class SessionRegistry {
    
    private volatile ConcurrentHashMap<String, WebSocketSession[]> sockets = new ConcurrentHashMap<>();
    
    private boolean isPresent(String code, int id){
        if(!sockets.containsKey(code)) return false;
        WebSocketSession[] get = sockets.get(code);
        if(get.length != 2) return false;
        return get[id] != null;
    }
    
    public synchronized boolean isTvPresent(String code){
        return isPresent(code, 1);
    }
    
    public synchronized boolean isAdminPresent(String code){
        return isPresent(code, 0);
    }
    
    public synchronized boolean areBothPresent(String code){
        return isPresent(code, 1) && isPresent(code, 0);
    }
    
    public synchronized boolean setSession(WebSocketSession session){
        if(session == null) return false;
        Integer socket_position = (Integer) session.getAttributes().get("SOCKET_POSITION");
        String room_code = session.getAttributes().get("ROOM_CODE").toString();
        sockets.putIfAbsent(room_code, new WebSocketSession[2]); //Id 0 = admin
        WebSocketSession[] game_sockets = sockets.get(room_code);
        if(game_sockets[socket_position] != null && game_sockets[socket_position].isOpen()){
            Logger.getLogger(SessionRegistry.class.getName()).log(Level.INFO, "Socket {0} for room code {1} already exists", new Object[]{socket_position, room_code});
            try {
                session.close();
            } catch (IOException ex) {
                Logger.getLogger(SessionRegistry.class.getName()).log(Level.SEVERE, null, ex);
            }
            return false;
        }
        game_sockets[socket_position] = session;
        Logger.getLogger(SessionRegistry.class.getName()).log(Level.INFO, "Connected socket {0} for room code {1} ", new Object[]{socket_position, room_code});
        return true;
    }
    
    public WebSocketSession getAdminSession(String code) {
        return sockets.getOrDefault(code, new WebSocketSession[2])[0];
    }

    public WebSocketSession getTvSession(String code) {
        return sockets.getOrDefault(code, new WebSocketSession[2])[1];
    }

    public synchronized boolean removeSession(WebSocketSession session, Integer socket_position,  String room_code) {
        if(sockets.get(room_code)[socket_position] != session)
            return false;
        sockets.get(room_code)[socket_position] = null;
        Logger.getLogger(SessionRegistry.class.getName()).log(Level.INFO, "Removed socket {0} for room code {1} ", new Object[]{socket_position, room_code});
        return true;
    }
}