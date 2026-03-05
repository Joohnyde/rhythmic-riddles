package com.cevapinxile.cestereg.runtime.websocket;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class MdcWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

  public MdcWebSocketHandlerDecorator(org.springframework.web.socket.WebSocketHandler delegate) {
    super(delegate);
  }

  @Override
  public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
    withMdc(session, () -> super.afterConnectionEstablished(session));
  }

  @Override
  public void handleMessage(final WebSocketSession session, final WebSocketMessage<?> message)
      throws Exception {
    withMdc(session, () -> super.handleMessage(session, message));
  }

  @Override
  public void handleTransportError(final WebSocketSession session, final Throwable exception)
      throws Exception {
    withMdc(session, () -> super.handleTransportError(session, exception));
  }

  @Override
  public void afterConnectionClosed(final WebSocketSession session, final CloseStatus closeStatus)
      throws Exception {
    withMdc(session, () -> super.afterConnectionClosed(session, closeStatus));
  }

  private void withMdc(final WebSocketSession session, final ThrowingRunnable r) throws Exception {
    final Map<String, Object> a = session.getAttributes();
    final Object room = a.get("ROOM_CODE");
    final Object pos = a.get("SOCKET_POSITION");

    try {
      MDC.put("roomCode", room != null ? room.toString() : "-");
      MDC.put("sessionId", session.getId());
      MDC.put("clientType", pos == null ? "-" : ("0".equals(pos.toString()) ? "ADMIN" : "TV"));
      r.run();
    } finally {
      MDC.clear();
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
