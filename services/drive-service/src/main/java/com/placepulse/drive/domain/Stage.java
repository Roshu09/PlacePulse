package com.placepulse.drive.domain;

/** The rounds an application moves through. REJECTED can happen at any point, and DEBARRED when a student edits their profile. */
public enum Stage {
    APPLIED,
    SHORTLISTED,
    TEST,
    INTERVIEW,
    OFFER,
    REJECTED,
    /** Set automatically when the student edits their registered profile; the placement team can reinstate. */
    DEBARRED
}
