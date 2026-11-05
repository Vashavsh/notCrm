package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class CourseContentTest {

    private static final Path FIXTURE = new File("src/test/resources/content-fixture").toPath();
    private final ObjectMapper json = new ObjectMapper();

    @TempDir
    Path temp;

    private CourseContent load(Path dir) {
        return new CourseContent(new LearnProperties(dir.toString(), null), json);
    }

    /** A copy of the fixture course the test can then break on purpose. */
    private Path copyOfFixture() throws IOException {
        Path target = temp.resolve("content");
        try (var paths = Files.walk(FIXTURE)) {
            for (Path source : (Iterable<Path>) paths.sorted(Comparator.naturalOrder())::iterator) {
                Path destination = target.resolve(FIXTURE.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(source, destination);
                }
            }
        }
        return target;
    }

    private void replaceInLesson(Path dir, String lessonFile, String from, String to) throws IOException {
        Path file = dir.resolve("lessons").resolve(lessonFile);
        String text = Files.readString(file);
        assertThat(text).contains(from);
        Files.writeString(file, text.replace(from, to));
    }

    @Test
    void loadsTheFixtureCourse() {
        CourseContent content = load(FIXTURE);

        assertThat(content.lessons()).extracting(CourseContent.Lesson::id).containsExactly("lesson-01", "lesson-02");
        assertThat(content.lesson("lesson-01").orElseThrow().questions()).hasSize(6);
        assertThat(content.stageOf("block-2")).isEqualTo(1);
        assertThat(content.resources()).hasSize(1);
        assertThat(content.isReferencedAsset("/assets/course/fixture-chart.png")).isTrue();
        assertThat(content.isReferencedAsset("/assets/course/other.png")).isFalse();
    }

    @Test
    void refusesToStartWithoutAContentDirectory() {
        assertThatThrownBy(() -> new CourseContent(new LearnProperties("", null), json))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LEARN_CONTENT_DIR");
        assertThatThrownBy(() -> load(temp.resolve("nowhere")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not exist");
    }

    @Test
    void refusesADuplicateQuestionId() throws IOException {
        Path dir = copyOfFixture();
        replaceInLesson(dir, "lesson-02.json", "\"t02-q1\"", "\"t01-q1\"");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("duplicate question id t01-q1");
    }

    @Test
    void refusesACorrectAnswerThatPointsPastTheOptions() throws IOException {
        Path dir = copyOfFixture();
        replaceInLesson(dir, "lesson-01.json", "\"correct\": [1]", "\"correct\": [7]");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("t01-q1").hasMessageContaining("'correct'");
    }

    @Test
    void refusesAnUnknownResource() throws IOException {
        Path dir = copyOfFixture();
        replaceInLesson(dir, "lesson-01.json", "\"resource\": \"fixture-data\"", "\"resource\": \"missing\"");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("unknown resource missing");
    }

    @Test
    void refusesAnUnknownBlockAndADuplicateOrder() throws IOException {
        Path dir = copyOfFixture();
        replaceInLesson(dir, "lesson-02.json", "\"moduleId\": \"block-2\"", "\"moduleId\": \"block-99\"");
        replaceInLesson(dir, "lesson-02.json", "\"order\": 2", "\"order\": 1");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("unknown moduleId block-99").hasMessageContaining("duplicate order 1");
    }

    @Test
    void refusesANumberQuestionWithoutAnAnswer() throws IOException {
        Path dir = copyOfFixture();
        replaceInLesson(dir, "lesson-01.json", "\"answer\": 12.5, ", "");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("numeric 'answer'");
    }

    @Test
    void refusesBrokenJson() throws IOException {
        Path dir = copyOfFixture();
        Files.writeString(dir.resolve("lessons/lesson-03.json"), "{ not json");
        assertThatThrownBy(() -> load(dir)).hasMessageContaining("lesson-03.json cannot be read");
    }

    @Test
    void acceptsAFileWithAByteOrderMark() throws IOException {
        Path dir = copyOfFixture();
        Path file = dir.resolve("lessons/lesson-02.json");
        Files.writeString(file, "﻿" + Files.readString(file));
        assertThat(load(dir).lessons()).hasSize(2);
    }
}
