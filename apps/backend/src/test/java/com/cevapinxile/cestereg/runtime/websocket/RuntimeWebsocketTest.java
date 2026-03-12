package com.cevapinxile.cestereg.runtime.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

/**
 * @author denijal
 */
public class RuntimeWebsocketTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class ClientTypeTest {

    @Test
    void indexReturnsExpectedSocketPositions() {
      assertEquals(0, ClientType.ADMIN.index());
      assertEquals(1, ClientType.TV.index());
    }

    @Test
    void fromSocketPositionMapsKnownValues() {
      assertEquals(ClientType.ADMIN, ClientType.fromSocketPosition(0));
      assertEquals(ClientType.TV, ClientType.fromSocketPosition(1));
    }

    @Test
    void fromSocketPositionRejectsUnknownValues() {
      final IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class, () -> ClientType.fromSocketPosition(2));

      assertEquals("Invalid SOCKET_POSITION: 2", exception.getMessage());
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class GameCodeExtractorTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameCodeExtractor extractor = new GameCodeExtractor();

    @BeforeEach
    void setUp() {
      ReflectionTestUtils.setField(extractor, "gameRepository", gameRepository);
    }

    @Test
    void beforeHandshakeAcceptsKnownRoomAndStoresAttributes() {
      when(gameRepository.findByCode("AKKU"))
          .thenReturn(Optional.of(new GameEntity(UUID.randomUUID())));

      final Map<String, Object> attributes = new HashMap<>();
      final boolean accepted =
          extractor.beforeHandshake(
              request("ws://localhost/ws/0AKKU"),
              mock(ServerHttpResponse.class),
              mock(WebSocketHandler.class),
              attributes);

      assertTrue(accepted);
      assertEquals("AKKU", attributes.get("ROOM_CODE"));
      assertEquals(0, attributes.get("SOCKET_POSITION"));
    }

    @Test
    void beforeHandshakeRejectsUnknownRoom() {
      when(gameRepository.findByCode("MISS")).thenReturn(Optional.empty());

      final boolean accepted =
          extractor.beforeHandshake(
              request("ws://localhost/ws/1MISS"),
              mock(ServerHttpResponse.class),
              mock(WebSocketHandler.class),
              new HashMap<>());

      assertFalse(accepted);
    }

    @Test
    void beforeHandshakeRejectsUnsupportedSocketPosition() {
      when(gameRepository.findByCode("AKKU"))
          .thenReturn(Optional.of(new GameEntity(UUID.randomUUID())));

      final boolean accepted =
          extractor.beforeHandshake(
              request("ws://localhost/ws/2AKKU"),
              mock(ServerHttpResponse.class),
              mock(WebSocketHandler.class),
              new HashMap<>());

      assertFalse(accepted);
    }

    private ServerHttpRequest request(final String uri) {
      final ServerHttpRequest request = mock(ServerHttpRequest.class);
      when(request.getURI()).thenReturn(URI.create(uri));
      return request;
    }
  }
}
