/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.websockets;

import com.cevapinxile.cestereg.exceptions.DerivedException;
import com.cevapinxile.cestereg.interfaces.interfaces.IgraInterface;
import com.cevapinxile.cestereg.interfaces.interfaces.OdgovorInterface;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 *
 * @author denijal
 */
public class WebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry registry;
    private final Broadcaster broadcaster;

    private final IgraInterface igra_interface;
    private final OdgovorInterface odgovor_interface;
    
    public WebSocketHandler(SessionRegistry registry, IgraInterface igra_interface, OdgovorInterface odgovor_interface) {
        this.registry = registry;
        this.broadcaster = new Broadcaster(registry);
        this.igra_interface = igra_interface;
        this.odgovor_interface = odgovor_interface;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if(registry.setSession(session)){
            String room_code = session.getAttributes().get("ROOM_CODE").toString();
            HashMap<String, Object> context = igra_interface.contextFetch(room_code);
            this.broadcaster.setToSomeone(session, new ObjectMapper().writeValueAsString(context));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

       
        Integer socket_position = (Integer) session.getAttributes().get("SOCKET_POSITION");
        String room_code = session.getAttributes().get("ROOM_CODE").toString();
        
        //Pauza - Broadcastuj da se pauzira
        
         /*
            Angularovi socketi se gase kad se promeni strana. To je implicitno i ne moze da se gasi.
        Tada je status.code = 1000. Kad se refreshulje strana ili ugasi browser onda je code 1001.
        Nisam siguran da li je ovaj uslov sveobuhvatan ali radi za sad.
        */
        if(registry.removeSession(session, socket_position, room_code) && this.igra_interface.getState(room_code) == 2 && status.getCode() != 1000){
            try {
                odgovor_interface.interrupt(room_code, null);
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
