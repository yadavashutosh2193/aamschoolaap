package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.AdminQuestionGenerationPreviewDto;
import aamscool.backend.aamschoolbackend.dto.AdminQuestionGenerationRequestDto;
import aamscool.backend.aamschoolbackend.dto.AdminSyllabusBatchGenerationRequestDto;
import aamscool.backend.aamschoolbackend.dto.AdminSyllabusBatchGenerationResponseDto;
import aamscool.backend.aamschoolbackend.dto.AdminSyllabusQuestionCoverageDto;
import aamscool.backend.aamschoolbackend.dto.AttemptStatsDto;
import aamscool.backend.aamschoolbackend.dto.CorrectAnswerDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusMasterDto;
import aamscool.backend.aamschoolbackend.dto.ExamTestSeriesGenerateResponseDto;
import aamscool.backend.aamschoolbackend.dto.ExplanationDto;
import aamscool.backend.aamschoolbackend.dto.QuestionBulkUpsertResult;
import aamscool.backend.aamschoolbackend.dto.QuestionChoiceDto;
import aamscool.backend.aamschoolbackend.dto.QuestionDto;
import aamscool.backend.aamschoolbackend.dto.SourceDto;
import aamscool.backend.aamschoolbackend.model.Exam;
import aamscool.backend.aamschoolbackend.model.Language;
import aamscool.backend.aamschoolbackend.model.QuestionStatus;
import aamscool.backend.aamschoolbackend.model.QuestionType;
import aamscool.backend.aamschoolbackend.repository.ExamRepository;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;
import aamscool.backend.aamschoolbackend.util.JsonRepairUtil;

@Service
public class AdminSyllabusQuestionService {
    private static final Logger log = LoggerFactory.getLogger(AdminSyllabusQuestionService.class);

    private static final int DEFAULT_TOTAL = 100;
    private static final int DEFAULT_EASY = 20;
    private static final int DEFAULT_MEDIUM = 40;
    private static final int DEFAULT_HARD = 40;
    private static final int MAX_ATTEMPTS_PER_DIFFICULTY = 10;
    private static final int MAX_QUESTIONS_PER_AI_CALL = 12;
    private static final int MINI_ONLY_ATTEMPTS_PER_DIFFICULTY = 2;
    private static final int MAX_AUTO_REFINE_ROUNDS = 4;
    private static final int MAX_AVOID_PROMPT_QUESTIONS = 120;
    private static final int MAX_AVOID_PROMPT_CHARS = 6000;
    private static final int MIN_EXPLANATION_LENGTH = 28;
    private static final int MIN_QUESTION_LENGTH = 12;
    private static final DateTimeFormatter CODE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ACRONYM_STOP_WORDS = Set.of(
            "AND", "OF", "THE", "FOR", "TO", "IN", "ON", "WITH", "A", "AN"
    );

    private final ExamSyllabusService examSyllabusService;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;
    private final QuestionService questionService;
    private final ExamTestSeriesService examTestSeriesService;
    private final AdminAlertService adminAlertService;
    private final McqGenerationArtifactService mcqGenerationArtifactService;

    public AdminSyllabusQuestionService(ExamSyllabusService examSyllabusService,
                                        ExamRepository examRepository,
                                        QuestionRepository questionRepository,
                                        OpenAIService openAIService,
                                        ObjectMapper objectMapper,
                                        QuestionService questionService,
                                        ExamTestSeriesService examTestSeriesService,
                                        AdminAlertService adminAlertService,
                                        McqGenerationArtifactService mcqGenerationArtifactService) {
        this.examSyllabusService = examSyllabusService;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
        this.questionService = questionService;
        this.examTestSeriesService = examTestSeriesService;
        this.adminAlertService = adminAlertService;
        this.mcqGenerationArtifactService = mcqGenerationArtifactService;
    }

    public Optional<AdminSyllabusQuestionCoverageDto> getCoverageByExamKey(String examKey) {
        Optional<ExamSyllabusDetailDto> detailOpt = examSyllabusService.getByExamKey(examKey);
        if (detailOpt.isEmpty()) {
            return Optional.empty();
        }

        ExamSyllabusMasterDto syllabus = detailOpt.get().getSyllabus();
        Map<String, SubjectTopicNode> subjectTopicIndex = buildSubjectTopicIndex(syllabus);
        Optional<Exam> matchedExam = resolveQuestionExam(syllabus);
        Long matchedExamId = matchedExam.map(Exam::getId).orElse(null);

        AdminSyllabusQuestionCoverageDto out = new AdminSyllabusQuestionCoverageDto();
        out.setExamCode(trimOrNull(syllabus.getExamCode()));
        out.setExamName(trimOrNull(syllabus.getExamName()));
        out.setMatchedQuestionExamId(matchedExamId);
        out.setMatchedQuestionExamName(matchedExam.map(Exam::getName).orElse(null));
        out.setExamMappedToQuestionBank(matchedExamId != null);

        List<AdminSyllabusQuestionCoverageDto.SubjectCoverageDto> subjects = new ArrayList<>();
        for (SubjectTopicNode node : subjectTopicIndex.values()) {
            AdminSyllabusQuestionCoverageDto.SubjectCoverageDto subjectDto =
                    new AdminSyllabusQuestionCoverageDto.SubjectCoverageDto();
            subjectDto.setSubjectName(node.subjectName);
            subjectDto.setAvailableQuestionsInExam(
                    matchedExamId == null ? 0L : questionRepository.countBySubjectAndExam(node.subjectName, matchedExamId)
            );
            subjectDto.setAvailableQuestionsOverall(questionRepository.countBySubjectAndExam(node.subjectName, null));

            List<AdminSyllabusQuestionCoverageDto.TopicCoverageDto> topics = new ArrayList<>();
            for (String topic : node.topics) {
                AdminSyllabusQuestionCoverageDto.TopicCoverageDto topicDto =
                        new AdminSyllabusQuestionCoverageDto.TopicCoverageDto();
                topicDto.setTopicName(topic);
                topicDto.setAvailableQuestionsInExam(
                        matchedExamId == null
                                ? 0L
                                : questionRepository.countBySubjectAndTopicAndExamId(node.subjectName, topic, matchedExamId)
                );
                topicDto.setAvailableQuestionsOverall(questionRepository.countBySubjectAndTopic(node.subjectName, topic));
                topics.add(topicDto);
            }
            subjectDto.setTopics(topics);
            subjects.add(subjectDto);
        }
        out.setSubjects(subjects);
        return Optional.of(out);
    }

    public Optional<AdminQuestionGenerationPreviewDto> generatePreviewByExamKey(
            String examKey,
            AdminQuestionGenerationRequestDto request) {
        Optional<ExamSyllabusDetailDto> detailOpt = examSyllabusService.getByExamKey(examKey);
        if (detailOpt.isEmpty()) {
            return Optional.empty();
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        ExamSyllabusMasterDto syllabus = detailOpt.get().getSyllabus();
        Map<String, SubjectTopicNode> subjectTopicIndex = buildSubjectTopicIndex(syllabus);
        String requestedSubject = trimOrNull(request.getSubject());
        String requestedTopic = trimOrNull(request.getTopic());
        String requestedSubTopic = trimOrNull(request.getSubTopic());
        if (requestedSubject == null) {
            throw new IllegalArgumentException("subject is required");
        }
        if (requestedTopic == null) {
            throw new IllegalArgumentException("topic is required");
        }

        SubjectTopicNode subjectNode = subjectTopicIndex.get(normalizeLookupKey(requestedSubject));
        if (subjectNode == null) {
            throw new IllegalArgumentException("subject is not part of this exam syllabus");
        }
        String matchedTopic = findMatchedTopic(subjectNode.topics, requestedTopic);
        if (matchedTopic == null) {
            throw new IllegalArgumentException("topic is not part of this exam syllabus subject");
        }

        DifficultyMix mix = resolveDifficultyMix(request);
        Language language = resolveLanguage(request.getLanguage());
        Integer marks = request.getMarks() == null ? 1 : request.getMarks();
        Double negativeMarks = request.getNegativeMarks() == null ? 0.0 : request.getNegativeMarks();
        Boolean isPremium = request.getIsPremium() == null ? Boolean.FALSE : request.getIsPremium();
        String createdBy = trimOrNull(request.getCreatedBy());
        if (createdBy == null) {
            createdBy = "OPENAI_ADMIN_PREVIEW";
        }
        String qualityHint = trimOrNull(request.getQualityHint());

        Optional<Exam> matchedExam = resolveQuestionExam(syllabus);
        Long matchedExamId = matchedExam.map(Exam::getId).orElse(null);
        String examNameForQuestions = firstNonBlank(
                trimOrNull(syllabus.getExamName()),
                trimOrNull(syllabus.getExamCode()),
                matchedExam.map(Exam::getName).orElse(null)
        );
        String examCodeForQuestions = trimOrNull(syllabus.getExamCode());
        String examTag = toExamTag(examCodeForQuestions, examNameForQuestions);
        String examBindingName = matchedExam.map(Exam::getName)
                .orElse(firstNonBlank(examTag, examNameForQuestions));

        List<String> existingTexts = questionRepository.findQuestionTextsBySubjectAndTopic(subjectNode.subjectName, matchedTopic);
        long existingInExam = matchedExamId == null
                ? 0L
                : questionRepository.countBySubjectAndTopicAndExamId(subjectNode.subjectName, matchedTopic, matchedExamId);
        long existingOverall = questionRepository.countBySubjectAndTopic(subjectNode.subjectName, matchedTopic);

        LinkedHashSet<String> existingNormalized = new LinkedHashSet<>();
        List<String> existingPromptQuestions = new ArrayList<>();
        for (String text : existingTexts) {
            String cleanText = trimOrNull(text);
            if (cleanText != null) {
                existingPromptQuestions.add(cleanText);
            }
            String normalized = normalizeQuestion(text);
            if (!normalized.isBlank()) {
                existingNormalized.add(normalized);
            }
        }

        List<QuestionDto> generated = new ArrayList<>();
        LinkedHashSet<String> generatedNormalized = new LinkedHashSet<>();
        List<String> generatedPromptQuestions = new ArrayList<>();
        GenerationStats stats = new GenerationStats();
        int sequence = 1;

        sequence = generateForDifficulty(generated, generatedNormalized, existingNormalized,
                existingPromptQuestions, generatedPromptQuestions, stats,
                examNameForQuestions, subjectNode.subjectName, matchedTopic,
                requestedSubTopic, examTag, examBindingName,
                "Easy", "EASY", mix.easy, language, marks, negativeMarks, isPremium, createdBy, qualityHint, sequence);
        sequence = generateForDifficulty(generated, generatedNormalized, existingNormalized,
                existingPromptQuestions, generatedPromptQuestions, stats,
                examNameForQuestions, subjectNode.subjectName, matchedTopic,
                requestedSubTopic, examTag, examBindingName,
                "Medium", "MEDIUM", mix.medium, language, marks, negativeMarks, isPremium, createdBy, qualityHint, sequence);
        generateForDifficulty(generated, generatedNormalized, existingNormalized,
                existingPromptQuestions, generatedPromptQuestions, stats,
                examNameForQuestions, subjectNode.subjectName, matchedTopic,
                requestedSubTopic, examTag, examBindingName,
                "Hard", "HARD", mix.hard, language, marks, negativeMarks, isPremium, createdBy, qualityHint, sequence);

        AdminQuestionGenerationPreviewDto out = new AdminQuestionGenerationPreviewDto();
        out.setExamCode(trimOrNull(syllabus.getExamCode()));
        out.setExamName(trimOrNull(syllabus.getExamName()));
        out.setMatchedQuestionExamId(matchedExamId);
        out.setMatchedQuestionExamName(matchedExam.map(Exam::getName).orElse(null));
        out.setSubject(subjectNode.subjectName);
        out.setTopic(matchedTopic);
        out.setRequestedTotal(mix.total);
        out.setRequestedEasy(mix.easy);
        out.setRequestedMedium(mix.medium);
        out.setRequestedHard(mix.hard);
        out.setExistingQuestionCountInExam(existingInExam);
        out.setExistingQuestionCountOverall(existingOverall);
        out.setSkippedExistingDuplicates(stats.skippedExistingDuplicates);
        out.setSkippedGeneratedDuplicates(stats.skippedGeneratedDuplicates);
        out.setGeneratedCount(generated.size());
        out.setComplete(generated.size() == mix.total);
        if (out.isComplete()) {
            out.setMessage("Preview generated successfully. Questions are not saved to DB.");
        } else {
            String errorSuffix = stats.openAiFailures > 0
                    ? " OpenAI call failures during generation: " + stats.openAiFailures + "."
                    : "";
            out.setMessage("Preview partially generated (" + generated.size() + "/" + mix.total
                    + "). Questions are not saved to DB. Retry to generate remaining questions." + errorSuffix);
        }
        out.setQuestions(generated);
        return Optional.of(out);
    }

    public Optional<AdminSyllabusBatchGenerationResponseDto> generateAndSaveAllByExamKey(
            String examKey,
            AdminSyllabusBatchGenerationRequestDto request) {
        try {
        Optional<ExamSyllabusDetailDto> detailOpt = examSyllabusService.getByExamKey(examKey);
        if (detailOpt.isEmpty()) {
            return Optional.empty();
        }

        ExamSyllabusMasterDto syllabus = detailOpt.get().getSyllabus();
        Map<String, SubjectTopicNode> subjectTopicIndex = buildSubjectTopicIndex(syllabus);
        if (subjectTopicIndex.isEmpty()) {
            throw new IllegalArgumentException("No subjects/topics found in this syllabus");
        }

        AdminSyllabusBatchGenerationRequestDto effective =
                request == null ? new AdminSyllabusBatchGenerationRequestDto() : request;
        Set<String> requestedSubjectKeys = new LinkedHashSet<>();
        if (effective.getSubjects() != null) {
            for (String subject : effective.getSubjects()) {
                String normalized = normalizeLookupKey(subject);
                if (!normalized.isBlank()) {
                    requestedSubjectKeys.add(normalized);
                }
            }
        }

        AdminQuestionGenerationRequestDto template = new AdminQuestionGenerationRequestDto();
        template.setTotalQuestions(effective.getTotalQuestionsPerTopic());
        template.setEasyCount(effective.getEasyCount());
        template.setMediumCount(effective.getMediumCount());
        template.setHardCount(effective.getHardCount());
        template.setLanguage(effective.getLanguage());
        template.setMarks(effective.getMarks());
        template.setNegativeMarks(effective.getNegativeMarks());
        template.setIsPremium(effective.getIsPremium());
        template.setCreatedBy(effective.getCreatedBy());

        DifficultyMix mix = resolveDifficultyMix(template);
        Language language = resolveLanguage(template.getLanguage());
        Integer marks = template.getMarks() == null ? 1 : template.getMarks();
        Double negativeMarks = template.getNegativeMarks() == null ? 0.0 : template.getNegativeMarks();
        Boolean isPremium = template.getIsPremium() == null ? Boolean.FALSE : template.getIsPremium();
        String createdBy = trimOrNull(template.getCreatedBy());
        if (createdBy == null) {
            createdBy = "OPENAI_ADMIN_BATCH";
        }

        boolean autoGenerateTestSeries = effective.getAutoGenerateTestSeries() == null
                || Boolean.TRUE.equals(effective.getAutoGenerateTestSeries());
        boolean stopOnFirstFailure = Boolean.TRUE.equals(effective.getStopOnFirstFailure());

        Optional<Exam> matchedExam = resolveQuestionExam(syllabus);

        AdminSyllabusBatchGenerationResponseDto out = new AdminSyllabusBatchGenerationResponseDto();
        out.setExamCode(trimOrNull(syllabus.getExamCode()));
        out.setExamName(trimOrNull(syllabus.getExamName()));
        out.setMatchedQuestionExamId(matchedExam.map(Exam::getId).orElse(null));
        out.setMatchedQuestionExamName(matchedExam.map(Exam::getName).orElse(null));
        out.setRequestedQuestionsPerTopic(mix.total);
        out.setAutoGenerateTestSeriesRequested(autoGenerateTestSeries);

        int requestedTopicsCount = 0;
        for (SubjectTopicNode node : subjectTopicIndex.values()) {
            if (!requestedSubjectKeys.isEmpty()
                    && !requestedSubjectKeys.contains(normalizeLookupKey(node.subjectName))) {
                continue;
            }
            requestedTopicsCount += node.topics.size();
        }
        if (requestedTopicsCount == 0) {
            throw new IllegalArgumentException("No matching syllabus subjects/topics found for batch generation");
        }

        int processedTopicsCount = 0;
        int successfulTopicsCount = 0;
        int failedTopicsCount = 0;
        int totalQuestionsGenerated = 0;
        int totalQuestionsRequested = 0;
        int totalQuestionsPersistedCreated = 0;
        int totalQuestionsPersistedUpdated = 0;
        int totalSkippedRunDuplicates = 0;
        int topicsSkippedAsAlreadySufficient = 0;
        List<String> failedTopicErrors = new ArrayList<>();
        String testSeriesFailure = null;

        LinkedHashSet<String> runNormalized = new LinkedHashSet<>();
        List<AdminSyllabusBatchGenerationResponseDto.TopicGenerationResultDto> topicResults = new ArrayList<>();
        List<Map<String, Object>> artifactTopicPayloads = new ArrayList<>();

        boolean stopRequested = false;
        outer:
        for (SubjectTopicNode node : subjectTopicIndex.values()) {
            if (!requestedSubjectKeys.isEmpty()
                    && !requestedSubjectKeys.contains(normalizeLookupKey(node.subjectName))) {
                continue;
            }

            for (String topic : node.topics) {
                processedTopicsCount++;

                AdminSyllabusBatchGenerationResponseDto.TopicGenerationResultDto topicResult =
                        new AdminSyllabusBatchGenerationResponseDto.TopicGenerationResultDto();
                topicResult.setSubject(node.subjectName);
                topicResult.setTopic(topic);
                long existingCount = countExistingQuestionsForTopic(
                        node.subjectName,
                        topic,
                        matchedExam.map(Exam::getId).orElse(null)
                );
                int remainingNeeded = Math.max(0, mix.total - (int) Math.min(Integer.MAX_VALUE, existingCount));
                topicResult.setRequestedTotal(remainingNeeded);
                totalQuestionsRequested += remainingNeeded;

                if (remainingNeeded <= 0) {
                    topicsSkippedAsAlreadySufficient++;
                    topicResult.setGeneratedCount(0);
                    topicResult.setPersistedCreated(0);
                    topicResult.setPersistedUpdated(0);
                    topicResult.setPersistedTotal(0);
                    topicResult.setSkippedExistingDuplicates(0);
                    topicResult.setSkippedGeneratedDuplicates(0);
                    topicResult.setSkippedRunDuplicates(0);
                    topicResult.setComplete(true);
                    topicResult.setSuccess(true);
                    topicResult.setMessage("Skipped: topic already has >= " + mix.total + " questions in question bank.");
                    topicResults.add(topicResult);
                    artifactTopicPayloads.add(buildTopicArtifactPayload(node.subjectName, topic, topicResult, List.of(), List.of()));
                    successfulTopicsCount++;
                    continue;
                }

                try {
                    int targetForTopic = remainingNeeded;
                    int needed = remainingNeeded;
                    int roundsUsed = 0;
                    int skippedExistingDuplicates = 0;
                    int skippedGeneratedDuplicates = 0;
                    int skippedRunDuplicates = 0;
                    int validationRejected = 0;
                    LinkedHashSet<String> topicNormalized = new LinkedHashSet<>();
                    List<QuestionDto> acceptedForPersistence = new ArrayList<>();
                    List<String> qualityHints = new ArrayList<>();

                    while (needed > 0 && roundsUsed < MAX_AUTO_REFINE_ROUNDS) {
                        roundsUsed++;
                        DifficultyMix remainingMix = scaleDifficultyMix(mix, needed);
                        AdminQuestionGenerationRequestDto topicRequest = new AdminQuestionGenerationRequestDto();
                        topicRequest.setSubject(node.subjectName);
                        topicRequest.setTopic(topic);
                        topicRequest.setSubTopic(topic);
                        topicRequest.setTotalQuestions(remainingMix.total);
                        topicRequest.setEasyCount(remainingMix.easy);
                        topicRequest.setMediumCount(remainingMix.medium);
                        topicRequest.setHardCount(remainingMix.hard);
                        topicRequest.setLanguage(language.name());
                        topicRequest.setMarks(marks);
                        topicRequest.setNegativeMarks(negativeMarks);
                        topicRequest.setIsPremium(isPremium);
                        topicRequest.setCreatedBy(createdBy);
                        if (!qualityHints.isEmpty()) {
                            topicRequest.setQualityHint(String.join(" | ", qualityHints));
                        }

                        AdminQuestionGenerationPreviewDto preview = generatePreviewByExamKey(examKey, topicRequest)
                                .orElseThrow(() -> new IllegalArgumentException("Syllabus not found for exam key"));
                        skippedExistingDuplicates += defaultInt(preview.getSkippedExistingDuplicates());
                        skippedGeneratedDuplicates += defaultInt(preview.getSkippedGeneratedDuplicates());

                        ValidationResult validation = validateGeneratedQuestions(preview.getQuestions(), topicNormalized);
                        validationRejected += validation.rejectedCount;

                        int roundRunDuplicates = 0;
                        for (QuestionDto generated : validation.validQuestions) {
                            String normalized = normalizeQuestion(generated.getQuestionText());
                            if (normalized.isBlank()) {
                                continue;
                            }
                            if (!runNormalized.add(normalized)) {
                                roundRunDuplicates++;
                                continue;
                            }
                            acceptedForPersistence.add(generated);
                            topicNormalized.add(normalized);
                        }
                        skippedRunDuplicates += roundRunDuplicates;

                        needed = Math.max(0, targetForTopic - acceptedForPersistence.size());
                        String nextHint = buildRefinementHint(validation.issueCounts, needed);
                        if (nextHint != null) {
                            qualityHints.add(nextHint);
                        }
                        if (needed <= 0) {
                            break;
                        }
                    }

                    QuestionBulkUpsertResult upsertResult = null;
                    if (!acceptedForPersistence.isEmpty()) {
                        upsertResult = questionService.bulkUpsert(acceptedForPersistence);
                    }

                    int created = upsertResult == null ? 0 : upsertResult.getCreated();
                    int updated = upsertResult == null ? 0 : upsertResult.getUpdated();

                    totalQuestionsGenerated += acceptedForPersistence.size();
                    totalQuestionsPersistedCreated += created;
                    totalQuestionsPersistedUpdated += updated;
                    totalSkippedRunDuplicates += skippedRunDuplicates;

                    topicResult.setGeneratedCount(acceptedForPersistence.size());
                    topicResult.setPersistedCreated(created);
                    topicResult.setPersistedUpdated(updated);
                    topicResult.setPersistedTotal(created + updated);
                    topicResult.setSkippedExistingDuplicates(skippedExistingDuplicates);
                    topicResult.setSkippedGeneratedDuplicates(skippedGeneratedDuplicates + validationRejected);
                    topicResult.setSkippedRunDuplicates(skippedRunDuplicates);
                    topicResult.setComplete(acceptedForPersistence.size() >= targetForTopic);
                    topicResult.setSuccess(topicResult.isComplete());
                    if (topicResult.isComplete()) {
                        topicResult.setMessage("Auto generation completed in " + roundsUsed
                                + " round(s) with validation and refine checks.");
                        successfulTopicsCount++;
                    } else {
                        topicResult.setMessage("Auto generation partial: generated " + acceptedForPersistence.size()
                                + "/" + targetForTopic + " after " + roundsUsed + " round(s).");
                        topicResult.setError("Unable to reach target after automated refine rounds");
                        failedTopicsCount++;
                        failedTopicErrors.add(node.subjectName + " :: " + topic
                                + " -> Unable to reach target after refine rounds");
                        if (stopOnFirstFailure) {
                            stopRequested = true;
                        }
                    }
                    artifactTopicPayloads.add(buildTopicArtifactPayload(
                            node.subjectName,
                            topic,
                            topicResult,
                            acceptedForPersistence,
                            qualityHints
                    ));
                } catch (Exception ex) {
                    failedTopicsCount++;
                    topicResult.setGeneratedCount(0);
                    topicResult.setPersistedCreated(0);
                    topicResult.setPersistedUpdated(0);
                    topicResult.setPersistedTotal(0);
                    topicResult.setSkippedExistingDuplicates(0);
                    topicResult.setSkippedGeneratedDuplicates(0);
                    topicResult.setSkippedRunDuplicates(0);
                    topicResult.setComplete(false);
                    topicResult.setSuccess(false);
                    topicResult.setMessage("Generation failed for this topic.");
                    topicResult.setError(ex.getMessage());
                    failedTopicErrors.add(node.subjectName + " :: " + topic + " -> " + safeErrorMessage(ex));
                    artifactTopicPayloads.add(buildTopicArtifactPayload(
                            node.subjectName,
                            topic,
                            topicResult,
                            List.of(),
                            List.of(safeErrorMessage(ex))
                    ));
                    log.warn("Batch generation failed for subject='{}', topic='{}': {}",
                            node.subjectName, topic, ex.getMessage());
                    if (stopOnFirstFailure) {
                        stopRequested = true;
                    }
                }

                topicResults.add(topicResult);
                if (stopRequested) {
                    break outer;
                }
            }
        }

        out.setRequestedTopicsCount(requestedTopicsCount);
        out.setProcessedTopicsCount(processedTopicsCount);
        out.setSuccessfulTopicsCount(successfulTopicsCount);
        out.setFailedTopicsCount(failedTopicsCount);
        out.setTotalQuestionsRequested(totalQuestionsRequested);
        out.setTotalQuestionsGenerated(totalQuestionsGenerated);
        out.setTotalQuestionsPersistedCreated(totalQuestionsPersistedCreated);
        out.setTotalQuestionsPersistedUpdated(totalQuestionsPersistedUpdated);
        out.setTotalQuestionsPersisted(totalQuestionsPersistedCreated + totalQuestionsPersistedUpdated);
        out.setTotalSkippedRunDuplicates(totalSkippedRunDuplicates);
        out.setTopicResults(topicResults);

        try {
            Map<String, Object> artifact = new LinkedHashMap<>();
            artifact.put("examKey", safeExamKey(examKey));
            artifact.put("examCode", out.getExamCode());
            artifact.put("examName", out.getExamName());
            artifact.put("requestedTopicsCount", out.getRequestedTopicsCount());
            artifact.put("processedTopicsCount", out.getProcessedTopicsCount());
            artifact.put("successfulTopicsCount", out.getSuccessfulTopicsCount());
            artifact.put("failedTopicsCount", out.getFailedTopicsCount());
            artifact.put("requestedQuestionsPerTopic", out.getRequestedQuestionsPerTopic());
            artifact.put("totalQuestionsRequested", out.getTotalQuestionsRequested());
            artifact.put("totalQuestionsGenerated", out.getTotalQuestionsGenerated());
            artifact.put("totalQuestionsPersistedCreated", out.getTotalQuestionsPersistedCreated());
            artifact.put("totalQuestionsPersistedUpdated", out.getTotalQuestionsPersistedUpdated());
            artifact.put("totalQuestionsPersisted", out.getTotalQuestionsPersisted());
            artifact.put("totalSkippedRunDuplicates", out.getTotalSkippedRunDuplicates());
            artifact.put("topicResults", out.getTopicResults());
            artifact.put("topics", artifactTopicPayloads);
            String artifactId = mcqGenerationArtifactService.saveArtifact(examKey, artifact);
            out.setArtifactId(artifactId);
            out.setArtifactDownloadUrl("/api/syllabus/admin/question-bank/artifacts/" + artifactId);
        } catch (Exception artifactEx) {
            log.warn("Failed to persist MCQ generation artifact for examKey='{}': {}", examKey, artifactEx.getMessage());
        }

        if (autoGenerateTestSeries) {
            try {
                Optional<ExamTestSeriesGenerateResponseDto> testSeriesOpt =
                        examTestSeriesService.generateAllPossibleByExamKey(examKey);
                if (testSeriesOpt.isPresent()) {
                    out.setTestSeriesGenerated(true);
                    out.setTestSeriesGeneration(testSeriesOpt.get());
                    out.setTestSeriesMessage(testSeriesOpt.get().getMessage());
                } else {
                    out.setTestSeriesGenerated(false);
                    out.setTestSeriesMessage("Test series generation skipped: syllabus not found for exam key.");
                }
            } catch (Exception ex) {
                out.setTestSeriesGenerated(false);
                out.setTestSeriesMessage("Test series generation failed: " + ex.getMessage());
                testSeriesFailure = safeErrorMessage(ex);
                log.warn("Auto test-series generation failed for examKey='{}': {}", examKey, ex.getMessage());
            }
        }

        if (failedTopicsCount == 0 && !stopRequested) {
            out.setMessage("Batch generation completed for all requested topics. Skipped topics already at target: "
                    + topicsSkippedAsAlreadySufficient + ".");
        } else if (stopRequested) {
            out.setMessage("Batch generation stopped due to failure because stopOnFirstFailure=true. Skipped topics already at target: "
                    + topicsSkippedAsAlreadySufficient + ".");
        } else {
            out.setMessage("Batch generation completed with partial failures. Skipped topics already at target: "
                    + topicsSkippedAsAlreadySufficient + ".");
        }

        if (failedTopicsCount > 0 || stopRequested || testSeriesFailure != null) {
            adminAlertService.sendFailureAlert(
                    "MCQ Batch Generation Alert [" + safeExamKey(examKey) + "]",
                    buildBatchFailureAlertBody(examKey, out, failedTopicErrors, stopRequested, testSeriesFailure)
            );
        }
        return Optional.of(out);
        } catch (RuntimeException ex) {
            adminAlertService.sendFailureAlert(
                    "MCQ Batch Fatal Failure [" + safeExamKey(examKey) + "]",
                    "Exam Key: " + safeExamKey(examKey) + "\n"
                            + "Failure: " + safeErrorMessage(ex)
            );
            throw ex;
        }
    }

    private int generateForDifficulty(List<QuestionDto> output,
                                      Set<String> generatedNormalized,
                                      Set<String> existingNormalized,
                                      List<String> existingPromptQuestions,
                                      List<String> generatedPromptQuestions,
                                      GenerationStats stats,
                                      String examName,
                                      String subject,
                                      String topic,
                                      String subTopic,
                                      String examTag,
                                      String examBindingName,
                                      String promptDifficulty,
                                      String difficultyValue,
                                      int targetCount,
                                      Language language,
                                      Integer marks,
                                      Double negativeMarks,
                                      Boolean isPremium,
                                      String createdBy,
                                      String qualityHint,
                                      int sequenceStart) {
        if (targetCount <= 0) {
            return sequenceStart;
        }

        int collected = 0;
        int attempts = 0;
        int sequence = sequenceStart;
        while (collected < targetCount && attempts < MAX_ATTEMPTS_PER_DIFFICULTY) {
            int remaining = targetCount - collected;
            int requestCount = Math.min(remaining, MAX_QUESTIONS_PER_AI_CALL);
            List<String> avoidList = buildAvoidPromptQuestions(existingPromptQuestions, generatedPromptQuestions);
            boolean allowPremiumFallback = attempts >= MINI_ONLY_ATTEMPTS_PER_DIFFICULTY;
            String aiJson;
            try {
                aiJson = openAIService.generateExamSubjectTopicQuestions(
                        examName,
                        subject,
                        topic,
                        requestCount,
                        promptDifficulty,
                        language.name(),
                        avoidList,
                        allowPremiumFallback,
                        qualityHint
                );
            } catch (Exception ex) {
                stats.openAiFailures++;
                log.warn("OpenAI generation failed for {} difficulty on attempt {}: {}",
                        promptDifficulty, attempts + 1, ex.getMessage());
                attempts++;
                continue;
            }
            log.info("OpenAI cleaned response for {} difficulty (length={}): {}",
                    promptDifficulty,
                    aiJson == null ? 0 : aiJson.length(),
                    truncateForLog(aiJson, 4000));

            List<AiQuestionItem> parsed;
            try {
                parsed = parseAiQuestions(aiJson);
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping malformed OpenAI batch for {} difficulty on attempt {}: {}",
                        promptDifficulty, attempts + 1, ex.getMessage());
                attempts++;
                continue;
            }
            int before = collected;
            for (AiQuestionItem item : parsed) {
                if (collected >= targetCount) {
                    break;
                }
                String normalized = normalizeQuestion(item.question);
                if (normalized.isBlank()) {
                    continue;
                }
                if (existingNormalized.contains(normalized)) {
                    stats.skippedExistingDuplicates++;
                    continue;
                }
                if (!generatedNormalized.add(normalized)) {
                    stats.skippedGeneratedDuplicates++;
                    continue;
                }

                QuestionDto dto = toQuestionDto(
                        item,
                        sequence,
                        examName,
                        examTag,
                        examBindingName,
                        subject,
                        topic,
                        subTopic,
                        difficultyValue,
                        language,
                        marks,
                        negativeMarks,
                        isPremium,
                        createdBy
                );
                output.add(dto);
                generatedPromptQuestions.add(item.question);
                sequence++;
                collected++;
            }
            attempts++;

            if (collected == before) {
                continue;
            }
        }

        if (collected < targetCount) {
            log.warn("Could not complete {} difficulty generation. requested={}, generated={}",
                    promptDifficulty, targetCount, collected);
        }
        return sequence;
    }

    private List<AiQuestionItem> parseAiQuestions(String aiJson) {
        String usableJson = makeParsableJson(aiJson);
        List<AiQuestionItem> out = new ArrayList<>();
        if (usableJson == null || usableJson.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(usableJson);
            JsonNode questionsNode = root.isArray() ? root : root.path("questions");
            if (!questionsNode.isArray()) {
                return out;
            }

            for (JsonNode node : questionsNode) {
                if (!node.isObject()) {
                    continue;
                }
                String question = firstNonBlank(
                        node.path("question").asText(null),
                        node.path("question_text").asText(null),
                        node.path("questionText").asText(null)
                );
                if (question == null) {
                    continue;
                }
                question = question.trim();
                if (question.length() < MIN_QUESTION_LENGTH) {
                    continue;
                }
                String questionImageSvg = trimOrNull(firstNonBlank(
                        node.path("question_image_svg").asText(null),
                        node.path("questionImageSvg").asText(null),
                        node.path("questionImage").asText(null),
                        node.path("question_svg").asText(null),
                        node.path("questionSvg").asText(null)
                ));

                List<AiOptionItem> options = extractOptions(node);
                if (options.size() != 4) {
                    continue;
                }
                if (!hasValidOptionTexts(options)) {
                    continue;
                }

                String correctRaw = firstNonBlank(
                        node.path("correct_option").asText(null),
                        node.path("correctOption").asText(null),
                        node.path("answer").asText(null),
                        resolveCorrectOptionFromDto(node)
                );
                String correctOption = resolveCorrectOption(correctRaw, options);
                if (correctOption == null) {
                    continue;
                }

                String explanation = firstNonBlank(
                        node.path("explanation").asText(null),
                        node.path("explanation").path("text").asText(null),
                        node.path("details_description").asText(null),
                        node.path("detailsDescription").asText(null),
                        node.path("explanation_text").asText(null)
                );

                AiQuestionItem item = new AiQuestionItem();
                item.question = question;
                item.options = options;
                item.correctOption = correctOption;
                item.explanation = explanation == null ? "" : explanation.trim();
                if (!hasDetailedExplanation(item.explanation)) {
                    continue;
                }
                item.questionImageSvg = questionImageSvg;
                if (!isHindiLikely(item)) {
                    continue;
                }
                out.add(item);
            }
            return out;
        } catch (Exception ex) {
            log.error("Invalid JSON received from OpenAI after repair attempts. Original payload: {}",
                    truncateForLog(aiJson, 20000), ex);
            throw new IllegalArgumentException("Invalid JSON received from OpenAI");
        }
    }

    private String makeParsableJson(String aiJson) {
        if (aiJson == null || aiJson.isBlank()) {
            return aiJson;
        }
        String raw = aiJson.trim();
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception ignored) {
            // continue with repair strategies
        }

        String repaired = JsonRepairUtil.fixBrokenJson(raw);
        if (repaired != null) {
            try {
                objectMapper.readTree(repaired);
                log.info("OpenAI response repaired via JsonRepairUtil (originalLength={}, repairedLength={})",
                        raw.length(), repaired.length());
                return repaired;
            } catch (Exception ignored) {
                // continue with tail salvage
            }
        }

        String salvagedQuestionsObject = salvageTruncatedQuestionsObject(raw);
        if (salvagedQuestionsObject != null) {
            try {
                objectMapper.readTree(salvagedQuestionsObject);
                log.info("OpenAI response salvaged by rebuilding truncated questions object (originalLength={}, salvagedLength={})",
                        raw.length(), salvagedQuestionsObject.length());
                return salvagedQuestionsObject;
            } catch (Exception ignored) {
                // continue with array salvage
            }
        }

        String salvaged = salvageTruncatedArray(raw);
        if (salvaged != null) {
            try {
                objectMapper.readTree(salvaged);
                log.info("OpenAI response salvaged by trimming incomplete tail (originalLength={}, salvagedLength={})",
                        raw.length(), salvaged.length());
                return salvaged;
            } catch (Exception ignored) {
                // final fallback
            }
        }
        return raw;
    }

    private String salvageTruncatedQuestionsObject(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }

        int questionsKey = trimmed.indexOf("\"questions\"");
        if (questionsKey < 0) {
            return null;
        }
        int arrayStart = trimmed.indexOf('[', questionsKey);
        if (arrayStart < 0) {
            return null;
        }

        int lastObjectClose = findLastCompleteObjectEndInArray(trimmed, arrayStart);
        if (lastObjectClose < 0) {
            return null;
        }

        String questionsBody = trimmed.substring(arrayStart + 1, lastObjectClose + 1).trim();
        if (questionsBody.isBlank()) {
            return null;
        }
        return "{\"questions\":[" + questionsBody + "]}";
    }

    private String salvageTruncatedArray(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            return null;
        }

        int lastObjectClose = findLastCompleteObjectEndInArray(trimmed, 0);
        if (lastObjectClose < 1) {
            return null;
        }
        String arrayBody = trimmed.substring(1, lastObjectClose + 1).trim();
        if (arrayBody.isBlank()) {
            return null;
        }
        return "[" + arrayBody + "]";
    }

    private int findLastCompleteObjectEndInArray(String text, int arrayStart) {
        if (text == null || arrayStart < 0 || arrayStart >= text.length() || text.charAt(arrayStart) != '[') {
            return -1;
        }
        boolean inString = false;
        boolean escape = false;
        int objectDepth = 0;
        int lastObjectEnd = -1;

        for (int i = arrayStart + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                objectDepth++;
                continue;
            }
            if (ch == '}') {
                if (objectDepth > 0) {
                    objectDepth--;
                    if (objectDepth == 0) {
                        lastObjectEnd = i;
                    }
                }
                continue;
            }
            if (ch == ']' && objectDepth == 0) {
                break;
            }
        }
        return lastObjectEnd;
    }

    private List<AiOptionItem> extractOptions(JsonNode node) {
        List<AiOptionItem> out = new ArrayList<>();
        JsonNode optionsNode = node.get("options");
        if (optionsNode == null || optionsNode.isNull() || optionsNode.isMissingNode()) {
            optionsNode = node.get("choices");
        }
        if (optionsNode != null && optionsNode.isArray()) {
            JsonNode optionSvgs = node.path("option_image_svgs");
            for (int i = 0; i < optionsNode.size(); i++) {
                JsonNode opt = optionsNode.get(i);
                String text = null;
                String imageSvg = null;
                if (opt != null && opt.isObject()) {
                    text = trimOrNull(firstNonBlank(
                            opt.path("text").asText(null),
                            opt.path("option_text").asText(null),
                            opt.path("optionText").asText(null)
                    ));
                    imageSvg = trimOrNull(firstNonBlank(
                            opt.path("image_svg").asText(null),
                            opt.path("imageSvg").asText(null),
                            opt.path("image").asText(null),
                            opt.path("svg").asText(null)
                    ));
                } else if (opt != null) {
                    text = trimOrNull(opt.asText(null));
                }
                if (imageSvg == null && optionSvgs.isArray() && i < optionSvgs.size()) {
                    imageSvg = trimOrNull(optionSvgs.get(i).asText(null));
                }
                if (text != null) {
                    AiOptionItem option = new AiOptionItem();
                    option.text = text;
                    option.imageSvg = imageSvg;
                    out.add(option);
                }
            }
        } else if (optionsNode != null && optionsNode.isObject()) {
            List<String> keys = List.of(
                    "option_a", "option_b", "option_c", "option_d",
                    "A", "B", "C", "D",
                    "1", "2", "3", "4",
                    "option1", "option2", "option3", "option4"
            );
            for (String key : keys) {
                String text = trimOrNull(optionsNode.path(key).asText(null));
                if (text != null) {
                    AiOptionItem option = new AiOptionItem();
                    option.text = text;
                    option.imageSvg = null;
                    out.add(option);
                }
            }
        } else {
            List<String> keys = List.of(
                    "option_a", "option_b", "option_c", "option_d",
                    "optionA", "optionB", "optionC", "optionD"
            );
            for (String key : keys) {
                String text = trimOrNull(node.path(key).asText(null));
                if (text != null) {
                    AiOptionItem option = new AiOptionItem();
                    option.text = text;
                    option.imageSvg = null;
                    out.add(option);
                }
            }
        }

        Map<String, AiOptionItem> deduped = new LinkedHashMap<>();
        for (AiOptionItem option : out) {
            String normalized = normalizeQuestion(option.text);
            if (!normalized.isBlank()) {
                deduped.putIfAbsent(normalized, option);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String resolveCorrectOption(String correctRaw, List<AiOptionItem> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        String raw = trimOrNull(correctRaw);
        if (raw == null) {
            return null;
        }

        String numeric = raw.replaceAll("[^0-9]", "");
        if (!numeric.isBlank()) {
            try {
                int index = Integer.parseInt(numeric);
                if (index >= 1 && index <= options.size()) {
                    return options.get(index - 1).text;
                }
            } catch (NumberFormatException ignored) {
                // Continue text-based matching.
            }
        }

        String alpha = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if (!alpha.isBlank()) {
            char first = alpha.charAt(0);
            if (first >= 'A' && first <= 'D') {
                int index = (first - 'A') + 1;
                if (index >= 1 && index <= options.size()) {
                    return options.get(index - 1).text;
                }
            }
        }

        for (AiOptionItem option : options) {
            if (option.text != null && option.text.equalsIgnoreCase(raw)) {
                return option.text;
            }
        }
        return null;
    }

    private String resolveCorrectOptionFromDto(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode correctAnswer = node.path("correctAnswer");
        if (!correctAnswer.isObject()) {
            correctAnswer = node.path("correct_answer");
        }
        if (!correctAnswer.isObject()) {
            return null;
        }
        JsonNode choiceIds = correctAnswer.path("choiceIds");
        if (!choiceIds.isArray() || choiceIds.isEmpty()) {
            choiceIds = correctAnswer.path("choice_ids");
        }
        if (!choiceIds.isArray() || choiceIds.isEmpty()) {
            return null;
        }
        JsonNode first = choiceIds.get(0);
        if (first == null) {
            return null;
        }
        if (first.isNumber()) {
            return String.valueOf(first.intValue());
        }
        String raw = trimOrNull(first.asText(null));
        if (raw == null) {
            return null;
        }
        return raw;
    }

    private boolean hasValidOptionTexts(List<AiOptionItem> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        for (AiOptionItem option : options) {
            if (option == null) {
                return false;
            }
            String text = trimOrNull(option.text);
            if (text == null || text.length() < 1) {
                return false;
            }
        }
        return true;
    }

    private boolean isHindiLikely(AiQuestionItem item) {
        if (item == null) {
            return false;
        }
        if (!containsDevanagari(item.question) || !containsDevanagari(item.explanation)) {
            return false;
        }
        // Options can legitimately be numeric dates/years or proper nouns; do not over-filter here.
        return true;
    }

    private boolean containsDevanagari(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '\u0900' && ch <= '\u097F') {
                return true;
            }
        }
        return false;
    }

    private boolean hasDetailedExplanation(String explanation) {
        String value = trimOrNull(explanation);
        return value != null && value.length() >= MIN_EXPLANATION_LENGTH;
    }

    private QuestionDto toQuestionDto(AiQuestionItem item,
                                      int sequence,
                                      String examName,
                                      String examTag,
                                      String examBindingName,
                                      String subject,
                                      String topic,
                                      String subTopic,
                                      String difficulty,
                                      Language language,
                                      Integer marks,
                                      Double negativeMarks,
                                      Boolean isPremium,
                                      String createdBy) {
        QuestionDto dto = new QuestionDto();
        dto.setQuestionCode(generateUniqueQuestionCode(subject, topic, sequence));
        String examAssociation = firstNonBlank(examBindingName, examTag, examName);
        if (examAssociation != null && !examAssociation.isBlank()) {
            dto.setExams(List.of(examAssociation));
        }
        dto.setSubject(subject);
        dto.setTopic(topic);
        dto.setSubTopic(firstNonBlank(subTopic, topic));
        dto.setQuestionText(item.question);
        dto.setQuestionImage(item.questionImageSvg);
        dto.setType(QuestionType.MCQ_SINGLE.name());
        dto.setDifficulty(difficulty);
        dto.setLanguage(language.name());
        dto.setMarks(marks);
        dto.setNegativeMarks(negativeMarks);
        dto.setStatus(QuestionStatus.ACTIVE.name());
        dto.setIsPremium(isPremium);
        dto.setCreatedBy(createdBy);

        List<QuestionChoiceDto> choices = new ArrayList<>();
        long correctChoicePosition = -1L;
        for (int i = 0; i < item.options.size(); i++) {
            AiOptionItem optionItem = item.options.get(i);
            QuestionChoiceDto choice = new QuestionChoiceDto();
            choice.setId((long) (i + 1));
            choice.setText(optionItem.text);
            choice.setImage(optionItem.imageSvg);
            choices.add(choice);
            if (optionItem.text != null && optionItem.text.equals(item.correctOption)) {
                correctChoicePosition = i + 1L;
            }
        }
        dto.setChoices(choices);

        CorrectAnswerDto correct = new CorrectAnswerDto();
        if (correctChoicePosition > 0) {
            correct.setChoiceIds(List.of(correctChoicePosition));
        }
        correct.setTextAnswer(null);
        dto.setCorrectAnswer(correct);

        ExplanationDto explanation = new ExplanationDto();
        explanation.setImage(null);
        explanation.setVideoUrl(null);
        explanation.setText(item.explanation == null ? null : item.explanation);
        dto.setExplanation(explanation);
        dto.setExplanationText(null);

        AttemptStatsDto stats = new AttemptStatsDto();
        stats.setTotalAttempts(0L);
        stats.setCorrectAttempts(0L);
        stats.setAccuracy(0.0);
        dto.setAttemptStats(stats);

        SourceDto source = new SourceDto();
        source.setType(null);
        source.setYear(null);
        source.setShift(null);
        dto.setSource(source);

        List<String> tags = new ArrayList<>();
        if (examTag != null && !examTag.isBlank()) {
            tags.add(examTag);
        } else if (examAssociation != null && !examAssociation.isBlank()) {
            tags.add(examAssociation);
        }
        tags.add(subject);
        tags.add(topic);
        tags.add(difficulty);
        dto.setTags(tags);
        LocalDateTime now = LocalDateTime.now();
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);
        return dto;
    }

    private String generateUniqueQuestionCode(String subject, String topic, int sequence) {
        String subjectCode = toAcronym(subject, 3);
        String topicCode = toAcronym(topic, 3);
        String prefix = subjectCode + "-" + topicCode + "-";
        int serial = Math.max(1, sequence);
        int guard = 0;
        while (guard < 5000) {
            String candidate = prefix + String.format("%03d", serial);
            if (!questionRepository.existsByQuestionCode(candidate)) {
                return candidate;
            }
            serial++;
            guard++;
        }
        String fallback = LocalDateTime.now().format(CODE_TS);
        return prefix + fallback;
    }

    private Map<String, SubjectTopicNode> buildSubjectTopicIndex(ExamSyllabusMasterDto syllabus) {
        Map<String, SubjectTopicNode> index = new LinkedHashMap<>();
        if (syllabus == null || syllabus.getPapers() == null) {
            return index;
        }

        for (ExamSyllabusMasterDto.PaperDto paper : syllabus.getPapers()) {
            if (paper == null || paper.getSubjects() == null) {
                continue;
            }
            for (ExamSyllabusMasterDto.SubjectDto subject : paper.getSubjects()) {
                if (subject == null) {
                    continue;
                }
                String subjectName = trimOrNull(subject.getSubjectName());
                if (subjectName == null) {
                    continue;
                }

                String key = normalizeLookupKey(subjectName);
                SubjectTopicNode node = index.computeIfAbsent(key, k -> new SubjectTopicNode(subjectName));
                List<String> topics = normalizeTopics(subject.getTopics());
                if (topics.isEmpty()) {
                    topics = normalizeTopics(subject.getOptions());
                }
                node.topics.addAll(topics);
            }
        }
        return index;
    }

    private Optional<Exam> resolveQuestionExam(ExamSyllabusMasterDto syllabus) {
        if (syllabus == null) {
            return Optional.empty();
        }
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, syllabus.getExamName());
        addCandidate(candidates, syllabus.getExamCode());

        for (String candidate : candidates) {
            Optional<Exam> matched = examRepository.findByNameIgnoreCase(candidate);
            if (matched.isPresent()) {
                return matched;
            }
            if (candidate.contains("_")) {
                String spaced = candidate.replace('_', ' ').replaceAll("\\s+", " ").trim();
                if (!spaced.isBlank()) {
                    matched = examRepository.findByNameIgnoreCase(spaced);
                    if (matched.isPresent()) {
                        return matched;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void addCandidate(List<String> candidates, String value) {
        String normalized = trimOrNull(value);
        if (normalized == null) {
            return;
        }
        for (String existing : candidates) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        candidates.add(normalized);
    }

    private List<String> normalizeTopics(Object rawTopics) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectTopicTokens(rawTopics, out);
        return new ArrayList<>(out);
    }

    private void collectTopicTokens(Object raw, Set<String> out) {
        if (raw == null) {
            return;
        }
        if (raw instanceof String text) {
            splitTopicText(text, out);
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectTopicTokens(item, out);
            }
            return;
        }
        if (raw instanceof Map<?, ?> mapValue) {
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    String keyText = cleanTopicToken(String.valueOf(entry.getKey()));
                    if (keyText != null && !isGenericTopicKey(keyText)) {
                        out.add(keyText);
                    }
                }
                collectTopicTokens(entry.getValue(), out);
            }
            return;
        }
        if (raw.getClass().isArray() && raw instanceof Object[] array) {
            for (Object item : array) {
                collectTopicTokens(item, out);
            }
        }
    }

    private void splitTopicText(String source, Set<String> out) {
        if (source == null || source.isBlank()) {
            return;
        }
        String normalized = source
                .replace('\r', '\n')
                .replace("â€¢", "\n")
                .replace("â—", "\n")
                .replace("â–ª", "\n")
                .replace("â€“", "-");
        String[] parts = normalized.split("[\\n,;|]+");
        for (String part : parts) {
            String cleaned = cleanTopicToken(part);
            if (cleaned != null && !isGenericTopicKey(cleaned)) {
                out.add(cleaned);
            }
        }
    }

    private String cleanTopicToken(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isBlank()) {
            return null;
        }
        value = value.replaceAll("^\\d+[\\).:-]?\\s*", "");
        value = value.replaceAll("^[-*]+\\s*", "");
        value = value.replaceAll("\\s+", " ").trim();
        value = value.replaceAll(":$", "").trim();
        return value.length() < 2 ? null : value;
    }

    private boolean isGenericTopicKey(String token) {
        String key = normalizeLookupKey(token);
        return key.equals("topic")
                || key.equals("topics")
                || key.equals("section")
                || key.equals("sections")
                || key.equals("syllabus")
                || key.equals("notes")
                || key.equals("weightage");
    }

    private String findMatchedTopic(Set<String> topics, String requestedTopic) {
        String requestedKey = normalizeLookupKey(requestedTopic);
        for (String topic : topics) {
            if (normalizeLookupKey(topic).equals(requestedKey)) {
                return topic;
            }
        }
        return null;
    }

    private DifficultyMix resolveDifficultyMix(AdminQuestionGenerationRequestDto request) {
        int total = request.getTotalQuestions() == null ? DEFAULT_TOTAL : request.getTotalQuestions();
        if (total <= 0) {
            throw new IllegalArgumentException("totalQuestions must be greater than 0");
        }

        Integer easy = request.getEasyCount();
        Integer medium = request.getMediumCount();
        Integer hard = request.getHardCount();
        if (easy == null && medium == null && hard == null) {
            if (total == DEFAULT_TOTAL) {
                easy = DEFAULT_EASY;
                medium = DEFAULT_MEDIUM;
                hard = DEFAULT_HARD;
            } else {
                easy = (int) Math.floor(total * 0.20);
                medium = (int) Math.floor(total * 0.40);
                hard = total - easy - medium;
            }
        } else if (easy == null || medium == null || hard == null) {
            throw new IllegalArgumentException("easyCount, mediumCount, and hardCount must all be provided together");
        }

        if (easy < 0 || medium < 0 || hard < 0) {
            throw new IllegalArgumentException("Difficulty counts cannot be negative");
        }
        if (easy + medium + hard != total) {
            throw new IllegalArgumentException("easyCount + mediumCount + hardCount must equal totalQuestions");
        }
        return new DifficultyMix(total, easy, medium, hard);
    }

    private Language resolveLanguage(String language) {
        if (language == null || language.isBlank()) {
            return Language.HINDI;
        }
        try {
            Language parsed = Language.from(language);
            if (parsed != Language.HINDI) {
                throw new IllegalArgumentException("Only HINDI language is supported for this generation endpoint");
            }
            return parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid language. Allowed value: HINDI");
        }
    }

    private List<String> buildAvoidPromptQuestions(List<String> existingPromptQuestions,
                                                   List<String> generatedPromptQuestions) {
        List<String> combined = new ArrayList<>();
        int totalChars = 0;
        if (generatedPromptQuestions != null && !generatedPromptQuestions.isEmpty()) {
            int fromIndex = Math.max(0, generatedPromptQuestions.size() - MAX_AVOID_PROMPT_QUESTIONS);
            for (int i = generatedPromptQuestions.size() - 1; i >= fromIndex; i--) {
                String text = trimOrNull(generatedPromptQuestions.get(i));
                if (text == null) {
                    continue;
                }
                String compact = text.replaceAll("\\s+", " ");
                if (totalChars + compact.length() > MAX_AVOID_PROMPT_CHARS && !combined.isEmpty()) {
                    break;
                }
                combined.add(compact);
                totalChars += compact.length();
                if (combined.size() >= MAX_AVOID_PROMPT_QUESTIONS || totalChars >= MAX_AVOID_PROMPT_CHARS) {
                    return combined;
                }
            }
        }

        if (existingPromptQuestions == null || existingPromptQuestions.isEmpty()) {
            return combined;
        }
        for (int i = existingPromptQuestions.size() - 1; i >= 0; i--) {
            String text = trimOrNull(existingPromptQuestions.get(i));
            if (text == null) {
                continue;
            }
            String compact = text.replaceAll("\\s+", " ");
            if (totalChars + compact.length() > MAX_AVOID_PROMPT_CHARS && !combined.isEmpty()) {
                break;
            }
            combined.add(compact);
            totalChars += compact.length();
            if (combined.size() >= MAX_AVOID_PROMPT_QUESTIONS || totalChars >= MAX_AVOID_PROMPT_CHARS) {
                break;
            }
        }
        return combined;
    }

    private String normalizeQuestion(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\s]", "");
        return normalized;
    }

    private String normalizeLookupKey(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private long countExistingQuestionsForTopic(String subject, String topic, Long examId) {
        if (examId != null) {
            return questionRepository.countBySubjectAndTopicAndExamId(subject, topic, examId);
        }
        return questionRepository.countBySubjectAndTopic(subject, topic);
    }

    private ValidationResult validateGeneratedQuestions(List<QuestionDto> generated, Set<String> topicNormalized) {
        ValidationResult out = new ValidationResult();
        if (generated == null || generated.isEmpty()) {
            out.issueCounts.merge("empty_batch", 1, Integer::sum);
            return out;
        }

        for (QuestionDto dto : generated) {
            if (dto == null) {
                out.rejectedCount++;
                out.issueCounts.merge("null_question", 1, Integer::sum);
                continue;
            }
            String questionText = trimOrNull(dto.getQuestionText());
            String normalized = normalizeQuestion(questionText);
            if (normalized.isBlank() || questionText == null || questionText.length() < MIN_QUESTION_LENGTH) {
                out.rejectedCount++;
                out.issueCounts.merge("question_text_invalid", 1, Integer::sum);
                continue;
            }
            if (topicNormalized != null && topicNormalized.contains(normalized)) {
                out.rejectedCount++;
                out.issueCounts.merge("duplicate_stem_generated", 1, Integer::sum);
                continue;
            }

            String explanation = dto.getExplanation() == null ? null : trimOrNull(dto.getExplanation().getText());
            if (!hasDetailedExplanation(explanation)) {
                out.rejectedCount++;
                out.issueCounts.merge("explanation_too_short", 1, Integer::sum);
                continue;
            }
            if (!containsDevanagari(questionText) || !containsDevanagari(explanation)) {
                out.rejectedCount++;
                out.issueCounts.merge("non_hindi_content", 1, Integer::sum);
                continue;
            }

            List<QuestionChoiceDto> choices = dto.getChoices();
            if (choices == null || choices.size() != 4) {
                out.rejectedCount++;
                out.issueCounts.merge("choices_not_four", 1, Integer::sum);
                continue;
            }
            LinkedHashSet<String> optionNormalized = new LinkedHashSet<>();
            boolean optionInvalid = false;
            for (QuestionChoiceDto choice : choices) {
                String optionText = choice == null ? null : trimOrNull(choice.getText());
                if (optionText == null) {
                    optionInvalid = true;
                    break;
                }
                optionNormalized.add(normalizeQuestion(optionText));
            }
            if (optionInvalid || optionNormalized.size() != 4) {
                out.rejectedCount++;
                out.issueCounts.merge("options_duplicate_or_invalid", 1, Integer::sum);
                continue;
            }

            List<Long> choiceIds = dto.getCorrectAnswer() == null ? null : dto.getCorrectAnswer().getChoiceIds();
            if (choiceIds == null || choiceIds.size() != 1) {
                out.rejectedCount++;
                out.issueCounts.merge("correct_answer_invalid", 1, Integer::sum);
                continue;
            }
            Long correctId = choiceIds.get(0);
            if (correctId == null || correctId < 1 || correctId > 4) {
                out.rejectedCount++;
                out.issueCounts.merge("correct_answer_out_of_range", 1, Integer::sum);
                continue;
            }

            out.validQuestions.add(dto);
        }
        return out;
    }

    private String buildRefinementHint(Map<String, Integer> issueCounts, int stillNeeded) {
        if ((issueCounts == null || issueCounts.isEmpty()) && stillNeeded <= 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (stillNeeded > 0) {
            parts.add("Need " + stillNeeded + " additional valid unique questions");
        }
        if (issueCounts != null && !issueCounts.isEmpty()) {
            Integer nonHindi = issueCounts.get("non_hindi_content");
            Integer shortExplanation = issueCounts.get("explanation_too_short");
            Integer optionIssues = issueCounts.get("options_duplicate_or_invalid");
            Integer answerIssues = issueCounts.get("correct_answer_invalid");
            Integer duplicateStem = issueCounts.get("duplicate_stem_generated");
            if (defaultInt(nonHindi) > 0) {
                parts.add("Use Devanagari Hindi for question and explanation");
            }
            if (defaultInt(shortExplanation) > 0) {
                parts.add("Explanation must be detailed (minimum around 28 chars)");
            }
            if (defaultInt(optionIssues) > 0) {
                parts.add("Exactly 4 distinct options with non-empty text");
            }
            if (defaultInt(answerIssues) > 0) {
                parts.add("One valid correct answer choice id in range 1-4");
            }
            if (defaultInt(duplicateStem) > 0) {
                parts.add("Avoid repeated question stems");
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String joined = String.join(". ", parts);
        return joined.length() > 600 ? joined.substring(0, 600) : joined;
    }

    private Map<String, Object> buildTopicArtifactPayload(String subject,
                                                          String topic,
                                                          AdminSyllabusBatchGenerationResponseDto.TopicGenerationResultDto result,
                                                          List<QuestionDto> acceptedQuestions,
                                                          List<String> qualityHints) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", subject);
        out.put("topic", topic);
        out.put("result", result);
        out.put("qualityHints", qualityHints == null ? List.of() : qualityHints);
        out.put("questions", acceptedQuestions == null ? List.of() : acceptedQuestions);
        return out;
    }

    private DifficultyMix scaleDifficultyMix(DifficultyMix baseMix, int newTotal) {
        if (newTotal <= 0) {
            return new DifficultyMix(0, 0, 0, 0);
        }

        if (baseMix == null || baseMix.total <= 0
                || (baseMix.easy == 0 && baseMix.medium == 0 && baseMix.hard == 0)) {
            int easy = (int) Math.floor(newTotal * 0.20);
            int medium = (int) Math.floor(newTotal * 0.40);
            int hard = newTotal - easy - medium;
            return new DifficultyMix(newTotal, easy, medium, hard);
        }

        double easyRatio = baseMix.easy / (double) baseMix.total;
        double mediumRatio = baseMix.medium / (double) baseMix.total;
        int easy = (int) Math.floor(newTotal * easyRatio);
        int medium = (int) Math.floor(newTotal * mediumRatio);
        int hard = newTotal - easy - medium;
        if (hard < 0) {
            hard = 0;
            medium = Math.max(0, newTotal - easy);
        }
        return new DifficultyMix(newTotal, easy, medium, hard);
    }

    private String buildBatchFailureAlertBody(String examKey,
                                              AdminSyllabusBatchGenerationResponseDto response,
                                              List<String> failedTopicErrors,
                                              boolean stopRequested,
                                              String testSeriesFailure) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exam Key: ").append(safeExamKey(examKey)).append('\n');
        sb.append("Exam Code: ").append(defaultString(response == null ? null : response.getExamCode(), "N/A")).append('\n');
        sb.append("Exam Name: ").append(defaultString(response == null ? null : response.getExamName(), "N/A")).append('\n');
        if (response != null) {
            sb.append("Requested Topics: ").append(defaultInt(response.getRequestedTopicsCount())).append('\n');
            sb.append("Processed Topics: ").append(defaultInt(response.getProcessedTopicsCount())).append('\n');
            sb.append("Successful Topics: ").append(defaultInt(response.getSuccessfulTopicsCount())).append('\n');
            sb.append("Failed Topics: ").append(defaultInt(response.getFailedTopicsCount())).append('\n');
            sb.append("Questions Persisted: ").append(defaultInt(response.getTotalQuestionsPersisted())).append('\n');
            sb.append("Run Message: ").append(defaultString(response.getMessage(), "N/A")).append('\n');
        }
        sb.append("Stopped Early: ").append(stopRequested).append('\n');
        if (testSeriesFailure != null) {
            sb.append("Test-Series Failure: ").append(testSeriesFailure).append('\n');
        }

        if (failedTopicErrors != null && !failedTopicErrors.isEmpty()) {
            sb.append("\nTopic Failure Details:\n");
            int limit = Math.min(25, failedTopicErrors.size());
            for (int i = 0; i < limit; i++) {
                sb.append(i + 1).append(". ").append(failedTopicErrors.get(i)).append('\n');
            }
            if (failedTopicErrors.size() > limit) {
                sb.append("... and ").append(failedTopicErrors.size() - limit).append(" more failures.");
            }
        }
        return sb.toString();
    }

    private String safeErrorMessage(Exception ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = trimOrNull(ex.getMessage());
        if (message == null) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) + "...[truncated]" : message;
    }

    private String safeErrorMessage(RuntimeException ex) {
        if (ex == null) {
            return "Unknown runtime error";
        }
        String message = trimOrNull(ex.getMessage());
        if (message == null) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) + "...[truncated]" : message;
    }

    private String safeExamKey(String examKey) {
        String safe = trimOrNull(examKey);
        return safe == null ? "N/A" : safe;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimOrNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String toExamTag(String examCode, String examName) {
        String code = trimOrNull(examCode);
        if (code != null) {
            String normalized = code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_\\-]", "");
            normalized = normalized.replaceAll("([_\\-]?20\\d{2})$", "");
            normalized = normalized.replaceAll("[_\\-]+$", "");
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        String name = trimOrNull(examName);
        if (name == null) {
            return null;
        }
        return toAcronym(name, 6);
    }

    private String toAcronym(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "GEN";
        }
        String[] tokens = text.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (ACRONYM_STOP_WORDS.contains(token)) {
                continue;
            }
            sb.append(token.charAt(0));
            if (sb.length() >= maxLen) {
                break;
            }
        }
        if (sb.length() == 0) {
            String compact = text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (compact.isBlank()) {
                return "GEN";
            }
            int len = Math.min(maxLen, compact.length());
            return compact.substring(0, len);
        }
        return sb.toString();
    }

    private String truncateForLog(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...[truncated]";
    }

    private static class SubjectTopicNode {
        private final String subjectName;
        private final LinkedHashSet<String> topics = new LinkedHashSet<>();

        private SubjectTopicNode(String subjectName) {
            this.subjectName = subjectName;
        }
    }

    private static class AiQuestionItem {
        private String question;
        private List<AiOptionItem> options = new ArrayList<>();
        private String correctOption;
        private String explanation;
        private String questionImageSvg;
    }

    private static class AiOptionItem {
        private String text;
        private String imageSvg;
    }

    private static class DifficultyMix {
        private final int total;
        private final int easy;
        private final int medium;
        private final int hard;

        private DifficultyMix(int total, int easy, int medium, int hard) {
            this.total = total;
            this.easy = easy;
            this.medium = medium;
            this.hard = hard;
        }
    }

    private static class GenerationStats {
        private int skippedExistingDuplicates = 0;
        private int skippedGeneratedDuplicates = 0;
        private int openAiFailures = 0;
    }

    private static class ValidationResult {
        private final List<QuestionDto> validQuestions = new ArrayList<>();
        private final Map<String, Integer> issueCounts = new LinkedHashMap<>();
        private int rejectedCount = 0;
    }
}
