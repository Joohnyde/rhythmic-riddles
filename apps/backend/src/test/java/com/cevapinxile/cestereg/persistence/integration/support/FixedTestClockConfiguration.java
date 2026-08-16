package com.cevapinxile.cestereg.persistence.integration.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Deterministic clock shared by persistence-backed service integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedTestClockConfiguration {

  public static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 5, 20, 0, 10);

  @Bean("fixedClock")
  @Primary
  Clock fixedClock() {
    return Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  }
}
