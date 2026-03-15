package com.cevapinxile.cestereg.api.assets.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.core.service.SongService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AudioAssetController.class)
class AudioAssetControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SongService songService;

  @Test
  void playSnippetReturnsAudioBytesAndRangeHeader() throws Exception {
    UUID songId = UUID.randomUUID();
    byte[] payload = "snippet-bytes".getBytes(StandardCharsets.UTF_8);
    when(songService.playSnippet(songId)).thenReturn(payload);

    mockMvc
        .perform(get("/assets/v1/audio/snippets/{songId}", songId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(parseMediaType("audio/mpeg")))
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(content().bytes(payload));

    verify(songService).playSnippet(songId);
  }

  @Test
  void playSnippetMapsDerivedExceptionStatusAndBody() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playSnippet(songId))
        .thenThrow(derived(404, "001", "Invalid referenced object", "Snippet missing"));

    mockMvc
        .perform(get("/assets/v1/audio/snippets/{songId}", songId))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            content()
                .string(
                    "{\"error\":\"E001 - Invalid referenced object\", \"message\":\"Snippet missing\"}"));
  }

  @Test
  void playSnippetReturnsInternalServerErrorForUnexpectedException() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playSnippet(songId)).thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(get("/assets/v1/audio/snippets/{songId}", songId))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().string(containsString("Unexpected internal error")));
  }

  @Test
  void playSnippetAddsCorsHeaderWhenOriginPresent() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playSnippet(songId)).thenReturn(new byte[] {1, 2, 3});

    mockMvc
        .perform(
            get("/assets/v1/audio/snippets/{songId}", songId)
                .header("Origin", "https://example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  @Test
  void playAnswerReturnsAudioBytesAndRangeHeader() throws Exception {
    UUID songId = UUID.randomUUID();
    byte[] payload = "answer-bytes".getBytes(StandardCharsets.UTF_8);
    when(songService.playAnswer(songId)).thenReturn(payload);

    mockMvc
        .perform(get("/assets/v1/audio/answers/{songId}", songId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(parseMediaType("audio/mpeg")))
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(content().bytes(payload));

    verify(songService).playAnswer(songId);
  }

  @Test
  void playAnswerMapsDerivedExceptionStatusAndBody() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playAnswer(songId))
        .thenThrow(derived(503, "004", "App not reachable", "Answer exists but could not be read"));

    mockMvc
        .perform(get("/assets/v1/audio/answers/{songId}", songId))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            content()
                .string(
                    "{\"error\":\"E004 - App not reachable\", \"message\":\"Answer exists but could not be read\"}"));
  }

  @Test
  void playAnswerReturnsInternalServerErrorForUnexpectedException() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playAnswer(songId)).thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(get("/assets/v1/audio/answers/{songId}", songId))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().string(containsString("Unexpected internal error")));
  }

  @Test
  void playAnswerAddsCorsHeaderWhenOriginPresent() throws Exception {
    UUID songId = UUID.randomUUID();
    when(songService.playAnswer(songId)).thenReturn(new byte[] {4, 5, 6});

    mockMvc
        .perform(
            get("/assets/v1/audio/answers/{songId}", songId)
                .header("Origin", "https://example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  @Test
  void snippetRejectsUnsupportedHttpMethod() throws Exception {
    mockMvc
        .perform(post("/assets/v1/audio/snippets/{songId}", UUID.randomUUID()))
        .andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(songService);
  }

  @Test
  void answerRejectsUnsupportedHttpMethod() throws Exception {
    mockMvc
        .perform(post("/assets/v1/audio/answers/{songId}", UUID.randomUUID()))
        .andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(songService);
  }

  @Test
  void snippetOptionsPreflightIncludesCorsHeaders() throws Exception {
    mockMvc
        .perform(
            options("/assets/v1/audio/snippets/{songId}", UUID.randomUUID())
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  @Test
  void answerOptionsPreflightIncludesCorsHeaders() throws Exception {
    mockMvc
        .perform(
            options("/assets/v1/audio/answers/{songId}", UUID.randomUUID())
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  @Test
  void snippetResponseDoesNotPretendToBeJson() throws Exception {
    UUID id = UUID.randomUUID();
    when(songService.playSnippet(id)).thenReturn(new byte[] {1, 2, 3});

    mockMvc
        .perform(get("/assets/v1/audio/snippets/{songId}", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", parseMediaType("audio/mpeg").toString()));
  }

  @Test
  void malformedSnippetUuidDoesNotCallService() throws Exception {
    mockMvc.perform(get("/assets/v1/audio/snippets/not-a-uuid")).andExpect(status().isBadRequest());
    verifyNoInteractions(songService);
  }

  @Test
  void malformedAnswerUuidDoesNotCallService() throws Exception {
    mockMvc.perform(get("/assets/v1/audio/answers/not-a-uuid")).andExpect(status().isBadRequest());
    verifyNoInteractions(songService);
  }
}
