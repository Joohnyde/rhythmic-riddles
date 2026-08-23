package com.cevapinxile.cestereg.io.assets;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.AssetAccessException;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.config.AssetProperties;
import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local filesystem-based asset gateway.
 *
 * <p>Base directory is configured via: app.assets.base-dir: /absolute/path/to/data
 *
 * <p>Expected structure under base-dir: audio/snippets/<songId>.mp3 audio/answers/<songId>.mp3
 * images/teams/<teamId>.(png|jpg|jpeg|webp) images/albums/<albumId>.(png|jpg|jpeg|webp)
 *
 * @author denijal
 */
@Component
public class LocalAssetGateway implements AssetGateway {

  private static final Logger LOG = LoggerFactory.getLogger(LocalAssetGateway.class);

  private final Path basePath;

  public LocalAssetGateway(AssetProperties props) {
    this.basePath = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
    LOG.info("LocalAssetGateway basePath={}", basePath);
  }

  // -------------------- Audio --------------------
  @Override
  public byte[] readSnippetMp3(final UUID songId) throws DerivedException {
    return readFile(
        resolveAudioPath(songId, AudioType.SNIPPET),
        "Snippet not found for song " + songId,
        "Failed reading snippet for song " + songId);
  }

  @Override
  public byte[] readAnswerMp3(final UUID songId) throws DerivedException {
    return readFile(
        resolveAudioPath(songId, AudioType.ANSWER),
        "Answer not found for song " + songId,
        "Failed reading answer for song " + songId);
  }

  private Path resolveAudioPath(final UUID songId, final AudioType type) {
    return basePath.resolve("audio").resolve(type.folder()).resolve(songId + ".mp3");
  }

  // -------------------- Images --------------------
  @Override
  public ImageAsset readTeamImage(final UUID teamId) throws DerivedException {
    return readImage(
        basePath.resolve("images").resolve("teams"),
        teamId,
        "Image not found for team " + teamId,
        "Failed reading image for team " + teamId);
  }

  @Override
  public ImageAsset readAlbumImage(final UUID albumId) throws DerivedException {
    return readImage(
        basePath.resolve("images").resolve("albums"),
        albumId,
        "Image not found for album " + albumId,
        "Failed reading image for album " + albumId);
  }

  private ImageAsset readImage(
      final Path folder,
      final UUID id,
      final String notFoundMessage,
      final String unreadableMessage)
      throws DerivedException {

    final Optional<ImagePath> imagePath = findImage(folder, id);

    if (imagePath.isEmpty()) {
      throw new AssetAccessException(AssetAccessException.Reason.NOT_FOUND, notFoundMessage);
    }

    final ImagePath image = imagePath.get();

    return new ImageAsset(
        readFile(image.path(), notFoundMessage, unreadableMessage), image.extension().mimeType());
  }

  private Optional<ImagePath> findImage(final Path folder, final UUID id) {
    for (ImageExtension extension : ImageExtension.values()) {
      final Path candidate = folder.resolve(id + "." + extension.extension());

      if (Files.isRegularFile(candidate)) {
        return Optional.of(new ImagePath(candidate, extension));
      }
    }

    return Optional.empty();
  }

  // -------------------- File reading --------------------
  private byte[] readFile(
      final Path path, final String notFoundMessage, final String unreadableMessage)
      throws DerivedException {

    if (!Files.isRegularFile(path)) {
      throw new AssetAccessException(AssetAccessException.Reason.NOT_FOUND, notFoundMessage);
    }

    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new AssetAccessException(AssetAccessException.Reason.UNREADABLE, unreadableMessage);
    }
  }

  private record ImagePath(Path path, ImageExtension extension) {}
}

enum AudioType {
  SNIPPET("snippets"),
  ANSWER("answers");

  private final String folderName;

  AudioType(String folderName) {
    this.folderName = folderName;
  }

  public String folder() {
    return folderName;
  }

  @Override
  public String toString() {
    return folderName;
  }
}

enum ImageExtension {
  PNG("png", "image/png"),
  JPG("jpg", "image/jpeg"),
  JPEG("jpeg", "image/jpeg"),
  WEBP("webp", "image/webp");

  private final String extension;
  private final String mimeType;

  ImageExtension(String extension, String mimeType) {
    this.extension = extension;
    this.mimeType = mimeType;
  }

  public String extension() {
    return extension;
  }

  public String mimeType() {
    return mimeType;
  }
}
