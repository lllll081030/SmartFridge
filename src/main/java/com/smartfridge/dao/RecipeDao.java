package com.smartfridge.dao;

import com.smartfridge.model.CuisineType;
import com.smartfridge.model.RecipeDetails;
import com.smartfridge.model.RecipeSimple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * DAO for recipe operations only
 */
@Repository
public class RecipeDao {

    @Autowired
    private DataSource dataSource;

    /**
     * Load recipe graph for Kahn's algorithm (non-seasonings only)
     */
    public Map<String, List<String>> loadRecipeGraph() {
        Map<String, List<String>> graph = new HashMap<>();
        String sql = "SELECT recipe_name, ingredient_name FROM recipe_dependencies WHERE is_seasoning = 0 ORDER BY recipe_name";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String recipeName = rs.getString("recipe_name");
                String ingredientName = rs.getString("ingredient_name");
                graph.computeIfAbsent(recipeName, k -> new ArrayList<>()).add(ingredientName);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load recipe graph", e);
        }
        return graph;
    }

    /**
     * Get recipe details by name
     */
    public RecipeDetails getRecipeDetails(String recipeName) {
        String sql = """
                SELECT rd.recipe_name, rd.cuisine_type, rd.instructions, rd.image_url,
                       dep.ingredient_name
                FROM recipe_details rd
                LEFT JOIN recipe_dependencies dep ON rd.recipe_name = dep.recipe_name
                WHERE rd.recipe_name = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, recipeName);

            String cuisineTypeStr = null;
            String instructions = null;
            String imageUrl = null;
            List<String> ingredients = new ArrayList<>();

            try (ResultSet rs = pstmt.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (first) {
                        cuisineTypeStr = rs.getString("cuisine_type");
                        instructions = rs.getString("instructions");
                        imageUrl = rs.getString("image_url");
                        first = false;
                    }
                    String ingredient = rs.getString("ingredient_name");
                    if (ingredient != null) {
                        ingredients.add(ingredient);
                    }
                }
            }

            if (!ingredients.isEmpty()) {
                CuisineType cuisineType = parseCuisineType(cuisineTypeStr);
                return new RecipeDetails(recipeName, ingredients, cuisineType, instructions, imageUrl);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get recipe details for: " + recipeName, e);
        }
        return null;
    }

    private CuisineType parseCuisineType(String cuisineTypeStr) {
        if (cuisineTypeStr == null || cuisineTypeStr.isEmpty()) {
            return CuisineType.OTHER;
        }
        try {
            return CuisineType.valueOf(cuisineTypeStr);
        } catch (IllegalArgumentException e) {
            return CuisineType.OTHER;
        }
    }

    /**
     * Save recipe with details
     */
    public void saveRecipeWithDetails(String recipeName, List<String> ingredients,
            String cuisineType, String instructions, String imageUrl) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert food items
                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                    pstmt.setString(1, recipeName);
                    pstmt.addBatch();
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert dependencies
                String insertDep = "INSERT OR IGNORE INTO recipe_dependencies (recipe_name, ingredient_name) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDep)) {
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, recipeName);
                        pstmt.setString(2, ingredient);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert details
                String insertDetails = "INSERT OR REPLACE INTO recipe_details (recipe_name, cuisine_type, instructions, image_url) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDetails)) {
                    pstmt.setString(1, recipeName);
                    pstmt.setString(2, cuisineType != null ? cuisineType : "OTHER");
                    pstmt.setString(3, instructions);
                    pstmt.setString(4, imageUrl);
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save recipe: " + recipeName, e);
        }
    }

    /**
     * Save recipe with separate ingredients and seasonings
     */
    public void saveRecipeWithSeparateSeasonings(String recipeName, List<String> ingredients,
            List<String> seasonings, String cuisineType, String instructions, String imageUrl) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Insert food items
                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                    pstmt.setString(1, recipeName);
                    pstmt.addBatch();
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, ingredient);
                        pstmt.addBatch();
                    }
                    for (String seasoning : seasonings) {
                        pstmt.setString(1, seasoning);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert dependencies with is_seasoning flag
                String insertDep = "INSERT OR IGNORE INTO recipe_dependencies (recipe_name, ingredient_name, is_seasoning) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDep)) {
                    for (String ingredient : ingredients) {
                        pstmt.setString(1, recipeName);
                        pstmt.setString(2, ingredient);
                        pstmt.setInt(3, 0);
                        pstmt.addBatch();
                    }
                    for (String seasoning : seasonings) {
                        pstmt.setString(1, recipeName);
                        pstmt.setString(2, seasoning);
                        pstmt.setInt(3, 1);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Insert details
                String insertDetails = "INSERT OR REPLACE INTO recipe_details (recipe_name, cuisine_type, instructions, image_url) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertDetails)) {
                    pstmt.setString(1, recipeName);
                    pstmt.setString(2, cuisineType != null ? cuisineType : "OTHER");
                    pstmt.setString(3, instructions);
                    pstmt.setString(4, imageUrl);
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save recipe: " + recipeName, e);
        }
    }

    /**
     * Delete a recipe
     */
    public void deleteRecipe(String recipeName) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String deleteDetails = "DELETE FROM recipe_details WHERE recipe_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteDetails)) {
                    pstmt.setString(1, recipeName);
                    pstmt.executeUpdate();
                }

                String deleteDeps = "DELETE FROM recipe_dependencies WHERE recipe_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteDeps)) {
                    pstmt.setString(1, recipeName);
                    pstmt.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete recipe: " + recipeName, e);
        }
    }

    /**
     * Get all recipes grouped by cuisine type
     */
    public Map<String, List<RecipeSimple>> getAllRecipesByCuisine() {
        Map<String, List<RecipeSimple>> result = new LinkedHashMap<>();

        String sql = """
                SELECT rd.recipe_name, rd.cuisine_type, dep.ingredient_name, dep.is_seasoning
                FROM recipe_details rd
                LEFT JOIN recipe_dependencies dep ON rd.recipe_name = dep.recipe_name
                ORDER BY rd.cuisine_type, rd.recipe_name
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            Map<String, List<String>> recipeIngredients = new LinkedHashMap<>();
            Map<String, List<String>> recipeSeasonings = new LinkedHashMap<>();
            Map<String, String> recipeCuisine = new HashMap<>();

            while (rs.next()) {
                String recipeName = rs.getString("recipe_name");
                String cuisineType = rs.getString("cuisine_type");
                String ingredient = rs.getString("ingredient_name");
                int isSeasoning = rs.getInt("is_seasoning");

                recipeCuisine.put(recipeName, cuisineType != null ? cuisineType : "OTHER");

                if (ingredient != null) {
                    if (isSeasoning == 1) {
                        recipeSeasonings.computeIfAbsent(recipeName, k -> new ArrayList<>()).add(ingredient);
                    } else {
                        recipeIngredients.computeIfAbsent(recipeName, k -> new ArrayList<>()).add(ingredient);
                    }
                }
            }

            for (Map.Entry<String, List<String>> entry : recipeIngredients.entrySet()) {
                String recipeName = entry.getKey();
                List<String> ingredients = entry.getValue();
                List<String> seasonings = recipeSeasonings.getOrDefault(recipeName, new ArrayList<>());
                String cuisine = recipeCuisine.get(recipeName);

                RecipeSimple recipe = new RecipeSimple(recipeName, ingredients, seasonings);
                result.computeIfAbsent(cuisine, k -> new ArrayList<>()).add(recipe);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get recipes by cuisine", e);
        }
        return result;
    }
}
