/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

import com.cevapinxile.cestereg.runtime.broadcast.WebSocketBroadcastGateway;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;

/**
 *
 * @author denijal
 */
public class WebSocketHandler extends TextWebSocketHandler {

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if(sessionRegistry.setSession(session)){
            String roomCode = session.getAttributes().get("ROOM_CODE").toString();
            HashMap<String, Object> context = gameService.contextFetch(roomCode);
            broadcastGateway.sendToSomeone(session, new ObjectMapper().writeValueAsString(context));
        }
    }

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
                Logger.getLogger(WebSocketHandler.class.getName()).log(Level.INFO, "How do I return this?", ex);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        //Nista ovo
    }
}
