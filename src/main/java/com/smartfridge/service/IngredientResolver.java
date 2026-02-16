package com.smartfridge.service;

import com.smartfridge.dao.IngredientAliasDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for resolving ingredient names to their canonical forms.
 * Supports AI-powered alias generation using OpenAI API.
 */
@Service
public class IngredientResolver {

    @Autowired
    private IngredientAliasDao aliasDao;

    @Value("${openai.api-key}")
    private String openaiApiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${openai.chat-model:gpt-4o-mini}")
    private String chatModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Resolve an ingredient name to its canonical form.
     * Falls back to original name if no alias found.
     */
    public String resolve(String ingredient) {
        if (ingredient == null || ingredient.trim().isEmpty()) {
            return ingredient;
        }
        return aliasDao.resolveToCanonical(ingredient.trim());
    }

    /**
     * Resolve multiple ingredients to their canonical forms.
     */
    public List<String> resolveAll(List<String> ingredients) {
        if (ingredients == null) {
            return Collections.emptyList();
        }
        return ingredients.stream()
                .map(this::resolve)
                .collect(Collectors.toList());
    }

    /**
     * Resolve ingredients and return unique canonical names.
     */
    public Set<String> resolveToSet(Collection<String> ingredients) {
        if (ingredients == null) {
            return Collections.emptySet();
        }
        return ingredients.stream()
                .map(this::resolve)
                .collect(Collectors.toSet());
    }

    /**
     * Get all known aliases for an ingredient.
     */
    public List<String> getAliases(String canonicalName) {
        return aliasDao.getAliasesForCanonical(canonicalName);
    }

    /**
     * Manually add an alias mapping.
     */
    public void addAlias(String canonicalName, String alias) {
        aliasDao.addAlias(canonicalName, alias, 1.0, "manual");
    }

    /**
     * Generate aliases for an ingredient using OpenAI API.
     * Returns the generated aliases.
     */
    public List<String> generateAliasesWithAI(String ingredient) {
        List<String> generatedAliases = new ArrayList<>();

        String prompt = String.format(
                """
                        You are a culinary expert. For the ingredient "%s", provide common alternative names, varieties, and related terms that could be used interchangeably in recipes.

                        Rules:
                        - Include common abbreviations
                        - Include regional name variations
                        - Include variety names (e.g., roma tomato, cherry tomato for tomato)
                        - Include singular/plural forms
                        - Do NOT include completely different ingredients

                        Return ONLY a JSON array of strings, nothing else. Example:
                        ["cherry tomato", "roma tomato", "tomatoes", "vine tomato", "plum tomato"]

                        Ingredient: %s
                        """,
                ingredient, ingredient);

        try {
            // Build OpenAI Chat Completions request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", chatModel);
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("response_format", Map.of("type", "json_object"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    openaiBaseUrl + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String responseText = root.path("choices").get(0).path("message").path("content").asText();

                // Parse the JSON array from response
                JsonNode parsed = objectMapper.readTree(responseText);
                // Handle both plain array and object with array field
                JsonNode aliasArray = parsed.isArray() ? parsed : parsed.path("aliases");
                if (!aliasArray.isArray()) {
                    // Try first array field in the object
                    var fields = parsed.fields();
                    while (fields.hasNext()) {
                        JsonNode val = fields.next().getValue();
                        if (val.isArray()) {
                            aliasArray = val;
                            break;
                        }
                    }
                }

                if (aliasArray.isArray()) {
                    for (JsonNode alias : aliasArray) {
                        String aliasText = alias.asText().trim().toLowerCase();
                        if (!aliasText.isEmpty() && !aliasText.equals(ingredient.toLowerCase())) {
                            generatedAliases.add(aliasText);
                        }
                    }
                }

                // Save generated aliases to database
                String canonical = ingredient.toLowerCase();
                for (String alias : generatedAliases) {
                    aliasDao.addAlias(canonical, alias, 0.8, "ai_generated");
                }

                // Also add the canonical name as its own alias (for lookup)
                aliasDao.addAlias(canonical, canonical, 1.0, "ai_generated");

                System.out.println("Generated " + generatedAliases.size() + " aliases for: " + ingredient);
            }
        } catch (Exception e) {
            System.err.println("Error generating aliases with AI: " + e.getMessage());
            e.printStackTrace();
        }

        return generatedAliases;
    }

    /**
     * Check if OpenAI API is available for AI alias generation.
     */
    public boolean isAIAvailable() {
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

    /**
     * Seed common ingredient aliases (for bootstrapping).
     */
    public void seedCommonAliases() {
        // Common tomato variants
        addAliasGroup("tomato", Arrays.asList(
                "tomatoes", "roma tomato", "cherry tomato", "plum tomato",
                "grape tomato", "beefsteak tomato", "vine tomato", "heirloom tomato"));

        // Common onion variants
        addAliasGroup("onion", Arrays.asList(
                "onions", "yellow onion", "white onion", "red onion",
                "sweet onion", "vidalia onion", "shallot", "spring onion"));

        // Common pepper variants
        addAliasGroup("bell pepper", Arrays.asList(
                "bell peppers", "red bell pepper", "green bell pepper",
                "yellow bell pepper", "capsicum", "sweet pepper"));

        // Common potato variants
        addAliasGroup("potato", Arrays.asList(
                "potatoes", "russet potato", "yukon gold", "red potato",
                "fingerling potato", "baby potato", "new potato"));

        // Common chicken variants
        addAliasGroup("chicken", Arrays.asList(
                "chicken breast", "chicken thigh", "chicken leg", "chicken wing",
                "whole chicken", "boneless chicken", "skinless chicken"));

        // Common beef variants
        addAliasGroup("beef", Arrays.asList(
                "ground beef", "beef steak", "beef chuck", "beef sirloin",
                "stewing beef", "beef brisket", "beef tenderloin"));

        // Common garlic variants
        addAliasGroup("garlic", Arrays.asList(
                "garlic clove", "garlic cloves", "minced garlic", "crushed garlic",
                "fresh garlic", "roasted garlic"));

        System.out.println("Seeded common ingredient aliases");
    }

    private void addAliasGroup(String canonical, List<String> aliases) {
        // Add canonical as its own alias
        aliasDao.addAlias(canonical, canonical, 1.0, "seed");
        // Add all aliases
        for (String alias : aliases) {
            aliasDao.addAlias(canonical, alias, 0.9, "seed");
        }
    }
}
