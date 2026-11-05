package com.pipeline.learn.course;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.pipeline.learn.common.ApiException;

/**
 * Checks a student's answer against the question and builds the stored answer. The rules and the messages
 * are those of the old course site; a wrong answer never blocks the student.
 */
@Component
public class Grader {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern NOT_FINITE = Pattern.compile("[+-]?(inf|infinity|nan)", Pattern.CASE_INSENSITIVE);

    public Map<String, Object> grade(Map<String, Object> question, Object value, boolean hasAttachment) {
        String kind = (String) question.get("kind");
        List<?> options = question.get("options") instanceof List<?> list ? list : List.of();
        Boolean correct = null;
        Object stored = value;

        switch (kind) {
            case "single", "reflection" -> {
                Integer index = asInt(value);
                if (index == null || index < 0 || index >= options.size()) {
                    throw bad("Выберите вариант ответа.");
                }
                if (kind.equals("single")) {
                    correct = indexes(question.get("correct")).contains(index);
                }
            }
            case "multi" -> {
                List<Integer> chosen = new ArrayList<>();
                if (value instanceof List<?> list && !list.isEmpty()) {
                    for (Object item : list) {
                        Integer index = asInt(item);
                        if (index == null || index < 0 || index >= options.size()) {
                            throw bad("Выберите допустимые варианты ответа.");
                        }
                        chosen.add(index);
                    }
                }
                if (chosen.isEmpty() || new HashSet<>(chosen).size() != chosen.size()) {
                    throw bad("Выберите допустимые варианты ответа.");
                }
                correct = new HashSet<>(chosen).equals(new HashSet<>(indexes(question.get("correct"))));
            }
            case "number" -> correct = gradeNumber(question, value);
            case "open" -> {
                if (!(value instanceof String text) || text.length() > 30000 || (text.isBlank() && !hasAttachment)) {
                    throw bad("Напишите ответ или прикрепите график (текст до 30 000 символов).");
                }
                stored = text.strip();
            }
            default -> throw bad("Тип задания пока не поддерживается.");
        }

        String feedback = Boolean.FALSE.equals(correct) ? text(question.get("wrongFeedback")) : text(question.get("feedback"));
        if (feedback.isEmpty()) {
            feedback = text(question.get("feedback"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", stored);
        result.put("correct", correct);
        result.put("feedback", feedback);
        result.put("selfAssessment", null);
        result.put("submitted", true);
        if (kind.equals("single") || kind.equals("multi")) {
            result.put("correctOptions", question.get("correct"));
        }
        if (kind.equals("number")) {
            Object answer = question.get("answer");
            result.put("correctAnswer", answer);
            if (Boolean.FALSE.equals(correct) && !feedback.contains("Правильный ответ")) {
                result.put("feedback", "<p>Правильный ответ — " + answer + ".</p>" + feedback);
            }
        }
        return result;
    }

    private Boolean gradeNumber(Map<String, Object> question, Object value) {
        if (!(value instanceof String || value instanceof Number)) {
            throw bad("Введите число.");
        }
        String normalized = String.valueOf(value).replaceAll("\\s+", "").replace(',', '.');
        if (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (NOT_FINITE.matcher(normalized).matches()) {
            throw bad("Введите конечное число.");
        }
        if (!DECIMAL.matcher(normalized).matches()) {
            throw bad("Введите число: можно использовать точку или запятую.");
        }
        double numeric = Double.parseDouble(normalized);
        if (!Double.isFinite(numeric)) {
            throw bad("Введите конечное число.");
        }
        double answer = ((Number) question.get("answer")).doubleValue();
        double tolerance = question.get("tolerance") instanceof Number t ? t.doubleValue() : 0.01;
        return Math.abs(numeric - answer) <= tolerance + 1e-10;
    }

    /** An integer sent as a JSON integer; a boolean, a fraction or text is not an option index. */
    private static Integer asInt(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return l.intValue();
        }
        return null;
    }

    private static Set<Integer> indexes(Object raw) {
        Set<Integer> result = new HashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Integer i) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    private static String text(Object value) {
        return value instanceof String s ? s : "";
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
