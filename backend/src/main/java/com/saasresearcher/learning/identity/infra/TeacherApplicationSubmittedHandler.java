package com.saasresearcher.learning.identity.infra;

import com.saasresearcher.learning.identity.entity.TeacherApplication;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.TeacherApplicationRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tells every active approved teacher about a new application (in-app, deduplicated, plus email). Already decided
 * applications are skipped; the review inbox lists the current pending set regardless of delivery.
 */
@Component
public class TeacherApplicationSubmittedHandler implements JobHandler {

    public static final String TYPE = "identity.teacher-application-submitted";

    private final TeacherApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final Notifications notifications;
    private final EmailSender email;
    private final TransactionTemplate transactions;

    TeacherApplicationSubmittedHandler(TeacherApplicationRepository applications, UserAccountRepository accounts, Notifications notifications,
            EmailSender email, TransactionTemplate transactions) {
        this.applications = applications;
        this.accounts = accounts;
        this.notifications = notifications;
        this.email = email;
        this.transactions = transactions;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(JobRequest job) {
        List<EmailMessage> messages = transactions.execute(status -> {
            TeacherApplication application = applications.findById(job.aggregateId()).filter(TeacherApplication::isPending).orElse(null);
            if (application == null) {
                return List.<EmailMessage>of();
            }
            UserAccount applicant = accounts.findById(application.getApplicantId()).orElseThrow();
            List<UserAccount> reviewers = accounts.findByIdIn(accounts.activeApprovedTeacherIds()).stream()
                    .filter(reviewer -> !reviewer.getId().equals(applicant.getId())).toList();
            reviewers.forEach(reviewer -> notifications.notify(new NotificationRequest(reviewer.getId(), "teacher-application.submitted",
                    "Teacher application from " + applicant.getDisplayName(), "Review it in the teacher applications inbox.",
                    "/workspace/teacher-applications", "teacher-application:" + application.getId() + ":" + reviewer.getId())));
            return reviewers.stream().map(reviewer -> new EmailMessage(reviewer.getEmail(), "New teacher application",
                    "Hello " + reviewer.getDisplayName() + ",\n\n" + applicant.getDisplayName() + " applied to teach. Any approved teacher can review"
                            + " the application in the workspace inbox.\n")).toList();
        });
        if (email.isConfigured()) {
            messages.forEach(email::send);
        }
    }
}
