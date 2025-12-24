package com.smartfridge.service;

import com.smartfridge.dao.RecipeDao;
import com.smartfridge.model.RecipeDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for vector-based semantic search using Qdrant REST API.
 * Stores recipe embeddings and provides similarity search.
 */
@Service
public class VectorSearchService {

    // V2: Multi-vector collection with dense (semantic) + sparse (BM25) vectors
    private static final String COLLECTION_NAME = "recipes_v2";
    private static final int VECTOR_SIZE = 768; // nomic-embed-text dimension

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private SparseEmbeddingService sparseEmbeddingService;

    @Autowired
    private RecipeDao recipeDao;

    @Autowired
    private VectorCacheService vectorCacheService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean initialized = false;

    /**
     * Initialize Qdrant collection on startup.
     */
    @PostConstruct
    public void initialize() {
        try {
            // Check if Qdrant is reachable
            String healthUrl = getBaseUrl() + "/";
            restTemplate.getForEntity(healthUrl, String.class);

            ensureCollectionExists();
            initialized = true;
            System.out.println("VectorSearchService initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize VectorSearchService: " + e.getMessage());
            System.err.println("Semantic search will be unavailable. Make sure Qdrant is running.");
            initialized = false;
        }
    }

    private String getBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantPort;
    }

    /**
     * Check if the service is available.
     */
    public boolean isAvailable() {
        return initialized && embeddingService.isAvailable();
    }

    /**
     * Ensure the recipes collection exists in Qdrant.
     */
    private void ensureCollectionExists() {
        try {
            // Check if collection exists
            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME;
            try {
                restTemplate.getForEntity(url, String.class);
                System.out.println("Collection '" + COLLECTION_NAME + "' already exists");
            } catch (Exception e) {
                // Collection doesn't exist, create it
                createCollection();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure collection exists", e);
        }
    }

    /**
     * Create multi-vector collection with:
     * - dense: 768d semantic vectors (Cosine distance)
     * - sparse: BM25-style keyword vectors with IDF
     */
    private void createCollection() {
        String url = getBaseUrl() + "/collections/" + COLLECTION_NAME;

        ObjectNode config = objectMapper.createObjectNode();

        // Named dense vectors for semantic search
        ObjectNode vectors = objectMapper.createObjectNode();
        ObjectNode denseConfig = objectMapper.createObjectNode();
        denseConfig.put("size", VECTOR_SIZE);
        denseConfig.put("distance", "Cosine");
        vectors.set("dense", denseConfig);
        config.set("vectors", vectors);

        // Sparse vectors for BM25-style keyword matching
        ObjectNode sparseVectors = objectMapper.createObjectNode();
        ObjectNode sparseConfig = objectMapper.createObjectNode();
        // Enable IDF modifier for BM25-like scoring
        sparseConfig.put("modifier", "idf");
        sparseVectors.set("sparse", sparseConfig);
        config.set("sparse_vectors", sparseVectors);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(config.toString(), headers);

        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        System.out.println("Created multi-vector collection '" + COLLECTION_NAME + "' with dense (" + VECTOR_SIZE
                + "d) + sparse vectors");
    }

    /**
     * Index a single recipe into Qdrant.
     */
    /**
     * Index a single recipe into Qdrant with both dense and sparse vectors.
     */
    public void indexRecipe(String recipeName) {
        if (!initialized) {
            System.err.println("VectorSearchService not initialized, skipping indexing");
            return;
        }

        try {
            RecipeDetails details = recipeDao.getRecipeDetails(recipeName);
            if (details == null) {
                System.err.println("Recipe not found: " + recipeName);
                return;
            }

            // Generate dense embedding for semantic search
            String recipeText = embeddingService.createRecipeText(
                    details.getName(),
                    details.getIngredients(),
                    details.getCuisineType() != null ? details.getCuisineType().name() : null,
                    details.getInstructions());

            float[] denseEmbedding = embeddingService.generateEmbedding(recipeText);
            if (denseEmbedding == null) {
                System.err.println("Failed to generate dense embedding for: " + recipeName);
                return;
            }

            // Generate sparse embedding for keyword matching
            SparseEmbeddingService.SparseVector sparseVector = sparseEmbeddingService.generateFromRecipe(
                    details.getName(),
                    details.getIngredients(),
                    details.getCuisineType() != null ? details.getCuisineType().name() : null);

            // Use recipe name hash as point ID
            long pointId = Math.abs(recipeName.hashCode());

            // Build upsert request with named vectors
            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME + "/points";

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode points = objectMapper.createArrayNode();

            ObjectNode point = objectMapper.createObjectNode();
            point.put("id", pointId);

            // Named vectors object
            ObjectNode vectorsNode = objectMapper.createObjectNode();

            // Dense vector
            ArrayNode denseArray = objectMapper.createArrayNode();
            for (float v : denseEmbedding) {
                denseArray.add(v);
            }
            vectorsNode.set("dense", denseArray);

            // Sparse vector (indices + values)
            if (!sparseVector.isEmpty()) {
                ObjectNode sparseNode = objectMapper.createObjectNode();
                ArrayNode indicesArray = objectMapper.createArrayNode();
                ArrayNode valuesArray = objectMapper.createArrayNode();
                for (int idx : sparseVector.getIndices()) {
                    indicesArray.add(idx);
                }
                for (float val : sparseVector.getValues()) {
                    valuesArray.add(val);
                }
                sparseNode.set("indices", indicesArray);
                sparseNode.set("values", valuesArray);
                vectorsNode.set("sparse", sparseNode);
            }

            point.set("vector", vectorsNode);

            // Payload with recipe metadata
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("recipe_name", recipeName);
            payload.put("cuisine_type", details.getCuisineType() != null ? details.getCuisineType().name() : "OTHER");
            payload.put("model_version", embeddingService.getModelVersion());

            // Store ingredients list for display
            ArrayNode ingredientsArray = objectMapper.createArrayNode();
            for (String ing : details.getIngredients()) {
                ingredientsArray.add(ing);
            }
            payload.set("ingredients", ingredientsArray);
            point.set("payload", payload);

            points.add(point);
            request.set("points", points);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            System.out.println("Indexed recipe with dual vectors: " + recipeName);
        } catch (Exception e) {
            System.err.println("Error indexing recipe: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Index all recipes from the database into Qdrant.
     */
    public int indexAllRecipes() {
        if (!initialized) {
            System.err.println("VectorSearchService not initialized");
            return 0;
        }

        int count = 0;
        try {
            Map<String, List<com.smartfridge.model.RecipeSimple>> recipesByCuisine = recipeDao.getAllRecipesByCuisine();

            for (List<com.smartfridge.model.RecipeSimple> recipes : recipesByCuisine.values()) {
                for (com.smartfridge.model.RecipeSimple recipe : recipes) {
                    indexRecipe(recipe.getName());
                    count++;
                }
            }

            System.out.println("Indexed " + count + " recipes");
        } catch (Exception e) {
            System.err.println("Error indexing all recipes: " + e.getMessage());
        }

        return count;
    }

    /**
     * Search for similar recipes using semantic similarity.
     * Only returns results with score above minimum threshold.
     */
    public List<SearchResult> searchSimilar(String query, int topK) {
        List<SearchResult> results = new ArrayList<>();
        final float MIN_SCORE_THRESHOLD = 0.5f; // Minimum relevance score (0-1)

        if (!initialized) {
            System.err.println("VectorSearchService not initialized");
            return results;
        }

        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding == null) {
                System.err.println("Failed to generate query embedding");
                return results;
            }

            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME + "/points/search";

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode vector = objectMapper.createArrayNode();
            for (float v : queryEmbedding) {
                vector.add(v);
            }
            request.set("vector", vector);
            request.put("limit", topK);
            request.put("with_payload", true);
            request.put("score_threshold", MIN_SCORE_THRESHOLD); // Filter low-relevance results

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode resultArray = root.path("result");

                if (resultArray.isArray()) {
                    for (JsonNode point : resultArray) {
                        String recipeName = point.path("payload").path("recipe_name").asText();
                        float score = (float) point.path("score").asDouble();
                        String cuisineType = point.path("payload").path("cuisine_type").asText(null);

                        // Double-check threshold (some Qdrant versions ignore score_threshold)
                        if (score >= MIN_SCORE_THRESHOLD) {
                            // NEW: Keyword filtering - ensure recipe contains important keywords from query
                            if (containsImportantKeywords(recipeName, query)) {
                                results.add(new SearchResult(recipeName, score, cuisineType));
                            } else {
                                System.out.println("[FILTER] Skipping '" + recipeName + "' - no keyword match for '"
                                        + query + "'");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error searching recipes: " + e.getMessage());
        }

        return results;
    }

    /**
     * Check if recipe name contains important keywords from the query.
     * Filters out keywords that are too short or too common.
     */
    private boolean containsImportantKeywords(String recipeName, String query) {
        if (query == null || query.isEmpty()) {
            return true; // No filtering if no query
        }

        String recipeNameLower = recipeName.toLowerCase();
        String queryLower = query.toLowerCase();

        // Common words to ignore (stop words)
        Set<String> stopWords = Set.of(
                "with", "and", "the", "for", "recipe", "dish", "food",
                "make", "cook", "how", "to", "is", "in", "on", "at");

        // Extract important keywords (length > 3, not stop words)
        String[] keywords = queryLower.split("\\s+");
        List<String> importantKeywords = new ArrayList<>();

        for (String keyword : keywords) {
            keyword = keyword.replaceAll("[^a-z]", ""); // Remove punctuation
            if (keyword.length() > 3 && !stopWords.contains(keyword)) {
                importantKeywords.add(keyword);
            }
        }

        // If no important keywords, allow all results
        if (importantKeywords.isEmpty()) {
            return true;
        }

        // Check if recipe name contains at least one important keyword
        for (String keyword : importantKeywords) {
            if (recipeNameLower.contains(keyword)) {
                return true;
            }
        }

        return false; // No keywords matched
    }

    /**
     * V2 Hybrid search using Qdrant's native prefetch + RRF fusion.
     * Combines dense semantic search with sparse keyword matching.
     * 
     * @param ingredients    List of ingredients to search for
     * @param query          Natural language query
     * @param topK           Maximum number of results to return
     * @param scoreThreshold Minimum score threshold (0.0-1.0) to filter results
     * 
     *                       This solves the problems of:
     *                       1. Semantic drift from template strings
     *                       2. Unfair score comparison between different search
     *                       types
     *                       3. Missing exact keyword matches
     */
    public List<SearchResult> hybridSearch(List<String> ingredients, String query, int topK, float scoreThreshold) {
        List<SearchResult> results = new ArrayList<>();

        if (!initialized) {
            System.err.println("VectorSearchService not initialized");
            return results;
        }

        // Cache-Aside Pattern: Check cache first
        if (vectorCacheService.isAvailable()) {
            String cacheKey = vectorCacheService.buildSearchCacheKey(ingredients, query) + "|t:" + topK + "|s:"
                    + scoreThreshold;
            List<SearchResult> cached = vectorCacheService.getCachedSearchResults(cacheKey);
            if (cached != null) {
                System.out.println("[HybridSearch] Returning cached results");
                return cached;
            }
        }

        try {
            // Use Qdrant's query API with prefetch for hybrid search
            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME + "/points/query";

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode prefetch = objectMapper.createArrayNode();

            // Prefetch 1: Dense semantic search (if query provided)
            if (query != null && !query.isEmpty()) {
                float[] queryEmbedding = embeddingService.generateEmbedding(query);
                if (queryEmbedding != null) {
                    ObjectNode densePrefetch = objectMapper.createObjectNode();
                    ArrayNode denseQuery = objectMapper.createArrayNode();
                    for (float v : queryEmbedding) {
                        denseQuery.add(v);
                    }
                    densePrefetch.set("query", denseQuery);
                    densePrefetch.put("using", "dense");
                    densePrefetch.put("limit", 50); // Recall more for RRF fusion
                    prefetch.add(densePrefetch);
                }
            }

            // Prefetch 2: Sparse keyword search (if ingredients provided)
            if (ingredients != null && !ingredients.isEmpty()) {
                SparseEmbeddingService.SparseVector sparseVec = sparseEmbeddingService
                        .generateFromIngredients(ingredients);
                if (!sparseVec.isEmpty()) {
                    ObjectNode sparsePrefetch = objectMapper.createObjectNode();
                    ObjectNode sparseQuery = objectMapper.createObjectNode();

                    ArrayNode indicesArray = objectMapper.createArrayNode();
                    ArrayNode valuesArray = objectMapper.createArrayNode();
                    for (int idx : sparseVec.getIndices()) {
                        indicesArray.add(idx);
                    }
                    for (float val : sparseVec.getValues()) {
                        valuesArray.add(val);
                    }
                    sparseQuery.set("indices", indicesArray);
                    sparseQuery.set("values", valuesArray);

                    sparsePrefetch.set("query", sparseQuery);
                    sparsePrefetch.put("using", "sparse");
                    sparsePrefetch.put("limit", 50);
                    prefetch.add(sparsePrefetch);
                }
            }

            // If no prefetch queries, fall back to simple search
            if (prefetch.isEmpty()) {
                System.err.println("No valid queries for hybrid search");
                return results;
            }

            request.set("prefetch", prefetch);

            // RRF Fusion - combines results fairly regardless of score magnitude
            ObjectNode fusionQuery = objectMapper.createObjectNode();
            fusionQuery.put("fusion", "rrf");
            request.set("query", fusionQuery);

            // Request more results to allow for threshold filtering
            request.put("limit", Math.max(topK * 2, 50));
            request.put("with_payload", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            System.out.println("[HybridSearch] Request with threshold=" + scoreThreshold);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode pointsArray = root.path("result").path("points");

                // Handle both possible response formats
                if (!pointsArray.isArray()) {
                    pointsArray = root.path("result");
                }

                if (pointsArray.isArray()) {
                    for (JsonNode point : pointsArray) {
                        String recipeName = point.path("payload").path("recipe_name").asText();
                        float score = (float) point.path("score").asDouble();
                        String cuisineType = point.path("payload").path("cuisine_type").asText(null);

                        // Apply score threshold filter
                        if (score >= scoreThreshold) {
                            SearchResult result = new SearchResult(recipeName, score, cuisineType);
                            result.setMatchType("hybrid_rrf");
                            results.add(result);

                            // Stop once we have enough results
                            if (results.size() >= topK) {
                                break;
                            }
                        }
                    }
                }

                System.out.println("[HybridSearch] Found " + results.size() + " results with RRF fusion (threshold="
                        + scoreThreshold + ")");
            }
        } catch (Exception e) {
            System.err.println("Error in hybrid search: " + e.getMessage());
            e.printStackTrace();

            // Fallback to legacy hybrid search if new API fails
            System.out.println("[HybridSearch] Falling back to legacy search");
            return legacyHybridSearch(ingredients, query, topK, scoreThreshold);
        }

        // Cache the results before returning
        if (vectorCacheService.isAvailable() && !results.isEmpty()) {
            String cacheKey = vectorCacheService.buildSearchCacheKey(ingredients, query) + "|t:" + topK + "|s:"
                    + scoreThreshold;
            vectorCacheService.cacheSearchResults(cacheKey, results);
        }

        return results;
    }

    /**
     * Overloaded method for backward compatibility (default threshold = 0.0)
     */
    public List<SearchResult> hybridSearch(List<String> ingredients, String query, int topK) {
        return hybridSearch(ingredients, query, topK, 0.0f);
    }

    /**
     * Legacy hybrid search for backward compatibility.
     * Used as fallback if Qdrant version doesn't support prefetch/RRF.
     */
    private List<SearchResult> legacyHybridSearch(List<String> ingredients, String query, int topK,
            float scoreThreshold) {
        List<SearchResult> results = new ArrayList<>();
        Set<String> addedRecipes = new HashSet<>();

        // Get semantic search results
        if (query != null && !query.isEmpty()) {
            List<SearchResult> semanticResults = searchSimilar(query, topK * 2);
            for (SearchResult result : semanticResults) {
                if (!addedRecipes.contains(result.getRecipeName()) && result.getScore() >= scoreThreshold) {
                    result.setMatchType("semantic");
                    results.add(result);
                    addedRecipes.add(result.getRecipeName());
                }
            }
        }

        // Search by ingredients using sparse vector similarity
        if (ingredients != null && !ingredients.isEmpty()) {
            // Use ingredient names directly for better keyword matching
            String ingredientQuery = String.join(" ", ingredients);
            List<SearchResult> ingredientResults = searchSimilar(ingredientQuery, topK * 2);

            for (SearchResult result : ingredientResults) {
                if (!addedRecipes.contains(result.getRecipeName()) && result.getScore() >= scoreThreshold) {
                    result.setMatchType("ingredient");
                    results.add(result);
                    addedRecipes.add(result.getRecipeName());
                }
            }
        }

        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        if (results.size() > topK) {
            return results.subList(0, topK);
        }

        return results;
    }

    /**
     * Delete a recipe from the vector index.
     */
    public void deleteRecipe(String recipeName) {
        if (!initialized) {
            return;
        }

        try {
            long pointId = Math.abs(recipeName.hashCode());
            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME + "/points/delete";

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode points = objectMapper.createArrayNode();
            points.add(pointId);
            request.set("points", points);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            System.out.println("Deleted recipe from index: " + recipeName);
        } catch (Exception e) {
            System.err.println("Error deleting recipe from index: " + e.getMessage());
        }
    }

    /**
     * Get collection statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("initialized", initialized);
        stats.put("embeddingAvailable", embeddingService.isAvailable());
        stats.put("collectionName", COLLECTION_NAME);

        if (initialized) {
            try {
                String url = getBaseUrl() + "/collections/" + COLLECTION_NAME;
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode result = root.path("result");
                    stats.put("pointsCount", result.path("points_count").asLong());
                    stats.put("vectorsCount", result.path("vectors_count").asLong());
                    stats.put("status", result.path("status").asText());
                }
            } catch (Exception e) {
                stats.put("error", e.getMessage());
            }
        }

        return stats;
    }

    /**
     * Result from a semantic search.
     */
    public static class SearchResult {
        private String recipeName;
        private float score;
        private String cuisineType;
        private String matchType;

        public SearchResult(String recipeName, float score, String cuisineType) {
            this.recipeName = recipeName;
            this.score = score;
            this.cuisineType = cuisineType;
            this.matchType = "semantic";
        }

        // Getters and setters
        public String getRecipeName() {
            return recipeName;
        }

        public void setRecipeName(String recipeName) {
            this.recipeName = recipeName;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public String getCuisineType() {
            return cuisineType;
        }

        public void setCuisineType(String cuisineType) {
            this.cuisineType = cuisineType;
        }

        public String getMatchType() {
            return matchType;
        }

        public void setMatchType(String matchType) {
            this.matchType = matchType;
        }
    }
}
