package com.pipeline.learn.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Logins are unique case-insensitively (index on lower(login)). */
    Optional<Account> findByLoginIgnoreCase(String login);

    /** Records a visit, at most once per throttle window: a no-op when the last visit is newer than {@code threshold}. */
    @Modifying
    @Transactional
    @Query("update Account a set a.lastSeenAt = :now where a.id = :id and (a.lastSeenAt is null or a.lastSeenAt < :threshold)")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);
}
