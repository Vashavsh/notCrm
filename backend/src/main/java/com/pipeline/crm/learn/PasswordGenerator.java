package com.pipeline.crm.learn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Passwords for the course site: three plain English words and two digits, e.g. {@code lamp-river-tiger-47}. Easy to read
 * out and type on a phone, no keyboard-layout trouble. About 33 bits from 400+ words; guessing is held back by the login
 * lockout of the course site, and the student can set their own password.
 */
@Component
public class PasswordGenerator {

    private final List<String> words;
    private final SecureRandom random = new SecureRandom();

    public PasswordGenerator() {
        try (var in = new ClassPathResource("learn/password-words.txt").getInputStream()) {
            this.words = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(w -> !w.isEmpty()).distinct().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String generate() {
        return pick() + "-" + pick() + "-" + pick() + "-" + (10 + random.nextInt(90));
    }

    int wordCount() {
        return words.size();
    }

    private String pick() {
        return words.get(random.nextInt(words.size()));
    }
}
