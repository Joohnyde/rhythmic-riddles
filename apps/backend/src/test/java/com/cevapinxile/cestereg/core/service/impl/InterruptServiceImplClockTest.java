package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.InterruptFrame;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.InterruptEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TeamEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InterruptServiceImplClockTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 12, 0);
  private static final Clock FIXED_CLOCK =
      Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

  @Mock private TeamService teamService;
  @Mock private InterruptRepository interruptRepository;
  @Mock private GameRepository gameRepository;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private BroadcastGateway broadcastGateway;
  @Mock private PresenceGateway presenceGateway;

  @InjectMocks private InterruptServiceImpl interruptService;

  @BeforeEach
  void useFixedClock() {
    ReflectionTestUtils.setField(interruptService, "clock", FIXED_CLOCK);
  }

  @Test
  void calculateSeekUsesInjectedClockForSyntheticTail() {
    final LocalDateTime start = NOW.minusSeconds(20);
    final UUID scheduleId = UUID.randomUUID();
    when(interruptRepository.findInterrupts(start, scheduleId))
        .thenReturn(
            new ArrayList<>(
                List.of(
                    new InterruptFrame(start.plusSeconds(5), start.plusSeconds(10)),
                    new InterruptFrame(start.plusSeconds(18), start.plusSeconds(20)))));

    final long seek = interruptService.calculateSeek(start, scheduleId);

    assertEquals(13_000L, seek);
  }

  @Test
  void interruptPersistsInjectedTimestamp() throws Exception {
    final GameEntity game = game("AKKU");
    final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);

    interruptService.interrupt("AKKU", null);

    final ArgumentCaptor<InterruptEntity> interruptCaptor =
        ArgumentCaptor.forClass(InterruptEntity.class);
    verify(interruptRepository).saveAndFlush(interruptCaptor.capture());
    assertEquals(NOW, interruptCaptor.getValue().getArrivedAt());
    verify(broadcastGateway).broadcast(eq("AKKU"), anyString());
  }

  @Test
  void resolveErrorsUsesInjectedTimestamp() throws Exception {
    final GameEntity game = game("AKKU");
    final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
    when(interruptRepository.findPreviousScenarioId(schedule.getId())).thenReturn(4);

    interruptService.resolveErrors(schedule.getId(), "AKKU");

    verify(interruptRepository).resolveErrors(schedule.getId(), NOW);
  }

  @Test
  void answerUsesSameInjectedTimestampForResolutionAndReveal() throws Exception {
    final GameEntity game = game("AKKU");
    final TeamEntity team = new TeamEntity(UUID.randomUUID());
    team.setGameId(game);
    final ScheduleEntity schedule = new ScheduleEntity(UUID.randomUUID());
    final InterruptEntity answer = new InterruptEntity(UUID.randomUUID());
    answer.setTeamId(team);
    answer.setScheduleId(schedule);

    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
    when(interruptRepository.findById(answer.getId())).thenReturn(Optional.of(answer));
    when(teamService.getTeamPoints(team.getId(), "AKKU")).thenReturn(20);

    interruptService.answer(answer.getId(), new AnswerRequest(true), "AKKU");

    assertEquals(NOW, answer.getResolvedAt());
    assertEquals(NOW, schedule.getRevealedAt());
    verify(interruptRepository).resolveErrors(schedule.getId(), NOW);
    verify(scheduleRepository).saveAndFlush(schedule);
  }

  private static GameEntity game(final String code) {
    final GameEntity game = new GameEntity(UUID.randomUUID());
    game.setCode(code);
    game.setStage(2);
    return game;
  }
}
