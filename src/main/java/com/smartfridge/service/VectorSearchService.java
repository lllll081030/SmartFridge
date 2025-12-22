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

    private static final String COLLECTION_NAME = "recipes";
    private static final int VECTOR_SIZE = 768; // nomic-embed-text dimension

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6333}")
    private int qdrantPort;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RecipeDao recipeDao;

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

    private void createCollection() {
        String url = getBaseUrl() + "/collections/" + COLLECTION_NAME;

        ObjectNode config = objectMapper.createObjectNode();
        ObjectNode vectors = objectMapper.createObjectNode();
        vectors.put("size", VECTOR_SIZE);
        vectors.put("distance", "Cosine");
        config.set("vectors", vectors);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(config.toString(), headers);

        restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
        System.out.println("Created collection '" + COLLECTION_NAME + "' with dimension " + VECTOR_SIZE);
    }

    /**
     * Index a single recipe into Qdrant.
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

            String recipeText = embeddingService.createRecipeText(
                    details.getName(),
                    details.getIngredients(),
                    details.getCuisineType() != null ? details.getCuisineType().name() : null,
                    details.getInstructions());

            float[] embedding = embeddingService.generateEmbedding(recipeText);
            if (embedding == null) {
                System.err.println("Failed to generate embedding for: " + recipeName);
                return;
            }

            // Use recipe name hash as point ID
            long pointId = Math.abs(recipeName.hashCode());

            // Build upsert request
            String url = getBaseUrl() + "/collections/" + COLLECTION_NAME + "/points";

            ObjectNode request = objectMapper.createObjectNode();
            ArrayNode points = objectMapper.createArrayNode();

            ObjectNode point = objectMapper.createObjectNode();
            point.put("id", pointId);

            ArrayNode vector = objectMapper.createArrayNode();
            for (float v : embedding) {
                vector.add(v);
            }
            point.set("vector", vector);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("recipe_name", recipeName);
            payload.put("cuisine_type", details.getCuisineType() != null ? details.getCuisineType().name() : "OTHER");
            payload.put("model_version", embeddingService.getModelVersion());
            point.set("payload", payload);

            points.add(point);
            request.set("points", points);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            System.out.println("Indexed recipe: " + recipeName);
        } catch (Exception e) {
            System.err.println("Error indexing recipe: " + e.getMessage());
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
     * Hybrid search combining exact ingredient matching with semantic search.
     * Returns recipes that either match ingredients OR are semantically similar.
     */
    public List<SearchResult> hybridSearch(List<String> ingredients, String query, int topK) {
        List<SearchResult> results = new ArrayList<>();
        Set<String> addedRecipes = new HashSet<>();

        // First, get semantic search results
        if (query != null && !query.isEmpty()) {
            List<SearchResult> semanticResults = searchSimilar(query, topK);
            for (SearchResult result : semanticResults) {
                if (!addedRecipes.contains(result.getRecipeName())) {
                    result.setMatchType("semantic");
                    results.add(result);
                    addedRecipes.add(result.getRecipeName());
                }
            }
        }

        // If we have ingredients, also search by ingredient text
        if (ingredients != null && !ingredients.isEmpty()) {
            String ingredientQuery = "Recipe with ingredients: " + String.join(", ", ingredients);
            List<SearchResult> ingredientResults = searchSimilar(ingredientQuery, topK);

            for (SearchResult result : ingredientResults) {
                if (!addedRecipes.contains(result.getRecipeName())) {
                    result.setMatchType("ingredient_semantic");
                    results.add(result);
                    addedRecipes.add(result.getRecipeName());
                }
            }
        }

        // Sort by score descending
        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        // Limit to topK
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
