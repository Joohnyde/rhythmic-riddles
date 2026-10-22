package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.AppNotRegisteredException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.common.exception.InvalidArgumentException;
import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.PlaybackDelayEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.PlaybackDelayRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class PlaybackDelayServiceImplTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0);
  @Mock private GameRepository gameRepository;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private PlaybackDelayRepository playbackDelayRepository;
  @Mock private BroadcastGateway broadcastGateway;
  @Mock private PresenceGateway presenceGateway;
  @InjectMocks private PlaybackDelayServiceImpl service;

  private GameEntity game;
  private ScheduleEntity schedule;

  @BeforeEach
  void prepare() {
    ReflectionTestUtils.setField(
        service, "clock", Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    lenient().when(presenceGateway.areBothPresent("AKKU")).thenReturn(true);
    game = new GameEntity(UUID.randomUUID());
    game.setCode("AKKU");
    game.setStage(2);
    final CategoryEntity category = new CategoryEntity(UUID.randomUUID());
    category.setGameId(game);
    schedule = new ScheduleEntity(UUID.randomUUID());
    schedule.setCategoryId(category);
    schedule.setStartedAt(NOW.minusSeconds(4));
  }

  private void currentSong() throws DerivedException {
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
  }

  @Test
  void registersPendingAttemptOnlyOnce() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final UUID first = service.registerPlaybackDelay(schedule.getId(), "AKKU");
    assertNotNull(first);
    final ArgumentCaptor<PlaybackDelayEntity> captured =
        ArgumentCaptor.forClass(PlaybackDelayEntity.class);
    verify(playbackDelayRepository).saveAndFlush(captured.capture());
    assertEquals(NOW, captured.getValue().getSentAt());
    verify(broadcastGateway, never()).toAdmin(any(), any());
  }

  @Test
  void repeatedRegistrationReturnsExistingPendingAttempt() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final PlaybackDelayEntity delay = new PlaybackDelayEntity(schedule, NOW.minusSeconds(2));
    when(playbackDelayRepository.findOpenBySchedule(schedule.getId())).thenReturn(delay);
    assertEquals(delay.getId(), service.registerPlaybackDelay(schedule.getId(), "AKKU"));
    verify(playbackDelayRepository, never()).saveAndFlush(any());
  }

  @Test
  void acknowledgementIsIdempotentAndSendsPlayOnlyOnFirstAttempt() throws Exception {
    currentSong();
    final PlaybackDelayEntity delay = new PlaybackDelayEntity(schedule, NOW.minusSeconds(2));
    when(playbackDelayRepository.findById(delay.getId())).thenReturn(Optional.of(delay));
    service.acknowledgePlaybackDelay(delay.getId(), "AKKU");
    assertEquals(NOW, delay.getAcknowledgedAt());
    service.acknowledgePlaybackDelay(delay.getId(), "AKKU");
    verify(broadcastGateway).toAdmin(eq("AKKU"), any(String.class));
  }

  @Test
  void staleReplayAcknowledgementDoesNotStartAdmin() throws Exception {
    currentSong();
    final PlaybackDelayEntity old = new PlaybackDelayEntity(schedule, NOW.minusSeconds(8));
    when(playbackDelayRepository.findById(old.getId())).thenReturn(Optional.of(old));
    assertThrows(
        WrongGameStateException.class, () -> service.acknowledgePlaybackDelay(old.getId(), "AKKU"));
    verify(broadcastGateway, never()).toAdmin(any(), any());
  }

  @Test
  void rejectsCrossRoomSchedulesEvenIfTheyExist() throws Exception {
    final GameEntity other = new GameEntity(UUID.randomUUID());
    other.setCode("AKKU");
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(other);
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    assertThrows(
        InvalidArgumentException.class,
        () -> service.registerPlaybackDelay(schedule.getId(), "AKKU"));
  }

  @Test
  void resendingPlayInfoAcrossSeveralReconnectsReusesThePendingAttempt() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final PlaybackDelayEntity pending = new PlaybackDelayEntity(schedule, schedule.getStartedAt());
    when(playbackDelayRepository.findOpenBySchedule(schedule.getId())).thenReturn(pending);
    for (int i = 0; i < 5; i++) {
      assertEquals(pending.getId(), service.registerPlaybackDelay(schedule.getId(), "AKKU"));
    }
    verify(playbackDelayRepository, never()).saveAndFlush(any());
    verify(broadcastGateway, never()).toAdmin(any(), any());
  }

  @Test
  void registrationStartsAtTvRequestNotAtBackendScheduleStart() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final UUID attempt = service.registerPlaybackDelay(schedule.getId(), "AKKU");
    final ArgumentCaptor<PlaybackDelayEntity> captured =
        ArgumentCaptor.forClass(PlaybackDelayEntity.class);
    verify(playbackDelayRepository).saveAndFlush(captured.capture());
    assertEquals(attempt, captured.getValue().getId());
    assertEquals(NOW, captured.getValue().getSentAt());
    verify(broadcastGateway, never()).toAdmin(any(), any());
  }

  @Test
  void registrationRejectsDisconnectedRoomWithoutCreatingHistory() throws Exception {
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(presenceGateway.areBothPresent("AKKU")).thenReturn(false);
    assertThrows(
        AppNotRegisteredException.class,
        () -> service.registerPlaybackDelay(schedule.getId(), "AKKU"));
    verify(playbackDelayRepository, never()).saveAndFlush(any());
  }

  @Test
  void acknowledgementRejectedDuringDisconnectCanSucceedAfterReconnect() throws Exception {
    currentSong();
    final PlaybackDelayEntity open = new PlaybackDelayEntity(schedule, NOW.minusSeconds(1));
    when(playbackDelayRepository.findById(open.getId())).thenReturn(Optional.of(open));
    when(presenceGateway.areBothPresent("AKKU")).thenReturn(false, true);
    assertThrows(
        AppNotRegisteredException.class,
        () -> service.acknowledgePlaybackDelay(open.getId(), "AKKU"));
    assertNull(open.getAcknowledgedAt());
    service.acknowledgePlaybackDelay(open.getId(), "AKKU");
    assertEquals(NOW, open.getAcknowledgedAt());
    verify(broadcastGateway)
        .toAdmin(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"play\""));
  }

  @Test
  void registrationAfterAcknowledgedAttemptCreatesNewAttemptForResumedPlayback() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final PlaybackDelayEntity previous = new PlaybackDelayEntity(schedule, NOW.minusSeconds(2));
    when(playbackDelayRepository.findById(previous.getId())).thenReturn(Optional.of(previous));

    service.acknowledgePlaybackDelay(previous.getId(), "AKKU");
    final UUID resumedAttempt = service.registerPlaybackDelay(schedule.getId(), "AKKU");

    assertEquals(NOW, previous.getAcknowledgedAt());
    assertNotNull(resumedAttempt);
    org.junit.jupiter.api.Assertions.assertNotEquals(previous.getId(), resumedAttempt);
    verify(playbackDelayRepository, times(2)).saveAndFlush(any(PlaybackDelayEntity.class));
    verify(broadcastGateway, times(1)).toAdmin(eq("AKKU"), any(String.class));
  }

  @Test
  void rejectsAcknowledgementFromAnotherRoomBeforeSendingPlay() throws Exception {
    final GameEntity otherGame = new GameEntity(UUID.randomUUID());
    otherGame.setCode("AKKU");
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(otherGame);
    final PlaybackDelayEntity delay = new PlaybackDelayEntity(schedule, NOW.minusSeconds(1));
    when(playbackDelayRepository.findById(delay.getId())).thenReturn(Optional.of(delay));

    assertThrows(
        InvalidArgumentException.class,
        () -> service.acknowledgePlaybackDelay(delay.getId(), "AKKU"));
    assertNull(delay.getAcknowledgedAt());
    verify(broadcastGateway, never()).toAdmin(any(), any());
  }

  @Test
  void rejectsPlaybackOnRevealedScheduleWithoutCreatingDelay() throws Exception {
    when(gameRepository.findByCode("AKKU", 2)).thenReturn(game);
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    when(scheduleRepository.findLastPlayed(game.getId())).thenReturn(schedule);
    schedule.setRevealedAt(NOW.minusSeconds(1));

    assertThrows(
        WrongGameStateException.class,
        () -> service.registerPlaybackDelay(schedule.getId(), "AKKU"));
    verify(playbackDelayRepository, never()).saveAndFlush(any());
  }

  @Test
  void repeatedAckOnlyBroadcastsPlayOnceAcrossHttpRetries() throws Exception {
    currentSong();
    final PlaybackDelayEntity delay = new PlaybackDelayEntity(schedule, NOW.minusSeconds(2));
    when(playbackDelayRepository.findById(delay.getId())).thenReturn(Optional.of(delay));

    for (int attempt = 0; attempt < 4; attempt++) {
      service.acknowledgePlaybackDelay(delay.getId(), "AKKU");
    }

    assertEquals(NOW, delay.getAcknowledgedAt());
    verify(playbackDelayRepository, times(1)).saveAndFlush(delay);
    verify(broadcastGateway, times(1))
        .toAdmin(eq("AKKU"), org.mockito.ArgumentMatchers.contains("\"type\":\"play\""));
  }

  @Test
  void staleOpenAttemptIsReplacedOnlyWhenTvRegistersAfterReplay() throws Exception {
    currentSong();
    when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));
    final PlaybackDelayEntity stale = new PlaybackDelayEntity(schedule, NOW.minusSeconds(9));
    when(playbackDelayRepository.findOpenBySchedule(schedule.getId())).thenReturn(stale);
    final UUID newId = service.registerPlaybackDelay(schedule.getId(), "AKKU");
    verify(playbackDelayRepository).delete(stale);
    final ArgumentCaptor<PlaybackDelayEntity> captured =
        ArgumentCaptor.forClass(PlaybackDelayEntity.class);
    verify(playbackDelayRepository).saveAndFlush(captured.capture());
    assertEquals(newId, captured.getValue().getId());
    assertEquals(NOW, captured.getValue().getSentAt());
  }
}
