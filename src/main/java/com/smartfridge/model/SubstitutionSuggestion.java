package com.smartfridge.model;

/**
 * Represents an AI-generated ingredient substitution suggestion
 */
public class SubstitutionSuggestion {
    private String originalIngredient;
    private String substitute;
    private boolean inFridge;
    private double confidence;
    private String reasoning;

    // Constructors
    public SubstitutionSuggestion() {
    }

    public SubstitutionSuggestion(String originalIngredient, String substitute, boolean inFridge,
            double confidence, String reasoning) {
        this.originalIngredient = originalIngredient;
        this.substitute = substitute;
        this.inFridge = inFridge;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }

    // Getters and Setters
    public String getOriginalIngredient() {
        return originalIngredient;
    }

    public void setOriginalIngredient(String originalIngredient) {
        this.originalIngredient = originalIngredient;
    }

    public String getSubstitute() {
        return substitute;
    }

    /**
     * Alias for getSubstitute() - allows JSON to have "ingredient" field
     * This matches what the frontend expects
     */
    public String getIngredient() {
        return substitute;
    }

    public void setSubstitute(String substitute) {
        this.substitute = substitute;
    }

    public boolean isInFridge() {
        return inFridge;
    }

    public void setInFridge(boolean inFridge) {
        this.inFridge = inFridge;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    @Override
    public String toString() {
        return "SubstitutionSuggestion{" +
                "originalIngredient='" + originalIngredient + '\'' +
                ", substitute='" + substitute + '\'' +
                ", inFridge=" + inFridge +
                ", confidence=" + confidence +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
}
