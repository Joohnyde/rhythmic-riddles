package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.InvalidReferencedObjectException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.CategoryService;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.ScheduleService;
import com.cevapinxile.cestereg.core.service.impl.ScheduleServiceImpl;
import com.cevapinxile.cestereg.persistence.entity.GameEntity;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import(ScheduleServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleAtomicityIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 2, 11, 20, 0);

  @Autowired private ScheduleService scheduleService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private GameService gameService;
  @MockitoBean private CategoryService categoryService;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    DatabaseTestCleaner.clear(jdbc);
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @AfterEach
  void tearDown() {
    DatabaseTestCleaner.clear(jdbc);
  }

  @Test
  void progressRollsBackResolvedSystemPauseWhenLateCategoryTransitionFails() throws Exception {
    final String roomCode = "SATM";
    final UUID gameId = fixture.game(roomCode, 2, 1, 2);
    final UUID teamId = fixture.team(gameId, "Atomic Progress", "atomic-progress.png");
    final UUID albumId = fixture.album("Atomic progress album");
    final UUID songId = fixture.song("Artist", "Atomic progress song", 30.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, START, START.plusSeconds(20));
    final UUID systemPause =
        fixture.interrupt(scheduleId, null, START.plusSeconds(5), null, null, 1);
    final GameEntity game = new GameEntity(gameId);
    game.setStage(2);

    when(gameService.findByCode(roomCode, 2)).thenReturn(game);
    when(presenceGateway.areBothPresent(roomCode)).thenReturn(true);
    doThrow(new InvalidReferencedObjectException("forced late category transition failure"))
        .when(categoryService)
        .finishAndNext(game);

    assertThrows(InvalidReferencedObjectException.class, () -> scheduleService.progress(roomCode));

    assertNull(
        jdbc.queryForObject(
            "SELECT resolved_at FROM interrupt WHERE id = ?", LocalDateTime.class, systemPause));
  }
}
