package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.QuizListItem;
import aamscool.backend.aamschoolbackend.repository.CurrentAffairsQuizRepository;

@Service
public class CurrentAffairsQuizService {

    private static final Logger log = LoggerFactory.getLogger(CurrentAffairsQuizService.class);

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

    public List<QuizListItem> getQuizTitles() {
        return quizRepository.findAllQuizTitlesAndIds();
    }

    public CurrentAffairsQuiz generateAndSaveTodayQuiz() throws Exception {
        LocalDate today = LocalDate.now();

        Optional<CurrentAffairsQuiz> existing = quizRepository.findFirstByCreatedAtOrderByQuizIdDesc(today);
       if (existing.isPresent()) {
            return existing.get();
        }

        String cleanJson = openAIService.generateCurrentAffairsQuizJson(today);
        JsonNode quizRoot = objectMapper.readTree(cleanJson);
        String title = quizRoot.path("title").asText("Daily Current Affairs Quiz - " + today);

        CurrentAffairsQuiz quiz = new CurrentAffairsQuiz();
        quiz.setTitle(title);
        quiz.setCreatedAt(today);
        quiz.setContent(cleanJson);
        CurrentAffairsQuiz saved = quizRepository.save(quiz);
        try {
            telegramNotifierService.sendCurrentAffairsQuizUpdate(saved);
        } catch (Exception ex) {
            log.error("Telegram notification failed for quizId={}", saved.getQuizId(), ex);
        }
        return saved;
    }
}
