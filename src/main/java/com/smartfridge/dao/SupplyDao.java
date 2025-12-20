package com.smartfridge.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * DAO for fridge supply operations
 */
@Repository
public class SupplyDao {

    @Autowired
    private DataSource dataSource;

    /**
     * Get current supplies with quantities and sort order
     */
    public List<Map<String, Object>> getSuppliesWithDetails() {
        List<Map<String, Object>> supplies = new ArrayList<>();
        String sql = "SELECT name, quantity, IFNULL(sort_order, 0) as sort_order FROM supplies ORDER BY sort_order ASC, name ASC";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("name"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("sortOrder", rs.getInt("sort_order"));
                supplies.add(item);
            }
        } catch (SQLException e) {
            // Fallback if sort_order doesn't exist
            return getSuppliesWithDetailsFallback();
        }
        return supplies;
    }

    private List<Map<String, Object>> getSuppliesWithDetailsFallback() {
        List<Map<String, Object>> supplies = new ArrayList<>();
        String sql = "SELECT name, quantity FROM supplies ORDER BY name ASC";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int order = 0;
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", rs.getString("name"));
                item.put("quantity", rs.getInt("quantity"));
                item.put("sortOrder", order++);
                supplies.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get supplies", e);
        }
        return supplies;
    }

    /**
     * Get supplies with quantities (legacy)
     */
    public Map<String, Integer> getSuppliesWithQuantity() {
        Map<String, Integer> supplies = new LinkedHashMap<>();
        for (Map<String, Object> item : getSuppliesWithDetails()) {
            supplies.put((String) item.get("name"), (Integer) item.get("quantity"));
        }
        return supplies;
    }

    /**
     * Get supply names only
     */
    public Set<String> getSupplies() {
        return getSuppliesWithQuantity().keySet();
    }

    /**
     * Update all supplies
     */
    public void updateSupplies(List<String> supplies) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM supplies");
                }

                String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
                String insertSupply = "INSERT OR IGNORE INTO supplies (name) VALUES (?)";

                try (PreparedStatement foodStmt = conn.prepareStatement(insertFood);
                        PreparedStatement supplyStmt = conn.prepareStatement(insertSupply)) {

                    for (String supply : supplies) {
                        foodStmt.setString(1, supply);
                        foodStmt.addBatch();
                        supplyStmt.setString(1, supply);
                        supplyStmt.addBatch();
                    }

                    foodStmt.executeBatch();
                    supplyStmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supplies", e);
        }
    }

    /**
     * Add single supply item with quantity
     */
    public void addSupply(String item, int quantity) {
        try (Connection conn = dataSource.getConnection()) {
            // Add to food_items
            String insertFood = "INSERT OR IGNORE INTO food_items (name) VALUES (?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertFood)) {
                pstmt.setString(1, item);
                pstmt.executeUpdate();
            }

            // Check if exists and update or insert
            String checkSql = "SELECT quantity FROM supplies WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                pstmt.setString(1, item);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int currentQty = rs.getInt("quantity");
                        String updateSql = "UPDATE supplies SET quantity = ? WHERE name = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, currentQty + quantity);
                            updateStmt.setString(2, item);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        String insertSupply = "INSERT INTO supplies (name, quantity) VALUES (?, ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSupply)) {
                            insertStmt.setString(1, item);
                            insertStmt.setInt(2, quantity);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add supply: " + item, e);
        }
    }

    public void addSupply(String item) {
        addSupply(item, 1);
    }

    /**
     * Update quantity for a supply item
     */
    public void updateSupplyCount(String item, int quantity) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE supplies SET quantity = ? WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, quantity);
                pstmt.setString(2, item);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supply count: " + item, e);
        }
    }

    /**
     * Update sort order for supplies
     */
    public void updateSuppliesOrder(List<String> orderedItems) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String sql = "UPDATE supplies SET sort_order = ? WHERE name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < orderedItems.size(); i++) {
                        pstmt.setInt(1, i);
                        pstmt.setString(2, orderedItems.get(i));
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update supplies order", e);
        }
    }

    /**
     * Remove single supply item
     */
    public void removeSupply(String item) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM supplies WHERE name = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, item);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove supply: " + item, e);
        }
    }
}
