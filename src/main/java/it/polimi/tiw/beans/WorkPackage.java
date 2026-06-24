package it.polimi.tiw.beans;

import java.util.ArrayList;
import java.util.List;

public class WorkPackage {
	private int id;
	private int projectId;
	private int orderNumber;
	private String title;
	private int startMonth;
	private int endMonth;
	
	private List<Task> tasks = new ArrayList<>();
	
	private List<MonthHours> monthHours = new ArrayList<>();
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public int getProjectId() {
		return projectId;
	}
	
	public void setProjectId(int projectId) {
		this.projectId = projectId;
	}
	
	public int getOrderNumber() {
		return orderNumber;
	}
	
	public void setOrderNumber(int orderNumber) {
		this.orderNumber = orderNumber;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public int getStartMonth() {
		return startMonth;
	}
	
	public void setStartMonth(int startMonth) {
		this.startMonth = startMonth;
	}
	
	public int getEndMonth() {
		return endMonth;
	}
	
	public void setEndMonth(int endMonth) {
		this.endMonth = endMonth;
	}

	public List<Task> getTasks() {
		return tasks;
	}

	public void setTasks(List<Task> tasks) {
		this.tasks = tasks;
	}
	
	public String getCode() {
        return "WP" + orderNumber;
    }
	
	public int getTotalPlannedHours() {
        int tot = 0;
        for (Task t : tasks) tot += t.getTotalPlannedHours();
        return tot;
    }

    public int getTotalWorkedHours() {
        int tot = 0;
        for (Task t : tasks) tot += t.getTotalWorkedHours();
        return tot;
    }

	public List<MonthHours> getMonthHours() {
		return monthHours;
	}

	public void setMonthHours(List<MonthHours> monthHours) {
		this.monthHours = monthHours;
	}
}
