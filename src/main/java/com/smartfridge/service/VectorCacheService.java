package com.smartfridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for caching vector embeddings and search results in Redis.
 * Implements Cache-Aside Pattern for vector search optimization.
 * 
 * Flow:
 * 1. User search -> Check Redis for cached results
 * 2. Cache miss -> Call Ollama + Qdrant -> Store in Redis -> Return to user
 * 3. Cache hit -> Return cached results directly
 */
@Service
public class VectorCacheService {

    private static final String EMBEDDING_KEY_PREFIX = "vector:embedding:";
    private static final String SEARCH_KEY_PREFIX = "vector:search:";

    @Value("${vector.cache.ttl:3600}")
    private long cacheTtlSeconds;

    @Autowired
    private RedisTemplate<String, List<Double>> vectorRedisTemplate;

    @Autowired
    private RedisTemplate<String, String> searchResultRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean available = false;

    @PostConstruct
    public void initialize() {
        try {
            // Test Redis connection
            var connectionFactory = vectorRedisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                throw new IllegalStateException("Redis connection factory is null");
            }
            connectionFactory.getConnection().ping();
            available = true;
            System.out.println("VectorCacheService initialized successfully. TTL: " + cacheTtlSeconds + "s");
        } catch (Exception e) {
            System.err.println("Redis not available, caching disabled: " + e.getMessage());
            available = false;
        }
    }

    /**
     * Check if Redis cache is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Get cached embedding vector for a query.
     * 
     * @param query The search query text
     * @return Cached embedding as float array, or null if not cached
     */
    public float[] getCachedEmbedding(String query) {
        if (!available || query == null) {
            return null;
        }

        try {
            String key = buildEmbeddingKey(query);
            List<Double> cached = vectorRedisTemplate.opsForValue().get(key);

            if (cached != null && !cached.isEmpty()) {
                System.out.println("[VectorCache] Embedding cache HIT for: " + truncateQuery(query));
                float[] result = new float[cached.size()];
                for (int i = 0; i < cached.size(); i++) {
                    result[i] = cached.get(i).floatValue();
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("[VectorCache] Error reading embedding cache: " + e.getMessage());
        }

        System.out.println("[VectorCache] Embedding cache MISS for: " + truncateQuery(query));
        return null;
    }

    /**
     * Cache an embedding vector for a query.
     * 
     * @param query     The search query text
     * @param embedding The embedding vector to cache
     */
    public void cacheEmbedding(String query, float[] embedding) {
        if (!available || query == null || embedding == null) {
            return;
        }

        try {
            String key = buildEmbeddingKey(query);
            List<Double> vectorList = new ArrayList<>(embedding.length);
            for (float v : embedding) {
                vectorList.add((double) v);
            }

            vectorRedisTemplate.opsForValue().set(key, vectorList, cacheTtlSeconds, TimeUnit.SECONDS);
            System.out.println("[VectorCache] Cached embedding for: " + truncateQuery(query));
        } catch (Exception e) {
            System.err.println("[VectorCache] Error caching embedding: " + e.getMessage());
        }
    }

    /**
     * Get cached search results for a query.
     * 
     * @param cacheKey The cache key (pre-built from ingredients + query)
     * @return Cached search results, or null if not cached
     */
    public List<VectorSearchService.SearchResult> getCachedSearchResults(String cacheKey) {
        if (!available || cacheKey == null) {
            return null;
        }

        try {
            String key = SEARCH_KEY_PREFIX + hashKey(cacheKey);
            String cachedJson = searchResultRedisTemplate.opsForValue().get(key);

            if (cachedJson != null && !cachedJson.isEmpty()) {
                System.out.println("[VectorCache] Search cache HIT for key: " + truncateQuery(cacheKey));
                return objectMapper.readValue(cachedJson,
                        new TypeReference<List<VectorSearchService.SearchResult>>() {
                        });
            }
        } catch (Exception e) {
            System.err.println("[VectorCache] Error reading search cache: " + e.getMessage());
        }

        System.out.println("[VectorCache] Search cache MISS for key: " + truncateQuery(cacheKey));
        return null;
    }

    /**
     * Cache search results.
     * 
     * @param cacheKey The cache key (pre-built from ingredients + query)
     * @param results  The search results to cache
     */
    public void cacheSearchResults(String cacheKey, List<VectorSearchService.SearchResult> results) {
        if (!available || cacheKey == null || results == null) {
            return;
        }

        try {
            String key = SEARCH_KEY_PREFIX + hashKey(cacheKey);
            String json = objectMapper.writeValueAsString(results);

            searchResultRedisTemplate.opsForValue().set(key, json, cacheTtlSeconds, TimeUnit.SECONDS);
            System.out.println("[VectorCache] Cached " + results.size() + " search results for key: "
                    + truncateQuery(cacheKey));
        } catch (Exception e) {
            System.err.println("[VectorCache] Error caching search results: " + e.getMessage());
        }
    }

    /**
     * Evict all cache entries matching a pattern.
     * 
     * @param pattern The pattern to match (e.g., "vector:*")
     */
    public void evictCacheByPattern(String pattern) {
        if (!available) {
            return;
        }

        try {
            var keys = searchResultRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                searchResultRedisTemplate.delete(keys);
                System.out.println("[VectorCache] Evicted " + keys.size() + " keys matching: " + pattern);
            }
        } catch (Exception e) {
            System.err.println("[VectorCache] Error evicting cache: " + e.getMessage());
        }
    }

    /**
     * Clear all vector caches.
     */
    public void clearAllCaches() {
        evictCacheByPattern(EMBEDDING_KEY_PREFIX + "*");
        evictCacheByPattern(SEARCH_KEY_PREFIX + "*");
    }

    /**
     * Build a cache key from ingredients and query.
     * 
     * @param ingredients List of ingredients (can be null)
     * @param query       Search query (can be null)
     * @return Unique cache key string
     */
    public String buildSearchCacheKey(List<String> ingredients, String query) {
        StringBuilder sb = new StringBuilder();
        if (ingredients != null && !ingredients.isEmpty()) {
            List<String> sorted = new ArrayList<>(ingredients);
            sorted.sort(String::compareToIgnoreCase);
            sb.append("ing:").append(String.join(",", sorted));
        }
        if (query != null && !query.isEmpty()) {
            sb.append("|q:").append(query.toLowerCase().trim());
        }
        return sb.toString();
    }

    private String buildEmbeddingKey(String query) {
        return EMBEDDING_KEY_PREFIX + hashKey(query);
    }

    /**
     * Create a hash of the key for shorter Redis keys.
     */
    private String hashKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 8; i++) { // Use first 8 bytes for shorter key
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }

    private String truncateQuery(String query) {
        if (query == null) {
            return "null";
        }
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }
}
