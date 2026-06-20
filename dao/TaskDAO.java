package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.beans.Task;

public class TaskDAO {
	private final Connection connection;

	public TaskDAO(Connection connection) {
		this.connection = connection;
	}

	public int createTask(int idWp, String title, String description, int startMonth, int endMonth) throws SQLException {
		int orderNumber = getMaxOrderNumber(idWp) + 1;
		String query = "INSERT INTO tasks (wp_id, order_number, title, description, start_month, end_month) VALUES (?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, idWp);
			ps.setInt(2, orderNumber);
			ps.setString(3, title);
			ps.setString(4, description);
			ps.setInt(5, startMonth);
			ps.setInt(6, endMonth);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) return keys.getInt(1);
			}
		}
		throw new SQLException("Task creation failed: no id was generated");
	}
	
	public int getMaxOrderNumber(int idWp) throws SQLException {
        String query = "SELECT COALESCE(MAX(order_number), 0) AS m FROM tasks WHERE wp_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, idWp);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("m");
            }
        }
    }

	public Task findById(int id) throws SQLException {
		String query = """
		        SELECT t.*
		             , wp.order_number AS wp_no
		             , COALESCE((SELECT SUM(hours) FROM planned_hours WHERE task_id = t.id), 0) AS tot_prev
		             , COALESCE((SELECT SUM(hours) FROM worked_hours  WHERE task_id = t.id), 0) AS tot_lav
		        FROM tasks t
		        JOIN work_packages wp ON t.wp_id = wp.id
		        WHERE t.id = ?
		        """;
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return buildTask(rs);
                return null;
            }
        }
    }
	
	public List<Task> findByWorkPackage(int idWp) throws SQLException {
		String query = "SELECT t.*"
	            + ", wp.order_number AS wp_no"
	            + ", COALESCE((SELECT SUM(hours) FROM planned_hours WHERE task_id = t.id), 0) AS tot_prev"
	            + ", COALESCE((SELECT SUM(hours) FROM worked_hours  WHERE task_id = t.id), 0) AS tot_lav"
	            + " FROM tasks t"
	            + " JOIN work_packages wp ON t.wp_id = wp.id"
	            + " WHERE t.wp_id = ?"
	            + " ORDER BY t.order_number";
		List<Task> result = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, idWp);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) result.add(buildTask(rs));
			}
		}
		return result;
	}
	
	public List<Task> findByWorkPackageAndCollaborator(int idWp, int idCollaborator) throws SQLException {
		String query = "SELECT t.*"
	            + ", wp.order_number AS wp_no"
	            + ", COALESCE((SELECT SUM(hours) FROM planned_hours WHERE task_id = t.id), 0) AS tot_prev"
	            + ", COALESCE((SELECT SUM(hours) FROM worked_hours  WHERE task_id = t.id), 0) AS tot_lav"
	            + " FROM tasks t"
	            + " JOIN work_packages wp ON t.wp_id = wp.id"
	            + " JOIN task_assignments a ON a.task_id = t.id"
	            + " WHERE t.wp_id = ? AND a.collaborator_id = ?"
	            + " ORDER BY t.order_number";
        List<Task> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, idWp);
            ps.setInt(2, idCollaborator);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(buildTask(rs));
            }
        }
        return result;
    }

	private Task buildTask(ResultSet rs) throws SQLException {
		Task t = new Task();
		t.setId(rs.getInt("id"));
		t.setWpId(rs.getInt("wp_id"));
		t.setOrderNumber(rs.getInt("order_number"));
		t.setWpOrderNumber(rs.getInt("wp_no"));
		t.setTitle(rs.getString("title"));
		t.setDescription(rs.getString("description"));
		t.setStartMonth(rs.getInt("start_month"));
		t.setEndMonth(rs.getInt("end_month"));
		t.setTotalPlannedHours(rs.getInt("tot_prev"));
	    t.setTotalWorkedHours(rs.getInt("tot_lav"));
		return t;
	}

}
