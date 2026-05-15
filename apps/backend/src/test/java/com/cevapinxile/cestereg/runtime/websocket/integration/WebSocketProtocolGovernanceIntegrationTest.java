package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cevapinxile.cestereg.api.quiz.dto.request.AnswerRequest;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ws-fast")
@Tag("ws-contract")
@DisplayName("WebSocket protocol-governance and backward-compatibility tests")
class WebSocketProtocolGovernanceIntegrationTest extends AbstractWebSocketIntegrationTestSupport {

  private static final String IMPLICIT_V1 = "implicit-v1";
  private static final List<String> DOCUMENTED_FRAME_TYPES =
      List.of(
          "welcome",
          "new_team",
          "kick_team",
          "album_picked",
          "song_next",
          "song_repeat",
          "song_reveal",
          "pause",
          "answer",
          "error_solved");

  @Test
  void frameCatalogListsExactlyTheDocumentedAngularMessageTypesInApiOrder() throws Exception {
    final JsonNode catalog = readCatalog();
    final List<String> actual = new ArrayList<>();
    catalog.path("frameTypes").forEach(node -> actual.add(node.path("type").asText()));

    assertEquals(DOCUMENTED_FRAME_TYPES, actual);
    assertEquals(IMPLICIT_V1, catalog.path("protocolVersion").asText());
    assertEquals("docs/developer-guide/api.md#websocket", catalog.path("sourceOfTruth").asText());
  }

  @Test
  void everyCatalogEntryPointsToSchemaAndExampleAndDocumentsRecipientsAndReplaySafety()
      throws Exception {
    final JsonNode catalog = readCatalog();

    for (JsonNode entry : catalog.path("frameTypes")) {
      final String type = entry.path("type").asText();
      assertFalse(entry.path("recipients").isEmpty(), "missing recipients for " + type);
      assertTrue(entry.path("replaySafe").isBoolean(), "missing replaySafe for " + type);
      assertEquals("schema/" + type + ".schema.json", entry.path("schema").asText());
      assertEquals("examples/" + type + ".json", entry.path("example").asText());
      assertFalse(entry.path("notes").asText().isBlank(), "missing governance note for " + type);

      assertNotNull(
          getClass().getResourceAsStream("/websocket-contracts/v1/schema/" + type + ".schema.json"),
          "catalog references missing schema for " + type);
      assertNotNull(
          getClass().getResourceAsStream("/websocket-contracts/v1/examples/" + type + ".json"),
          "catalog references missing example for " + type);
    }
  }

  @Test
  void schemaMetadataMatchesFrameCatalogForRecipientsAndReplaySafety() throws Exception {
    final JsonNode catalog = readCatalog();

    for (JsonNode entry : catalog.path("frameTypes")) {
      final String type = entry.path("type").asText();
      final JsonNode schema = readJson("/websocket-contracts/v1/schema/" + type + ".schema.json");

      assertEquals(IMPLICIT_V1, schema.path("x-protocolVersion").asText());
      assertEquals(entry.path("replaySafe").asBoolean(), schema.path("x-replaySafe").asBoolean());
      assertEquals(entry.path("recipients"), schema.path("x-recipients"));
    }
  }

  @Test
  void welcomeFrameRemainsBackwardCompatibleWithCurrentImplicitV1RecoveryShape() throws Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);

    final java.util.Map<?, ?> frame = admin.readJson();

    assertContract(frame, "welcome");
    assertEquals(Set.of("type", "roomCode", "stage", "recovery"), frame.keySet());
    assertEquals("welcome", frame.get("type"));
    assertEquals(ROOM_A, frame.get("roomCode"));
  }

  @Test
  void fullPublishedRuntimeFrameSequenceStillMatchesStrictImplicitV1WireContract()
      throws DerivedException, Exception {
    final SocketProbe admin = connectAdmin(ROOM_A);
    final SocketProbe tv = connectTv(ROOM_A);
    assertContract(admin.readJson(), "welcome");
    assertContract(tv.readJson(), "welcome");
    final Fixture fixture = fixture();
    stubRound(fixture);

    interruptService.interrupt(ROOM_A, fixture.team().getId());
    assertContract(admin.readJson(), "pause");
    assertContract(tv.readJson(), "pause");

    interruptService.answer(fixture.teamInterrupt().getId(), new AnswerRequest(true), ROOM_A);
    assertContract(admin.readJson(), "answer");
    assertContract(tv.readJson(), "answer");

    interruptService.resolveErrors(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "error_solved");
    assertContract(tv.readJson(), "error_solved");

    scheduleService.replaySong(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "song_repeat");
    assertContract(tv.readJson(), "song_repeat");

    scheduleService.revealAnswer(fixture.currentSchedule().getId(), ROOM_A);
    assertContract(admin.readJson(), "song_reveal");
    assertContract(tv.readJson(), "song_reveal");

    scheduleService.progress(ROOM_A);
    assertContract(admin.readJson(), "song_next");
    assertContract(tv.readJson(), "song_next");
  }

  private JsonNode readCatalog() throws Exception {
    return readJson("/websocket-contracts/v1/frame-catalog.json");
  }

  private JsonNode readJson(final String resource) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      assertNotNull(in, "missing websocket contract resource " + resource);
      return new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
    }
  }
}
