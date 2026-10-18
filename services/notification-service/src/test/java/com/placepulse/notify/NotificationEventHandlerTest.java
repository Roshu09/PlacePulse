package com.placepulse.notify;

import com.placepulse.notify.domain.KnownStudent;
import com.placepulse.notify.domain.Notification;
import com.placepulse.notify.events.InvalidEventException;
import com.placepulse.notify.events.NotificationEventHandler;
import com.placepulse.notify.repository.KnownStudentRepository;
import com.placepulse.notify.repository.KnownTprRepository;
import com.placepulse.notify.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The JSON below is written out by hand on purpose: it is the contract with drive-service. If
 * drive-service renames a field, its own tests and this one should be updated together.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationEventHandlerTest {

    @Autowired NotificationEventHandler handler;
    @Autowired NotificationRepository notifications;
    @Autowired KnownStudentRepository students;
    @Autowired KnownTprRepository tprs;

    private final UUID asha = UUID.randomUUID();
    private final UUID ravi = UUID.randomUUID();
    private final UUID tpr = UUID.randomUUID();
    private final UUID driveId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        notifications.deleteAll();
        students.deleteAll();
        tprs.deleteAll();
    }

    private static String event(UUID eventId, String type, String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\","
                + "\"occurredAt\":\"2026-10-12T10:00:00Z\",\"payload\":" + payload + "}";
    }

    private String stageChanged(UUID student, String from, String to) {
        return "{\"applicationId\":\"" + UUID.randomUUID() + "\",\"driveId\":\"" + driveId + "\","
                + "\"company\":\"Kickdrum\",\"roleTitle\":\"Software Developer\","
                + "\"studentId\":\"" + student + "\",\"studentName\":\"Asha Verma\","
                + "\"fromStage\":\"" + from + "\",\"toStage\":\"" + to + "\"}";
    }

    private String driveCreated(String batch) {
        String b = batch == null ? "null" : "\"" + batch + "\"";
        return "{\"driveId\":\"" + driveId + "\",\"company\":\"Acme\",\"roleTitle\":\"SDE Intern\","
                + "\"minCgpa\":7.5,\"eligibleBatch\":" + b + ",\"deadline\":\"2026-11-05\",\"createdBy\":\"" + tpr + "\"}";
    }

    private String applicationSubmitted(UUID student) {
        return "{\"applicationId\":\"" + UUID.randomUUID() + "\",\"driveId\":\"" + driveId + "\","
                + "\"company\":\"Acme\",\"roleTitle\":\"SDE Intern\",\"studentId\":\"" + student + "\","
                + "\"studentName\":\"Asha Verma\",\"driveCreatedBy\":\"" + tpr + "\"}";
    }

    @Test
    void stageChangeNotifiesTheStudentInPlainWords() {
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "APPLIED", "SHORTLISTED")));

        List<Notification> mine = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).getTitle()).isEqualTo("You're shortlisted at Kickdrum");
        assertThat(mine.get(0).getBody()).isEqualTo("Software Developer: moved from Applied to Shortlisted");
        assertThat(mine.get(0).getLink()).isEqualTo("/applications");
        assertThat(mine.get(0).getReadAt()).isNull();
    }

    @Test
    void eachStageHasItsOwnTitle() {
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "SHORTLISTED", "INTERVIEW")));
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "INTERVIEW", "OFFER")));
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "OFFER", "REJECTED")));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha))
                .extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("Interview round at Kickdrum", "Offer from Kickdrum", "Update on your Kickdrum application");
    }

    @Test
    void aStageThisVersionDoesNotKnowStillProducesAReadableMessage() {
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "APPLIED", "GROUP_DISCUSSION")));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0).getTitle())
                .isEqualTo("Your Kickdrum application moved to GROUP_DISCUSSION");
    }

    @Test
    void newApplicationNotifiesTheTprWhoOwnsTheDrive() {
        handler.handle(event(UUID.randomUUID(), "application.submitted", applicationSubmitted(asha)));

        List<Notification> forTpr = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(tpr);
        assertThat(forTpr).hasSize(1);
        assertThat(forTpr.get(0).getTitle()).isEqualTo("New application for Acme");
        assertThat(forTpr.get(0).getBody()).isEqualTo("Asha Verma applied for SDE Intern");
        assertThat(forTpr.get(0).getLink()).isEqualTo("/drives/" + driveId);
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha)).isEmpty();
    }

    @Test
    void newDriveReachesEveryKnownStudentAndNobodyElse() {
        students.save(new KnownStudent(asha));
        students.save(new KnownStudent(ravi));

        handler.handle(event(UUID.randomUUID(), "drive.created", driveCreated("2025-27")));

        assertThat(notifications.count()).isEqualTo(2);
        Notification n = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0);
        assertThat(n.getTitle()).isEqualTo("New drive: Acme");
        assertThat(n.getBody()).isEqualTo("SDE Intern · CGPA 7.50+ · batch 2025-27 · apply by 5 Nov 2026");
        assertThat(n.getLink()).isEqualTo("/drives");
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(tpr)).isEmpty();
    }

    @Test
    void aDriveForAnyBatchLeavesTheBatchOutOfTheMessage() {
        students.save(new KnownStudent(asha));

        handler.handle(event(UUID.randomUUID(), "drive.created", driveCreated(null)));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0).getBody())
                .isEqualTo("SDE Intern · CGPA 7.50+ · apply by 5 Nov 2026");
    }

    @Test
    void aStudentWhoAppliedBecomesReachableForLaterDrives() {
        handler.handle(event(UUID.randomUUID(), "application.submitted", applicationSubmitted(asha)));
        handler.handle(event(UUID.randomUUID(), "drive.created", driveCreated(null)));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha))
                .extracting(Notification::getType)
                .containsExactly("drive.created");
    }

    @Test
    void theSameEventDeliveredTwiceNotifiesOnce() {
        students.save(new KnownStudent(asha));
        String message = event(UUID.randomUUID(), "drive.created", driveCreated(null));

        handler.handle(message);
        handler.handle(message);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aRedeliveryOnlyFillsInWhoWasMissed() {
        students.save(new KnownStudent(asha));
        UUID eventId = UUID.randomUUID();
        String message = event(eventId, "drive.created", driveCreated(null));
        handler.handle(message);

        // ravi becomes known, then the same event arrives again (e.g. after a consumer restart)
        students.save(new KnownStudent(ravi));
        handler.handle(message);

        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test
    void anUnknownEventTypeIsIgnored() {
        handler.handle(event(UUID.randomUUID(), "drive.archived", "{}"));
        assertThat(notifications.count()).isZero();
    }

    @Test
    void brokenJsonIsRejectedAsInvalid() {
        assertThatThrownBy(() -> handler.handle("{not json")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> handler.handle("[1,2]")).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void aMissingFieldIsRejectedAsInvalid() {
        String noCompany = "{\"applicationId\":\"" + UUID.randomUUID() + "\",\"studentId\":\"" + asha + "\","
                + "\"roleTitle\":\"SDE\",\"fromStage\":\"APPLIED\",\"toStage\":\"OFFER\"}";
        assertThatThrownBy(() -> handler.handle(event(UUID.randomUUID(), "application.stage-changed", noCompany)))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("company");
    }

    @Test
    void anIdThatIsNotAUuidIsRejectedAsInvalid() {
        assertThatThrownBy(() -> handler.handle(event(UUID.randomUUID(), "application.stage-changed",
                stageChanged(asha, "APPLIED", "OFFER").replace(asha.toString(), "not-a-uuid"))))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void aDriveForSeveralBatchesListsThemAll() {
        students.save(new KnownStudent(asha));
        String payload = "{\"driveId\":\"" + driveId + "\",\"company\":\"Acme\",\"roleTitle\":\"SDE Intern\","
                + "\"minCgpa\":7.5,\"eligibleBatches\":[2026,2027],\"deadline\":\"2026-11-05\",\"createdBy\":\"" + tpr + "\"}";

        handler.handle(event(UUID.randomUUID(), "drive.created", payload));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0).getBody())
                .isEqualTo("SDE Intern · CGPA 7.50+ · batch 2026, 2027 · apply by 5 Nov 2026");
    }

    @Test
    void anEditedDriveTellsKnownStudentsWhatChanged() {
        students.save(new KnownStudent(asha));
        students.save(new KnownStudent(ravi));
        String payload = "{\"driveId\":\"" + driveId + "\",\"company\":\"Acme\",\"roleTitle\":\"SDE Intern\","
                + "\"changes\":[\"minimum CGPA\",\"last date to apply\"],\"createdBy\":\"" + tpr + "\"}";

        handler.handle(event(UUID.randomUUID(), "drive.updated", payload));

        assertThat(notifications.count()).isEqualTo(2);
        Notification n = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0);
        assertThat(n.getTitle()).isEqualTo("Drive updated: Acme");
        assertThat(n.getBody()).isEqualTo("SDE Intern · changed: minimum CGPA, last date to apply");
        assertThat(n.getLink()).isEqualTo("/drives");
    }

    @Test
    void aRemovedApplicationTellsOnlyThatStudent() {
        String payload = "{\"applicationId\":\"" + UUID.randomUUID() + "\",\"driveId\":\"" + driveId + "\","
                + "\"company\":\"Acme\",\"roleTitle\":\"SDE Intern\",\"studentId\":\"" + asha + "\",\"studentName\":\"Asha Verma\"}";

        handler.handle(event(UUID.randomUUID(), "application.removed", payload));

        List<Notification> forAsha = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha);
        assertThat(forAsha).hasSize(1);
        assertThat(forAsha.get(0).getTitle()).isEqualTo("Your Acme application was removed");
        assertThat(forAsha.get(0).getBody()).contains("apply again");
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void debarringTellsTheStudentWhatHappened() {
        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "INTERVIEW", "DEBARRED")));

        Notification n = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha).get(0);
        assertThat(n.getTitle()).isEqualTo("You were debarred from Kickdrum");
        assertThat(n.getBody()).contains("edited your profile").contains("reinstate");
    }

    @Test
    void aStudentWhoRegisteredBecomesReachableWithoutANotification() {
        UUID newcomer = UUID.randomUUID();
        handler.handle(event(UUID.randomUUID(), "student.registered",
                "{\"studentId\":\"" + newcomer + "\",\"studentName\":\"New Kid\",\"email\":\"n@example.com\"}"));

        handler.handle(event(UUID.randomUUID(), "drive.created", driveCreated(null)));
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(newcomer)).hasSize(1);
    }

    @Test
    void everyKnownTprHearsAboutANewApplication() {
        UUID otherTpr = UUID.randomUUID();
        directoryTprs(otherTpr);

        handler.handle(event(UUID.randomUUID(), "application.submitted", applicationSubmitted(asha)));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(tpr)).hasSize(1);
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(otherTpr)).hasSize(1);
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha)).isEmpty();
    }

    @Test
    void debarringAlertsTheStudentAndEveryTpr() {
        UUID otherTpr = UUID.randomUUID();
        directoryTprs(otherTpr, tpr);

        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "TEST", "DEBARRED")));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(asha)).hasSize(1);
        List<Notification> forTeam = notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(otherTpr);
        assertThat(forTeam).hasSize(1);
        assertThat(forTeam.get(0).getTitle()).isEqualTo("Asha Verma was debarred from Kickdrum");
        assertThat(forTeam.get(0).getLink()).isEqualTo("/drives/" + driveId);
        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(tpr)).hasSize(1);
    }

    @Test
    void anOrdinaryStageChangeDoesNotBotherTheTeam() {
        UUID otherTpr = UUID.randomUUID();
        directoryTprs(otherTpr);

        handler.handle(event(UUID.randomUUID(), "application.stage-changed", stageChanged(asha, "APPLIED", "SHORTLISTED")));

        assertThat(notifications.findTop30ByRecipientIdOrderByCreatedAtDesc(otherTpr)).isEmpty();
    }

    private void directoryTprs(UUID... ids) {
        for (UUID id : ids) {
            tprs.save(new com.placepulse.notify.domain.KnownTpr(id));
        }
    }
}
