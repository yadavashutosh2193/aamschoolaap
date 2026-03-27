package aamscool.backend.aamschoolbackend.controllers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aamscool.backend.aamschoolbackend.dto.CurrentAffairsQuizAttemptStatsDto;
import aamscool.backend.aamschoolbackend.dto.CurrentAffairsQuizSubmitRequest;
import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.QuizListItem;
import aamscool.backend.aamschoolbackend.service.CurrentAffairsQuizService;
import aamscool.backend.aamschoolbackend.service.UserAccountService;

@RestController
@RequestMapping("/api/current-affairs-quizzes")
public class CurrentAffairsQuizController {

    private static final Logger log = LoggerFactory.getLogger(CurrentAffairsQuizController.class);
    @Autowired
    private CurrentAffairsQuizService quizService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private CurrentAffairsQuizScheduler currentAffairsQuizScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final ZoneId INDIA_TIMEZONE = ZoneId.of("Asia/Kolkata");

    @GetMapping
    public List<QuizListItem> getQuizTitles() {
        return quizService.getQuizTitles();
    }

    @GetMapping("/{slugWithId}")
    public ResponseEntity<?> getQuizBySlug(@PathVariable("slugWithId") String slugWithId) {
        long id = extractIdFromSlug(slugWithId);
        if (id == -1) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid quiz slug"));
        }

        Optional<CurrentAffairsQuiz> quiz = quizService.getQuiz(id);
        if (quiz.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            JsonNode content = objectMapper.readTree(quiz.get().getContent());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content.toString());
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Saved quiz content is not valid JSON"));
        }
    }

    @PostMapping("/generate-now")
    public ResponseEntity<?> generateNow() {
        try {
            CurrentAffairsQuiz quiz = quizService.generateAndSaveTodayQuiz();
            return ResponseEntity.ok(Map.of(
                    "quizId", quiz.getQuizId(),
                    "title", quiz.getTitle(),
                    "date", quiz.getCreatedAt().toString()
            ));
        } catch (Exception ex) {
            log.error("Failed to generate quiz via /generate-now", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to generate quiz",
                    "error", ex.getMessage()
            ));
        }
    }

    @GetMapping("/admin/scheduler/status")
    public ResponseEntity<?> getSchedulerStatus(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        return ResponseEntity.ok(Map.of(
                "schedulerEnabled", currentAffairsQuizScheduler.isSchedulerEnabled()
        ));
    }

    @PostMapping("/admin/scheduler/enable")
    public ResponseEntity<?> enableScheduler(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        boolean enabled = currentAffairsQuizScheduler.setSchedulerEnabled(true);
        return ResponseEntity.ok(Map.of(
                "schedulerEnabled", enabled,
                "message", "Daily quiz scheduler enabled"
        ));
    }

    @PostMapping("/admin/scheduler/disable")
    public ResponseEntity<?> disableScheduler(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        boolean enabled = currentAffairsQuizScheduler.setSchedulerEnabled(false);
        return ResponseEntity.ok(Map.of(
                "schedulerEnabled", enabled,
                "message", "Daily quiz scheduler disabled"
        ));
    }

    @PostMapping
    public ResponseEntity<?> createQuiz(@RequestBody JsonNode payload) {
        try {
            String title = getTextOrNull(payload, "title");
            String content = extractContentJson(payload);
            if (content == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "content is required and must be valid JSON"));
            }
            LocalDate createdAt = parseDate(getTextOrNull(payload, "createdAt"));
            if (createdAt == null) {
                createdAt = parseDate(getTextOrNull(payload, "quiz_date"));
            }
            if (createdAt == null) {
                createdAt = LocalDate.now(INDIA_TIMEZONE);
            }
            if (title == null) {
                title = extractTitleFromContent(content);
            }
            CurrentAffairsQuiz saved = quizService.createQuiz(title, createdAt, content);
            return ResponseEntity.ok(Map.of(
                    "quizId", saved.getQuizId(),
                    "title", saved.getTitle(),
                    "date", saved.getCreatedAt().toString()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to create quiz",
                    "error", ex.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuiz(@PathVariable("id") long id, @RequestBody JsonNode payload) {
        try {
            String title = getTextOrNull(payload, "title");
            String content = extractContentJson(payload);
            LocalDate createdAt = parseDate(getTextOrNull(payload, "createdAt"));
            if (createdAt == null) {
                createdAt = parseDate(getTextOrNull(payload, "quiz_date"));
            }

            if (title == null && content == null && createdAt == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "No updatable fields provided"));
            }

            Optional<CurrentAffairsQuiz> updated = quizService.updateQuiz(id, title, createdAt, content);
            if (updated.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            CurrentAffairsQuiz quiz = updated.get();
            return ResponseEntity.ok(Map.of(
                    "quizId", quiz.getQuizId(),
                    "title", quiz.getTitle(),
                    "date", quiz.getCreatedAt().toString()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to update quiz",
                    "error", ex.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuiz(@PathVariable("id") long id) {
        boolean deleted = quizService.deleteQuiz(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PostMapping("/{slugWithId}/submit")
    public ResponseEntity<CurrentAffairsQuizAttemptStatsDto> submitQuiz(@PathVariable("slugWithId") String slugWithId,
            @RequestBody CurrentAffairsQuizSubmitRequest request, Authentication authentication) {
        long id = extractIdFromSlug(slugWithId);
        if (id == -1) {
            return ResponseEntity.badRequest().build();
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CurrentAffairsQuizAttemptStatsDto result = quizService.submitQuiz(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{slugWithId}/my-stats")
    public ResponseEntity<CurrentAffairsQuizAttemptStatsDto> getMyStats(@PathVariable("slugWithId") String slugWithId,
            Authentication authentication) {
        long id = extractIdFromSlug(slugWithId);
        if (id == -1) {
            return ResponseEntity.badRequest().build();
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = userAccountService.getUserByEmail(authentication.getName()).getId();
        return ResponseEntity.ok(quizService.getQuizStatsForUser(id, userId));
    }

    @GetMapping("/{slugWithId}/user-stats/{userId}")
    public ResponseEntity<CurrentAffairsQuizAttemptStatsDto> getUserStats(@PathVariable("slugWithId") String slugWithId,
            @PathVariable("userId") long userId, Authentication authentication) {
        long id = extractIdFromSlug(slugWithId);
        if (id == -1) {
            return ResponseEntity.badRequest().build();
        }
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(quizService.getQuizStatsForUser(id, userId));
    }

    private long extractIdFromSlug(String slug) {
        try {
            String idStr = slug.substring(slug.lastIndexOf("-") + 1);
            return Long.parseLong(idStr);
        } catch (Exception ex) {
            return -1;
        }
    }

    private String extractContentJson(JsonNode payload) throws Exception {
        if (payload == null || payload.isNull()) {
            return null;
        }
        JsonNode contentNode = payload.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            String text = contentNode.asText("");
            if (text.isBlank()) {
                return null;
            }
            objectMapper.readTree(text);
            return text;
        }
        return objectMapper.writeValueAsString(contentNode);
    }

    private String extractTitleFromContent(String contentJson) {
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            String title = root.path("title").asText(null);
            return title != null && !title.isBlank() ? title : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String getTextOrNull(JsonNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text != null && !text.isBlank() ? text : null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
