/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Record.java to edit this template
 */
package com.cevapinxile.cestereg.api.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 *
 * @author denijal
 */
@Schema(name = "ScheduleIdRequest")
public record ScheduleIdRequest(
        @Schema(description = "UUID of the last played song (schedule).")
        UUID scheduleId) {

}
