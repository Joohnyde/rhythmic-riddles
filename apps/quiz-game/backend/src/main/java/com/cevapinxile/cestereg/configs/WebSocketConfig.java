/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.configs;

import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.OdgovorInterface;
import com.cevapinxile.cestereg.websockets.GameCodeExtractor;
import com.cevapinxile.cestereg.websockets.SessionRegistry;
import com.cevapinxile.cestereg.websockets.WebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
    private IgraInterface igraInterface;
    
    @Autowired
    private OdgovorInterface odgovor_interface;

    public WebSocketConfig(SessionRegistry sessionRegistry, GameCodeExtractor gameCodeExtractor) {
        this.sessionRegistry = sessionRegistry;
        this.gameCodeExtractor = gameCodeExtractor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new WebSocketHandler(sessionRegistry, igraInterface, odgovor_interface), "/ws/*")
                .addInterceptors(gameCodeExtractor)
                .setAllowedOrigins("*");
    }
}