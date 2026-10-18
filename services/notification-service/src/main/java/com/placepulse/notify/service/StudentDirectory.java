package com.placepulse.notify.service;

import com.placepulse.notify.domain.KnownStudent;
import com.placepulse.notify.domain.KnownTpr;
import com.placepulse.notify.repository.KnownTprRepository;
import com.placepulse.notify.repository.KnownStudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Remembers which students and TPRs exist, so a "new drive" or a team alert can be sent to all of them. */
@Service
public class StudentDirectory {

    private final KnownStudentRepository students;
    private final KnownTprRepository tprs;

    public StudentDirectory(KnownStudentRepository students, KnownTprRepository tprs) {
        this.students = students;
        this.tprs = tprs;
    }

    public void remember(UUID studentId) {
        if (students.existsById(studentId)) {
            return;
        }
        try {
            students.saveAndFlush(new KnownStudent(studentId));
        } catch (DataIntegrityViolationException e) {
            // another request added the same student a moment ago: that is fine
        }
    }

    public void rememberTpr(UUID tprId) {
        if (tprs.existsById(tprId)) {
            return;
        }
        try {
            tprs.saveAndFlush(new KnownTpr(tprId));
        } catch (DataIntegrityViolationException e) {
            // added a moment ago by another request: fine
        }
    }

    public List<UUID> allTprIds() {
        return tprs.findAll().stream().map(KnownTpr::getId).toList();
    }

    public List<UUID> allIds() {
        return students.findAll().stream().map(KnownStudent::getId).toList();
    }
}
