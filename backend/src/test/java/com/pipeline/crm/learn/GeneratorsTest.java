package com.pipeline.crm.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GeneratorsTest {

    @Test
    void passwordsAreThreeWordsAndTwoDigits() {
        PasswordGenerator generator = new PasswordGenerator();
        assertThat(generator.wordCount()).isGreaterThanOrEqualTo(400);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String password = generator.generate();
            assertThat(password).matches("[a-z]{3,8}-[a-z]{3,8}-[a-z]{3,8}-[1-9][0-9]");
            assertThat(password.length()).isBetween(14, 32);
            seen.add(password);
        }
        // Random, not a counter: 2000 draws from ~8 billion are all different.
        assertThat(seen).hasSize(2000);
    }

    @Test
    void theLoginIsTheTelegramHandleInLowerCase() {
        LoginGenerator generator = new LoginGenerator();
        assertThat(generator.generate("@Ivan_Petrov", "Иван Петров", l -> false)).isEqualTo("ivan_petrov");
        assertThat(generator.generate("ivan.petrov-77", "Иван Петров", l -> false)).isEqualTo("ivanpetrov77");
    }

    @Test
    void withoutAHandleTheNameIsWrittenInLatinLetters() {
        LoginGenerator generator = new LoginGenerator();
        assertThat(generator.generate(null, "Иван Петров", l -> false)).isEqualTo("ivan_petrov");
        assertThat(generator.generate("", "Юлия Щербакова-Ёлкина", l -> false)).isEqualTo("yuliya_scherbakova_elkina");
        assertThat(generator.generate("@ab", "Ян Ли", l -> false)).isEqualTo("yan_li");
        assertThat(generator.generate(null, "Anna  Smith ", l -> false)).isEqualTo("anna_smith");
    }

    @Test
    void aTakenLoginGetsANumber() {
        LoginGenerator generator = new LoginGenerator();
        Set<String> taken = Set.of("ivan_petrov", "ivan_petrov2");
        assertThat(generator.generate("@ivan_petrov", "Иван Петров", taken::contains)).isEqualTo("ivan_petrov3");
    }

    @Test
    void anUnusableNameFallsBackToStudent() {
        LoginGenerator generator = new LoginGenerator();
        assertThat(generator.generate(null, "!!", l -> false)).isEqualTo("student");
        assertThat(generator.generate(null, "!!", "student"::equals)).isEqualTo("student2");
    }
}
