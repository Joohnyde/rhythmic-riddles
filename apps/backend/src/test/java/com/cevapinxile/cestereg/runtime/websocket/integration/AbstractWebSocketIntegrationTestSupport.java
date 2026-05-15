package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.config.WebSocketConfig;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.ScheduleServiceImpl;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.runtime.broadcast.WebSocketBroadcastGateway;
import com.cevapinxile.cestereg.runtime.websocket.GameCodeExtractor;
import com.cevapinxile.cestereg.runtime.websocket.SessionRegistry;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AbstractWebSocketIntegrationTestSupport.TestApplication.class)
abstract class AbstractWebSocketIntegrationTestSupport {

  protected static final String ROOM_A = "AKKU";
  protected static final String ROOM_B = "BETA";

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    WebSocketConfig.class,
    GameCodeExtractor.class,
    SessionRegistry.class,
    WebSocketBroadcastGateway.class,
    ScheduleServiceImpl.class,
    InterruptServiceImpl.class
  })
  static class TestApplication {}

  @LocalServerPort protected int port;

  @Autowired protected BroadcastGateway broadcastGateway;
  @Autowired protected ScheduleService scheduleService;
  @Autowired protected InterruptService interruptService;
  @Autowired protected SessionRegistry sessionRegistry;

  @MockitoBean protected GameRepository gameRepository;
  @MockitoBean protected GameService gameService;
  @MockitoBean protected CategoryService categoryService;
  @MockitoBean protected TeamService teamService;
  @MockitoBean protected ScheduleRepository scheduleRepository;
  @MockitoBean protected InterruptRepository interruptRepository;

  protected final ObjectMapper mapper = new ObjectMapper();
  protected final StandardWebSocketClient client = new StandardWebSocketClient();
  protected final GameEntity gameA = game(ROOM_A);
  protected final GameEntity gameB = game(ROOM_B);
  protected final Map<String, GameEntity> gamesByCode = new ConcurrentHashMap<>();

  @BeforeEach
  void setUpBase() throws DerivedException {
    gamesByCode.clear();
    gamesByCode.put(ROOM_A, gameA);
    gamesByCode.put(ROOM_B, gameB);
    when(gameRepository.findByCode(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(gamesByCode.get(invocation.getArgument(0))));
    when(gameRepository.findByCode(anyString(), any()))
        .thenAnswer(invocation -> gamesByCode.get(invocation.getArgument(0)));
    when(gameService.findByCode(anyString(), any()))
        .thenAnswer(invocation -> gamesByCode.get(invocation.getArgument(0)));
    when(gameService.contextFetch(anyString())).thenAnswer(invocation -> welcome(invocation.getArgument(0)));
  }

  @AfterEach
  void tearDownBase() throws Exception {
    for (String roomCode : new ArrayList<>(gamesByCode.keySet())) {
      closeRegistrySession(roomCode, true);
      closeRegistrySession(roomCode, false);
    }
  }

  protected SocketProbe connectAdmin(final String roomCode) throws Exception {
    final SocketProbe probe = new SocketProbe();
    assertTrue(connectPossiblyRejected(0, roomCode, probe));
    return probe;
  }

  protected SocketProbe connectTv(final String roomCode) throws Exception {
    final SocketProbe probe = new SocketProbe();
    assertTrue(connectPossiblyRejected(1, roomCode, probe));
    return probe;
  }

  protected boolean connectPossiblyRejected(
      final int socketPosition, final String roomCode, final SocketProbe probe) {
    try {
      final Object future =
          client.execute(
              probe, new WebSocketHttpHeaders(), URI.create(wsUrl(socketPosition, roomCode)));
      probe.session = awaitSession(future);
      return probe.session != null && probe.session.isOpen();
    } catch (RuntimeException ex) {
      return false;
    }
  }

  protected boolean connectUrlPossiblyRejected(final String url, final SocketProbe probe) {
    try {
      final Object future = client.execute(probe, new WebSocketHttpHeaders(), URI.create(url));
      probe.session = awaitSession(future);
      return probe.session != null && probe.session.isOpen();
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private WebSocketSession awaitSession(final Object future) {
    try {
      if (future instanceof CompletableFuture<?> completableFuture) {
        return (WebSocketSession) completableFuture.get(3, TimeUnit.SECONDS);
      }
      final Method get = future.getClass().getMethod("get", long.class, TimeUnit.class);
      return (WebSocketSession) get.invoke(future, 3L, TimeUnit.SECONDS);
    } catch (ExecutionException ex) {
      throw new IllegalStateException(ex.getCause());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  protected String wsUrl(final int socketPosition, final String roomCode) {
    return "ws://localhost:" + port + "/ws/" + socketPosition + roomCode;
  }

  protected HashMap<String, Object> welcome(final String roomCode) {
    final HashMap<String, Object> welcome = new HashMap<>();
    welcome.put("type", "welcome");
    welcome.put("roomCode", roomCode);
    welcome.put("stage", 2);
    welcome.put("recovery", true);
    return welcome;
  }

  protected static GameEntity game(final String roomCode) {
    final GameEntity game = new GameEntity(UUID.randomUUID());
    game.setCode(roomCode);
    game.setDate(LocalDateTime.now());
    game.setStage(2);
    return game;
  }

  protected Fixture fixture() {
    final TeamEntity team = new TeamEntity(UUID.randomUUID());
    team.setGameId(gameA);
    team.setName("Enterprise Testers");

    final ScheduleEntity current = schedule(21.5d, 7.0d, "Current question?", "Current answer");
    current.setStartedAt(LocalDateTime.now().minusSeconds(1));
    current.setInterruptList(new ArrayList<>());

    final ScheduleEntity next = schedule(13.25d, 5.5d, "Next question?", "Next answer");

    final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
    answer.setTeamId(team);
    answer.setScheduleId(current);
    answer.setArrivedAt(LocalDateTime.now());
    return new Fixture(team, current, next, answer);
  }

  protected static ScheduleEntity schedule(
      final double snippetDuration,
      final double answerDuration,
      final String question,
      final String answer) {
    final SongEntity song = new SongEntity(UUID.randomUUID());
    song.setSnippetDuration(snippetDuration);
    song.setAnswerDuration(answerDuration);

    final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
    album.setCustomQuestion(question);

    final TrackEntity track = new TrackEntity(UUID.randomUUID());
    track.setAlbumId(album);
    track.setSongId(song);
    track.setCustomAnswer(answer);

    final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
    schedule.setTrackId(track);
    return schedule;
  }

  protected void stubRound(final Fixture fixture) throws DerivedException {
    when(scheduleRepository.findLastPlayed(gameA.getId())).thenReturn(fixture.currentSchedule());
    when(scheduleRepository.findById(fixture.currentSchedule().getId()))
        .thenReturn(Optional.of(fixture.currentSchedule()));
    when(scheduleRepository.findNext(gameA.getId())).thenReturn(Optional.of(fixture.nextSchedule()));
    when(teamService.findById(fixture.team().getId())).thenReturn(Optional.of(fixture.team()));
    when(teamService.getTeamPoints(fixture.team().getId(), ROOM_A)).thenReturn(10);
    when(interruptRepository.findById(fixture.teamInterrupt().getId()))
        .thenReturn(Optional.of(fixture.teamInterrupt()));
    when(interruptRepository.findInterrupts(any(), any())).thenReturn(new ArrayList<>());
    when(interruptRepository.findLastAnswer(any(), any())).thenReturn(null);
    when(interruptRepository.findLastPause(any(), any())).thenReturn(null);
    when(interruptRepository.findPreviousScenarioId(fixture.currentSchedule().getId())).thenReturn(4);
    when(interruptRepository.saveAndFlush(any(InterruptEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(interruptRepository.save(any(InterruptEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(scheduleRepository.saveAndFlush(any(ScheduleEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  protected void assertPause(final Map<?, ?> frame, final UUID expectedTeamId) {
    assertEquals("pause", frame.get("type"));
    assertEquals(expectedTeamId.toString(), frame.get("answeringTeamId"));
    assertNotNull(frame.get("interruptId"));
    assertEquals(3, frame.size());
  }

  protected void assertAnswer(
      final Map<?, ?> frame, final UUID teamId, final UUID scheduleId, final boolean correct) {
    assertEquals("answer", frame.get("type"));
    assertEquals(teamId.toString(), frame.get("teamId"));
    assertEquals(scheduleId.toString(), frame.get("scheduleId"));
    assertEquals(correct, frame.get("correct"));
    assertEquals(4, frame.size());
  }

  protected void assertErrorSolved(final Map<?, ?> frame, final int previousScenario) {
    assertEquals("error_solved", frame.get("type"));
    assertEquals(previousScenario, ((Number) frame.get("previousScenario")).intValue());
    assertEquals(2, frame.size());
  }

  protected void assertRepeat(final Map<?, ?> frame, final double remaining) {
    assertEquals("song_repeat", frame.get("type"));
    assertEquals(remaining, ((Number) frame.get("remaining")).doubleValue(), 0.0001d);
    assertEquals(2, frame.size());
  }

  protected void assertOnlyType(final Map<?, ?> frame, final String type) {
    assertEquals(type, frame.get("type"));
    assertEquals(1, frame.size());
  }

  protected void assertNext(final Map<?, ?> frame, final ScheduleEntity schedule) {
    assertEquals("song_next", frame.get("type"));
    assertEquals(schedule.getId().toString(), frame.get("scheduleId"));
    assertEquals(schedule.getTrackId().getSongId().getId().toString(), frame.get("songId"));
    assertEquals("Next question?", frame.get("question"));
    assertEquals("Next answer", frame.get("answer"));
    assertEquals(13.25d, ((Number) frame.get("remaining")).doubleValue(), 0.0001d);
    assertEquals(5.5d, ((Number) frame.get("answerDuration")).doubleValue(), 0.0001d);
    assertContract(frame, "song_next");
  }

  protected void assertContract(final Map<?, ?> frame, final String expectedType) {
    assertEquals(expectedType, frame.get("type"));
    switch (expectedType) {
      case "welcome" -> {
        assertExactKeys(frame, "type", "roomCode", "stage", "recovery");
        assertValueMatchesContract("type", frame.get("type"), "string");
        assertValueMatchesContract("roomCode", frame.get("roomCode"), "string");
        assertValueMatchesContract("stage", frame.get("stage"), "number");
        assertValueMatchesContract("recovery", frame.get("recovery"), "boolean");
      }
      case "pause" -> {
        assertExactKeys(frame, "type", "answeringTeamId", "interruptId");
        assertValueMatchesContract("answeringTeamId", frame.get("answeringTeamId"), "uuid-or-literal-null");
        assertValueMatchesContract("interruptId", frame.get("interruptId"), "uuid");
      }
      case "answer" -> {
        assertExactKeys(frame, "type", "teamId", "scheduleId", "correct");
        assertValueMatchesContract("teamId", frame.get("teamId"), "uuid");
        assertValueMatchesContract("scheduleId", frame.get("scheduleId"), "uuid");
        assertValueMatchesContract("correct", frame.get("correct"), "boolean");
      }
      case "error_solved" -> {
        assertExactKeys(frame, "type", "previousScenario");
        assertValueMatchesContract("previousScenario", frame.get("previousScenario"), "number");
      }
      case "song_repeat" -> {
        assertExactKeys(frame, "type", "remaining");
        assertValueMatchesContract("remaining", frame.get("remaining"), "number");
      }
      case "song_reveal" -> assertExactKeys(frame, "type");
      case "song_next" -> {
        assertExactKeys(
            frame,
            "type",
            "scheduleId",
            "songId",
            "question",
            "answer",
            "remaining",
            "answerDuration");
        assertValueMatchesContract("scheduleId", frame.get("scheduleId"), "uuid");
        assertValueMatchesContract("songId", frame.get("songId"), "uuid");
        assertValueMatchesContract("question", frame.get("question"), "string");
        assertValueMatchesContract("answer", frame.get("answer"), "string");
        assertValueMatchesContract("remaining", frame.get("remaining"), "number");
        assertValueMatchesContract("answerDuration", frame.get("answerDuration"), "number");
      }
      default -> throw new AssertionError("No websocket contract registered for type " + expectedType);
    }
  }

  private static void assertValueMatchesContract(
      final String key, final Object value, final String expectedType) {
    switch (expectedType) {
      case "string" -> assertTrue(value instanceof String, key + " must be a string");
      case "number" -> assertTrue(value instanceof Number, key + " must be a number");
      case "boolean" -> assertTrue(value instanceof Boolean, key + " must be a boolean");
      case "uuid" -> assertUuid(value);
      case "uuid-or-literal-null" -> assertUuidOrLiteralNull(value);
      default -> throw new AssertionError("Unknown contract field type " + expectedType);
    }
  }

  protected static void assertExactKeys(final Map<?, ?> frame, final String... keys) {
    assertEquals(Set.of(keys), frame.keySet(), "websocket contract changed: unexpected/missing JSON fields");
  }

  protected static void assertUuid(final Object value) {
    assertTrue(value instanceof String, "expected UUID string but got " + value);
    UUID.fromString((String) value);
  }

  protected static void assertUuidOrLiteralNull(final Object value) {
    assertTrue(value instanceof String, "expected UUID string or literal null string but got " + value);
    if (!"null".equals(value)) {
      UUID.fromString((String) value);
    }
  }

  protected void assertLoadBurst(final String roomCode, final int burstSize, final SocketProbe probe)
      throws Exception {
    for (int i = 0; i < burstSize; i++) {
      final Map<?, ?> frame = probe.readJson();
      assertEquals("load_probe", frame.get("type"));
      assertEquals(roomCode, frame.get("roomCode"));
      assertEquals(i, ((Number) frame.get("sequence")).intValue());
      assertExactKeys(frame, "type", "roomCode", "sequence");
    }
  }

  protected int countFramesOfType(
      final SocketProbe probe, final String expectedType, final long totalWaitMillis) throws Exception {
    int count = 0;
    final long deadline = System.currentTimeMillis() + totalWaitMillis;

    while (System.currentTimeMillis() < deadline) {
      final String frame = probe.pollFrame(50);
      if (frame == null) {
        continue;
      }

      final Map<?, ?> json = mapper.readValue(frame, HashMap.class);
      if (expectedType.equals(json.get("type"))) {
        count++;
      }
    }

    return count;
  }

  protected void closeRegistrySession(final String roomCode, final boolean admin) throws Exception {
    final WebSocketSession session =
        admin ? sessionRegistry.getAdminSession(roomCode) : sessionRegistry.getTvSession(roomCode);
    if (session != null && session.isOpen()) {
      session.close(CloseStatus.NORMAL);
    }
  }

  protected static void assertEventuallyFalse(final BooleanSupplier condition) throws Exception {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (System.nanoTime() < deadline) {
      if (!condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(25);
    }
    assertFalse(condition.getAsBoolean());
  }

  protected final class SocketProbe extends TextWebSocketHandler {
    private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    private volatile WebSocketSession session;

    @Override
    protected void handleTextMessage(final WebSocketSession session, final TextMessage message) {
      frames.add(message.getPayload());
    }

    Map<?, ?> readJson() throws Exception {
      final String payload = pollFrame(1500);
      assertNotNull(payload, "expected a websocket frame but none arrived");
      return mapper.readValue(payload, HashMap.class);
    }

    String pollFrame(final long millis) {
      try {
        return frames.poll(millis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(ex);
      }
    }

    void send(final String payload) throws Exception {
      assertNotNull(session, "cannot send before websocket session is established");
      session.sendMessage(new TextMessage(payload));
    }

    boolean isOpen() {
      return session != null && session.isOpen();
    }

    void close(final CloseStatus status) throws Exception {
      if (session != null && session.isOpen()) {
        session.close(status);
      }
    }
  }

  protected record Fixture(
      TeamEntity team,
      ScheduleEntity currentSchedule,
      ScheduleEntity nextSchedule,
      InterruptEntity teamInterrupt) {}
}
