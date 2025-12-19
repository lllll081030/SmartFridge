package com.smartfridge.model;

import java.util.List;

/**
 * Response model for POST /api/generate endpoint
 * Example:
 * {
 * "made": ["sandwich", "burger"]
 * }
 */
public class RecipeResponse {

    private List<String> made;

    public RecipeResponse() {
    }

    public RecipeResponse(List<String> made) {
        this.made = made;
    }

    public List<String> getMade() {
        return made;
    }

    public void setMade(List<String> made) {
        this.made = made;
    }
}
