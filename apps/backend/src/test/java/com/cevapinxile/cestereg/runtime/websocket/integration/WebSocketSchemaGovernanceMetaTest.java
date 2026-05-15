package com.cevapinxile.cestereg.runtime.websocket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("ws-fast")
@Tag("ws-contract")
@DisplayName("WebSocket schema governance meta-tests")
class WebSocketSchemaGovernanceMetaTest extends AbstractWebSocketIntegrationTestSupport {

  private static final List<String> PUBLISHED_FRAME_TYPES =
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

  private final ObjectMapper json = new ObjectMapper();
  private final JsonSchemaFactory schemaFactory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  @Test
  void everyPublishedAngularMessageTypeHasExactlyOneSchemaAndExactlyOneExample() throws Exception {
    assertEquals(PUBLISHED_FRAME_TYPES, catalogTypes());
    assertEquals(PUBLISHED_FRAME_TYPES, resourceNames("/websocket-contracts/v1/schema", ".schema.json"));
    assertEquals(PUBLISHED_FRAME_TYPES, resourceNames("/websocket-contracts/v1/examples", ".json"));
  }

  @Test
  void everySchemaHasTypeDiscriminatorGovernanceMetadataAndNoBroadAdditionalPropertiesAtRoot()
      throws Exception {
    for (String type : PUBLISHED_FRAME_TYPES) {
      final JsonNode schema = readJson("/websocket-contracts/v1/schema/" + type + ".schema.json");
      assertEquals("implicit-v1", schema.path("x-protocolVersion").asText());
      assertFalse(schema.path("title").asText().isBlank(), "schema must have a reviewable title: " + type);
      assertFalse(schema.path("x-recipients").isMissingNode(), "schema must declare recipients: " + type);
      assertTrue(schema.path("x-replaySafe").isBoolean(), "schema must declare replay safety: " + type);
      assertContainsTypeConst(schema, type, type + " schema must constrain the top-level type field");
      assertRootAdditionalPropertiesFalse(schema, type);
    }
  }

  @Test
  void everyExamplePayloadValidatesAgainstItsOwnSchemaAndAgainstNoOtherConcreteEventSchema()
      throws Exception {
    for (String type : PUBLISHED_FRAME_TYPES) {
      final JsonNode payload = readJson("/websocket-contracts/v1/examples/" + type + ".json");
      assertEquals(type, payload.path("type").asText(), "example type discriminator must match filename");
      assertValid(type, payload);

      for (String other : PUBLISHED_FRAME_TYPES) {
        if (!other.equals(type)) {
          assertInvalid(other, payload, "example for " + type + " must not validate as " + other);
        }
      }
    }
  }

  @Test
  void catalogRecipientsMatchCurrentAngularConsumersDocumentedInTheFrontend() throws Exception {
    final JsonNode catalog = readJson("/websocket-contracts/v1/frame-catalog.json");

    for (JsonNode entry : catalog.path("frameTypes")) {
      final String type = entry.path("type").asText();
      final List<String> recipients = new ArrayList<>();
      entry.path("recipients").forEach(node -> recipients.add(node.asText()));

      if (Set.of("new_team", "kick_team", "album_picked").contains(type)) {
        assertEquals(List.of("tv"), recipients, type + " should stay TV-only unless Angular/admin handling changes");
      } else {
        assertEquals(List.of("admin", "tv"), recipients, type + " should be consumed by both admin and TV");
      }
    }
  }

  @Test
  void replaySafetyIsExplicitlyDocumentedForAngularShareReplayRisk() throws Exception {
    final JsonNode catalog = readJson("/websocket-contracts/v1/frame-catalog.json");

    for (JsonNode entry : catalog.path("frameTypes")) {
      final String type = entry.path("type").asText();
      if ("welcome".equals(type)) {
        assertTrue(entry.path("replaySafe").asBoolean(), "welcome is a state snapshot and should remain replay-safe");
      } else {
        assertFalse(entry.path("replaySafe").asBoolean(), type + " is an event/command and should not be replay-safe");
      }
    }
  }

  private void assertValid(final String type, final JsonNode payload) {
    final Set<ValidationMessage> violations = schema(type).validate(payload);
    assertTrue(violations.isEmpty(), "expected valid " + type + " example but got " + violations);
  }

  private void assertInvalid(final String type, final JsonNode payload, final String message) {
    final Set<ValidationMessage> violations = schema(type).validate(payload);
    assertFalse(violations.isEmpty(), message);
  }

  private JsonSchema schema(final String type) {
    try {
      return schemaFactory.getSchema(readJson("/websocket-contracts/v1/schema/" + type + ".schema.json"));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private List<String> catalogTypes() throws Exception {
    final List<String> types = new ArrayList<>();
    readJson("/websocket-contracts/v1/frame-catalog.json")
        .path("frameTypes")
        .forEach(node -> types.add(node.path("type").asText()));
    return types;
  }

  private List<String> resourceNames(final String resourceDir, final String suffix) throws Exception {
    final URI uri = getClass().getResource(resourceDir).toURI();
    final Path dir = Path.of(uri);
    final List<String> names = new ArrayList<>();
    try (var stream = Files.list(dir)) {
      stream
          .filter(path -> path.getFileName().toString().endsWith(suffix))
          .filter(path -> !path.getFileName().toString().startsWith("_"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .forEach(
              path -> {
                String name = path.getFileName().toString();
                name = name.substring(0, name.length() - suffix.length());
                names.add(name);
              });
    }
    names.sort(Comparator.comparingInt(PUBLISHED_FRAME_TYPES::indexOf));
    return names;
  }

  private JsonNode readJson(final String resource) throws Exception {
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      assertNotNull(in, "missing websocket resource " + resource);
      return json.readTree(in);
    }
  }

  private static void assertContainsTypeConst(final JsonNode node, final String type, final String message) {
    if (node.isObject()) {
      if (node.path("const").asText(null) != null && type.equals(node.path("const").asText())) {
        return;
      }
      for (JsonNode child : node) {
        if (containsTypeConst(child, type)) {
          return;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsTypeConst(child, type)) {
          return;
        }
      }
    }
    throw new AssertionError(message);
  }

  private static boolean containsTypeConst(final JsonNode node, final String type) {
    if (node.isObject()) {
      if (type.equals(node.path("const").asText(null))) {
        return true;
      }
      for (JsonNode child : node) {
        if (containsTypeConst(child, type)) {
          return true;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsTypeConst(child, type)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void assertRootAdditionalPropertiesFalse(final JsonNode schema, final String type) {
    if (schema.has("oneOf")) {
      for (JsonNode variant : schema.path("oneOf")) {
        assertTrue(
            variant.path("additionalProperties").isBoolean()
                && !variant.path("additionalProperties").asBoolean(),
            "every top-level " + type + " schema variant must reject unknown root fields");
      }
    } else {
      assertTrue(
          schema.path("additionalProperties").isBoolean()
              && !schema.path("additionalProperties").asBoolean(),
          type + " schema must reject unknown root fields");
    }
  }
}
