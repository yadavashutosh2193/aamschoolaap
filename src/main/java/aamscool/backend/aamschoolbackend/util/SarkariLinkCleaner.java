package aamscool.backend.aamschoolbackend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class SarkariLinkCleaner {

    private static final ObjectMapper mapper = new ObjectMapper();

    /* ===============================
       BLOCKED DOMAINS / TEXT
    =============================== */

    private static final String[] BLOCKED_DOMAINS = {

            // Sarkari sites
            "sarkariresult.com",
            "sarkariresult",
            "sarkariexam.com",

            // Social
            "facebook.com",
            "instagram.com",
            "twitter.com",
            "youtube.com",
            "youtu.be",
            "telegram",
            "whatsapp.com",
            "linkedin.com",
            "t.me",

            // App store
            "play.google.com"
    };

    /* ===============================
       MAIN METHOD
    =============================== */

    public static String cleanLinks(Map<String, Object> job) throws Exception {

        if (job == null) return "{}";

        removeLinks(job);

        return mapper.writeValueAsString(job);
    }

    /* ===============================
       RECURSIVE CLEANER
    =============================== */

    @SuppressWarnings("unchecked")
    private static void removeLinks(Object node) {

        if (node == null) return;

        /* ---------- MAP ---------- */

        if (node instanceof Map) {

            Map<String, Object> map = (Map<String, Object>) node;

            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {

                Map.Entry<String, Object> entry = iterator.next();
                Object value = entry.getValue();

                // remove field if blocked string
                if (isBlocked(value)) {
                    iterator.remove();
                    continue;
                }

                removeLinks(value);
            }
        }

        /* ---------- LIST ---------- */

        else if (node instanceof List) {

            List<Object> list = (List<Object>) node;

            for (int i = list.size() - 1; i >= 0; i--) {

                Object item = list.get(i);

                // 🚨 If any object inside list contains blocked text/link → remove whole object
                if (containsBlockedDeep(item)) {
                    list.remove(i);
                    continue;
                }

                removeLinks(item);
            }
        }
    }

    /* ===============================
       CHECK BLOCKED TEXT
    =============================== */

    private static boolean isBlocked(Object value) {

        if (!(value instanceof String)) return false;

        String val = ((String) value).toLowerCase();

        for (String domain : BLOCKED_DOMAINS) {
            if (val.contains(domain)) return true;
        }

        return false;
    }

    /* ===============================
       DEEP CHECK (IMPORTANT)
       If any field inside object has blocked text
       remove whole object
    =============================== */

    @SuppressWarnings("unchecked")
    private static boolean containsBlockedDeep(Object node) {

        if (node == null) return false;

        if (node instanceof String) {
            return isBlocked(node);
        }

        if (node instanceof Map) {
            for (Object v : ((Map<String, Object>) node).values()) {
                if (containsBlockedDeep(v)) return true;
            }
        }

        if (node instanceof List) {
            for (Object v : (List<Object>) node) {
                if (containsBlockedDeep(v)) return true;
            }
        }

        return false;
    }
}