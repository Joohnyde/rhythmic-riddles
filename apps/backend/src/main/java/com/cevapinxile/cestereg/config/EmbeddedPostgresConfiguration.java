package com.cevapinxile.cestereg.config;

import com.cevapinxile.cestereg.platform.db.embedded.AppDataDirs;
import com.cevapinxile.cestereg.platform.db.embedded.AppEmbeddedDbProperties;
import com.cevapinxile.cestereg.platform.db.embedded.PsqlScriptRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("embeddb")
@EnableConfigurationProperties(AppEmbeddedDbProperties.class)
public class EmbeddedPostgresConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgresConfiguration.class);

  private EmbeddedPostgres pg;

  /**
   * Starts and exposes an embedded PostgreSQL instance for the {@code embeddb} profile.
   *
   * <p>The server uses application-managed working and data directories under the app data base
   * directory, binds to {@code props.getHost()}, and uses {@code props.getPort()} when configured
   * or an automatically selected free port otherwise.
   *
   * <p>After startup, SQL bootstrap scripts are executed once against the instance via {@link
   * PsqlScriptRunner}.
   *
   * @param props embedded database configuration
   * @return the started embedded PostgreSQL instance
   * @throws Exception if startup or SQL bootstrap fails
   */
  @Bean(name = "embeddedPostgres")
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public EmbeddedPostgres embeddedPostgres(final AppEmbeddedDbProperties props) throws Exception {

    final Path base = AppDataDirs.appDataBaseDir().resolve("postgres");
    final Path distBase = base.resolve("dist");
    final Path dataDir = base.resolve("data");
    final Path sqlDir = base.resolve("sql");

    Files.createDirectories(distBase);
    Files.createDirectories(dataDir);
    Files.createDirectories(sqlDir);

    LOG.info("Starting embedded Postgres (base={}, dist={}, data={})", base, distBase, dataDir);

    final EmbeddedPostgres.Builder builder =
        EmbeddedPostgres.builder()
            .setOverrideWorkingDirectory(distBase.toFile())
            .setDataDirectory(dataDir.toFile())
            .setCleanDataDirectory(false)
            .setServerConfig("listen_addresses", props.getHost());

    if (props.getPort() > 0) {
      builder.setPort(props.getPort());
      LOG.info("Embedded Postgres requested port: {}", props.getPort());
    } else {
      LOG.info("Embedded Postgres port not specified; will choose a free port.");
    }

    this.pg = builder.start();
    LOG.info("Embedded Postgres started on port {}", pg.getPort());

    // Helpful during packaging/debugging: see what got unpacked into distBase.
    if (LOG.isDebugEnabled()) {
      try (var s = Files.list(distBase)) {
        s.forEach(p -> LOG.debug("dist: {}{}", p, (Files.isDirectory(p) ? "/" : "")));
      }
    }

    // Run bootstrap scripts via real psql (supports \connect, \i, etc.)
    PsqlScriptRunner.runOnceFromClasspathDbFolder(props, pg.getPort(), distBase, dataDir);

    return pg;
  }

  /**
   * Creates the {@link DataSource} connected to the embedded PostgreSQL instance.
   *
   * <p>Depends on {@code embeddedPostgres} so the server is started before the DataSource is
   * created. When the {@code embeddb} profile is active, Spring Boot uses this bean for database
   * access.
   *
   * @param props embedded database configuration
   * @param pg the running embedded PostgreSQL instance
   * @return DataSource connected to the embedded database
   */
  @Bean
  @DependsOn("embeddedPostgres")
  public DataSource dataSource(final AppEmbeddedDbProperties props, final EmbeddedPostgres pg) {
    final String url =
        "jdbc:postgresql://" + props.getHost() + ":" + pg.getPort() + "/" + props.getDatabase();
    LOG.info("Creating DataSource for embedded Postgres: {}", url);

    return DataSourceBuilder.create()
        .url(url)
        .username(props.getUsername())
        .password(props.getPassword())
        .build();
  }

  @PreDestroy
  public void stop() {
    if (pg != null) {
      try {
        LOG.info("Stopping embedded Postgres...");
        pg.close();
        LOG.info("Embedded Postgres stopped.");
      } catch (IOException e) {
        // Shutdown should never fail the JVM exit path.
        LOG.warn("Failed to stop embedded Postgres cleanly: {}", e.toString());
      }
    }
  }
}
