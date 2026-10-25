package com.saasresearcher.learning.courses.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface CourseOwnershipService {

    CourseOwner requireOwner(UUID courseId);

    CourseOwner requireAuthor(UUID courseId, Actor actor);

    CourseOwner requirePublishAuthor(UUID courseId, Actor actor);

    CourseOwner lock(UUID courseId);

    CourseOwner lockShared(UUID courseId);
}
