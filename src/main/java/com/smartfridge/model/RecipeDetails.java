package com.smartfridge.model;

import java.util.List;

/**
 * Detailed recipe information (loaded on-demand from recipe_details table)
 */
public class RecipeDetails {

    private String name;
    private List<String> ingredients;
    private CuisineType cuisineType;
    private String instructions;
    private String imageUrl; // URL or file path to recipe image

    public RecipeDetails() {
    }

    /* Constructors */
    public RecipeDetails(String name, List<String> ingredients, CuisineType cuisineType, String instructions, String imageUrl) {
        this.name = name;
        this.ingredients = ingredients;
        this.cuisineType = cuisineType;
        this.instructions = instructions;
        this.imageUrl = imageUrl;
    }

    // constructor for lightweight data (no image URL)
    public RecipeDetails(String name, List<String> ingredients, CuisineType cuisineType, String instructions) {
        this.name = name;
        this.ingredients = ingredients;
        this.cuisineType = cuisineType;
        this.instructions = instructions;
    }

    // constructor for lightweight data (no instructions or image URL)
    public RecipeDetails(String name, List<String> ingredients, CuisineType cuisineType) {
        this.name = name;
        this.ingredients = ingredients;
        this.cuisineType = cuisineType; 
    }

    /* Getters and Setters */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(List<String> ingredients) {
        this.ingredients = ingredients;
    }

    public CuisineType getCuisineType() {
        return cuisineType;
    }

    public void setCuisineType(CuisineType cuisineType) {
        this.cuisineType = cuisineType;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
