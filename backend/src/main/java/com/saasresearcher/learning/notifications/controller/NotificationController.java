package com.saasresearcher.learning.notifications.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.notifications.service.NotificationService.NotificationView;
import com.saasresearcher.learning.notifications.service.NotificationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Own in-app notifications only. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    PageResponse<NotificationView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return notifications.list(actor.userId(), unreadOnly, PageQuery.of(page, size));
    }

    @PutMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID id, Actor actor) {
        notifications.markRead(id, actor.userId());
    }
}
