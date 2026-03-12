package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.GuessNotAllowedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.common.exception.UnauthorizedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.SongEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

public class InterruptServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class InterruptCoreFlow {
    @Mock private TeamService teamService;
    @Mock private InterruptRepository interruptRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private InterruptServiceImpl interruptService;

    @Test
    void calculateSeekSubtractsOuterInterruptDurations() {
      final LocalDateTime start = LocalDateTime.now().minusSeconds(20);
      final UUID scheduleId = UUID.randomUUID();
      when(interruptRepository.findInterrupts(start, scheduleId))
          .thenReturn(
              new ArrayList<>(
                  List.of(
                      new InterruptFrame(start.plusSeconds(5), start.plusSeconds(10)),
                      new InterruptFrame(start.plusSeconds(18), start.plusSeconds(20)))));

      final long seek = interruptService.calculateSeek(start, scheduleId);

      assertEquals(13000L, seek);
    }

    @Test
    void getLastTwoInterruptsReturnsTeamThenSystemInterrupt() {
      final LocalDateTime start = LocalDateTime.now().minusSeconds(10);
      final UUID scheduleId = UUID.randomUUID();
      final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(interruptRepository.findLastAnswer(start, scheduleId)).thenReturn(answer);
      when(interruptRepository.findLastPause(start, scheduleId)).thenReturn(pause);

      final InterruptEntity[] result = interruptService.getLastTwoInterrupts(start, scheduleId);

      assertEquals(answer, result[0]);
      assertEquals(pause, result[1]);
    }

    @Test
    void interruptRejectsUnknownTeamId() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      final UUID missingTeamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(missingTeamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.interrupt("AKKU", missingTeamId));

      assertEquals("Team with id" + missingTeamId + " does not exist", exception.getMessage());
    }

    @Test
    void interruptRejectsTeamsFromAnotherGame() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      final TeamEntity foreignTeam = team(game("ZZZZ", 2), "Foreign");
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(foreignTeam.getId())).thenReturn(Optional.of(foreignTeam));

      final UnauthorizedException exception =
          assertThrows(
              UnauthorizedException.class,
              () -> interruptService.interrupt("AKKU", foreignTeam.getId()));

      assertTrue(exception.getMessage().contains("isn't part of the game AKKU"));
    }

    @Test
    void interruptRejectsSecondGuessFromSameTeamForSong() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      final InterruptEntity priorGuess = new InterruptEntity(UUID.randomUUID());
      priorGuess.setTeamId(team);
      schedule.setInterruptList(new ArrayList<>(List.of(priorGuess)));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals(
          "Team with id " + team.getId() + " already made a guess for this song",
          exception.getMessage());
    }

    @Test
    void interruptRejectsAlreadyFinishedSong() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = playingSchedule(game, 5.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertTrue(exception.getMessage().contains("is no longer playing"));
    }

    @Test
    void interruptRejectsWhenAnotherTeamIsStillAnswering() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      final InterruptEntity activeAnswer = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(activeAnswer);

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals("Someone's already guessing", exception.getMessage());
    }

    @Test
    void interruptRejectsWhenSystemPauseIsActive() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pause);

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals("The game is paused", exception.getMessage());
    }

    @Test
    void interruptCreatesAndBroadcastsSystemPause() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = playingSchedule(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

      interruptService.interrupt("AKKU", null);

      verify(interruptRepository).saveAndFlush(any(InterruptEntity.class));
      verify(broadcastGateway).broadcast(anyString(), anyString());
    }

    @Test
    void interruptCreatesAndBroadcastsTeamPause() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = freshSchedule(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);

      interruptService.interrupt("AKKU", team.getId());

      final ArgumentCaptor<InterruptEntity> interruptCaptor =
          ArgumentCaptor.forClass(InterruptEntity.class);
      verify(interruptRepository).saveAndFlush(interruptCaptor.capture());
      assertEquals(team.getId(), interruptCaptor.getValue().getTeamId().getId());
      verify(broadcastGateway).broadcast(anyString(), anyString());
    }

    @Test
    void resolveErrorsRejectsMissingSchedule() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final UUID scheduleId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.resolveErrors(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void resolveErrorsRequiresBothApps() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> interruptService.resolveErrors(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void resolveErrorsResolvesAndBroadcastsPreviousScenario() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

      interruptService.resolveErrors(schedule.getId(), "AKKU");

      verify(interruptRepository).resolveErrors(any(UUID.class), any(LocalDateTime.class));
      verify(broadcastGateway).broadcast(anyString(), anyString());
    }

    @Test
    void answerRejectsAlreadyResolvedGuess() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game);
      answer.setCorrect(true);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
    }

    @Test
    void answerRejectsRoomMismatch() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game("ZZZZ", 2));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the answer", exception.getMessage());
    }

    @Test
    void answerCorrectAwardsPointsRevealsSongAndBroadcasts() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game);
      final UUID teamId = answer.getTeamId().getId();
      final UUID scheduleId = answer.getScheduleId().getId();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(teamId, "AKKU")).thenReturn(20);

      interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU");

      assertEquals(Boolean.TRUE, answer.isCorrect());
      assertEquals(50, answer.getScoreOrScenarioId());
      assertTrue(answer.getResolvedAt() != null);
      assertTrue(answer.getScheduleId().getRevealedAt() != null);
      verify(interruptRepository).resolveErrors(scheduleId, answer.getResolvedAt());
      verify(teamService).saveTeamAnswer(teamId, scheduleId, 50, "AKKU");
      verify(scheduleRepository).saveAndFlush(answer.getScheduleId());
      verify(broadcastGateway).broadcast(anyString(), anyString());
    }

    @Test
    void answerWrongAppliesPenaltyWithoutRevealingSong() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game);
      final UUID teamId = answer.getTeamId().getId();
      final UUID scheduleId = answer.getScheduleId().getId();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(teamId, "AKKU")).thenReturn(20);

      interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU");

      assertEquals(Boolean.FALSE, answer.isCorrect());
      assertEquals(10, answer.getScoreOrScenarioId());
      verify(scheduleRepository, never()).saveAndFlush(any());
      verify(teamService).saveTeamAnswer(teamId, scheduleId, 10, "AKKU");
    }

    @Test
    void savePreviousScenarioRejectsUnsupportedScenarioIds() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(3, "AKKU"));

      assertEquals("Scenario has to be a number between 0 and 4 but not 3", exception.getMessage());
    }

    @Test
    void savePreviousScenarioStoresScenarioOnLatestPause() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = freshSchedule(game, 30.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pause);

      interruptService.savePreviousScenario(4, "AKKU");

      assertEquals(4, pause.getScoreOrScenarioId());
      verify(interruptRepository).saveAndFlush(pause);
    }

    private GameEntity game(final String code, final int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      return game;
    }

    private TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name + ".png");
      return team;
    }

    private ScheduleEntity playingSchedule(final GameEntity game, final double snippetDuration) {
      return schedule(game, snippetDuration, 10);
    }

    private ScheduleEntity freshSchedule(final GameEntity game, final double snippetDuration) {
      return schedule(game, snippetDuration, 0);
    }

    private ScheduleEntity schedule(
        final GameEntity game, final double snippetDuration, final long startedSecondsAgo) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setSongId(song);
      track.setAlbumId(album);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(startedSecondsAgo));
      return schedule;
    }

    private InterruptEntity answerInterrupt(final GameEntity game) {
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = freshSchedule(game, 30.0);
      final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
      answer.setTeamId(team);
      answer.setScheduleId(schedule);
      answer.setArrivedAt(LocalDateTime.now().minusSeconds(2));
      return answer;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class InterruptValidationAndEligibility {
    @Mock private TeamService teamService;
    @Mock private InterruptRepository interruptRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private InterruptServiceImpl interruptService;

    @Test
    void calculateSeekTreatsNullInterruptListAsStillPlaying() {
      final LocalDateTime start = LocalDateTime.now().minusSeconds(2);

      when(interruptRepository.findInterrupts(eq(start), any(UUID.class))).thenReturn(null);

      final long seek = interruptService.calculateSeek(start, UUID.randomUUID());

      assertTrue(seek >= 0L);
      assertTrue(seek <= 2500L);
    }

    @Test
    void interruptRejectsRevealedSongsEvenWhenSnippetTimeWouldOtherwiseRemain() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = schedule(game, 30.0);
      schedule.setRevealedAt(LocalDateTime.now());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertTrue(exception.getMessage().contains("is no longer playing"));
    }

    @Test
    void interruptAllowsTeamBuzzWhenEarlierInterruptsWereFullyResolved() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final TeamEntity team = team(game, "Sharks");
      final ScheduleEntity schedule = schedule(game, 30.0);
      final InterruptEntity previousAnswer = new InterruptEntity(UUID.randomUUID());
      previousAnswer.setTeamId(team(game, "Other"));
      previousAnswer.setCorrect(false);
      previousAnswer.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      final InterruptEntity previousPause = new InterruptEntity(UUID.randomUUID());
      previousPause.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(previousAnswer);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(previousPause);

      interruptService.interrupt("AKKU", team.getId());

      verify(interruptRepository).saveAndFlush(any(InterruptEntity.class));
      verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
    }

    @Test
    void resolveErrorsBroadcastsPreviousScenarioAndTimestampResolution() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

      interruptService.resolveErrors(schedule.getId(), "AKKU");

      verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      final ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
      verify(broadcastGateway).broadcast(eq("AKKU"), payload.capture());
      assertTrue(payload.getValue().contains("\"type\":\"error_solved\""));
      assertTrue(payload.getValue().contains("\"previousScenario\":4"));
    }

    @Test
    void answerRequiresBothAppsPresent() throws Exception {
      final GameEntity game = game("AKKU", 2);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> interruptService.answer(UUID.randomUUID(), new AnswerRequest(true), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void answerRejectsMissingInterrupt() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final UUID answerId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answerId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.answer(answerId, new AnswerRequest(true), "AKKU"));

      assertEquals("Answer with id " + answerId + " does not exist", exception.getMessage());
    }

    @Test
    void answerRejectsRoomCodeMismatch() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game("ZZZZ", 2), 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the answer", exception.getMessage());
    }

    @Test
    void answerRejectsAlreadyResolvedGuess() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game, 30.0);
      answer.setCorrect(Boolean.TRUE);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
    }

    @Test
    void answerMarksCorrectGuessPersistsRevealAndSendsUpdatedScore() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(answer.getTeamId().getId(), "AKKU")).thenReturn(50);

      interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU");

      assertTrue(answer.isCorrect());
      assertNotNull(answer.getResolvedAt());
      assertEquals(80, answer.getScoreOrScenarioId());
      assertNotNull(answer.getScheduleId().getRevealedAt());
      verify(interruptRepository)
          .resolveErrors(eq(answer.getScheduleId().getId()), any(LocalDateTime.class));
      verify(scheduleRepository).saveAndFlush(answer.getScheduleId());
      verify(teamService)
          .saveTeamAnswer(answer.getTeamId().getId(), answer.getScheduleId().getId(), 80, "AKKU");
    }

    @Test
    void answerMarksWrongGuessWithoutRevealingSong() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final InterruptEntity answer = answerInterrupt(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(answer.getTeamId().getId(), "AKKU")).thenReturn(15);

      interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU");

      assertFalse(answer.isCorrect());
      assertEquals(5, answer.getScoreOrScenarioId());
      assertNull(answer.getScheduleId().getRevealedAt());
      verify(scheduleRepository, never()).saveAndFlush(any());
      verify(teamService)
          .saveTeamAnswer(answer.getTeamId().getId(), answer.getScheduleId().getId(), 5, "AKKU");
    }

    @Test
    void findCorrectAnswerDelegatesToRepository() throws Exception {
      final UUID scheduleId = UUID.randomUUID();
      final UUID winnerId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game("AKKU", 2));
      when(interruptRepository.findCorrectAnswer(scheduleId)).thenReturn(winnerId);

      assertEquals(winnerId, interruptService.findCorrectAnswer(scheduleId, "AKKU"));
    }

    @Test
    void savePreviousScenarioRejectsInvalidValuesAndReservedScenarioThree() {
      final InvalidArgumentException negative =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(-1, "AKKU"));
      final InvalidArgumentException reserved =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(3, "AKKU"));
      final InvalidArgumentException tooHigh =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(5, "AKKU"));

      assertEquals("Scenario has to be a number between 0 and 4 but not 3", negative.getMessage());
      assertEquals("Scenario has to be a number between 0 and 4 but not 3", reserved.getMessage());
      assertEquals("Scenario has to be a number between 0 and 4 but not 3", tooHigh.getMessage());
    }

    @Test
    void savePreviousScenarioDoesNothingWhenNoSongHasBeenPlayedYet() throws Exception {
      final GameEntity game = game("AKKU", 2);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      interruptService.savePreviousScenario(2, "AKKU");

      verify(interruptRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePreviousScenarioStoresScenarioOnLatestPause() throws Exception {
      final GameEntity game = game("AKKU", 2);
      final ScheduleEntity schedule = schedule(game, 30.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pause);

      interruptService.savePreviousScenario(4, "AKKU");

      assertEquals(4, pause.getScoreOrScenarioId());
      verify(interruptRepository).saveAndFlush(pause);
    }

    private GameEntity game(String code, int stage) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(stage);
      return game;
    }

    private TeamEntity team(GameEntity game, String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name.toLowerCase() + ".png");
      return team;
    }

    private ScheduleEntity schedule(GameEntity game, double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setAuthors("Artist");
      song.setName("Track");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(7.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(null);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(4));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private InterruptEntity answerInterrupt(GameEntity game, double snippetDuration) {
      final TeamEntity team = team(game, "Blue");
      final ScheduleEntity schedule = schedule(game, snippetDuration);
      final InterruptEntity interrupt = new InterruptEntity(UUID.randomUUID());
      interrupt.setTeamId(team);
      interrupt.setScheduleId(schedule);
      interrupt.setArrivedAt(LocalDateTime.now().minusSeconds(1));
      return interrupt;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class InterruptRecoveryAndBranchCoverage {
    @Mock private TeamService teamService;
    @Mock private InterruptRepository interruptRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private InterruptServiceImpl interruptService;

    @Test
    void calculateSeekTreatsNullInterruptListAsStillPlayingUntilNow() {
      final LocalDateTime start = LocalDateTime.now().minusSeconds(4);
      final UUID scheduleId = UUID.randomUUID();
      when(interruptRepository.findInterrupts(start, scheduleId)).thenReturn(null);

      final long seek = interruptService.calculateSeek(start, scheduleId);

      assertNotNull(seek);
    }

    @Test
    void calculateSeekAddsSyntheticOpenFrameWhenLastInterruptResolved() {
      final LocalDateTime start = LocalDateTime.now().minusSeconds(20);
      final UUID scheduleId = UUID.randomUUID();
      final List<InterruptFrame> frames =
          new ArrayList<>(List.of(new InterruptFrame(start.plusSeconds(2), start.plusSeconds(5))));
      when(interruptRepository.findInterrupts(start, scheduleId)).thenReturn(frames);

      final long seek = interruptService.calculateSeek(start, scheduleId);

      // 0-2s played before pause, then 5s-now after pause. Any positive result means synthetic tail
      // was applied.
      assertNotNull(seek);
      org.junit.jupiter.api.Assertions.assertTrue(seek >= 2_000L);
    }

    @Test
    void interruptAllowsSystemPauseWithNullTeamId() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

      interruptService.interrupt("AKKU", null);

      final ArgumentCaptor<InterruptEntity> captor = ArgumentCaptor.forClass(InterruptEntity.class);
      verify(interruptRepository).saveAndFlush(captor.capture());
      assertNull(captor.getValue().getTeamId());
      assertEquals(schedule, captor.getValue().getScheduleId());
      verify(broadcastGateway)
          .broadcast(
              eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"answeringTeamId\":\"null\""));
    }

    @Test
    void interruptRejectsUnknownTeam() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.interrupt("AKKU", teamId));

      assertEquals("Team with id" + teamId + " does not exist", exception.getMessage());
    }

    @Test
    void interruptRejectsTeamFromDifferentGame() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity outsider = team(game("DIFF"), "Blue");
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(outsider.getId())).thenReturn(Optional.of(outsider));

      final UnauthorizedException exception =
          assertThrows(
              UnauthorizedException.class,
              () -> interruptService.interrupt("AKKU", outsider.getId()));

      assertEquals(
          "Team with id " + outsider.getId() + " isn't part of the game AKKU",
          exception.getMessage());
    }

    @Test
    void interruptRejectsDuplicateAttemptBySameTeamForSong() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity team = team(game, "Red");
      final InterruptEntity previous = new InterruptEntity(UUID.randomUUID());
      previous.setTeamId(team);
      schedule.setInterruptList(new ArrayList<>(List.of(previous)));

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals(
          "Team with id " + team.getId() + " already made a guess for this song",
          exception.getMessage());
    }

    @Test
    void interruptRejectsWhenSongAlreadyRevealed() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      schedule.setRevealedAt(LocalDateTime.now().minusSeconds(1));
      final TeamEntity team = team(game, "Red");

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals(
          "Song " + schedule.getTrackId().getSongId().getId() + " is no longer playing",
          exception.getMessage());
    }

    @Test
    void interruptRejectsWhenSongFinishedByTime() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 5.0);
      final TeamEntity team = team(game, "Red");

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals(
          "Song " + schedule.getTrackId().getSongId().getId() + " is no longer playing",
          exception.getMessage());
    }

    @Test
    void interruptRejectsWhenAnotherTeamIsAlreadyGuessing() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity team = team(game, "Red");
      final InterruptEntity pending = new InterruptEntity(UUID.randomUUID());
      pending.setTeamId(team(game, "Blue"));
      pending.setCorrect(null);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pending);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals("Someone's already guessing", exception.getMessage());
    }

    @Test
    void interruptRejectsWhenSystemPauseIsStillActive() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity team = team(game, "Red");
      final InterruptEntity activePause = new InterruptEntity(UUID.randomUUID());
      activePause.setResolvedAt(null);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(activePause);

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals("The game is paused", exception.getMessage());
    }

    @Test
    void interruptPersistsNewTeamInterruptAndBroadcastsExactPayload() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity team = team(game, "Red");

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);

      interruptService.interrupt("AKKU", team.getId());

      final ArgumentCaptor<InterruptEntity> captor = ArgumentCaptor.forClass(InterruptEntity.class);
      verify(interruptRepository).saveAndFlush(captor.capture());
      assertEquals(team.getId(), captor.getValue().getTeamId().getId());
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"pause\""));
      verify(broadcastGateway)
          .broadcast(
              eq("AKKU"),
              org.mockito.ArgumentMatchers.contains(
                  "\"answeringTeamId\":\"" + team.getId() + "\""));
    }

    @Test
    void resolveErrorsRequiresBothApps() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> interruptService.resolveErrors(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(any(), any());
    }

    @Test
    void resolveErrorsClosesOnlySystemErrorsAndBroadcastsScenarioRecovery() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

      interruptService.resolveErrors(schedule.getId(), "AKKU");

      verify(interruptRepository).resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"previousScenario\":4"));
    }

    @Test
    void answerRejectsWhenAppsAreNotBothPresent() throws DerivedException {
      final GameEntity game = game("AKKU");
      final UUID answerId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> interruptService.answer(answerId, new AnswerRequest(true), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
    }

    @Test
    void answerRejectsWhenAnswerDoesNotExist() throws DerivedException {
      final GameEntity game = game("AKKU");
      final UUID answerId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answerId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.answer(answerId, new AnswerRequest(true), "AKKU"));

      assertEquals("Answer with id " + answerId + " does not exist", exception.getMessage());
    }

    @Test
    void answerRejectsWhenRoomCodeDoesNotMatchAnswerTeamGame() throws DerivedException {
      final GameEntity game = game("AKKU");
      final TeamEntity team = team(game("BLAH"), "Red");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity answer = answerInterrupt(team, schedule);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("Room code AKKU isn't consistent with the answer", exception.getMessage());
    }

    @Test
    void answerRejectsAlreadyAnsweredInterrupt() throws DerivedException {
      final GameEntity game = game("AKKU");
      final TeamEntity team = team(game, "Red");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity answer = answerInterrupt(team, schedule);
      answer.setCorrect(Boolean.FALSE);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
    }

    @Test
    void answerRejectsAlreadyResolvedInterrupt() throws DerivedException {
      final GameEntity game = game("AKKU");
      final TeamEntity team = team(game, "Red");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity answer = answerInterrupt(team, schedule);
      answer.setResolvedAt(LocalDateTime.now().minusSeconds(1));

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
    }

    @Test
    void answerMarksCorrectAnswerRevealsScheduleUpdatesScoreAndBroadcastsInOrder()
        throws Exception {
      final GameEntity game = game("AKKU");
      final TeamEntity team = team(game, "Red");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity answer = answerInterrupt(team, schedule);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(team.getId(), "AKKU")).thenReturn(40);

      interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU");

      assertEquals(Boolean.TRUE, answer.isCorrect());
      assertNotNull(answer.getResolvedAt());
      assertEquals(70, answer.getScoreOrScenarioId());
      assertNotNull(schedule.getRevealedAt());

      final org.mockito.InOrder inOrder =
          inOrder(interruptRepository, scheduleRepository, teamService, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder.verify(interruptRepository).save(answer);
      inOrder.verify(scheduleRepository).saveAndFlush(schedule);
      inOrder.verify(teamService).saveTeamAnswer(team.getId(), schedule.getId(), 70, "AKKU");
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"correct\":true"));
    }

    @Test
    void answerMarksWrongAnswerWithoutRevealingSongAndStillSavesPenalty() throws Exception {
      final GameEntity game = game("AKKU");
      final TeamEntity team = team(game, "Red");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity answer = answerInterrupt(team, schedule);

      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(team.getId(), "AKKU")).thenReturn(15);

      interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU");

      assertEquals(Boolean.FALSE, answer.isCorrect());
      assertEquals(5, answer.getScoreOrScenarioId());
      assertNull(schedule.getRevealedAt());
      verify(scheduleRepository, never()).saveAndFlush(any());
      verify(teamService).saveTeamAnswer(team.getId(), schedule.getId(), 5, "AKKU");
      verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"correct\":false"));
    }

    @Test
    void findCorrectAnswerDelegatesToRepositoryAfterStageValidation() throws Exception {
      final GameEntity game = game("AKKU");
      final UUID scheduleId = UUID.randomUUID();
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(interruptRepository.findCorrectAnswer(scheduleId)).thenReturn(teamId);

      final UUID result = interruptService.findCorrectAnswer(scheduleId, "AKKU");

      assertEquals(teamId, result);
      verify(interruptRepository).findCorrectAnswer(scheduleId);
    }

    @Test
    void savePreviousScenarioRejectsInvalidScenarioIds() {
      final InvalidArgumentException negative =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(-1, "AKKU"));
      final InvalidArgumentException forbidden =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(3, "AKKU"));
      final InvalidArgumentException tooHigh =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(5, "AKKU"));

      assertEquals("Scenario has to be a number between 0 and 4 but not 3", negative.getMessage());
      assertEquals("Scenario has to be a number between 0 and 4 but not 3", forbidden.getMessage());
      assertEquals("Scenario has to be a number between 0 and 4 but not 3", tooHigh.getMessage());
    }

    @Test
    void savePreviousScenarioDoesNothingWhenNoSongWasPlayedYet() throws Exception {
      final GameEntity game = game("AKKU");
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      interruptService.savePreviousScenario(4, "AKKU");

      verify(interruptRepository, never()).findLastPause(any(), any());
      verify(interruptRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePreviousScenarioDoesNothingWhenNoPauseExists() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);

      interruptService.savePreviousScenario(2, "AKKU");

      verify(interruptRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePreviousScenarioUpdatesPauseScenarioWhenPauseExists() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pause);

      interruptService.savePreviousScenario(4, "AKKU");

      assertEquals(4, pause.getScoreOrScenarioId());
      verify(interruptRepository).saveAndFlush(pause);
    }

    private GameEntity game(final String roomCode) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(roomCode);
      game.setStage(2);
      return game;
    }

    private TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      return team;
    }

    private ScheduleEntity schedule(final GameEntity game, final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(7.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID(), "Album");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(category);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(8));
      return schedule;
    }

    private InterruptEntity answerInterrupt(final TeamEntity team, final ScheduleEntity schedule) {
      final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
      answer.setTeamId(team);
      answer.setScheduleId(schedule);
      return answer;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class InterruptSupportAndCalculations {
    @Mock private TeamService teamService;
    @Mock private InterruptRepository interruptRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private InterruptServiceImpl interruptService;

    @Test
    void calculateSeekTreatsNullInterruptListAsRunningPlayback() {
      final LocalDateTime startedAt = LocalDateTime.now().minusSeconds(2);
      final UUID scheduleId = UUID.randomUUID();
      when(interruptRepository.findInterrupts(startedAt, scheduleId)).thenReturn(null);

      final long seek = interruptService.calculateSeek(startedAt, scheduleId);

      assertTruePositive(seek);
    }

    @Test
    void resolveErrorsRejectsUnknownSchedule() throws DerivedException {
      final GameEntity game = game("AKKU");
      final UUID scheduleId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.resolveErrors(scheduleId, "AKKU"));

      assertEquals("Order with id " + scheduleId + " does not exist", exception.getMessage());
    }

    @Test
    void resolveErrorsRejectsWhenAppsMissing() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);

      final AppNotRegisteredException exception =
          assertThrows(
              AppNotRegisteredException.class,
              () -> interruptService.resolveErrors(schedule.getId(), "AKKU"));

      assertEquals("Both apps need to be present in order to continue", exception.getMessage());
      verify(interruptRepository, never()).resolveErrors(eq(schedule.getId()), any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void resolveErrorsResolvesBeforeBroadcastingPreviousScenario() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

      interruptService.resolveErrors(schedule.getId(), "AKKU");

      final InOrder inOrder = inOrder(interruptRepository, broadcastGateway);
      inOrder.verify(interruptRepository).findPreviousScenarioId(schedule.getId());
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(schedule.getId()), any(LocalDateTime.class));
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"previousScenario\":4"));
    }

    @Test
    void answerRejectsWhenAlreadyResolvedByTimestamp() throws DerivedException {
      final GameEntity game = game("AKKU");
      final InterruptEntity answer = answer(game, true);
      answer.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
    }

    @Test
    void answerWrongGuessDoesNotRevealSongAndPersistsPenaltyBeforeBroadcast() throws Exception {
      final GameEntity game = game("AKKU");
      final InterruptEntity answer = answer(game, null);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(answer.getTeamId().getId(), "AKKU")).thenReturn(10);

      interruptService.answer(answer.getId(), new AnswerRequest(false), "AKKU");

      assertEquals(false, answer.isCorrect());
      assertEquals(Integer.valueOf(0), answer.getScoreOrScenarioId());
      final InOrder inOrder = inOrder(interruptRepository, teamService, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(answer.getScheduleId().getId()), any(LocalDateTime.class));
      inOrder.verify(interruptRepository).save(answer);
      inOrder
          .verify(teamService)
          .saveTeamAnswer(answer.getTeamId().getId(), answer.getScheduleId().getId(), 0, "AKKU");
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"correct\":false"));
      verify(scheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    void findCorrectAnswerDelegatesAfterStageValidation() throws Exception {
      final GameEntity game = game("AKKU");
      final UUID scheduleId = UUID.randomUUID();
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(interruptRepository.findCorrectAnswer(scheduleId)).thenReturn(teamId);

      final UUID result = interruptService.findCorrectAnswer(scheduleId, "AKKU");

      assertEquals(teamId, result);
      verify(gameRepository).findByCode("AKKU", 2);
      verify(interruptRepository).findCorrectAnswer(scheduleId);
    }

    @Test
    void savePreviousScenarioRejectsForbiddenScenarioThree() {
      final InvalidArgumentException exception =
          assertThrows(
              InvalidArgumentException.class,
              () -> interruptService.savePreviousScenario(3, "AKKU"));

      assertEquals("Scenario has to be a number between 0 and 4 but not 3", exception.getMessage());
    }

    @Test
    void savePreviousScenarioDoesNothingWhenLastSongMissing() throws Exception {
      final GameEntity game = game("AKKU");
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(null);

      assertDoesNotThrow(() -> interruptService.savePreviousScenario(2, "AKKU"));

      verify(interruptRepository, never()).findLastPause(any(), any());
      verify(interruptRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePreviousScenarioDoesNothingWhenPauseMissing() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);

      assertDoesNotThrow(() -> interruptService.savePreviousScenario(2, "AKKU"));

      verify(interruptRepository, never()).saveAndFlush(any());
    }

    @Test
    void savePreviousScenarioUpdatesPauseScenarioId() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 20.0);
      final InterruptEntity pause = new InterruptEntity(UUID.randomUUID());
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(pause);

      interruptService.savePreviousScenario(4, "AKKU");

      assertEquals(Integer.valueOf(4), pause.getScoreOrScenarioId());
      verify(interruptRepository).saveAndFlush(pause);
    }

    private static void assertTruePositive(final long seek) {
      org.junit.jupiter.api.Assertions.assertTrue(seek >= 1500L && seek <= 4000L);
    }

    private static GameEntity game(final String code) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(2);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static ScheduleEntity schedule(final GameEntity game, final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setName("Song");
      song.setAuthors("Artist");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(8.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setName("Album");
      album.setCustomQuestion("Question");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");
      final com.cevapinxile.cestereg.persistence.entity.CategoryEntity category =
          new com.cevapinxile.cestereg.persistence.entity.CategoryEntity(UUID.randomUUID());
      category.setGameId(game);
      category.setAlbumId(album);
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setCategoryId(category);
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(2));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private static InterruptEntity answer(final GameEntity game, final Boolean correct) {
      final ScheduleEntity schedule = schedule(game, 20.0);
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName("Red");
      team.setImage("red.png");
      final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
      answer.setTeamId(team);
      answer.setScheduleId(schedule);
      answer.setCorrect(correct);
      answer.setArrivedAt(LocalDateTime.now().minusSeconds(1));
      return answer;
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  class InterruptOrderingAndRegressionGuards {
    @Mock private TeamService teamService;
    @Mock private InterruptRepository interruptRepository;
    @Mock private GameRepository gameRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BroadcastGateway broadcastGateway;
    @Mock private PresenceGateway presenceGateway;

    @InjectMocks private InterruptServiceImpl interruptService;

    @Test
    void interruptRejectsUnknownTeamWithoutPersistingOrBroadcasting() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 30.0);
      final UUID teamId = UUID.randomUUID();
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(teamId)).thenReturn(Optional.empty());

      final InvalidReferencedObjectException exception =
          assertThrows(
              InvalidReferencedObjectException.class,
              () -> interruptService.interrupt("AKKU", teamId));

      assertEquals("Team with id" + teamId + " does not exist", exception.getMessage());
      verify(interruptRepository, never()).saveAndFlush(any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void interruptRejectsForeignTeamWithoutPersistingOrBroadcasting() throws DerivedException {
      final GameEntity game = game("AKKU");
      final GameEntity otherGame = game("BLAH");
      final ScheduleEntity schedule = schedule(game, 30.0);
      final TeamEntity foreignTeam = team(otherGame, "Blue");
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(foreignTeam.getId())).thenReturn(Optional.of(foreignTeam));

      final UnauthorizedException exception =
          assertThrows(
              UnauthorizedException.class,
              () -> interruptService.interrupt("AKKU", foreignTeam.getId()));

      assertEquals(
          "Team with id " + foreignTeam.getId() + " isn't part of the game AKKU",
          exception.getMessage());
      verify(interruptRepository, never()).saveAndFlush(any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void interruptRejectsWhenSongAlreadyRevealedWithoutPersistingOrBroadcasting()
        throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 30.0);
      final TeamEntity team = team(game, "Red");
      schedule.setRevealedAt(LocalDateTime.now().minusSeconds(1));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals(
          "Song " + schedule.getTrackId().getSongId().getId() + " is no longer playing",
          exception.getMessage());
      verify(interruptRepository, never()).saveAndFlush(any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void interruptRejectsWhenSystemPauseStillActive() throws DerivedException {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 30.0);
      final TeamEntity team = team(game, "Red");
      final InterruptEntity systemPause = new InterruptEntity(UUID.randomUUID());
      systemPause.setResolvedAt(null);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
      when(teamService.findById(team.getId())).thenReturn(Optional.of(team));
      when(interruptRepository.findInterrupts(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(new ArrayList<>());
      when(interruptRepository.findLastAnswer(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(null);
      when(interruptRepository.findLastPause(schedule.getStartedAt(), schedule.getId()))
          .thenReturn(systemPause);

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.interrupt("AKKU", team.getId()));

      assertEquals("The game is paused", exception.getMessage());
      verify(interruptRepository, never()).saveAndFlush(any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    @Test
    void interruptSystemPausePersistsAndBroadcastsNullAnsweringTeam() throws Exception {
      final GameEntity game = game("AKKU");
      final ScheduleEntity schedule = schedule(game, 30.0);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

      interruptService.interrupt("AKKU", null);

      final ArgumentCaptor<InterruptEntity> captor = ArgumentCaptor.forClass(InterruptEntity.class);
      verify(interruptRepository).saveAndFlush(captor.capture());
      assertSame(schedule, captor.getValue().getScheduleId());
      assertEquals(null, captor.getValue().getTeamId());
      verify(broadcastGateway)
          .broadcast(
              eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"answeringTeamId\":\"null\""));
    }

    @Test
    void answerCorrectPersistsThenRevealsThenUpdatesScoreThenBroadcasts() throws Exception {
      final GameEntity game = game("AKKU");
      final InterruptEntity answer = answer(game, null);
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
      when(teamService.getTeamPoints(answer.getTeamId().getId(), "AKKU")).thenReturn(5);

      interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU");

      assertEquals(true, answer.isCorrect());
      assertEquals(Integer.valueOf(35), answer.getScoreOrScenarioId());
      assertNotNull(answer.getResolvedAt());
      final InOrder inOrder =
          inOrder(interruptRepository, scheduleRepository, teamService, broadcastGateway);
      inOrder
          .verify(interruptRepository)
          .resolveErrors(eq(answer.getScheduleId().getId()), any(LocalDateTime.class));
      inOrder.verify(interruptRepository).save(answer);
      inOrder.verify(scheduleRepository).saveAndFlush(answer.getScheduleId());
      inOrder
          .verify(teamService)
          .saveTeamAnswer(answer.getTeamId().getId(), answer.getScheduleId().getId(), 35, "AKKU");
      inOrder
          .verify(broadcastGateway)
          .broadcast(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"correct\":true"));
    }

    @Test
    void answerRepeatedAfterResolutionDoesNotResolveErrorsAgainOrBroadcast()
        throws DerivedException {
      final GameEntity game = game("AKKU");
      final InterruptEntity answer = answer(game, false);
      answer.setResolvedAt(LocalDateTime.now().minusSeconds(1));
      when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
      when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
      when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));

      final GuessNotAllowedException exception =
          assertThrows(
              GuessNotAllowedException.class,
              () -> interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU"));

      assertEquals("That guess was already answered", exception.getMessage());
      verify(interruptRepository, never())
          .resolveErrors(eq(answer.getScheduleId().getId()), any(LocalDateTime.class));
      verify(interruptRepository, never()).save(any());
      verify(scheduleRepository, never()).saveAndFlush(any());
      verify(broadcastGateway, never()).broadcast(anyString(), anyString());
    }

    private static GameEntity game(final String code) {
      final GameEntity game = new GameEntity(UUID.randomUUID());
      game.setCode(code);
      game.setStage(2);
      game.setDate(LocalDateTime.now().minusHours(1));
      return game;
    }

    private static ScheduleEntity schedule(final GameEntity game, final double snippetDuration) {
      final SongEntity song = new SongEntity(UUID.randomUUID());
      song.setName("Song");
      song.setAuthors("Artist");
      song.setSnippetDuration(snippetDuration);
      song.setAnswerDuration(8.0);
      final AlbumEntity album = new AlbumEntity(UUID.randomUUID());
      album.setCustomQuestion("Question");
      final TrackEntity track = new TrackEntity(UUID.randomUUID());
      track.setAlbumId(album);
      track.setSongId(song);
      track.setCustomAnswer("Answer");
      final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
      schedule.setTrackId(track);
      schedule.setStartedAt(LocalDateTime.now().minusSeconds(3));
      schedule.setInterruptList(new ArrayList<>());
      return schedule;
    }

    private static TeamEntity team(final GameEntity game, final String name) {
      final TeamEntity team = new TeamEntity(UUID.randomUUID());
      team.setGameId(game);
      team.setName(name);
      team.setImage(name + ".png");
      return team;
    }

    private static InterruptEntity answer(final GameEntity game, final Boolean correct) {
      final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
      answer.setTeamId(team(game, "Blue"));
      answer.setScheduleId(schedule(game, 30.0));
      answer.setCorrect(correct);
      return answer;
    }
  }
}
