package it.polimi.tiw.dao;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlannedHoursDAO {

    private final Connection conn;

    public PlannedHoursDAO(Connection conn) {
        this.conn = conn;
    }

    public Map<Integer,Integer> findByTask(int taskId) throws SQLException {
        String query = "SELECT month_index, hours FROM planned_hours WHERE task_id = ? ORDER BY month_index";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer,Integer> map = new LinkedHashMap<>();
                while (rs.next()) map.put(rs.getInt("month_index"), rs.getInt("hours"));
                return map;
            }
        }
    }

    public int getTotalByTask(int taskId) throws SQLException {
        String query = "SELECT COALESCE(SUM(hours), 0) FROM planned_hours WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void saveAll(int taskId, Map<Integer,Integer> orePerMese) throws SQLException {
        String query = "INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE hours = VALUES(hours)";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            for (Map.Entry<Integer,Integer> e : orePerMese.entrySet()) {
                ps.setInt(1, taskId);
                ps.setInt(2, e.getKey());
                ps.setInt(3, Math.max(0, e.getValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}