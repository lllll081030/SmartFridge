package com.smartfridge.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for generating sparse vectors for BM25-style keyword matching.
 * Used in hybrid search to combine with dense semantic vectors.
 * 
 * Sparse vectors represent text as a set of (index, weight) pairs where:
 * - Index: Position in a vocabulary (using hash-based indexing)
 * - Weight: Term frequency or TF-IDF weight
 */
@Service
public class SparseEmbeddingService {

    // Use a large vocabulary space to minimize hash collisions
    private static final int VOCABULARY_SIZE = 100000;

    // Common stop words to filter out
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "need",
            "recipe", "dish", "food", "make", "cook", "cooking", "made");

    /**
     * Generate a sparse vector from a list of ingredients.
     * Each ingredient is tokenized and mapped to vocabulary indices.
     * 
     * @param ingredients List of ingredient names
     * @return SparseVector with indices and values
     */
    public SparseVector generateFromIngredients(List<String> ingredients) {
        Map<Integer, Float> sparseMap = new HashMap<>();

        for (String ingredient : ingredients) {
            List<String> tokens = tokenize(ingredient);
            for (String token : tokens) {
                int index = getVocabularyIndex(token);
                // Increment term frequency
                sparseMap.merge(index, 1.0f, Float::sum);
            }
        }

        return mapToSparseVector(sparseMap);
    }

    /**
     * Generate a sparse vector from recipe text (name + ingredients + cuisine).
     * Used for indexing recipes into Qdrant.
     * 
     * @param recipeName  Recipe name
     * @param ingredients List of ingredients
     * @param cuisineType Cuisine type (optional)
     * @return SparseVector for the recipe
     */
    public SparseVector generateFromRecipe(String recipeName, List<String> ingredients, String cuisineType) {
        Map<Integer, Float> sparseMap = new HashMap<>();

        // Add recipe name tokens with higher weight
        List<String> nameTokens = tokenize(recipeName);
        for (String token : nameTokens) {
            int index = getVocabularyIndex(token);
            sparseMap.merge(index, 2.0f, Float::sum); // Higher weight for recipe name
        }

        // Add ingredient tokens
        for (String ingredient : ingredients) {
            List<String> tokens = tokenize(ingredient);
            for (String token : tokens) {
                int index = getVocabularyIndex(token);
                sparseMap.merge(index, 1.0f, Float::sum);
            }
        }

        // Add cuisine type if present
        if (cuisineType != null && !cuisineType.isEmpty()) {
            List<String> cuisineTokens = tokenize(cuisineType);
            for (String token : cuisineTokens) {
                int index = getVocabularyIndex(token);
                sparseMap.merge(index, 1.5f, Float::sum); // Medium weight for cuisine
            }
        }

        return mapToSparseVector(sparseMap);
    }

    /**
     * Tokenize text into normalized tokens.
     * Handles basic normalization: lowercase, remove punctuation, filter stop
     * words.
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();

        // Lowercase and split by non-alphanumeric characters
        String normalized = text.toLowerCase().trim();
        String[] parts = normalized.split("[^a-z0-9\\u4e00-\\u9fff]+"); // Support Chinese characters

        for (String part : parts) {
            if (part.length() >= 2 && !STOP_WORDS.contains(part)) {
                tokens.add(part);
            }
        }

        return tokens;
    }

    /**
     * Map a token to a vocabulary index using consistent hashing.
     * Uses positive hash to ensure valid indices.
     */
    private int getVocabularyIndex(String token) {
        int hash = token.hashCode();
        // Ensure positive index within vocabulary size
        return Math.abs(hash % VOCABULARY_SIZE);
    }

    /**
     * Convert a sparse map to SparseVector format.
     */
    private SparseVector mapToSparseVector(Map<Integer, Float> sparseMap) {
        int[] indices = new int[sparseMap.size()];
        float[] values = new float[sparseMap.size()];

        int i = 0;
        for (Map.Entry<Integer, Float> entry : sparseMap.entrySet()) {
            indices[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }

        return new SparseVector(indices, values);
    }

    /**
     * Represents a sparse vector with indices and values.
     */
    public static class SparseVector {
        private final int[] indices;
        private final float[] values;

        public SparseVector(int[] indices, float[] values) {
            this.indices = indices;
            this.values = values;
        }

        public int[] getIndices() {
            return indices;
        }

        public float[] getValues() {
            return values;
        }

        public int size() {
            return indices.length;
        }

        public boolean isEmpty() {
            return indices.length == 0;
        }
    }
}
