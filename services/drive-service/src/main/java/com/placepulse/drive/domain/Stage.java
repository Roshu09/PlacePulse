package com.placepulse.drive.domain;

/** The rounds an application moves through. REJECTED can happen at any point. */
public enum Stage {
    APPLIED,
    SHORTLISTED,
    TEST,
    INTERVIEW,
    OFFER,
    REJECTED
}
