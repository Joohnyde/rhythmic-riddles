/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service.impl;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import com.cevapinxile.cestereg.core.gateway.AssetGateway;
import com.cevapinxile.cestereg.core.service.ImageService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author denijal
 */
@Service
public class ImageServiceImpl implements ImageService {

  @Autowired private AssetGateway assetGateway;

  @Override
  public ImageAsset getAlbumImage(UUID albumId) throws DerivedException {
    return assetGateway.readAlbumImage(albumId);
  }
}
