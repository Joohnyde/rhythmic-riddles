package com.cevapinxile.cestereg.io.assets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.AssetAccessException;
import com.cevapinxile.cestereg.config.AssetProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocalAssetGatewayTest {

  @TempDir Path tempDir;

  @Nested
  class AlbumImages {

    @ParameterizedTest
    @CsvSource({"png,image/png", "jpg,image/jpeg", "jpeg,image/jpeg", "webp,image/webp"})
    void resolvesSupportedExtensionsWithTheirMimeTypes(
        final String extension, final String mimeType) throws Exception {
      final UUID albumId = UUID.randomUUID();
      final byte[] expectedBytes = new byte[] {3, 1, 4, 1, 5};
      writeImage("albums", albumId, extension, expectedBytes);

      final ImageAsset actual = gateway().readAlbumImage(albumId);

      assertArrayEquals(expectedBytes, actual.bytes());
      assertEquals(mimeType, actual.mimeType());
    }

    @Test
    void prefersFirstSupportedExtensionWhenDuplicateAlbumAssetsExist() throws Exception {
      final UUID albumId = UUID.randomUUID();
      writeImage("albums", albumId, "webp", new byte[] {9});
      writeImage("albums", albumId, "jpeg", new byte[] {8});
      writeImage("albums", albumId, "png", new byte[] {7});

      final ImageAsset actual = gateway().readAlbumImage(albumId);

      assertArrayEquals(new byte[] {7}, actual.bytes());
      assertEquals("image/png", actual.mimeType());
    }

    @Test
    void missingAlbumImageUsesAssetNotFoundContract() {
      final UUID albumId = UUID.randomUUID();

      final AssetAccessException actual =
          assertThrows(AssetAccessException.class, () -> gateway().readAlbumImage(albumId));

      assertEquals(
          "{\"error\":\"E007 - Asset Not Found\", \"message\":\"Image not found for album "
              + albumId
              + "\"}",
          actual.toString());
    }
  }

  @Nested
  class TeamImages {

    @Test
    void resolvesTeamImagesFromTheTeamDirectoryWithMimeMetadata() throws Exception {
      final UUID teamId = UUID.randomUUID();
      final byte[] expectedBytes = new byte[] {6, 2, 6};
      writeImage("teams", teamId, "webp", expectedBytes);

      final ImageAsset actual = gateway().readTeamImage(teamId);

      assertArrayEquals(expectedBytes, actual.bytes());
      assertEquals("image/webp", actual.mimeType());
    }

    @Test
    void missingTeamImageUsesTeamSpecificAssetNotFoundContract() {
      final UUID teamId = UUID.randomUUID();

      final AssetAccessException actual =
          assertThrows(AssetAccessException.class, () -> gateway().readTeamImage(teamId));

      assertEquals(
          "{\"error\":\"E007 - Asset Not Found\", \"message\":\"Image not found for team "
              + teamId
              + "\"}",
          actual.toString());
    }
  }

  private LocalAssetGateway gateway() {
    final AssetProperties properties = new AssetProperties();
    properties.setBaseDir(tempDir.toString());
    return new LocalAssetGateway(properties);
  }

  private void writeImage(
      final String directory, final UUID id, final String extension, final byte[] bytes)
      throws Exception {
    final Path folder = Files.createDirectories(tempDir.resolve("images").resolve(directory));
    Files.write(folder.resolve(id + "." + extension), bytes);
  }
}
