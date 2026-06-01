package it.polimi.tiw.beans;

public class User {
	
	private int id;
	private String username;
	private String firstName;
	private String lastName;
	private String photo;
	private PersonType personType; //ADMINISTRATIVE or TECHNICAL or COLLABORATOR
	private boolean administrative;
	private boolean technical;
	private boolean collaborator;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getFirstName() {
		return firstName;
	}
	
	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}
	
	public String getLastName() {
		return lastName;
	}
	
	public void setLastName(String lastName) {
		this.lastName = lastName;
	}
	
	public String getPhoto() {
		return photo;
	}
	
	public void setPhoto(String photo) {
		this.photo = photo;
	}
	
	public PersonType getPersonType() {
		return personType;
	}
	
	public void setPersonType(PersonType personType) {
		this.personType = personType;
	}
	
	public String getFullName() {
		return firstName + " " + lastName;
	}
	


	public boolean isAdministrative() { return administrative; }
	public boolean isTechnical()      { return technical; }
	public boolean isCollaborator()   { return collaborator; }

	public void setAdministrative(boolean v) { this.administrative = v; }
	public void setTechnical(boolean v)      { this.technical = v; }
	public void setCollaborator(boolean v)   { this.collaborator = v; }
}
