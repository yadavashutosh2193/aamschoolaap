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
import aamscool.backend.aamschoolbackend.dto.AdminSyllabusQuestionCoverageDto;
import aamscool.backend.aamschoolbackend.dto.AttemptStatsDto;
import aamscool.backend.aamschoolbackend.dto.CorrectAnswerDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusMasterDto;
import aamscool.backend.aamschoolbackend.dto.ExplanationDto;
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
    private static final int MAX_ATTEMPTS_PER_DIFFICULTY = 15;
    private static final int MAX_AVOID_PROMPT_QUESTIONS = 120;
    private static final DateTimeFormatter CODE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> ACRONYM_STOP_WORDS = Set.of(
            "AND", "OF", "THE", "FOR", "TO", "IN", "ON", "WITH", "A", "AN"
    );

    private final ExamSyllabusService examSyllabusService;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public AdminSyllabusQuestionService(ExamSyllabusService examSyllabusService,
                                        ExamRepository examRepository,
                                        QuestionRepository questionRepository,
                                        OpenAIService openAIService,
                                        ObjectMapper objectMapper) {
        this.examSyllabusService = examSyllabusService;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
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

        Optional<Exam> matchedExam = resolveQuestionExam(syllabus);
        Long matchedExamId = matchedExam.map(Exam::getId).orElse(null);
        String examNameForQuestions = firstNonBlank(
                trimOrNull(syllabus.getExamName()),
                trimOrNull(syllabus.getExamCode()),
                matchedExam.map(Exam::getName).orElse(null)
        );
        String examCodeForQuestions = trimOrNull(syllabus.getExamCode());
        String examTag = toExamTag(examCodeForQuestions, examNameForQuestions);

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
                requestedSubTopic, examTag, "Easy", "EASY", mix.easy, language, marks, negativeMarks, isPremium, createdBy, sequence);
        sequence = generateForDifficulty(generated, generatedNormalized, existingNormalized,
                existingPromptQuestions, generatedPromptQuestions, stats,
                examNameForQuestions, subjectNode.subjectName, matchedTopic,
                requestedSubTopic, examTag, "Medium", "MEDIUM", mix.medium, language, marks, negativeMarks, isPremium, createdBy, sequence);
        generateForDifficulty(generated, generatedNormalized, existingNormalized,
                existingPromptQuestions, generatedPromptQuestions, stats,
                examNameForQuestions, subjectNode.subjectName, matchedTopic,
                requestedSubTopic, examTag, "Hard", "HARD", mix.hard, language, marks, negativeMarks, isPremium, createdBy, sequence);

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
            out.setMessage("Preview partially generated (" + generated.size() + "/" + mix.total
                    + "). Questions are not saved to DB. Retry to generate remaining questions.");
        }
        out.setQuestions(generated);
        return Optional.of(out);
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
                                      String promptDifficulty,
                                      String difficultyValue,
                                      int targetCount,
                                      Language language,
                                      Integer marks,
                                      Double negativeMarks,
                                      Boolean isPremium,
                                      String createdBy,
                                      int sequenceStart) {
        if (targetCount <= 0) {
            return sequenceStart;
        }

        int collected = 0;
        int attempts = 0;
        int sequence = sequenceStart;
        while (collected < targetCount && attempts < MAX_ATTEMPTS_PER_DIFFICULTY) {
            int remaining = targetCount - collected;
            int requestCount = Math.min(18, Math.max(8, remaining));
            List<String> avoidList = buildAvoidPromptQuestions(existingPromptQuestions, generatedPromptQuestions);
            String aiJson;
            try {
                aiJson = openAIService.generateExamSubjectTopicQuestions(
                        examName,
                        subject,
                        topic,
                        requestCount,
                        promptDifficulty,
                        language.name(),
                        avoidList
                );
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to generate questions from OpenAI: " + ex.getMessage(), ex);
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

                List<AiOptionItem> options = extractOptions(node);
                if (options.size() != 4) {
                    continue;
                }

                String correctRaw = firstNonBlank(
                        node.path("correct_option").asText(null),
                        node.path("correctOption").asText(null),
                        node.path("answer").asText(null)
                );
                String correctOption = resolveCorrectOption(correctRaw, options);
                if (correctOption == null) {
                    continue;
                }

                String explanation = firstNonBlank(
                        node.path("explanation").asText(null),
                        node.path("details_description").asText(null),
                        node.path("detailsDescription").asText(null),
                        node.path("explanation_text").asText(null)
                );

                AiQuestionItem item = new AiQuestionItem();
                item.question = question.trim();
                item.options = options;
                item.correctOption = correctOption;
                item.explanation = explanation == null ? "" : explanation.trim();
                item.questionImageSvg = trimOrNull(firstNonBlank(
                        node.path("question_image_svg").asText(null),
                        node.path("questionImageSvg").asText(null),
                        node.path("question_svg").asText(null),
                        node.path("questionSvg").asText(null)
                ));
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

    private String salvageTruncatedArray(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            return null;
        }

        int lastObjectClose = trimmed.lastIndexOf('}');
        if (lastObjectClose < 1) {
            return null;
        }
        String candidate = trimmed.substring(0, lastObjectClose + 1).trim();
        if (candidate.endsWith(",")) {
            candidate = candidate.substring(0, candidate.length() - 1).trim();
        }
        if (!candidate.startsWith("[")) {
            candidate = "[" + candidate;
        }
        if (!candidate.endsWith("]")) {
            candidate = candidate + "]";
        }
        return candidate;
    }

    private List<AiOptionItem> extractOptions(JsonNode node) {
        List<AiOptionItem> out = new ArrayList<>();
        JsonNode optionsNode = node.get("options");
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

        for (AiOptionItem option : options) {
            if (option.text != null && option.text.equalsIgnoreCase(raw)) {
                return option.text;
            }
        }
        return null;
    }

    private QuestionDto toQuestionDto(AiQuestionItem item,
                                      int sequence,
                                      String examName,
                                      String examTag,
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
        if (examTag != null && !examTag.isBlank()) {
            dto.setExams(List.of(examTag));
        } else if (examName != null && !examName.isBlank()) {
            dto.setExams(List.of(examName));
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
        if (generatedPromptQuestions != null && !generatedPromptQuestions.isEmpty()) {
            int fromIndex = Math.max(0, generatedPromptQuestions.size() - MAX_AVOID_PROMPT_QUESTIONS);
            for (int i = generatedPromptQuestions.size() - 1; i >= fromIndex; i--) {
                String text = trimOrNull(generatedPromptQuestions.get(i));
                if (text == null) {
                    continue;
                }
                combined.add(text);
                if (combined.size() >= MAX_AVOID_PROMPT_QUESTIONS) {
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
            combined.add(text);
            if (combined.size() >= MAX_AVOID_PROMPT_QUESTIONS) {
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
    }
}
