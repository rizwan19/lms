package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.courses.entity.CourseRows.CourseRow;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Course authoring scope: administrators and assigned course teachers. Others see the course as missing. */
@Component
public class CourseAccess {

    private final CourseRepository courses;

    CourseAccess(CourseRepository courses) {
        this.courses = courses;
    }

    void requireAuthorRole(Actor actor) {
        if (!actor.isAdministrator() && !actor.has(Role.TEACHER)) {
            throw ApiException.accessDenied("Teacher or administrator role required");
        }
    }

    CourseRow requireCourse(UUID courseId, Actor actor) {
        requireAuthorRole(actor);
        CourseRow course = courses.findCourse(courseId).orElseThrow(ApiException::notFound);
        if (!actor.isAdministrator() && !courses.isTeacher(courseId, actor.userId())) {
            throw ApiException.notFound();
        }
        return course;
    }

    /** Same check with the course row locked; used by publication and draft creation. */
    CourseRow lockCourse(UUID courseId, Actor actor) {
        requireCourse(courseId, actor);
        return courses.lockCourse(courseId).orElseThrow(ApiException::notFound);
    }
}
