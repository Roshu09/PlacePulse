package com.placepulse.drive;

import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;
import com.placepulse.drive.service.EligibilityChecker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityCheckerTest {

    /** No years means the drive is open to every batch. */
    private Drive drive(String minCgpa, Integer... years) {
        return new Drive("Acme", "SDE Intern", null, "Remote", null,
                new BigDecimal(minCgpa), Set.of(years), LocalDate.now().plusDays(7), UUID.randomUUID());
    }

    private StudentProfile profile(String cgpa, String batch) {
        return new StudentProfile(UUID.randomUUID(), "asha@example.com", "Asha Verma", "STUDENT",
                cgpa == null ? null : new BigDecimal(cgpa), "MCA", batch, true);
    }

    @Test
    void studentAboveTheMinimumIsEligible() {
        assertThat(EligibilityChecker.check(drive("7.0"), profile("8.2", "2027"))).isEmpty();
    }

    @Test
    void studentExactlyAtTheMinimumIsEligible() {
        assertThat(EligibilityChecker.check(drive("7.50"), profile("7.5", "2027"))).isEmpty();
    }

    @Test
    void studentBelowTheMinimumIsNotEligible() {
        Optional<String> reason = EligibilityChecker.check(drive("7.5"), profile("7.49", "2027"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("CGPA");
    }

    @Test
    void missingCgpaIsNotEligible() {
        Optional<String> reason = EligibilityChecker.check(drive("0.0"), profile(null, "2027"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("Add your CGPA");
    }

    @Test
    void batchRestrictedDriveRejectsOtherBatches() {
        Optional<String> reason = EligibilityChecker.check(drive("6.0", 2026), profile("8.0", "2027"));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("2026").contains("Yours is 2027");
    }

    @Test
    void anyOfSeveralBatchesIsAccepted() {
        Drive d = drive("6.0", 2026, 2027, 2028);
        assertThat(EligibilityChecker.check(d, profile("8.0", "2026"))).isEmpty();
        assertThat(EligibilityChecker.check(d, profile("8.0", "2028"))).isEmpty();
        assertThat(EligibilityChecker.check(d, profile("8.0", "2025"))).isPresent();
    }

    @Test
    void batchComparisonIgnoresSpaces() {
        assertThat(EligibilityChecker.check(drive("6.0", 2027), profile("8.0", " 2027 "))).isEmpty();
    }

    @Test
    void studentWithNoBatchIsRejectedFromBatchRestrictedDrive() {
        Optional<String> reason = EligibilityChecker.check(drive("6.0", 2027), profile("8.0", null));
        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("pass-out year");
    }

    @Test
    void driveWithoutBatchRestrictionAcceptsAnyBatch() {
        assertThat(EligibilityChecker.check(drive("6.0"), profile("8.0", null))).isEmpty();
    }

    @Test
    void passOutYearIsReadFromYearsAndOlderRanges() {
        assertThat(EligibilityChecker.passOutYear("2027")).isEqualTo(2027);
        assertThat(EligibilityChecker.passOutYear(" 2027 ")).isEqualTo(2027);
        assertThat(EligibilityChecker.passOutYear("2025-27")).isEqualTo(2027);
        assertThat(EligibilityChecker.passOutYear("2025 - 2027")).isEqualTo(2027);
        assertThat(EligibilityChecker.passOutYear("2025\u201327")).isEqualTo(2027);
        assertThat(EligibilityChecker.passOutYear("last batch")).isNull();
        assertThat(EligibilityChecker.passOutYear(null)).isNull();
    }

    @Test
    void unregisteredStudentMustRegisterFirst() {
        StudentProfile unregistered = new StudentProfile(UUID.randomUUID(), "a@example.com", "Asha", "STUDENT",
                new BigDecimal("9.0"), "MCA", "2027", false);
        assertThat(EligibilityChecker.check(drive("7.0"), unregistered)).hasValueSatisfying(
                reason -> assertThat(reason).contains("Register for placements"));
        StudentProfile unknown = new StudentProfile(UUID.randomUUID(), "a@example.com", "Asha", "STUDENT",
                new BigDecimal("9.0"), "MCA", "2027", null);
        assertThat(EligibilityChecker.check(drive("7.0"), unknown)).isPresent();
    }
}
