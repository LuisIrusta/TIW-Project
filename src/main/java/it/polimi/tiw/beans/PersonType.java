package it.polimi.tiw.beans;

public enum PersonType {
	ADMINISTRATIVE,
	TECHNICAL;
	
	public static PersonType fromDB(String str) {
		return valueOf(str.toUpperCase());
	}
}
