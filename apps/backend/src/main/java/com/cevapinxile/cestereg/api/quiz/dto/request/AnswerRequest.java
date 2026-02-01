/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * @author denijal
 */
@Schema(name = "AnswerRequest")
public record AnswerRequest(
        @Schema(example = "true", description = "True if the team guessed correctly, false otherwise.")
        boolean correct) {

}
