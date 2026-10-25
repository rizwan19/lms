package com.saasresearcher.learning.notifications.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.EmailSender;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/** Sends transactional email through the Brevo HTTP API; a non-2xx response throws, so the job retries. */
@Component
public class BrevoEmailSender implements EmailSender {

    static final String SEND_URL = "https://api.brevo.com/v3/smtp/email";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;
    private final String apiKey;
    private final Address sender;

    @Autowired
    BrevoEmailSender(@Value("${learning.mail.brevo-api-key:}") String apiKey, @Value("${learning.mail.from:}") String from) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey, from);
    }

    BrevoEmailSender(RestClient.Builder builder, String apiKey, String from) {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.sender = from.isBlank() ? null : Address.parse(from);
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requests.setReadTimeout(TIMEOUT);
        return requests;
    }

    /** Brevo rejects a message without a sender, so both the API key and the from address are required. */
    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && sender != null;
    }

    @Override
    public void send(EmailMessage message) {
        if (!isConfigured()) {
            throw new IllegalStateException("Brevo email is not configured");
        }
        String html = HtmlUtils.htmlEscape(message.body()).replace("\n", "<br>\n");
        client.post()
                .uri(SEND_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new SendRequest(sender, List.of(new Address(null, message.to())), message.subject(), html, message.body()))
                .retrieve()
                .toBodilessEntity();
    }

    record SendRequest(Address sender, List<Address> to, String subject, String htmlContent, String textContent) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Address(String name, String email) {

        /** Accepts {@code no-reply@example.org} or {@code Learning Portal <no-reply@example.org>}. */
        static Address parse(String value) {
            int open = value.lastIndexOf('<');
            if (open < 0 || !value.endsWith(">")) {
                return new Address(null, value.trim());
            }
            String name = value.substring(0, open).trim();
            return new Address(name.isEmpty() ? null : name, value.substring(open + 1, value.length() - 1).trim());
        }
    }
}
