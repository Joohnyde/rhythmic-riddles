package com.cevapinxile.cestereg.config;

import com.cevapinxile.cestereg.platform.db.embedded.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Profile("embeddb")
@EnableConfigurationProperties(AppEmbeddedDbProperties.class)
public class EmbeddedPostgresConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfiguration.class);

    private EmbeddedPostgres pg;

    /**
     * Starts an embedded PostgreSQL instance when the "embeddb" Spring profile is active.
     *
     * Notes:
     * - Working/data directories are stored under the app's data base directory.
     * - listen_addresses is set from properties (so you can control bind interface).
     * - Port is optionally fixed via props; otherwise EmbeddedPostgres chooses one.
     * - SQL bootstrap is executed once via {@link PsqlScriptRunner} (marker in dataDir).
     */
    @Bean(name = "embeddedPostgres")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public EmbeddedPostgres embeddedPostgres(AppEmbeddedDbProperties props) throws Exception {

        Path base = AppDataDirs.appDataBaseDir().resolve("postgres");
        Path distBase = base.resolve("dist");
        Path dataDir  = base.resolve("data");
        Path sqlDir   = base.resolve("sql");

        Files.createDirectories(distBase);
        Files.createDirectories(dataDir);
        Files.createDirectories(sqlDir);

        log.info("Starting embedded Postgres (base={}, dist={}, data={})", base, distBase, dataDir);

        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                .setOverrideWorkingDirectory(distBase.toFile())
                .setDataDirectory(dataDir.toFile())
                .setCleanDataDirectory(false)
                .setServerConfig("listen_addresses", props.getHost());

        if (props.getPort() > 0) {
            builder.setPort(props.getPort());
            log.info("Embedded Postgres requested port: {}", props.getPort());
        } else {
            log.info("Embedded Postgres port not specified; will choose a free port.");
        }

        this.pg = builder.start();
        log.info("Embedded Postgres started on port {}", pg.getPort());

        // Helpful during packaging/debugging: see what got unpacked into distBase.
        if (log.isDebugEnabled()) {
            try (var s = Files.list(distBase)) {
                s.forEach(p -> log.debug("dist: {}{}", p, (Files.isDirectory(p) ? "/" : "")));
            }
        }

        // Run bootstrap scripts via real psql (supports \connect, \i, etc.)
        PsqlScriptRunner.runOnceFromClasspathDbFolder(
                props,
                pg.getPort(),
                distBase,
                dataDir
        );

        return pg;
    }

    /**
     * DataSource pointing to the embedded Postgres instance.
     * Boot will use this bean when the embeddb profile is active.
     */
    @Bean
    @DependsOn("embeddedPostgres")
    public DataSource dataSource(AppEmbeddedDbProperties props, EmbeddedPostgres pg) {
        String url = "jdbc:postgresql://" + props.getHost() + ":" + pg.getPort() + "/" + props.getDatabase();
        log.info("Creating DataSource for embedded Postgres: {}", url);

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
                log.info("Stopping embedded Postgres...");
                pg.close();
                log.info("Embedded Postgres stopped.");
            } catch (Exception e) {
                // Shutdown should never fail the JVM exit path.
                log.warn("Failed to stop embedded Postgres cleanly: {}", e.toString());
            }
        }
    }
}