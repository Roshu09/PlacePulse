package com.placepulse.drive.service;

import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;

import java.util.Optional;

/**
 * The rules for who may apply to a drive. Kept free of Spring and the database so every rule
 * can be tested with plain objects.
 */
public final class EligibilityChecker {

    private EligibilityChecker() {
    }

    /** Returns why the student cannot apply, or empty if they can. */
    public static Optional<String> check(Drive drive, StudentProfile profile) {
        if (profile.cgpa() == null) {
            return Optional.of("Add your CGPA to your profile before applying.");
        }
        // a student whose CGPA equals the minimum is eligible
        if (profile.cgpa().compareTo(drive.getMinCgpa()) < 0) {
            return Optional.of("This drive needs a CGPA of " + drive.getMinCgpa().toPlainString()
                    + " or higher. Yours is " + profile.cgpa().toPlainString() + ".");
        }
        String requiredBatch = drive.getEligibleBatch();
        if (requiredBatch != null && !requiredBatch.isBlank()) {
            String studentBatch = profile.batch();
            if (studentBatch == null || !requiredBatch.trim().equalsIgnoreCase(studentBatch.trim())) {
                return Optional.of("This drive is open to the " + requiredBatch + " batch only.");
            }
        }
        return Optional.empty();
    }
}
