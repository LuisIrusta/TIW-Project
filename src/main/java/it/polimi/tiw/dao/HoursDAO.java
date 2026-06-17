package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.polimi.tiw.beans.MonthHours;
import it.polimi.tiw.beans.PersonType;
import it.polimi.tiw.beans.User;


public class HoursDAO {

	private final Connection connection;

	public HoursDAO(Connection connection) {
		this.connection = connection;
	}

	public List<User> getCollaboratorsOfTask(int taskId) throws SQLException {
		String query = "SELECT u.id, u.username, u.first_name, u.last_name, u.photo, u.role "
				+ "FROM users u JOIN task_assignments a ON a.collaborator_id = u.id "
				+ "WHERE a.task_id = ? ORDER BY u.last_name, u.first_name";
		List<User> result = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, taskId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					User u = new User();
					u.setId(rs.getInt("id"));
					u.setUsername(rs.getString("username"));
					u.setFirstName(rs.getString("first_name"));
					u.setLastName(rs.getString("last_name"));
					u.setPhoto(rs.getString("photo"));
					u.setPersonType(PersonType.fromDB(rs.getString("role")));
					result.add(u);
				}
			}
		}
		return result;
	}

	public Set<Integer> getCollaboratorIdsOfTask(int taskId) throws SQLException {
		String query = "SELECT collaborator_id FROM task_assignments WHERE task_id = ?";
		Set<Integer> ids = new HashSet<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, taskId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) ids.add(rs.getInt(1));
			}
		}
		return ids;
	}
	
	public void saveTaskAssignment(int taskId, List<Integer> collabIds, Map<Integer, Integer> hoursByMonth) throws SQLException {
		boolean previousAutoCommit = connection.getAutoCommit();
		try {
			connection.setAutoCommit(false);
			try (PreparedStatement del = connection.prepareStatement("DELETE FROM task_assignments WHERE task_id = ?")) {
				del.setInt(1, taskId);
				del.executeUpdate();
			}
			try (PreparedStatement ins = connection.prepareStatement("INSERT INTO task_assignments (task_id, collaborator_id) VALUES (?, ?)")) {
				for (Integer cid : collabIds) {
					ins.setInt(1, taskId);
					ins.setInt(2, cid);
					ins.addBatch();
				}
				ins.executeBatch();
			}
			try (PreparedStatement del = connection.prepareStatement("DELETE FROM planned_hours WHERE task_id = ?")) {
				del.setInt(1, taskId);
				del.executeUpdate();
			}
			try (PreparedStatement ins = connection.prepareStatement("INSERT INTO planned_hours (task_id, month_index, hours) VALUES (?, ?, ?)")) {
				for (Map.Entry<Integer, Integer> e : hoursByMonth.entrySet()) {
					ins.setInt(1, taskId);
					ins.setInt(2, e.getKey());
					ins.setInt(3, e.getValue());
					ins.addBatch();
				}
				ins.executeBatch();
			}
			connection.commit();
		} catch (SQLException ex) {
			connection.rollback();
			throw ex;
		} finally {
			connection.setAutoCommit(previousAutoCommit);
		}
	}

	public Map<Integer, Integer> getPlannedHoursMap(int taskId) throws SQLException {
		String query = "SELECT month_index, hours FROM planned_hours WHERE task_id = ?";
		Map<Integer, Integer> map = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, taskId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) map.put(rs.getInt("month_index"), rs.getInt("hours"));
			}
		}
		return map;
	}

	public void saveWorkedHours(int taskId, int collaboratorId, int month, int hours) throws SQLException {
		String query = "INSERT INTO worked_hours (task_id, collaborator_id, month_index, hours) "
				+ "VALUES (?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE hours = VALUES(hours)";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, taskId);
			ps.setInt(2, collaboratorId);
			ps.setInt(3, month);
			ps.setInt(4, hours);
			ps.executeUpdate();
		}
	}

	public int getTotalPlannedForTask(int taskId) throws SQLException {
		return scalarSum("SELECT COALESCE(SUM(hours),0) FROM planned_hours WHERE task_id = ?", taskId);
	}

	public int getTotalWorkedForTask(int taskId) throws SQLException {
		return scalarSum("SELECT COALESCE(SUM(hours),0) FROM worked_hours WHERE task_id = ?", taskId);
	}

	public int getTotalWorkedForCollaboratorTask(int taskId, int collaboratorId) throws SQLException {
		String query = "SELECT COALESCE(SUM(hours),0) FROM worked_hours WHERE task_id = ? AND collaborator_id = ?";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, taskId);
			ps.setInt(2, collaboratorId);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}
	
	public List<MonthHours> getMonthHoursForTask(int taskId, int startMonth, int endMonth) throws SQLException {
		Map<Integer, MonthHours> map = new HashMap<>();
		for (int m = startMonth; m <= endMonth; m++) {
			map.put(m, new MonthHours(m));
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT month_index, hours FROM planned_hours WHERE task_id = ?")) {
			ps.setInt(1, taskId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					MonthHours mh = map.get(rs.getInt("month_index"));
					if (mh != null) mh.setPlannedHours(rs.getInt("hours"));
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT month_index, COALESCE(SUM(hours),0) AS tot FROM worked_hours WHERE task_id = ? GROUP BY month_index")) {
			ps.setInt(1, taskId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					MonthHours mh = map.get(rs.getInt("month_index"));
					if (mh != null) mh.setWorkedHours(rs.getInt("tot"));
				}
			}
		}
		return toSortedList(map, startMonth, endMonth);
	}

	public List<MonthHours> getMonthWorkedForCollaboratorTask(int taskId, int collaboratorId, int startMonth, int endMonth) throws SQLException {
		Map<Integer, MonthHours> map = new HashMap<>();
		for (int m = startMonth; m <= endMonth; m++) {
			map.put(m, new MonthHours(m));
		}
		try (PreparedStatement ps = connection.prepareStatement( "SELECT month_index, hours FROM worked_hours WHERE task_id = ? AND collaborator_id = ?")) {
			ps.setInt(1, taskId);
			ps.setInt(2, collaboratorId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					MonthHours mh = map.get(rs.getInt("month_index"));
					if (mh != null) mh.setWorkedHours(rs.getInt("hours"));
				}
			}
		}
		return toSortedList(map, startMonth, endMonth);
	}

	public List<String> getAssignmentBlockers(int projectId) throws SQLException {
		String query =
				"SELECT wp.order_number AS wp_no, t.order_number AS t_no, t.title, "
						+ "       t.start_month, t.end_month, "
						+ "       (SELECT COUNT(*) FROM task_assignments a WHERE a.task_id = t.id) AS n_coll, "
						+ "       (SELECT COUNT(DISTINCT op.month_index) FROM planned_hours op WHERE op.task_id = t.id) AS n_months "
						+ "FROM tasks t JOIN work_packages wp ON t.wp_id = wp.id "
						+ "WHERE wp.project_id = ? "
						+ "ORDER BY wp.order_number, t.order_number";

		List<String> blockers = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, projectId);
			try (ResultSet rs = ps.executeQuery()) {
				boolean atLeastOneTask = false;
				while (rs.next()) {
					atLeastOneTask = true;
					String code = "T" + rs.getInt("wp_no") + "." + rs.getInt("t_no");
					String title = rs.getString("title");
					int nColl = rs.getInt("n_coll");
					int nMonths = rs.getInt("n_months");
					int duration = rs.getInt("end_month") - rs.getInt("start_month") + 1;
					if (nColl == 0) {
						blockers.add("The task " + code + " (" + title
								+ ") has no collaborators assigned");
					}
					if (nMonths < duration) {
						blockers.add("The task " + code + " (" + title
								+ ") does not have planned hours for all months");
					}
				}
				if (!atLeastOneTask) {
					blockers.add("The project does not contain any task");
				}
			}
		}
		return blockers;
	}

	public boolean canConcludeProject(int projectId) throws SQLException {
		String query =
				"SELECT t.id, "
						+ "       (SELECT COALESCE(SUM(op.hours),0) FROM planned_hours op WHERE op.task_id = t.id) AS planned, "
						+ "       (SELECT COALESCE(SUM(ol.hours),0) FROM worked_hours ol WHERE ol.task_id = t.id) AS worked "
						+ "FROM tasks t JOIN work_packages wp ON t.wp_id = wp.id "
						+ "WHERE wp.project_id = ?";
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, projectId);
			try (ResultSet rs = ps.executeQuery()) {
				boolean atLeastOneTask = false;
				while (rs.next()) {
					atLeastOneTask = true;
					if (rs.getInt("worked") < rs.getInt("planned")) {
						return false;
					}
				}
				return atLeastOneTask;
			}
		}
	}

	
	
	private int scalarSum(String query, int param) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(query)) {
			ps.setInt(1, param);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	private List<MonthHours> toSortedList(Map<Integer, MonthHours> map, int from, int to) {
		List<MonthHours> list = new ArrayList<>();
		for (int m = from; m <= to; m++) {
			list.add(map.get(m));
		}
		return list;
	}
}