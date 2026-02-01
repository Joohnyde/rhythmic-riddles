package com.cevapinxile.cestereg.config;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author denijal
 */

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "RhythmicRiddles Quiz Game API",
        version = "v1",
        description = "REST API for Admin/TV clients. Real-time updates are pushed via WebSockets."
    ),
    tags = {
        @Tag(name = "Games",      description = "Game lifecycle and stage transitions"),
        @Tag(name = "Teams",      description = "Team registration and moderation"),
        @Tag(name = "Categories", description = "Category/album selection and start"),
        @Tag(name = "Schedules",  description = "Song playback: replay/reveal/next"),
        @Tag(name = "Interrupts", description = "Buzz/interrupts, answers, recovery"),
        @Tag(name = "Assets",     description = "Audio assets (snippets/answers)")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {}
