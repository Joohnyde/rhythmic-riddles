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
 * @author denijal
 * Extracts room code and client slot information from the WebSocket handshake URL.
 *
 * <p>Expected format: {@code /ws/{pos}{roomCode}}, where {@code pos} is a single digit representing the
 * client type/slot (e.g., 0=admin, 1=tv). This keeps the handshake free of query parameters and ensures
 * routing is explicit.</p>
 *
 * <p>If extraction fails (missing/invalid slot or room code), the caller should treat the session as
 * unauthenticated and close it.</p>
 */

@Component
public class GameCodeExtractor implements HandshakeInterceptor {

    @Autowired
    private GameRepository gameRepository;

    /**
    * Intercepts the WebSocket handshake and validates the incoming connection.
    *
    * <p>Expected URL format: {@code /ws/{pos}{roomCode}}, where {@code pos} is a single digit identifying
    * the client type/slot (e.g., 0 = ADMIN, 1 = TV).</p>
    *
    * <p>On successful validation, this method stores handshake-derived attributes on the session:
    * <ul>
    *   <li>{@code ROOM_CODE} - the room identifier (without the prefix digit)</li>
    *   <li>{@code SOCKET_POSITION} - the client slot/type (0 or 1)</li>
    * </ul>
    * These attributes are later used by the WebSocket handler to route messages and clean up sessions.</p>
    *
    * <p>The handshake is rejected ({@code false}) if the room code is unknown or the slot prefix is invalid.</p>
    *
    * @param request incoming handshake request
    * @param response handshake response
    * @param wsHandler WebSocket handler associated with the endpoint
    * @param attributes map of attributes to attach to the WebSocket session
    * @return {@code true} to accept the handshake; {@code false} to reject it
    */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
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
