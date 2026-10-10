package com.apexinsight.model;

import java.time.LocalDate;

public record Goal(int id, int employeeId, String employeeName, String title, String description,
                   LocalDate dueDate, String status, int progress) implements ProgressTrackable, Searchable {
    @Override
    public String searchText() {
        return employeeName + " " + title + " " + description + " " + status;
    }

    @Override
    public String toString() {
        return title + " — " + employeeName;
    }
}
