package com.smartfridge.service;

import com.smartfridge.dao.RecipeDao;
import com.smartfridge.dao.SupplyDao;
import com.smartfridge.model.MissingIngredientsResponse;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.SubstitutionSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service for ingredient substitution recommendations
 */
@Service
public class IngredientSubstitutionService {

    @Autowired
    private RecipeDao recipeDao;

    @Autowired
    private SupplyDao supplyDao;

    @Autowired
    private IngredientResolver ingredientResolver;

    @Value("${ai.service.url:http://localhost:5001}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Find missing ingredients for a recipe by comparing recipe requirements with
     * fridge supplies
     * Resolves ingredient aliases before determining missing items
     */
    public MissingIngredientsResponse findMissingIngredients(String recipeName) {
        System.out.println("[DEBUG] Finding missing ingredients for recipe: " + recipeName);

        // Get recipe details
        RecipeDetails recipe = recipeDao.getRecipeDetails(recipeName);
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe not found: " + recipeName);
        }

        // Get fridge supplies and resolve to canonical names
        Set<String> fridgeSupplies = supplyDao.getSupplies();
        System.out.println("[DEBUG] Fridge supplies: " + fridgeSupplies);

        Set<String> resolvedSupplies = ingredientResolver.resolveToSet(fridgeSupplies);
        resolvedSupplies.addAll(fridgeSupplies); // Include original names too
        System.out.println("[DEBUG] Resolved supplies: " + resolvedSupplies);

        // Get ONLY non-seasoning ingredients from recipe
        List<String> nonSeasoningIngredients = recipeDao.getNonSeasoningIngredients(recipeName);
        System.out.println("[DEBUG] Non-seasoning ingredients needed: " + nonSeasoningIngredients);

        List<String> missingIngredients = new ArrayList<>();

        for (String ingredient : nonSeasoningIngredients) {
            String resolvedIngredient = ingredientResolver.resolve(ingredient);
            if (!resolvedSupplies.contains(ingredient) && !resolvedSupplies.contains(resolvedIngredient)) {
                missingIngredients.add(ingredient);
                System.out.println("[DEBUG] Missing: " + ingredient + " (resolved: " + resolvedIngredient + ")");
            } else {
                System.out.println("[DEBUG] Found: " + ingredient + " in fridge");
            }
        }

        // Calculate coverage percentage
        int totalRequired = nonSeasoningIngredients.size();
        double coveragePercent = totalRequired > 0
                ? ((totalRequired - missingIngredients.size()) * 100.0 / totalRequired)
                : 100.0;

        System.out.println("[DEBUG] Total non-seasonings: " + totalRequired + ", Missing: " + missingIngredients.size()
                + ", Coverage: " + coveragePercent + "%");
        return new MissingIngredientsResponse(recipeName, missingIngredients, totalRequired, coveragePercent);
    }

    /**
     * Get AI-powered substitution suggestions for missing ingredients in a recipe
     */
    public Map<String, List<SubstitutionSuggestion>> getSubstitutions(String recipeName) {
        // Find missing ingredients
        MissingIngredientsResponse missingInfo = findMissingIngredients(recipeName);
        List<String> missingIngredients = missingInfo.getMissingIngredients();

        if (missingIngredients.isEmpty()) {
            System.out.println("[DEBUG] No missing ingredients for " + recipeName + ", returning empty map");
            return Collections.emptyMap();
        }

        System.out.println("[DEBUG] Getting substitutions for " + missingIngredients.size() + " missing ingredients: "
                + missingIngredients);

        // Get recipe details for context
        RecipeDetails recipe = recipeDao.getRecipeDetails(recipeName);
        Set<String> fridgeSupplies = supplyDao.getSupplies();

        // Map to store substitutions for each missing ingredient
        Map<String, List<SubstitutionSuggestion>> allSubstitutions = new LinkedHashMap<>();

        // Request substitutions from AI service for each missing ingredient
        for (String missingIngredient : missingIngredients) {
            System.out.println("[DEBUG] Requesting AI substitutions for: " + missingIngredient);
            try {
                List<SubstitutionSuggestion> suggestions = requestSubstitutionsFromAI(
                        missingIngredient,
                        recipe.getCuisineType().name(),
                        recipe.getIngredients(),
                        fridgeSupplies);
                System.out.println("[DEBUG] Got " + suggestions.size() + " suggestions for " + missingIngredient);
                allSubstitutions.put(missingIngredient, suggestions);
            } catch (Exception e) {
                System.err.println(
                        "[ERROR] Failed to get substitutions for " + missingIngredient + ": " + e.getMessage());
                e.printStackTrace();
                // Add empty list on failure
                allSubstitutions.put(missingIngredient, Collections.emptyList());
            }
        }

        return allSubstitutions;
    }

    /**
     * Request substitution suggestions from Python AI service
     */
    private List<SubstitutionSuggestion> requestSubstitutionsFromAI(
            String ingredient,
            String cuisineType,
            List<String> recipeIngredients,
            Set<String> fridgeSupplies) {

        try {
            System.out.println("[DEBUG] Calling AI service at: " + aiServiceUrl);

            // Build request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ingredient", ingredient);
            requestBody.put("cuisine", cuisineType);
            requestBody.put("recipeIngredients", recipeIngredients);
            requestBody.put("fridgeSupplies", new ArrayList<>(fridgeSupplies));

            System.out.println("[DEBUG] Request payload: " + requestBody);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Call AI service
            String url = aiServiceUrl + "/ai/substitutions";
            System.out.println("[DEBUG] Sending POST request to: " + url);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            System.out.println("[DEBUG] AI service response status: " + response.getStatusCode());
            System.out.println("[DEBUG] AI service response body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseSubstitutionsResponse(response.getBody(), ingredient, fridgeSupplies);
            } else {
                System.err.println("[ERROR] AI service returned non-success status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error calling AI service at " + aiServiceUrl + ": " + e.getMessage());
            System.err.println("[ERROR] Exception type: " + e.getClass().getName());
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    /**
     * Parse AI service response into SubstitutionSuggestion objects
     */
    @SuppressWarnings("unchecked")
    private List<SubstitutionSuggestion> parseSubstitutionsResponse(
            Map<String, Object> responseBody,
            String originalIngredient,
            Set<String> fridgeSupplies) {

        List<SubstitutionSuggestion> suggestions = new ArrayList<>();

        Object substitutesObj = responseBody.get("substitutes");
        if (substitutesObj instanceof List) {
            List<Map<String, Object>> substitutes = (List<Map<String, Object>>) substitutesObj;

            for (Map<String, Object> sub : substitutes) {
                String substitute = (String) sub.get("ingredient");
                Object confidenceObj = sub.get("confidence");
                double confidence = confidenceObj instanceof Number ? ((Number) confidenceObj).doubleValue() : 0.0;
                String reasoning = (String) sub.getOrDefault("reasoning", "");

                // Check if substitute is in fridge
                boolean actuallyInFridge = fridgeSupplies.contains(substitute) ||
                        fridgeSupplies.contains(ingredientResolver.resolve(substitute));

                suggestions.add(new SubstitutionSuggestion(
                        originalIngredient,
                        substitute,
                        actuallyInFridge,
                        confidence,
                        reasoning));
            }
        }

        return suggestions;
    }
}
