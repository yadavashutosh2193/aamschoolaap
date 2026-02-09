package aamscool.backend.aamschoolbackend.util;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostDateSorter {

    // Format: October 8, 2025
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private static final Pattern DATE_PATTERN =
            Pattern.compile("Post Date\\s+([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})");


    /* =====================================
       MAIN SORT METHOD (CALL THIS)
    ===================================== */

    public static void sortByPostDate(
            List<Map<String, Object>> jobs) {

        jobs.sort((a, b) -> {

            LocalDate d1 = extractPostDate(a);
            LocalDate d2 = extractPostDate(b);

            boolean u1 = d1.equals(LocalDate.MAX);
            boolean u2 = d2.equals(LocalDate.MAX);

            // Unknown dates → TOP
            if (u1 && u2) return 0;
            if (u1) return -1;
            if (u2) return 1;

            // Old → New
            return d1.compareTo(d2);
        });
    }


    /* =====================================
       EXTRACT DATE FROM MAP
    ===================================== */

    private static LocalDate extractPostDate(
            Map<String, Object> map) {

        for (Object val : map.values()) {

            LocalDate date = findDate(val);

            if (date != null) {
                return date;
            }
        }

        // No date found
        return LocalDate.MAX;
    }


    /* =====================================
       RECURSIVE DATE FINDER
    ===================================== */

    private static LocalDate findDate(Object obj) {

        if (obj == null) return null;

        // Case 1: String
        if (obj instanceof String str) {

            Matcher m = DATE_PATTERN.matcher(str);

            if (m.find()) {

                try {
                    return LocalDate.parse(
                            m.group(1), FORMATTER);
                } catch (Exception ignored) {}
            }
        }

        // Case 2: Map
        if (obj instanceof Map<?, ?> map) {

            for (Object v : map.values()) {

                LocalDate d = findDate(v);

                if (d != null) return d;
            }
        }

        // Case 3: List
        if (obj instanceof List<?> list) {

            for (Object v : list) {

                LocalDate d = findDate(v);

                if (d != null) return d;
            }
        }

        return null;
    }
}
