package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.common.exception.WrongGameStateException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.TeamService;
import com.cevapinxile.cestereg.core.service.impl.GameServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
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

@Import(GameServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameStateAtomicityIntegrationTest extends PostgresJpaIntegrationTest {

  @Autowired private GameService gameService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private TeamService teamService;
  @MockitoBean private InterruptService interruptService;
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
  void changeStageDoesNotCommitNewStageWhenRecoveryPayloadCannotBeBuilt() throws Exception {
    final String roomCode = "GATM";
    final UUID gameId = fixture.game(roomCode, 1, 3, 2);
    when(presenceGateway.areBothPresent(roomCode)).thenReturn(true);

    assertThrows(WrongGameStateException.class, () -> gameService.changeStage(2, roomCode));

    assertEquals(
        1, jdbc.queryForObject("SELECT stage FROM game WHERE id = ?", Integer.class, gameId));
  }
}
