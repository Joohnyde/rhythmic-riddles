/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.websockets;

import com.cevapinxile.cestereg.entities.Igra;
import com.cevapinxile.cestereg.interfaces.repositories.IgraRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 *
 * @author denijal
 */
@Component
public class GameCodeExtractor implements HandshakeInterceptor {
    
    private final IgraRepository ir;

    @Autowired
    public GameCodeExtractor(IgraRepository ir) {
        this.ir = ir;
    }
    
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // Get the URI segment corresponding to the auction id during handshake
        String path = request.getURI().getPath();
        String room_code = path.substring(path.lastIndexOf('/') + 1);
        
        Integer socket_pos = (int) room_code.charAt(0) - '0';
        room_code = room_code.substring(1);
        
        //Sanity checks
        if(ir.findByKod(room_code).isEmpty() || (socket_pos != 0 && socket_pos != 1)) return false;
        
        // This will be added to the websocket session
        attributes.put("ROOM_CODE", room_code);
        attributes.put("SOCKET_POSITION", socket_pos);
        
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        //Nista ovo
    }

}
