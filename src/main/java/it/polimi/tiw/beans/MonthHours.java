package it.polimi.tiw.beans;

public class MonthHours {

    private int month;
    private int plannedHours;
    private int workedHours;
    private boolean active;

    public MonthHours(int m) {
        this.month = m;
        this.plannedHours = 0;
        this.workedHours = 0;
        this.active = true;
    }

    public MonthHours(int mese, boolean active) {
        this.month = mese;
        this.plannedHours = 0;
        this.workedHours = 0;
        this.active = active;
    }

    public int getMonth() { return month; }
    public void setMonth(int m) { this.month = m; }

    public int getPlannedHours() { return plannedHours; }
    public void setPlannedHours(int planH) { this.plannedHours = planH; }

    public int getWorkedHours() { return workedHours; }
    public void setWorkedHours(int workedH) { this.workedHours = workedH; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}