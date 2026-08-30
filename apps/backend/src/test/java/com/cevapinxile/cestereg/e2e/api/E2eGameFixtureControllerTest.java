package com.cevapinxile.cestereg.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.core.service.BuzzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("E2E game fixture controller")
class E2eGameFixtureControllerTest {

  private static final String BASE_URL = "/api/e2e/v1/game-fixtures";

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mock private E2eGameFixtureService fixtureService;
  @Mock private BuzzerService buzzerService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    E2eGameFixtureController controller = new E2eGameFixtureController();
    ReflectionTestUtils.setField(controller, "e2eGameFixtureService", fixtureService);
    ReflectionTestUtils.setField(controller, "buzzerService", buzzerService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Nested
  @DisplayName("DELETE /api/e2e/v1/game-fixtures/{roomCode}")
  class DeleteRoom {

    @Test
    void resetsRuntimeStateForExactlyTheRequestedRoomCode() throws Exception {
      mockMvc.perform(delete(BASE_URL + "/AKKU")).andExpect(status().isOk());

      verify(fixtureService).resetRuntimeState("AKKU");
    }

    @Test
    void doesNotSilentlyConvertRoomCodeBeforeCallingService() throws Exception {
      mockMvc.perform(delete(BASE_URL + "/akku")).andExpect(status().isOk());

      verify(fixtureService).resetRuntimeState("akku");
    }

    @Test
    void deleteRoomReturnsInternalServerErrorWhenServiceFails() throws Exception {
      org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
          .when(fixtureService)
          .resetRuntimeState("AKKU");

      mockMvc
          .perform(delete(BASE_URL + "/AKKU"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error").value("E999 - Internal Server Error"))
          .andExpect(jsonPath("$.message").value("Unexpected internal error"));
    }

    @Test
    void missingRoomCodeDoesNotInvokeService() throws Exception {
      mockMvc.perform(delete(BASE_URL + "/")).andExpect(status().isNotFound());

      verifyNoInteractions(fixtureService);
    }
  }

  @Nested
  @DisplayName("POST /api/e2e/v1/game-fixtures")
  class CreateFixture {

    @Test
    void forwardsACompleteDeepFixtureGraphWithoutLosingNestedObjectsOrRuntimeState()
        throws Exception {
      String gameId = UUID.randomUUID().toString();
      String teamId = UUID.randomUUID().toString();
      String categoryId = UUID.randomUUID().toString();
      String albumId = UUID.randomUUID().toString();
      String scheduleId = UUID.randomUUID().toString();
      String interruptId = UUID.randomUUID().toString();
      LocalDateTime startedAt = LocalDateTime.of(2026, 5, 31, 20, 15, 30);
      LocalDateTime revealedAt = startedAt.plusSeconds(20);
      LocalDateTime arrivedAt = startedAt.plusSeconds(3);
      LocalDateTime resolvedAt = arrivedAt.plusSeconds(7);

      String payload =
          """
          {
            "id": "%s",
            "roomCode": "AKKU",
            "maxSongs": 12,
            "maxAlbums": 4,
            "stage": 2,
            "teams": [
              {
                "id": "%s",
                "buttonCode": "BTN-1",
                "name": "Team Cyan",
                "image": "https://example.com/team-cyan.png"
              }
            ],
            "categories": [
              {
                "id": "%s",
                "pickedByTeamId": "%s",
                "ordinalNumber": 1,
                "done": false,
                "album": {
                  "id": "%s",
                  "name": "YU Rock",
                  "customQuestion": "Prepoznaj ovu pjesmu!",
                  "tracks": [
                    {
                      "customAnswer": "Song Name",
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
                            "correct": true,
                            "score": 10,
                            "scenario": 2
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

      mockMvc
          .perform(post(BASE_URL).contentType("application/json").content(payload))
          .andExpect(status().isOk());

      ArgumentCaptor<E2eGameFixtureRequest> requestCaptor =
          ArgumentCaptor.forClass(E2eGameFixtureRequest.class);
      verify(fixtureService).createFixture(requestCaptor.capture());

      E2eGameFixtureRequest request = requestCaptor.getValue();
      assertThat(request.id()).isEqualTo(UUID.fromString(gameId));
      assertThat(request.roomCode()).isEqualTo("AKKU");
      assertThat(request.maxSongs()).isEqualTo(12);
      assertThat(request.maxAlbums()).isEqualTo(4);
      assertThat(request.stage()).isEqualTo(2);

      E2eGameFixtureRequest.Team team = request.teams().getFirst();
      assertThat(team.id()).isEqualTo(UUID.fromString(teamId));
      assertThat(team.buttonCode()).isEqualTo("BTN-1");
      assertThat(team.name()).isEqualTo("Team Cyan");
      assertThat(team.image()).isEqualTo("https://example.com/team-cyan.png");

      E2eGameFixtureRequest.Category category = request.categories().getFirst();
      assertThat(category.id()).isEqualTo(UUID.fromString(categoryId));
      assertThat(category.pickedByTeamId()).isEqualTo(UUID.fromString(teamId));
      assertThat(category.ordinalNumber()).isEqualTo(1);
      assertThat(category.done()).isFalse();
      assertThat(category.album().id()).isEqualTo(UUID.fromString(albumId));
      assertThat(category.album().name()).isEqualTo("YU Rock");
      assertThat(category.album().customQuestion()).isEqualTo("Prepoznaj ovu pjesmu!");

      E2eGameFixtureRequest.Track track = category.album().tracks().getFirst();
      assertThat(track.customAnswer()).isEqualTo("Song Name");
      assertThat(track.schedule().id()).isEqualTo(UUID.fromString(scheduleId));
      assertThat(track.schedule().startedAt()).isEqualTo(startedAt);
      assertThat(track.schedule().revealedAt()).isEqualTo(revealedAt);
      assertThat(track.schedule().ordinalNumber()).isEqualTo(1);

      E2eGameFixtureRequest.Interrupt interrupt = track.schedule().interrupts().getFirst();
      assertThat(interrupt.id()).isEqualTo(UUID.fromString(interruptId));
      assertThat(interrupt.teamId()).isEqualTo(UUID.fromString(teamId));
      assertThat(interrupt.arrivedAt()).isEqualTo(arrivedAt);
      assertThat(interrupt.resolvedAt()).isEqualTo(resolvedAt);
      assertThat(interrupt.correct()).isTrue();
      assertThat(interrupt.score()).isEqualTo(10);
      assertThat(interrupt.scenario()).isEqualTo(2);
    }

    @Test
    void malformedJsonReturnsBadRequestAndDoesNotCreateAnything() throws Exception {
      mockMvc
          .perform(post(BASE_URL).contentType("application/json").content("{ this is not json"))
          .andExpect(status().isBadRequest());

      verify(fixtureService, never()).createFixture(any());
    }

    @Test
    void invalidUuidReturnsBadRequestAndDoesNotCreateAnything() throws Exception {
      String payload =
          """
          {
            "id": "not-a-uuid",
            "roomCode": "AKKU",
            "teams": [],
            "categories": []
          }
          """;

      mockMvc
          .perform(post(BASE_URL).contentType("application/json").content(payload))
          .andExpect(status().isBadRequest());

      verify(fixtureService, never()).createFixture(any());
    }

    @Test
    void invalidDateTimeReturnsBadRequestAndDoesNotCreateAnything() throws Exception {
      String payload =
          """
          {
            "id": "%s",
            "roomCode": "AKKU",
            "teams": [],
            "categories": [
              {
                "id": "%s",
                "album": {
                  "id": "%s",
                  "tracks": [
                    {
                      "schedule": {
                        "id": "%s",
                        "startedAt": "31-05-2026 20:15:30",
                        "interrupts": []
                      }
                    }
                  ]
                }
              }
            ]
          }
          """
              .formatted(
                  UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

      mockMvc
          .perform(post(BASE_URL).contentType("application/json").content(payload))
          .andExpect(status().isBadRequest());

      verify(fixtureService, never()).createFixture(any());
    }
  }

  @Nested
  @DisplayName("POST /api/e2e/v1/game-fixtures/receiver/{buttonCode}")
  class ReceiverBoundary {
    @Test
    void receiverButtonForwardsOneCodeToTheRealBuzzerBoundary() throws Exception {
      mockMvc.perform(post(BASE_URL + "/receiver/710001")).andExpect(status().isOk());

      verify(buzzerService).buzz("710001");
    }
  }
}
