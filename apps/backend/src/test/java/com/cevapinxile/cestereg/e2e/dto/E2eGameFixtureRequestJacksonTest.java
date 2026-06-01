package com.cevapinxile.cestereg.e2e.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.cevapinxile.cestereg.e2e.E2eGameFixtureRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("E2E game fixture request JSON contract")
class E2eGameFixtureRequestJacksonTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void deserializesTheCompleteFixtureShapeUsedByPlaywrightAndBackendSeeding() throws Exception {
    UUID gameId = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID albumId = UUID.randomUUID();
    UUID scheduleId = UUID.randomUUID();
    UUID interruptId = UUID.randomUUID();
    LocalDateTime startedAt = LocalDateTime.of(2026, 5, 31, 18, 0, 0);
    LocalDateTime revealedAt = startedAt.plusSeconds(15);
    LocalDateTime arrivedAt = startedAt.plusSeconds(4);
    LocalDateTime resolvedAt = arrivedAt.plusSeconds(3);

    String json =
        """
        {
          "id": "%s",
          "roomCode": "AKKU",
          "maxSongs": 10,
          "maxAlbums": 3,
          "stage": 2,
          "teams": [
            {"id": "%s", "buttonCode": "BTN-1", "name": "Team A", "image": "team-a.png"}
          ],
          "categories": [
            {
              "id": "%s",
              "pickedByTeamId": "%s",
              "ordinalNumber": 1,
              "done": false,
              "album": {
                "id": "%s",
                "name": "Album A",
                "customQuestion": "Question A",
                "tracks": [
                  {
                    "customAnswer": "Answer A",
                    "schedule": {
                      "id": "%s",
                      "startedAt": "%s",
                      "revealedAt": "%s",
                      "ordinalNumber": 1,
                      "interrupts": [
                        {
                          "id": "%s",
                          "teamId": "%s",
                          "arrivedAt": "%s",
                          "resolvedAt": "%s",
                          "correct": false,
                          "score": 0,
                          "scenario": 3
                        }
                      ]
                    }
                  }
                ]
              }
            }
          ]
        }
        """
            .formatted(
                gameId,
                teamId,
                categoryId,
                teamId,
                albumId,
                scheduleId,
                startedAt,
                revealedAt,
                interruptId,
                teamId,
                arrivedAt,
                resolvedAt);

    E2eGameFixtureRequest request = objectMapper.readValue(json, E2eGameFixtureRequest.class);

    assertThat(request.id()).isEqualTo(gameId);
    assertThat(request.roomCode()).isEqualTo("AKKU");
    assertThat(request.maxSongs()).isEqualTo(10);
    assertThat(request.maxAlbums()).isEqualTo(3);
    assertThat(request.stage()).isEqualTo(2);
    assertThat(request.teams()).hasSize(1);
    assertThat(request.teams().get(0).buttonCode()).isEqualTo("BTN-1");
    assertThat(request.categories()).hasSize(1);
    assertThat(request.categories().get(0).album().tracks()).hasSize(1);
    assertThat(request.categories().get(0).album().tracks().get(0).schedule().startedAt())
        .isEqualTo(startedAt);
    assertThat(
            request
                .categories()
                .get(0)
                .album()
                .tracks()
                .get(0)
                .schedule()
                .interrupts()
                .get(0)
                .scenario())
        .isEqualTo(3);
  }

  @Test
  void serializesRecordsBackToTheSamePublicFieldNamesExpectedByTheE2eApi() throws Exception {
    UUID teamId = UUID.randomUUID();
    LocalDateTime now = LocalDateTime.of(2026, 5, 31, 12, 0);
    E2eGameFixtureRequest request =
        new E2eGameFixtureRequest(
            UUID.randomUUID(),
            "AKKU",
            5,
            2,
            1,
            List.of(new E2eGameFixtureRequest.Team(teamId, "BTN-1", "Team A", "team.png")),
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
                                            now,
                                            null,
                                            null,
                                            null,
                                            2)))))))));

    String json = objectMapper.writeValueAsString(request);

    assertThat(json).contains("\"roomCode\":\"AKKU\"");
    assertThat(json).contains("\"buttonCode\":\"BTN-1\"");
    assertThat(json).contains("\"pickedByTeamId\":\"" + teamId + "\"");
    assertThat(json).contains("\"customQuestion\":\"Question A\"");
    assertThat(json).contains("\"customAnswer\":\"Answer A\"");
    assertThat(json).contains("\"startedAt\":");
    assertThat(json).contains("\"scenario\":2");
  }
}
