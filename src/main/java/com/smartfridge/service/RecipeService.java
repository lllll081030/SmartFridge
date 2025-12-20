package com.smartfridge.service;

import com.smartfridge.dao.RecipeDao;
import com.smartfridge.dao.SupplyDao;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.RecipeSimple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RecipeService {

    @Autowired
    private RecipeDao recipeDao;

    @Autowired
    private SupplyDao supplyDao;

    /**
     * Find all cookable recipes using Kahn's algorithm (topological sorting)
     */
    public List<String> findCookableRecipes(List<String> recipes,
            List<List<String>> ingredients,
            List<String> supplies) {

        // Handle duplicate recipe names - merge ingredients
        Map<String, Set<String>> mergedRecipes = new LinkedHashMap<>();
        for (int i = 0; i < recipes.size(); i++) {
            String recipeName = recipes.get(i);
            Set<String> recipeIngredients = mergedRecipes.computeIfAbsent(recipeName, k -> new HashSet<>());
            recipeIngredients.addAll(ingredients.get(i));
        }

        // Build dependency graph and inDegree list
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : mergedRecipes.entrySet()) {
            String recipeName = entry.getKey();
            Set<String> recipeIngredients = entry.getValue();

            for (String ingredient : recipeIngredients) {
                graph.computeIfAbsent(ingredient, k -> new ArrayList<>()).add(recipeName);
                inDegree.merge(recipeName, 1, Integer::sum);
            }
        }

        return kahnAlgorithm(graph, inDegree, supplies);
    }

    /**
     * Find cookable recipes from stored fridge supplies and database recipes
     */
    public List<String> findCookableRecipesFromFridge() {
        // Load recipe graph from database
        Map<String, List<String>> recipeToIngredients = recipeDao.loadRecipeGraph();
        Set<String> fridgeSupplies = supplyDao.getSupplies();

        if (recipeToIngredients.isEmpty() || fridgeSupplies.isEmpty()) {
            return Collections.emptyList();
        }

        // Build ingredient -> recipes graph
        // Note: loadRecipeGraph() already filters out seasonings (is_seasoning = 0)
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : recipeToIngredients.entrySet()) {
            String recipeName = entry.getKey();
            List<String> ingredients = entry.getValue();

            for (String ingredient : ingredients) {
                graph.computeIfAbsent(ingredient, k -> new ArrayList<>()).add(recipeName);
                inDegree.merge(recipeName, 1, Integer::sum);
            }
        }

        return kahnAlgorithm(graph, inDegree, new ArrayList<>(fridgeSupplies));
    }

    /**
     * Kahn's algorithm for topological sorting
     */
    private List<String> kahnAlgorithm(Map<String, List<String>> graph, Map<String, Integer> inDegree,
            List<String> supplies) {
        List<String> cookableRecipes = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> processed = new HashSet<>();

        for (String supply : supplies) {
            if (!processed.contains(supply)) {
                queue.offer(supply);
                processed.add(supply);
            }
        }

        while (!queue.isEmpty()) {
            String ingredient = queue.poll();
            List<String> dependentRecipes = graph.get(ingredient);
            if (dependentRecipes == null)
                continue;

            for (String recipe : dependentRecipes) {
                int remaining = inDegree.merge(recipe, -1, Integer::sum);
                if (remaining == 0 && !processed.contains(recipe)) {
                    queue.offer(recipe);
                    cookableRecipes.add(recipe);
                    processed.add(recipe);
                }
            }
        }
        return cookableRecipes;
    }

    /**
     * Get detailed recipe information by name
     */
    public RecipeDetails getRecipeDetails(String recipeName) {
        return recipeDao.getRecipeDetails(recipeName);
    }

    /**
     * Get all recipes grouped by cuisine type
     */
    public Map<String, List<RecipeSimple>> getAllRecipesByCuisine() {
        return recipeDao.getAllRecipesByCuisine();
    }

    /**
     * Get current fridge supplies with full details (quantity, sortOrder)
     */
    public List<Map<String, Object>> getFridgeSuppliesWithDetails() {
        return supplyDao.getSuppliesWithDetails();
    }

    /**
     * Get current fridge supplies with quantities
     */
    public Map<String, Integer> getFridgeSuppliesWithQuantity() {
        return supplyDao.getSuppliesWithQuantity();
    }

    /**
     * Get current fridge supplies (names only)
     */
    public Set<String> getFridgeSupplies() {
        return supplyDao.getSupplies();
    }

    /**
     * Update fridge supplies order
     */
    public void updateFridgeSuppliesOrder(List<String> orderedItems) {
        supplyDao.updateSuppliesOrder(orderedItems);
    }

    /**
     * Update fridge supplies (replace all)
     */
    public void updateFridgeSupplies(List<String> supplies) {
        supplyDao.updateSupplies(supplies);
    }

    /**
     * Add single item to fridge with quantity
     */
    public void addToFridge(String item, int count) {
        supplyDao.addSupply(item, count);
    }

    /**
     * Add single item to fridge with default quantity
     */
    public void addToFridge(String item) {
        supplyDao.addSupply(item, 1);
    }

    /**
     * Update item count in fridge
     */
    public void updateFridgeItemCount(String item, int count) {
        supplyDao.updateSupplyCount(item, count);
    }

    /**
     * Remove single item from fridge
     */
    public void removeFromFridge(String item) {
        supplyDao.removeSupply(item);
    }

    /**
     * Add a new recipe with details
     */
    public void addRecipe(String name, List<String> ingredients, String cuisineType, String instructions,
            String imageUrl) {
        recipeDao.saveRecipeWithDetails(name, ingredients, cuisineType, instructions, imageUrl);
    }

    /**
     * Add a new recipe with separate ingredients and seasonings
     */
    public void addRecipeWithSeasonings(String name, List<String> ingredients, List<String> seasonings,
            String cuisineType, String instructions, String imageUrl) {
        recipeDao.saveRecipeWithSeparateSeasonings(name, ingredients, seasonings, cuisineType, instructions, imageUrl);
    }

    /**
     * Delete a recipe
     */
    public void deleteRecipe(String name) {
        recipeDao.deleteRecipe(name);
    }
}
