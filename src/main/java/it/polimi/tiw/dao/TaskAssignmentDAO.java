package it.polimi.tiw.dao;

import java.sql.*;

public class TaskAssignmentDAO{

    private final Connection conn;

    public TaskAssignmentDAO(Connection conn) {
        this.conn = conn;
    }

    public void assign(int taskId, int collabId) throws SQLException {
        String query = "INSERT IGNORE INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.executeUpdate();
        }
    }

    public void remove(int taskId, int collabId) throws SQLException {
        String query = "DELETE FROM task_assignments WHERE task_id = ? AND collaborator_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            ps.executeUpdate();
        }
    }

    public boolean isAssigned(int taskId, int collabId) throws SQLException {
        String query = "SELECT 1 FROM task_assignments WHERE task_id = ? AND collaborator_id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isAssignedAndProjectAssigned(int taskId, int collabId) throws SQLException {
        String query = "SELECT 1 FROM task_assignments ta " +
                       "JOIN tasks t ON t.id = ta.task_id " +
                       "JOIN work_packages w ON w.id = t.wp_id " +
                       "JOIN projects p ON p.id = w.project_id " +
                       "WHERE ta.task_id = ? AND ta.collaborator_id = ? AND p.state = 'ASSIGNED' LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, taskId);
            ps.setInt(2, collabId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}