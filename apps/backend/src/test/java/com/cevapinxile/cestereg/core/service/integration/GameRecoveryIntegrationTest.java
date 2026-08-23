package com.cevapinxile.cestereg.core.service.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple;
import com.cevapinxile.cestereg.api.quiz.dto.response.CreateTeamResponse;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.core.gateway.BroadcastGateway;
import com.cevapinxile.cestereg.core.gateway.PresenceGateway;
import com.cevapinxile.cestereg.core.service.GameService;
import com.cevapinxile.cestereg.core.service.InterruptService;
import com.cevapinxile.cestereg.core.service.impl.GameServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.InterruptServiceImpl;
import com.cevapinxile.cestereg.core.service.impl.TeamServiceImpl;
import com.cevapinxile.cestereg.persistence.integration.support.FixedTestClockConfiguration;
import com.cevapinxile.cestereg.persistence.integration.support.PostgresJpaIntegrationTest;
import com.cevapinxile.cestereg.persistence.integration.support.QuizPersistenceFixture;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({
  GameServiceImpl.class,
  TeamServiceImpl.class,
  InterruptServiceImpl.class,
  FixedTestClockConfiguration.class
})
class GameRecoveryIntegrationTest extends PostgresJpaIntegrationTest {

  private static final LocalDateTime NOW = FixedTestClockConfiguration.NOW;

  private final ObjectMapper schemaMapper = new ObjectMapper();
  private final SchemaRegistry schemaRegistry =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  @Autowired private GameService gameService;
  @Autowired private InterruptService interruptService;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private BroadcastGateway broadcastGateway;
  @MockitoBean private PresenceGateway presenceGateway;

  private QuizPersistenceFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new QuizPersistenceFixture(jdbc);
  }

  @Test
  void contextFetchReconstructsNormalPlaybackFromPersistedState() throws Exception {
    final Round round = round("RNOR", 10.0, NOW.minusSeconds(4), null);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(4.0, (Double) context.get("seek"), 0.0001);
    assertEquals(6.0, (Double) context.get("remaining"), 0.0001);
    assertFalse(context.containsKey("revealed"));
    assertFalse(context.containsKey("answeringTeam"));
    assertFalse(context.containsKey("error"));
  }

  @Test
  void contextFetchReconstructsActiveTeamInterruptFromPersistedState() throws Exception {
    final Round round = round("RANS", 10.0, NOW.minusSeconds(6), null);
    final UUID interruptId =
        fixture.interrupt(
            round.scheduleId(), round.teamId(), NOW.minusSeconds(2), null, null, null);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(4.0, (Double) context.get("seek"), 0.0001);
    assertEquals(6.0, (Double) context.get("remaining"), 0.0001);
    assertEquals(interruptId, context.get("interruptId"));
    final CreateTeamResponse answeringTeam = (CreateTeamResponse) context.get("answeringTeam");
    assertEquals(round.teamId(), answeringTeam.getId());
    assertFalse(context.containsKey("error"));
  }

  @Test
  void contextFetchReconstructsLayeredSystemPauseInsideActiveTeamInterrupt() throws Exception {
    final Round round = round("RLAY", 20.0, NOW.minusSeconds(8), null);
    final UUID teamInterrupt =
        fixture.interrupt(
            round.scheduleId(), round.teamId(), NOW.minusSeconds(4), null, null, null);
    interruptService.interrupt(round.roomCode(), null);

    assertNull(
        jdbc.queryForObject(
            "SELECT resolved_at FROM interrupt WHERE id = ?", LocalDateTime.class, teamInterrupt));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM interrupt
                WHERE schedule_id = ?
                  AND team_id IS NULL
                  AND resolved_at IS NULL
                """,
            Integer.class,
            round.scheduleId()));

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(4.0, (Double) context.get("seek"), 0.0001);
    assertEquals(16.0, (Double) context.get("remaining"), 0.0001);
    assertEquals(teamInterrupt, context.get("interruptId"));
    final CreateTeamResponse answeringTeam = (CreateTeamResponse) context.get("answeringTeam");
    assertEquals(round.teamId(), answeringTeam.getId());
    assertFalse(context.containsKey("error"));
  }

  @Test
  void contextFetchReconstructsActiveSystemPauseFromPersistedState() throws Exception {
    final Round round = round("RERR", 10.0, NOW.minusSeconds(6), null);
    fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(2), null, null, 1);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(4.0, (Double) context.get("seek"), 0.0001);
    assertEquals(6.0, (Double) context.get("remaining"), 0.0001);
    assertEquals(Boolean.TRUE, context.get("error"));
    assertFalse(context.containsKey("answeringTeam"));
  }

  @Test
  void contextFetchReconstructsEndedButUnrevealedSongFromPersistedState() throws Exception {
    final Round round = round("REND", 10.0, NOW.minusSeconds(12), null);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(Boolean.FALSE, context.get("revealed"));
    assertFalse(context.containsKey("seek"));
    assertFalse(context.containsKey("remaining"));
    assertFalse(context.containsKey("answeringTeam"));
    assertFalse(context.containsKey("error"));
  }

  @Test
  void contextFetchReconstructsRevealedSongAndCorrectTeamFromPersistedState() throws Exception {
    final Round round = round("RREV", 10.0, NOW.minusSeconds(8), NOW.minusSeconds(1));
    fixture.interrupt(
        round.scheduleId(), round.teamId(), NOW.minusSeconds(3), NOW.minusSeconds(1), true, 30);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(Boolean.TRUE, context.get("revealed"));
    assertEquals(round.teamId(), context.get("bravo"));
    assertFalse(context.containsKey("seek"));
    assertFalse(context.containsKey("remaining"));
  }

  @Test
  void contextFetchReconstructsRevealedSongWithoutFabricatingCorrectTeam() throws Exception {
    final Round round = round("RNON", 10.0, NOW.minusSeconds(8), NOW.minusSeconds(1));
    fixture.interrupt(
        round.scheduleId(), round.teamId(), NOW.minusSeconds(4), NOW.minusSeconds(3), false, -10);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertCommonSongContext(context, round);
    assertEquals(Boolean.TRUE, context.get("revealed"));
    assertTrue(context.containsKey("bravo"));
    assertNull(context.get("bravo"));
    assertFalse(context.containsKey("seek"));
    assertFalse(context.containsKey("remaining"));
  }

  @Test
  void contextFetchComposesRepositoryAndSeekLogicWithoutDoubleCountingNestedPauses()
      throws Exception {
    final Round round = round("RNST", 20.0, NOW.minusSeconds(10), null);
    fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(8), NOW.minusSeconds(5), null, 1);
    fixture.interrupt(
        round.scheduleId(), round.teamId(), NOW.minusSeconds(7), NOW.minusSeconds(6), false, -10);
    fixture.interrupt(round.scheduleId(), null, NOW.minusSeconds(3), NOW.minusSeconds(2), null, 2);

    final HashMap<String, Object> context = gameService.contextFetch(round.roomCode());

    assertEquals(6.0, (Double) context.get("seek"), 0.0001);
    assertEquals(14.0, (Double) context.get("remaining"), 0.0001);
    assertFalse(context.containsKey("error"));
    assertFalse(context.containsKey("answeringTeam"));
  }

  @Test
  void contextFetchReconstructsLobbyTeamsFromPersistedState() throws Exception {
    final UUID gameId = fixture.game("RLOB", 0, 3, 2);
    final UUID red = fixture.team(gameId, "Red", "red.png");
    final UUID blue = fixture.team(gameId, "Blue", "blue.png");

    final HashMap<String, Object> context = gameService.contextFetch("RLOB");

    assertEquals("welcome", context.get("type"));
    assertEquals("lobby", context.get("stage"));
    final List<?> teams = (List<?>) context.get("teams");
    assertEquals(2, teams.size());
    final Set<UUID> ids =
        teams.stream()
            .map(CreateTeamResponse.class::cast)
            .map(CreateTeamResponse::getId)
            .collect(Collectors.toSet());
    assertEquals(Set.of(red, blue), ids);
  }

  @Test
  void contextFetchReconstructsSelectedAlbumWaitingToStart() throws Exception {
    final UUID gameId = fixture.game("RSEL", 1, 3, 2);
    final UUID picker = fixture.team(gameId, "Picker", "picker.png");
    final UUID selectedAlbum = fixture.album("Selected Album");
    final UUID selectedCategory = fixture.category(gameId, selectedAlbum, picker, 1, false);
    final UUID openAlbum = fixture.album("Open Album");
    fixture.category(gameId, openAlbum, null, null, false);

    final HashMap<String, Object> context = gameService.contextFetch("RSEL");

    assertEquals("welcome", context.get("type"));
    assertEquals("albums", context.get("stage"));
    final LastCategory selected = (LastCategory) context.get("selected");
    assertNotNull(selected);
    assertEquals(selectedCategory, selected.getCategoryId());
    assertEquals(picker, selected.getPickedByTeam().getId());
    assertEquals("Selected Album", selected.getChosenCategoryPreview().title());
    assertEquals(selectedAlbum.toString(), selected.getChosenCategoryPreview().image());
    final List<?> albums = (List<?>) context.get("albums");
    assertEquals(2, albums.size());
    assertEquals(
        Set.of(selectedAlbum.toString(), openAlbum.toString()),
        albums.stream()
            .map(CategorySimple.class::cast)
            .map(CategorySimple::getImage)
            .collect(Collectors.toSet()));
    assertFalse(context.containsKey("team"));
    assertWelcomeSchema(context);
  }

  @Test
  void contextFetchReconstructsNextAutomaticPickerAfterCompletedAlbum() throws Exception {
    final UUID gameId = fixture.game("RNXT", 1, 3, 3);
    final UUID first =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000101"), gameId, "First", "first.png");
    final UUID second =
        fixture.team(
            UUID.fromString("00000000-0000-0000-0000-000000000102"),
            gameId,
            "Second",
            "second.png");
    fixture.category(gameId, fixture.album("Completed"), first, 1, true);
    final UUID openA = fixture.category(gameId, fixture.album("Open A"), null, null, false);
    final UUID openB = fixture.category(gameId, fixture.album("Open B"), null, null, false);

    final HashMap<String, Object> context = gameService.contextFetch("RNXT");

    assertEquals("albums", context.get("stage"));
    assertFalse(context.containsKey("selected"));
    final CreateTeamResponse choosingTeam = (CreateTeamResponse) context.get("team");
    assertNotNull(choosingTeam);
    assertEquals(second, choosingTeam.getId());

    final List<?> albums = (List<?>) context.get("albums");
    assertEquals(3, albums.size());
    final Set<UUID> categoryIds =
        albums.stream()
            .map(CategorySimple.class::cast)
            .map(CategorySimple::getId)
            .collect(Collectors.toSet());
    assertTrue(categoryIds.contains(openA));
    assertTrue(categoryIds.contains(openB));
    assertWelcomeSchema(context);
  }

  @Test
  void contextFetchReconstructsWinnerScoresFromPersistedState() throws Exception {
    final UUID gameId = fixture.game("RWIN", 3, 3, 2);
    final UUID red = fixture.team(gameId, "Red", "red.png");
    final UUID blue = fixture.team(gameId, "Blue", "blue.png");
    final UUID albumId = fixture.album("Final Album");
    final UUID songId = fixture.song("Artist", "Final Song", 20.0, 8.0);
    final UUID trackId = fixture.track(albumId, songId, null);
    final UUID categoryId = fixture.category(gameId, albumId, red, 1, true);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, NOW.minusMinutes(1), NOW);
    fixture.interrupt(scheduleId, red, NOW.minusSeconds(50), NOW.minusSeconds(49), true, 60);
    fixture.interrupt(scheduleId, blue, NOW.minusSeconds(40), NOW.minusSeconds(39), false, 20);

    final HashMap<String, Object> context = gameService.contextFetch("RWIN");

    assertEquals("welcome", context.get("type"));
    assertEquals("winner", context.get("stage"));
    assertNotNull(context.get("scores"));
    final String scoresJson = schemaMapper.writeValueAsString(context.get("scores"));
    assertTrue(scoresJson.contains(red.toString()));
    assertTrue(scoresJson.contains(blue.toString()));
    assertTrue(scoresJson.contains("\"score\":60"));
    assertTrue(scoresJson.contains("\"score\":20"));
  }

  @Test
  void contextFetchRejectsSongStageWithoutPlayedScheduleUsingDomainError() {
    fixture.game("RMIS", 2, 3, 2);

    assertThrows(DerivedException.class, () -> gameService.contextFetch("RMIS"));
  }

  @Test
  void contextFetchRejectsStartedScheduleWithoutTrackUsingDomainError() {
    final UUID gameId = fixture.game("RBRO", 2, 3, 2);
    final UUID albumId = fixture.album("Broken Album");
    final UUID categoryId = fixture.category(gameId, albumId, null, 1, false);
    fixture.schedule(categoryId, null, 1, NOW.minusSeconds(4), null);

    assertThrows(DerivedException.class, () -> gameService.contextFetch("RBRO"));
  }

  private void assertWelcomeSchema(final HashMap<String, Object> context) throws Exception {
    final String resource = "/websocket-contracts/v1/schema/welcome.schema.json";
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      assertNotNull(in, "missing websocket JSON schema resource " + resource);
      final JsonNode schemaNode = schemaMapper.readTree(in);
      final Schema schema = schemaRegistry.getSchema(schemaNode);
      final List<Error> violations =
          schema.validate(schemaMapper.readTree(schemaMapper.writeValueAsString(context)));
      assertTrue(
          violations.isEmpty(),
          "real contextFetch payload must satisfy welcome schema: " + violations);
    }
  }

  private Round round(
      final String roomCode,
      final double snippetDuration,
      final LocalDateTime startedAt,
      final LocalDateTime revealedAt) {
    final UUID gameId = fixture.game(roomCode, 2, 3, 2);
    final UUID teamId = fixture.team(gameId, "Recovery Team", "recovery.png");
    final UUID albumId = fixture.album("Recovery Album", "Who is this?");
    final UUID songId = fixture.song("Recovery Artist", "Recovery Song", snippetDuration, 8.0);
    final UUID trackId = fixture.track(albumId, songId, "Recovery Answer");
    final UUID categoryId = fixture.category(gameId, albumId, teamId, 1, false);
    final UUID scheduleId = fixture.schedule(categoryId, trackId, 1, startedAt, revealedAt);
    return new Round(roomCode, gameId, teamId, songId, scheduleId);
  }

  private void assertCommonSongContext(final HashMap<String, Object> context, final Round round) {
    assertEquals("welcome", context.get("type"));
    assertEquals("songs", context.get("stage"));
    assertEquals(round.songId(), context.get("songId"));
    assertEquals(round.scheduleId(), context.get("scheduleId"));
    assertEquals("Who is this?", context.get("question"));
    assertEquals("Recovery Answer", context.get("answer"));
    assertEquals(8.0, context.get("answerDuration"));
    assertNotNull(context.get("scores"));
  }

  private record Round(String roomCode, UUID gameId, UUID teamId, UUID songId, UUID scheduleId) {}
}
