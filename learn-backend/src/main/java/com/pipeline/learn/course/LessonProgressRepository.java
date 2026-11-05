package com.pipeline.learn.course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    List<LessonProgress> findByAccountId(UUID accountId);

    Optional<LessonProgress> findByAccountIdAndLessonId(UUID accountId, String lessonId);
}
