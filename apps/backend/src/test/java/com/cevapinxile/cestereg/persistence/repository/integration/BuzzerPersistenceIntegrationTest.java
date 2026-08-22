package com.cevapinxile.cestereg.persistence.repository.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.cevapinxile.cestereg.persistence.repository.TeamRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class BuzzerPersistenceIntegrationTest extends PostgresJpaIntegrationTest {

  @Autowired private TeamRepository teamRepository;
  @Autowired private JdbcTemplate jdbc;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void buttonLookupIsScopedToTheActiveGame() {
    final UUID firstGame = fixture.game("BZ01", 0, 3, 2);
    final UUID secondGame = fixture.game("BZ02", 0, 3, 2);
    final UUID firstTeam = insertTeam(firstGame, "1671", "First");
    insertTeam(secondGame, "1671", "Second");

    assertEquals(
        firstTeam, teamRepository.findIdByButtonAndGameId("1671", firstGame).orElseThrow());
  }

  @Test
  void sameButtonCannotBeAssignedToTwoTeamsInTheSameGame() {
    final UUID gameId = fixture.game("BZ03", 0, 3, 2);
    insertTeam(gameId, "1671", "First");

    assertThrows(DataIntegrityViolationException.class, () -> insertTeam(gameId, "1671", "Second"));
  }

  @Test
  void samePhysicalButtonCanBeReusedInAnotherGame() {
    final UUID firstGame = fixture.game("BZ04", 0, 3, 2);
    final UUID secondGame = fixture.game("BZ05", 0, 3, 2);

    insertTeam(firstGame, "1671", "First");
    final UUID secondTeam = insertTeam(secondGame, "1671", "Second");

    assertEquals(
        secondTeam, teamRepository.findIdByButtonAndGameId("1671", secondGame).orElseThrow());
  }

  @Test
  void unknownButtonDoesNotResolveToATeam() {
    final UUID gameId = fixture.game("BZ06", 0, 3, 2);
    insertTeam(gameId, "1671", "First");

    assertTrue(teamRepository.findIdByButtonAndGameId("9999", gameId).isEmpty());
  }

  private UUID insertTeam(final UUID gameId, final String buttonCode, final String name) {
    final UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO team (id, button_code, game_id, name, image) VALUES (?, ?, ?, ?, ?)",
        id,
        buttonCode,
        gameId,
        name,
        name.toLowerCase() + ".png");
    return id;
  }
}
