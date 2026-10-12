package com.placepulse.drive;

import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.service.EligibilityChecker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityCheckerTest {

    private Drive drive(String minCgpa, String eligibleBatch) {
        return new Drive("Acme", "SDE Intern", null, "Remote", null,
                new BigDecimal(minCgpa), eligibleBatch, LocalDate.now().plusDays(7), UUID.randomUUID());
    }

    private StudentProfile profile(String cgpa, String batch) {
        return new StudentProfile(UUID.randomUUID(), "asha@example.com", "Asha Verma", "STUDENT",
                cgpa == null ? null : new BigDecimal(cgpa), "MCA", batch);
    }

    @Test
    void studentAboveTheMinimumIsEligible() {
        assertThat(EligibilityChecker.check(drive("7.0", null), profile("8.2", "2025-27"))).isEmpty();
    }

    @Test
    void studentExactlyAtTheMinimumIsEligible() {
        assertThat(EligibilityChecker.check(drive("7.50", null), profile("7.5", "2025-27"))).isEmpty();
    }

    @Test
    void studentBelowTheMinimumIsNotEligible() {
        Optional<String> reason = EligibilityChecker.check(drive("7.5", null), profile("7.49", "2025-27"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("CGPA");
    }

    @Test
    void missingCgpaIsNotEligible() {
        Optional<String> reason = EligibilityChecker.check(drive("0.0", null), profile(null, "2025-27"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("Add your CGPA");
    }

    @Test
    void batchRestrictedDriveRejectsOtherBatches() {
        Optional<String> reason = EligibilityChecker.check(drive("6.0", "2024-26"), profile("8.0", "2025-27"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("2024-26");
    }

    @Test
    void batchComparisonIgnoresCaseAndSpaces() {
        assertThat(EligibilityChecker.check(drive("6.0", "2025-27"), profile("8.0", " 2025-27 "))).isEmpty();
    }

    @Test
    void studentWithNoBatchIsRejectedFromBatchRestrictedDrive() {
        assertThat(EligibilityChecker.check(drive("6.0", "2025-27"), profile("8.0", null))).isPresent();
    }

    @Test
    void driveWithoutBatchRestrictionAcceptsAnyBatch() {
        assertThat(EligibilityChecker.check(drive("6.0", null), profile("8.0", null))).isEmpty();
    }
}
