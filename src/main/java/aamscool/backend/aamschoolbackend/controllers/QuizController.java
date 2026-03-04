package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.dto.AdminSubjectQuizAttemptStatsDto;
import aamscool.backend.aamschoolbackend.dto.QuizAttemptResultDto;
import aamscool.backend.aamschoolbackend.dto.QuizListItemDto;
import aamscool.backend.aamschoolbackend.dto.QuizDto;
import aamscool.backend.aamschoolbackend.dto.QuizGenerateRequest;
import aamscool.backend.aamschoolbackend.dto.QuizLeaderboardDto;
import aamscool.backend.aamschoolbackend.dto.QuizSubmitRequest;
import aamscool.backend.aamschoolbackend.service.QuizService;

@RestController
@RequestMapping("/quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public List<QuizDto> getAll() {
        return quizService.getAll();
    }

    @GetMapping("/by-topic")
    public List<QuizListItemDto> getByTopic(@RequestParam("subject") String subject,
            @RequestParam("topic") String topic) {
        return quizService.getBySubjectAndTopic(subject, topic);
    }

    @GetMapping("/{id}")
    public QuizDto getById(@PathVariable Long id) {
        return quizService.getById(id);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<QuizAttemptResultDto> submit(@PathVariable Long id, @RequestBody QuizSubmitRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        QuizAttemptResultDto result = quizService.submitQuiz(id, request, authentication == null ? null : authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/leaderboard")
    public QuizLeaderboardDto getLeaderboard(@PathVariable Long id, Authentication authentication) {
        return quizService.getLeaderboard(id, authentication == null ? null : authentication.getName());
    }

    @GetMapping("/admin/attempt-stats")
    public ResponseEntity<AdminSubjectQuizAttemptStatsDto> getAttemptStatsBySubject(
            @RequestParam("subject") String subject, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(quizService.getAttemptStatsBySubject(subject));
    }

    @PostMapping("/generate")
    public ResponseEntity<QuizDto> generate(@RequestBody QuizGenerateRequest request) {
        QuizDto quiz = quizService.generateRandomQuiz(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(quiz);
    }

    @PostMapping("/generate-batch")
    public ResponseEntity<List<QuizDto>> generateBatch(@RequestBody QuizGenerateRequest request) {
        List<QuizDto> quizzes = quizService.generateRandomQuizzes(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(quizzes);
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
