package com.smartfridge.model;

import java.util.List;

/**
 * Response model for missing ingredients analysis
 */
public class MissingIngredientsResponse {
    private String recipeName;
    private List<String> missingIngredients;
    private int totalRequired;
    private double coveragePercent;

    // Constructors
    public MissingIngredientsResponse() {
    }

    public MissingIngredientsResponse(String recipeName, List<String> missingIngredients,
            int totalRequired, double coveragePercent) {
        this.recipeName = recipeName;
        this.missingIngredients = missingIngredients;
        this.totalRequired = totalRequired;
        this.coveragePercent = coveragePercent;
    }

    // Getters and Setters
    public String getRecipeName() {
        return recipeName;
    }

    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    public List<String> getMissingIngredients() {
        return missingIngredients;
    }

    public void setMissingIngredients(List<String> missingIngredients) {
        this.missingIngredients = missingIngredients;
    }

    public int getTotalRequired() {
        return totalRequired;
    }

    public void setTotalRequired(int totalRequired) {
        this.totalRequired = totalRequired;
    }

    public double getCoveragePercent() {
        return coveragePercent;
    }

    public void setCoveragePercent(double coveragePercent) {
        this.coveragePercent = coveragePercent;
    }

    @Override
    public String toString() {
        return "MissingIngredientsResponse{" +
                "recipeName='" + recipeName + '\'' +
                ", missingIngredients=" + missingIngredients +
                ", totalRequired=" + totalRequired +
                ", coveragePercent=" + coveragePercent +
                '}';
    }
}
