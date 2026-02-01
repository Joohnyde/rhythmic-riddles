package com.cevapinxile.cestereg.runtime.websocket;

import org.slf4j.MDC;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.Map;

public class MdcWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    public MdcWebSocketHandlerDecorator(org.springframework.web.socket.WebSocketHandler delegate) {
        super(delegate);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        withMdc(session, () -> super.afterConnectionEstablished(session));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        withMdc(session, () -> super.handleMessage(session, message));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        withMdc(session, () -> super.handleTransportError(session, exception));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        withMdc(session, () -> super.afterConnectionClosed(session, closeStatus));
    }

    private void withMdc(WebSocketSession session, ThrowingRunnable r) throws Exception {
        Map<String, Object> a = session.getAttributes();
        Object room = a.get("ROOM_CODE");
        Object pos  = a.get("SOCKET_POSITION");

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
