package com.apexinsight.model;

import java.time.LocalDateTime;

public record Evaluation(int id, int employeeId, String employeeName, String managerName,
                         int rating, String feedback, LocalDateTime createdAt) implements Searchable {
    @Override
    public String searchText() {
        return employeeName + " " + managerName + " " + feedback + " " + rating;
    }
}
