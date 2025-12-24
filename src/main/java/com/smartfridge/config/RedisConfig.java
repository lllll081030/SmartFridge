package com.smartfridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis configuration for vector caching.
 * Provides RedisTemplate configured for storing vector embeddings and search
 * results.
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate for storing vector embeddings.
     * Key: query string hash
     * Value: List<Double> embedding vector
     */
    @Bean
    public RedisTemplate<String, List<Double>> vectorRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, List<Double>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    /**
     * RedisTemplate for storing search results as JSON.
     * Key: query string hash
     * Value: Serialized search results
     */
    @Bean
    public RedisTemplate<String, String> searchResultRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
