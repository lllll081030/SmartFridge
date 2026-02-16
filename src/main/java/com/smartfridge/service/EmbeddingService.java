package com.smartfridge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Service for generating text embeddings using OpenAI API.
 * Used for semantic search of recipes.
 */
@Service
public class EmbeddingService {

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate an embedding vector for the given text.
     * Returns null if embedding generation fails.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    openaiBaseUrl + "/embeddings",
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode embeddingNode = root.path("data").get(0).path("embedding");

                if (embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }
        } catch (Exception e) {
            System.err.println("Error generating embedding: " + e.getMessage());
        }

        return null;
    }

    /**
     * Generate embeddings for multiple texts.
     * Returns a map of text -> embedding.
     */
    public Map<String, float[]> generateEmbeddings(List<String> texts) {
        Map<String, float[]> embeddings = new HashMap<>();

        for (String text : texts) {
            float[] embedding = generateEmbedding(text);
            if (embedding != null) {
                embeddings.put(text, embedding);
            }
        }

        return embeddings;
    }

    /**
     * Create a searchable text representation of a recipe.
     * Combines name, ingredients, and cuisine for better semantic matching.
     */
    public String createRecipeText(String name, List<String> ingredients, String cuisineType, String instructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Recipe: ").append(name).append(". ");

        if (cuisineType != null && !cuisineType.isEmpty()) {
            sb.append("Cuisine: ").append(cuisineType).append(". ");
        }

        if (ingredients != null && !ingredients.isEmpty()) {
            sb.append("Ingredients: ").append(String.join(", ", ingredients)).append(". ");
        }

        if (instructions != null && !instructions.isEmpty()) {
            // Truncate long instructions
            String truncatedInstructions = instructions.length() > 500
                    ? instructions.substring(0, 500) + "..."
                    : instructions;
            sb.append("Instructions: ").append(truncatedInstructions);
        }

        return sb.toString();
    }

    /**
     * Get the dimension of embeddings from the current model.
     * Useful for initializing vector database collections.
     */
    public int getEmbeddingDimension() {
        // text-embedding-3-small produces 1536-dimensional embeddings
        return 1536;
    }

    /**
     * Get the current embedding model name.
     */
    public String getModelVersion() {
        return embeddingModel;
    }

    /**
     * Check if OpenAI API is available for embedding generation.
     */
    public boolean isAvailable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(openaiApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    openaiBaseUrl + "/models",
                    HttpMethod.GET,
                    entity,
                    String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
