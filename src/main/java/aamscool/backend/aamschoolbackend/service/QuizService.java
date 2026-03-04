package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.dto.ExamDto;
import aamscool.backend.aamschoolbackend.dto.AdminQuizAttemptStatDto;
import aamscool.backend.aamschoolbackend.dto.AdminSubjectQuizAttemptStatsDto;
import aamscool.backend.aamschoolbackend.dto.LeaderboardEntryDto;
import aamscool.backend.aamschoolbackend.dto.LeaderboardUserDto;
import aamscool.backend.aamschoolbackend.dto.QuestionDto;
import aamscool.backend.aamschoolbackend.dto.QuizAttemptResultDto;
import aamscool.backend.aamschoolbackend.dto.QuizDto;
import aamscool.backend.aamschoolbackend.dto.QuizGenerateRequest;
import aamscool.backend.aamschoolbackend.dto.QuizLeaderboardDto;
import aamscool.backend.aamschoolbackend.dto.QuizListItemDto;
import aamscool.backend.aamschoolbackend.dto.QuizQuestionDto;
import aamscool.backend.aamschoolbackend.dto.QuizSubmissionAnswerDto;
import aamscool.backend.aamschoolbackend.dto.QuizSubmitRequest;
import aamscool.backend.aamschoolbackend.dto.TopicCountDto;
import aamscool.backend.aamschoolbackend.model.Difficulty;
import aamscool.backend.aamschoolbackend.model.Exam;
import aamscool.backend.aamschoolbackend.model.Language;
import aamscool.backend.aamschoolbackend.model.Question;
import aamscool.backend.aamschoolbackend.model.QuestionChoice;
import aamscool.backend.aamschoolbackend.model.QuestionStatus;
import aamscool.backend.aamschoolbackend.model.Quiz;
import aamscool.backend.aamschoolbackend.model.QuizAttempt;
import aamscool.backend.aamschoolbackend.model.QuizQuestion;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.ExamRepository;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizAttemptRepository;
import aamscool.backend.aamschoolbackend.repository.QuizQuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizRepository;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;
    private final QuestionService questionService;
    private final UserAccountService userAccountService;

    public QuizService(QuizRepository quizRepository, QuizQuestionRepository quizQuestionRepository,
            QuizAttemptRepository quizAttemptRepository, QuestionRepository questionRepository,
            ExamRepository examRepository, QuestionService questionService, UserAccountService userAccountService) {
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.questionRepository = questionRepository;
        this.examRepository = examRepository;
        this.questionService = questionService;
        this.userAccountService = userAccountService;
    }

    public List<QuizDto> getAll() {
        return quizRepository.findAll().stream().map(this::toDto).toList();
    }

    public List<QuizListItemDto> getBySubjectAndTopic(String subject, String topic) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
        return quizRepository.findBySubjectAndTopic(subject, topic).stream()
                .map(this::toListItem)
                .toList();
    }

    public List<TopicCountDto> getTopicCountsBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        List<String> topics = questionRepository.findDistinctTopicsBySubject(subject);
        if (topics.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object[]> rows = quizRepository.countByTopicIn(topics);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((String) row[0], (Long) row[1]);
        }
        List<TopicCountDto> result = new ArrayList<>();
        for (String topic : topics) {
            Long count = counts.getOrDefault(topic, 0L);
            result.add(new TopicCountDto(topic, count));
        }
        return result;
    }

    public QuizDto getById(Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        return toDto(quiz);
    }

    @Transactional(readOnly = true)
    public AdminSubjectQuizAttemptStatsDto getAttemptStatsBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }

        List<Quiz> quizzes = quizRepository.findBySubject(subject);
        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();
        List<QuizAttempt> attempts = quizIds.isEmpty() ? new ArrayList<>() : quizAttemptRepository.findByQuizIdIn(quizIds);

        Map<Long, Integer> attemptedCounts = new HashMap<>();
        Map<Long, LocalDateTime> lastAttempted = new HashMap<>();
        for (QuizAttempt attempt : attempts) {
            Long quizId = attempt.getQuiz().getId();
            attemptedCounts.put(quizId, attemptedCounts.getOrDefault(quizId, 0) + 1);

            LocalDateTime submittedAt = attempt.getSubmittedAt();
            LocalDateTime currentLatest = lastAttempted.get(quizId);
            if (submittedAt != null && (currentLatest == null || submittedAt.isAfter(currentLatest))) {
                lastAttempted.put(quizId, submittedAt);
            }
        }

        List<AdminQuizAttemptStatDto> quizStats = new ArrayList<>();
        int totalAttemptedCandidates = 0;
        for (Quiz quiz : quizzes) {
            int attemptedCandidates = attemptedCounts.getOrDefault(quiz.getId(), 0);
            totalAttemptedCandidates += attemptedCandidates;

            AdminQuizAttemptStatDto dto = new AdminQuizAttemptStatDto();
            dto.setQuizId(quiz.getId());
            dto.setTitle(quiz.getTitle());
            dto.setSubject(quiz.getSubject());
            dto.setTopic(quiz.getTopic());
            dto.setSubTopic(quiz.getSubTopic());
            dto.setTotalQuestions(quiz.getTotalQuestions());
            dto.setTotalMarks(quiz.getTotalMarks());
            dto.setAttemptedCandidates(attemptedCandidates);
            dto.setLastAttemptedAt(lastAttempted.get(quiz.getId()));
            quizStats.add(dto);
        }

        AdminSubjectQuizAttemptStatsDto response = new AdminSubjectQuizAttemptStatsDto();
        response.setSubject(subject);
        response.setTotalQuizzes(quizzes.size());
        response.setTotalAttemptedCandidates(totalAttemptedCandidates);
        response.setQuizzes(quizStats);
        return response;
    }

    @Transactional
    public QuizAttemptResultDto submitQuiz(Long quizId, QuizSubmitRequest request, String currentUserEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Submission request is required");
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        UserAccount user = resolveSubmissionUser(request.getUserId(), currentUserEmail);
        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quizId);
        if (quizQuestions.isEmpty()) {
            throw new IllegalArgumentException("Quiz has no questions");
        }

        Map<Long, Long> submittedAnswers = normalizeAnswers(request.getAnswers(), quizQuestions);

        double score = 0.0;
        int correctAnswers = 0;
        int wrongAnswers = 0;
        int attemptedQuestions = 0;
        int totalMarks = quiz.getTotalMarks() == null
                ? quizQuestions.stream().mapToInt(q -> q.getMarks() == null ? 0 : q.getMarks()).sum()
                : quiz.getTotalMarks();

        for (QuizQuestion quizQuestion : quizQuestions) {
            Question question = quizQuestion.getQuestion();
            question.setTotalAttempts((question.getTotalAttempts() == null ? 0L : question.getTotalAttempts()) + 1);

            Long selectedChoiceId = submittedAnswers.get(quizQuestion.getId());
            if (selectedChoiceId == null) {
                continue;
            }

            attemptedQuestions++;
            if (isCorrectChoice(question.getChoices(), selectedChoiceId)) {
                correctAnswers++;
                score += quizQuestion.getMarks() == null ? 0 : quizQuestion.getMarks();
                question.setCorrectAttempts((question.getCorrectAttempts() == null ? 0L : question.getCorrectAttempts()) + 1);
            } else {
                wrongAnswers++;
                score -= quizQuestion.getNegativeMarks() == null ? 0.0 : quizQuestion.getNegativeMarks();
            }
        }

        double accuracy = attemptedQuestions == 0 ? 0.0 : (correctAnswers * 100.0) / attemptedQuestions;

        QuizAttempt attempt = quizAttemptRepository.findByQuizIdAndUserId(quizId, user.getId())
                .orElseGet(QuizAttempt::new);
        attempt.setQuiz(quiz);
        attempt.setUser(user);
        attempt.setScore(score);
        attempt.setTotalMarks(totalMarks);
        attempt.setCorrectAnswers(correctAnswers);
        attempt.setWrongAnswers(wrongAnswers);
        attempt.setAttemptedQuestions(attemptedQuestions);
        attempt.setAccuracy(accuracy);
        attempt.setTimeTakenSeconds(request.getTimeTakenSeconds());
        attempt.setSubmittedAt(LocalDateTime.now());

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);
        return toAttemptResultDto(savedAttempt);
    }

    @Transactional(readOnly = true)
    public QuizLeaderboardDto getLeaderboard(Long quizId, String currentUserEmail) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));

        Long currentUserId = resolveCurrentUserId(currentUserEmail);
        List<QuizAttempt> attempts = new ArrayList<>(quizAttemptRepository.findByQuizId(quizId));
        attempts.sort(compareAttempts());

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (QuizAttempt attempt : attempts) {
            LeaderboardEntryDto entry = new LeaderboardEntryDto();
            entry.setRank(rank++);
            entry.setUser(toLeaderboardUserDto(attempt.getUser()));
            entry.setScore(attempt.getScore());
            entry.setTotalMarks(attempt.getTotalMarks());
            entry.setCorrectAnswers(attempt.getCorrectAnswers());
            entry.setWrongAnswers(attempt.getWrongAnswers());
            entry.setAttemptedQuestions(attempt.getAttemptedQuestions());
            entry.setAccuracy(attempt.getAccuracy());
            entry.setTimeTakenSeconds(attempt.getTimeTakenSeconds());
            entry.setSubmittedAt(attempt.getSubmittedAt());
            entry.setIsCurrentUser(currentUserId != null && currentUserId.equals(attempt.getUser().getId()));
            entries.add(entry);
        }

        QuizLeaderboardDto leaderboard = new QuizLeaderboardDto();
        leaderboard.setQuizId(quiz.getId());
        leaderboard.setQuizTitle(quiz.getTitle());
        leaderboard.setTotalParticipants(entries.size());
        leaderboard.setGeneratedAt(LocalDateTime.now());
        leaderboard.setEntries(entries);
        return leaderboard;
    }

    @Transactional
    public List<QuizDto> regenerateAutoQuizzesForSubjectTopic(String subject, String topic) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }

        log.info("Auto-quiz generation started: subject='{}', topic='{}'", subject, topic);
        List<Question> pool = questionRepository.findBySubjectAndTopic(subject, topic);
        if (pool.isEmpty()) {
            removeExistingQuizzes(subject, topic);
            log.info("Auto-quiz generation skipped: no questions found for subject='{}', topic='{}'", subject, topic);
            return new ArrayList<>();
        }

        int total = pool.size();
        int fullCount = total / 20;
        int remainder = total % 20;
        int targetCount = fullCount + (remainder >= 14 ? 1 : 0);

        log.info(
                "Auto-quiz sizing: subject='{}', topic='{}', totalQuestions={}, fullQuizzes={}, remainder={}, targetQuizzes={}",
                subject, topic, total, fullCount, remainder, targetCount);
        removeExistingQuizzes(subject, topic);

        Collections.shuffle(pool, ThreadLocalRandom.current());
        List<QuizDto> result = new ArrayList<>();
        int index = 0;
        for (int i = 1; i <= fullCount; i++) {
            List<Question> selected = new ArrayList<>(pool.subList(index, index + 20));
            index += 20;
            result.add(createAutoQuiz(subject, topic, buildAutoTitle(subject, topic, i), selected));
        }

        if (remainder >= 14) {
            int setNumber = fullCount + 1;
            List<Question> selected = new ArrayList<>(pool.subList(index, total));
            int uniqueCount = selected.size();
            while (selected.size() < 20) {
                Question q = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                selected.add(q);
            }
            Collections.shuffle(selected, ThreadLocalRandom.current());
            log.info(
                    "Auto-quiz extra set: subject='{}', topic='{}', setNumber={}, uniqueQuestions={}, totalQuestions={}",
                    subject, topic, setNumber, uniqueCount, selected.size());
            result.add(createAutoQuiz(subject, topic, buildAutoTitle(subject, topic, setNumber), selected));
        }

        if (targetCount == 0) {
            log.info("Auto-quiz generation finished: subject='{}', topic='{}', createdQuizzes=0", subject, topic);
            return new ArrayList<>();
        }

        log.info("Auto-quiz generation finished: subject='{}', topic='{}', createdQuizzes={}", subject, topic,
                result.size());
        return result;
    }

    @Transactional
    public void regenerateAutoQuizzesForBulkUpload(List<QuestionDto> questions) {
        if (questions == null || questions.isEmpty()) {
            log.info("Auto-quiz generation skipped: bulk upload list is empty");
            return;
        }
        Set<String> subjectTopicKeys = new HashSet<>();
        for (QuestionDto dto : questions) {
            if (dto == null) {
                continue;
            }
            String subject = dto.getSubject();
            String topic = dto.getTopic();
            if (subject == null || subject.isBlank() || topic == null || topic.isBlank()) {
                continue;
            }
            subjectTopicKeys.add(subject.trim() + "||" + topic.trim());
        }
        log.info("Auto-quiz generation triggered for bulk upload: distinctSubjectTopics={}", subjectTopicKeys.size());
        for (String key : subjectTopicKeys) {
            String[] parts = key.split("\\|\\|", 2);
            regenerateAutoQuizzesForSubjectTopic(parts[0], parts[1]);
        }
    }

    @Transactional
    public List<QuizDto> generateRandomQuizzes(QuizGenerateRequest request) {
        int count = request.getQuizCount() == null ? 1 : request.getQuizCount();
        if (count < 1) {
            throw new IllegalArgumentException("Quiz count must be at least 1");
        }
        List<QuizDto> quizzes = new ArrayList<>();
        Set<Long> globalUsed = new HashSet<>();
        String baseTitle = request.getTitle();
        for (int i = 1; i <= count; i++) {
            QuizGenerateRequest copy = copyRequest(request);
            if (baseTitle != null && !baseTitle.isBlank() && count > 1) {
                copy.setTitle(baseTitle + " - Set " + i);
            }
            quizzes.add(generateRandomQuizInternal(copy, globalUsed));
        }
        return quizzes;
    }

    @Transactional
    public QuizDto generateRandomQuiz(QuizGenerateRequest request) {
        return generateRandomQuizInternal(request, new HashSet<>());
    }

    private QuizDto generateRandomQuizInternal(QuizGenerateRequest request, Set<Long> globalUsed) {
        validateRequest(request);

        int totalQuestions = request.getTotalQuestions() == null ? 20 : request.getTotalQuestions();
        int easyCount = request.getEasyCount() == null ? (totalQuestions * 40 / 100) : request.getEasyCount();
        int mediumCount = request.getMediumCount() == null ? (totalQuestions * 40 / 100) : request.getMediumCount();
        int hardCount = request.getHardCount() == null ? (totalQuestions - easyCount - mediumCount)
                : request.getHardCount();

        if (easyCount + mediumCount + hardCount != totalQuestions) {
            throw new IllegalArgumentException("Easy/Medium/Hard counts must sum to total questions");
        }

        Exam exam = resolveExam(request.getExamId(), request.getExamName());

        Difficulty easy = Difficulty.EASY;
        Difficulty medium = Difficulty.MEDIUM;
        Difficulty hard = Difficulty.HARD;

        Language language = parseLanguage(request.getLanguage());
        QuestionStatus status = parseStatus(request.getStatus());

        List<Question> easyPool = questionRepository.findByDifficultyAndFilters(
                easy, request.getSubject(), request.getTopic(), request.getSubTopic(), language, status,
                request.getIsPremium(), exam == null ? null : exam.getId());
        List<Question> mediumPool = questionRepository.findByDifficultyAndFilters(
                medium, request.getSubject(), request.getTopic(), request.getSubTopic(), language, status,
                request.getIsPremium(), exam == null ? null : exam.getId());
        List<Question> hardPool = questionRepository.findByDifficultyAndFilters(
                hard, request.getSubject(), request.getTopic(), request.getSubTopic(), language, status,
                request.getIsPremium(), exam == null ? null : exam.getId());

        easyPool = filterByTags(easyPool, request.getTags());
        mediumPool = filterByTags(mediumPool, request.getTags());
        hardPool = filterByTags(hardPool, request.getTags());

        if (easyPool.isEmpty() || mediumPool.isEmpty() || hardPool.isEmpty()) {
            throw new IllegalArgumentException("Not enough questions to generate the quiz");
        }

        Set<Long> localUsed = new HashSet<>();
        List<Question> selected = new ArrayList<>();
        selected.addAll(pickQuestions(easyPool, easyCount, globalUsed, localUsed, false));
        selected.addAll(pickQuestions(mediumPool, mediumCount, globalUsed, localUsed, false));
        selected.addAll(pickQuestions(hardPool, hardCount, globalUsed, localUsed, false));

        int remaining = totalQuestions - selected.size();
        if (remaining > 0) {
            List<Question> allPool = new ArrayList<>();
            allPool.addAll(easyPool);
            allPool.addAll(mediumPool);
            allPool.addAll(hardPool);
            selected.addAll(pickQuestions(allPool, remaining, globalUsed, localUsed, false));
        }

        remaining = totalQuestions - selected.size();
        if (remaining > 0) {
            List<Question> allPool = new ArrayList<>();
            allPool.addAll(easyPool);
            allPool.addAll(mediumPool);
            allPool.addAll(hardPool);
            selected.addAll(pickQuestions(allPool, remaining, globalUsed, localUsed, true));
        }

        Collections.shuffle(selected, ThreadLocalRandom.current());

        Quiz quiz = new Quiz();
        quiz.setTitle(request.getTitle());
        quiz.setSubject(request.getSubject());
        quiz.setTopic(request.getTopic());
        quiz.setSubTopic(request.getSubTopic());
        quiz.setExam(exam);
        quiz.setTotalQuestions(totalQuestions);
        quiz.setCreatedBy(request.getCreatedBy());

        Quiz savedQuiz = quizRepository.save(quiz);

        int order = 1;
        int totalMarks = 0;
        List<QuizQuestion> quizQuestions = new ArrayList<>();
        for (Question q : selected) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(savedQuiz);
            qq.setQuestion(q);
            qq.setOrderIndex(order++);
            int marks = request.getMarks() == null ? (q.getMarks() == null ? 1 : q.getMarks()) : request.getMarks();
            double negative = request.getNegativeMarks() == null
                    ? (q.getNegativeMarks() == null ? 0.0 : q.getNegativeMarks())
                    : request.getNegativeMarks();
            qq.setMarks(marks);
            qq.setNegativeMarks(negative);
            totalMarks += marks;
            quizQuestions.add(qq);
        }
        quizQuestionRepository.saveAll(quizQuestions);
        savedQuiz.setTotalMarks(totalMarks);
        quizRepository.save(savedQuiz);

        return toDto(savedQuiz, quizQuestions);
    }

    private void validateRequest(QuizGenerateRequest request) {
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
    }

    private Language parseLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return Language.from(language);
    }

    private QuestionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return QuestionStatus.valueOf(status);
    }

    private List<Question> filterByTags(List<Question> pool, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return pool;
        }
        List<Question> result = new ArrayList<>();
        for (Question q : pool) {
            if (q.getTags() != null && q.getTags().containsAll(tags)) {
                result.add(q);
            }
        }
        return result;
    }

    private List<Question> pickQuestions(List<Question> pool, int count, Set<Long> globalUsed, Set<Long> localUsed,
            boolean allowReuse) {
        if (pool.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }

        List<Question> available = new ArrayList<>();
        for (Question q : pool) {
            if (!globalUsed.contains(q.getId())) {
                available.add(q);
            }
        }
        Collections.shuffle(available, ThreadLocalRandom.current());

        List<Question> picked = new ArrayList<>();
        int take = Math.min(count, available.size());
        for (int i = 0; i < take; i++) {
            Question q = available.get(i);
            if (!localUsed.contains(q.getId())) {
                picked.add(q);
                localUsed.add(q.getId());
                globalUsed.add(q.getId());
            }
        }

        if (picked.size() < count && !allowReuse) {
            List<Question> fallback = new ArrayList<>();
            for (Question q : pool) {
                if (!localUsed.contains(q.getId())) {
                    fallback.add(q);
                }
            }
            Collections.shuffle(fallback, ThreadLocalRandom.current());
            for (Question q : fallback) {
                if (picked.size() >= count) {
                    break;
                }
                picked.add(q);
                localUsed.add(q.getId());
            }
        }

        if (picked.size() < count && allowReuse) {
            while (picked.size() < count) {
                Question q = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                picked.add(q);
            }
        }

        return picked;
    }

    private Exam resolveExam(Long examId, String examName) {
        if (examId != null) {
            return examRepository.findById(examId)
                    .orElseThrow(() -> new IllegalArgumentException("Exam not found"));
        }
        if (examName == null || examName.isBlank()) {
            return null;
        }
        String normalized = examName.trim();
        if (normalized.isBlank()) {
            return null;
        }
        return examRepository.findByNameIgnoreCase(normalized)
                .orElseGet(() -> {
                    Exam exam = new Exam();
                    exam.setName(normalized);
                    return examRepository.save(exam);
                });
    }

    private QuizGenerateRequest copyRequest(QuizGenerateRequest request) {
        QuizGenerateRequest copy = new QuizGenerateRequest();
        copy.setTitle(request.getTitle());
        copy.setExamId(request.getExamId());
        copy.setExamName(request.getExamName());
        copy.setSubject(request.getSubject());
        copy.setTopic(request.getTopic());
        copy.setSubTopic(request.getSubTopic());
        copy.setLanguage(request.getLanguage());
        copy.setStatus(request.getStatus());
        copy.setIsPremium(request.getIsPremium());
        copy.setTags(request.getTags());
        copy.setTotalQuestions(request.getTotalQuestions());
        copy.setEasyCount(request.getEasyCount());
        copy.setMediumCount(request.getMediumCount());
        copy.setHardCount(request.getHardCount());
        copy.setMarks(request.getMarks());
        copy.setNegativeMarks(request.getNegativeMarks());
        copy.setCreatedBy(request.getCreatedBy());
        copy.setQuizCount(request.getQuizCount());
        return copy;
    }

    private UserAccount resolveSubmissionUser(Long requestUserId, String currentUserEmail) {
        if (currentUserEmail == null || currentUserEmail.isBlank()) {
            throw new IllegalArgumentException("Login is required to submit the quiz");
        }
        return userAccountService.getUserByEmail(currentUserEmail);
    }

    private Long resolveCurrentUserId(String currentUserEmail) {
        if (currentUserEmail == null || currentUserEmail.isBlank()) {
            return null;
        }
        return userAccountService.getUserByEmail(currentUserEmail).getId();
    }

    private Map<Long, Long> normalizeAnswers(List<QuizSubmissionAnswerDto> answers, List<QuizQuestion> quizQuestions) {
        Map<Long, QuizQuestion> quizQuestionMap = new HashMap<>();
        for (QuizQuestion quizQuestion : quizQuestions) {
            quizQuestionMap.put(quizQuestion.getId(), quizQuestion);
        }

        Map<Long, Long> normalized = new HashMap<>();
        if (answers == null) {
            return normalized;
        }

        for (QuizSubmissionAnswerDto answer : answers) {
            if (answer == null || answer.getQuizQuestionId() == null) {
                continue;
            }
            if (!quizQuestionMap.containsKey(answer.getQuizQuestionId())) {
                throw new IllegalArgumentException("Invalid quiz question id: " + answer.getQuizQuestionId());
            }
            normalized.put(answer.getQuizQuestionId(), answer.getSelectedChoiceId());
        }
        return normalized;
    }

    private boolean isCorrectChoice(List<QuestionChoice> choices, Long selectedChoiceId) {
        if (selectedChoiceId == null || choices == null) {
            return false;
        }
        for (QuestionChoice choice : choices) {
            if (selectedChoiceId.equals(choice.getId())) {
                return Boolean.TRUE.equals(choice.getIsCorrect());
            }
        }
        return false;
    }

    private Comparator<QuizAttempt> compareAttempts() {
        return Comparator.comparingDouble((QuizAttempt attempt) -> attempt.getScore() == null ? 0.0 : attempt.getScore())
                .reversed()
                .thenComparing(Comparator.comparingInt(
                        (QuizAttempt attempt) -> attempt.getCorrectAnswers() == null ? 0 : attempt.getCorrectAnswers())
                        .reversed())
                .thenComparingInt(
                        attempt -> attempt.getTimeTakenSeconds() == null ? Integer.MAX_VALUE : attempt.getTimeTakenSeconds())
                .thenComparing(attempt -> attempt.getSubmittedAt() == null ? LocalDateTime.MAX : attempt.getSubmittedAt());
    }

    private QuizDto toDto(Quiz quiz) {
        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAsc(quiz.getId());
        return toDto(quiz, quizQuestions);
    }

    private QuizDto toDto(Quiz quiz, List<QuizQuestion> quizQuestions) {
        QuizDto dto = new QuizDto();
        dto.setId(quiz.getId());
        dto.setTitle(quiz.getTitle());
        dto.setSubject(quiz.getSubject());
        dto.setTopic(quiz.getTopic());
        dto.setSubTopic(quiz.getSubTopic());
        if (quiz.getExam() != null) {
            ExamDto examDto = new ExamDto();
            examDto.setId(quiz.getExam().getId());
            examDto.setName(quiz.getExam().getName());
            dto.setExam(examDto);
        }
        dto.setTotalQuestions(quiz.getTotalQuestions());
        dto.setTotalMarks(quiz.getTotalMarks());
        dto.setCreatedBy(quiz.getCreatedBy());
        dto.setCreatedAt(quiz.getCreatedAt());
        dto.setUpdatedAt(quiz.getUpdatedAt());

        List<QuizQuestionDto> questionDtos = new ArrayList<>();
        for (QuizQuestion qq : quizQuestions) {
            QuizQuestionDto qqDto = new QuizQuestionDto();
            qqDto.setId(qq.getId());
            qqDto.setOrderIndex(qq.getOrderIndex());
            qqDto.setMarks(qq.getMarks());
            qqDto.setNegativeMarks(qq.getNegativeMarks());
            QuestionDto qdto = questionService.mapToDto(qq.getQuestion());
            qqDto.setQuestion(qdto);
            questionDtos.add(qqDto);
        }
        dto.setQuestions(questionDtos);
        return dto;
    }

    private LeaderboardUserDto toLeaderboardUserDto(UserAccount user) {
        LeaderboardUserDto dto = new LeaderboardUserDto();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }

    private QuizAttemptResultDto toAttemptResultDto(QuizAttempt attempt) {
        QuizAttemptResultDto dto = new QuizAttemptResultDto();
        dto.setQuizId(attempt.getQuiz().getId());
        dto.setAttemptId(attempt.getId());
        dto.setUserId(attempt.getUser().getId());
        dto.setUsername(attempt.getUser().getUsername());
        dto.setScore(attempt.getScore());
        dto.setTotalMarks(attempt.getTotalMarks());
        dto.setCorrectAnswers(attempt.getCorrectAnswers());
        dto.setWrongAnswers(attempt.getWrongAnswers());
        dto.setAttemptedQuestions(attempt.getAttemptedQuestions());
        dto.setAccuracy(attempt.getAccuracy());
        dto.setTimeTakenSeconds(attempt.getTimeTakenSeconds());
        dto.setSubmittedAt(attempt.getSubmittedAt());
        return dto;
    }

    private QuizListItemDto toListItem(Quiz quiz) {
        QuizListItemDto dto = new QuizListItemDto();
        dto.setId(quiz.getId());
        dto.setTitle(quiz.getTitle());
        dto.setSlug(slugify(quiz.getTitle()) + "-" + quiz.getId());
        return dto;
    }

    private String slugify(String input) {
        if (input == null) {
            return "quiz";
        }
        String slug = input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "quiz" : slug;
    }

    private void removeExistingQuizzes(String subject, String topic) {
        List<Quiz> existing = quizRepository.findBySubjectAndTopic(subject, topic);
        if (existing.isEmpty()) {
            log.info("No existing quizzes to remove: subject='{}', topic='{}'", subject, topic);
            return;
        }
        List<Long> ids = existing.stream().map(Quiz::getId).toList();
        log.info("Removing existing quizzes: subject='{}', topic='{}', quizCount={}", subject, topic, ids.size());
        quizQuestionRepository.deleteByQuizIdIn(ids);
        quizRepository.deleteAllById(ids);
    }

    private QuizDto createAutoQuiz(String subject, String topic, String title, List<Question> selected) {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setSubject(subject);
        quiz.setTopic(topic);
        quiz.setTotalQuestions(selected.size());

        Quiz savedQuiz = quizRepository.save(quiz);
        log.info("Created auto-quiz shell: quizId={}, title='{}', subject='{}', topic='{}', totalQuestions={}",
                savedQuiz.getId(), title, subject, topic, selected.size());

        int order = 1;
        int totalMarks = 0;
        List<QuizQuestion> quizQuestions = new ArrayList<>();
        for (Question q : selected) {
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(savedQuiz);
            qq.setQuestion(q);
            qq.setOrderIndex(order++);
            int marks = q.getMarks() == null ? 1 : q.getMarks();
            double negative = q.getNegativeMarks() == null ? 0.0 : q.getNegativeMarks();
            qq.setMarks(marks);
            qq.setNegativeMarks(negative);
            totalMarks += marks;
            quizQuestions.add(qq);
        }
        quizQuestionRepository.saveAll(quizQuestions);
        savedQuiz.setTotalMarks(totalMarks);
        quizRepository.save(savedQuiz);

        return toDto(savedQuiz, quizQuestions);
    }

    private String buildAutoTitle(String subject, String topic, int setNumber) {
        return subject + " - " + topic + " - Set " + setNumber;
    }
}
