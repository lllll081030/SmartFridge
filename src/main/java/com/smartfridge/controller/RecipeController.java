package com.smartfridge.controller;

import com.smartfridge.model.CuisineType;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.RecipeRequest;
import com.smartfridge.model.RecipeResponse;
import com.smartfridge.model.RecipeSimple;
import com.smartfridge.service.RecipeService;
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
}
