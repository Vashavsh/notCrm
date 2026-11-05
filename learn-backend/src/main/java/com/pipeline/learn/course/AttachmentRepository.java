package com.pipeline.learn.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select new com.pipeline.learn.course.AttachmentRef(a.id, a.questionId) from Attachment a "
            + "where a.attemptId = :attemptId order by a.createdAt, a.id")
    List<AttachmentRef> refsOf(@Param("attemptId") Long attemptId);

    boolean existsByAttemptIdAndQuestionId(Long attemptId, String questionId);
}
