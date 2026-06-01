package com.cevapinxile.cestereg.common.exception;

import java.util.List;

/**
 * @author denijal Thrown when an E2E game fixture payload is syntactically valid but semantically
 *     impossible.
 *     <p>Used to reject invalid seeded test states before persistence, such as:
 *     <ul>
 *       <li>chosen categories without exactly {@code maxSongs} scheduled tracks
 *       <li>stage 2 fixtures without a started song
 *       <li>multiple active songs
 *       <li>invalid interrupt state, scenario, ordering, or resolution consistency
 *       <li>references to teams that are not present in the fixture
 *     </ul>
 *     <p>Typical throw site:
 *     <ul>
 *       <li>{@code E2eGameFixtureValidator.validateOrThrow(...)} before fixture persistence
 *     </ul>
 */
public class E2eGameFixtureValidationException extends DerivedException {

  public E2eGameFixtureValidationException(List<String> violations) {
    super(400, "009", "Invalid e2e game fixture", "Violations: " + String.join("; ", violations));
  }
}
