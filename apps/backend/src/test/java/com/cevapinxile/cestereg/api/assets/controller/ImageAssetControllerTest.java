package com.cevapinxile.cestereg.api.assets.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.parseMediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.api.support.ControllerTestSupport;
import com.cevapinxile.cestereg.common.exception.AssetAccessException;
import com.cevapinxile.cestereg.common.exception.AssetAccessException.Reason;
import com.cevapinxile.cestereg.core.service.ImageService;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImageAssetController.class)
class ImageAssetControllerTest extends ControllerTestSupport {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ImageService imageService;

  @Nested
  class AlbumImageResponses {

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp"})
    void returnsExactImageBytesWithResolvedContentType(final String mimeType) throws Exception {
      final UUID albumId = UUID.randomUUID();
      final byte[] payload = new byte[] {0, 1, 2, (byte) 0xFF};
      when(imageService.getAlbumImage(albumId)).thenReturn(new ImageAsset(payload, mimeType));

      mockMvc
          .perform(get("/assets/v1/image/albums/{albumId}", albumId))
          .andExpect(status().isOk())
          .andExpect(content().contentType(parseMediaType(mimeType)))
          .andExpect(content().bytes(payload));

      verify(imageService).getAlbumImage(albumId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_FOUND", "UNREADABLE"})
    void mapsAssetAccessFailuresToTheirPublicHttpContract(final String reasonName)
        throws Exception {
      final UUID albumId = UUID.randomUUID();
      final Reason reason = Reason.valueOf(reasonName);
      final String message =
          reason == Reason.NOT_FOUND
              ? "Image not found for album " + albumId
              : "Failed reading image for album " + albumId;
      final AssetAccessException failure = new AssetAccessException(reason, message);
      when(imageService.getAlbumImage(albumId)).thenThrow(failure);

      mockMvc
          .perform(get("/assets/v1/image/albums/{albumId}", albumId))
          .andExpect(
              reason == Reason.NOT_FOUND ? status().isNotFound() : status().isServiceUnavailable())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().string(failure.toString()));
    }

    @Test
    void returnsInternalServerErrorForUnexpectedException() throws Exception {
      final UUID albumId = UUID.randomUUID();
      when(imageService.getAlbumImage(albumId)).thenThrow(new RuntimeException("boom"));

      mockMvc
          .perform(get("/assets/v1/image/albums/{albumId}", albumId))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
          .andExpect(content().string(containsString("E999 - Internal Server Error")))
          .andExpect(content().string(containsString("Unexpected internal error")));
    }
  }

  @Nested
  class AlbumImageRequestContract {

    @Test
    void malformedAlbumUuidIsRejectedBeforeServiceInvocation() throws Exception {
      mockMvc.perform(get("/assets/v1/image/albums/not-a-uuid")).andExpect(status().isBadRequest());

      verifyNoInteractions(imageService);
    }

    @Test
    void corsPreflightAllowsAlbumImageReads() throws Exception {
      mockMvc
          .perform(
              options("/assets/v1/image/albums/{albumId}", UUID.randomUUID())
                  .header("Origin", "https://example.com")
                  .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "*"));

      verifyNoInteractions(imageService);
    }
  }
}
