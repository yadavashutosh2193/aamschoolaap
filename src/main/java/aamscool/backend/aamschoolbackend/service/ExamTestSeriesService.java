package aamscool.backend.aamschoolbackend.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import aamscool.backend.aamschoolbackend.model.QuizType;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizQuestionRepository;
import aamscool.backend.aamschoolbackend.repository.QuizRepository;

@Service
public class ExamTestSeriesService {

    private static final String TEST_SERIES_CREATED_BY = "SYSTEM_TEST_SERIES";
    private static final String TEST_SERIES_TOPIC = "Test Series";
    private static final String SECTIONAL_PREFIX = "SECTIONAL_TEST::";
    private static final String FULL_PREFIX = "FULL_TEST::";
    private static final String PYQ_PREFIX = "PYQ_TEST::";
    private static final int DEFAULT_SECTIONAL_QUESTIONS = 25;
    private static final int MIN_SECTIONAL_QUESTIONS = 10;

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
            String dedupe = examKey.trim().toUpperCase(Locale.ROOT);
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
        int fullAdditional = computeAdditionalCapacity(
                copyQueue(context.fullEasyPool),
                copyQueue(context.fullMediumPool),
                copyQueue(context.fullHardPool),
                context.fullEasyPerSeries,
                context.fullMediumPerSeries,
                context.fullHardPerSeries
        );
        int pyqAdditional = computeAdditionalCapacity(
                copyQueue(context.pyqEasyPool),
                copyQueue(context.pyqMediumPool),
                copyQueue(context.pyqHardPool),
                context.pyqEasyPerSeries,
                context.pyqMediumPerSeries,
                context.pyqHardPerSeries
        );

        int sectionalGenerated = 0;
        int sectionalAdditional = 0;
        for (SectionalContext sectional : context.sectionalContexts) {
            sectionalGenerated += sectional.generatedSeriesCount;
            sectionalAdditional += computeAdditionalCapacity(
                    copyQueue(sectional.easyPool),
                    copyQueue(sectional.mediumPool),
                    copyQueue(sectional.hardPool),
                    sectional.easyPerSeries,
                    sectional.mediumPerSeries,
                    sectional.hardPerSeries
            );
        }

        int generatedAll = context.fullGeneratedSeriesCount + sectionalGenerated + context.pyqGeneratedSeriesCount;
        int additionalAll = fullAdditional + sectionalAdditional + pyqAdditional;

        ExamTestSeriesOverviewDto dto = new ExamTestSeriesOverviewDto();
        dto.setExamCode(context.examCode);
        dto.setExamName(context.examName);
        dto.setMatchedQuestionExamName(context.matchedQuestionExamName);
        dto.setMatchedQuestionExamId(context.matchedQuestionExamId);
        dto.setGeneratedSeriesCount(generatedAll);
        dto.setAdditionalGeneratableSeriesCount(additionalAll);
        dto.setTotalGeneratableSeriesCount(generatedAll + additionalAll);
        dto.setQuestionsPerSeries(context.totalQuestionsPerSeries);

        dto.setGeneratedFullTestCount(context.fullGeneratedSeriesCount);
        dto.setGeneratedSectionalTestCount(sectionalGenerated);
        dto.setGeneratedPyqTestCount(context.pyqGeneratedSeriesCount);
        dto.setAdditionalGeneratableFullTestCount(fullAdditional);
        dto.setAdditionalGeneratableSectionalTestCount(sectionalAdditional);
        dto.setAdditionalGeneratablePyqTestCount(pyqAdditional);
        dto.setTotalGeneratableFullTestCount(context.fullGeneratedSeriesCount + fullAdditional);
        dto.setTotalGeneratableSectionalTestCount(sectionalGenerated + sectionalAdditional);
        dto.setTotalGeneratablePyqTestCount(context.pyqGeneratedSeriesCount + pyqAdditional);
        return Optional.of(dto);
    }

    @Transactional
    public Optional<ExamTestSeriesGenerateResponseDto> generateAllPossibleByExamKey(String examKey) {
        Optional<GenerationContext> contextOpt = buildContext(examKey);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }

        GenerationContext context = contextOpt.get();
        List<Long> createdFull = new ArrayList<>();
        List<Long> createdSectional = new ArrayList<>();
        List<Long> createdPyq = new ArrayList<>();

        int nextFullSet = context.fullGeneratedSeriesCount + 1;
        while (true) {
            List<Question> selected = pickOneSeriesQuestions(
                    context.fullEasyPool, context.fullMediumPool, context.fullHardPool,
                    context.fullEasyPerSeries, context.fullMediumPerSeries, context.fullHardPerSeries
            );
            if (selected == null) {
                break;
            }
            Quiz saved = createSeriesQuiz(
                    context.examSubjectKey,
                    QuizType.FULL_TEST,
                    buildFullSeriesTitle(context.examName, nextFullSet),
                    buildFullSubTopic(nextFullSet),
                    context.totalQuestionsPerSeries,
                    context.totalMarks,
                    context.durationMinutes,
                    context.markPerQuestion,
                    context.negativeMarkPerQuestion,
                    selected
            );
            createdFull.add(saved.getId());
            context.fullGeneratedSeriesCount++;
            nextFullSet++;
        }

        for (SectionalContext sectional : context.sectionalContexts) {
            int nextSet = sectional.generatedSeriesCount + 1;
            while (true) {
                List<Question> selected = pickOneSeriesQuestions(
                        sectional.easyPool, sectional.mediumPool, sectional.hardPool,
                        sectional.easyPerSeries, sectional.mediumPerSeries, sectional.hardPerSeries
                );
                if (selected == null) {
                    break;
                }
                Quiz saved = createSeriesQuiz(
                        context.examSubjectKey,
                        QuizType.SECTIONAL_TEST,
                        buildSectionalSeriesTitle(context.examName, sectional.subjectName, nextSet),
                        buildSectionalSubTopic(sectional.subjectName, nextSet),
                        sectional.questionsPerSeries,
                        sectional.totalMarks,
                        sectional.durationMinutes,
                        context.markPerQuestion,
                        context.negativeMarkPerQuestion,
                        selected
                );
                createdSectional.add(saved.getId());
                sectional.generatedSeriesCount++;
                nextSet++;
            }
        }

        int nextPyqSet = context.pyqGeneratedSeriesCount + 1;
        while (true) {
            List<Question> selected = pickOneSeriesQuestions(
                    context.pyqEasyPool, context.pyqMediumPool, context.pyqHardPool,
                    context.pyqEasyPerSeries, context.pyqMediumPerSeries, context.pyqHardPerSeries
            );
            if (selected == null) {
                break;
            }
            Quiz saved = createSeriesQuiz(
                    context.examSubjectKey,
                    QuizType.PYQ_TEST,
                    buildPyqSeriesTitle(context.examName, nextPyqSet),
                    buildPyqSubTopic(nextPyqSet),
                    context.totalQuestionsPerSeries,
                    context.totalMarks,
                    context.durationMinutes,
                    context.markPerQuestion,
                    context.negativeMarkPerQuestion,
                    selected
            );
            createdPyq.add(saved.getId());
            context.pyqGeneratedSeriesCount++;
            nextPyqSet++;
        }

        int fullAdditionalAfter = computeAdditionalCapacity(
                copyQueue(context.fullEasyPool),
                copyQueue(context.fullMediumPool),
                copyQueue(context.fullHardPool),
                context.fullEasyPerSeries,
                context.fullMediumPerSeries,
                context.fullHardPerSeries
        );
        int pyqAdditionalAfter = computeAdditionalCapacity(
                copyQueue(context.pyqEasyPool),
                copyQueue(context.pyqMediumPool),
                copyQueue(context.pyqHardPool),
                context.pyqEasyPerSeries,
                context.pyqMediumPerSeries,
                context.pyqHardPerSeries
        );

        int sectionalGeneratedAfter = 0;
        int sectionalAdditionalAfter = 0;
        for (SectionalContext sectional : context.sectionalContexts) {
            sectionalGeneratedAfter += sectional.generatedSeriesCount;
            sectionalAdditionalAfter += computeAdditionalCapacity(
                    copyQueue(sectional.easyPool),
                    copyQueue(sectional.mediumPool),
                    copyQueue(sectional.hardPool),
                    sectional.easyPerSeries,
                    sectional.mediumPerSeries,
                    sectional.hardPerSeries
            );
        }

        int generatedAllAfter = context.fullGeneratedSeriesCount + sectionalGeneratedAfter + context.pyqGeneratedSeriesCount;
        int additionalAllAfter = fullAdditionalAfter + sectionalAdditionalAfter + pyqAdditionalAfter;

        List<Long> createdAll = new ArrayList<>();
        createdAll.addAll(createdFull);
        createdAll.addAll(createdSectional);
        createdAll.addAll(createdPyq);

        ExamTestSeriesGenerateResponseDto response = new ExamTestSeriesGenerateResponseDto();
        response.setExamCode(context.examCode);
        response.setExamName(context.examName);
        response.setMatchedQuestionExamName(context.matchedQuestionExamName);
        response.setMatchedQuestionExamId(context.matchedQuestionExamId);
        response.setQuestionsPerSeries(context.totalQuestionsPerSeries);
        response.setGeneratedNow(createdAll.size());
        response.setGeneratedTotalAfterRun(generatedAllAfter);
        response.setTotalGeneratableSeriesCount(generatedAllAfter + additionalAllAfter);
        response.setAdditionalGeneratableAfterRun(additionalAllAfter);
        response.setCreatedQuizIds(createdAll);

        response.setGeneratedNowFullTestCount(createdFull.size());
        response.setGeneratedNowSectionalTestCount(createdSectional.size());
        response.setGeneratedNowPyqTestCount(createdPyq.size());
        response.setGeneratedTotalFullTestAfterRun(context.fullGeneratedSeriesCount);
        response.setGeneratedTotalSectionalTestAfterRun(sectionalGeneratedAfter);
        response.setGeneratedTotalPyqTestAfterRun(context.pyqGeneratedSeriesCount);
        response.setAdditionalGeneratableFullTestAfterRun(fullAdditionalAfter);
        response.setAdditionalGeneratableSectionalTestAfterRun(sectionalAdditionalAfter);
        response.setAdditionalGeneratablePyqTestAfterRun(pyqAdditionalAfter);
        response.setCreatedFullTestQuizIds(createdFull);
        response.setCreatedSectionalTestQuizIds(createdSectional);
        response.setCreatedPyqTestQuizIds(createdPyq);
        response.setMessage(createdAll.isEmpty()
                ? "No additional Full/Sectional/PYQ test series can be generated with current question bank."
                : "Generated available Full, Sectional, and PYQ test series.");
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
        context.examSubjectKey = firstNonBlank(context.examCode, context.examName);
        context.matchedQuestionExamName = safe(blueprint.getMatchedQuestionExamName());
        context.matchedQuestionExamId = blueprint.getMatchedQuestionExamId();
        context.totalQuestionsPerSeries = detail.getSyllabus().getExamPattern().getTotalQuestions();
        context.totalMarks = detail.getSyllabus().getExamPattern().getTotalMarks();
        context.markPerQuestion = detail.getSyllabus().getExamPattern().getMarkPerQuestion() == null
                ? 1.0
                : detail.getSyllabus().getExamPattern().getMarkPerQuestion();
        context.durationMinutes = detail.getSyllabus().getExamPattern().getDurationMinutes();
        context.negativeMarkPerQuestion = Boolean.TRUE.equals(detail.getSyllabus().getExamPattern().getNegativeMarking())
                ? detail.getSyllabus().getExamPattern().getNegativeMarkPerQuestion()
                : 0.0;

        List<Quiz> existingAll = fetchExistingSeries(context.examCode, context.examName);
        Map<Long, Set<Long>> usedQuestionIdsByQuiz = loadQuestionIdsByQuiz(existingAll);
        Map<String, List<Question>> mappedBySubject = fetchMappedQuestionsBySubject(blueprint);
        Map<String, Integer> sectionalQuestionTargets = extractSectionalQuestionTargets(blueprint);

        List<Quiz> existingFull = filterByFormat(existingAll, QuizType.FULL_TEST);
        List<Quiz> existingSectional = filterByFormat(existingAll, QuizType.SECTIONAL_TEST);
        List<Quiz> existingPyq = filterByFormat(existingAll, QuizType.PYQ_TEST);

        context.fullGeneratedSeriesCount = existingFull.size();
        context.pyqGeneratedSeriesCount = existingPyq.size();

        Set<Long> usedByFull = collectUsedQuestionIds(existingFull, usedQuestionIdsByQuiz);
        List<Question> fullPool = flattenQuestions(mappedBySubject, usedByFull, false);
        DifficultyMix fullMix = calculateDifficultyMix(context.totalQuestionsPerSeries);
        context.fullEasyPerSeries = fullMix.easy;
        context.fullMediumPerSeries = fullMix.medium;
        context.fullHardPerSeries = fullMix.hard;
        populateDifficultyPools(fullPool, context.fullEasyPool, context.fullMediumPool, context.fullHardPool);

        Set<Long> usedByPyq = collectUsedQuestionIds(existingPyq, usedQuestionIdsByQuiz);
        List<Question> pyqPool = flattenQuestions(mappedBySubject, usedByPyq, true);
        DifficultyMix pyqMix = calculateDifficultyMix(context.totalQuestionsPerSeries);
        context.pyqEasyPerSeries = pyqMix.easy;
        context.pyqMediumPerSeries = pyqMix.medium;
        context.pyqHardPerSeries = pyqMix.hard;
        populateDifficultyPools(pyqPool, context.pyqEasyPool, context.pyqMediumPool, context.pyqHardPool);

        for (Map.Entry<String, List<Question>> entry : mappedBySubject.entrySet()) {
            String subjectName = entry.getKey();
            List<Question> subjectQuestions = entry.getValue();
            if (subjectQuestions == null || subjectQuestions.isEmpty()) {
                continue;
            }

            List<Quiz> subjectExisting = new ArrayList<>();
            for (Quiz quiz : existingSectional) {
                if (belongsToSectionalSubject(quiz, subjectName)) {
                    subjectExisting.add(quiz);
                }
            }
            Set<Long> usedBySectionalSubject = collectUsedQuestionIds(subjectExisting, usedQuestionIdsByQuiz);
            List<Question> remaining = new ArrayList<>();
            for (Question q : subjectQuestions) {
                if (q != null && q.getId() != null && !usedBySectionalSubject.contains(q.getId())) {
                    remaining.add(q);
                }
            }

            int configured = sectionalQuestionTargets.getOrDefault(normalizeKey(subjectName), 0);
            int questionsPerSeries = resolveSectionalQuestionsPerSeries(
                    configured,
                    context.totalQuestionsPerSeries,
                    remaining.size()
            );
            if (questionsPerSeries <= 0) {
                continue;
            }

            DifficultyMix sectionalMix = calculateDifficultyMix(questionsPerSeries);
            SectionalContext sectional = new SectionalContext();
            sectional.subjectName = subjectName;
            sectional.questionsPerSeries = questionsPerSeries;
            sectional.totalMarks = (int) Math.round(context.markPerQuestion * questionsPerSeries);
            sectional.durationMinutes = resolveSectionalDuration(
                    context.durationMinutes,
                    context.totalQuestionsPerSeries,
                    questionsPerSeries
            );
            sectional.easyPerSeries = sectionalMix.easy;
            sectional.mediumPerSeries = sectionalMix.medium;
            sectional.hardPerSeries = sectionalMix.hard;
            sectional.generatedSeriesCount = subjectExisting.size();
            populateDifficultyPools(remaining, sectional.easyPool, sectional.mediumPool, sectional.hardPool);
            context.sectionalContexts.add(sectional);
        }

        context.sectionalContexts.sort(Comparator.comparing(sc -> safe(sc.subjectName), String.CASE_INSENSITIVE_ORDER));
        return Optional.of(context);
    }

    private Map<Long, Set<Long>> loadQuestionIdsByQuiz(List<Quiz> quizzes) {
        Map<Long, Set<Long>> byQuiz = new LinkedHashMap<>();
        if (quizzes == null || quizzes.isEmpty()) {
            return byQuiz;
        }
        List<Long> quizIds = quizzes.stream().map(Quiz::getId).toList();
        for (Object[] row : quizQuestionRepository.findQuizIdAndQuestionIdByQuizIdIn(quizIds)) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            Long quizId = ((Number) row[0]).longValue();
            Long questionId = ((Number) row[1]).longValue();
            byQuiz.computeIfAbsent(quizId, key -> new LinkedHashSet<>()).add(questionId);
        }
        return byQuiz;
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

    private List<Quiz> filterByFormat(List<Quiz> quizzes, QuizType format) {
        List<Quiz> out = new ArrayList<>();
        if (quizzes == null || quizzes.isEmpty()) {
            return out;
        }
        for (Quiz quiz : quizzes) {
            if (inferSeriesFormat(quiz) == format) {
                out.add(quiz);
            }
        }
        return out;
    }

    private Set<Long> collectUsedQuestionIds(List<Quiz> quizzes, Map<Long, Set<Long>> usedQuestionIdsByQuiz) {
        Set<Long> used = new LinkedHashSet<>();
        if (quizzes == null || quizzes.isEmpty()) {
            return used;
        }
        for (Quiz quiz : quizzes) {
            Set<Long> ids = usedQuestionIdsByQuiz.get(quiz.getId());
            if (ids != null) {
                used.addAll(ids);
            }
        }
        return used;
    }

    private Map<String, List<Question>> fetchMappedQuestionsBySubject(ExamSyllabusTestSeriesBlueprintDto blueprint) {
        Map<String, SubjectQuestionBucket> normalizedBuckets = new LinkedHashMap<>();
        if (blueprint.getPapers() == null) {
            return new LinkedHashMap<>();
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

                String key = normalizeKey(subjectName);
                SubjectQuestionBucket bucket = normalizedBuckets.computeIfAbsent(key,
                        ignored -> new SubjectQuestionBucket(subjectName));
                List<String> topics = subject.getNormalizedTopics() == null ? List.of() : subject.getNormalizedTopics();

                if (topics.isEmpty()) {
                    for (Question q : questionRepository.findBySubject(subjectName)) {
                        if (q == null || q.getId() == null) {
                            continue;
                        }
                        bucket.questions.putIfAbsent(q.getId(), q);
                    }
                    continue;
                }

                for (String topic : topics) {
                    String topicName = safe(topic);
                    if (topicName == null || topicName.isBlank()) {
                        continue;
                    }
                    for (Question q : questionRepository.findBySubjectAndTopic(subjectName, topicName)) {
                        if (q == null || q.getId() == null) {
                            continue;
                        }
                        bucket.questions.putIfAbsent(q.getId(), q);
                    }
                }
            }
        }

        Map<String, List<Question>> out = new LinkedHashMap<>();
        for (SubjectQuestionBucket bucket : normalizedBuckets.values()) {
            List<Question> list = new ArrayList<>(bucket.questions.values());
            list.sort(Comparator.comparing(Question::getId));
            out.put(bucket.subjectName, list);
        }
        return out;
    }

    private Map<String, Integer> extractSectionalQuestionTargets(ExamSyllabusTestSeriesBlueprintDto blueprint) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (blueprint.getPapers() == null) {
            return out;
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
                Integer target = subject.getSyllabusQuestions();
                if (target == null || target <= 0) {
                    continue;
                }
                out.putIfAbsent(normalizeKey(subjectName), target);
            }
        }
        return out;
    }

    private List<Question> flattenQuestions(Map<String, List<Question>> bySubject, Set<Long> used, boolean pyqOnly) {
        Map<Long, Question> unique = new LinkedHashMap<>();
        for (List<Question> list : bySubject.values()) {
            for (Question q : list) {
                if (q == null || q.getId() == null || used.contains(q.getId())) {
                    continue;
                }
                if (pyqOnly && !isPyqQuestion(q)) {
                    continue;
                }
                unique.putIfAbsent(q.getId(), q);
            }
        }
        List<Question> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparing(Question::getId));
        return out;
    }

    private boolean isPyqQuestion(Question question) {
        if (question == null) {
            return false;
        }
        if (question.getSourceYear() != null) {
            return true;
        }
        String sourceType = safe(question.getSourceType());
        if (sourceType == null) {
            return false;
        }
        String normalized = sourceType.toLowerCase(Locale.ROOT);
        return normalized.contains("pyq")
                || normalized.contains("previous")
                || normalized.contains("prev year")
                || normalized.contains("past year");
    }

    private int resolveSectionalQuestionsPerSeries(int configuredQuestions, int fullTotalQuestions, int available) {
        if (available < MIN_SECTIONAL_QUESTIONS) {
            return 0;
        }
        int target = configuredQuestions > 0
                ? configuredQuestions
                : Math.min(DEFAULT_SECTIONAL_QUESTIONS, Math.max(MIN_SECTIONAL_QUESTIONS, fullTotalQuestions / 2));
        if (target > available) {
            target = available;
        }
        return target < MIN_SECTIONAL_QUESTIONS ? 0 : target;
    }

    private int resolveSectionalDuration(int fullDuration, int fullQuestions, int sectionalQuestions) {
        if (fullDuration <= 0 || fullQuestions <= 0 || sectionalQuestions <= 0) {
            return 30;
        }
        int scaled = (int) Math.round((fullDuration * sectionalQuestions) / (double) fullQuestions);
        return Math.max(15, scaled);
    }

    private void populateDifficultyPools(List<Question> questions,
                                         ArrayDeque<Question> easyPool,
                                         ArrayDeque<Question> mediumPool,
                                         ArrayDeque<Question> hardPool) {
        for (Question question : questions) {
            if (question.getDifficulty() == null) {
                continue;
            }
            if (question.getDifficulty() == Difficulty.EASY) {
                easyPool.add(question);
            } else if (question.getDifficulty() == Difficulty.MEDIUM) {
                mediumPool.add(question);
            } else if (question.getDifficulty() == Difficulty.HARD) {
                hardPool.add(question);
            }
        }
    }

    private int computeAdditionalCapacity(ArrayDeque<Question> easyPool,
                                          ArrayDeque<Question> mediumPool,
                                          ArrayDeque<Question> hardPool,
                                          int easyPerSeries,
                                          int mediumPerSeries,
                                          int hardPerSeries) {
        int count = 0;
        while (true) {
            List<Question> picked = pickOneSeriesQuestions(
                    easyPool,
                    mediumPool,
                    hardPool,
                    easyPerSeries,
                    mediumPerSeries,
                    hardPerSeries
            );
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

    private Quiz createSeriesQuiz(String examSubject,
                                  QuizType quizType,
                                  String title,
                                  String subTopic,
                                  int totalQuestions,
                                  int totalMarks,
                                  int durationMinutes,
                                  Double markPerQuestion,
                                  Double negativeMarkPerQuestion,
                                  List<Question> selected) {
        Quiz quiz = new Quiz();
        quiz.setTitle(title);
        quiz.setSubject(examSubject);
        quiz.setTopic(TEST_SERIES_TOPIC);
        quiz.setSubTopic(subTopic);
        quiz.setQuizType(quizType);
        quiz.setCreatedBy(TEST_SERIES_CREATED_BY);
        quiz.setTotalQuestions(totalQuestions);
        quiz.setTotalMarks(totalMarks);
        quiz.setDurationMinutes(durationMinutes);
        Quiz saved = quizRepository.save(quiz);

        List<QuizQuestion> rows = new ArrayList<>();
        int order = 1;
        for (Question question : selected) {
            QuizQuestion row = new QuizQuestion();
            row.setQuiz(saved);
            row.setQuestion(question);
            row.setOrderIndex(order++);
            row.setMarks(markPerQuestion == null ? 1.0 : markPerQuestion);
            row.setNegativeMarks(negativeMarkPerQuestion == null ? 0.0 : negativeMarkPerQuestion);
            rows.add(row);
        }
        quizQuestionRepository.saveAll(rows);
        return saved;
    }

    private String buildFullSeriesTitle(String examName, int setNumber) {
        String base = safe(examName);
        if (base == null || base.isBlank()) {
            base = "Exam";
        }
        return base + " Full Test - Set " + setNumber;
    }

    private String buildSectionalSeriesTitle(String examName, String subjectName, int setNumber) {
        String base = safe(examName);
        if (base == null || base.isBlank()) {
            base = "Exam";
        }
        return base + " Sectional Test - " + subjectName + " - Set " + setNumber;
    }

    private String buildPyqSeriesTitle(String examName, int setNumber) {
        String base = safe(examName);
        if (base == null || base.isBlank()) {
            base = "Exam";
        }
        return base + " PYQ Test - Set " + setNumber;
    }

    private String buildFullSubTopic(int setNumber) {
        return FULL_PREFIX + "Set " + setNumber;
    }

    private String buildSectionalSubTopic(String subjectName, int setNumber) {
        return SECTIONAL_PREFIX + subjectName + "::Set " + setNumber;
    }

    private String buildPyqSubTopic(int setNumber) {
        return PYQ_PREFIX + "Set " + setNumber;
    }

    private boolean belongsToSectionalSubject(Quiz quiz, String subjectName) {
        if (quiz == null || subjectName == null) {
            return false;
        }
        String target = normalizeKey(subjectName);
        String subTopic = safe(quiz.getSubTopic());
        if (subTopic != null && subTopic.startsWith(SECTIONAL_PREFIX)) {
            String rest = subTopic.substring(SECTIONAL_PREFIX.length());
            int cut = rest.indexOf("::Set ");
            String extracted = cut >= 0 ? rest.substring(0, cut) : rest;
            return normalizeKey(extracted).equals(target);
        }
        String title = safe(quiz.getTitle());
        if (title == null) {
            return false;
        }
        String marker = "sectional test - ";
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        int markerIdx = normalizedTitle.indexOf(marker);
        if (markerIdx < 0) {
            return false;
        }
        String extracted = title.substring(markerIdx + marker.length());
        int setIdx = extracted.toLowerCase(Locale.ROOT).lastIndexOf(" - set ");
        if (setIdx >= 0) {
            extracted = extracted.substring(0, setIdx);
        }
        return normalizeKey(extracted).equals(target);
    }

    private QuizType inferSeriesFormat(Quiz quiz) {
        if (quiz.getQuizType() != null) {
            return quiz.getQuizType();
        }
        String text = (safe(quiz.getTitle()) + " " + safe(quiz.getSubTopic())).toLowerCase(Locale.ROOT);
        if (text.contains("sectional")) {
            return QuizType.SECTIONAL_TEST;
        }
        if (text.contains("pyq") || text.contains("previous year") || text.contains("prev year")) {
            return QuizType.PYQ_TEST;
        }
        return QuizType.FULL_TEST;
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

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
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

    private static class SectionalContext {
        private String subjectName;
        private int questionsPerSeries;
        private int totalMarks;
        private int durationMinutes;
        private int easyPerSeries;
        private int mediumPerSeries;
        private int hardPerSeries;
        private int generatedSeriesCount;
        private ArrayDeque<Question> easyPool = new ArrayDeque<>();
        private ArrayDeque<Question> mediumPool = new ArrayDeque<>();
        private ArrayDeque<Question> hardPool = new ArrayDeque<>();
    }

    private static class GenerationContext {
        private String examCode;
        private String examName;
        private String examSubjectKey;
        private String matchedQuestionExamName;
        private Long matchedQuestionExamId;

        private int totalQuestionsPerSeries;
        private int totalMarks;
        private Double markPerQuestion;
        private int durationMinutes;
        private Double negativeMarkPerQuestion;

        private int fullEasyPerSeries;
        private int fullMediumPerSeries;
        private int fullHardPerSeries;
        private int fullGeneratedSeriesCount;
        private ArrayDeque<Question> fullEasyPool = new ArrayDeque<>();
        private ArrayDeque<Question> fullMediumPool = new ArrayDeque<>();
        private ArrayDeque<Question> fullHardPool = new ArrayDeque<>();

        private int pyqEasyPerSeries;
        private int pyqMediumPerSeries;
        private int pyqHardPerSeries;
        private int pyqGeneratedSeriesCount;
        private ArrayDeque<Question> pyqEasyPool = new ArrayDeque<>();
        private ArrayDeque<Question> pyqMediumPool = new ArrayDeque<>();
        private ArrayDeque<Question> pyqHardPool = new ArrayDeque<>();

        private List<SectionalContext> sectionalContexts = new ArrayList<>();
    }

    private static class SubjectQuestionBucket {
        private final String subjectName;
        private final Map<Long, Question> questions = new LinkedHashMap<>();

        private SubjectQuestionBucket(String subjectName) {
            this.subjectName = subjectName;
        }
    }
}
