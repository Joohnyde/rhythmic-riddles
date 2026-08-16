package com.cevapinxile.cestereg.persistence.integration.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(
    showSql = false,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.sql.init.mode=never"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class PostgresJpaIntegrationTest {

  @DynamicPropertySource
  static void postgresProperties(final DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", EmbeddedPostgresTestDatabase::jdbcUrl);
    registry.add("spring.datasource.username", () -> "postgres");
    registry.add("spring.datasource.password", () -> "postgres");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }
}
