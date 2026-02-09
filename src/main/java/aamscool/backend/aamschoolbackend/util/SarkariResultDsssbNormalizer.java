package aamscool.backend.aamschoolbackend.util;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SarkariResultDsssbNormalizer {

    private static final String NA = "NA";


    /* ================= KEYWORD RULES ================= */

    private static final Pattern ELIGIBILITY =
            Pattern.compile(".*(degree|graduate|bed|b\\.ed|llb|diploma|qualified).*",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern SYLLABUS =
            Pattern.compile(".*(knowledge|psychology|affairs|history|geography|culture|method).*",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern APPLY =
            Pattern.compile(".*(apply|visit|click|submit|registration).*",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern SELECTION =
            Pattern.compile(".*(examination|interview|merit|medical|written).*",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern RESULT =
            Pattern.compile(".*(result|download).*",
                    Pattern.CASE_INSENSITIVE);


    /* ================= MAIN API ================= */

    public Map<String, Object> normalize(Map<String, Object> scraped) {

        Map<String, Object> job = new LinkedHashMap<>();

        Map<String, Object> content =
                (Map<String, Object>) scraped.get("content");


        /* ============== BASIC INFO ============== */

        String title = safe(scraped.get("pageTitle"));

        job.put("title", cleanTitle(title));

        job.put("short_description",
                extractDescription(content));

        job.put("advertisement_no", NA);

        job.put("post_name",
                extractPost(title));

        job.put("conducting_body",
                extractOrg(content));


        /* ============== DATES ============== */

        job.put("important_dates",
                extractDates(content));


        /* ============== FEE ============== */

        job.put("application_fee",
                extractFee(content));


        /* ============== AGE ============== */

        job.put("eligibility_criteria",
                extractAgeAndEligibility(content));


        /* ============== VACANCY / MARKS ============== */

        job.put("vacancy_details",
                extractVacancy(content));


        /* ============== PAY ============== */

        job.put("pay_scale",
                extractPay(content));


        /* ============== APPLY PROCESS ============== */

        job.put("application_process",
                extractApplyProcess(content));


        /* ============== EXAM SCHEME ============== */

        job.put("exam_scheme",
                extractExamScheme(content));


        /* ============== SELECTION ============== */

        job.put("selection_process",
                extractSelection(content));


        /* ============== NOTES ============== */

        job.put("important_notes",
                extractNotes(content));


        /* ============== LINKS ============== */

        job.put("official_links",
                extractLinks(content));


        /* ============== SOURCE ============== */

        job.put("source",
                safe(scraped.get("url")));


        /* ============== SYLLABUS ============== */

        job.put("syllabus_overview",
                extractSyllabus(content));


        return job;
    }


    /* ==================================================
                         EXTRACTORS
       ================================================== */

    /* ---------- DESCRIPTION ---------- */

    private String extractDescription(Map<String, Object> content) {

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                List<String> list = (List<String>) sec.get("list");

                if (!list.isEmpty()
                        && list.get(0).length() > 80) {

                    return cleanText(list.get(0));
                }
            }
        }
        return NA;
    }


    /* ---------- ORGANIZATION ---------- */

    private String extractOrg(Map<String, Object> content) {

        for (String k : content.keySet()) {

            String t = k.toLowerCase();

            if (t.contains("commission")
                || t.contains("board")
                || t.contains("army")
                || t.contains("police")
                || t.contains("recruitment")) {

                return k;
            }
        }

        return "NA";
    }


    /* ---------- POST ---------- */

    private String extractPost(String title) {

        return title
                .replaceAll("(?i)result|out|admit card|online form|202\\d", "")
                .trim();
    }


    /* ---------- DATES ---------- */

    private Map<String, String> extractDates(Map<String, Object> content) {

        for (String k : content.keySet()) {

            if (k.toLowerCase().contains("date")) {

                Map<String, Object> sec =
                        (Map<String, Object>) content.get(k);

                Map<String, String> map = new LinkedHashMap<>();

                sec.forEach((a, b) -> {

                    if (!"list".equals(a)) {

                        map.put(a, b.toString());
                    }
                });

                return map;
            }
        }

        return Map.of("NA", "NA");
    }


    /* ---------- FEE ---------- */

    private Map<String, String> extractFee(Map<String, Object> content) {

        for (String k : content.keySet()) {

            if (k.toLowerCase().contains("fee")) {

                Map<String, Object> sec =
                        (Map<String, Object>) content.get(k);

                Map<String, String> fee = new LinkedHashMap<>();

                sec.forEach((a, b) -> {

                    if (!"list".equals(a)) {

                        fee.put(a, b.toString());
                    }
                });

                return fee;
            }
        }

        return Map.of("NA", "NA");
    }


    /* ---------- AGE + ELIGIBILITY ---------- */

    private Map<String, Object> extractAgeAndEligibility(
            Map<String, Object> content) {

        Map<String, Object> map = new LinkedHashMap<>();

        /* Age */

        for (String k : content.keySet()) {

            if (k.toLowerCase().contains("age")) {

                Map<String, Object> sec =
                        (Map<String, Object>) content.get(k);

                map.put("minimum_age", sec.get("Minimum Age"));
                map.put("maximum_age", sec.get("Maximum Age"));
            }
        }

        /* Eligibility */

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                for (String s : (List<String>) sec.get("list")) {

                    if (ELIGIBILITY.matcher(s).matches()) {

                        map.put("qualification", cleanText(s));
                        break;
                    }
                }
            }
        }

        if (map.isEmpty())
            map.put("NA", "NA");

        return map;
    }


    /* ---------- VACANCY / MARKS ---------- */

    private Map<String, String> extractVacancy(
            Map<String, Object> content) {

        for (String k : content.keySet()) {

            if (k.toLowerCase().contains("vacancy")) {

                Map<String, Object> sec =
                        (Map<String, Object>) content.get(k);

                if (sec.containsKey("rows")) {

                    List<Map<String, String>> rows =
                            (List<Map<String, String>>) sec.get("rows");

                    Map<String, String> map = new LinkedHashMap<>();

                    for (int i = 1; i < rows.size(); i++) {

                        map.putAll(rows.get(i));
                    }

                    return map;
                }
            }
        }

        return Map.of("Total", NA);
    }


    /* ---------- PAY ---------- */

    private String extractPay(Map<String, Object> content) {

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("rows")) {

                List<Map<String, String>> rows =
                        (List<Map<String, String>>) sec.get("rows");

                for (Map<String, String> row : rows) {

                    for (String v : row.values()) {

                        if (v.contains("₹")) {

                            return v;
                        }
                    }
                }
            }
        }

        return NA;
    }


    /* ---------- APPLY PROCESS ---------- */

    private List<String> extractApplyProcess(
            Map<String, Object> content) {

        Set<String> set = new LinkedHashSet<>();

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                for (String s : (List<String>) sec.get("list")) {

                    if (APPLY.matcher(s).matches()) {

                        set.add(cleanText(s));
                    }
                }
            }
        }

        return set.isEmpty()
                ? List.of(NA)
                : new ArrayList<>(set);
    }


    /* ---------- EXAM ---------- */

    private Map<String, String> extractExamScheme(
            Map<String, Object> content) {

        Map<String, String> exam = new LinkedHashMap<>();

        for (String k : content.keySet()) {

            if (k.toLowerCase().contains("hour")) {

                exam.put("duration", k);

                Map<String, Object> sec =
                        (Map<String, Object>) content.get(k);

                if (sec.containsKey("rows")) {

                    List<Map<String, String>> rows =
                            (List<Map<String, String>>) sec.get("rows");

                    for (Map<String, String> r : rows) {

                        exam.putAll(r);
                    }
                }
            }
        }

        return exam.isEmpty()
                ? Map.of("NA", "NA")
                : exam;
    }


    /* ---------- SELECTION ---------- */

    private List<String> extractSelection(
            Map<String, Object> content) {

        Set<String> set = new LinkedHashSet<>();

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                for (String s : (List<String>) sec.get("list")) {

                    if (SELECTION.matcher(s).matches()) {

                        set.add(cleanText(s));
                    }
                }
            }
        }

        return set.isEmpty()
                ? List.of("Written Examination")
                : new ArrayList<>(set);
    }


    /* ---------- NOTES ---------- */

    private List<String> extractNotes(
            Map<String, Object> content) {

        Set<String> set = new LinkedHashSet<>();

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                for (String s : (List<String>) sec.get("list")) {

                    if (s.toLowerCase().contains("note")
                        || s.toLowerCase().contains("advised")
                        || s.contains("ध्यान")) {

                        set.add(cleanText(s));
                    }
                }
            }
        }

        return set.isEmpty()
                ? List.of(NA)
                : new ArrayList<>(set);
    }


    /* ---------- SYLLABUS ---------- */

    private List<String> extractSyllabus(
            Map<String, Object> content) {

        Set<String> set = new LinkedHashSet<>();

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            if (sec.containsKey("list")) {

                for (String s : (List<String>) sec.get("list")) {

                    if (SYLLABUS.matcher(s).matches()) {

                        set.add(cleanText(s));
                    }
                }
            }
        }

        return set.isEmpty()
                ? List.of(NA)
                : new ArrayList<>(set);
    }


    /* ---------- LINKS ---------- */

    private static final List<String> BLOCKED_DOMAINS = List.of(
            "sarkariresult",
            "freejobalert",
            "rojgarresult",
            "telegram",
            "youtube",
            "google"
    );


    private Map<String, String> extractLinks(
            Map<String, Object> content) {

        Map<String, String> links = new LinkedHashMap<>();

        for (Object secObj : content.values()) {

            Map<String, Object> sec = (Map<String, Object>) secObj;

            sec.forEach((k, v) -> {

                String url = v.toString();

                if (!url.startsWith("http"))
                    return;

                if (isBlocked(url))
                    return;

                links.put(cleanLabel(k), url);
            });
        }

        return links;
    }


    private boolean isBlocked(String url) {

        String t = url.toLowerCase();

        for (String d : BLOCKED_DOMAINS) {

            if (t.contains(d))
                return true;
        }

        return false;
    }


    private String cleanLabel(String s) {

        return s
                .replaceAll("(?i)click here", "")
                .replaceAll("(?i)download", "")
                .trim();
    }


    /* ==================================================
                          UTILS
       ================================================== */

    private String safe(Object o) {

        return o == null ? NA : o.toString();
    }


    private String cleanTitle(String t) {

        return t.replaceAll("(?i)out|result|admit", "")
                .trim();
    }


    private String cleanText(String s) {

        return s
                .replaceAll("(?i)sarkariresult.*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
