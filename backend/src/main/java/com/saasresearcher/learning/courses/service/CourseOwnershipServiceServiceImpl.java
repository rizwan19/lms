package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.courses.entity.Course;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseOwnershipServiceServiceImpl implements CourseOwnershipService {

    private final CourseRepository courseRepository;
    private final RowLocks locks;

    @Override
    @Transactional(readOnly = true)
    public CourseOwner requireOwner(UUID courseId) {
        return courseRepository.findById(courseId).map(Course::toOwner).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseOwner requireAuthor(UUID courseId, Actor actor) {
        actor.requireVerified();
        CourseOwner owner = requireOwner(courseId);
        if (!owner.authorId().equals(actor.userId())) {
            throw ApiException.notFound();
        }
        return owner;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseOwner requirePublishAuthor(UUID courseId, Actor actor) {
        CourseOwner owner = requireAuthor(courseId, actor);
        actor.requireApprovedTeacher();
        return owner;
    }

    @Override
    @Transactional
    public CourseOwner lock(UUID courseId) {
        return courseRepository.findById(courseId).map(locks::forUpdate).map(Course::toOwner).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional
    public CourseOwner lockShared(UUID courseId) {
        return courseRepository.findById(courseId).map(locks::forShare).map(Course::toOwner).orElseThrow(ApiException::notFound);
    }
}
