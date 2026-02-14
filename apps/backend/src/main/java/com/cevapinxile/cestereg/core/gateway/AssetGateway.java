/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.gateway;

import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface AssetGateway {

    // ---- Audio ----

    byte[] readSnippetMp3(UUID songId) throws DerivedException;

    byte[] readAnswerMp3(UUID songId) throws DerivedException;

    // ---- Images (teams/albums) ----
    
    Optional<byte[]> readTeamImage(UUID teamId) throws IOException;

    Optional<byte[]> readAlbumImage(UUID albumId) throws IOException;
}
