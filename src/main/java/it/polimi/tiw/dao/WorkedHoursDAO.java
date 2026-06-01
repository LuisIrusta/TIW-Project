package it.polimi.tiw.dao;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkedHoursDAO {

    private final Connection conn;

    public WorkedHoursDAO(Connection conn) {
        this.conn = conn;
    }

    public Map<Integer,Integer> findByTaskAndCollaborator(int taskId, int collabId) throws SQLException {
        String query = "SELECT month_index, hours FROM worked_hours " +
                       "WHERE task_id = ? AND collaborator_id = ? ORDER BY month_index";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer,Integer> map = new LinkedHashMap<>();
                while (rs.next()) map.put(rs.getInt("month_index"), rs.getInt("hours"));
                return map;
            }
        }
    }

    public Map<Integer,Integer> findTotalByTask(int taskId) throws SQLException {
        String query = "SELECT month_index, SUM(hours) as hours FROM worked_hours " +
                       "WHERE task_id = ? GROUP BY month_index ORDER BY month_index";
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
        String query = "SELECT COALESCE(SUM(hours), 0) FROM worked_hours WHERE task_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void save(int taskId, int collabId, int monthIndex, int hours) throws SQLException {
        String query = "INSERT INTO worked_hours (task_id, collaborator_id, month_index, hours) " +
                       "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE hours = VALUES(hours)";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.setInt(3, monthIndex);
            ps.setInt(4, hours);
            ps.executeUpdate();
        }
    }
}