package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.time.LocalDate;
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
        String prompt = buildCurrentAffairsPrompt(quizDate, 20, List.of());
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
        String prompt = buildCurrentAffairsPrompt(quizDate, count, avoidQuestions);
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
        String prompt = buildCurrentAffairsQuestionsOnlyPrompt(quizDate, count, avoidQuestions);
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

    private String buildCurrentAffairsPrompt(LocalDate quizDate, int count, List<String> avoidQuestions) {
        String avoidBlock = buildAvoidQuestionsBlock(avoidQuestions);
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
   - exam_name (example: UPSC, SSC, Banking, Railways, State PSC)
   - details_description (2-4 lines explanation)
4) Questions must be based on recent India + global current affairs and should be useful for competitive exams.
5) Keep language clear and exam-ready.
6) Ensure no duplicate questions and no empty fields.
7) Cover a balanced mix of topics: polity/governance, economy, science & tech, environment, international relations, and national schemes.
%s
""".formatted(quizDate, count, count, avoidBlock);
    }

    private String buildCurrentAffairsQuestionsOnlyPrompt(LocalDate quizDate, int count, List<String> avoidQuestions) {
        String avoidBlock = buildAvoidQuestionsBlock(avoidQuestions);
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
   - exam_name (example: UPSC, SSC, Banking, Railways, State PSC)
   - details_description (2-4 lines explanation)
3) Questions must be based on recent India + global current affairs and should be useful for competitive exams.
4) Keep language clear and exam-ready.
5) Ensure no duplicate questions and no empty fields.
%s
""".formatted(count, quizDate, avoidBlock);
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
}
