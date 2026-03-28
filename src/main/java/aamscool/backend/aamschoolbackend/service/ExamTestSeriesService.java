package aamscool.backend.aamschoolbackend.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusSummaryDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusTestSeriesBlueprintDto;
import aamscool.backend.aamschoolbackend.dto.ExamTestSeriesGenerateResponseDto;
import aamscool.backend.aamschoolbackend.dto.ExamTestSeriesOverviewDto;
import aamscool.backend.aamschoolbackend.model.Difficulty;
import aamscool.backend.aamschoolbackend.model.Question;
import aamscool.backend.aamschoolbackend.model.Quiz;
import aamscool.backend.aamschoolbackend.model.QuizQuestion;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizQuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizRepository;

@Service
public class ExamTestSeriesService {

    private static final String TEST_SERIES_CREATED_BY = "SYSTEM_TEST_SERIES";
    private static final String TEST_SERIES_TOPIC = "Test Series";

    private final ExamSyllabusService examSyllabusService;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuestionRepository questionRepository;

    public ExamTestSeriesService(ExamSyllabusService examSyllabusService,
                                 QuizRepository quizRepository,
                                 QuizQuestionRepository quizQuestionRepository,
                                 QuestionRepository questionRepository) {
        this.examSyllabusService = examSyllabusService;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.questionRepository = questionRepository;
    }

    public List<ExamTestSeriesOverviewDto> getOverviewForAllExams() {
        List<ExamSyllabusSummaryDto> summaries = examSyllabusService.getAllSummaries();
        List<ExamTestSeriesOverviewDto> out = new ArrayList<>();
        Set<String> seenExamCodes = new LinkedHashSet<>();

        for (ExamSyllabusSummaryDto summary : summaries) {
            String examKey = firstNonBlank(summary.getExamCode(), summary.getExamName());
            if (examKey == null || examKey.isBlank()) {
                continue;
            }
            String dedupe = examKey.trim().toUpperCase();
            if (!seenExamCodes.add(dedupe)) {
                continue;
            }
            getOverviewByExamKey(examKey).ifPresent(out::add);
        }

        out.sort(Comparator.comparing(dto -> safe(dto.getExamName()), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public Optional<ExamTestSeriesOverviewDto> getOverviewByExamKey(String examKey) {
        Optional<GenerationContext> contextOpt = buildContext(examKey);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }

        GenerationContext context = contextOpt.get();
        int additional = computeAdditionalCapacity(
                copyQueue(context.easyPool),
                copyQueue(context.mediumPool),
                copyQueue(context.hardPool),
                context.easyPerSeries,
                context.mediumPerSeries,
                context.hardPerSeries
        );

        ExamTestSeriesOverviewDto dto = new ExamTestSeriesOverviewDto();
        dto.setExamCode(context.examCode);
        dto.setExamName(context.examName);
        dto.setMatchedQuestionExamName("SUBJECT_TOPIC_MAPPED");
        dto.setMatchedQuestionExamId(null);
        dto.setGeneratedSeriesCount(context.generatedSeriesCount);
        dto.setAdditionalGeneratableSeriesCount(additional);
        dto.setTotalGeneratableSeriesCount(context.generatedSeriesCount + additional);
        dto.setQuestionsPerSeries(context.totalQuestionsPerSeries);
        return Optional.of(dto);
    }

    @Transactional
    public Optional<ExamTestSeriesGenerateResponseDto> generateAllPossibleByExamKey(String examKey) {
        Optional<GenerationContext> contextOpt = buildContext(examKey);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }

        GenerationContext context = contextOpt.get();

        int additionalBefore = computeAdditionalCapacity(
                copyQueue(context.easyPool),
                copyQueue(context.mediumPool),
                copyQueue(context.hardPool),
                context.easyPerSeries,
                context.mediumPerSeries,
                context.hardPerSeries
        );

        List<Long> createdQuizIds = new ArrayList<>();
        int nextSetNumber = context.generatedSeriesCount + 1;

        while (true) {
            List<Question> selected = pickOneSeriesQuestions(
                    context.easyPool,
                    context.mediumPool,
                    context.hardPool,
                    context.easyPerSeries,
                    context.mediumPerSeries,
                    context.hardPerSeries
            );
            if (selected == null) {
                break;
            }
            Quiz saved = createSeriesQuiz(context, selected, nextSetNumber);
            createdQuizIds.add(saved.getId());
            nextSetNumber++;
            context.generatedSeriesCount++;
        }

        int additionalAfter = computeAdditionalCapacity(
                copyQueue(context.easyPool),
                copyQueue(context.mediumPool),
                copyQueue(context.hardPool),
                context.easyPerSeries,
                context.mediumPerSeries,
                context.hardPerSeries
        );

        ExamTestSeriesGenerateResponseDto response = new ExamTestSeriesGenerateResponseDto();
        response.setExamCode(context.examCode);
        response.setExamName(context.examName);
        response.setMatchedQuestionExamName("SUBJECT_TOPIC_MAPPED");
        response.setMatchedQuestionExamId(null);
        response.setQuestionsPerSeries(context.totalQuestionsPerSeries);
        response.setGeneratedNow(createdQuizIds.size());
        response.setGeneratedTotalAfterRun(context.generatedSeriesCount);
        response.setTotalGeneratableSeriesCount(context.generatedSeriesCount + additionalAfter);
        response.setAdditionalGeneratableAfterRun(additionalAfter);
        response.setCreatedQuizIds(createdQuizIds);
        response.setMessage(additionalBefore == 0
                ? "No additional test series can be generated with current syllabus mapping and difficulty mix."
                : "Generated all currently possible additional test series.");
        return Optional.of(response);
    }

    private Optional<GenerationContext> buildContext(String examKey) {
        Optional<ExamSyllabusDetailDto> detailOpt = examSyllabusService.getByExamKey(examKey);
        Optional<ExamSyllabusTestSeriesBlueprintDto> blueprintOpt =
                examSyllabusService.getTestSeriesBlueprintByExamKey(examKey);
        if (detailOpt.isEmpty() || blueprintOpt.isEmpty()) {
            return Optional.empty();
        }

        ExamSyllabusDetailDto detail = detailOpt.get();
        ExamSyllabusTestSeriesBlueprintDto blueprint = blueprintOpt.get();

        GenerationContext context = new GenerationContext();
        context.examCode = safe(detail.getSyllabus().getExamCode());
        context.examName = safe(detail.getSyllabus().getExamName());
        context.totalQuestionsPerSeries = detail.getSyllabus().getExamPattern().getTotalQuestions();
        context.totalMarks = detail.getSyllabus().getExamPattern().getTotalMarks();
        context.markPerQuestion = detail.getSyllabus().getExamPattern().getMarkPerQuestion() == null
                ? 1.0
                : detail.getSyllabus().getExamPattern().getMarkPerQuestion();
        context.durationMinutes = detail.getSyllabus().getExamPattern().getDurationMinutes();
        context.negativeMarkingEnabled = Boolean.TRUE.equals(detail.getSyllabus().getExamPattern().getNegativeMarking());
        context.negativeMarkPerQuestion = context.negativeMarkingEnabled
                ? detail.getSyllabus().getExamPattern().getNegativeMarkPerQuestion()
                : 0.0;

        DifficultyMix mix = calculateDifficultyMix(context.totalQuestionsPerSeries);
        context.easyPerSeries = mix.easy;
        context.mediumPerSeries = mix.medium;
        context.hardPerSeries = mix.hard;

        List<Quiz> existingSeries = fetchExistingSeries(context.examCode, context.examName);
        context.generatedSeriesCount = existingSeries.size();

        Set<Long> alreadyUsed = fetchAlreadyUsedQuestionIds(existingSeries);
        List<Question> mappedQuestions = fetchMappedQuestions(blueprint, alreadyUsed);

        context.easyPool = new ArrayDeque<>();
        context.mediumPool = new ArrayDeque<>();
        context.hardPool = new ArrayDeque<>();
        for (Question question : mappedQuestions) {
            if (question.getDifficulty() == null) {
                continue;
            }
            if (question.getDifficulty() == Difficulty.EASY) {
                context.easyPool.add(question);
            } else if (question.getDifficulty() == Difficulty.MEDIUM) {
                context.mediumPool.add(question);
            } else if (question.getDifficulty() == Difficulty.HARD) {
                context.hardPool.add(question);
            }
        }

        return Optional.of(context);
    }

    private Set<Long> fetchAlreadyUsedQuestionIds(List<Quiz> existingSeries) {
        Set<Long> used = new LinkedHashSet<>();
        if (existingSeries == null || existingSeries.isEmpty()) {
            return used;
        }
        List<Long> ids = existingSeries.stream().map(Quiz::getId).toList();
        used.addAll(quizQuestionRepository.findQuestionIdsByQuizIdIn(ids));
        return used;
    }

    private List<Quiz> fetchExistingSeries(String examCode, String examName) {
        Map<Long, Quiz> merged = new LinkedHashMap<>();
        String code = safe(examCode);
        if (code != null && !code.isBlank()) {
            for (Quiz quiz : quizRepository.findByCreatedByAndSubjectAndTopicOrderByCreatedAtAsc(
                    TEST_SERIES_CREATED_BY,
                    code,
                    TEST_SERIES_TOPIC
            )) {
                merged.put(quiz.getId(), quiz);
            }
        }

        String name = safe(examName);
        if (name != null && !name.isBlank() && (code == null || !name.equalsIgnoreCase(code))) {
            for (Quiz quiz : quizRepository.findByCreatedByAndSubjectAndTopicOrderByCreatedAtAsc(
                    TEST_SERIES_CREATED_BY,
                    name,
                    TEST_SERIES_TOPIC
            )) {
                merged.put(quiz.getId(), quiz);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Question> fetchMappedQuestions(ExamSyllabusTestSeriesBlueprintDto blueprint, Set<Long> alreadyUsed) {
        Map<Long, Question> unique = new LinkedHashMap<>();

        if (blueprint.getPapers() == null) {
            return new ArrayList<>();
        }

        for (ExamSyllabusTestSeriesBlueprintDto.PaperBlueprintDto paper : blueprint.getPapers()) {
            if (paper == null || paper.getSubjects() == null) {
                continue;
            }
            for (ExamSyllabusTestSeriesBlueprintDto.SubjectBlueprintDto subject : paper.getSubjects()) {
                if (subject == null) {
                    continue;
                }
                String subjectName = safe(subject.getSubjectName());
                if (subjectName == null || subjectName.isBlank()) {
                    continue;
                }

                List<String> topics = subject.getNormalizedTopics() == null
                        ? List.of()
                        : subject.getNormalizedTopics();

                if (topics.isEmpty()) {
                    for (Question q : questionRepository.findBySubject(subjectName)) {
                        if (q == null || q.getId() == null || alreadyUsed.contains(q.getId())) {
                            continue;
                        }
                        unique.putIfAbsent(q.getId(), q);
                    }
                    continue;
                }

                for (String topic : topics) {
                    String topicName = safe(topic);
                    if (topicName == null || topicName.isBlank()) {
                        continue;
                    }
                    for (Question q : questionRepository.findBySubjectAndTopic(subjectName, topicName)) {
                        if (q == null || q.getId() == null || alreadyUsed.contains(q.getId())) {
                            continue;
                        }
                        unique.putIfAbsent(q.getId(), q);
                    }
                }
            }
        }

        List<Question> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(Question::getId));
        return result;
    }

    private int computeAdditionalCapacity(ArrayDeque<Question> easyPool,
                                          ArrayDeque<Question> mediumPool,
                                          ArrayDeque<Question> hardPool,
                                          int easyPerSeries,
                                          int mediumPerSeries,
                                          int hardPerSeries) {
        int count = 0;
        while (true) {
            List<Question> picked = pickOneSeriesQuestions(easyPool, mediumPool, hardPool,
                    easyPerSeries, mediumPerSeries, hardPerSeries);
            if (picked == null) {
                break;
            }
            count++;
        }
        return count;
    }

    private List<Question> pickOneSeriesQuestions(ArrayDeque<Question> easyPool,
                                                  ArrayDeque<Question> mediumPool,
                                                  ArrayDeque<Question> hardPool,
                                                  int easyCount,
                                                  int mediumCount,
                                                  int hardCount) {
        if (easyPool.size() < easyCount || mediumPool.size() < mediumCount || hardPool.size() < hardCount) {
            return null;
        }

        List<Question> out = new ArrayList<>(easyCount + mediumCount + hardCount);
        for (int i = 0; i < easyCount; i++) {
            out.add(easyPool.pollFirst());
        }
        for (int i = 0; i < mediumCount; i++) {
            out.add(mediumPool.pollFirst());
        }
        for (int i = 0; i < hardCount; i++) {
            out.add(hardPool.pollFirst());
        }
        return out;
    }

    private Quiz createSeriesQuiz(GenerationContext context, List<Question> selected, int setNumber) {
        Quiz quiz = new Quiz();
        quiz.setTitle(buildSeriesTitle(context.examName, setNumber));
        quiz.setSubject(context.examCode);
        quiz.setTopic(TEST_SERIES_TOPIC);
        quiz.setSubTopic("Set " + setNumber);
        quiz.setCreatedBy(TEST_SERIES_CREATED_BY);
        quiz.setTotalQuestions(context.totalQuestionsPerSeries);
        quiz.setTotalMarks(context.totalMarks);
        quiz.setDurationMinutes(context.durationMinutes);
        Quiz saved = quizRepository.save(quiz);

        List<QuizQuestion> rows = new ArrayList<>();
        int order = 1;
        for (Question question : selected) {
            QuizQuestion row = new QuizQuestion();
            row.setQuiz(saved);
            row.setQuestion(question);
            row.setOrderIndex(order++);
            row.setMarks(context.markPerQuestion);
            row.setNegativeMarks(context.negativeMarkPerQuestion == null ? 0.0 : context.negativeMarkPerQuestion);
            rows.add(row);
        }
        quizQuestionRepository.saveAll(rows);
        return saved;
    }

    private String buildSeriesTitle(String examName, int setNumber) {
        String base = safe(examName);
        if (base == null || base.isBlank()) {
            base = "Exam";
        }
        return base + " Test Series - Set " + setNumber;
    }

    private DifficultyMix calculateDifficultyMix(int totalQuestions) {
        int hard = (int) Math.floor(totalQuestions * 0.40);
        int medium = (int) Math.floor(totalQuestions * 0.40);
        int easy = totalQuestions - hard - medium;
        return new DifficultyMix(easy, medium, hard);
    }

    private ArrayDeque<Question> copyQueue(ArrayDeque<Question> source) {
        return new ArrayDeque<>(source);
    }

    private String firstNonBlank(String first, String second) {
        String a = safe(first);
        if (a != null && !a.isBlank()) {
            return a;
        }
        return safe(second);
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }

    private static class DifficultyMix {
        private final int easy;
        private final int medium;
        private final int hard;

        private DifficultyMix(int easy, int medium, int hard) {
            this.easy = easy;
            this.medium = medium;
            this.hard = hard;
        }
    }

    private static class GenerationContext {
        private String examCode;
        private String examName;
        private int totalQuestionsPerSeries;
        private int totalMarks;
        private Double markPerQuestion;
        private int durationMinutes;
        private boolean negativeMarkingEnabled;
        private Double negativeMarkPerQuestion;
        private int easyPerSeries;
        private int mediumPerSeries;
        private int hardPerSeries;
        private int generatedSeriesCount;
        private ArrayDeque<Question> easyPool = new ArrayDeque<>();
        private ArrayDeque<Question> mediumPool = new ArrayDeque<>();
        private ArrayDeque<Question> hardPool = new ArrayDeque<>();
    }
}
