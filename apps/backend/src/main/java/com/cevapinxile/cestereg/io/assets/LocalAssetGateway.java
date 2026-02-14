/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.io.assets;

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
 * Base directory is configured via: app.assets.base-dir: /absolute/path/to/data
 *
 * Expected structure under base-dir: audio/snippets/<songId>.mp3
 * audio/answers/<songId>.mp3 images/teams/<teamId>.(png|jpg|jpeg|webp)
 * images/albums/<albumId>.(png|jpg|jpeg|webp)
 *
 * @author denijal
 */
@Component
public class LocalAssetGateway implements AssetGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalAssetGateway.class);

    private static final String[] IMAGE_EXTS = new String[]{"png", "jpg", "jpeg", "webp"};

    private final Path basePath;

    public LocalAssetGateway(AssetProperties props) {
        // IMPORTANT: base-dir should point to the "data" folder.
        // Example: /home/denijal/Documents/cestereg/data
        this.basePath = Path.of(props.getBaseDir()).toAbsolutePath().normalize();
        log.info("LocalAssetGateway basePath={}", basePath);
    }

    // -------------------- Audio --------------------
    @Override
    public byte[] readSnippetMp3(UUID songId) throws DerivedException {
        Path path = resolveAudioPath(songId, AudioType.SNIPPET);
        if (!Files.exists(path)) {
            throw new AssetAccessException(AssetAccessException.Reason.NOT_FOUND, "Snippet not found for song " + songId);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new AssetAccessException(AssetAccessException.Reason.UNREADABLE, "Failed reading snippet for song " + songId);
        }
    }

    @Override
    public byte[] readAnswerMp3(UUID songId) throws DerivedException {
        Path path = resolveAudioPath(songId, AudioType.ANSWER);
        if (!Files.exists(path)) {
            throw new AssetAccessException(AssetAccessException.Reason.NOT_FOUND, "Answer not found for song " + songId);
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new AssetAccessException(AssetAccessException.Reason.UNREADABLE, "Failed reading answer for song " + songId);
        }
    }

    private Path resolveAudioPath(UUID songId, AudioType type) {
        return basePath
                .resolve("audio")
                .resolve(type.folder())
                .resolve(songId.toString() + ".mp3");
    }

    // -------------------- Images --------------------
    @Override
    public Optional<byte[]> readTeamImage(UUID teamId) throws IOException {
        return readFirstExistingImage(basePath.resolve("images").resolve("teams"), teamId);
    }

    @Override
    public Optional<byte[]> readAlbumImage(UUID albumId) throws IOException {
        return readFirstExistingImage(basePath.resolve("images").resolve("albums"), albumId);
    }

    private Optional<byte[]> readFirstExistingImage(Path folder, UUID id) throws IOException {
        for (String ext : IMAGE_EXTS) {
            Path candidate = folder.resolve(id.toString() + "." + ext);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return Optional.of(Files.readAllBytes(candidate));
            }
        }
        return Optional.empty();
    }
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
