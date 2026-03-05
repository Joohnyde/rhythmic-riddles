/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

import org.springframework.web.socket.WebSocketSession;

/*
 * @author denijal
 */
public record RoomSessions(WebSocketSession admin, WebSocketSession tv) {

  public WebSocketSession get(final ClientType type) {
    return type == ClientType.ADMIN ? admin : tv;
  }

  public RoomSessions with(final ClientType type, final WebSocketSession session) {
    return type == ClientType.ADMIN
        ? new RoomSessions(session, tv)
        : new RoomSessions(admin, session);
  }

  public boolean isPresent(final ClientType type) {
    final WebSocketSession s = get(type);
    return s != null && s.isOpen();
  }
}
