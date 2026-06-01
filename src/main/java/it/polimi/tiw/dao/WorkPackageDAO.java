package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import it.polimi.tiw.beans.WorkPackage;

public class WorkPackageDAO {
	private final Connection connection;

	public WorkPackageDAO(Connection connection) {
		this.connection = connection;
	}

	public int createWorkPackage(int projectId, String title, int startMonth, int endMonth) throws SQLException {
		int orderNumber = getMaxOrderNumber(projectId) + 1;
		String query = "INSERT INTO work_packages (project_id, order_number, title, start_month, end_month) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
			ps.setInt(1, projectId);
			ps.setInt(2, orderNumber);
			ps.setString(3, title);
			ps.setInt(4, startMonth);
			ps.setInt(5, endMonth);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) return keys.getInt(1);
			}
		}
		throw new SQLException("WP creation failed: no id was generated");
	}
	
	public int getMaxOrderNumber(int projectId) throws SQLException {
        String query = "SELECT COALESCE(MAX(order_number), 0) AS m FROM work_packages WHERE project_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("m");
            }
        }
    }

	public WorkPackage findById(int id) throws SQLException {
		String query = "SELECT * FROM work_packages WHERE id = ?";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) return buildWorkPackage(rs);
				return null;
			}
		}
	}

	public List<WorkPackage> findByProject(int idProgetto) throws SQLException {
		String query = "SELECT * FROM work_packages WHERE project_id = ? ORDER BY order_number";
		List<WorkPackage> result = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, idProgetto);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) result.add(buildWorkPackage(rs));
			}
		}
		return result;
	}
	   public List<WorkPackage> findByProjectAndCollaborator(int projectId, int collabId) throws SQLException {
	        String query = "SELECT DISTINCT w.id, w.project_id, w.order_number, w.title, w.start_month, w.end_month " +
	                       "FROM work_packages w " +
	                       "JOIN tasks t ON t.wp_id = w.id " +
	                       "JOIN task_assignments ta ON ta.task_id = t.id " +
	                       "WHERE w.project_id = ? AND ta.collaborator_id = ? ORDER BY w.order_number";
	    	List<WorkPackage> result = new ArrayList<>();
	        try (PreparedStatement ps = connection.prepareStatement(query)) {
	            ps.setInt(1, projectId);
	            ps.setInt(2, collabId);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) result.add(buildWorkPackage(rs));
				}
	        }
	    	return result;
	    }

	private WorkPackage buildWorkPackage(ResultSet rs) throws SQLException {
		WorkPackage wp = new WorkPackage();
		wp.setId(rs.getInt("id"));
		wp.setProjectId(rs.getInt("project_id"));
		wp.setOrderNumber(rs.getInt("order_number"));
		wp.setTitle(rs.getString("title"));
		wp.setStartMonth(rs.getInt("start_month"));
		wp.setEndMonth(rs.getInt("end_month"));
		return wp;
	}

}
