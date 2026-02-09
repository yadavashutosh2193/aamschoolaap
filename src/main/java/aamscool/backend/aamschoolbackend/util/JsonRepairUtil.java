package aamscool.backend.aamschoolbackend.util;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonRepairUtil {

    private static final ObjectMapper mapper =
            new ObjectMapper();


    /* =========================================
       MAIN METHOD (USE THIS)
    ========================================= */

    public static String fixBrokenJson(String json) {

        if (json == null || json.isBlank()) {
            return null;
        }

        json = json.trim();

        // 1️⃣ Try normal parse
        if (isValid(json)) {
            return json;
        }

        // 2️⃣ Remove markdown
        json = json
                .replace("```json", "")
                .replace("```", "")
                .trim();


        // 3️⃣ Balance Brackets
        json = balance(json, '{', '}');
        json = balance(json, '[', ']');


        // 4️⃣ Close open quotes
        json = closeQuotes(json);


        // 5️⃣ Final validate
        if (isValid(json)) {
            return json;
        }

        // Still broken
        return null;
    }


    /* =========================================
       VALIDATE JSON
    ========================================= */

    private static boolean isValid(String json) {

        try {
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /* =========================================
       BALANCE {} AND []
    ========================================= */

    private static String balance(String s,
                                  char open,
                                  char close) {

        long openCount =
                s.chars().filter(c -> c == open).count();

        long closeCount =
                s.chars().filter(c -> c == close).count();

        StringBuilder sb = new StringBuilder(s);

        while (closeCount < openCount) {
            sb.append(close);
            closeCount++;
        }

        return sb.toString();
    }


    /* =========================================
       FIX UNCLOSED "
    ========================================= */

    private static String closeQuotes(String s) {

        long quotes =
                s.chars().filter(c -> c == '"').count();

        if (quotes % 2 != 0) {
            return s + "\"";
        }

        return s;
    }
}
