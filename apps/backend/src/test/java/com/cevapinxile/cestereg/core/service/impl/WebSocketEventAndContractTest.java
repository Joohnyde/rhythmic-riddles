package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.request.TeamIdRequest;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WebSocketEventAndContractTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class StageTwoBroadcastJsonContractSuiteTest {
    @Nested
    @ExtendWith(MockitoExtension.class)
    class ScheduleBroadcasts {
      @Mock private GameService gameService;
      @Mock private CategoryService categoryService;
      @Mock private ScheduleRepository scheduleRepository;
      @Mock private InterruptRepository interruptRepository;
      @Mock private PresenceGateway presenceGateway;
      @Mock private BroadcastGateway broadcastGateway;

      private ScheduleServiceImpl service;
      private final ObjectMapper mapper = new ObjectMapper();

      @BeforeEach
      void setUp() {
        service = new ScheduleServiceImpl();
        ReflectionTestUtils.setField(service, "gameService", gameService);
        ReflectionTestUtils.setField(service, "categoryService", categoryService);
        ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
        ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
        ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
      }

      @Test
      void replayFrameLocksOnlyTypeAndRemaining() throws Exception {
        final GameEntity game = new GameEntity(UUID.randomUUID());
        final ScheduleEntity schedule = schedule(17.25d, 5.0d);

        when(gameService.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        service.replaySong(schedule.getId(), "ROOM");

        final Map<?, ?> parsed = capture();
        assertEquals("song_repeat", parsed.get("type"));
        assertEquals(17.25d, ((Number) parsed.get("remaining")).doubleValue(), 0.0001d);
        assertEquals(2, parsed.size());
        assertFalse(parsed.containsKey("songId"));
        assertFalse(parsed.containsKey("scheduleId"));
        assertFalse(parsed.containsKey("revealed"));
      }

      @Test
      void revealFrameLocksTypeOnly() throws Exception {
        final GameEntity game = new GameEntity(UUID.randomUUID());
        final ScheduleEntity schedule = schedule(17.25d, 5.0d);

        when(gameService.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);

        service.revealAnswer(schedule.getId(), "ROOM");

        final Map<?, ?> parsed = capture();
        assertEquals("song_reveal", parsed.get("type"));
        assertEquals(1, parsed.size());
        assertFalse(parsed.containsKey("remaining"));
        assertFalse(parsed.containsKey("scheduleId"));
        assertFalse(parsed.containsKey("error"));
      }

      @Test
      void nextSongFrameLocksRequiredFieldsAndNoPauseOrRecoveryLeaks() throws Exception {
        final GameEntity game = new GameEntity(UUID.randomUUID());
        final ScheduleEntity current = schedule(12.0d, 4.0d);
        final ScheduleEntity next = schedule(19.0d, 7.0d);

        when(gameService.findByCode("ROOM", 2)).thenReturn(game);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(current);
        when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(next));

        service.progress("ROOM");

        final Map<?, ?> parsed = capture();
        assertEquals("song_next", parsed.get("type"));
        assertEquals(next.getTrackId().getSongId().getId().toString(), parsed.get("songId"));
        assertEquals("Question?", parsed.get("question"));
        assertEquals("Answer", parsed.get("answer"));
        assertEquals(next.getId().toString(), parsed.get("scheduleId"));
        assertEquals(7.0d, ((Number) parsed.get("answerDuration")).doubleValue(), 0.0001d);
        assertEquals(19.0d, ((Number) parsed.get("remaining")).doubleValue(), 0.0001d);
        assertFalse(parsed.containsKey("revealed"));
        assertFalse(parsed.containsKey("answeringTeam"));
        assertFalse(parsed.containsKey("interruptId"));
        assertFalse(parsed.containsKey("error"));
        verify(interruptRepository).resolveErrors(eq(current.getId()), any());
      }

      private Map<?, ?> capture() throws Exception {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
        return mapper.readValue(captor.getValue(), HashMap.class);
      }
    }

    private static ScheduleEntity schedule(
        final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      album.setCustomQuestion("Question?");

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");

      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(3));
      return schedule;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class StageTwoTransitionConcurrencyTest {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    private ScheduleServiceImpl service;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
      service = new ScheduleServiceImpl();
      ReflectionTestUtils.setField(service, "gameService", gameService);
      ReflectionTestUtils.setField(service, "categoryService", categoryService);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
    }

    @AfterEach
    void tearDown() {
      executor.shutdownNow();
    }

    @Test
    void replayAndRevealCollisionBroadcastsOneValidFramePerAction() throws Exception {
      final GameEntity game = songsGame();
      final ScheduleEntity schedule = schedule(22.0d, 6.0d);
      final List<String> payloads = new ArrayList<>();
      final CountDownLatch gate = new CountDownLatch(1);

      when(gameService.findByCode("ROOM", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
      doAnswer(
              invocation -> {
                await(gate);
                synchronized (payloads) {
                  payloads.add(invocation.getArgument(1, String.class));
                }
                return null;
              })
          .when(broadcastGateway)
          .broadcast(eq("ROOM"), anyString());

      final Future<?> replay =
          executor.submit(
              () -> {
                try {
                  service.replaySong(schedule.getId(), "ROOM");
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
      final Future<?> reveal =
          executor.submit(
              () -> {
                try {
                  service.revealAnswer(schedule.getId(), "ROOM");
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });

      gate.countDown();
      replay.get(5, TimeUnit.SECONDS);
      reveal.get(5, TimeUnit.SECONDS);

      assertEquals(2, payloads.size());
      assertTrue(payloads.stream().anyMatch(p -> p.contains("\"type\":\"song_repeat\"")));
      assertTrue(payloads.stream().anyMatch(p -> p.equals("{\"type\":\"song_reveal\"}")));
    }

    @Test
    void concurrentRevealAndProgressProduceTwoIndividuallyValidContracts() throws Exception {
      final GameEntity game = songsGame();
      final ScheduleEntity current = schedule(20.0d, 6.0d);
      final ScheduleEntity next = schedule(14.0d, 5.5d);
      final List<Map<?, ?>> frames = new ArrayList<>();
      final CountDownLatch gate = new CountDownLatch(1);

      when(gameService.findByCode("ROOM", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
      when(scheduleRepository.findById(current.getId())).thenReturn(Optional.of(current));
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(current);
      when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(next));
      doAnswer(
              invocation -> {
                await(gate);
                final String payload = invocation.getArgument(1, String.class);
                synchronized (frames) {
                  frames.add(mapper.readValue(payload, HashMap.class));
                }
                return null;
              })
          .when(broadcastGateway)
          .broadcast(eq("ROOM"), anyString());

      final Future<?> reveal =
          executor.submit(
              () -> {
                try {
                  service.revealAnswer(current.getId(), "ROOM");
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
      final Future<?> progress =
          executor.submit(
              () -> {
                try {
                  service.progress("ROOM");
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });

      gate.countDown();
      reveal.get(5, TimeUnit.SECONDS);
      progress.get(5, TimeUnit.SECONDS);

      assertEquals(2, frames.size());
      assertTrue(
          frames.stream()
              .anyMatch(frame -> "song_reveal".equals(frame.get("type")) && frame.size() == 1));
      assertTrue(
          frames.stream()
              .anyMatch(
                  frame -> {
                    if (!"song_next".equals(frame.get("type"))) {
                      return false;
                    }
                    return frame.containsKey("songId")
                        && frame.containsKey("scheduleId")
                        && frame.containsKey("question")
                        && frame.containsKey("answer")
                        && frame.containsKey("answerDuration")
                        && frame.containsKey("remaining");
                  }));
    }

    private static void await(final CountDownLatch latch) {
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting for latch");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }

    private static GameEntity songsGame() {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setStage(2);
      return game;
    }

    private static ScheduleEntity schedule(
        final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      album.setCustomQuestion("Question?");

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");

      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(3));
      return schedule;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class StageTwoTransitionRaceStressRepeatedTest {
    @Mock private GameService gameService;
    @Mock private CategoryService categoryService;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private InterruptRepository interruptRepository;
    @Mock private PresenceGateway presenceGateway;
    @Mock private BroadcastGateway broadcastGateway;

    private ScheduleServiceImpl service;
    private ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
      executor = Executors.newFixedThreadPool(4);
      service = new ScheduleServiceImpl();
      ReflectionTestUtils.setField(service, "gameService", gameService);
      ReflectionTestUtils.setField(service, "categoryService", categoryService);
      ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
      ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
      ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
      ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
    }

    @AfterEach
    void tearDown() {
      executor.shutdownNow();
    }

    @Test
    void replayAndRevealCollisionRemainsContractValidAcrossManyRuns() throws Exception {
      for (int i = 0; i < 25; i++) {
        final GameEntity game = songsGame();
        final ScheduleEntity schedule = schedule(20.0d, 6.0d);
        final List<HashMap<?, ?>> frames = new ArrayList<>();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        when(gameService.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
        doAnswer(
                invocation -> {
                  synchronized (frames) {
                    frames.add(
                        mapper.readValue(invocation.getArgument(1, String.class), HashMap.class));
                  }
                  return null;
                })
            .when(broadcastGateway)
            .broadcast(eq("ROOM"), anyString());

        final var replay =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  service.replaySong(schedule.getId(), "ROOM");
                  return null;
                });
        final var reveal =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  service.revealAnswer(schedule.getId(), "ROOM");
                  return null;
                });

        await(ready);
        start.countDown();
        replay.get(5, TimeUnit.SECONDS);
        reveal.get(5, TimeUnit.SECONDS);

        assertEquals(2, frames.size());
        assertTrue(
            frames.stream()
                .anyMatch(
                    f ->
                        "song_repeat".equals(f.get("type"))
                            && f.containsKey("remaining")
                            && f.size() == 2));
        assertTrue(
            frames.stream().anyMatch(f -> "song_reveal".equals(f.get("type")) && f.size() == 1));
        frames.clear();
        reset(
            broadcastGateway,
            scheduleRepository,
            interruptRepository,
            gameService,
            presenceGateway);
      }
    }

    @Test
    void revealAndProgressCollisionRemainsContractValidAcrossManyRuns() throws Exception {
      for (int i = 0; i < 25; i++) {
        final GameEntity game = songsGame();
        final ScheduleEntity current = schedule(18.0d, 5.0d);
        final ScheduleEntity next = schedule(14.0d, 4.0d);
        final List<HashMap<?, ?>> frames = new ArrayList<>();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        when(gameService.findByCode("ROOM", 2)).thenReturn(game);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
        when(scheduleRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(current);
        when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(next));
        doAnswer(
                invocation -> {
                  synchronized (frames) {
                    frames.add(
                        mapper.readValue(invocation.getArgument(1, String.class), HashMap.class));
                  }
                  return null;
                })
            .when(broadcastGateway)
            .broadcast(eq("ROOM"), anyString());

        final var reveal =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  service.revealAnswer(current.getId(), "ROOM");
                  return null;
                });
        final var progress =
            executor.submit(
                () -> {
                  ready.countDown();
                  await(start);
                  service.progress("ROOM");
                  return null;
                });

        await(ready);
        start.countDown();
        reveal.get(5, TimeUnit.SECONDS);
        progress.get(5, TimeUnit.SECONDS);

        assertEquals(2, frames.size());
        assertTrue(
            frames.stream().anyMatch(f -> "song_reveal".equals(f.get("type")) && f.size() == 1));
        assertTrue(
            frames.stream()
                .anyMatch(
                    f ->
                        "song_next".equals(f.get("type"))
                            && f.containsKey("songId")
                            && f.containsKey("scheduleId")
                            && f.containsKey("question")
                            && f.containsKey("answer")
                            && f.containsKey("answerDuration")
                            && f.containsKey("remaining")));
        frames.clear();
        reset(
            broadcastGateway,
            scheduleRepository,
            interruptRepository,
            gameService,
            presenceGateway);
      }
    }

    private static void await(final CountDownLatch latch) {
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting for latch");
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }

    private static GameEntity songsGame() {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setStage(2);
      return game;
    }

    private static ScheduleEntity schedule(
        final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      album.setCustomQuestion("Question?");

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");

      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(3));
      return schedule;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketContractEventsTest {
    @Nested
    @ExtendWith(MockitoExtension.class)
    class TeamEventContracts {
      @Mock private BroadcastGateway broadcastGateway;
      @Mock private TeamRepository teamRepository;
      @Mock private GameRepository gameRepository;

      private TeamServiceImpl service;

      @BeforeEach
      void setUp() {
        service = new TeamServiceImpl();
        ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
        ReflectionTestUtils.setField(service, "teamRepository", teamRepository);
        ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
      }

      @Test
      void createTeamPublishesNewTeamFrameToTvOnly() throws Exception {
        final GameEntity game = game("AKKU", 0);
        when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);

        service.createTeam(new CreateTeamRequest("Blue", "BTN-1", "blue.png"), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).toTv(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"new_team\""));
        assertTrue(payload.getValue().contains("\"team\":"));
        assertTrue(payload.getValue().contains("\"name\":\"Blue\""));
        assertTrue(payload.getValue().contains("\"image\":\"blue.png\""));
      }

      @Test
      void kickTeamPublishesKickTeamFrameWithRemovedUuid() throws Exception {
        final GameEntity game = game("AKKU", 0);
        final TeamEntity team = team(game, "Red");
        when(gameRepository.findByCode("AKKU", 0)).thenReturn(game);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        service.kickTeam(team.getId().toString(), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).toTv(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"kick_team\""));
        assertTrue(payload.getValue().contains("\"uuid\":\"" + team.getId() + "\""));
      }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CategoryAndScheduleEventContracts {
      @Mock private GameService gameService;
      @Mock private CategoryRepository categoryRepository;
      @Mock private ScheduleRepository scheduleRepository;
      @Mock private TeamRepository teamRepository;
      @Mock private BroadcastGateway broadcastGateway;
      @Mock private PresenceGateway presenceGateway;
      @Mock private CategoryService categoryService;
      @Mock private InterruptRepository interruptRepository;

      private CategoryServiceImpl categoryServiceImpl;
      private ScheduleServiceImpl scheduleServiceImpl;

      @BeforeEach
      void setUp() {
        categoryServiceImpl = new CategoryServiceImpl();
        ReflectionTestUtils.setField(categoryServiceImpl, "gameService", gameService);
        ReflectionTestUtils.setField(categoryServiceImpl, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(categoryServiceImpl, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(categoryServiceImpl, "teamRepository", teamRepository);
        ReflectionTestUtils.setField(categoryServiceImpl, "broadcastGateway", broadcastGateway);
        ReflectionTestUtils.setField(categoryServiceImpl, "presenceGateway", presenceGateway);

        scheduleServiceImpl = new ScheduleServiceImpl();
        ReflectionTestUtils.setField(scheduleServiceImpl, "gameService", gameService);
        ReflectionTestUtils.setField(scheduleServiceImpl, "categoryService", categoryService);
        ReflectionTestUtils.setField(scheduleServiceImpl, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(
            scheduleServiceImpl, "interruptRepository", interruptRepository);
        ReflectionTestUtils.setField(scheduleServiceImpl, "presenceGateway", presenceGateway);
        ReflectionTestUtils.setField(scheduleServiceImpl, "broadcastGateway", broadcastGateway);
      }

      @Test
      void pickAlbumPublishesAlbumPickedFrameToTvOnly() throws Exception {
        final GameEntity game = game("AKKU", 1);
        final CategoryEntity category = category(game);
        final TeamEntity picker = team(game, "Gold");
        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(teamRepository.findById(picker.getId())).thenReturn(Optional.of(picker));
        when(categoryRepository.findNextId(game.getId())).thenReturn(2);
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

        categoryServiceImpl.pickAlbum(category.getId(), new TeamIdRequest(picker.getId()), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).toTv(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"album_picked\""));
        assertTrue(payload.getValue().contains("\"selected\":"));
        assertTrue(payload.getValue().contains("\"categoryId\":\"" + category.getId() + "\""));
      }

      @Test
      void replaySongBroadcastsSongRepeatWithRemainingDuration() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity schedule = schedule(game, 12.5, 8.0);
        when(gameService.findByCode("AKKU", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

        scheduleServiceImpl.replaySong(schedule.getId(), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"song_repeat\""));
        assertTrue(payload.getValue().contains("\"remaining\":12.5"));
      }

      @Test
      void revealAnswerBroadcastsSongRevealContractFrame() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity schedule = schedule(game, 12.5, 8.0);
        when(gameService.findByCode("AKKU", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);

        scheduleServiceImpl.revealAnswer(schedule.getId(), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertEquals("{\"type\":\"song_reveal\"}", payload.getValue());
      }

      @Test
      void progressBroadcastsSongNextFrameWithRecoveryFields() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity lastPlayed = schedule(game, 10.0, 7.0);
        final ScheduleEntity nextSong = schedule(game, 14.0, 6.5);
        when(gameService.findByCode("AKKU", 2)).thenReturn(game);
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(lastPlayed);
        when(scheduleRepository.findNext(game.getId())).thenReturn(Optional.of(nextSong));

        scheduleServiceImpl.progress("AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"song_next\""));
        assertTrue(
            payload
                .getValue()
                .contains("\"songId\":\"" + nextSong.getTrackId().getSongId().getId() + "\""));
        assertTrue(payload.getValue().contains("\"scheduleId\":\"" + nextSong.getId() + "\""));
        assertTrue(payload.getValue().contains("\"question\":\"Question?\""));
        assertTrue(payload.getValue().contains("\"answer\":\"Custom answer\""));
        assertTrue(payload.getValue().contains("\"answerDuration\":6.5"));
        assertTrue(payload.getValue().contains("\"remaining\":14.0"));
      }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class InterruptEventContracts {
      @Mock private TeamService teamService;
      @Mock private InterruptRepository interruptRepository;
      @Mock private GameRepository gameRepository;
      @Mock private ScheduleRepository scheduleRepository;
      @Mock private BroadcastGateway broadcastGateway;
      @Mock private PresenceGateway presenceGateway;

      private InterruptServiceImpl service;

      @BeforeEach
      void setUp() {
        service = new InterruptServiceImpl();
        ReflectionTestUtils.setField(service, "teamService", teamService);
        ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
        ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
        ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
        ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
      }

      @Test
      void teamInterruptBroadcastsPauseFrameWithAnsweringTeamIdAndInterruptId() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity schedule = schedule(game, 20.0, 8.0);
        final TeamEntity team = team(game, "Blue");
        when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
        when(teamService.findById(team.getId())).thenReturn(Optional.of(team));

        service.interrupt("AKKU", team.getId());

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"pause\""));
        assertTrue(payload.getValue().contains("\"answeringTeamId\":\"" + team.getId() + "\""));
        assertTrue(payload.getValue().contains("\"interruptId\":\""));
      }

      @Test
      void systemInterruptBroadcastsPauseFrameWithNullAnsweringTeamId() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity schedule = schedule(game, 20.0, 8.0);
        when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
        when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

        service.interrupt("AKKU", null);

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"pause\""));
        assertTrue(payload.getValue().contains("\"answeringTeamId\":\"null\""));
      }

      @Test
      void answerBroadcastsResolvedGuessContractFrame() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final InterruptEntity answer = answerInterrupt(game, 15.0);
        when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
        when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(teamService.getTeamPoints(answer.getTeamId().getId(), "AKKU")).thenReturn(10);

        service.answer(answer.getId(), new AnswerRequest(false), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertTrue(payload.getValue().contains("\"type\":\"answer\""));
        assertTrue(
            payload.getValue().contains("\"teamId\":\"" + answer.getTeamId().getId() + "\""));
        assertTrue(
            payload
                .getValue()
                .contains("\"scheduleId\":\"" + answer.getScheduleId().getId() + "\""));
        assertTrue(payload.getValue().contains("\"correct\":false"));
      }

      @Test
      void resolveErrorsBroadcastsPreviousScenarioForRecovery() throws Exception {
        final GameEntity game = game("AKKU", 2);
        final ScheduleEntity schedule = schedule(game, 15.0, 7.0);
        when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
        when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

        service.resolveErrors(schedule.getId(), "AKKU");

        final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
        assertEquals("{\"type\":\"error_solved\",\"previousScenario\":4}", payload.getValue());
      }
    }

    private static GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      game.setMaxAlbums(3);
      game.setMaxSongs(3);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name.toLowerCase() + ".png");
      team.setButtonCode(name.substring(0, 1));
      return team;
    }

    private static CategoryEntity category(final GameEntity game) {
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      category.setAlbumId(album());
      return category;
    }

    private static AlbumEntity album() {
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setCustomQuestion("Question?");
      return album;
    }

    private static ScheduleEntity schedule(
        final GameEntity game, final double snippetDuration, final double answerDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setAuthors("Artist");
      song.setName("Track");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(answerDuration);

      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album());
      track.setSongId(song);
      track.setCustomAnswer("Custom answer");

      final CategoryEntity category = category(game);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(category);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(4));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private static InterruptEntity answerInterrupt(
        final GameEntity game, final double snippetDuration) {
      final TeamEntity team = team(game, "Blue");
      final ScheduleEntity schedule = schedule(game, snippetDuration, 7.0);
      final InterruptEntity interrupt = new InterruptEntity(UUID.randomUUID());
      interrupt.setTeamId(team);
      interrupt.setScheduleId(schedule);
      interrupt.setArrivedAt(LocalDateTime.now().minusSeconds(1));
      return interrupt;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class WebSocketJsonContractLockStageTwoTest {
    @Nested
    @ExtendWith(MockitoExtension.class)
    class BroadcastFrames {
      @Mock private TeamService teamService;
      @Mock private InterruptRepository interruptRepository;
      @Mock private GameRepository gameRepository;
      @Mock private ScheduleRepository scheduleRepository;
      @Mock private BroadcastGateway broadcastGateway;
      @Mock private PresenceGateway presenceGateway;

      private InterruptServiceImpl service;
      private final ObjectMapper mapper = new ObjectMapper();

      @BeforeEach
      void setUp() {
        service = new InterruptServiceImpl();
        ReflectionTestUtils.setField(service, "teamService", teamService);
        ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
        ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
        ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
        ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
      }

      @Test
      void pauseFrameLocksRequiredFieldsAndNoLeaks() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final UUID teamId = UUID.randomUUID();
        final GameEntity game = new GameEntity(gameId);
        final TeamEntity team = team(teamId, gameId, "Blue", "blue.png");
        final ScheduleEntity schedule = playingSchedule(gameId, 30.0d, 6.0d, "Question?", "Answer");
        final LocalDateTime startedAt = schedule.getStartedAt();

        when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.findById(teamId)).thenReturn(Optional.of(team));
        when(interruptRepository.findInterrupts(startedAt, schedule.getId()))
            .thenReturn(new ArrayList<>());
        when(interruptRepository.findLastAnswer(startedAt, schedule.getId())).thenReturn(null);
        when(interruptRepository.findLastPause(startedAt, schedule.getId())).thenReturn(null);

        service.interrupt("ROOM", teamId);

        final Map<?, ?> parsed = captureJson();
        assertEquals("pause", parsed.get("type"));
        assertEquals(teamId.toString(), parsed.get("answeringTeamId"));
        assertNotNull(parsed.get("interruptId"));
        assertEquals(3, parsed.size());
        assertForbiddenFieldsForPauseFrame(parsed);
      }

      @Test
      void systemPauseFrameKeepsLiteralNullStringContractAndNoLeaks() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final GameEntity game = new GameEntity(gameId);
        final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());

        when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);

        service.interrupt("ROOM", null);

        final Map<?, ?> parsed = captureJson();
        assertEquals("pause", parsed.get("type"));
        assertEquals("null", parsed.get("answeringTeamId"));
        assertNotNull(parsed.get("interruptId"));
        assertEquals(3, parsed.size());
        assertForbiddenFieldsForPauseFrame(parsed);
      }

      @Test
      void answerFrameLocksBooleanStringIdentifiersAndNoRevealLeak() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final UUID teamId = UUID.randomUUID();
        final GameEntity game = new GameEntity(gameId);
        game.setCode("ROOM");
        final TeamEntity team = team(teamId, gameId, "Blue", "blue.png");
        final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
        final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
        answer.setTeamId(team);
        answer.setScheduleId(schedule);

        when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
        when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
        when(teamService.getTeamPoints(teamId, "ROOM")).thenReturn(40);

        service.answer(answer.getId(), new AnswerRequest(false), "ROOM");

        final Map<?, ?> parsed = captureJson();
        assertEquals("answer", parsed.get("type"));
        assertEquals(teamId.toString(), parsed.get("teamId"));
        assertEquals(schedule.getId().toString(), parsed.get("scheduleId"));
        assertEquals(Boolean.FALSE, parsed.get("correct"));
        assertEquals(4, parsed.size());
        assertFalse(parsed.containsKey("revealed"));
        assertFalse(parsed.containsKey("score"));
        assertFalse(parsed.containsKey("answeringTeam"));
      }

      @Test
      void errorSolvedFrameLocksScenarioTypeAndNoLeaks() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final UUID scheduleId = UUID.randomUUID();
        final GameEntity game = new GameEntity(gameId);
        final ScheduleEntity schedule = new ScheduleEntity(scheduleId);

        when(gameRepository.findByCode("ROOM", 2)).thenReturn(game);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(presenceGateway.areBothPresent("ROOM")).thenReturn(true);
        when(interruptRepository.findPreviousScenarioId(scheduleId)).thenReturn(4);

        service.resolveErrors(scheduleId, "ROOM");

        final Map<?, ?> parsed = captureJson();
        assertEquals("error_solved", parsed.get("type"));
        assertEquals(4, ((Number) parsed.get("previousScenario")).intValue());
        assertEquals(2, parsed.size());
        assertForbiddenPlaybackAndRecoveryFields(parsed);
        verify(interruptRepository).resolveErrors(eq(scheduleId), any());
      }

      private Map<?, ?> captureJson() throws Exception {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(broadcastGateway).broadcast(eq("ROOM"), captor.capture());
        return mapper.readValue(captor.getValue(), HashMap.class);
      }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class RecoveryWelcomeFrames {
      @Mock private TeamService teamService;
      @Mock private InterruptService interruptService;
      @Mock private GameRepository gameRepository;
      @Mock private CategoryRepository categoryRepository;
      @Mock private ScheduleRepository scheduleRepository;
      @Mock private BroadcastGateway broadcastGateway;
      @Mock private PresenceGateway presenceGateway;

      private GameServiceImpl service;
      private final ObjectMapper mapper = new ObjectMapper();

      @BeforeEach
      void setUp() {
        service = new GameServiceImpl();
        ReflectionTestUtils.setField(service, "teamService", teamService);
        ReflectionTestUtils.setField(service, "interruptService", interruptService);
        ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
        ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
        ReflectionTestUtils.setField(service, "broadcastGateway", broadcastGateway);
        ReflectionTestUtils.setField(service, "presenceGateway", presenceGateway);
      }

      @Test
      void normalPlaybackWelcomeLocksPlaybackFieldsAndNoTransientPauseFields() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final GameEntity game = songsGame(gameId);
        final ScheduleEntity schedule = playingSchedule(gameId, 30.0d, 5.0d, "Question?", "Answer");
        final List<Map<String, Object>> scores = scorePayload();

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.getTeamScores("ROOM")).thenReturn(scores);
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(9000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {null, null});

        final Map<?, ?> parsed = serialize(service.contextFetch("ROOM"));

        assertBaseSongWelcome(parsed, schedule);
        assertEquals(9.0d, ((Number) parsed.get("seek")).doubleValue());
        assertEquals(21.0d, ((Number) parsed.get("remaining")).doubleValue());
        assertTrue(parsed.containsKey("scores"));
        assertFalse(parsed.containsKey("answeringTeam"));
        assertFalse(parsed.containsKey("interruptId"));
        assertFalse(parsed.containsKey("error"));
        assertFalse(parsed.containsKey("revealed"));
      }

      @Test
      void answeringTeamWelcomeLocksNestedTeamAndInterruptIdWithoutErrorField() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final GameEntity game = songsGame(gameId);
        final ScheduleEntity schedule = playingSchedule(gameId, 30.0d, 5.0d, "Question?", "Answer");
        final TeamEntity team = team(UUID.randomUUID(), gameId, "Blue", "blue.png");
        final InterruptEntity answerInterrupt = new InterruptEntity(UUID.randomUUID());
        answerInterrupt.setTeamId(team);
        answerInterrupt.setCorrect(null);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.getTeamScores("ROOM")).thenReturn(scorePayload());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(4000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {answerInterrupt, null});

        final Map<?, ?> parsed = serialize(service.contextFetch("ROOM"));

        assertBaseSongWelcome(parsed, schedule);
        final Map<?, ?> answeringTeam = (Map<?, ?>) parsed.get("answeringTeam");
        assertEquals(team.getId().toString(), answeringTeam.get("id"));
        assertEquals("Blue", answeringTeam.get("name"));
        assertEquals("blue.png", answeringTeam.get("image"));
        assertEquals(answerInterrupt.getId().toString(), parsed.get("interruptId"));
        assertFalse(parsed.containsKey("error"));
        assertFalse(parsed.containsKey("revealed"));
      }

      @Test
      void technicalPauseWelcomeLocksErrorFlagWithoutTeamSpecificFields() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final GameEntity game = songsGame(gameId);
        final ScheduleEntity schedule = playingSchedule(gameId, 30.0d, 5.0d, "Question?", "Answer");
        final InterruptEntity systemPause = new InterruptEntity(UUID.randomUUID());
        systemPause.setResolvedAt(null);

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.getTeamScores("ROOM")).thenReturn(scorePayload());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(4000L);
        when(interruptService.getLastTwoInterrupts(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(new InterruptEntity[] {null, systemPause});

        final Map<?, ?> parsed = serialize(service.contextFetch("ROOM"));

        assertBaseSongWelcome(parsed, schedule);
        assertEquals(Boolean.TRUE, parsed.get("error"));
        assertFalse(parsed.containsKey("answeringTeam"));
        assertFalse(parsed.containsKey("interruptId"));
        assertFalse(parsed.containsKey("revealed"));
      }

      @Test
      void endedNotRevealedWelcomeLocksRevealedFalseAndDropsPlaybackProgressFields()
          throws Exception {
        final UUID gameId = UUID.randomUUID();
        final GameEntity game = songsGame(gameId);
        final ScheduleEntity schedule = playingSchedule(gameId, 10.0d, 5.0d, "Question?", "Answer");

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.getTeamScores("ROOM")).thenReturn(scorePayload());
        when(interruptService.calculateSeek(schedule.getStartedAt(), schedule.getId()))
            .thenReturn(12000L);

        final Map<?, ?> parsed = serialize(service.contextFetch("ROOM"));

        assertBaseSongWelcome(parsed, schedule);
        assertEquals(Boolean.FALSE, parsed.get("revealed"));
        assertFalse(parsed.containsKey("seek"));
        assertFalse(parsed.containsKey("remaining"));
        assertFalse(parsed.containsKey("error"));
        assertFalse(parsed.containsKey("answeringTeam"));
        assertFalse(parsed.containsKey("interruptId"));
      }

      @Test
      void revealedWelcomeLocksBravoAndDropsProgressAndErrorFields() throws Exception {
        final UUID gameId = UUID.randomUUID();
        final UUID bravo = UUID.randomUUID();
        final GameEntity game = songsGame(gameId);
        final ScheduleEntity schedule = playingSchedule(gameId, 10.0d, 5.0d, "Question?", "Answer");
        schedule.setRevealedAt(LocalDateTime.now());

        when(gameRepository.findByCode("ROOM")).thenReturn(Optional.of(game));
        when(scheduleRepository.findLastPlayed(gameId)).thenReturn(schedule);
        when(teamService.getTeamScores("ROOM")).thenReturn(scorePayload());
        when(interruptService.findCorrectAnswer(schedule.getId(), "ROOM")).thenReturn(bravo);

        final Map<?, ?> parsed = serialize(service.contextFetch("ROOM"));

        assertBaseSongWelcome(parsed, schedule);
        assertEquals(Boolean.TRUE, parsed.get("revealed"));
        assertEquals(bravo.toString(), parsed.get("bravo"));
        assertFalse(parsed.containsKey("seek"));
        assertFalse(parsed.containsKey("remaining"));
        assertFalse(parsed.containsKey("error"));
        assertFalse(parsed.containsKey("answeringTeam"));
        assertFalse(parsed.containsKey("interruptId"));
      }

      private void assertBaseSongWelcome(final Map<?, ?> parsed, final ScheduleEntity schedule) {
        assertEquals("welcome", parsed.get("type"));
        assertEquals("songs", parsed.get("stage"));
        assertEquals(schedule.getTrackId().getSongId().getId().toString(), parsed.get("songId"));
        assertEquals("Question?", parsed.get("question"));
        assertEquals("Answer", parsed.get("answer"));
        assertEquals(schedule.getId().toString(), parsed.get("scheduleId"));
        assertEquals(5.0d, ((Number) parsed.get("answerDuration")).doubleValue());
      }

      private Map<?, ?> serialize(final HashMap<String, Object> payload) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(payload), HashMap.class);
      }
    }

    private static void assertForbiddenFieldsForPauseFrame(final Map<?, ?> parsed) {
      assertFalse(parsed.containsKey("songId"));
      assertFalse(parsed.containsKey("question"));
      assertFalse(parsed.containsKey("answer"));
      assertFalse(parsed.containsKey("scheduleId"));
      assertFalse(parsed.containsKey("answerDuration"));
      assertFalse(parsed.containsKey("seek"));
      assertFalse(parsed.containsKey("remaining"));
      assertFalse(parsed.containsKey("revealed"));
      assertFalse(parsed.containsKey("bravo"));
      assertFalse(parsed.containsKey("answeringTeam"));
      assertFalse(parsed.containsKey("error"));
    }

    private static void assertForbiddenPlaybackAndRecoveryFields(final Map<?, ?> parsed) {
      assertFalse(parsed.containsKey("songId"));
      assertFalse(parsed.containsKey("question"));
      assertFalse(parsed.containsKey("answer"));
      assertFalse(parsed.containsKey("scheduleId"));
      assertFalse(parsed.containsKey("answerDuration"));
      assertFalse(parsed.containsKey("seek"));
      assertFalse(parsed.containsKey("remaining"));
      assertFalse(parsed.containsKey("revealed"));
      assertFalse(parsed.containsKey("bravo"));
      assertFalse(parsed.containsKey("answeringTeam"));
      assertFalse(parsed.containsKey("interruptId"));
      assertFalse(parsed.containsKey("error"));
    }

    private static GameEntity songsGame(final UUID gameId) {
      final GameEntity game = new GameEntity(gameId);
      game.setStage(2);
      return game;
    }

    private static ScheduleEntity playingSchedule(
        final UUID gameId,
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
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(5));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private static TeamEntity team(
        final UUID teamId, final UUID gameId, final String name, final String image) {
      final TeamEntity team = new TeamEntity(teamId);
      final GameEntity game = new GameEntity(gameId);
      game.setCode("ROOM");
      team.setGameId(game);
      team.setName(name);
      team.setImage(image);
      return team;
    }

    private static List<Map<String, Object>> scorePayload() {
      final Map<String, Object> team = new HashMap<>();
      team.put("teamId", UUID.randomUUID().toString());
      team.put("name", "Blue");
      team.put("image", "blue.png");
      team.put("score", 30);
      return List.of(team);
    }
  }
}
