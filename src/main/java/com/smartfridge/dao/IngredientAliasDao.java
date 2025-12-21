package com.smartfridge.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * DAO for ingredient alias operations.
 * Manages canonical name to alias mappings for flexible ingredient matching.
 */
@Repository
public class IngredientAliasDao {

    @Autowired
    private DataSource dataSource;

    /**
     * Resolve an ingredient name to its canonical form.
     * Returns the original name if no alias is found.
     */
    public String resolveToCanonical(String ingredient) {
        if (ingredient == null || ingredient.trim().isEmpty()) {
            return ingredient;
        }

        String normalized = ingredient.trim().toLowerCase();

        // First check if this is already a canonical name
        String sql = "SELECT DISTINCT canonical_name FROM ingredient_aliases WHERE LOWER(canonical_name) = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalized);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("canonical_name");
            }
        } catch (SQLException e) {
            System.err.println("Error checking canonical name: " + e.getMessage());
        }

        // Then check if this is an alias
        sql = "SELECT canonical_name FROM ingredient_aliases WHERE LOWER(alias) = ? ORDER BY confidence DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalized);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("canonical_name");
            }
        } catch (SQLException e) {
            System.err.println("Error resolving alias: " + e.getMessage());
        }

        // No alias found, return original
        return ingredient;
    }

    /**
     * Get all aliases for a canonical name
     */
    public List<String> getAliasesForCanonical(String canonicalName) {
        List<String> aliases = new ArrayList<>();
        String sql = "SELECT alias, confidence FROM ingredient_aliases WHERE LOWER(canonical_name) = ? ORDER BY confidence DESC";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, canonicalName.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                aliases.add(rs.getString("alias"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting aliases: " + e.getMessage());
        }

        return aliases;
    }

    /**
     * Add a new alias mapping
     */
    public void addAlias(String canonicalName, String alias, double confidence, String source) {
        String sql = "INSERT OR REPLACE INTO ingredient_aliases (canonical_name, alias, confidence, source, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, canonicalName.toLowerCase());
            pstmt.setString(2, alias.toLowerCase());
            pstmt.setDouble(3, confidence);
            pstmt.setString(4, source);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding alias: " + e.getMessage());
        }
    }

    /**
     * Add multiple aliases for a canonical name
     */
    public void addAliases(String canonicalName, List<String> aliases, double confidence, String source) {
        for (String alias : aliases) {
            addAlias(canonicalName, alias, confidence, source);
        }
    }

    /**
     * Get all canonical names in the system
     */
    public Set<String> getAllCanonicalNames() {
        Set<String> names = new HashSet<>();
        String sql = "SELECT DISTINCT canonical_name FROM ingredient_aliases";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("canonical_name"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting canonical names: " + e.getMessage());
        }

        return names;
    }

    /**
     * Delete all aliases for a canonical name
     */
    public void deleteAliasesForCanonical(String canonicalName) {
        String sql = "DELETE FROM ingredient_aliases WHERE LOWER(canonical_name) = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, canonicalName.toLowerCase());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting aliases: " + e.getMessage());
        }
    }

    /**
     * Check if an alias exists
     */
    public boolean aliasExists(String alias) {
        String sql = "SELECT 1 FROM ingredient_aliases WHERE LOWER(alias) = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, alias.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking alias existence: " + e.getMessage());
            return false;
        }
    }
}
