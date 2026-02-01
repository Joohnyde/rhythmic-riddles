/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.models.responses;

import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface TeamScoreProjection {
    UUID getTim();
    String getSlika();
    String getNaziv();
    Integer getBodovi();
    UUID getOdgovarao();
}
