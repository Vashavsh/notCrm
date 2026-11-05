package com.pipeline.learn.course;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

/** The course text addresses a placeholder student, "Анон"; each student sees their own name instead. */
final class Personalizer {

    // The standalone word only: "Анонимный" must stay as it is.
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<![А-Яа-яЁё])Анон(?![А-Яа-яЁё])");

    private Personalizer() {
    }

    static String personalize(String text, String name) {
        if (text == null) {
            return null;
        }
        String safeName = Matcher.quoteReplacement(HtmlUtils.htmlEscape(name));
        return PLACEHOLDER.matcher(text).replaceAll(safeName);
    }
}
