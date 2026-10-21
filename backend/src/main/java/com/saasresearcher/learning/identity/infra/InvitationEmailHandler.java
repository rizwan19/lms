package com.saasresearcher.learning.identity.infra;

import com.saasresearcher.learning.identity.entity.Invitation;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.InvitationRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InvitationEmailHandler extends TokenEmailHandler<Invitation> {

    public static final String TYPE = "identity.invitation-email";

    InvitationEmailHandler(InvitationRepository invitations, UserAccountRepository accounts, EmailSender email,
            TransactionTemplate transactions, Clock clock, @Value("${learning.public-origin}") String publicOrigin) {
        super(invitations, accounts, email, transactions, clock, publicOrigin);
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    String path() {
        return "/accept-invitation";
    }

    @Override
    EmailMessage message(UserAccount account, String link) {
        return new EmailMessage(account.email(), "Your learning portal account",
                "Hello " + account.displayName() + ",\n\nAn account was created for you with username " + account.username()
                        + ".\nSet your password within 72 hours using this link:\n\n" + link + "\n");
    }
}
