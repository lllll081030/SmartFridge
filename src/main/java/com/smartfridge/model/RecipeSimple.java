package com.smartfridge.model;

import java.util.List;

public class RecipeSimple {
    private String name; // name of the recipe
    private List<String> ingredients; // list of ingredients needed

    // Constructors
    public RecipeSimple() {
    }

    public RecipeSimple(String name, List<String> ingredients) {
        this.name = name;
        this.ingredients = ingredients;
    }

    // Getters and Setters
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
}