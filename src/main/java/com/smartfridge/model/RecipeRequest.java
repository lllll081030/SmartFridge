package com.smartfridge.model;

import java.util.List;

/**
 * Request model for POST /api/generate endpoint
 * Example:
 * {
 * "recipes": ["sandwich", "burger"],
 * "ingredients": [["bread", "ham"], ["bread", "meat"]],
 * "supplies": ["bread", "ham", "meat"]
 * }
 */
public class RecipeRequest {

    private List<String> recipes;
    private List<List<String>> ingredients;
    private List<String> supplies;

    public RecipeRequest() {
    }

    public RecipeRequest(List<String> recipes, List<List<String>> ingredients, List<String> supplies) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.supplies = supplies;
    }

    public List<String> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<String> recipes) {
        this.recipes = recipes;
    }

    public List<List<String>> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<List<String>> ingredients) {
        this.ingredients = ingredients;
    }

    public List<String> getSupplies() {
        return supplies;
    }

    public void setSupplies(List<String> supplies) {
        this.supplies = supplies;
    }
}
