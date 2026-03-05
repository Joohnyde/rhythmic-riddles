/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.broadcast;

import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/*
 * @author denijal
 */
@Component
public class WebSocketBroadcastGateway implements BroadcastGateway {

  private static final Logger LOG = LoggerFactory.getLogger(WebSocketBroadcastGateway.class);

  private final SessionRegistry registry;

  public WebSocketBroadcastGateway(final SessionRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void broadcast(final String code, final String payload) {
    toTv(code, payload);
    toAdmin(code, payload);
  }

  @Override
  public void toTv(final String code, final String payload) {
    final WebSocketSession tvSession = registry.getTvSession(code);
    if (tvSession == null) {
      LOG.warn("Tried sending this {} to non-existing TV", payload);
      return;
    }
    try {
      tvSession.sendMessage(new TextMessage(payload));
    } catch (IOException ex) {
      LOG.warn("An IO exception occured", payload);
    }
  }

  @Override
  public void toAdmin(final String code, final String payload) {
    final WebSocketSession adminSession = registry.getAdminSession(code);
    if (adminSession == null) {
      LOG.warn("Tried sending this {} to non-existing Admin", payload);
      return;
    }
    try {
      adminSession.sendMessage(new TextMessage(payload));
    } catch (IOException ex) {
      LOG.warn("An IO exception occured", payload);
    }
  }

  public void sendToSomeone(final WebSocketSession socket, final String payload) {
    if (socket == null || !socket.isOpen()) {
      LOG.warn("Tried sending this {} to non-existing socket", payload);
      return;
    }
    try {
      socket.sendMessage(new TextMessage(payload));
      LOG.info("Sent {} to {}", payload, socket.getUri());
    } catch (IOException ex) {
      LOG.error(null, ex);
    }
  }
}
