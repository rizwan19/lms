package com.saasresearcher.learning.identity.infra;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Tells the applicant the review result, once in-app and by email. */
@Component
public class TeacherApplicationDecidedHandler implements JobHandler {

    public static final String TYPE = "identity.teacher-application-decided";

    private final TeacherApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final Notifications notifications;
    private final EmailSender email;
    private final TransactionTemplate transactions;

    TeacherApplicationDecidedHandler(TeacherApplicationRepository applications, UserAccountRepository accounts, Notifications notifications,
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
        EmailMessage message = transactions.execute(status -> {
            TeacherApplication application = applications.findById(job.aggregateId()).orElseThrow();
            UserAccount applicant = accounts.findById(application.getApplicantId()).orElseThrow();
            boolean approved = application.getStatus() == ApplicationStatus.APPROVED;
            String title = approved ? "Your teacher application was approved" : "Your teacher application was not approved";
            String body = approved ? "You can now publish courses." : application.getReason() == null ? "" : application.getReason();
            notifications.notify(new NotificationRequest(applicant.getId(), "teacher-application.decided", title, body, "/me/teacher-application",
                    "teacher-application-decided:" + application.getId()));
            return new EmailMessage(applicant.getEmail(), title, "Hello " + applicant.getDisplayName() + ",\n\n" + title + ".\n" + body + "\n");
        });
        if (email.isConfigured()) {
            email.send(message);
        }
    }
}
