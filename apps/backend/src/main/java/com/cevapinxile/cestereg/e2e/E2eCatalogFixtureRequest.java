package com.cevapinxile.cestereg.e2e;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Finite catalog content attached to an HTTP-arranged runtime E2E room.
 *
 * @param categories deterministic categories to attach to the room
 */
public record E2eCatalogFixtureRequest(
    @Valid @NotEmpty List<E2eGameFixtureRequest.Category> categories) {}
