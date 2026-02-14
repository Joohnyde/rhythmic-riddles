/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.config;

import com.cevapinxile.cestereg.runtime.websocket.GameCodeExtractor;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import com.cevapinxile.cestereg.runtime.websocket.WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;

/**
 *
 * @author denijal
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionRegistry sessionRegistry;
    private final GameCodeExtractor gameCodeExtractor;
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private InterruptService interruptService;

    public WebSocketConfig(SessionRegistry sessionRegistry, GameCodeExtractor gameCodeExtractor) {
        this.sessionRegistry = sessionRegistry;
        this.gameCodeExtractor = gameCodeExtractor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new WebSocketHandler(sessionRegistry, gameService, interruptService), "/ws/*")
                .addInterceptors(gameCodeExtractor)
                .setAllowedOrigins("*");
    }
}