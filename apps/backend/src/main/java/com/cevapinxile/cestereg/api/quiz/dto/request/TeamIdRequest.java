/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Schema(name = "TeamIdRequest")
public record TeamIdRequest (
    @Schema(nullable = true, description = "Optional team id. If omitted, interrupt is system-caused.")
    UUID teamId
){}
