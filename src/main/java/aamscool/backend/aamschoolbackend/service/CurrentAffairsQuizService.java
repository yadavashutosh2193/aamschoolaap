package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.QuizListItem;
import aamscool.backend.aamschoolbackend.repository.CurrentAffairsQuizRepository;

@Service
public class CurrentAffairsQuizService {

    private static final Logger log = LoggerFactory.getLogger(CurrentAffairsQuizService.class);
    private static final ZoneId INDIA_TIMEZONE = ZoneId.of("Asia/Kolkata");
    private static final int TARGET_QUESTION_COUNT = 15;
    private static final int CANDIDATE_POOL_SIZE = 30;
    private static final int RECENT_QUIZ_LOOKBACK = 5;
    private static final int MAX_TOPUP_ATTEMPTS = 1;
    private static final int MAX_AVOID_PROMPT_QUESTIONS = 60;

    @Autowired
    private CurrentAffairsQuizRepository quizRepository;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private TelegramNotifierService telegramNotifierService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<CurrentAffairsQuiz> getQuiz(long id) {
        return quizRepository.findById(id);
    }

    public CurrentAffairsQuiz createQuiz(String title, LocalDate createdAt, String content) {
        CurrentAffairsQuiz quiz = new CurrentAffairsQuiz();
        quiz.setTitle(title);
        quiz.setCreatedAt(createdAt != null ? createdAt : LocalDate.now(INDIA_TIMEZONE));
        quiz.setContent(content);
        return quizRepository.save(quiz);
    }

    public Optional<CurrentAffairsQuiz> updateQuiz(long id, String title, LocalDate createdAt, String content) {
        Optional<CurrentAffairsQuiz> quizOpt = quizRepository.findById(id);
        if (quizOpt.isEmpty()) {
            return Optional.empty();
        }
        CurrentAffairsQuiz quiz = quizOpt.get();
        if (title != null) {
            quiz.setTitle(title);
        }
        if (createdAt != null) {
            quiz.setCreatedAt(createdAt);
        }
        if (content != null) {
            quiz.setContent(content);
        }
        return Optional.of(quizRepository.save(quiz));
    }

    public boolean deleteQuiz(long id) {
        if (!quizRepository.existsById(id)) {
            return false;
        }
        quizRepository.deleteById(id);
        return true;
    }

    public List<QuizListItem> getQuizTitles() {
        return quizRepository.findAllQuizTitlesAndIds();
    }

    public CurrentAffairsQuiz generateAndSaveTodayQuiz() throws Exception {
        LocalDate today = LocalDate.now(INDIA_TIMEZONE);

        Optional<CurrentAffairsQuiz> existing = quizRepository.findFirstByCreatedAtOrderByQuizIdDesc(today);
       if (existing.isPresent()) {
            return existing.get();
        }

        List<String> avoidQuestionPrompts = loadRecentQuestionTexts(RECENT_QUIZ_LOOKBACK);
        avoidQuestionPrompts.addAll(loadQuestionTextsForLastDays(today, 5));
        Set<String> avoidNormalized = normalizeQuestions(avoidQuestionPrompts);
        String cleanJson = openAIService.generateCurrentAffairsQuizJson(
                today,
                CANDIDATE_POOL_SIZE,
                limitPromptQuestions(avoidQuestionPrompts)
        );
        ObjectNode quizRoot = (ObjectNode) objectMapper.readTree(cleanJson);
        String title = quizRoot.path("title").asText("Daily Current Affairs Quiz - " + today);

        List<ObjectNode> uniqueQuestions = filterUniqueQuestions(quizRoot.path("questions"), avoidNormalized);
        Set<String> seenNormalized = new HashSet<>(avoidNormalized);
        for (ObjectNode q : uniqueQuestions) {
            String questionText = q.path("question").asText("");
            if (!questionText.isBlank()) {
                seenNormalized.add(normalizeQuestion(questionText));
                avoidQuestionPrompts.add(questionText);
            }
        }

        int attempts = 0;
        while (uniqueQuestions.size() < TARGET_QUESTION_COUNT && attempts < MAX_TOPUP_ATTEMPTS) {
            int remaining = TARGET_QUESTION_COUNT - uniqueQuestions.size();
            String extraJson = openAIService.generateCurrentAffairsQuizQuestions(
                    today,
                    remaining,
                    limitPromptQuestions(avoidQuestionPrompts)
            );
            JsonNode extraRoot = objectMapper.readTree(extraJson);
            JsonNode extraQuestions = extraRoot.isArray() ? extraRoot : extraRoot.path("questions");
            List<ObjectNode> extraUnique = filterUniqueQuestions(extraQuestions, seenNormalized);
            for (ObjectNode q : extraUnique) {
                uniqueQuestions.add(q);
                String questionText = q.path("question").asText("");
                if (!questionText.isBlank()) {
                    seenNormalized.add(normalizeQuestion(questionText));
                    avoidQuestionPrompts.add(questionText);
                }
            }
            attempts++;
        }

        if (uniqueQuestions.size() < TARGET_QUESTION_COUNT) {
            int remaining = TARGET_QUESTION_COUNT - uniqueQuestions.size();
            String extraJson = openAIService.generateCurrentAffairsQuizQuestions(
                    today,
                    remaining,
                    limitPromptQuestions(new ArrayList<>(seenNormalized))
            );
            JsonNode extraRoot = objectMapper.readTree(extraJson);
            JsonNode extraQuestions = extraRoot.isArray() ? extraRoot : extraRoot.path("questions");
            List<ObjectNode> extraUnique = filterUniqueQuestions(extraQuestions, seenNormalized);
            for (ObjectNode q : extraUnique) {
                if (uniqueQuestions.size() >= TARGET_QUESTION_COUNT) {
                    break;
                }
                uniqueQuestions.add(q);
                String questionText = q.path("question").asText("");
                if (!questionText.isBlank()) {
                    seenNormalized.add(normalizeQuestion(questionText));
                }
            }
        }

        int finalCount = Math.min(TARGET_QUESTION_COUNT, uniqueQuestions.size());
        if (finalCount < TARGET_QUESTION_COUNT) {
            log.warn("Only {} unique questions generated. Proceeding with partial quiz.", finalCount);
        }
        ArrayNode finalQuestions = objectMapper.createArrayNode();
        for (int i = 0; i < finalCount; i++) {
            ObjectNode q = uniqueQuestions.get(i);
            finalQuestions.add(q);
        }
        quizRoot.set("questions", finalQuestions);
        quizRoot.put("total_questions", finalCount);
        String finalJson = objectMapper.writeValueAsString(quizRoot);

        CurrentAffairsQuiz quiz = new CurrentAffairsQuiz();
        quiz.setTitle(title);
        quiz.setCreatedAt(today);
        quiz.setContent(finalJson);
        CurrentAffairsQuiz saved = quizRepository.save(quiz);
        try {
            telegramNotifierService.sendCurrentAffairsQuizUpdate(saved);
        } catch (Exception ex) {
            log.error("Telegram notification failed for quizId={}", saved.getQuizId(), ex);
        }
        return saved;
    }

    private List<String> loadRecentQuestionTexts(int lookbackQuizzes) {
        List<String> questions = new ArrayList<>();
        quizRepository.findAll(PageRequest.of(0, lookbackQuizzes, Sort.by(Sort.Direction.DESC, "quizId")))
                .forEach(quiz -> {
                    String content = quiz.getContent();
                    if (content == null || content.isBlank()) {
                        return;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(content);
                        JsonNode questionsNode = root.path("questions");
                        if (questionsNode.isArray()) {
                            for (JsonNode q : questionsNode) {
                                String questionText = q.path("question").asText("");
                                if (!questionText.isBlank()) {
                                    questions.add(questionText);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to parse previous quiz content for dedupe. quizId={}", quiz.getQuizId(), ex);
                    }
                });
        return questions;
    }

    private List<String> loadQuestionTextsForLastDays(LocalDate today, int daysBack) {
        List<String> questions = new ArrayList<>();
        if (daysBack <= 0) {
            return questions;
        }
        for (int i = 1; i <= daysBack; i++) {
            LocalDate date = today.minusDays(i);
            Optional<CurrentAffairsQuiz> quizOpt = quizRepository.findFirstByCreatedAtOrderByQuizIdDesc(date);
            if (quizOpt.isEmpty()) {
                continue;
            }
            String content = quizOpt.get().getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(content);
                JsonNode questionsNode = root.path("questions");
                if (questionsNode.isArray()) {
                    for (JsonNode q : questionsNode) {
                        String questionText = q.path("question").asText("");
                        if (!questionText.isBlank()) {
                            questions.add(questionText);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to parse quiz content for date {}. quizId={}", date, quizOpt.get().getQuizId(), ex);
            }
        }
        return questions;
    }

    private List<ObjectNode> filterUniqueQuestions(JsonNode questionsNode, Set<String> avoidQuestions) {
        List<ObjectNode> unique = new ArrayList<>();
        if (questionsNode == null || !questionsNode.isArray()) {
            return unique;
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode q : questionsNode) {
            if (!q.isObject()) {
                continue;
            }
            ObjectNode obj = (ObjectNode) q;
            String questionText = obj.path("question").asText("");
            if (questionText.isBlank()) {
                continue;
            }
            String key = normalizeQuestion(questionText);
            if (avoidQuestions != null && avoidQuestions.contains(key)) {
                continue;
            }
            if (seen.add(key)) {
                unique.add(obj);
            }
        }
        return unique;
    }

    private Set<String> normalizeQuestions(List<String> questions) {
        Set<String> normalized = new HashSet<>();
        if (questions == null) {
            return normalized;
        }
        for (String q : questions) {
            if (q == null || q.isBlank()) {
                continue;
            }
            normalized.add(normalizeQuestion(q));
        }
        return normalized;
    }

    private List<String> limitPromptQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(questions.size(), MAX_AVOID_PROMPT_QUESTIONS);
        return new ArrayList<>(questions.subList(0, limit));
    }

    private String normalizeQuestion(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[^a-z0-9\\s]", "");
        return normalized;
    }
}
