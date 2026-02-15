/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

import com.cevapinxile.cestereg.runtime.broadcast.WebSocketBroadcastGateway;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.HashMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import java.sql.SQLOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author denijal
 * WebSocket entry point for the quiz runtime.
 *
 * <p>Handles the connection lifecycle (connect / message / close) and routes events to the
 * correct game room using handshake-derived identifiers.</p>
 *
 * <p>Client type (e.g., admin vs TV) is encoded in the WebSocket URL and extracted during the handshake.
 * Disconnect handling is defensive because SPAs (Angular) may close sockets on navigation with a
 * normal close code, while refresh / browser close typically yields a different close code.</p>
 *
 * <p>This handler is responsible for best-effort cleanup to prevent leaking session state and to
 * ensure the game can recover after reconnects.</p>
 */

public class WebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    
    private final SessionRegistry sessionRegistry;
    private final WebSocketBroadcastGateway broadcastGateway;

    private final GameService gameService;
    private final InterruptService interruptService;
    
    public WebSocketHandler(SessionRegistry sessionRegistry, GameService gameService, InterruptService interruptService) {
        this.sessionRegistry = sessionRegistry;
        this.broadcastGateway = new WebSocketBroadcastGateway(sessionRegistry);
        this.gameService = gameService;
        this.interruptService = interruptService;
    }

    /**
    * Registers a newly established WebSocket session.
    *
    * <p>On success, the session is stored in the {@code SessionRegistry} under a key derived from the handshake.
    * On failure (invalid handshake, unknown room, etc.), the session should be closed and ignored.</p>
    *
    * @param session active WebSocket session
    * @throws Exception if the underlying WebSocket framework requires propagation
    */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if(sessionRegistry.setSession(session)){
            String roomCode = session.getAttributes().get("ROOM_CODE").toString();
            HashMap<String, Object> context = gameService.contextFetch(roomCode);
            System.out.println(context);
            broadcastGateway.sendToSomeone(session, new ObjectMapper().writeValueAsString(context));
        }
    }

    /**
     * Cleans up session state when a WebSocket connection is closed.
     *
     * <p>Close events can occur due to "normal" SPA navigation, refreshes, browser closes, or network drops.
     * Cleanup is guarded to avoid accidental removal of a newer session in case of reconnects.</p>
     *
     * @param session closed WebSocket session
     * @param status close status/code
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        // Sanity checks - Can't happen regularly
        if(session == null || session.getAttributes() == null || !session.getAttributes().containsKey("ROOM_CODE")) return;
        String roomCode = session.getAttributes().get("ROOM_CODE").toString();
        
        /* Angular closes the WebSocket on route changes (status 1000 = normal closure).
           Page refresh or browser close results in status 1001.
           We use this difference to avoid treating navigation as a real disconnect.
           Not exhaustive, but works with current frontend behavior. */
        if(sessionRegistry.removeSession(session) && this.gameService.getStage(roomCode) == 2 && status.getCode() != 1000){
            try {
                interruptService.interrupt(roomCode, null);
            } catch (DerivedException ex) {
                log.info("How do I return this?", ex);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        //Nista ovo
    }
}
