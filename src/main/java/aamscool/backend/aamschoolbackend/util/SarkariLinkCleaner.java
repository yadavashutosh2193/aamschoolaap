package aamscool.backend.aamschoolbackend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

public class SarkariLinkCleaner {
	private static final ObjectMapper mapper =
            new ObjectMapper();


    /* ===============================
       BLOCKED DOMAINS
    =============================== */

    private static final String[] BLOCKED_DOMAINS = {

            // SarkariResult
            "sarkariresult.com",
            "sarkariresult.com.cm",
            "https://www.sarkariexam.com",

            // Social media
            "facebook.com",
            "fb.com",
            "instagram.com",
            "twitter.com",
            "x.com",
            "youtube.com",
            "youtu.be",
            "t.me",
            "telegram.me",
            "wa.me",
            "whatsapp.com",
            "linkedin.com",

            // App stores (optional)
            "play.google.com"
    };


    /* ===============================
       MAIN METHOD
    =============================== */

    public static String cleanLinks(String json)
            throws Exception {

        JsonNode root = mapper.readTree(json);

        removeLinks(root);

        return mapper.writeValueAsString(root);
    }


    /* ===============================
       CORE RECURSIVE CLEANER
    =============================== */

    private static void removeLinks(JsonNode node) {

        if (node == null) return;


        /* ---------- OBJECT ---------- */

        if (node.isObject()) {

            ObjectNode obj = (ObjectNode) node;

            Iterator<Map.Entry<String, JsonNode>> fields =
                    obj.fields();

            while (fields.hasNext()) {

                Map.Entry<String, JsonNode> entry =
                        fields.next();

                JsonNode value = entry.getValue();

                // If this value is blocked URL → REMOVE FIELD
                if (isBlocked(value)) {

                    fields.remove();
                    continue;
                }

                // Recurse
                removeLinks(value);
            }
        }


        /* ---------- ARRAY ---------- */

        if (node.isArray()) {

            ArrayNode array = (ArrayNode) node;

            for (int i = array.size() - 1; i >= 0; i--) {

                JsonNode child = array.get(i);

                // If blocked → remove element
                if (isBlocked(child)) {

                    array.remove(i);
                    continue;
                }

                removeLinks(child);
            }
        }
    }


    /* ===============================
       CHECK BLOCKED URL
    =============================== */

    private static boolean isBlocked(JsonNode node) {

        if (node == null) return false;

        if (!node.isTextual()) return false;

        String val = node.asText().toLowerCase();

        for (String domain : BLOCKED_DOMAINS) {

            if (val.contains(domain)) {
                return true;
            }
        }

        return false;
    }
}

