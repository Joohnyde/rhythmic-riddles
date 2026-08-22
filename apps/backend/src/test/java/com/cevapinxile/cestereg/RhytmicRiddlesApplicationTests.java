package com.cevapinxile.cestereg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateTeamRequest;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.io.buzzer.BuzzerSerialAdapter;
import com.cevapinxile.cestereg.persistence.integration.support.DatabaseTestCleaner;
import com.cevapinxile.cestereg.persistence.integration.support.EmbeddedPostgresTestDatabase;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never"})
@AutoConfigureRestTestClient
class RhytmicRiddlesApplicationTests {

  // Business rejection and lock contention cover the two distinct failure boundaries;
  // deeper failure variants stay in focused integration suites.

  @Autowired private RestTestClient restClient;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private BuzzerSerialAdapter buzzerSerialAdapter;

  private QuizPersistenceFixture fixture;

  @DynamicPropertySource
  static void postgresProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", EmbeddedPostgresTestDatabase::jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @BeforeEach
  void resetDatabase() {
    DatabaseTestCleaner.clear(jdbc);
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @AfterEach
  void cleanDatabase() {
    DatabaseTestCleaner.clear(jdbc);
  }

  @Nested
  class SuccessfulVerticalSlice {

    @Test
    void createTeamReturnsCreatedTeamAndCommitsItToPostgres() {
      final String roomCode = "FSTS";
      final UUID gameId = fixture.game(roomCode, 0, 10, 3);
      final CreateTeamRequest request =
          new CreateTeamRequest("Full Stack Team", "424242", "full-stack.png");

      final EntityExchangeResult<CreateTeamResponse> result =
          restClient
              .post()
              .uri("/api/v1/games/{roomCode}/teams", roomCode)
              .contentType(APPLICATION_JSON)
              .body(request)
              .exchange()
              .expectStatus()
              .isOk()
              .expectHeader()
              .contentTypeCompatibleWith(APPLICATION_JSON)
              .expectBody(CreateTeamResponse.class)
              .returnResult();

      final CreateTeamResponse response = result.getResponseBody();
      assertNotNull(response);
      assertNotNull(response.getId());
      assertEquals("Full Stack Team", response.getName());
      assertEquals("full-stack.png", response.getImage());

      assertEquals(
          1,
          jdbc.queryForObject(
              """
              SELECT COUNT(*)
              FROM team
              WHERE id = ?
                AND game_id = ?
                AND name = ?
                AND image = ?
                AND button_code = ?
              """,
              Integer.class,
              response.getId(),
              gameId,
              request.name(),
              request.image(),
              request.buttonCode()));
    }
  }

  @Nested
  class BusinessFailureVerticalSlice {

    @Test
    void createTeamAfterGameStartedReturnsWrongGameStateAndDoesNotPersistTeam() {
      final String roomCode = "FSTB";
      final UUID gameId = fixture.game(roomCode, 1, 10, 3);
      final CreateTeamRequest request =
          new CreateTeamRequest("Late Team", "515151", "late-team.png");

      restClient
          .post()
          .uri("/api/v1/games/{roomCode}/teams", roomCode)
          .contentType(APPLICATION_JSON)
          .body(request)
          .exchange()
          .expectStatus()
          .isEqualTo(409)
          .expectHeader()
          .contentTypeCompatibleWith(APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.error")
          .isEqualTo("E003 - Wrong game-state")
          .jsonPath("$.message")
          .isEqualTo("Game with code " + roomCode + " already started");

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM team WHERE game_id = ?", Integer.class, gameId));
    }
  }

  @Nested
  class LockContentionFailureVerticalSlice {

    @Test
    void createTeamWhileRoomIsLockedReturnsRoomBusyAndDoesNotPersistTeam() {
      final String roomCode = "FSTL";
      final UUID gameId = fixture.game(roomCode, 0, 10, 3);
      final CreateTeamRequest request =
          new CreateTeamRequest("Blocked Team", "616161", "blocked-team.png");
      final TransactionTemplate blocker = new TransactionTemplate(transactionManager);

      blocker.executeWithoutResult(
          status -> {
            jdbc.queryForObject(
                "SELECT id FROM game WHERE code = ? FOR UPDATE", UUID.class, roomCode);

            restClient
                .post()
                .uri("/api/v1/games/{roomCode}/teams", roomCode)
                .contentType(APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isEqualTo(423)
                .expectHeader()
                .contentTypeCompatibleWith(APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("E010 - Room busy")
                .jsonPath("$.message")
                .isEqualTo("Another request is already changing game " + roomCode + ".");
          });

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM team WHERE game_id = ?", Integer.class, gameId));
    }
  }
}
