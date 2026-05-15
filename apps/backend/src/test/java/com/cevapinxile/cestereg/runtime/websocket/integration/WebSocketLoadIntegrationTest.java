package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;

@Tag("ws-load")
@Tag("ws-nightly")
@DisplayName("WebSocket load and scaled burst behavior")
class WebSocketLoadIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  @Test
  void manyRoomsReceiveOnlyTheirOwnBurstFramesExactlyOnce() throws Exception {
    final int roomCount = 12;
    final int burstSize = 6;
    final List<String> roomCodes = new ArrayList<>();
    final Map<String, SocketProbe> admins = new HashMap<>();
    final Map<String, SocketProbe> tvs = new HashMap<>();

    for (int i = 0; i < roomCount; i++) {
      final String code = "L" + i + "AD";
      roomCodes.add(code);
      gamesByCode.put(code, game(code));
    }

    for (String roomCode : roomCodes) {
      final SocketProbe admin = connectAdmin(roomCode);
      final SocketProbe tv = connectTv(roomCode);
      assertContract(admin.readJson(), "welcome");
      assertContract(tv.readJson(), "welcome");
      admins.put(roomCode, admin);
      tvs.put(roomCode, tv);
    }

    for (String roomCode : roomCodes) {
      for (int i = 0; i < burstSize; i++) {
        broadcastGateway.broadcast(
            roomCode,
            "{\"type\":\"load_probe\",\"roomCode\":\"" + roomCode + "\",\"sequence\":" + i + "}");
      }
    }

    for (String roomCode : roomCodes) {
      assertLoadBurst(roomCode, burstSize, admins.get(roomCode));
      assertLoadBurst(roomCode, burstSize, tvs.get(roomCode));
      assertNull(admins.get(roomCode).pollFrame(250), "admin must not receive duplicate burst frames");
      assertNull(tvs.get(roomCode).pollFrame(250), "TV must not receive duplicate burst frames");
    }
  }

  @Test
  void largeSingleRoomBurstCanBeBufferedBySlowConsumersAndDrainedInOrder() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    final int burstSize = 40;

    for (int i = 0; i < burstSize; i++) {
      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"slow_consumer_probe\",\"sequence\":" + i + "}");
    }

    for (int i = 0; i < burstSize; i++) {
      assertEquals(i, ((Number) admin.readJson().get("sequence")).intValue());
      assertEquals(i, ((Number) tv.readJson().get("sequence")).intValue());
    }
    assertNull(admin.pollFrame(300));
    assertNull(tv.pollFrame(300));
  }

  @Test
  void burstAfterOneSideDisconnectsDoesNotBackPressureOrKillRemainingSide() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();
    tv.close(CloseStatus.GOING_AWAY);
    assertEventuallyFalse(() -> sessionRegistry.isTvPresent(ROOM_A));

    for (int i = 0; i < 20; i++) {
      broadcastGateway.broadcast(ROOM_A, "{\"type\":\"admin_survives_disconnect\",\"sequence\":" + i + "}");
    }

    for (int i = 0; i < 20; i++) {
      final Map<?, ?> frame = admin.readJson();
      assertEquals("admin_survives_disconnect", frame.get("type"));
      assertEquals(i, ((Number) frame.get("sequence")).intValue());
    }
    assertTrue(admin.isOpen());
  }

  @Test
  void independentRegistryInstanceDocumentsCurrentSingleNodeSessionStorage() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    assertContract(admin.readJson(), "welcome");

    final SessionRegistry independentNodeRegistry = new SessionRegistry();

    assertTrue(sessionRegistry.isAdminPresent(ROOM_A));
    assertFalse(
        independentNodeRegistry.isAdminPresent(ROOM_A),
        "current websocket sessions are in-memory and node-local; cross-node recovery needs explicit shared storage");
  }
}
