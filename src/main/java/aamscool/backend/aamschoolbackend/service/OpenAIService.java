package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.models:gpt-4.1-mini,gpt-4o-mini}")
    private String configuredModels;

    @Value("${openai.max-retries:3}")
    private int maxRetries;

    @Value("${openai.mcq.models:gpt-4.1-mini,gpt-4o-mini}")
    private String mcqConfiguredModels;

    @Value("${openai.mcq.premium-models:gpt-4.1,gpt-4o}")
    private String mcqPremiumConfiguredModels;

    @Value("${openai.mcq.enable-premium-fallback:true}")
    private boolean mcqEnablePremiumFallback;

    @Value("${openai.mcq.max-retries:2}")
    private int mcqMaxRetries;

    @Value("${openai.mcq.max-tokens:3200}")
    private int mcqMaxTokens;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_TOKENS = 6000;
    private static final long RETRY_DELAY_BASE_MS = 800L;
    private static final int MAX_AVOID_PROMPT_QUESTIONS = 120;
    private static final int MAX_AVOID_PROMPT_CHARS = 6000;
    private static final int MAX_AVOID_QUESTION_LENGTH = 220;

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

        return executePromptRaw(prompt);
    }

    /**
     * Refine master job JSON content while preserving source-critical sections.
     */
    public String refineMasterJobContent(String rawMasterJson) throws IOException {
        String prompt = buildMasterRefinePrompt(rawMasterJson);
        String raw = executePromptRaw(prompt);
        try {
            return extractCleanJson(raw);
        } catch (Exception ex) {
            return rawMasterJson;
        }
    }

    /**
     * Direct URL -> master DTO JSON via OpenAI prompt only (no local scraper parsing).
     */
    public String generateMasterJsonFromUrlDirect(String sourceUrl) throws IOException {
        String prompt = buildDirectUrlMasterPrompt(sourceUrl);
        String raw = executePromptRaw(prompt);
        try {
            return extractCleanJson(raw);
        } catch (Exception ex) {
            return raw;
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
3. Improve wording for SEO readability, but keep content factual and concise.
4. Keep AdSense-friendly tone: no exaggerated claims, no fake urgency, no misleading statements.
5. Must include all useful source links from input under important links/offical links keys; do not drop valid links.
6. Always include a useful short description and vacancy details if available.
7. Do not put exam pattern inside vacancy details; vacancy_details should focus on total/post/category vacancy.
8. Create clear exam process and exam pattern sections when data exists.
9. Do not use any parent key (JSON must start with {).
10. Use key title, advertisement_no, post_name, eligibility (including age + qualification), and keep naming stable.
11. Title should be search-friendly and factual (prefer 55-100 chars, avoid all-caps and clickbait words like "urgent", "hurry", "must apply now").
12. short_description should be 120-220 chars, factual, and not repetitive.
13. Never invent numbers, dates, or links. If unknown, keep null/empty.
Return final valid JSON only.
 """;
    }

    private String buildMasterRefinePrompt(String rawJson) {
        return """
You are a JSON refiner for Indian government job pages.
Refine the input JSON for SEO readability and AdSense-safe content quality while preserving factual integrity.

Input JSON:
""" + rawJson + """

Rules:
1) Return valid JSON only.
2) Keep exactly the same top-level schema and keys from input.
3) Improve wording quality for:
   - title
   - short_description
   - important_notes
   - application_process
4) SEO rules:
   - title should be clear, factual, and search-friendly
   - avoid keyword stuffing and repeated words
   - avoid all-caps title
   - keep title preferably 55-100 characters
   - short_description should be 120-220 characters
5) AdSense-safe rules:
   - no sensational/clickbait phrases ("urgent", "hurry", "guaranteed selection", "100% sure", etc.)
   - no misleading claims or unverifiable promises
   - no abusive/adult/gambling language
6) Do not fabricate dates, fees, vacancies, eligibility, or links.
7) Preserve all official links from input unless clearly malformed duplicates.
8) Keep output concise and user-trust focused.
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
  "date_posted": "",
  "date_updated": "",
  "job_location": {
    "country": "",
    "states": [],
    "city": "",
    "location_text": ""
  },
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

    private String executePromptRaw(String prompt) throws IOException {
        List<String> models = resolveModels();
        IOException last = null;
        for (String model : models) {
            try {
                return executePromptRawWithRetries(prompt, model, maxRetries, null, MAX_TOKENS);
            } catch (IOException ex) {
                last = ex;
                log.warn("OpenAI call failed for model {}: {}", model, ex.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("No OpenAI model configured");
    }

    private String executePromptRawForMcq(String prompt, boolean allowPremiumFallback) throws IOException {
        List<String> primaryModels = resolveModels(mcqConfiguredModels, List.of("gpt-4.1-mini", "gpt-4o-mini"));
        Map<String, Object> responseFormat = buildExamMcqResponseFormat();
        IOException lastPrimary = null;
        for (String model : primaryModels) {
            try {
                return executePromptRawWithRetries(prompt, model, mcqMaxRetries, responseFormat, mcqMaxTokens);
            } catch (IOException ex) {
                lastPrimary = ex;
                log.warn("OpenAI MCQ call failed for model {}: {}", model, ex.getMessage());
            }
        }

        if (allowPremiumFallback && mcqEnablePremiumFallback) {
            List<String> premiumModels = resolveModels(mcqPremiumConfiguredModels, List.of("gpt-4.1", "gpt-4o"));
            IOException lastPremium = null;
            for (String model : premiumModels) {
                try {
                    log.info("Switching to premium MCQ model fallback: {}", model);
                    return executePromptRawWithRetries(prompt, model, mcqMaxRetries, responseFormat, mcqMaxTokens);
                } catch (IOException ex) {
                    lastPremium = ex;
                    log.warn("OpenAI premium MCQ call failed for model {}: {}", model, ex.getMessage());
                }
            }
            if (lastPremium != null) {
                throw lastPremium;
            }
        }

        if (lastPrimary != null) {
            throw lastPrimary;
        }
        throw new IOException("No OpenAI MCQ model configured");
    }

    private String executePromptRawWithRetries(String prompt,
                                               String model,
                                               int retryLimit,
                                               Map<String, Object> responseFormat,
                                               int maxTokens) throws IOException {
        int attempts = Math.max(1, retryLimit);
        IOException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return executePromptRawSingle(prompt, model, responseFormat, maxTokens);
            } catch (OpenAiRequestException ex) {
                last = ex;
                if (!ex.retryable || attempt >= attempts) {
                    throw ex;
                }
                sleepBeforeRetry(attempt, model, ex.getMessage());
            } catch (IOException ex) {
                last = ex;
                if (attempt >= attempts) {
                    throw ex;
                }
                sleepBeforeRetry(attempt, model, ex.getMessage());
            }
        }
        throw last == null ? new IOException("OpenAI request failed") : last;
    }

    private String executePromptRawSingle(String prompt,
                                          String model,
                                          Map<String, Object> responseFormat,
                                          int maxTokens) throws IOException {
        String requestBody = buildRequestBody(prompt, model, responseFormat, maxTokens);
        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .post(RequestBody.create(requestBody, JSON))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() == null ? null : response.body().string();
                String errorDetail = extractOpenAiErrorDetail(errorBody);
                String message = "OpenAI API Error [model=%s, status=%d]: %s".formatted(
                        model,
                        response.code(),
                        errorDetail
                );
                throw new OpenAiRequestException(message, response.code(), isRetryableStatus(response.code()));
            }
            if (response.body() == null) {
                throw new OpenAiRequestException(
                        "OpenAI API Error [model=%s]: empty response body".formatted(model),
                        502,
                        true
                );
            }
            return response.body().string();
        }
    }

    private String buildRequestBody(String prompt,
                                    String model,
                                    Map<String, Object> responseFormat,
                                    int maxTokens) throws IOException {
        String systemMessage = responseFormat == null
                ? "You are a professional JSON formatter."
                : "You are an expert Hindi exam question setter for Indian competitive exams. Return strict JSON only.";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", prompt == null ? "" : prompt)
        ));
        payload.put("temperature", responseFormat == null ? 0.05 : 0.0);
        payload.put("max_tokens", Math.max(256, maxTokens));
        if (responseFormat != null && !responseFormat.isEmpty()) {
            payload.put("response_format", ensureTopLevelObjectSchema(responseFormat));
        }
        return objectMapper.writeValueAsString(payload);
    }

    private Map<String, Object> ensureTopLevelObjectSchema(Map<String, Object> responseFormat) {
        if (responseFormat == null || responseFormat.isEmpty()) {
            return responseFormat;
        }

        Object jsonSchemaObj = responseFormat.get("json_schema");
        if (!(jsonSchemaObj instanceof Map<?, ?> jsonSchemaRaw)) {
            return responseFormat;
        }

        Map<String, Object> jsonSchema = toStringKeyMap(jsonSchemaRaw);
        Object schemaObj = jsonSchema.get("schema");
        if (!(schemaObj instanceof Map<?, ?> schemaRaw)) {
            return responseFormat;
        }

        Map<String, Object> schema = toStringKeyMap(schemaRaw);
        String rootType = trimOrNull(schema.get("type") == null ? null : String.valueOf(schema.get("type")));
        if (!"array".equalsIgnoreCase(rootType)) {
            return responseFormat;
        }

        // OpenAI json_schema requires top-level object; wrap legacy array schema under "questions".
        Map<String, Object> wrappedSchema = new LinkedHashMap<>();
        wrappedSchema.put("type", "object");
        wrappedSchema.put("additionalProperties", false);
        wrappedSchema.put("required", List.of("questions"));
        wrappedSchema.put("properties", Map.of("questions", schema));
        jsonSchema.put("schema", wrappedSchema);

        Map<String, Object> sanitizedResponseFormat = new LinkedHashMap<>(responseFormat);
        sanitizedResponseFormat.put("json_schema", jsonSchema);
        log.warn("response_format json_schema had top-level array; auto-wrapped into object with 'questions' key.");
        return sanitizedResponseFormat;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                out.put(key, entry.getValue());
            }
        }
        return out;
    }

    private List<String> resolveModels() {
        return resolveModels(configuredModels, List.of("gpt-4.1-mini", "gpt-4o-mini"));
    }

    private List<String> resolveModels(String configured, List<String> defaults) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (configured != null) {
            String[] parts = configured.split(",");
            for (String part : parts) {
                String model = trimOrNull(part);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        if (models.isEmpty()) {
            models.addAll(defaults);
        }
        return new ArrayList<>(models);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 409
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private void sleepBeforeRetry(int attempt, String model, String reason) throws IOException {
        long delay = RETRY_DELAY_BASE_MS * (1L << Math.max(0, attempt - 1));
        long boundedDelay = Math.min(delay, 4000L);
        log.warn("Retrying OpenAI call [model={}, attempt={}, delayMs={}] due to: {}",
                model, attempt + 1, boundedDelay, reason);
        try {
            Thread.sleep(boundedDelay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry OpenAI request", ex);
        }
    }

    private Map<String, Object> buildExamMcqResponseFormat() {
        Map<String, Object> choiceSchema = new LinkedHashMap<>();
        choiceSchema.put("type", "object");
        choiceSchema.put("additionalProperties", false);
        choiceSchema.put("required", List.of("id", "text", "image"));
        choiceSchema.put("properties", Map.of(
                "id", Map.of("type", "integer", "minimum", 1, "maximum", 4),
                "text", Map.of("type", "string", "minLength", 1),
                "image", Map.of("type", "string")
        ));

        Map<String, Object> correctAnswerSchema = new LinkedHashMap<>();
        correctAnswerSchema.put("type", "object");
        correctAnswerSchema.put("additionalProperties", false);
        correctAnswerSchema.put("required", List.of("choiceIds", "textAnswer"));
        correctAnswerSchema.put("properties", Map.of(
                "choiceIds", Map.of(
                        "type", "array",
                        "minItems", 1,
                        "maxItems", 1,
                        "items", Map.of("type", "integer", "minimum", 1, "maximum", 4)
                ),
                "textAnswer", Map.of("type", "string")
        ));

        Map<String, Object> explanationSchema = new LinkedHashMap<>();
        explanationSchema.put("type", "object");
        explanationSchema.put("additionalProperties", false);
        explanationSchema.put("required", List.of("text", "image", "videoUrl"));
        explanationSchema.put("properties", Map.of(
                "text", Map.of("type", "string", "minLength", 28),
                "image", Map.of("type", "string"),
                "videoUrl", Map.of("type", "string")
        ));

        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("questionText", Map.of("type", "string", "minLength", 12));
        itemProperties.put("questionImage", Map.of("type", "string"));
        itemProperties.put("choices", Map.of(
                "type", "array",
                "minItems", 4,
                "maxItems", 4,
                "items", choiceSchema
        ));
        itemProperties.put("correctAnswer", correctAnswerSchema);
        itemProperties.put("explanation", explanationSchema);

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("additionalProperties", false);
        itemSchema.put("required", List.of(
                "questionText",
                "questionImage",
                "choices",
                "correctAnswer",
                "explanation"
        ));
        itemSchema.put("properties", itemProperties);

        Map<String, Object> rootSchema = new LinkedHashMap<>();
        rootSchema.put("type", "object");
        rootSchema.put("additionalProperties", false);
        rootSchema.put("required", List.of("questions"));
        rootSchema.put("properties", Map.of(
                "questions", Map.of(
                        "type", "array",
                        "minItems", 1,
                        "items", itemSchema
                )
        ));

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "exam_subject_topic_mcq_generation");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", rootSchema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }

    public String extractCleanJson(String openAiResponse) throws Exception {
        if (openAiResponse == null) {
            return null;
        }
        String raw = openAiResponse.trim();
        if (raw.isBlank()) {
            return raw;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = extractMessageContent(root);
            if (content != null && !content.isBlank()) {
                return stripCodeFence(content);
            }
            if (root.has("error")) {
                String detail = extractOpenAiErrorDetail(root.toString());
                throw new IllegalArgumentException("OpenAI error payload: " + detail);
            }
            if (root.isArray() || root.has("questions") || root.has("title")) {
                return stripCodeFence(root.toString());
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            return stripCodeFence(raw);
        }
        return stripCodeFence(raw);
    }

    private String extractMessageContent(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode contentNode = choices.get(0).path("message").path("content");
            String content = readMessageContentNode(contentNode);
            if (content != null) {
                return content;
            }
        }
        return trimOrNull(root.path("output_text").asText(null));
    }

    private String readMessageContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return trimOrNull(contentNode.asText(null));
        }
        if (!contentNode.isArray()) {
            return trimOrNull(contentNode.asText(null));
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : contentNode) {
            String partText;
            if (part.isTextual()) {
                partText = trimOrNull(part.asText(null));
            } else {
                partText = firstNonBlank(
                        part.path("text").asText(null),
                        part.path("content").asText(null),
                        part.path("value").asText(null)
                );
            }
            if (partText == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(partText);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String stripCodeFence(String content) {
        if (content == null) {
            return null;
        }
        return content
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
    }

    private String extractOpenAiErrorDetail(String errorBody) {
        String fallback = trimOrNull(errorBody);
        if (fallback == null) {
            return "No error body";
        }
        try {
            JsonNode node = objectMapper.readTree(fallback);
            String message = firstNonBlank(
                    node.path("error").path("message").asText(null),
                    node.path("message").asText(null),
                    node.path("error").asText(null)
            );
            if (message != null) {
                return message;
            }
        } catch (Exception ignored) {
            // Keep plain body fallback.
        }
        if (fallback.length() > 500) {
            return fallback.substring(0, 500) + "...[truncated]";
        }
        return fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimOrNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate) throws Exception {
        String prompt = buildCurrentAffairsPrompt(quizDate, 20, List.of(), DEFAULT_CURRENT_AFFAIRS_TOPICS);
        String raw = executePromptRaw(prompt);
        return extractCleanJson(raw);
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate, int count, List<String> avoidQuestions) throws Exception {
        return generateCurrentAffairsQuizJson(quizDate, count, avoidQuestions, DEFAULT_CURRENT_AFFAIRS_TOPICS);
    }

    public String generateCurrentAffairsQuizJson(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) throws Exception {
        String prompt = buildCurrentAffairsPrompt(quizDate, count, avoidQuestions, topicAreas);
        String raw = executePromptRaw(prompt);
        return extractCleanJson(raw);
    }

    public String generateCurrentAffairsQuizQuestions(LocalDate quizDate, int count, List<String> avoidQuestions) throws Exception {
        return generateCurrentAffairsQuizQuestions(quizDate, count, avoidQuestions, DEFAULT_CURRENT_AFFAIRS_TOPICS);
    }

    public String generateCurrentAffairsQuizQuestions(LocalDate quizDate, int count, List<String> avoidQuestions,
            List<String> topicAreas) throws Exception {
        String prompt = buildCurrentAffairsQuestionsOnlyPrompt(quizDate, count, avoidQuestions, topicAreas);
        String raw = executePromptRaw(prompt);
        return extractCleanJson(raw);
    }

    public String generateExamSubjectTopicQuestions(String examName,
                                                    String subject,
                                                    String topic,
                                                    int count,
                                                    String difficultyLevel,
                                                    String language,
                                                    List<String> avoidQuestions) throws Exception {
        return generateExamSubjectTopicQuestions(
                examName,
                subject,
                topic,
                count,
                difficultyLevel,
                language,
                avoidQuestions,
                false,
                null
        );
    }

    public String generateExamSubjectTopicQuestions(String examName,
                                                    String subject,
                                                    String topic,
                                                    int count,
                                                    String difficultyLevel,
                                                    String language,
                                                    List<String> avoidQuestions,
                                                    boolean allowPremiumFallback) throws Exception {
        return generateExamSubjectTopicQuestions(
                examName,
                subject,
                topic,
                count,
                difficultyLevel,
                language,
                avoidQuestions,
                allowPremiumFallback,
                null
        );
    }

    public String generateExamSubjectTopicQuestions(String examName,
                                                    String subject,
                                                    String topic,
                                                    int count,
                                                    String difficultyLevel,
                                                    String language,
                                                    List<String> avoidQuestions,
                                                    boolean allowPremiumFallback,
                                                    String qualityFeedback) throws Exception {
        String prompt = buildExamSubjectTopicQuestionsPrompt(
                examName,
                subject,
                topic,
                count,
                difficultyLevel,
                language,
                avoidQuestions,
                qualityFeedback
        );
        String raw = executePromptRawForMcq(prompt, allowPremiumFallback);
        return extractCleanJson(raw);
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

    private String buildExamSubjectTopicQuestionsPrompt(String examName,
                                                        String subject,
                                                        String topic,
                                                        int count,
                                                        String difficultyLevel,
                                                        String language,
                                                        List<String> avoidQuestions,
                                                        String qualityFeedback) {
        String avoidBlock = buildAvoidQuestionsBlock(avoidQuestions);
        String qualityFeedbackBlock = buildQualityFeedbackBlock(qualityFeedback);
        String safeExam = examName == null ? "" : examName.trim();
        String examDifficultyContext = safeExam.isBlank()
                ? "Use a competitive government-exam standard."
                : "Use the exact difficulty standard expected in " + safeExam + " exam.";
        String safeDifficulty = difficultyLevel == null ? "Medium" : difficultyLevel.trim();
        String safeLanguage = language == null || language.isBlank() ? "HINDI" : language.trim();
        return """
Generate exactly %d exam-ready MCQ questions as a JSON object with top-level key "questions".

Context:
- Exam Name: %s
- Subject: %s
- Topic: %s
- Difficulty: %s
- Output Language: %s
- Exam Difficulty Context: %s

Rules:
1) Return only valid JSON in this shape:
   {"questions":[ ... ]}
   No markdown, no comments, no extra text.
2) Output must align with existing QuestionDto structure for persistence compatibility.
3) Every item inside "questions" must contain exactly these keys:
   - questionText
   - questionImage (optional, keep empty string if not needed)
   - choices (array of exactly 4 objects: {id, text, image}; keep image empty string if not needed)
   - correctAnswer ({choiceIds:[1..4], textAnswer:""})
   - explanation ({text, image:"", videoUrl:""})
4) Use Hindi language (Devanagari) for questionText, choices.text and explanation.text.
5) explanation.text must be concise but useful (2-3 lines) with exam-relevant reasoning and elimination logic.
6) Every question must strictly belong to the provided subject and topic.
7) Keep the requested difficulty level consistent for all questions in this call.
8) Questions must be unique and non-repetitive.
9) Avoid ambiguous or multiple-correct answers.
10) Keep language clear for competitive exam candidates.
11) Never repeat question stem from the avoid list. If unique questions are exhausted, return fewer items instead of duplicates.
12) Calibrate every question to the exam level of "%s" (pattern, reasoning depth, trap options, and factual granularity).
13) Difficulty labels are relative to this exam standard:
    - Easy: easy for %s aspirants, not beginner/school level
    - Medium: typical %s level
    - Hard: above typical %s level with deeper elimination and conceptual traps
14) Use formal exam-style Hindi wording; avoid Hinglish and conversational tone.
15) Prefer question archetypes seen in competitive exams:
    fact-based, statement-based, assertion-reason, match-the-following, chronology, and applied conceptual MCQs.
16) Distractors must be plausible and close to the right answer; avoid obviously wrong options.
17) Keep output compact: avoid unnecessarily long explanations.
18) If correction feedback is provided, strictly fix those issues in this response.
19) If schema or language constraints cannot be satisfied for a question, skip that question instead of returning broken structure.
%s
%s
""".formatted(
                count,
                safeExam,
                subject,
                topic,
                safeDifficulty,
                safeLanguage,
                examDifficultyContext,
                safeExam.isBlank() ? "the target exam" : safeExam,
                safeExam.isBlank() ? "the target exam" : safeExam,
                safeExam.isBlank() ? "the target exam" : safeExam,
                safeExam.isBlank() ? "the target exam" : safeExam,
                qualityFeedbackBlock,
                avoidBlock
        );
    }

    private String buildQualityFeedbackBlock(String qualityFeedback) {
        String feedback = trimOrNull(qualityFeedback);
        if (feedback == null) {
            return "";
        }
        String cleaned = feedback.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 1400) {
            cleaned = cleaned.substring(0, 1400) + "...";
        }
        return "Correction feedback from previous attempt:\n- " + cleaned + "\n";
    }

    private String buildAvoidQuestionsBlock(List<String> avoidQuestions) {
        if (avoidQuestions == null || avoidQuestions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Do not repeat or paraphrase any of the following questions:\n");
        int limit = Math.min(avoidQuestions.size(), MAX_AVOID_PROMPT_QUESTIONS);
        int totalChars = 0;
        for (int i = 0; i < limit; i++) {
            String clean = trimOrNull(avoidQuestions.get(i));
            if (clean == null) {
                continue;
            }
            clean = clean.replaceAll("\\s+", " ");
            if (clean.length() > MAX_AVOID_QUESTION_LENGTH) {
                clean = clean.substring(0, MAX_AVOID_QUESTION_LENGTH) + "...";
            }
            if (totalChars + clean.length() > MAX_AVOID_PROMPT_CHARS) {
                break;
            }
            sb.append("- ").append(clean).append("\n");
            totalChars += clean.length();
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

    private static class OpenAiRequestException extends IOException {
        private final int statusCode;
        private final boolean retryable;

        private OpenAiRequestException(String message, int statusCode, boolean retryable) {
            super(message);
            this.statusCode = statusCode;
            this.retryable = retryable;
        }
    }
}
