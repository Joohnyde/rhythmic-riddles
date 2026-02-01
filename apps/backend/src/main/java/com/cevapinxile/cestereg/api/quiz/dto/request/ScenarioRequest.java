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
@Schema(name = "ScenarioRequest")
public record ScenarioRequest(
        @Schema(example = "2", description = "Scenario id (0..4), but 3 is not allowed here.")
        int scenario) {

}
