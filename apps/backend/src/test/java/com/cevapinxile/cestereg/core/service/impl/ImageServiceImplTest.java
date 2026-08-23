package com.cevapinxile.cestereg.core.service.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.AssetAccessException;
import com.cevapinxile.cestereg.common.exception.AssetAccessException.Reason;
import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class ImageServiceImplTest {

  @Nested
  @ExtendWith(MockitoExtension.class)
  class AlbumImageAccess {

    @Mock private AssetGateway assetGateway;

    @InjectMocks private ImageServiceImpl imageService;

    @Test
    void returnsGatewayImageAssetWithoutLosingPayloadOrMimeMetadata() throws Exception {
      final UUID albumId = UUID.randomUUID();
      final ImageAsset expected = new ImageAsset(new byte[] {1, 2, 3}, "image/webp");
      when(assetGateway.readAlbumImage(albumId)).thenReturn(expected);

      final ImageAsset actual = imageService.getAlbumImage(albumId);

      assertSame(expected, actual);
      verify(assetGateway).readAlbumImage(albumId);
    }

    @Test
    void propagatesAssetFailureWithoutRemappingItsPublicErrorContract() throws Exception {
      final UUID albumId = UUID.randomUUID();
      final AssetAccessException expected =
          new AssetAccessException(Reason.NOT_FOUND, "Image not found for album " + albumId);
      when(assetGateway.readAlbumImage(albumId)).thenThrow(expected);

      final AssetAccessException actual =
          assertThrows(AssetAccessException.class, () -> imageService.getAlbumImage(albumId));

      assertSame(expected, actual);
      verify(assetGateway).readAlbumImage(albumId);
    }
  }
}
