package com.pipeline.crm.learn;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pipeline.crm.audit.AuditService;
import com.pipeline.crm.common.exception.ConflictException;
import com.pipeline.crm.security.CurrentUser;
import com.pipeline.crm.student.Student;
import com.pipeline.crm.student.StudentService;

import lombok.RequiredArgsConstructor;

/**
 * Course-site access of a student, from the student's card: issue a login, reset the password, see the progress.
 * Allowed for an admin and for the student's own curator (anyone else gets "not found", as everywhere in the CRM).
 */
@Service
@RequiredArgsConstructor
public class LearnAccessService {

    private final StudentService students;
    private final LearnBridge bridge;
    private final StudentLearnAccessRepository access;
    private final PasswordEncoder encoder;
    private final LoginGenerator logins;
    private final PasswordGenerator passwords;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public LearnAccessDto get(UUID studentId, CurrentUser user) {
        students.requireAccessible(studentId, user);
        return view(studentId);
    }

    @Transactional
    public LearnAccessDto issue(UUID studentId, CurrentUser user) {
        Student student = students.requireAccessible(studentId, user);
        bridge.requireAvailable();
        if (bridge.account(studentId).isPresent()) {
            throw new ConflictException("Access has already been issued");
        }
        String login = logins.generate(student.getTelegramUsername(), student.getFullName(), bridge::loginTaken);
        String password = passwords.generate();
        String hash = encoder.encode(password);
        bridge.insertAccount(studentId, login, student.getFullName(), hash);
        save(studentId, login, password, hash, user.id(), Instant.now(), true);
        // The password itself never goes into the audit log.
        audit.log(user.id(), "student_learn_access", studentId, "issue", null, Map.of("login", login));
        return view(studentId);
    }

    @Transactional
    public LearnAccessDto resetPassword(UUID studentId, CurrentUser user) {
        students.requireAccessible(studentId, user);
        bridge.requireAvailable();
        LearnBridge.Account account = bridge.account(studentId).orElseThrow(() -> new ConflictException("Access has not been issued yet"));
        String password = passwords.generate();
        String hash = encoder.encode(password);
        bridge.resetPassword(studentId, hash);
        save(studentId, account.login(), password, hash, user.id(), Instant.now(), false);
        audit.log(user.id(), "student_learn_access", studentId, "reset_password", null, Map.of("login", account.login()));
        return view(studentId);
    }

    private void save(UUID studentId, String login, String password, String hash, UUID actorId, Instant now, boolean isNew) {
        StudentLearnAccess row = isNew ? new StudentLearnAccess() : access.findById(studentId).orElseGet(StudentLearnAccess::new);
        if (row.getStudentId() == null) {
            row.setStudentId(studentId);
            row.setIssuedAt(now);
        }
        row.setLogin(login);
        row.setPasswordPlain(password);
        row.setIssuedHash(hash);
        row.setIssuedById(actorId);
        row.setUpdatedAt(now);
        access.save(row);
    }

    private LearnAccessDto view(UUID studentId) {
        if (!bridge.available()) {
            return new LearnAccessDto(false, null, null);
        }
        LearnBridge.Account account = bridge.account(studentId).orElse(null);
        if (account == null) {
            return new LearnAccessDto(true, null, null);
        }
        StudentLearnAccess issued = access.findById(studentId).orElse(null);
        boolean changed = issued != null && !issued.getIssuedHash().equals(account.passwordHash());
        String password = issued == null || changed ? null : issued.getPasswordPlain();
        LearnAccessDto.AccountDto dto = new LearnAccessDto.AccountDto(account.login(), password, changed, account.status(),
                issued != null ? issued.getIssuedAt() : toInstant(account.createdAt()), toInstant(account.lastSeenAt()));
        return new LearnAccessDto(true, dto, progress(bridge.progress(studentId)));
    }

    private static LearnAccessDto.ProgressDto progress(List<LearnBridge.LessonRow> rows) {
        Map<String, List<LearnBridge.LessonRow>> byBlock = new LinkedHashMap<>();
        rows.forEach(r -> byBlock.computeIfAbsent(r.moduleId(), k -> new ArrayList<>()).add(r));

        List<LearnAccessDto.BlockDto> blocks = new ArrayList<>();
        int completed = 0;
        OffsetDateTime first = null;
        OffsetDateTime last = null;
        for (List<LearnBridge.LessonRow> block : byBlock.values()) {
            List<LearnAccessDto.LessonDto> lessons = new ArrayList<>();
            int done = 0;
            for (LearnBridge.LessonRow r : block) {
                lessons.add(new LearnAccessDto.LessonDto(r.lessonId(), r.lessonTitle(), r.lessonOrder(), toInstant(r.openedAt()), toInstant(r.completedAt())));
                if (r.completedAt() != null) {
                    done++;
                }
                if (r.openedAt() != null && (first == null || r.openedAt().isBefore(first))) {
                    first = r.openedAt();
                }
                if (r.lastOpenedAt() != null && (last == null || r.lastOpenedAt().isAfter(last))) {
                    last = r.lastOpenedAt();
                }
            }
            completed += done;
            blocks.add(new LearnAccessDto.BlockDto(block.get(0).moduleId(), block.get(0).moduleTitle(), block.get(0).moduleOrder(), done, block.size(), lessons));
        }
        return new LearnAccessDto.ProgressDto(completed, rows.size(), toInstant(first), toInstant(last), blocks);
    }

    private static Instant toInstant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
