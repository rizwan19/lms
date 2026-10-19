package com.saasresearcher.learning.notifications.internal;

import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP delivery via spring.mail.*; unconfigured when the mail host is unset or blank. */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
            @Value("${learning.mail.from:}") String from) {
        this.mailSender = host.isBlank() ? null : mailSender.getIfAvailable();
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return mailSender != null;
    }

    @Override
    public void send(EmailMessage message) {
        if (mailSender == null) {
            throw new IllegalStateException("SMTP is not configured");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        if (!from.isBlank()) {
            mail.setFrom(from);
        }
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
