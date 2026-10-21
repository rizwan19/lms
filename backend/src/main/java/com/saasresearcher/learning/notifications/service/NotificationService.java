package com.saasresearcher.learning.notifications.service;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService implements Notifications {

    private final JdbcClient jdbc;
    private final Clock clock;

    NotificationService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void notify(NotificationRequest request) {
        jdbc.sql("""
                insert into notifications (id, recipient_id, type, title, body, link, deduplication_key, created_at)
                values (:id, :recipient, :type, :title, :body, :link, :key, :now)
                on conflict (deduplication_key) do nothing
                """)
                .param("id", UUID.randomUUID())
                .param("recipient", request.recipientId())
                .param("type", request.type())
                .param("title", request.title())
                .param("body", request.body() == null ? "" : request.body())
                .param("link", request.link())
                .param("key", request.deduplicationKey())
                .param("now", Timestamp.from(clock.instant()))
                .update();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(UUID recipientId, boolean unreadOnly, PageQuery query) {
        String filter = "recipient_id = :recipient" + (unreadOnly ? " and read_at is null" : "");
        List<NotificationView> items = jdbc.sql("select id, type, title, body, link, created_at, read_at from notifications where " + filter
                        + " order by created_at desc, id limit :limit offset :offset")
                .param("recipient", recipientId)
                .param("limit", query.size())
                .param("offset", query.offset())
                .query((rs, row) -> new NotificationView(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"),
                        rs.getString("body"), rs.getString("link"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()))
                .list();
        long total = jdbc.sql("select count(*) from notifications where " + filter).param("recipient", recipientId).query(Long.class).single();
        return PageResponse.of(items, query, total);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID recipientId) {
        int updated = jdbc.sql("update notifications set read_at = coalesce(read_at, :now) where id = :id and recipient_id = :recipient")
                .param("now", Timestamp.from(clock.instant()))
                .param("id", notificationId)
                .param("recipient", recipientId)
                .update();
        if (updated == 0) {
            throw ApiException.notFound();
        }
    }

    public record NotificationView(UUID id, String type, String title, String body, String link, Instant createdAt, Instant readAt) {
    }
}
