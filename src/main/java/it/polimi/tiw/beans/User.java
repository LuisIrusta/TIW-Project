package it.polimi.tiw.beans;

public class User {
	
	private int id;
	private String username;
	private String firstName;
	private String lastName;
	private String photo;
	private PersonType personType; //ADMINISTRATIVE or TECHNICAL
	
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
	
	public boolean isAdministrative() {
		return personType.equals(PersonType.ADMINISTRATIVE);
	}
	
	public boolean isTechnical() {
		return personType.equals(PersonType.TECHNICAL);
	}
	
	public boolean isCollaborator() {
		if(personType.equals(PersonType.COLLABORATOR) || personType.equals(PersonType.TECHNICAL)) {
			return true;
		}
		
	return false;
}
}
