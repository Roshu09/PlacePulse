package com.placepulse.auth.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything a student can edit on their profile. A record, so two profiles can be compared with equals()
 * to tell whether a save actually changed anything.
 */
public record ProfileData(
        // required to register for placements
        String phone,
        String course,
        String batch,
        BigDecimal cgpa,
        BigDecimal tenthPercent,
        BigDecimal twelfthPercent,
        String resumeUrl,
        // optional
        LocalDate dateOfBirth,
        String gender,
        String city,
        String graduationDegree,
        BigDecimal graduationPercent,
        String graduationUniversity,
        Integer activeBacklogs,
        String githubUrl,
        String linkedinUrl,
        String portfolioUrl,
        String skills,
        String projects,
        String certifications) {

    /** The keys of required fields that are still empty, in the order the form shows them. */
    public List<String> missingRequired() {
        List<String> missing = new ArrayList<>();
        if (isBlank(phone)) missing.add("phone");
        if (isBlank(course)) missing.add("course");
        if (isBlank(batch)) missing.add("batch");
        if (cgpa == null) missing.add("cgpa");
        if (tenthPercent == null) missing.add("tenthPercent");
        if (twelfthPercent == null) missing.add("twelfthPercent");
        if (isBlank(resumeUrl)) missing.add("resumeUrl");
        return missing;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
