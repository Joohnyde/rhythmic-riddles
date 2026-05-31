package com.cevapinxile.cestereg.e2e.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureRequest;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureServiceImpl;
import com.cevapinxile.cestereg.e2e.E2eGameFixtureValidator;
import com.cevapinxile.cestereg.persistence.entity.AlbumEntity;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.entity.ScheduleEntity;
import com.cevapinxile.cestereg.persistence.entity.TrackEntity;
import com.cevapinxile.cestereg.persistence.repository.AlbumRepository;
import com.cevapinxile.cestereg.persistence.repository.CategoryRepository;
import com.cevapinxile.cestereg.persistence.repository.GameRepository;
import com.cevapinxile.cestereg.persistence.repository.InterruptRepository;
import com.cevapinxile.cestereg.persistence.repository.ScheduleRepository;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import com.cevapinxile.cestereg.persistence.repository.TrackRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("E2E fixture service implementation")
class E2eGameFixtureServiceImplTest {

  @Mock private GameRepository gameRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private TrackRepository trackRepository;
  @Mock private AlbumRepository albumRepository;
  @Mock private ScheduleRepository scheduleRepository;
  @Mock private InterruptRepository interruptRepository;
  @Mock private E2eGameFixtureValidator fixtureValidator;

  private E2eGameFixtureServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new E2eGameFixtureServiceImpl();
    ReflectionTestUtils.setField(service, "gameRepository", gameRepository);
    ReflectionTestUtils.setField(service, "categoryRepository", categoryRepository);
    ReflectionTestUtils.setField(service, "teamRepository", teamRepository);
    ReflectionTestUtils.setField(service, "trackRepository", trackRepository);
    ReflectionTestUtils.setField(service, "albumRepository", albumRepository);
    ReflectionTestUtils.setField(service, "scheduleRepository", scheduleRepository);
    ReflectionTestUtils.setField(service, "interruptRepository", interruptRepository);
    ReflectionTestUtils.setField(service, "fixtureValidator", fixtureValidator);
  }

  private void stubRepositoryEchoSaves() {
    lenient()
        .when(gameRepository.saveAndFlush(any(GameEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(teamRepository.saveAllAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(categoryRepository.saveAndFlush(any(CategoryEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(albumRepository.saveAndFlush(any(AlbumEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(trackRepository.saveAndFlush(any(TrackEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(scheduleRepository.saveAndFlush(any(ScheduleEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(interruptRepository.saveAllAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createFixturePersistsGraphInDependencyOrder() throws E2eGameFixtureValidationException {
    stubRepositoryEchoSaves();
    E2eGameFixtureRequest request = validFixture();

    service.createFixture(request);

    InOrder order =
        inOrder(
            gameRepository,
            teamRepository,
            albumRepository,
            categoryRepository,
            trackRepository,
            scheduleRepository,
            interruptRepository);
    order.verify(gameRepository).saveAndFlush(any(GameEntity.class));
    order.verify(teamRepository).saveAllAndFlush(any());
    order.verify(albumRepository).saveAndFlush(any(AlbumEntity.class));
    order.verify(categoryRepository).saveAndFlush(any(CategoryEntity.class));
    order.verify(trackRepository).saveAndFlush(any(TrackEntity.class));
    order.verify(scheduleRepository).saveAndFlush(any(ScheduleEntity.class));
    order.verify(interruptRepository).saveAllAndFlush(any());
  }

  @Test
  void createFixtureSkipsScheduleAndInterruptRepositoriesWhenTracksAreUnscheduled()
      throws E2eGameFixtureValidationException {
    stubRepositoryEchoSaves();
    E2eGameFixtureRequest request = fixtureWithUnscheduledTrack();

    service.createFixture(request);

    verify(trackRepository).saveAndFlush(any(TrackEntity.class));
    verify(scheduleRepository, never()).saveAndFlush(any(ScheduleEntity.class));
    verify(interruptRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void createFixtureSkipsInterruptRepositoryWhenScheduleHasNoInterrupts()
      throws E2eGameFixtureValidationException {
    stubRepositoryEchoSaves();
    E2eGameFixtureRequest request = fixtureWithScheduleButNoInterrupts();

    service.createFixture(request);

    verify(scheduleRepository).saveAndFlush(any(ScheduleEntity.class));
    verify(interruptRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void validationRunsBeforeAnyRepositorySave() throws E2eGameFixtureValidationException {
    E2eGameFixtureRequest request = validFixture();
    stubRepositoryEchoSaves();

    service.createFixture(request);

    InOrder order = inOrder(fixtureValidator, gameRepository);
    order.verify(fixtureValidator).validateOrThrow(request);
    order.verify(gameRepository).saveAndFlush(any(GameEntity.class));
  }

  @Test
  void validationFailureStopsBeforeAnyRepositorySave() throws E2eGameFixtureValidationException {
    E2eGameFixtureRequest request = validFixture();
    doThrow(new IllegalArgumentException("invalid fixture"))
        .when(fixtureValidator)
        .validateOrThrow(request);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createFixture(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fixture");

    verifyNoInteractions(
        gameRepository,
        teamRepository,
        albumRepository,
        categoryRepository,
        trackRepository,
        scheduleRepository,
        interruptRepository);
  }

  @Test
  void teamSaveFailureStopsAlbumCategoryTrackScheduleAndInterruptSaves() {
    when(gameRepository.saveAndFlush(any(GameEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(teamRepository.saveAllAndFlush(any()))
        .thenThrow(new IllegalStateException("team save failed"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createFixture(validFixture()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("team save failed");

    verify(albumRepository, never()).saveAndFlush(any(AlbumEntity.class));
    verify(categoryRepository, never()).saveAndFlush(any(CategoryEntity.class));
    verify(trackRepository, never()).saveAndFlush(any(TrackEntity.class));
    verify(scheduleRepository, never()).saveAndFlush(any(ScheduleEntity.class));
    verify(interruptRepository, never()).saveAllAndFlush(any());
  }

  @Test
  void resetRuntimeStateIsRoomScopedAndDoesNotTouchChildRepositoriesDirectly() {
    service.resetRuntimeState("AKKU");

    verify(gameRepository).deleteByCode("AKKU");
    verify(categoryRepository, never()).deleteAll();
    verify(teamRepository, never()).deleteAll();
    verify(albumRepository, never()).deleteAll();
    verify(trackRepository, never()).deleteAll();
    verify(scheduleRepository, never()).deleteAll();
    verify(interruptRepository, never()).deleteAll();
  }

  @Test
  void duplicateSeedFailsPredictablyAndDoesNotPartiallyPersistChildRows() {
    when(gameRepository.saveAndFlush(any(GameEntity.class)))
        .thenThrow(new IllegalStateException("duplicate room code"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createFixture(validFixture()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate room code");

    verify(teamRepository, never()).saveAllAndFlush(any());
    verify(albumRepository, never()).saveAndFlush(any(AlbumEntity.class));
    verify(categoryRepository, never()).saveAndFlush(any(CategoryEntity.class));
    verify(trackRepository, never()).saveAndFlush(any(TrackEntity.class));
    verify(scheduleRepository, never()).saveAndFlush(any(ScheduleEntity.class));
    verify(interruptRepository, never()).saveAllAndFlush(any());
  }

  private static E2eGameFixtureRequest validFixture() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 18, 0);
    return new E2eGameFixtureRequest(
        UUID.randomUUID(),
        "AKKU",
        5,
        2,
        1,
        List.of(new E2eGameFixtureRequest.Team(teamId, "BTN-1", "Team A", null)),
        List.of(
            new E2eGameFixtureRequest.Category(
                UUID.randomUUID(),
                teamId,
                1,
                false,
                new E2eGameFixtureRequest.Album(
                    UUID.randomUUID(),
                    "Album A",
                    "Question A",
                    List.of(
                        new E2eGameFixtureRequest.Track(
                            "Answer A",
                            new E2eGameFixtureRequest.Schedule(
                                UUID.randomUUID(),
                                now,
                                null,
                                1,
                                List.of(
                                    new E2eGameFixtureRequest.Interrupt(
                                        UUID.randomUUID(),
                                        teamId,
                                        now.plusSeconds(1),
                                        null,
                                        true,
                                        10,
                                        2)))))))));
  }

  private static E2eGameFixtureRequest fixtureWithUnscheduledTrack() {
    UUID teamId = UUID.randomUUID();
    return new E2eGameFixtureRequest(
        UUID.randomUUID(),
        "AKKU",
        5,
        2,
        1,
        List.of(new E2eGameFixtureRequest.Team(teamId, "BTN-1", "Team A", null)),
        List.of(
            new E2eGameFixtureRequest.Category(
                UUID.randomUUID(),
                teamId,
                1,
                false,
                new E2eGameFixtureRequest.Album(
                    UUID.randomUUID(),
                    "Album A",
                    "Question A",
                    List.of(new E2eGameFixtureRequest.Track("Answer A", null))))));
  }

  private static E2eGameFixtureRequest fixtureWithScheduleButNoInterrupts() {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 18, 0);
    return new E2eGameFixtureRequest(
        UUID.randomUUID(),
        "AKKU",
        5,
        2,
        1,
        List.of(new E2eGameFixtureRequest.Team(teamId, "BTN-1", "Team A", null)),
        List.of(
            new E2eGameFixtureRequest.Category(
                UUID.randomUUID(),
                teamId,
                1,
                false,
                new E2eGameFixtureRequest.Album(
                    UUID.randomUUID(),
                    "Album A",
                    "Question A",
                    List.of(
                        new E2eGameFixtureRequest.Track(
                            "Answer A",
                            new E2eGameFixtureRequest.Schedule(
                                UUID.randomUUID(), now, null, 1, null)))))));
  }
}
