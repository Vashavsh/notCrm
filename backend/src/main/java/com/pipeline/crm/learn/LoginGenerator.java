package com.pipeline.crm.learn;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * Picks the course-site login for a student: their Telegram handle without "@" (lower case), or, when there is none,
 * the name in Latin letters ("Иван Петров" gives {@code ivan_petrov}). A taken login gets a number: {@code ivan_petrov2}.
 * The login is fixed when access is issued and never follows later changes of the handle, so renaming in Telegram or
 * in the CRM cannot lock a student out.
 */
@Component
public class LoginGenerator {

    private static final int MAX_BASE_LENGTH = 40;
    private static final Map<Character, String> LATIN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "sch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"));

    /** @param taken tells whether a login is already used on the course site */
    public String generate(String telegramUsername, String fullName, Predicate<String> taken) {
        String base = fromHandle(telegramUsername);
        if (base.length() < 4) {
            base = fromName(fullName);
        }
        if (base.length() < 3) {
            base = "student";
        }
        base = base.length() > MAX_BASE_LENGTH ? base.substring(0, MAX_BASE_LENGTH) : base;
        String login = base;
        for (int n = 2; taken.test(login); n++) {
            login = base + n;
        }
        return login;
    }

    static String fromHandle(String handle) {
        if (handle == null) {
            return "";
        }
        String h = handle.trim().toLowerCase(Locale.ROOT);
        h = h.startsWith("@") ? h.substring(1) : h;
        return h.replaceAll("[^a-z0-9_]", "");
    }

    static String fromName(String fullName) {
        if (fullName == null) {
            return "";
        }
        StringBuilder latin = new StringBuilder();
        for (char c : fullName.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (LATIN.containsKey(c)) {
                latin.append(LATIN.get(c));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                latin.append(c);
            } else {
                latin.append('_');
            }
        }
        return latin.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
    }
}
