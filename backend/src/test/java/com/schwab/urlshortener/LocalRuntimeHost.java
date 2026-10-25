package com.schwab.urlshortener;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import redis.embedded.RedisServer;

/** Temporary local runtime host used for browser-level smoke testing. */
public final class LocalRuntimeHost {
  private LocalRuntimeHost() {}

  public static void main(String[] args) throws Exception {
    RedisServer redis =
        RedisServer.newRedisServer()
            .port(6379)
            .setting("bind 127.0.0.1")
            .setting("requirepass local-runtime-only")
            .setting("appendonly no")
            .build();
    redis.start();
    try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPort(5432).start()) {
      System.setProperty("SPRING_DATASOURCE_URL", postgres.getJdbcUrl("postgres", "postgres"));
      System.setProperty("SPRING_DATASOURCE_USERNAME", "postgres");
      System.setProperty("SPRING_DATASOURCE_PASSWORD", "local-runtime-only");
      System.setProperty("SPRING_DATA_REDIS_PASSWORD", "local-runtime-only");
      System.setProperty(
          "APP_JWT_SECRET",
          "local-runtime-jwt-secret-that-is-at-least-sixty-four-characters-long-123456");
      System.setProperty(
          "APP_ANALYTICS_IP_HASH_SECRET", "local-runtime-ip-hash-secret-at-least-32-characters");
      SpringApplication application = new SpringApplication(SchwabUrlShortenerApplication.class);
      application.setDefaultProperties(
          Map.of("server.port", "8080", "management.health.redis.enabled", "false"));
      try (ConfigurableApplicationContext ignored = application.run(args)) {
        new CountDownLatch(1).await();
      }
    } finally {
      redis.stop();
    }
  }
}
