/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;

/**
 *
 * @author denijal
 */
@Component
public class GameCodeExtractor implements HandshakeInterceptor {

    @Autowired
    private GameRepository gameRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // Get the URI segment corresponding to the auction id during handshake
        String path = request.getURI().getPath();
        String roomCode = path.substring(path.lastIndexOf('/') + 1);

        /* URL encodes both client type and room code in a single path segment:
           /ws/{pos}{roomCode}  where pos=0 (ADMIN) or pos=1 (TV)
            This keeps handshake simple (no query params / headers).*/
        Integer socketPosition = (int) roomCode.charAt(0) - '0';
        roomCode = roomCode.substring(1);

        /* Reject handshake unless the game exists AND the client-type prefix is valid (0/1).
           Prevents creating sessions for random room codes.*/
        if (gameRepository.findByCode(roomCode).isEmpty() || (socketPosition != 0 && socketPosition != 1)) {
            return false;
        }

        // This will be added to the websocket session
        attributes.put("ROOM_CODE", roomCode);
        attributes.put("SOCKET_POSITION", socketPosition);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // Can be ignored!
    }

}
