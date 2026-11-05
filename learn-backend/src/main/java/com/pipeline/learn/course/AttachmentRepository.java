package com.pipeline.learn.course;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("select new com.pipeline.learn.course.AttachmentRef(a.id, a.questionId) from Attachment a "
            + "where a.attemptId = :attemptId order by a.createdAt, a.id")
    List<AttachmentRef> refsOf(@Param("attemptId") Long attemptId);

    boolean existsByAttemptIdAndQuestionId(Long attemptId, String questionId);

    /** The attachment, but only if it belongs to one of this student's attempts. */
    @Query("select a from Attachment a where a.id = :id and a.attemptId in ("
            + "select t.id from Attempt t where t.progressId in (select p.id from LessonProgress p where p.accountId = :accountId))")
    Optional<Attachment> findOwned(@Param("id") Long id, @Param("accountId") UUID accountId);

    @Modifying
    @Query("delete from Attachment a where a.attemptId = :attemptId and a.questionId = :questionId and a.id <> :keep")
    void deleteOthers(@Param("attemptId") Long attemptId, @Param("questionId") String questionId, @Param("keep") Long keep);
}
