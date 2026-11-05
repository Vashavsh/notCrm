package com.pipeline.learn.auth;

import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.pipeline.learn.common.ApiException;

/**
 * Rules for a password the student chooses. Passwords issued by the CRM are random words plus digits
 * and pass these rules; the limits are the same as the old site's: 10 to 1000 characters, never trimmed.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 1000;

    private static final Set<String> TOO_COMMON = Set.of(
            "password12", "password123", "1234567890", "12345678910", "qwertyuiop", "qwerty12345",
            "1q2w3e4r5t", "0123456789", "iloveyou12", "йцукенгшщз", "1234567890q");

    public static boolean isAcceptableInput(String password) {
        return password != null && !password.isEmpty() && password.length() <= MAX_LENGTH;
    }

    /** Throws a 400 with a message for the student when the password may not be used. */
    public void validate(String password, String login) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw bad("Пароль должен быть длиной от 10 до 1000 символов.");
        }
        if (password.chars().allMatch(Character::isDigit)) {
            throw bad("Пароль не должен состоять только из цифр.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (TOO_COMMON.contains(lower)) {
            throw bad("Пароль слишком простой.");
        }
        if (login != null && login.length() >= 4 && lower.contains(login.toLowerCase(Locale.ROOT))) {
            throw bad("Пароль не должен содержать ваш логин.");
        }
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
