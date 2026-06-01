package com.cevapinxile.cestereg.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.common.exception.E2eGameFixtureValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;

@ExtendWith(MockitoExtension.class)
@DisplayName("E2E fixture controller error contract")
class E2eGameFixtureControllerErrorContractTest {

  private static final String BASE_URL = "/api/e2e/v1/game-fixtures";

  @Mock private E2eGameFixtureService service;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    E2eGameFixtureController controller = new E2eGameFixtureController();
    ReflectionTestUtils.setField(controller, "e2eGameFixtureService", service);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void beanValidationFailureReturns400BeforeServiceIsCalled() throws Exception {
    mockMvc
        .perform(post(BASE_URL).contentType("application/json").content(minimalJson()))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                assertThat(result.getResolvedException())
                    .isInstanceOf(MethodArgumentNotValidException.class));

    verifyNoInteractions(service);
  }

  @Test
  void serviceValidationExceptionReturns400WithDerivedErrorBody() throws Exception {
    doThrow(
            new E2eGameFixtureValidationException(
                List.of("stage 2 requires startedAt", "bad interrupt")))
        .when(service)
        .createFixture(any(E2eGameFixtureRequest.class));

    mockMvc
        .perform(post(BASE_URL).contentType("application/json").content(validMinimalJson()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("E009 - Invalid e2e game fixture"))
        .andExpect(
            jsonPath("$.message").value("Violations: stage 2 requires startedAt; bad interrupt"));
  }

  private static String validMinimalJson() {
    return """
      {
        "id": "11111111-1111-1111-1111-111111111111",
        "roomCode": "AKKU",
        "maxSongs": 1,
        "maxAlbums": 1,
        "stage": 0,
        "teams": [
          {
            "id": "22222222-2222-2222-2222-222222222222",
            "buttonCode": "A",
            "name": "Team A"
          }
        ],
        "categories": [
          {
            "id": "33333333-3333-3333-3333-333333333333",
            "done": false,
            "album": {
              "id": "44444444-4444-4444-4444-444444444444",
              "name": "Album A",
              "tracks": [
                {
                  "customAnswer": "Song A"
                }
              ]
            }
          }
        ]
      }
      """;
  }

  private static String minimalJson() {
    return """
        {
          "id": "%s",
          "roomCode": "AKKU",
          "maxSongs": 1,
          "maxAlbums": 10,
          "stage": 2,
          "teams": [],
          "categories": []
        }
        """
        .formatted(UUID.randomUUID());
  }
}
