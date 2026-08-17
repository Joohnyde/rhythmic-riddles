package com.cevapinxile.cestereg.api.support;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/** Shared Swagger documentation for room-mutating REST operations. */
@Configuration
@OpenAPIDefinition(
    tags = {
      @Tag(
          name = "Games",
          description =
              "Room mutations are serialized per game. "
                  + "A competing command may return 423 / E010 and should be retried against the latest state."),
      @Tag(
          name = "Teams",
          description =
              "Room mutations are serialized per game. "
                  + "A competing command may return 423 / E010 and should be retried against the latest state."),
      @Tag(
          name = "Categories",
          description =
              "Room mutations are serialized per game. "
                  + "A competing command may return 423 / E010 and should be retried against the latest state."),
      @Tag(
          name = "Schedules",
          description =
              "Room mutations are serialized per game. "
                  + "A competing command may return 423 / E010 and should be retried against the latest state."),
      @Tag(
          name = "Interrupts",
          description =
              "Room mutations are serialized per game. "
                  + "Team commands fail fast on contention; must-persist system/recovery events wait for the room lock.")
    })
public class RoomLockingOpenApiDocumentation {}
