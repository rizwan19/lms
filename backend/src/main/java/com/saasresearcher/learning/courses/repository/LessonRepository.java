package com.saasresearcher.learning.courses.repository;

import com.saasresearcher.learning.courses.entity.Lesson;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByCourseVersionIdOrderByPosition(UUID courseVersionId);

    /** Removes lessons one by one so their resource rows go with them. */
    void deleteByCourseVersionId(UUID courseVersionId);

    @Query("select distinct l.courseVersionId from Lesson l left join l.resourceAssetIds r where l.videoAssetId = :asset or r = :asset")
    Set<UUID> versionsUsingAsset(@Param("asset") UUID assetId);

    @Query("""
            select count(l) > 0 from Lesson l left join l.resourceAssetIds r, CourseVersion v, Course c
            where v.id = l.courseVersionId and c.id = v.courseId and c.authorId = :author and (l.videoAssetId = :asset or r = :asset)
            """)
    boolean authorUsesAsset(@Param("author") UUID authorId, @Param("asset") UUID assetId);
}
