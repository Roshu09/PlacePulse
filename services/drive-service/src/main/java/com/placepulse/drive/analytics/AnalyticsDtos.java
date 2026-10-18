package com.placepulse.drive.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Totals(
            int drives,
            int openDrives,
            long applications,
            long studentsApplied,
            long studentsPlaced,
            long offers,
            /** Students with at least one offer, as a percentage of students who applied anywhere. */
            double placementRate,
            Double averageCgpaApplicants,
            Double averageCgpaPlaced,
            /** Offer-weighted average of the packages of the drives that gave offers, in LPA. */
            Double averagePackageLpa,
            Double highestPackageLpa) {
    }

    public record DriveStat(
            UUID driveId,
            String company,
            String roleTitle,
            String status,
            LocalDate deadline,
            long applicants,
            long shortlisted,
            long offers,
            /** Offers as a percentage of applicants. */
            double conversion) {
    }

    public record DayCount(LocalDate date, long applications) {
    }

    public record Analytics(
            Totals totals,
            /** Current number of applications at each stage, in pipeline order. */
            Map<String, Long> funnel,
            List<DriveStat> drives,
            /** The last 14 days, zero-filled, oldest first. */
            List<DayCount> timeline,
            Instant generatedAt) {
    }
}
