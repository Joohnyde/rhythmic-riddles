package com.cevapinxile.cestereg.api.quiz.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.request.CreateGameRequest;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.GameService;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GameController.class)
class GameControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GameService gameService;

  @Nested
  class CreateGameTests {

    @Test
    void createGameReturnsRoomCodeResponse() throws Exception {
      when(gameService.createGame(any())).thenReturn("AKKU");

      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":10,\"maxAlbums\":3}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(jsonPath("$.roomCode").value("AKKU"));

      ArgumentCaptor<CreateGameRequest> captor = ArgumentCaptor.forClass(CreateGameRequest.class);
      verify(gameService).createGame(captor.capture());
      CreateGameRequest request = captor.getValue();
      assertEquals(10, request.maxSongs());
      assertEquals(3, request.maxAlbums());
    }

    @Test
    void createGameReturnsBadRequestForMissingBody() throws Exception {
      mockMvc.perform(post("/api/v1/games")).andExpect(status().isBadRequest());
    }

    @Test
    void createGameReturnsBadRequestForMalformedJson() throws Exception {
      mockMvc
          .perform(post("/api/v1/games").contentType(APPLICATION_JSON).content("{\"maxSongs\":"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void createGameForwardsNullFieldsWhenPayloadOmitsParameters() throws Exception {
      when(gameService.createGame(any())).thenReturn("AKKU");

      mockMvc
          .perform(post("/api/v1/games").contentType(APPLICATION_JSON).content("{}"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

      ArgumentCaptor<CreateGameRequest> captor = ArgumentCaptor.forClass(CreateGameRequest.class);
      verify(gameService).createGame(captor.capture());
      assertNull(captor.getValue().maxSongs());
      assertNull(captor.getValue().maxAlbums());
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForCreateGame")
    void createGameMapsDerivedException(int status, String code, String title, String message)
        throws Exception {
      when(gameService.createGame(any())).thenThrow(derived(status, code, title, message));

      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":10,\"maxAlbums\":3}"))
          .andExpect(status().is(status))
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(
              content()
                  .string(
                      "{\"error\":\"E"
                          + code
                          + " - "
                          + title
                          + "\", \"message\":\""
                          + message
                          + "\"}"));
    }

    @Test
    void createGameReturnsInternalServerErrorForUnexpectedException() throws Exception {
      when(gameService.createGame(any())).thenThrow(new RuntimeException("boom"));

      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":10,\"maxAlbums\":3}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void createGameAddsCorsHeaderWhenOriginPresent() throws Exception {
      when(gameService.createGame(any())).thenReturn("AKKU");

      mockMvc
          .perform(
              post("/api/v1/games")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":10,\"maxAlbums\":3}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForCreateGame() {
      return Stream.of(
          Arguments.of(
              422,
              "002",
              "Malformed argument",
              "maxSongs and maxAlbums must be positive integers."));
    }
  }

  @Nested
  class ChangeStageTests {

    @Test
    void changeStageReturnsOkAndForwardsStageId() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2}"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));

      verify(gameService).changeStage(2, "AKKU");
    }

    @Test
    void changeStageReturnsBadRequestForMissingBody() throws Exception {
      mockMvc
          .perform(put("/api/v1/games/{roomCode}/stage", "AKKU"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void changeStageReturnsBadRequestForMalformedJson() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void changeStageReturnsBadRequestWhenStageIdMissingFromPayload() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName(
        "RoomCodePath is documentation-only, so invalid room codes still reach the service")
    void changeStageDoesNotRejectInvalidRoomCodeAtControllerLevel() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "abc")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":1}"))
          .andExpect(status().isOk());

      verify(gameService).changeStage(1, "abc");
    }

    @ParameterizedTest
    @MethodSource("derivedExceptionsForChangeStage")
    void changeStageMapsDerivedException(int status, String code, String title, String message)
        throws Exception {

      doThrow(derived(status, code, title, message)).when(gameService).changeStage(2, "AKKU");

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2}"))
          .andExpect(status().is(status))
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(
              content()
                  .string(
                      "{\"error\":\"E"
                          + code
                          + " - "
                          + title
                          + "\", \"message\":\""
                          + message
                          + "\"}"));
    }

    @Test
    void changeStageReturnsInternalServerErrorForUnexpectedException() throws Exception {
      doThrow(new RuntimeException("boom")).when(gameService).changeStage(2, "AKKU");

      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2}"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
    }

    @Test
    void changeStageAddsCorsHeaderWhenOriginPresent() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .header("Origin", "https://example.com")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2}"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    static Stream<Arguments> derivedExceptionsForChangeStage() {
      return Stream.of(
          Arguments.of(422, "002", "Malformed argument", "stage_id must be between 0 and 3."),
          Arguments.of(409, "003", "Wrong game-state", "Illegal stage transition."),
          Arguments.of(503, "004", "App not reachable", "TV app is not reachable."));
    }
  }

  @Nested
  class CreateGameContract {

    @Test
    void createGameRejectsUnsupportedMethod() throws Exception {
      mockMvc.perform(get("/api/v1/games")).andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(gameService);
    }

    @Test
    void createGameRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(post("/api/v1/games").contentType("text/plain").content("nope"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(gameService);
    }

    @Test
    void createGamePreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void createGameMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(post("/api/v1/games").contentType(APPLICATION_JSON).content("{\"maxSongs\":"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }
  }

  @Nested
  class ChangeStageContract {

    @Test
    void changeStageRejectsUnsupportedMethod() throws Exception {
      mockMvc
          .perform(post("/api/v1/games/{roomCode}/stage", "AKKU"))
          .andExpect(status().isMethodNotAllowed());
      verifyNoInteractions(gameService);
    }

    @Test
    void changeStageRejectsUnsupportedMediaType() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU").contentType("text/plain").content("2"))
          .andExpect(status().isUnsupportedMediaType());
      verifyNoInteractions(gameService);
    }

    @Test
    void changeStageReturnsEmptyBodyOnSuccess() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2}"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }

    @Test
    void changeStagePreflightReturnsCorsHeaders() throws Exception {
      mockMvc
          .perform(
              options("/api/v1/games/{roomCode}/stage", "AKKU")
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "PUT"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void changeStageMalformedJsonDoesNotCallService() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }
  }

  @Nested
  class CreateGameBinding {

    @Test
    void createGameRejectsStringForNumericField() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":\"ten\",\"maxAlbums\":3}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }

    @Test
    void createGameAllowsNullMaxSongsAndForwardsIt() throws Exception {
      when(gameService.createGame(any())).thenReturn("ROOM");

      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":null,\"maxAlbums\":3}"))
          .andExpect(status().isOk());

      ArgumentCaptor<CreateGameRequest> captor = ArgumentCaptor.forClass(CreateGameRequest.class);
      verify(gameService).createGame(captor.capture());
      assertNull(captor.getValue().maxSongs());
      assertEquals(3, captor.getValue().maxAlbums());
    }

    @Test
    void createGameUnknownFieldBehaviorIsDocumented() throws Exception {
      when(gameService.createGame(any())).thenReturn("ROOM");

      mockMvc
          .perform(
              post("/api/v1/games")
                  .contentType(APPLICATION_JSON)
                  .content("{\"maxSongs\":10,\"maxAlbums\":3,\"extra\":\"ignored\"}"))
          .andExpect(status().isOk())
          .andExpect(content().json("{\"roomCode\":\"ROOM\"}"));
    }
  }

  @Nested
  class ChangeStageBinding {

    @Test
    void changeStageRejectsStringForStageId() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":\"two\"}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }

    @Test
    void changeStageRejectsExplicitNullStageId() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":null}"))
          .andExpect(status().isBadRequest());

      verifyNoInteractions(gameService);
    }

    @Test
    void changeStageIgnoresUnknownFieldsWhenMainPayloadIsValid() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/games/{roomCode}/stage", "AKKU")
                  .contentType(APPLICATION_JSON)
                  .content("{\"stageId\":2,\"extra\":\"ignored\"}"))
          .andExpect(status().isOk())
          .andExpect(content().string(""));
    }
  }
}
