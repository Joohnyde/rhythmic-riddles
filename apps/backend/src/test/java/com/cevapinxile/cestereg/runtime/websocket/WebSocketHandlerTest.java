package com.cevapinxile.cestereg.runtime.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WebSocketHandlerTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerIteration7ReconnectTest {
    @Test
    void reconnectAfterInterruptResolutionSendsRecoveredPlaybackWelcomeOnlyToConnectingSocket()
        throws Exception {
      final SessionRegistry registry = mock(SessionRegistry.class);
      final GameService gameService = mock(GameService.class);
      final InterruptService interruptService = mock(InterruptService.class);
      final WebSocketHandler handler =
          new WebSocketHandler(registry, gameService, interruptService);
      final WebSocketSession session = session("ws://localhost/ws/0AKKU", "AKKU", 0, true);
      final HashMap<String, Object> snapshot =
          new HashMap<>(
              Map.of("type", "welcome", "stage", "songs", "seek", 8.0, "remaining", 12.0));

      when(registry.setSession(session)).thenReturn(true);
      when(gameService.contextFetch("AKKU")).thenReturn(snapshot);

      handler.afterConnectionEstablished(session);

      final ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
      verify(session).sendMessage(message.capture());
      verify(gameService).contextFetch("AKKU");
      final Map<?, ?> json =
          new ObjectMapper().readValue(message.getValue().getPayload(), HashMap.class);
      assertEquals("welcome", json.get("type"));
      assertEquals("songs", json.get("stage"));
      assertEquals(8.0, ((Number) json.get("seek")).doubleValue());
      assertEquals(12.0, ((Number) json.get("remaining")).doubleValue());
    }

    @Test
    void reconnectDuringTechnicalPauseSendsErrorRecoveryWelcomeOnlyToConnectingSocket()
        throws Exception {
      final SessionRegistry registry = mock(SessionRegistry.class);
      final GameService gameService = mock(GameService.class);
      final InterruptService interruptService = mock(InterruptService.class);
      final WebSocketHandler handler =
          new WebSocketHandler(registry, gameService, interruptService);
      final WebSocketSession session = session("ws://localhost/ws/1AKKU", "AKKU", 1, true);
      final HashMap<String, Object> snapshot =
          new HashMap<>(
              Map.of(
                  "type",
                  "welcome",
                  "stage",
                  "songs",
                  "error",
                  true,
                  "seek",
                  3.0,
                  "remaining",
                  20.0));

      when(registry.setSession(session)).thenReturn(true);
      when(gameService.contextFetch("AKKU")).thenReturn(snapshot);

      handler.afterConnectionEstablished(session);

      final ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
      verify(session).sendMessage(message.capture());
      final Map<?, ?> json =
          new ObjectMapper().readValue(message.getValue().getPayload(), HashMap.class);
      assertEquals(Boolean.TRUE, json.get("error"));
      assertEquals(3.0, ((Number) json.get("seek")).doubleValue());
      assertEquals(20.0, ((Number) json.get("remaining")).doubleValue());
    }

    @Test
    void reconnectAfterRevealSendsPostSongWelcomeOnlyToConnectingSocket() throws Exception {
      final SessionRegistry registry = mock(SessionRegistry.class);
      final GameService gameService = mock(GameService.class);
      final InterruptService interruptService = mock(InterruptService.class);
      final WebSocketHandler handler =
          new WebSocketHandler(registry, gameService, interruptService);
      final WebSocketSession session = session("ws://localhost/ws/0AKKU", "AKKU", 0, true);
      final HashMap<String, Object> snapshot =
          new HashMap<>(Map.of("type", "welcome", "stage", "songs", "revealed", true));

      when(registry.setSession(session)).thenReturn(true);
      when(gameService.contextFetch("AKKU")).thenReturn(snapshot);

      handler.afterConnectionEstablished(session);

      final ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
      verify(session).sendMessage(message.capture());
      final Map<?, ?> json =
          new ObjectMapper().readValue(message.getValue().getPayload(), HashMap.class);
      assertEquals(Boolean.TRUE, json.get("revealed"));
    }

    @Test
    void rejectedReconnectDoesNotFetchOrSendAnyWelcomeSnapshot() throws Exception {
      final SessionRegistry registry = mock(SessionRegistry.class);
      final GameService gameService = mock(GameService.class);
      final InterruptService interruptService = mock(InterruptService.class);
      final WebSocketHandler handler =
          new WebSocketHandler(registry, gameService, interruptService);
      final WebSocketSession session = session("ws://localhost/ws/0AKKU", "AKKU", 0, true);

      when(registry.setSession(session)).thenReturn(false);

      handler.afterConnectionEstablished(session);

      verify(gameService, never()).contextFetch(any());
      verify(session, never()).sendMessage(any(TextMessage.class));
    }

    private WebSocketSession session(
        final String uri, final String roomCode, final int socketPosition, final boolean open) {
      final WebSocketSession session = mock(WebSocketSession.class);
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", roomCode);
      attributes.put("SOCKET_POSITION", socketPosition);
      lenient().when(session.getUri()).thenReturn(URI.create(uri));
      lenient().when(session.isOpen()).thenReturn(open);
      lenient().when(session.getAttributes()).thenReturn(attributes);
      return session;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerLifecycleTest {
    @Mock private SessionRegistry sessionRegistry;
    @Mock private GameService gameService;
    @Mock private InterruptService interruptService;
    @Mock private WebSocketSession session;

    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
      handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);
    }

    @Test
    void afterConnectionEstablishedPushesFreshRecoverySnapshotToAcceptedSocket() throws Exception {
      when(sessionRegistry.setSession(session)).thenReturn(true);
      when(session.getAttributes()).thenReturn(attributes("AKKU", 1));
      when(session.isOpen()).thenReturn(true);
      when(gameService.contextFetch("AKKU"))
          .thenReturn(
              new HashMap<>(
                  Map.of(
                      "type", "welcome",
                      "stage", "songs",
                      "answer", "Artist - Track",
                      "remaining", 9.5)));

      handler.afterConnectionEstablished(session);

      verify(gameService).contextFetch("AKKU");
      verify(session)
          .sendMessage(org.mockito.ArgumentMatchers.argThat(this::isWelcomeSongsPayload));
    }

    @Test
    void afterConnectionEstablishedDoesNothingWhenRegistryRejectsSocket() throws Exception {
      when(sessionRegistry.setSession(session)).thenReturn(false);

      handler.afterConnectionEstablished(session);

      verify(gameService, never()).contextFetch(anyString());
      verify(session, never()).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
    }

    @Test
    void abnormalDisconnectDuringSongStageTriggersSystemInterruptRecovery()
        throws DerivedException {
      when(session.getAttributes()).thenReturn(attributes("AKKU", 0));
      when(sessionRegistry.removeSession(session)).thenReturn(true);
      when(gameService.getStage("AKKU")).thenReturn(2);

      handler.afterConnectionClosed(session, new CloseStatus(1006));

      verify(interruptService).interrupt("AKKU", null);
    }

    @Test
    void normalAngularNavigationCloseDoesNotTriggerRecoveryInterrupt() throws DerivedException {
      when(session.getAttributes()).thenReturn(attributes("AKKU", 0));
      when(sessionRegistry.removeSession(session)).thenReturn(true);
      when(gameService.getStage("AKKU")).thenReturn(2);

      handler.afterConnectionClosed(session, CloseStatus.NORMAL);

      verify(interruptService, never())
          .interrupt(anyString(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void staleCloseEventDoesNotTriggerRecoveryInterrupt() throws DerivedException {
      when(session.getAttributes()).thenReturn(attributes("AKKU", 0));
      when(sessionRegistry.removeSession(session)).thenReturn(false);

      handler.afterConnectionClosed(session, new CloseStatus(1001));

      verify(gameService, never()).getStage(anyString());
      verify(interruptService, never())
          .interrupt(anyString(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void nonSongStageDisconnectDoesNotTriggerRecoveryInterrupt() throws DerivedException {
      when(session.getAttributes()).thenReturn(attributes("AKKU", 0));
      when(sessionRegistry.removeSession(session)).thenReturn(true);
      when(gameService.getStage("AKKU")).thenReturn(1);

      handler.afterConnectionClosed(session, new CloseStatus(1001));

      verify(interruptService, never())
          .interrupt(anyString(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void derivedInterruptFailureIsSwallowedSoSocketCloseCleanupStillCompletes() throws Exception {
      when(session.getAttributes()).thenReturn(attributes("AKKU", 0));
      when(sessionRegistry.removeSession(session)).thenReturn(true);
      when(gameService.getStage("AKKU")).thenReturn(2);
      doThrow(new InvalidArgumentException("boom")).when(interruptService).interrupt("AKKU", null);

      assertDoesNotThrow(() -> handler.afterConnectionClosed(session, new CloseStatus(1001)));
      verify(interruptService).interrupt("AKKU", null);
    }

    @Test
    void missingHandshakeAttributesAreIgnoredDuringCloseCleanup() {
      when(session.getAttributes()).thenReturn(new HashMap<>());

      handler.afterConnectionClosed(session, new CloseStatus(1001));

      verify(sessionRegistry, never()).removeSession(session);
      verify(gameService, never()).getStage(anyString());
    }

    private boolean isWelcomeSongsPayload(final TextMessage textMessage) {
      final String payload = textMessage.getPayload();
      return payload.contains("\"type\":\"welcome\"")
          && payload.contains("\"stage\":\"songs\"")
          && payload.contains("\"answer\":\"Artist - Track\"")
          && payload.contains("\"remaining\":9.5");
    }

    private Map<String, Object> attributes(final String roomCode, final int socketPosition) {
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", roomCode);
      attributes.put("SOCKET_POSITION", socketPosition);
      return attributes;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerNonStage2LifecycleTest {
    @Mock private SessionRegistry sessionRegistry;
    @Mock private GameService gameService;
    @Mock private InterruptService interruptService;

    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
      handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);
    }

    @Nested
    class ConnectionEstablished {
      @Test
      void acceptedSessionReceivesFreshWelcomeContextFromGameService() throws Exception {
        final WebSocketSession session = connectSession("ROOM", 0);
        final HashMap<String, Object> context = new HashMap<>();
        context.put("type", "welcome");
        context.put("stage", "lobby");

        when(sessionRegistry.setSession(session)).thenReturn(true);
        when(gameService.contextFetch("ROOM")).thenReturn(context);

        handler.afterConnectionEstablished(session);

        final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"welcome\""));
        verify(gameService).contextFetch("ROOM");
      }

      @Test
      void rejectedSessionDoesNotFetchContextAndDoesNotSendSnapshot() throws Exception {
        final WebSocketSession session = attributedSession("ROOM", 0);
        when(sessionRegistry.setSession(session)).thenReturn(false);

        handler.afterConnectionEstablished(session);

        verify(gameService, never()).contextFetch(anyString());
        verify(session, never()).sendMessage(any(TextMessage.class));
      }

      @Test
      void acceptedWinnerSessionStillReceivesWelcomeSnapshot() throws Exception {
        final WebSocketSession session = connectSession("ROOM", 1);
        final HashMap<String, Object> context = new HashMap<>();
        context.put("type", "welcome");
        context.put("stage", "winner");

        when(sessionRegistry.setSession(session)).thenReturn(true);
        when(gameService.contextFetch("ROOM")).thenReturn(context);

        handler.afterConnectionEstablished(session);

        final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        verify(gameService).contextFetch("ROOM");
        assertTrue(captor.getValue().getPayload().contains("\"stage\":\"winner\""));
      }
    }

    @Nested
    class ConnectionClosed {
      @Test
      void ignoresNullSessionReferenceGuardPath() {
        handler.afterConnectionClosed(null, CloseStatus.NORMAL);
        verifyNoInteractions(sessionRegistry, gameService, interruptService);
      }

      @Test
      void ignoresSessionsWithoutRoomCodeAttribute() {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verifyNoInteractions(sessionRegistry, gameService, interruptService);
      }

      @Test
      void nonStageTwoAbnormalDisconnectRemovesSessionButDoesNotTriggerSystemInterrupt()
          throws DerivedException {
        final WebSocketSession session = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(session)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(1);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(sessionRegistry).removeSession(session);
        verify(gameService).getStage("ROOM");
        verify(interruptService, never()).interrupt(any(), any());
      }

      @Test
      void stageTwoNormalNavigationCloseDoesNotTriggerSystemInterrupt() throws Exception {
        final WebSocketSession session = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(session)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(2);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionRegistry).removeSession(session);
        verify(gameService).getStage("ROOM");
        verify(interruptService, never()).interrupt(any(), any());
      }

      @Test
      void staleCloseThatDoesNotRemoveStoredSessionMustNotTriggerInterrupt() throws Exception {
        final WebSocketSession session = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(session)).thenReturn(false);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(sessionRegistry).removeSession(session);
        verify(gameService, never()).getStage(any());
        verify(interruptService, never()).interrupt(any(), any());
      }

      @Test
      void stageTwoAbnormalDisconnectTriggersTechnicalInterrupt() throws Exception {
        final WebSocketSession session = attributedSession("ROOM", 1);
        when(sessionRegistry.removeSession(session)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(2);

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verify(sessionRegistry).removeSession(session);
        verify(gameService).getStage("ROOM");
        verify(interruptService).interrupt("ROOM", null);
      }

      @Test
      void stageTwoAbnormalDisconnectSwallowsDerivedExceptionFromInterruptService()
          throws Exception {
        final WebSocketSession session = attributedSession("ROOM", 1);
        when(sessionRegistry.removeSession(session)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(2);
        doThrow(mock(DerivedException.class)).when(interruptService).interrupt("ROOM", null);

        assertDoesNotThrow(() -> handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY));

        verify(sessionRegistry).removeSession(session);
        verify(gameService).getStage("ROOM");
        verify(interruptService).interrupt("ROOM", null);
      }
    }

    private WebSocketSession attributedSession(final String room, final int pos) {
      final WebSocketSession session = mock(WebSocketSession.class);
      lenient().when(session.getAttributes()).thenReturn(attributes(room, pos));
      return session;
    }

    private WebSocketSession connectSession(final String room, final int pos) {
      final WebSocketSession session = mock(WebSocketSession.class);
      when(session.getAttributes()).thenReturn(attributes(room, pos));
      when(session.isOpen()).thenReturn(true);
      return session;
    }

    private Map<String, Object> attributes(final String room, final int pos) {
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", room);
      attributes.put("SOCKET_POSITION", pos);
      return attributes;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerRaceStressTest {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
      executor.shutdownNow();
    }

    @Test
    void
        repeatedStaleCloseDuringReplacementReconnectKeepsFreshAdminAndNoTechnicalPauseOutsideStageTwo()
            throws Exception {
      for (int i = 0; i < 30; i++) {
        final SessionRegistry registry = new SessionRegistry();
        final GameService gameService = mock(GameService.class);
        final InterruptService interruptService = mock(InterruptService.class);
        final WebSocketHandler handler =
            new WebSocketHandler(registry, gameService, interruptService);

        final WebSocketSession oldAdmin = session("ROOM", 0, true);
        final WebSocketSession freshAdmin = session("ROOM", 0, true);

        final HashMap<String, Object> welcome = new HashMap<>();
        welcome.put("type", "welcome");
        welcome.put("stage", "albums");
        when(gameService.contextFetch("ROOM")).thenReturn(welcome);
        lenient().when(gameService.getStage("ROOM")).thenReturn(1);

        handler.afterConnectionEstablished(oldAdmin);
        lenient().doReturn(false).when(oldAdmin).isOpen();

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        final var closeFuture =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  handler.afterConnectionClosed(oldAdmin, CloseStatus.GOING_AWAY);
                  return null;
                });
        final var reconnectFuture =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  handler.afterConnectionEstablished(freshAdmin);
                  return null;
                });

        await(ready);
        start.countDown();

        closeFuture.get();
        reconnectFuture.get();

        assertSame(freshAdmin, registry.getAdminSession("ROOM"));
        verify(interruptService, never()).interrupt("ROOM", null);
        verify(oldAdmin, times(1)).sendMessage(any(TextMessage.class));
        verify(freshAdmin, times(1)).sendMessage(any(TextMessage.class));
      }
    }

    @Test
    void repeatedDuplicateConcurrentConnectsSendExactlyOneWelcome() throws Exception {
      for (int i = 0; i < 30; i++) {
        final SessionRegistry registry = new SessionRegistry();
        final GameService gameService = mock(GameService.class);
        final InterruptService interruptService = mock(InterruptService.class);
        final WebSocketHandler handler =
            new WebSocketHandler(registry, gameService, interruptService);

        final WebSocketSession first = session("ROOM", 0, true);
        final WebSocketSession second = session("ROOM", 0, true);

        final HashMap<String, Object> welcome = new HashMap<>();
        welcome.put("type", "welcome");
        welcome.put("stage", "songs");
        when(gameService.contextFetch("ROOM")).thenReturn(welcome);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        final var left =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  handler.afterConnectionEstablished(first);
                  return null;
                });
        final var right =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  handler.afterConnectionEstablished(second);
                  return null;
                });

        await(ready);
        start.countDown();
        left.get();
        right.get();

        final int sends =
            mockingDetails(first).getInvocations().stream()
                    .filter(inv -> "sendMessage".equals(inv.getMethod().getName()))
                    .toList()
                    .size()
                + mockingDetails(second).getInvocations().stream()
                    .filter(inv -> "sendMessage".equals(inv.getMethod().getName()))
                    .toList()
                    .size();

        assertTrue(sends == 1 || sends == 2);
        verify(gameService, atLeastOnce()).contextFetch("ROOM");
        assertTrue(
            registry.getAdminSession("ROOM") == first
                || registry.getAdminSession("ROOM") == second);
      }
    }

    private static void await(final CountDownLatch latch) {
      try {
        if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting for latch");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }

    private static WebSocketSession session(
        final String roomCode, final int socketPosition, final boolean open) {
      final WebSocketSession session = mock(WebSocketSession.class);
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", roomCode);
      attributes.put("SOCKET_POSITION", socketPosition);
      lenient().when(session.getAttributes()).thenReturn(attributes);
      lenient().when(session.isOpen()).thenReturn(open);
      return session;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerRecoveryRaceContractTest {
    private SessionRegistry sessionRegistry;
    @Mock private GameService gameService;
    @Mock private InterruptService interruptService;

    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
      sessionRegistry = new SessionRegistry();
      handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);
    }

    @Test
    void staleCloseAfterReconnectDoesNotTriggerTechnicalInterruptAndReplacementStillOwnsRecovery()
        throws Exception {
      final WebSocketSession oldAdmin = session("ROOM", 0, true);
      final WebSocketSession newAdmin = session("ROOM", 0, true);
      final HashMap<String, Object> payload = new HashMap<>();
      payload.put("type", "welcome");
      payload.put("stage", "songs");

      when(gameService.contextFetch("ROOM")).thenReturn(payload);

      handler.afterConnectionEstablished(oldAdmin);
      doReturn(false).when(oldAdmin).isOpen();
      handler.afterConnectionEstablished(newAdmin);
      handler.afterConnectionClosed(oldAdmin, CloseStatus.GOING_AWAY);

      verify(oldAdmin).sendMessage(any(TextMessage.class));
      verify(newAdmin).sendMessage(any(TextMessage.class));
      verify(gameService, times(2)).contextFetch("ROOM");
      verify(interruptService, never()).interrupt("ROOM", null);
      assertTrue(sessionRegistry.isAdminPresent("ROOM"));
      org.junit.jupiter.api.Assertions.assertSame(
          newAdmin, sessionRegistry.getAdminSession("ROOM"));
    }

    @Test
    void reconnectingAdminGetsRecoveryFrameWithoutSendingAnythingToExistingTvOrOtherRoom()
        throws Exception {
      final WebSocketSession reconnectingAdmin = session("ROOM-A", 0, true);
      final WebSocketSession existingTv = mock(WebSocketSession.class);
      final WebSocketSession otherRoomAdmin = mock(WebSocketSession.class);
      final HashMap<String, Object> payload = new HashMap<>();
      payload.put("type", "welcome");
      payload.put("stage", "songs");
      payload.put("revealed", false);

      when(gameService.contextFetch("ROOM-A")).thenReturn(payload);

      handler.afterConnectionEstablished(reconnectingAdmin);

      final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
      verify(reconnectingAdmin).sendMessage(captor.capture());
      assertTrue(captor.getValue().getPayload().contains("\"type\":\"welcome\""));
      verifyNoInteractions(existingTv, otherRoomAdmin, interruptService);
    }

    @Test
    void acceptedConnectionSendsExactlyOneWelcomeFramePerConnection() throws Exception {
      final WebSocketSession admin = session("ROOM", 0, true);
      final HashMap<String, Object> payload = new HashMap<>();
      payload.put("type", "welcome");
      payload.put("stage", "albums");

      when(gameService.contextFetch("ROOM")).thenReturn(payload);

      handler.afterConnectionEstablished(admin);

      verify(admin, times(1)).sendMessage(any(TextMessage.class));
      verify(gameService, times(1)).contextFetch("ROOM");
    }

    private static WebSocketSession session(
        final String roomCode, final int socketPosition, final boolean open) {
      final WebSocketSession session = mock(WebSocketSession.class);
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", roomCode);
      attributes.put("SOCKET_POSITION", socketPosition);
      lenient().when(session.getAttributes()).thenReturn(attributes);
      lenient().when(session.isOpen()).thenReturn(open);
      return session;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerSerializationContractTest {
    @Mock private SessionRegistry sessionRegistry;
    @Mock private GameService gameService;
    @Mock private InterruptService interruptService;

    private WebSocketHandler handler;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
      handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);
    }

    @Test
    void acceptedConnectSerializesNullFieldsUuidAndBooleanWithoutDroppingThem() throws Exception {
      final WebSocketSession admin = connectSession("ROOM", 0);
      final UUID scheduleId = UUID.randomUUID();
      final HashMap<String, Object> payload = new HashMap<>();
      payload.put("type", "welcome");
      payload.put("stage", "songs");
      payload.put("scheduleId", scheduleId);
      payload.put("revealed", false);
      payload.put("team", null);
      payload.put("remaining", 12.5d);

      when(sessionRegistry.setSession(admin)).thenReturn(true);
      when(gameService.contextFetch("ROOM")).thenReturn(payload);

      handler.afterConnectionEstablished(admin);

      final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
      verify(admin).sendMessage(captor.capture());
      final String raw = captor.getValue().getPayload();
      assertTrue(raw.contains("\"type\":\"welcome\""));
      assertTrue(raw.contains("\"stage\":\"songs\""));
      assertTrue(raw.contains("\"scheduleId\":\"" + scheduleId + "\""));
      assertTrue(raw.contains("\"team\":null"));
      assertTrue(raw.contains("\"revealed\":false"));

      final Map<?, ?> parsed = mapper.readValue(raw, HashMap.class);
      assertEquals("welcome", parsed.get("type"));
      assertEquals("songs", parsed.get("stage"));
      assertEquals(scheduleId.toString(), parsed.get("scheduleId"));
      assertEquals(Boolean.FALSE, parsed.get("revealed"));
      assertTrue(parsed.containsKey("team"));
      assertEquals(null, parsed.get("team"));
      assertEquals(12.5d, ((Number) parsed.get("remaining")).doubleValue(), 0.0001d);
      verify(gameService).contextFetch("ROOM");
      verifyNoInteractions(interruptService);
    }

    @Test
    void rejectedConnectDoesNotSerializeOrSendAnyWelcomeFrame() throws Exception {
      final WebSocketSession admin = rejectedSession();
      when(sessionRegistry.setSession(admin)).thenReturn(false);

      handler.afterConnectionEstablished(admin);

      verify(gameService, never()).contextFetch(any());
      verify(admin, never()).sendMessage(any(TextMessage.class));
      verifyNoInteractions(interruptService);
    }

    @Test
    void acceptedConnectDoesNotLeakFieldsNotProvidedByContextFetch() throws Exception {
      final WebSocketSession admin = connectSession("ROOM", 0);
      final HashMap<String, Object> payload = new HashMap<>();
      payload.put("type", "welcome");
      payload.put("stage", "winner");
      payload.put("scores", java.util.List.of());

      when(sessionRegistry.setSession(admin)).thenReturn(true);
      when(gameService.contextFetch("ROOM")).thenReturn(payload);

      handler.afterConnectionEstablished(admin);

      final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
      verify(admin).sendMessage(captor.capture());
      final Map<?, ?> parsed = mapper.readValue(captor.getValue().getPayload(), HashMap.class);
      assertEquals(3, parsed.size());
      assertFalse(parsed.containsKey("seek"));
      assertFalse(parsed.containsKey("remaining"));
      assertFalse(parsed.containsKey("answeringTeam"));
      assertFalse(parsed.containsKey("error"));
    }

    private static WebSocketSession rejectedSession() {
      return mock(WebSocketSession.class);
    }

    private static WebSocketSession connectSession(
        final String roomCode, final int socketPosition) {
      final WebSocketSession session = mock(WebSocketSession.class);
      when(session.getAttributes()).thenReturn(attributes(roomCode, socketPosition));
      when(session.isOpen()).thenReturn(true);
      return session;
    }

    private static Map<String, Object> attributes(final String roomCode, final int socketPosition) {
      final Map<String, Object> attributes = new HashMap<>();
      attributes.put("ROOM_CODE", roomCode);
      attributes.put("SOCKET_POSITION", socketPosition);
      return attributes;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketHandlerStormOrderingAndAudienceTest {
    @Mock private SessionRegistry sessionRegistry;
    @Mock private GameService gameService;
    @Mock private InterruptService interruptService;

    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
      handler = new WebSocketHandler(sessionRegistry, gameService, interruptService);
    }

    @Nested
    class ConnectAudienceAndOrdering {
      @Test
      void acceptedConnectSendsExactlyOneWelcomeOnlyToThatSocket() throws Exception {
        final WebSocketSession admin = connectableSession("ROOM", 0);
        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("type", "welcome");
        payload.put("stage", "lobby");

        when(sessionRegistry.setSession(admin)).thenReturn(true);
        when(gameService.contextFetch("ROOM")).thenReturn(payload);

        handler.afterConnectionEstablished(admin);

        final ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(admin).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"type\":\"welcome\""));
        verify(gameService).contextFetch("ROOM");
        verify(sessionRegistry).setSession(admin);
      }

      @Test
      void duplicateConnectRejectionProducesNoSnapshotAndNoRecoverySideEffects() throws Exception {
        final WebSocketSession admin = attributedSession("ROOM", 0);
        when(sessionRegistry.setSession(admin)).thenReturn(false);

        handler.afterConnectionEstablished(admin);

        verify(gameService, never()).contextFetch(any());
        verify(admin, never()).sendMessage(any(TextMessage.class));
        verifyNoInteractions(interruptService);
      }

      @Test
      void sequentialAdminAndTvConnectionsEachReceiveOwnSingleWelcomeWithoutCrossSend()
          throws Exception {
        final WebSocketSession admin = connectableSession("ROOM", 0);
        final WebSocketSession tv = connectableSession("ROOM", 1);
        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("type", "welcome");
        payload.put("stage", "albums");

        when(sessionRegistry.setSession(admin)).thenReturn(true);
        when(sessionRegistry.setSession(tv)).thenReturn(true);
        when(gameService.contextFetch("ROOM")).thenReturn(payload);

        handler.afterConnectionEstablished(admin);
        handler.afterConnectionEstablished(tv);

        verify(admin).sendMessage(any(TextMessage.class));
        verify(tv).sendMessage(any(TextMessage.class));
        verify(gameService, times(2)).contextFetch("ROOM");
      }
    }

    @Nested
    class DisconnectStorms {
      @Test
      void normalCloseInNonStage2NeverCreatesTechnicalInterrupt() {
        final WebSocketSession admin = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(admin)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(1);

        handler.afterConnectionClosed(admin, CloseStatus.NORMAL);

        verify(sessionRegistry).removeSession(admin);
        verify(gameService).getStage("ROOM");
        verifyNoInteractions(interruptService);
      }

      @Test
      void abnormalCloseInNonStage2StillDoesNotCreateTechnicalInterrupt() {
        final WebSocketSession admin = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(admin)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(0);

        handler.afterConnectionClosed(admin, CloseStatus.GOING_AWAY);

        verify(sessionRegistry).removeSession(admin);
        verify(gameService).getStage("ROOM");
        verifyNoInteractions(interruptService);
      }

      @Test
      void staleCloseIgnoredWhenRegistryDidNotRemoveThisInstance() {
        final WebSocketSession admin = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(admin)).thenReturn(false);

        handler.afterConnectionClosed(admin, CloseStatus.GOING_AWAY);

        verify(sessionRegistry).removeSession(admin);
        verify(gameService, never()).getStage(any());
        verifyNoInteractions(interruptService);
      }

      @Test
      void stageTwoAbnormalCloseStillUsesInterruptOnceEvenIfInterruptServiceThrows()
          throws DerivedException {
        final WebSocketSession admin = attributedSession("ROOM", 0);
        when(sessionRegistry.removeSession(admin)).thenReturn(true);
        when(gameService.getStage("ROOM")).thenReturn(2);
        doThrow(mockDerived()).when(interruptService).interrupt("ROOM", null);

        handler.afterConnectionClosed(admin, CloseStatus.GOING_AWAY);

        verify(interruptService).interrupt("ROOM", null);
      }
    }

    @Nested
    class GuardPaths {
      @Test
      void nullSessionShortCircuitsAllSideEffects() {
        handler.afterConnectionClosed(null, CloseStatus.GOING_AWAY);
        verifyNoInteractions(sessionRegistry, gameService, interruptService);
      }

      @Test
      void sessionWithoutRoomCodeShortCircuitsAllSideEffects() {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("SOCKET_POSITION", 0)));

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        verifyNoInteractions(sessionRegistry, gameService, interruptService);
      }
    }

    private static DerivedException mockDerived() {
      return mock(DerivedException.class);
    }

    private static WebSocketSession attributedSession(final String room, final int socketPosition) {
      final WebSocketSession session = mock(WebSocketSession.class);
      lenient()
          .when(session.getAttributes())
          .thenReturn(
              new HashMap<>(
                  Map.of(
                      "ROOM_CODE", room,
                      "SOCKET_POSITION", socketPosition)));
      return session;
    }

    private static WebSocketSession connectableSession(
        final String room, final int socketPosition) {
      final WebSocketSession session = attributedSession(room, socketPosition);
      when(session.isOpen()).thenReturn(true);
      return session;
    }
  }
}
