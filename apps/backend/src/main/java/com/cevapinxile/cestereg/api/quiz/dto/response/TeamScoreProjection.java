/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import java.util.UUID;

/**
 *
 * @author denijal
 */
public interface TeamScoreProjection {
    UUID getTeam();
    String getImage();
    String getName();
    Integer getScore();
    UUID getSchedule();
}
