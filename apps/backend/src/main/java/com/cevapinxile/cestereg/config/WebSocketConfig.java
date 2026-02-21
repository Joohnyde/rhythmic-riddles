/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.config;

import com.cevapinxile.cestereg.runtime.websocket.GameCodeExtractor;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import com.cevapinxile.cestereg.runtime.websocket.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.runtime.websocket.MdcWebSocketHandlerDecorator;
import org.springframework.context.annotation.Bean;

/**
 *
 * @author denijal
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionRegistry sessionRegistry;
    private final GameCodeExtractor gameCodeExtractor;
    private final GameService gameService;
    private final InterruptService interruptService;

    public WebSocketConfig(SessionRegistry sessionRegistry,
                           GameCodeExtractor gameCodeExtractor,
                           GameService gameService,
                           InterruptService interruptService) {
        this.sessionRegistry = sessionRegistry;
        this.gameCodeExtractor = gameCodeExtractor;
        this.gameService = gameService;
        this.interruptService = interruptService;
    }

    @Bean
    public org.springframework.web.socket.WebSocketHandler quizWebSocketHandler() {
        // Your existing handler (unchanged)
        WebSocketHandler handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);

        // Wrap with MDC so every WS callback automatically has MDC fields
        return new MdcWebSocketHandlerDecorator(handler);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(quizWebSocketHandler(), "/ws/*")
                .addInterceptors(gameCodeExtractor)
                .setAllowedOrigins("*");
    }
}