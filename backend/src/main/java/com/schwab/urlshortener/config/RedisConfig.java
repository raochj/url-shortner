package com.schwab.urlshortener.config;

import io.lettuce.core.api.StatefulConnection;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
  @Value("${spring.data.redis.host}")
  private String redisHost;

  @Value("${spring.data.redis.port}")
  private int redisPort;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Value("${spring.data.redis.lettuce.pool.max-active:8}")
  private int maxActive;

  @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
  private int maxIdle;

  @Value("${spring.data.redis.lettuce.pool.min-idle:2}")
  private int minIdle;

  @Bean
  @Primary
  public LettuceConnectionFactory redisConnectionFactory() {
    RedisStandaloneConfiguration serverConfig =
        new RedisStandaloneConfiguration(redisHost, redisPort);
    if (redisPassword != null && !redisPassword.isBlank()) {
      serverConfig.setPassword(redisPassword);
    }
    GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
    poolConfig.setMaxTotal(maxActive);
    poolConfig.setMaxIdle(maxIdle);
    poolConfig.setMinIdle(minIdle);
    poolConfig.setTestOnBorrow(true);
    poolConfig.setTestWhileIdle(true);
    poolConfig.setMinEvictableIdleDuration(Duration.ofSeconds(60));
    LettucePoolingClientConfiguration clientConfig =
        LettucePoolingClientConfiguration.builder()
            .poolConfig(poolConfig)
            .commandTimeout(Duration.ofSeconds(5))
            .build();
    return new LettuceConnectionFactory(serverConfig, clientConfig);
  }

  @Bean(name = "objectRedisTemplate")
  public RedisTemplate<String, Object> objectRedisTemplate() {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory());
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(jsonSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(jsonSerializer);
    template.afterPropertiesSet();
    return template;
  }
}
