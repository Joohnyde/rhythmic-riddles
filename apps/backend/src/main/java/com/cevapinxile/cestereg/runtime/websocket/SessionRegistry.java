/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.runtime.websocket;

import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 *
 * @author denijal
 *
 * Thread-safe session store. compute(...) keeps "check + update" atomic per
 * key, and identity checks prevent stale close events from removing a freshly
 * reconnected session.
 */
@Component
public class SessionRegistry implements PresenceGateway {

    private static final Logger log = Logger.getLogger(SessionRegistry.class.getName());

    private final ConcurrentHashMap<String, RoomSessions> sessionsByRoom = new ConcurrentHashMap<>();

    @Override
    public boolean isTvPresent(String roomCode) {
        RoomSessions rs = sessionsByRoom.get(roomCode);
        return rs != null && rs.isPresent(ClientType.TV);
    }

    @Override
    public boolean isAdminPresent(String roomCode) {
        RoomSessions rs = sessionsByRoom.get(roomCode);
        return rs != null && rs.isPresent(ClientType.ADMIN);
    }

    @Override
    public boolean areBothPresent(String roomCode) {
        RoomSessions rs = sessionsByRoom.get(roomCode);
        return rs != null && rs.isPresent(ClientType.ADMIN) && rs.isPresent(ClientType.TV);
    }

    public boolean setSession(WebSocketSession session) {
        SessionKey key = extractSessionKey(session);
        if (key == null) {
            return false;
        }

        String roomCode = key.roomCode();
        ClientType type = key.type();

        /* We update the registry ATOMICALLY via ConcurrentHashMap.compute(...).
           Because lambdas can only capture effectively-final vars, we use an AtomicBoolean
           as a mutable "out param" to return whether the new session was accepted.*/
        AtomicBoolean accepted = new AtomicBoolean(false);

        sessionsByRoom.compute(roomCode, (code, existing) -> {
            RoomSessions current = (existing == null) ? new RoomSessions(null, null) : existing;

            WebSocketSession already = current.get(type);
            /* The decision below is the single source of truth for session replacement rules.
               It must be made *inside* compute(...) so "check + update" is atomic under concurrency.*/
            if (already != null && already.isOpen()) {
                accepted.set(true);
                log.log(Level.INFO, "Socket {0} for room {1} already exists; rejecting new session.", new Object[]{type, roomCode});
                return current; // keep old one
            }

            log.log(Level.INFO, "Connected socket {0} for room {1}.", new Object[]{type, roomCode});
            return current.with(type, session);
        });

        boolean wasAccepted = accepted.get();
        if (!wasAccepted) {
            closeQuietly(session);
        }
        return wasAccepted;
    }

    public WebSocketSession getAdminSession(String roomCode) {
        RoomSessions rs = sessionsByRoom.get(roomCode);
        return rs == null ? null : rs.admin();
    }

    public WebSocketSession getTvSession(String roomCode) {
        RoomSessions rs = sessionsByRoom.get(roomCode);
        return rs == null ? null : rs.tv();
    }

    public boolean removeSession(WebSocketSession session) {
        SessionKey key = extractSessionKey(session);
        if (key == null) {
            return false;
        }

        String roomCode = key.roomCode();
        ClientType type = key.type();

        AtomicBoolean removed = new AtomicBoolean(false);
        sessionsByRoom.computeIfPresent(roomCode, (code, existing) -> {
            WebSocketSession current = existing.get(type);
            /* Close events can be delayed. If the client reconnected, the registry may already contain
               a newer session. Only remove/update when the stored session is *this exact instance*.*/
            if (current != session) {
                return existing;
            }
            removed.set(true);
            log.log(Level.INFO, "Removed socket {0} for room {1}.", new Object[]{type, roomCode});
            return existing.with(type, null);
        });

        return removed.get();
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
        }
    }

    private static record SessionKey(String roomCode, ClientType type) {

    }

    private SessionKey extractSessionKey(WebSocketSession session) {
        /* Handshake is expected to inject ROOM_CODE + SOCKET_POSITION.
           If they're missing/invalid, we treat the session as unauthenticated and close it.*/
        if (session == null) {
            return null;
        }

        Object posObj = session.getAttributes().get("SOCKET_POSITION");
        Object codeObj = session.getAttributes().get("ROOM_CODE");

        if (!(posObj instanceof Integer) || codeObj == null) {
            log.log(Level.WARNING, "Missing or invalid SOCKET_POSITION / ROOM_CODE. Closing session.");
            closeQuietly(session);
            return null;
        }

        ClientType type;
        try {
            type = ClientType.fromSocketPosition((Integer) posObj);
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, "Invalid SOCKET_POSITION {0}. Closing session.", posObj);
            closeQuietly(session);
            return null;
        }

        return new SessionKey(codeObj.toString(), type);
    }
}
