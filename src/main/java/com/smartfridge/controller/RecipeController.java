package com.smartfridge.controller;

import com.smartfridge.model.CuisineType;
import com.smartfridge.model.MissingIngredientsResponse;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.RecipeRequest;
import com.smartfridge.model.RecipeResponse;
import com.smartfridge.model.RecipeSimple;
import com.smartfridge.model.SubstitutionSuggestion;
import com.smartfridge.service.RecipeService;
import com.smartfridge.service.IngredientResolver;
import com.smartfridge.service.IngredientSubstitutionService;
import com.smartfridge.service.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend to call API
public class RecipeController {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientResolver ingredientResolver;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private IngredientSubstitutionService substitutionService;

    /**
     * Generate cookable recipes from fridge supplies
     * 
     * POST /api/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateRecipes(@RequestBody RecipeRequest request) {
        // Input validation
        String validationError = validateRequest(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        List<String> cookableRecipes = recipeService.findCookableRecipes(
                request.getRecipes(),
                request.getIngredients(),
                request.getSupplies());

        RecipeResponse response = new RecipeResponse(cookableRecipes);
        return ResponseEntity.ok(response);
    }

    /**
     * Generate cookable recipes using stored fridge supplies
     * 
     * GET /api/generate
     */
    @GetMapping("/generate")
    public ResponseEntity<?> generateFromFridge() {
        List<String> cookableRecipes = recipeService.findCookableRecipesFromFridge();
        return ResponseEntity.ok(new RecipeResponse(cookableRecipes));
    }

    /**
     * Validate request input
     */
    private String validateRequest(RecipeRequest request) {
        if (request == null) {
            return "Request body is required";
        }
        if (request.getRecipes() == null || request.getRecipes().isEmpty()) {
            return "Recipes list is required and cannot be empty";
        }
        if (request.getIngredients() == null || request.getIngredients().isEmpty()) {
            return "Ingredients list is required and cannot be empty";
        }
        if (request.getSupplies() == null || request.getSupplies().isEmpty()) {
            return "Supplies list is required and cannot be empty";
        }
        if (request.getRecipes().size() != request.getIngredients().size()) {
            return "Recipes and ingredients lists must have the same size";
        }
        for (int i = 0; i < request.getIngredients().size(); i++) {
            List<String> ingredientList = request.getIngredients().get(i);
            if (ingredientList == null || ingredientList.isEmpty()) {
                return "Ingredient list for recipe '" + request.getRecipes().get(i) + "' cannot be empty";
            }
        }
        return null;
    }

    /**
     * Semantic search for recipes
     * 
     * GET /api/recipes/search?query=...&limit=10
     */
    @GetMapping("/recipes/search")
    public ResponseEntity<?> searchRecipes(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query is required"));
        }

        if (!vectorSearchService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "results", List.of(),
                    "warning", "Semantic search is not available. Make sure Qdrant and Ollama are running."));
        }

        List<VectorSearchService.SearchResult> results = vectorSearchService.searchSimilar(query.trim(), limit);
        return ResponseEntity.ok(Map.of("results", results));
    }

    /**
     * Hybrid search combining exact ingredient matching and semantic search
     * 
     * POST /api/recipes/hybrid-search
     * Request body: { "ingredients": [...], "query": "...", "limit": 10 }
     */
    @PostMapping("/recipes/hybrid-search")
    public ResponseEntity<?> hybridSearch(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> ingredients = (List<String>) request.get("ingredients");
        String query = (String) request.get("query");
        Integer limit = (Integer) request.getOrDefault("limit", 10);

        if ((ingredients == null || ingredients.isEmpty()) && (query == null || query.trim().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Either ingredients or query is required"));
        }

        if (!vectorSearchService.isAvailable()) {
            // Fall back to exact matching only
            List<String> cookable = recipeService.findCookableRecipesFromFridge();
            return ResponseEntity.ok(Map.of(
                    "results",
                    cookable.stream().map(name -> Map.of("recipeName", name, "matchType", "exact"))
                            .collect(Collectors.toList()),
                    "warning", "Semantic search unavailable, showing exact matches only"));
        }

        List<VectorSearchService.SearchResult> results = vectorSearchService.hybridSearch(ingredients, query, limit);
        return ResponseEntity.ok(Map.of("results", results));
    }

    /**
     * Get detailed recipe information by name
     * 
     * GET /api/recipes/{name}
     */
    @GetMapping("/recipes/{name}")
    public ResponseEntity<?> getRecipeDetails(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipe name is required"));
        }

        RecipeDetails details = recipeService.getRecipeDetails(name.trim());

        if (details == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Get all recipes grouped by cuisine type
     * 
     * GET /api/recipes
     * 
     * Response:
     * {
     * "AMERICAN": [{"name": "sandwich", "ingredients": ["bread", "ham"]}, ...],
     * "ITALIAN": [...],
     * ...
     * }
     */
    @GetMapping("/recipes")
    public ResponseEntity<?> getAllRecipesByCuisine() {
        Map<String, List<RecipeSimple>> recipesByCuisine = recipeService.getAllRecipesByCuisine();
        return ResponseEntity.ok(recipesByCuisine);
    }

    /**
     * Add a new recipe
     * 
     * POST /api/recipes
     * Request body: { "name": "...", "ingredients": [...], "seasonings": [...],
     * "cuisineType": "...",
     * "instructions": "..." }
     */
    @PostMapping("/recipes")
    public ResponseEntity<?> addRecipe(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        @SuppressWarnings("unchecked")
        List<String> ingredients = (List<String>) request.get("ingredients");
        @SuppressWarnings("unchecked")
        List<String> seasonings = (List<String>) request.get("seasonings");
        String cuisineType = (String) request.get("cuisineType");
        String instructions = (String) request.get("instructions");
        String imageUrl = (String) request.get("imageUrl");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipe name is required"));
        }
        if (ingredients == null || ingredients.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingredients list is required"));
        }

        try {
            if (seasonings != null && !seasonings.isEmpty()) {
                // Use new method with separate seasonings
                recipeService.addRecipeWithSeasonings(name.trim(), ingredients, seasonings, cuisineType, instructions,
                        imageUrl);
            } else {
                // Use original method (legacy support)
                recipeService.addRecipe(name.trim(), ingredients, cuisineType, instructions, imageUrl);
            }

            // Index the new recipe for semantic search (async, non-blocking)
            try {
                vectorSearchService.indexRecipe(name.trim());
            } catch (Exception e) {
                System.err.println("Warning: Failed to index recipe for semantic search: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of("message", "Recipe added successfully", "name", name));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add recipe: " + e.getMessage()));
        }
    }

    /**
     * Delete a recipe
     * 
     * DELETE /api/recipes/{name}
     */
    @DeleteMapping("/recipes/{name}")
    public ResponseEntity<?> deleteRecipe(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipe name is required"));
        }

        try {
            recipeService.deleteRecipe(name.trim());

            // Remove from vector index
            try {
                vectorSearchService.deleteRecipe(name.trim());
            } catch (Exception e) {
                System.err.println("Warning: Failed to remove recipe from search index: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of("message", "Recipe deleted successfully", "name", name));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete recipe: " + e.getMessage()));
        }
    }

    /**
     * Get all cuisine types
     * 
     * GET /api/cuisines
     */
    @GetMapping("/cuisines")
    public ResponseEntity<?> getCuisineTypes() {
        List<Map<String, String>> cuisines = Arrays.stream(CuisineType.values())
                .map(c -> Map.of("name", c.name(), "displayName", c.getDisplayName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(cuisines);
    }

    // ==================== Fridge Endpoints ====================

    /**
     * Get current fridge supplies with quantities and sort order
     * 
     * GET /api/fridge
     */
    @GetMapping("/fridge")
    public ResponseEntity<?> getFridgeSupplies() {
        List<Map<String, Object>> supplies = recipeService.getFridgeSuppliesWithDetails();
        return ResponseEntity.ok(Map.of("supplies", supplies));
    }

    /**
     * Update fridge supplies order
     * 
     * PUT /api/fridge/order
     * Request body: { "items": ["item1", "item2", ...] }
     */
    @PutMapping("/fridge/order")
    public ResponseEntity<?> updateFridgeOrder(@RequestBody Map<String, List<String>> request) {
        List<String> items = request.get("items");
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "items list is required"));
        }
        recipeService.updateFridgeSuppliesOrder(items);
        return ResponseEntity.ok(Map.of("message", "Order updated successfully"));
    }

    /**
     * Update fridge supplies
     * 
     * PUT /api/fridge
     * Request body: { "supplies": ["bread", "ham", "cheese"] }
     */
    @PutMapping("/fridge")
    public ResponseEntity<?> updateFridgeSupplies(@RequestBody Map<String, List<String>> request) {
        List<String> supplies = request.get("supplies");
        if (supplies == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "supplies list is required"));
        }
        recipeService.updateFridgeSupplies(supplies);
        return ResponseEntity.ok(Map.of("message", "Fridge updated successfully", "supplies", supplies));
    }

    /**
     * Add item to fridge with optional count
     * 
     * POST /api/fridge/{item}?count=N
     */
    @PostMapping("/fridge/{item}")
    public ResponseEntity<?> addToFridge(@PathVariable String item,
            @RequestParam(defaultValue = "1") int count) {
        if (item == null || item.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Item name is required"));
        }
        if (count < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Count must be at least 1"));
        }
        recipeService.addToFridge(item.trim(), count);
        return ResponseEntity.ok(Map.of("message", "Added " + count + " " + item + " to fridge"));
    }

    /**
     * Update item count in fridge
     * 
     * PUT /api/fridge/{item}
     * Request body: { "count": N }
     */
    @PutMapping("/fridge/{item}")
    public ResponseEntity<?> updateFridgeItemCount(@PathVariable String item,
            @RequestBody Map<String, Integer> request) {
        if (item == null || item.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Item name is required"));
        }
        Integer count = request.get("count");
        if (count == null || count < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Count must be at least 1"));
        }
        recipeService.updateFridgeItemCount(item.trim(), count);
        return ResponseEntity.ok(Map.of("message", "Updated " + item + " count to " + count));
    }

    /**
     * Remove item from fridge
     * 
     * DELETE /api/fridge/{item}
     */
    @DeleteMapping("/fridge/{item}")
    public ResponseEntity<?> removeFromFridge(@PathVariable String item) {
        if (item == null || item.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Item name is required"));
        }
        recipeService.removeFromFridge(item.trim());
        return ResponseEntity.ok(Map.of("message", "Removed " + item + " from fridge"));
    }

    // ==================== Ingredient Alias Endpoints ====================

    /**
     * Get aliases for an ingredient
     * 
     * GET /api/ingredients/{name}/aliases
     */
    @GetMapping("/ingredients/{name}/aliases")
    public ResponseEntity<?> getIngredientAliases(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingredient name is required"));
        }

        List<String> aliases = ingredientResolver.getAliases(name.trim());
        String canonical = ingredientResolver.resolve(name.trim());

        return ResponseEntity.ok(Map.of(
                "ingredient", name,
                "canonical", canonical,
                "aliases", aliases));
    }

    /**
     * Add an alias for an ingredient
     * 
     * POST /api/ingredients/{canonical}/aliases
     * Request body: { "alias": "..." }
     */
    @PostMapping("/ingredients/{canonical}/aliases")
    public ResponseEntity<?> addIngredientAlias(
            @PathVariable String canonical,
            @RequestBody Map<String, String> request) {

        String alias = request.get("alias");
        if (canonical == null || canonical.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Canonical name is required"));
        }
        if (alias == null || alias.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Alias is required"));
        }

        ingredientResolver.addAlias(canonical.trim(), alias.trim());
        return ResponseEntity.ok(Map.of(
                "message", "Alias added successfully",
                "canonical", canonical,
                "alias", alias));
    }

    /**
     * Generate AI-powered aliases for an ingredient
     * 
     * POST /api/ingredients/{name}/generate-aliases
     */
    @PostMapping("/ingredients/{name}/generate-aliases")
    public ResponseEntity<?> generateIngredientAliases(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingredient name is required"));
        }

        List<String> generatedAliases = ingredientResolver.generateAliasesWithAI(name.trim());

        return ResponseEntity.ok(Map.of(
                "ingredient", name,
                "generated", generatedAliases,
                "count", generatedAliases.size()));
    }

    /**
     * Resolve an ingredient to its canonical form
     * 
     * GET /api/ingredients/{name}/resolve
     */
    @GetMapping("/ingredients/{name}/resolve")
    public ResponseEntity<?> resolveIngredient(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ingredient name is required"));
        }

        String canonical = ingredientResolver.resolve(name.trim());
        boolean isResolved = !canonical.equals(name.trim());

        return ResponseEntity.ok(Map.of(
                "original", name,
                "canonical", canonical,
                "resolved", isResolved));
    }

    /**
     * Seed common ingredient aliases
     * 
     * POST /api/ingredients/seed-aliases
     */
    @PostMapping("/ingredients/seed-aliases")
    public ResponseEntity<?> seedIngredientAliases() {
        ingredientResolver.seedCommonAliases();
        return ResponseEntity.ok(Map.of("message", "Seeded common ingredient aliases"));
    }

    // ==================== Vector Search Admin Endpoints ====================

    /**
     * Index all recipes for semantic search
     * 
     * POST /api/search/index-all
     */
    @PostMapping("/search/index-all")
    public ResponseEntity<?> indexAllRecipes() {
        if (!vectorSearchService.isAvailable()) {
            return ResponseEntity.ok(Map.of(
                    "error", "Vector search is not available. Make sure Qdrant and Ollama are running."));
        }

        int count = vectorSearchService.indexAllRecipes();
        return ResponseEntity.ok(Map.of(
                "message", "Indexed recipes for semantic search",
                "count", count));
    }

    /**
     * Get vector search statistics
     * 
     * GET /api/search/stats
     */
    @GetMapping("/search/stats")
    public ResponseEntity<?> getSearchStats() {
        return ResponseEntity.ok(vectorSearchService.getStats());
    }

    // ==================== Substitution Endpoints ====================

    /**
     * Get missing ingredients for a recipe
     * 
     * GET /api/recipes/{name}/missing
     */
    @GetMapping("/recipes/{name}/missing")
    public ResponseEntity<?> getMissingIngredients(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipe name is required"));
        }

        try {
            MissingIngredientsResponse response = substitutionService.findMissingIngredients(name.trim());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to analyze recipe: " + e.getMessage()));
        }
    }

    /**
     * Get AI substitution suggestions for missing ingredients in a recipe
     * 
     * GET /api/recipes/{name}/substitutions
     */
    @GetMapping("/recipes/{name}/substitutions")
    public ResponseEntity<?> getSubstitutionSuggestions(@PathVariable String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Recipe name is required"));
        }

        try {
            Map<String, List<SubstitutionSuggestion>> substitutions = substitutionService
                    .getSubstitutions(name.trim());
            return ResponseEntity.ok(Map.of(
                    "recipeName", name,
                    "substitutions", substitutions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get substitutions: " + e.getMessage()));
        }
    }

    /**
     * Get recipes that are almost cookable (missing only a few ingredients)
     * 
     * GET /api/recipes/almost-cookable?maxMissing=2
     */
    @GetMapping("/recipes/almost-cookable")
    public ResponseEntity<?> getAlmostCookableRecipes(
            @RequestParam(defaultValue = "2") int maxMissing) {

        if (maxMissing < 1 || maxMissing > 5) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "maxMissing must be between 1 and 5"));
        }

        Map<String, List<String>> almostCookable = recipeService.findAlmostCookableRecipes(maxMissing);
        return ResponseEntity.ok(Map.of(
                "recipes", almostCookable,
                "count", almostCookable.size(),
                "maxMissing", maxMissing));
    }
}
