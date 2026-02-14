package com.cevapinxile.cestereg.common.util;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(
    name = "roomCode",
    description = "Unique 4-character uppercase room identifier of the game session.",
    required = true,
    example = "AKKU",
    schema = @Schema(
        type = "string",
        pattern = "^[A-Z]{4}$"
    )
)
public @interface RoomCodePath {
}
