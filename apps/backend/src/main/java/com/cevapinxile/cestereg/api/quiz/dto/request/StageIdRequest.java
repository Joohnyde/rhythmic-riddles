/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * @author denijal
 */
@Schema(name = "StageIdRequest")
public record StageIdRequest(
        @Schema(example = "1", description = "New stage id (0=lobby, 1=album selection, 2=song playing, 3=winner table).")
        int stageId) {

}
