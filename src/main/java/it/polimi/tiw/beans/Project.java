package it.polimi.tiw.beans;

import java.util.ArrayList;
import java.util.List;

public class Project {
	private int id;
	private String title;
	private int durationMonths;
	private State state;
	private int administratorId;
	private int managerId;
	
	private List<WorkPackage> workPackages = new ArrayList<>();
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
	}
	
	public int getDurationMonths() {
		return durationMonths;
	}
	
	public void setDurationMonths(int durationMonths) {
		this.durationMonths = durationMonths;
	}
	
	public State getState() {
		return state;
	}
	
	public void setState(State state) {
		this.state = state;
	}
	
	public int getAdministratorId() {
		return administratorId;
	}
	
	public void setAdministratorId(int administratorId) {
		this.administratorId = administratorId;
	}
	
	public int getManagerId() {
		return managerId;
	}
	
	public void setManagerId(int managerId) {
		this.managerId = managerId;
	}
	
	public List<WorkPackage> getWorkPackages() {
		return workPackages;
	}
	
    public void setWorkPackages(List<WorkPackage> workPackages) {
    	this.workPackages = workPackages;
    }
	
	public boolean isCreated() {
		return state.equals(State.CREATED);
	}
	
	public boolean isAssigned() {
		return state.equals(State.ASSIGNED);
	}
	
	public boolean isConcluded() {
		return state.equals(State.CONCLUDED);
	}
	
}
