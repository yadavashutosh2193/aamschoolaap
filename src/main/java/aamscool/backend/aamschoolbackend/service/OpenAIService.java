package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/chat/completions";

    private static final MediaType JSON =
            MediaType.parse("application/json");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private static final List<String> DEFAULT_CURRENT_AFFAIRS_TOPICS = List.of(
            "Polity & Governance",
            "Economy & Banking",
            "Science & Technology",
            "Environment & Ecology",
            "International Relations",
            "National Schemes & Welfare",
            "Defence & Security",
            "Awards & Honours",
            "Sports",
            "Reports/Indices/Summits"
    );


    public OpenAIService() {

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();
    }


    /**
     * Process raw job Map and return standardized JSON
     */
    public String processJobMap(Map<String, Object> resp) throws IOException {

        // 1 Convert Map -> JSON
        String rawJson = objectMapper.writeValueAsString(resp);

        // 2 Build Prompt
        String prompt = buildPrompt(rawJson);

        // 3 Build Request Body
        String requestBody = buildRequestBody(prompt);


        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();


        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {

                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                        " | " + response.message()
                );
            }

            return response.body().string();
        }
    }

    /**
     * Refine master job JSON content while preserving source-critical sections.
     */
    public String refineMasterJobContent(String rawMasterJson) throws IOException {
        String prompt = buildMasterRefinePrompt(rawMasterJson);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                        " | " + response.message()
                );
            }
            String raw = response.body().string();
            try {
                return extractCleanJson(raw);
            } catch (Exception ex) {
                return rawMasterJson;
            }
        }
    }

    /**
     * Low-token text cleanup for narrative fields only.
     */
    public String rewriteNarrativeFields(String narrativeJson) throws IOException {
        String prompt = buildNarrativeRewritePrompt(narrativeJson);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                                " | " + response.message()
                );
            }
            String raw = response.body().string();
            try {
                return extractCleanJson(raw);
            } catch (Exception ex) {
                return narrativeJson;
            }
        }
    }

    /**
     * Direct URL -> master DTO JSON via OpenAI prompt only (no local scraper parsing).
     */
    public String generateMasterJsonFromUrlDirect(String sourceUrl) throws IOException {
        String prompt = buildDirectUrlMasterPrompt(sourceUrl);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                                " | " + response.message()
                );
            }

            String raw = response.body().string();
            try {
                return extractCleanJson(raw);
            } catch (Exception ex) {
                return raw;
            }
        }
    }


    // ---------------- PRIVATE HELPERS ----------------


    private String buildPrompt(String rawJson) {

        return """
    You are a job-notification JSON generator for my recruitment portal.
================= INPUT DATA =================
"""
 + rawJson +

 """
================================================

Now:

1. Extract information from input.
2. Validate JSON syntax before returning.
3. please try to add more information that you feel is needed and not available in the input json
4. please must add all the links in response json from raw json inside important links key (do not remove any links)
5. please always try to add description and vacancy details.
6. please do not put exampattern under vacancy detail it should contain total vacancy and post wise vacancy and category wise vacancy.
7.please create section for exam process and pattern
8. please do not use any parent key for the json (means json should start with {)
9. use key title for title(always modify from input), advertisement_no for advertisement and also add key post_name, eligibility(includes age limit and educational eligibiity) rest all keys word should we spaces seperated
Return final valid JSON only.
 """;
    }

    private String buildMasterRefinePrompt(String rawJson) {
        return """
You are refining scraped job JSON for publishing.

Input JSON:
""" + rawJson + """

Rules:
1) Return valid JSON only.
2) Keep these keys exactly unchanged from input:
   - advertisement_no
   - important_dates
   - application_fee
   - official_links
   - source
3) Rewrite and sanitize content-heavy fields to be concise and original:
   - title
   - short_description
   - post_name
   - conducting_body
   - eligibility_criteria.qualification
   - application_process
   - selection_process
   - important_notes
   - syllabus_overview
4) Do not invent factual values. If uncertain, keep original.
5) Remove "click here", "question/answer", and boilerplate phrasing from rewritten fields.
6) Keep JSON structure and key names same as input.
7) For non-locked keys, you may normalize/clean structure, but do not alter factual numbers/dates/quantities present in input.
8) exam_scheme may contain structured objects (nested JSON), not only strings.
9) If physical standards are available in exam_scheme.physical_standard_test text or other_tables physical table, convert to structured object format:
   {
     "male": { "general_bc": {...}, "other": {...} },
     "female": { "general_bc": {...}, "other": {...} }
   }
   Keep values factual from input only (height/chest/weight), set missing ones to null.
10) If you cannot confidently structure physical_standard_test, keep original value unchanged.
""";
    }

    private String buildDirectUrlMasterPrompt(String sourceUrl) {
        return """
You are a job-data extraction assistant.

Task:
1) Read the job page at this URL: %s
2) Return exactly one valid JSON object in this master DTO shape.
3) If any field is unavailable, set it to null (or [] / {} for list/map fields).
4) Do not include markdown, comments, or extra text.
5) Prefer concise, original wording for title/description/notes (avoid verbatim copying long source text).

Output schema (snake_case):
{
  "title": "",
  "short_description": "",
  "advertisement_no": "",
  "post_name": "",
  "conducting_body": "",
  "important_dates": {
    "notification_date": "",
    "online_apply_start_date": "",
    "online_apply_last_date": "",
    "last_date_for_fee_payment": "",
    "correction_window": "",
    "exam_date": "",
    "admit_card": "",
    "result_date": ""
  },
  "application_fee": {
    "general_obc": "",
    "sc_st_ebc_female_transgender": "",
    "refund_general_obc_after_cbt": "",
    "refund_sc_st_ebc_female_transgender_after_cbt": "",
    "payment_mode": []
  },
  "eligibility_criteria": {
    "minimum_age": "",
    "maximum_age": "",
    "age_as_on": "",
    "qualification": ""
  },
  "vacancy_details": {
    "total_vacancy": "",
    "post_wise": {},
    "category_wise": {}
  },
  "pay_scale": "",
  "application_process": [],
  "exam_scheme": {},
  "selection_process": [],
  "important_notes": [],
  "official_links": {},
  "source": "",
  "syllabus_overview": []
}

Important:
- Keep `source` equal to the given URL.
- Keep `important_dates` and `official_links` as factual extraction only.
- Do not fabricate values.
""".formatted(sourceUrl);
    }

    private String buildNarrativeRewritePrompt(String narrativeJson) {
        return """
You are cleaning and rewriting text fields of a scraped job JSON.

Input JSON (only narrative fields):
""" + narrativeJson + """

Rules:
1) Return valid JSON only.
2) Keep the same keys exactly:
   - title
   - short_description
   - post_name
   - application_process
   - important_notes
   - syllabus_overview
   - exam_scheme
   - context_source
   - context_conducting_body
3) Rewrite only text-heavy fields to concise, original wording:
   title, short_description, post_name, application_process, important_notes, syllabus_overview, exam_scheme values.
4) Do not fabricate facts, dates, links, fee values, or eligibility values.
5) Remove boilerplate phrases like "click here", "apply online link", question-answer style text, and repetitive disclaimers.
6) Keep arrays as arrays and exam_scheme keys unchanged.
7) exam_scheme values can be nested objects when structure is clearly available (e.g., physical standards table).
8) Keep context_source and context_conducting_body unchanged.
""";
    }



    private String buildRequestBody(String prompt) {

        String escapedPrompt = escapeJson(prompt);

        return """
        {
          "model": "gpt-4.1-mini",
          "messages": [
            {
              "role": "system",
              "content": "You are a professional job JSON formatter."
            },
            {
              "role": "user",
              "content": "%s"
            }
          ],
          "temperature": 0.05,
          "max_tokens": 6000
        }
        """.formatted(escapedPrompt);
    }


    private String escapeJson(String text) {

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    public String extractCleanJson(String openAiResponse) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        // Parse full OpenAI response
        JsonNode root = mapper.readTree(openAiResponse);

        // Get content field
        String content = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        // Remove markdown if present
        content = content
                .replace("```json", "")
                .replace("```", "")
                .trim();

        return content;
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate) throws Exception {
        String prompt = buildCurrentAffairsPrompt(quizDate, 20, List.of(), DEFAULT_CURRENT_AFFAIRS_TOPICS);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                                " | " + response.message()
                );
            }

            String raw = response.body().string();
            return extractCleanJson(raw);
        }
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate, int count, List<String> avoidQuestions) throws Exception {
        return generateCurrentAffairsQuizJson(quizDate, count, avoidQuestions, DEFAULT_CURRENT_AFFAIRS_TOPICS);
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) throws Exception {
        String prompt = buildCurrentAffairsPrompt(quizDate, count, avoidQuestions, topicAreas);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                                " | " + response.message()
                );
            }

            String raw = response.body().string();
            return extractCleanJson(raw);
        }
    }

    public String generateCurrentAffairsQuizQuestions(LocalDate quizDate, int count, List<String> avoidQuestions) throws Exception {
        return generateCurrentAffairsQuizQuestions(quizDate, count, avoidQuestions, DEFAULT_CURRENT_AFFAIRS_TOPICS);
    }

    public String generateCurrentAffairsQuizQuestions(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) throws Exception {
        String prompt = buildCurrentAffairsQuestionsOnlyPrompt(quizDate, count, avoidQuestions, topicAreas);
        String requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new RuntimeException(
                        "OpenAI API Error: " + response.code() +
                                " | " + response.message()
                );
            }

            String raw = response.body().string();
            return extractCleanJson(raw);
        }
    }

    private String buildCurrentAffairsPrompt(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) {
        String avoidBlock = buildAvoidQuestionsBlock(avoidQuestions);
        List<String> effectiveTopics = normalizeTopicAreas(topicAreas);
        String topicAreasBlock = buildTopicAreasBlock(effectiveTopics);
        int currentYear = quizDate.getYear();
        int previousYear = currentYear - 1;
        return """
Generate exactly one JSON object for a daily current affairs MCQ quiz.
Target date: %s

Rules:
1) Return valid JSON only. No markdown, no comments, no extra text.
2) Include top-level keys:
   - title
   - quiz_date (YYYY-MM-DD)
   - topic (Current Affairs)
   - total_questions (must be %d)
   - questions (array of %d items)
3) Each question item must contain:
   - question
   - options (array of exactly 4 string options)
   - correct_option (must exactly match one option text)
   - difficulty_level (Easy/Medium/Hard)
   - topic_area (must exactly match one listed topic area)
   - exam_name (example: UPSC, SSC, Banking, Railways, State PSC)
   - details_description (2-4 lines explanation)
4) Questions must be based on India + global current affairs from %d and %d only, and should be useful for competitive exams.
5) Keep language clear and exam-ready.
6) Ensure no duplicate questions and no empty fields.
7) Use only the following topic areas:
%s
8) Ensure the quiz includes at least one question from each listed topic area.
%s
""".formatted(quizDate, count, count, currentYear, previousYear, topicAreasBlock, avoidBlock);
    }

    private String buildCurrentAffairsQuestionsOnlyPrompt(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) {
        String avoidBlock = buildAvoidQuestionsBlock(avoidQuestions);
        List<String> effectiveTopics = normalizeTopicAreas(topicAreas);
        String topicAreasBlock = buildTopicAreasBlock(effectiveTopics);
        String topicAreasInline = String.join(", ", effectiveTopics);
        int currentYear = quizDate.getYear();
        int previousYear = currentYear - 1;
        return """
Generate a JSON array of exactly %d MCQ questions for a daily current affairs quiz.
Target date: %s

Rules:
1) Return a JSON array only. No markdown, no comments, no extra text.
2) Each question item must contain:
   - question
   - options (array of exactly 4 string options)
   - correct_option (must exactly match one option text)
   - difficulty_level (Easy/Medium/Hard)
   - topic_area (must exactly match one listed topic area)
   - exam_name (example: UPSC, SSC, Banking, Railways, State PSC)
   - details_description (2-4 lines explanation)
3) Questions must be based on India + global current affairs from %d and %d only, and should be useful for competitive exams.
4) Use only the following topic areas:
%s
5) Prioritize distinct coverage across topic areas before repeating.
6) Give highest priority to these topic areas for this call: %s
7) Keep language clear and exam-ready.
8) Ensure no duplicate questions and no empty fields.
%s
""".formatted(count, quizDate, currentYear, previousYear, topicAreasBlock, topicAreasInline, avoidBlock);
    }

    private String buildAvoidQuestionsBlock(List<String> avoidQuestions) {
        if (avoidQuestions == null || avoidQuestions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Do not repeat or paraphrase any of the following questions:\n");
        int limit = Math.min(avoidQuestions.size(), 120);
        for (int i = 0; i < limit; i++) {
            sb.append("- ").append(avoidQuestions.get(i)).append("\n");
        }
        return sb.toString();
    }

    private List<String> normalizeTopicAreas(List<String> topicAreas) {
        if (topicAreas == null || topicAreas.isEmpty()) {
            return DEFAULT_CURRENT_AFFAIRS_TOPICS;
        }
        List<String> normalized = new ArrayList<>();
        for (String topic : topicAreas) {
            if (topic == null || topic.isBlank()) {
                continue;
            }
            String trimmed = topic.trim();
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            return DEFAULT_CURRENT_AFFAIRS_TOPICS;
        }
        return normalized;
    }

    private String buildTopicAreasBlock(List<String> topicAreas) {
        StringBuilder sb = new StringBuilder();
        for (String topic : topicAreas) {
            sb.append("- ").append(topic).append("\n");
        }
        return sb.toString();
    }
}
