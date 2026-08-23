/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.core.service;

import com.cevapinxile.cestereg.api.quiz.dto.response.ImageAsset;
import com.cevapinxile.cestereg.common.exception.DerivedException;
import java.util.UUID;

/**
 * @author denijal
 */
public interface ImageService {

  ImageAsset getAlbumImage(UUID albumId) throws DerivedException;
}
