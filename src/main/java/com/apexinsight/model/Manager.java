package com.apexinsight.model;

public final class Manager extends User {
    public Manager(int id, String username, String fullName) {
        super(id, username, fullName, Role.MANAGER);
    }

    @Override
    public String getRoleLabel() {
        return "Manager";
    }
}
