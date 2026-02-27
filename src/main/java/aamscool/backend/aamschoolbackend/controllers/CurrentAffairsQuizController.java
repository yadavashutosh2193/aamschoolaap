package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.QuizListItem;
import aamscool.backend.aamschoolbackend.service.CurrentAffairsQuizService;

@RestController
@RequestMapping("/api/current-affairs-quizzes")
public class CurrentAffairsQuizController {

    @Autowired
    private CurrentAffairsQuizService quizService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Failed to generate quiz",
                    "error", ex.getMessage()
            ));
        }
    }

    private long extractIdFromSlug(String slug) {
        try {
            String idStr = slug.substring(slug.lastIndexOf("-") + 1);
            return Long.parseLong(idStr);
        } catch (Exception ex) {
            return -1;
        }
    }
}
