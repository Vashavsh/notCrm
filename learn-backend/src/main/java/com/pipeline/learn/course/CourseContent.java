package com.pipeline.learn.course;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The course material, read from disk once at startup and checked: if anything is wrong (a missing
 * directory, a duplicate id, a question whose right answer points past its options, a resource that does
 * not exist) the application refuses to start, so a bad content deploy is caught by the deploy's health
 * check and rolled back instead of reaching students.
 *
 * Question ids and lesson ids must never change: student progress is stored against them.
 */
@Component
public class CourseContent {

    public record Module(String id, String title, String description) {
    }

    /** One lesson as in {@code lesson-NN.json}; {@code sections} keep every field, including the private ones. */
    public record Lesson(String id, String moduleId, int order, String title, String summary, Integer minutes,
            String kind, List<Map<String, Object>> sections, Map<String, Map<String, Object>> questions) {
    }

    public static final List<Module> MODULES = List.of(
            new Module("block-1", "Введение. Базовые метрики и анализ рынка", "Знакомство с продуктовой аналитикой: первые метрики, исследование рынка и запуск продукта."),
            new Module("block-2", "Retention и прогноз аудитории", "Удержание пользователей и прогнозирование аудитории."),
            new Module("block-3", "Понимание воронок", "Как пользователи проходят путь к ценности продукта."),
            new Module("block-4", "Гипотеза ценности и оценка фичи", "Проверка продуктовой ценности и оценка новых возможностей."),
            new Module("block-5", "Дизайн эксперимента", "Планирование и анализ продуктовых экспериментов."),
            new Module("block-6", "Ценность и гипотеза роста", "Поиск устойчивого роста продукта."),
            new Module("block-7", "ROI, маркетинг и концовка", "Экономика привлечения и завершение курса."));

    static final Set<String> QUESTION_KINDS = Set.of("single", "multi", "number", "open", "reflection");
    private static final Pattern ASSET_REFERENCE = Pattern.compile("/assets/course/[^\\s\"'<>\\\\]+");

    private final List<Lesson> lessons;
    private final Map<String, Lesson> byId = new LinkedHashMap<>();
    private final Map<String, Integer> stages;
    private final List<Map<String, Object>> resources;
    private final Set<String> assetReferences = new HashSet<>();
    private final Path imagesDir;
    private final Path secretsDir;

    public CourseContent(LearnProperties properties, ObjectMapper json) {
        if (properties.contentDir() == null || properties.contentDir().isBlank()) {
            throw new IllegalStateException("Course content directory is not configured: set LEARN_CONTENT_DIR.");
        }
        Path root = Path.of(properties.contentDir()).toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Course content directory does not exist: " + root);
        }
        this.imagesDir = root.resolve("images");
        this.secretsDir = properties.secretsDir() == null || properties.secretsDir().isBlank()
                ? null : Path.of(properties.secretsDir()).toAbsolutePath().normalize();

        this.stages = readStages(root.resolve("stages.json"), json, problems);
        this.resources = readResources(root.resolve("resources.json"), json, problems);
        Set<String> resourceIds = new HashSet<>();
        resources.forEach(r -> resourceIds.add(String.valueOf(r.get("id"))));

        List<Lesson> loaded = readLessons(root.resolve("lessons"), json, problems);
        validate(loaded, resourceIds, problems);
        for (String reference : assetReferences) {
            Path file = imagesDir.resolve(reference.substring("/assets/course/".length())).normalize();
            if (!file.startsWith(imagesDir) || !Files.isRegularFile(file)) {
                problems.add("lessons refer to " + reference + " but images/ has no such file");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Course content in " + root + " is invalid:\n  - "
                    + String.join("\n  - ", problems.stream().limit(25).toList())
                    + (problems.size() > 25 ? "\n  ... and " + (problems.size() - 25) + " more" : ""));
        }
        this.lessons = loaded.stream().sorted(Comparator.comparingInt(Lesson::order)).toList();
        lessons.forEach(l -> byId.put(l.id(), l));
    }

    public List<Lesson> lessons() {
        return lessons;
    }

    public Optional<Lesson> lesson(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int stageOf(String moduleId) {
        return stages.get(moduleId);
    }

    /** Resource catalog entries as written in {@code resources.json} (public metadata only, no logins). */
    public List<Map<String, Object>> resources() {
        return resources;
    }

    public boolean isReferencedAsset(String reference) {
        return assetReferences.contains(reference);
    }

    public Path imagesDir() {
        return imagesDir;
    }

    public Path secretsDir() {
        return secretsDir;
    }

    // ---- reading ----

    private Map<String, Integer> readStages(Path file, ObjectMapper json, List<String> problems) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = json.readValue(readText(file), new TypeReference<>() {
            });
            for (Module module : MODULES) {
                Object value = raw.get(module.id());
                if (value instanceof Integer stage) {
                    result.put(module.id(), stage);
                } else {
                    problems.add("stages.json: no integer stage for " + module.id());
                }
            }
        } catch (IOException | RuntimeException e) {
            problems.add("stages.json cannot be read: " + e.getMessage());
        }
        return result;
    }

    private List<Map<String, Object>> readResources(Path file, ObjectMapper json, List<String> problems) {
        try {
            List<Map<String, Object>> raw = json.readValue(readText(file), new TypeReference<>() {
            });
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> resource : raw) {
                Object id = resource.get("id");
                if (!(id instanceof String s) || s.isBlank() || !s.matches("[A-Za-z0-9_-]+")) {
                    problems.add("resources.json: a resource has no valid id: " + resource);
                } else if (!seen.add(s)) {
                    problems.add("resources.json: duplicate resource id " + s);
                }
            }
            return raw;
        } catch (IOException | RuntimeException e) {
            problems.add("resources.json cannot be read: " + e.getMessage());
            return List.of();
        }
    }

    private List<Lesson> readLessons(Path dir, ObjectMapper json, List<String> problems) {
        List<Lesson> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            problems.add("lessons/ directory is missing");
            return result;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().matches("lesson-.*\\.json")).sorted().toList();
        } catch (IOException e) {
            problems.add("lessons/ cannot be listed: " + e.getMessage());
            return result;
        }
        if (files.isEmpty()) {
            problems.add("lessons/ has no lesson-*.json files");
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                String text = readText(file);
                Matcher matcher = ASSET_REFERENCE.matcher(text);
                while (matcher.find()) {
                    assetReferences.add(matcher.group());
                }
                Map<String, Object> raw = json.readValue(text, new TypeReference<>() {
                });
                result.add(toLesson(name, raw, problems));
            } catch (IOException | RuntimeException e) {
                problems.add(name + " cannot be read: " + e.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Lesson toLesson(String file, Map<String, Object> raw, List<String> problems) {
        List<Map<String, Object>> sections = raw.get("sections") instanceof List<?> list
                ? (List<Map<String, Object>>) list : List.of();
        if (!(raw.get("sections") instanceof List<?>)) {
            problems.add(file + ": 'sections' must be a list");
        }
        Map<String, Map<String, Object>> questions = new LinkedHashMap<>();
        for (Map<String, Object> section : sections) {
            if ("question".equals(section.get("type")) && section.get("id") instanceof String id) {
                questions.putIfAbsent(id, section);
            }
        }
        Object order = raw.get("order");
        if (!(order instanceof Integer)) {
            problems.add(file + ": 'order' must be an integer");
        }
        return new Lesson(string(raw, "id"), string(raw, "moduleId"), order instanceof Integer i ? i : 0,
                string(raw, "title"), string(raw, "summary"),
                raw.get("minutes") instanceof Integer m ? m : null, string(raw, "kind"), sections, questions);
    }

    private static String string(Map<String, Object> raw, String key) {
        return raw.get(key) instanceof String s ? s : null;
    }

    /** Lesson files may start with a UTF-8 byte order mark. */
    private static String readText(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    // ---- validation ----

    private void validate(List<Lesson> loaded, Set<String> resourceIds, List<String> problems) {
        Set<String> lessonIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        Set<String> moduleIds = new HashSet<>();
        MODULES.forEach(m -> moduleIds.add(m.id()));

        for (Lesson lesson : loaded) {
            String where = lesson.id() == null ? "a lesson" : lesson.id();
            if (lesson.id() == null || lesson.id().isBlank()) {
                problems.add("a lesson has no id");
            } else if (!lessonIds.add(lesson.id())) {
                problems.add("duplicate lesson id " + lesson.id());
            }
            if (!orders.add(lesson.order())) {
                problems.add(where + ": duplicate order " + lesson.order());
            }
            if (lesson.moduleId() == null || !moduleIds.contains(lesson.moduleId())) {
                problems.add(where + ": unknown moduleId " + lesson.moduleId());
            }
            if (lesson.title() == null || lesson.title().isBlank()) {
                problems.add(where + ": no title");
            }
            for (Map<String, Object> section : lesson.sections()) {
                Object type = section.get("type");
                if ("html".equals(type)) {
                    if (!(section.get("html") instanceof String)) {
                        problems.add(where + ": an html section has no 'html' text");
                    }
                } else if ("resource".equals(type)) {
                    if (!(section.get("resource") instanceof String r) || !resourceIds.contains(r)) {
                        problems.add(where + ": resource section refers to unknown resource " + section.get("resource"));
                    }
                } else if ("question".equals(type)) {
                    validateQuestion(where, section, questionIds, problems);
                } else {
                    problems.add(where + ": unknown section type " + type);
                }
            }
        }
    }

    private void validateQuestion(String lesson, Map<String, Object> question, Set<String> questionIds, List<String> problems) {
        Object idValue = question.get("id");
        if (!(idValue instanceof String id) || id.isBlank()) {
            problems.add(lesson + ": a question has no id");
            return;
        }
        String where = lesson + "/" + id;
        if (!questionIds.add(id)) {
            problems.add("duplicate question id " + id);
        }
        Object kind = question.get("kind");
        if (!(kind instanceof String k) || !QUESTION_KINDS.contains(k)) {
            problems.add(where + ": unknown question kind " + kind);
            return;
        }
        if (!(question.get("prompt") instanceof String)) {
            problems.add(where + ": no prompt");
        }
        switch (k) {
            case "single", "multi", "reflection" -> {
                int options = question.get("options") instanceof List<?> list ? list.size() : 0;
                if (options < 2) {
                    problems.add(where + ": needs at least 2 options");
                }
                if (!k.equals("reflection")) {
                    if (!(question.get("correct") instanceof List<?> correct) || correct.isEmpty()
                            || correct.stream().anyMatch(c -> !(c instanceof Integer i) || i < 0 || i >= options)) {
                        problems.add(where + ": 'correct' must list option indexes within 0.." + (options - 1));
                    }
                }
            }
            case "number" -> {
                if (!(question.get("answer") instanceof Number)) {
                    problems.add(where + ": a number question needs a numeric 'answer'");
                }
            }
            default -> {
            }
        }
    }
}
