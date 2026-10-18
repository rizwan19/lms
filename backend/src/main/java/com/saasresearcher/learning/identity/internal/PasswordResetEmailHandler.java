package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class PasswordResetEmailHandler extends TokenEmailHandler<PasswordResetToken> {

    static final String TYPE = "identity.password-reset-email";

    PasswordResetEmailHandler(PasswordResetTokenRepository resetTokens, UserAccountRepository accounts, EmailSender email,
            TransactionTemplate transactions, Clock clock, @Value("${learning.public-origin}") String publicOrigin) {
        super(resetTokens, accounts, email, transactions, clock, publicOrigin);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    String path() {
        return "/reset-password";
    }

    @Override
    EmailMessage message(UserAccount account, String link) {
        return new EmailMessage(account.email(), "Reset your learning portal password",
                "Hello " + account.displayName() + ",\n\nUse this link within 30 minutes to choose a new password:\n\n" + link
                        + "\n\nIf you did not ask for this, you can ignore this email.\n");
    }
}
