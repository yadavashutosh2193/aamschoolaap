package aamscool.backend.aamschoolbackend.model;

import java.util.Locale;

public enum Language {
    ENGLISH,
    HINDI;

    public static Language from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Language.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
