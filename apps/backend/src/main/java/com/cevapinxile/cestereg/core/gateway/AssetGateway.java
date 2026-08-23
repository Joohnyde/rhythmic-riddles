/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.core.gateway;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/*
 * @author denijal
 */
public interface AssetGateway {

  // ---- Audio ----
  byte[] readSnippetMp3(UUID songId) throws DerivedException;

  byte[] readAnswerMp3(UUID songId) throws DerivedException;

  // ---- Images (teams/albums) ----
  ImageAsset readTeamImage(UUID teamId) throws DerivedException;

  ImageAsset readAlbumImage(UUID albumId) throws DerivedException;
}
