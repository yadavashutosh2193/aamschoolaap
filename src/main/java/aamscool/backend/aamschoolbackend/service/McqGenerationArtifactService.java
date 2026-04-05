package aamscool.backend.aamschoolbackend.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class McqGenerationArtifactService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern SAFE_FILE = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private final ObjectMapper objectMapper;

    @Value("${app.mcq.artifacts.dir:artifacts/mcq-generation}")
    private String artifactDir;

    public McqGenerationArtifactService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String saveArtifact(String examKey, Map<String, Object> payload) {
        try {
            Path dir = Paths.get(artifactDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String safeExam = normalizeFilePart(examKey == null ? "exam" : examKey);
            String fileName = LocalDateTime.now().format(TS)
                    + "-" + safeExam
                    + "-" + UUID.randomUUID().toString().substring(0, 8)
                    + ".json";
            Path file = dir.resolve(fileName).normalize();
            if (!file.startsWith(dir)) {
                throw new IllegalArgumentException("Invalid artifact path");
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return fileName;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save MCQ generation artifact: " + ex.getMessage(), ex);
        }
    }

    public byte[] readArtifact(String artifactId) {
        validateArtifactId(artifactId);
        try {
            Path dir = Paths.get(artifactDir).toAbsolutePath().normalize();
            Path file = dir.resolve(artifactId).normalize();
            if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Artifact not found");
            }
            return Files.readAllBytes(file);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read artifact: " + ex.getMessage(), ex);
        }
    }

    private void validateArtifactId(String artifactId) {
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId is required");
        }
        if (!SAFE_FILE.matcher(artifactId).matches()) {
            throw new IllegalArgumentException("Invalid artifactId");
        }
        if (!artifactId.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("Only .json artifact is allowed");
        }
    }

    private String normalizeFilePart(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "exam" : normalized;
    }
}
