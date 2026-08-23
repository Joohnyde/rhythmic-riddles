package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("ws-fast")
@Tag("ws-contract")
@DisplayName("Real JSON Schema validation for published WebSocket protocol frames")
class WebSocketJsonSchemaContractIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  private static final List<String> PUBLISHED_FRAME_TYPES =
      List.of(
          "welcome",
          "new_team",
          "kick_team",
          "button_clicked",
          "album_picked",
          "song_next",
          "song_repeat",
          "song_reveal",
          "pause",
          "answer",
          "error_solved");

  private final ObjectMapper schemaMapper = new ObjectMapper();
  private final SchemaRegistry schemaRegistry =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  @Test
  void everyPublishedFrameTypeHasACompilableJsonSchemaWithGovernanceMetadata() throws Exception {
    for (String frameType : PUBLISHED_FRAME_TYPES) {
      final JsonNode schemaNode = readSchemaNode(frameType);
      final Schema schema = schemaRegistry.getSchema(schemaNode);

      assertNotNull(schema, "schema must compile for " + frameType);
      assertEquals("implicit-v1", schemaNode.path("x-protocolVersion").asText());
      assertFalse(
          schemaNode.path("x-recipients").isMissingNode(), "schema must document recipients");
      assertFalse(
          schemaNode.path("x-replaySafe").isMissingNode(), "schema must document replay safety");
    }
  }

  @Test
  void allDocumentedPositiveExamplesValidateAgainstTheirJsonSchemas() throws Exception {
    final String teamId = UUID.randomUUID().toString();
    final String scheduleId = UUID.randomUUID().toString();
    final String songId = UUID.randomUUID().toString();
    final String interruptId = UUID.randomUUID().toString();

    assertSchemaValid(
        "new_team",
        "{\"type\":\"new_team\",\"team\":{\"id\":\""
            + teamId
            + "\",\"name\":\"Team A\",\"image\":\"team.png\"}}");
    assertSchemaValid("kick_team", "{\"type\":\"kick_team\",\"uuid\":\"" + teamId + "\"}");
    assertSchemaValid("button_clicked", "{\"type\":\"button_clicked\",\"buttonCode\":1671}");
    assertSchemaValid(
        "album_picked",
        "{\"type\":\"album_picked\",\"selected\":{\"id\":\"cat-1\",\"started\":false}}");
    assertSchemaValid(
        "song_next",
        "{\"type\":\"song_next\",\"scheduleId\":\""
            + scheduleId
            + "\",\"songId\":\""
            + songId
            + "\",\"question\":\"Prepoznaj ovu pjesmu!\",\"answer\":\"Answer\",\"remaining\":15.0,\"answerDuration\":8.0}");
    assertSchemaValid("song_repeat", "{\"type\":\"song_repeat\",\"remaining\":15.0}");
    assertSchemaValid("song_reveal", "{\"type\":\"song_reveal\"}");
    assertSchemaValid(
        "pause",
        "{\"type\":\"pause\",\"answeringTeamId\":\""
            + teamId
            + "\",\"interruptId\":\""
            + interruptId
            + "\"}");
    assertSchemaValid(
        "pause",
        "{\"type\":\"pause\",\"answeringTeamId\":\"null\",\"interruptId\":\""
            + interruptId
            + "\"}");
    assertSchemaValid(
        "answer",
        "{\"type\":\"answer\",\"teamId\":\""
            + teamId
            + "\",\"scheduleId\":\""
            + scheduleId
            + "\",\"correct\":true}");
    assertSchemaValid("error_solved", "{\"type\":\"error_solved\",\"previousScenario\":2}");
  }

  @Test
  void welcomeSchemaAcceptsOnlyGroundTruthStageSnapshots() throws Exception {
    final String teamId = UUID.randomUUID().toString();
    final String categoryId = UUID.randomUUID().toString();
    final String albumId = UUID.randomUUID().toString();
    final String scheduleId = UUID.randomUUID().toString();
    final String songId = UUID.randomUUID().toString();
    final String interruptId = UUID.randomUUID().toString();

    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"lobby\",\"teams\":["
            + team(teamId, "Team A", "team.png")
            + "]}");
    final String album =
        "{\"id\":\""
            + categoryId
            + "\",\"name\":\"Album A\",\"image\":\""
            + albumId
            + "\",\"pickedByTeam\":null,\"ordinalNumber\":null}";
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"albums\",\"albums\":["
            + album
            + "],\"team\":"
            + team(teamId, "Team A", "team.png")
            + "}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"albums\",\"albums\":["
            + album
            + "],\"selected\":{\"categoryId\":\""
            + categoryId
            + "\",\"chosenCategoryPreview\":{\"title\":\"Album A\",\"image\":\""
            + albumId
            + "\"},\"pickedByTeam\":null,\"started\":false,\"ordinalNumber\":1}}");
    assertSchemaValid(
        "welcome", "{\"type\":\"welcome\",\"stage\":\"albums\",\"albums\":[" + album + "]}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Question\",\"answer\":\"Answer\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8.0,\"scores\":["
            + teamScore(teamId, "team.png", "Team A", 10, scheduleId)
            + "],\"seek\":1.0,\"remaining\":14.0}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Question\",\"answer\":\"Answer\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8.0,\"scores\":[],\"answeringTeam\":"
            + team(teamId, "Team A", "team.png")
            + ",\"interruptId\":\""
            + interruptId
            + "\"}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Question\",\"answer\":\"Answer\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8.0,\"scores\":[],\"error\":true}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Question\",\"answer\":\"Answer\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8.0,\"scores\":[],\"revealed\":true,\"bravo\":\""
            + teamId
            + "\"}");
    assertSchemaValid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"winner\",\"scores\":["
            + teamScore(teamId, "team.png", "Team A", 10, null)
            + "]}");
  }

  @Test
  void welcomeSchemaRejectsLegacyRecoveryShapes() throws Exception {
    final String categoryId = UUID.randomUUID().toString();
    final String albumId = UUID.randomUUID().toString();

    assertSchemaInvalid(
        "welcome", "{\"type\":\"welcome\",\"roomCode\":\"AKKU\",\"stage\":2,\"recovery\":true}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"albums\",\"selected\":{\"categoryId\":\""
            + categoryId
            + "\",\"chosenCategoryPreview\":{\"title\":\"Album A\",\"image\":\""
            + albumId
            + "\"},\"pickedByTeam\":null,\"started\":false,\"ordinalNumber\":1}}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"albums\",\"albums\":[{\"id\":\""
            + categoryId
            + "\",\"name\":\"Album A\",\"image\":\""
            + albumId
            + ".png\",\"pickedByTeam\":null,\"ordinalNumber\":null}],\"team\":null}");
  }

  @Test
  void realWireRuntimeFramesValidateAgainstJsonSchemas() throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);

    assertLegacyRecoveryWelcome(admin.pollFrame(1500));
    assertLegacyRecoveryWelcome(tv.pollFrame(1500));

    final Fixture fixture = fixture();
    stubRound(fixture);

    interruptService.interrupt(ROOM_A, fixture.team().getId());
    assertFrameMatchesSchema(admin, "pause");
    assertFrameMatchesSchema(tv, "pause");

    interruptService.answer(fixture.teamInterrupt().getId(), new AnswerRequest(true), ROOM_A);
    assertFrameMatchesSchema(admin, "answer");
    assertFrameMatchesSchema(tv, "answer");

    interruptService.resolveErrors(fixture.currentSchedule().getId(), ROOM_A);
    assertFrameMatchesSchema(admin, "error_solved");
    assertFrameMatchesSchema(tv, "error_solved");

    scheduleService.replaySong(fixture.currentSchedule().getId(), ROOM_A);
    assertFrameMatchesSchema(admin, "song_repeat");
    assertFrameMatchesSchema(tv, "song_repeat");

    scheduleService.revealAnswer(fixture.currentSchedule().getId(), ROOM_A);
    assertFrameMatchesSchema(admin, "song_reveal");
    assertFrameMatchesSchema(tv, "song_reveal");

    scheduleService.progress(ROOM_A);
    assertFrameMatchesSchema(admin, "song_next");
    assertFrameMatchesSchema(tv, "song_next");
  }

  @Test
  void realWireButtonClickedFrameValidatesAgainstSchemaAndStaysAdminOnly() throws Exception {
    gameA.setStage(0);
    org.mockito.Mockito.when(gameRepository.findActive()).thenReturn(java.util.Optional.of(gameA));
    org.mockito.Mockito.when(teamRepository.findIdByButtonAndGameId("1671", gameA.getId()))
        .thenReturn(java.util.Optional.empty());
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    admin.readJson();
    tv.readJson();

    buzzerService.buzz("1671");

    assertFrameMatchesSchema(admin, "button_clicked");
    assertNull(tv.pollFrame(350), "button_clicked must not be published to TV");
  }

  @Test
  void schemasRejectExtraFieldsMissingRequiredFieldsWrongTypesAndWrongDiscriminators()
      throws Exception {
    final String teamId = UUID.randomUUID().toString();
    final String scheduleId = UUID.randomUUID().toString();
    final String songId = UUID.randomUUID().toString();
    final String interruptId = UUID.randomUUID().toString();

    assertSchemaInvalid("song_reveal", "{\"type\":\"song_reveal\",\"unexpected\":true}");
    assertSchemaInvalid(
        "song_next",
        "{\"type\":\"song_next\",\"scheduleId\":\""
            + scheduleId
            + "\",\"songId\":\""
            + songId
            + "\",\"question\":\"Q\",\"answer\":\"A\",\"remaining\":10}");
    assertSchemaInvalid(
        "song_next",
        "{\"type\":\"song_next\",\"scheduleId\":\""
            + scheduleId
            + "\",\"songId\":\""
            + songId
            + "\",\"question\":\"Q\",\"answer\":\"A\",\"remaining\":\"10\",\"answerDuration\":5}");
    assertSchemaInvalid(
        "answer",
        "{\"type\":\"answer\",\"teamId\":\""
            + teamId
            + "\",\"scheduleId\":\""
            + scheduleId
            + "\",\"correct\":\"true\"}");
    assertSchemaInvalid(
        "pause",
        "{\"type\":\"pause\",\"answeringTeamId\":null,\"interruptId\":\"" + interruptId + "\"}");
    assertSchemaInvalid(
        "pause",
        "{\"type\":\"pause\",\"answeringTeamId\":\"not-a-uuid\",\"interruptId\":\""
            + interruptId
            + "\"}");
    assertSchemaInvalid("kick_team", "{\"type\":\"kick_team\",\"uuid\":\"not-a-uuid\"}");
    assertSchemaInvalid("button_clicked", "{\"type\":\"button_clicked\",\"buttonCode\":\"1671\"}");
    assertSchemaInvalid(
        "button_clicked", "{\"type\":\"button_clicked\",\"buttonCode\":101,\"extra\":true}");
    assertSchemaInvalid("error_solved", "{\"type\":\"error_solved\",\"previousScenario\":2.5}");
    assertSchemaInvalid("song_repeat", "{\"type\":\"song_repeat\",\"remaining\":-1}");
    assertSchemaInvalid(
        "answer",
        "{\"type\":\"pause\",\"teamId\":\""
            + teamId
            + "\",\"scheduleId\":\""
            + scheduleId
            + "\",\"correct\":true}");
  }

  @Test
  void welcomeSchemaRejectsAmbiguousOrFrontendBreakingSnapshots() throws Exception {
    final String teamId = UUID.randomUUID().toString();
    final String scheduleId = UUID.randomUUID().toString();
    final String songId = UUID.randomUUID().toString();

    assertSchemaInvalid(
        "welcome", "{\"type\":\"welcome\",\"stage\":\"lobby\",\"teams\":[],\"roomCode\":\"AKKU\"}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Q\",\"answer\":\"A\",\"scheduleId\":\""
            + scheduleId
            + "\",\"scores\":[]}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\""
            + songId
            + "\",\"question\":\"Q\",\"answer\":\"A\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8,\"scores\":[],\"answeringTeam\":"
            + team(teamId, "Team", "team.png")
            + "}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"songs\",\"songId\":\"not-a-uuid\",\"question\":\"Q\",\"answer\":\"A\",\"scheduleId\":\""
            + scheduleId
            + "\",\"answerDuration\":8,\"scores\":[]}");
    assertSchemaInvalid(
        "welcome", "{\"type\":\"welcome\",\"roomCode\":\"AKKU\",\"stage\":4,\"recovery\":true}");
    assertSchemaInvalid("welcome", "{\"type\":\"welcome\",\"stage\":\"winner\"}");
    assertSchemaInvalid(
        "welcome",
        "{\"type\":\"welcome\",\"stage\":\"winner\",\"scores\":[{\"id\":\""
            + teamId
            + "\",\"name\":\"Team A\",\"points\":10}]}");
  }

  @Test
  void schemaRegistryListsExactlyThePublishedFrameTypesFromTheDocumentation() throws Exception {
    final JsonNode registry = readSchemaNode("_published-frame-registry");
    final Schema schema = schemaRegistry.getSchema(registry);
    final String json =
        schemaMapper.writeValueAsString(Map.of("publishedFrameTypes", PUBLISHED_FRAME_TYPES));

    assertTrue(schema.validate(schemaMapper.readTree(json)).isEmpty());
    assertSchemaSetEquals(
        PUBLISHED_FRAME_TYPES,
        registry.path("properties").path("publishedFrameTypes").path("items").path("enum"));
  }

  private void assertFrameMatchesSchema(final SocketProbe probe, final String expectedType)
      throws Exception {
    final String payload = probe.pollFrame(1500);
    assertNotNull(payload, "expected a websocket frame for schema " + expectedType);
    assertSchemaValid(expectedType, payload);
    assertEquals(expectedType, schemaMapper.readTree(payload).path("type").asText());
  }

  private void assertLegacyRecoveryWelcome(final String payload) throws Exception {
    assertNotNull(payload, "expected initial legacy welcome recovery frame");
    final JsonNode frame = schemaMapper.readTree(payload);
    assertEquals("welcome", frame.path("type").asText());
    assertTrue(frame.path("stage").isInt(), "legacy recovery welcome uses numeric stage");
    assertFalse(
        schema("welcome").validate(frame).isEmpty(),
        "legacy recovery welcome must not validate against ground-truth welcome schema");
  }

  private void assertSchemaValid(final String frameType, final String payload) throws Exception {
    final List<Error> violations = schema(frameType).validate(schemaMapper.readTree(payload));
    assertTrue(
        violations.isEmpty(),
        "expected valid " + frameType + " payload but got " + violations + " for " + payload);
  }

  private void assertSchemaInvalid(final String frameType, final String payload) throws Exception {
    final List<Error> violations = schema(frameType).validate(schemaMapper.readTree(payload));
    assertFalse(
        violations.isEmpty(),
        "expected invalid " + frameType + " payload, but schema accepted " + payload);
  }

  private Schema schema(final String frameType) throws Exception {
    return schemaRegistry.getSchema(readSchemaNode(frameType));
  }

  private JsonNode readSchemaNode(final String frameType) throws Exception {
    final String resource = "/websocket-contracts/v1/schema/" + frameType + ".schema.json";
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      assertNotNull(in, "missing websocket JSON schema resource " + resource);
      return schemaMapper.readTree(in);
    }
  }

  private static String team(final String id, final String name, final String image) {
    return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"image\":\"" + image + "\"}";
  }

  private static String teamScore(
      final String teamId,
      final String image,
      final String name,
      final int score,
      final String scheduleId) {
    final String scheduleValue = scheduleId == null ? "null" : "\"" + scheduleId + "\"";
    return "{\"teamId\":\""
        + teamId
        + "\",\"image\":\""
        + image
        + "\",\"name\":\""
        + name
        + "\",\"score\":"
        + score
        + ",\"scheduleId\":"
        + scheduleValue
        + "}";
  }

  private static void assertSchemaSetEquals(final List<String> expected, final JsonNode enumNode) {
    final Map<String, Boolean> seen = new HashMap<>();
    enumNode.forEach(item -> seen.put(item.asText(), true));
    assertEquals(expected.size(), seen.size(), "schema registry enum must not contain duplicates");
    for (String frameType : expected) {
      assertTrue(seen.containsKey(frameType), "schema registry is missing " + frameType);
    }
  }
}
