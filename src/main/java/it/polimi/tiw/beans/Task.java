package it.polimi.tiw.beans;

import java.util.ArrayList;
import java.util.List;

public class Task {
	private int id;
	private int wpId;
	private int orderNumber;
	private String title;
	private String description;
	private int startMonth;
	private int endMonth;
	private int wpOrderNumber;
	private int totalPlannedHours;
    private int totalWorkedHours;
    
    //private List<MonthHours> monthHours = new ArrayList<>();
    private List<User> collaborators = new ArrayList<>();
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public int getWpId() {
		return wpId;
	}
	
	public void setWpId(int wpId) {
		this.wpId = wpId;
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
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
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

	public int getWpOrderNumber() {
		return wpOrderNumber;
	}

	public void setWpOrderNumber(int wpOrderNumber) {
		this.wpOrderNumber = wpOrderNumber;
	}

	public int getTotalPlannedHours() {
		return totalPlannedHours;
	}

	public void setTotalPlannedHours(int totalPlannedHours) {
		this.totalPlannedHours = totalPlannedHours;
	}

	public int getTotalWorkedHours() {
		return totalWorkedHours;
	}

	public void setTotalWorkedHours(int totalWorkedHours) {
		this.totalWorkedHours = totalWorkedHours;
	}

	public List<User> getCollaborators() {
		return collaborators;
	}

	public void setCollaborators(List<User> collaborators) {
		this.collaborators = collaborators;
	}
	
	public String getCode() {
        return "T" + wpOrderNumber + "." + orderNumber;
    }
}
