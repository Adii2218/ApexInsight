package com.apexinsight.model;

public final class Employee extends User implements Searchable {
    private final String department;
    private final String jobTitle;

    public Employee(int id, String username, String fullName, String department, String jobTitle) {
        super(id, username, fullName, Role.EMPLOYEE);
        this.department = department;
        this.jobTitle = jobTitle;
    }

    public String getDepartment() {
        return department;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    @Override
    public String searchText() {
        return getFullName() + " " + getUsername() + " " + department + " " + jobTitle;
    }

    @Override
    public String getRoleLabel() {
        return "Employee";
    }

    @Override
    public String toString() {
        return getFullName() + " · " + jobTitle;
    }
}
