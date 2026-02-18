package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
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
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();
    }


    /**
     * Process raw job Map and return standardized JSON
     */
    public String processJobMap(Map<String, Object> resp) throws IOException {

        // 1️⃣ Convert Map → JSON
        String rawJson = objectMapper.writeValueAsString(resp);

        // 2️⃣ Build Prompt
        String prompt = buildPrompt(rawJson);

        // 3️⃣ Build Request Body
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
 """ ;
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
          "max_tokens": 4000
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

}

