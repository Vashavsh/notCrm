package com.pipeline.learn.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Locks the student's row for the rest of the transaction. Every write to a student's progress takes this
     * lock first, so two tabs (or a double click) cannot create two progress rows or two current attempts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);

    /** Logins are unique case-insensitively (index on lower(login)). */
    Optional<Account> findByLoginIgnoreCase(String login);

    /** Records a visit, at most once per throttle window: a no-op when the last visit is newer than {@code threshold}. */
    @Modifying
    @Transactional
    @Query("update Account a set a.lastSeenAt = :now where a.id = :id and (a.lastSeenAt is null or a.lastSeenAt < :threshold)")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);
}
