package com.apexinsight.model;

public record ManagerInsights(Double recentAverageRating, int recentReviewCount,
                              Double previousAverageRating, int previousReviewCount,
                              int atRiskOrOverdueGoalCount) {
}
