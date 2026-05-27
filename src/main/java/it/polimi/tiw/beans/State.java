package it.polimi.tiw.beans;

public enum State {
	CREATED,
	ASSIGNED,
	CONCLUDED;
	
	public static State fromDB(String str) {
		return valueOf(str.toUpperCase());
	}
}
