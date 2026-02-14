/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * @author denijal
 */
@Schema(name = "RoomCodeResponse")
public record RoomCodeResponse(
        @Schema(example = "AKKU", description = "4-character room code of the newly created game.")
        String roomCode) {

}
