package com.placepulse.drive.service;

import com.placepulse.drive.client.StudentProfile;
import com.placepulse.drive.domain.Drive;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        Set<Integer> allowed = drive.getEligibleBatches();
        if (!allowed.isEmpty()) {
            String list = allowed.stream().map(String::valueOf).collect(Collectors.joining(", "));
            Integer year = passOutYear(profile.batch());
            if (year == null) {
                return Optional.of("Add your pass-out year to your profile. This drive is open to the " + list + " batch only.");
            }
            if (!allowed.contains(year)) {
                return Optional.of("This drive is open to the " + list + " batch only. Yours is " + year + ".");
            }
        }
        return Optional.empty();
    }

    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern RANGE = Pattern.compile("^(\\d{4})\\s*[-\u2013/]\\s*(\\d{2}|\\d{4})$");

    /**
     * The pass-out year of a profile batch: "2027" is 2027, and an older "2025-27" or "2025-2027" is also 2027.
     * Returns null when nothing sensible can be read from it.
     */
    public static Integer passOutYear(String batch) {
        if (batch == null) {
            return null;
        }
        String text = batch.trim();
        Matcher single = YEAR.matcher(text);
        if (single.matches()) {
            return Integer.valueOf(single.group(1));
        }
        Matcher range = RANGE.matcher(text);
        if (range.matches()) {
            String end = range.group(2);
            return end.length() == 2 ? 2000 + Integer.parseInt(end) : Integer.valueOf(end);
        }
        return null;
    }
}
