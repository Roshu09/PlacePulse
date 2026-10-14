package com.placepulse.notify.service;

import com.placepulse.notify.domain.KnownStudent;
import com.placepulse.notify.repository.KnownStudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Remembers which students exist, so a "new drive" can be sent to all of them. */
@Service
public class StudentDirectory {

    private final KnownStudentRepository students;

    public StudentDirectory(KnownStudentRepository students) {
        this.students = students;
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

    public List<UUID> allIds() {
        return students.findAll().stream().map(KnownStudent::getId).toList();
    }
}
