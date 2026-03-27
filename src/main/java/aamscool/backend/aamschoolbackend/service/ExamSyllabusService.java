package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.ExamSyllabusDetailDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusMasterDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusSummaryDto;
import aamscool.backend.aamschoolbackend.dto.ExamSyllabusTestSeriesBlueprintDto;
import aamscool.backend.aamschoolbackend.model.Exam;
import aamscool.backend.aamschoolbackend.model.ExamSyllabus;
import aamscool.backend.aamschoolbackend.repository.ExamRepository;
import aamscool.backend.aamschoolbackend.repository.ExamSyllabusRepository;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;

@Service
public class ExamSyllabusService {

    private final ExamSyllabusRepository examSyllabusRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final TelegramNotifierService telegramNotifierService;
    private final FacebookPageNotifierService facebookPageNotifierService;

    public ExamSyllabusService(ExamSyllabusRepository examSyllabusRepository,
                               ExamRepository examRepository,
                               QuestionRepository questionRepository,
                               ObjectMapper objectMapper,
                               TelegramNotifierService telegramNotifierService,
                               FacebookPageNotifierService facebookPageNotifierService) {
        this.examSyllabusRepository = examSyllabusRepository;
        this.examRepository = examRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.telegramNotifierService = telegramNotifierService;
        this.facebookPageNotifierService = facebookPageNotifierService;
    }

    public List<ExamSyllabusSummaryDto> getAllSummaries() {
        List<ExamSyllabusSummaryDto> out = new ArrayList<>();
        for (ExamSyllabus row : examSyllabusRepository.findAllByOrderByUpdatedAtDesc()) {
            out.add(toSummary(row));
        }
        return out;
    }

    public Optional<ExamSyllabusDetailDto> getBySlugWithId(String slugWithId) {
        long id = extractIdFromSlug(slugWithId);
        if (id <= 0) {
            return Optional.empty();
        }
        return getById(id);
    }

    public Optional<ExamSyllabusDetailDto> getById(long id) {
        return examSyllabusRepository.findById(id).map(this::toDetail);
    }

    public Optional<ExamSyllabusDetailDto> getByExamKey(String examKey) {
        return findByExamKey(examKey).map(this::toDetail);
    }

    public Optional<ExamSyllabusTestSeriesBlueprintDto> getTestSeriesBlueprintByExamKey(String examKey) {
        Optional<ExamSyllabus> rowOpt = findByExamKey(examKey);
        if (rowOpt.isEmpty()) {
            return Optional.empty();
        }

        ExamSyllabus row = rowOpt.get();
        ExamSyllabusMasterDto payload = readSyllabusPayload(row);
        Optional<Exam> matchedExam = resolveQuestionExam(payload, row);
        Long examId = matchedExam.map(Exam::getId).orElse(null);
        boolean examMapped = examId != null;

        ExamSyllabusTestSeriesBlueprintDto blueprint = new ExamSyllabusTestSeriesBlueprintDto();
        blueprint.setSyllabusId(row.getId());
        blueprint.setExamCode(payload.getExamCode());
        blueprint.setExamName(payload.getExamName());
        blueprint.setConductingBody(payload.getConductingBody());
        blueprint.setNotificationYear(payload.getNotificationYear());
        blueprint.setUpdatedAt(row.getUpdatedAt());
        blueprint.setMatchedQuestionExamId(examId);
        blueprint.setMatchedQuestionExamName(matchedExam.map(Exam::getName).orElse(null));

        List<ExamSyllabusTestSeriesBlueprintDto.PaperBlueprintDto> paperBlueprints = new ArrayList<>();
        for (ExamSyllabusMasterDto.PaperDto paper : payload.getPapers()) {
            if (paper == null) {
                continue;
            }

            ExamSyllabusTestSeriesBlueprintDto.PaperBlueprintDto paperBlueprint =
                    new ExamSyllabusTestSeriesBlueprintDto.PaperBlueprintDto();
            paperBlueprint.setPaperCode(safe(paper.getPaperCode()));
            paperBlueprint.setPaperName(safe(paper.getPaperName()));
            paperBlueprint.setApplicableFor(safe(paper.getApplicableFor()));

            List<ExamSyllabusTestSeriesBlueprintDto.SubjectBlueprintDto> subjectBlueprints = new ArrayList<>();
            for (ExamSyllabusMasterDto.SubjectDto subject : paper.getSubjects()) {
                if (subject == null) {
                    continue;
                }
                String subjectName = safe(subject.getSubjectName());
                if (subjectName == null || subjectName.isBlank()) {
                    continue;
                }

                long availableForSubject = 0L;
                List<Object[]> rawTopicCoverage = new ArrayList<>();
                if (examMapped) {
                    availableForSubject = questionRepository.countBySubjectAndExam(subjectName, examId);
                    rawTopicCoverage = questionRepository.countTopicCoverageBySubjectAndExam(subjectName, examId);
                }

                Map<String, Long> topicCoverageByNormalizedKey = new LinkedHashMap<>();
                for (Object[] rowData : rawTopicCoverage) {
                    if (rowData == null || rowData.length < 2) {
                        continue;
                    }
                    String topic = rowData[0] == null ? null : String.valueOf(rowData[0]);
                    Long count = parseLong(rowData[1]);
                    if (topic == null || topic.isBlank() || count == null) {
                        continue;
                    }
                    String normalizedTopicKey = normalizeLookupKey(topic);
                    topicCoverageByNormalizedKey.merge(normalizedTopicKey, count, Long::sum);
                }

                List<String> normalizedTopics = normalizeTopics(subject.getTopics());
                if (normalizedTopics.isEmpty()) {
                    normalizedTopics = normalizeTopics(subject.getOptions());
                }

                Map<String, Long> matchedTopicCoverage = new LinkedHashMap<>();
                if (!normalizedTopics.isEmpty()) {
                    for (String topic : normalizedTopics) {
                        matchedTopicCoverage.put(
                                topic,
                                topicCoverageByNormalizedKey.getOrDefault(normalizeLookupKey(topic), 0L)
                        );
                    }
                } else {
                    for (Object[] rowData : rawTopicCoverage) {
                        if (rowData == null || rowData.length < 2) {
                            continue;
                        }
                        String topic = rowData[0] == null ? null : String.valueOf(rowData[0]).trim();
                        Long count = parseLong(rowData[1]);
                        if (topic == null || topic.isBlank() || count == null) {
                            continue;
                        }
                        matchedTopicCoverage.put(topic, count);
                    }
                }

                ExamSyllabusTestSeriesBlueprintDto.SubjectBlueprintDto subjectBlueprint =
                        new ExamSyllabusTestSeriesBlueprintDto.SubjectBlueprintDto();
                subjectBlueprint.setSubjectName(subjectName);
                subjectBlueprint.setApplicableFor(safe(subject.getApplicableFor()));
                subjectBlueprint.setSyllabusQuestions(subject.getQuestions());
                subjectBlueprint.setSyllabusMarks(subject.getMarks());
                subjectBlueprint.setNormalizedTopics(normalizedTopics);
                subjectBlueprint.setAvailableQuestionsForSubject(availableForSubject);
                subjectBlueprint.setAvailableQuestionCountByTopic(matchedTopicCoverage);
                subjectBlueprints.add(subjectBlueprint);
            }

            paperBlueprint.setSubjects(subjectBlueprints);
            paperBlueprints.add(paperBlueprint);
        }

        blueprint.setPapers(paperBlueprints);
        return Optional.of(blueprint);
    }

    @Transactional
    public ExamSyllabusDetailDto create(ExamSyllabusMasterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Syllabus payload is required");
        }
        ExamSyllabus row = new ExamSyllabus();
        applyPayload(row, dto, true);
        ExamSyllabus saved = examSyllabusRepository.save(row);
        try {
            telegramNotifierService.sendExamSyllabusUpdate(saved, false);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        try {
            facebookPageNotifierService.sendExamSyllabusUpdate(saved, false);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        return toDetail(saved);
    }

    @Transactional
    public Optional<ExamSyllabusDetailDto> updateById(long id, ExamSyllabusMasterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Syllabus payload is required");
        }
        Optional<ExamSyllabus> existing = examSyllabusRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ExamSyllabus row = existing.get();
        applyPayload(row, dto, false);
        ExamSyllabus saved = examSyllabusRepository.save(row);
        try {
            telegramNotifierService.sendExamSyllabusUpdate(saved, true);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        try {
            facebookPageNotifierService.sendExamSyllabusUpdate(saved, true);
        } catch (Exception ignored) {
            // Notification failure should not break write flow.
        }
        return Optional.of(toDetail(saved));
    }

    @Transactional
    public boolean deleteById(long id) {
        if (!examSyllabusRepository.existsById(id)) {
            return false;
        }
        examSyllabusRepository.deleteById(id);
        return true;
    }

    private void applyPayload(ExamSyllabus row, ExamSyllabusMasterDto dto, boolean isCreate) {
        validateRequiredTestSeriesFields(dto);

        String normalizedCode = normalizeExamCode(dto.getExamCode(), dto.getExamName());
        Optional<ExamSyllabus> byCode = examSyllabusRepository.findByExamCodeIgnoreCase(normalizedCode);
        if (isCreate && byCode.isPresent()) {
            throw new IllegalArgumentException("Syllabus exam code already exists");
        }
        if (!isCreate && byCode.isPresent() && byCode.get().getId() != row.getId()) {
            throw new IllegalArgumentException("Syllabus exam code already exists");
        }

        dto.setExamCode(normalizedCode);
        row.setExamCode(normalizedCode);
        row.setExamName(safe(dto.getExamName()));
        row.setConductingBody(safe(dto.getConductingBody()));
        row.setNotificationYear(dto.getNotificationYear());

        try {
            row.setPayloadJson(objectMapper.writeValueAsString(dto));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid syllabus JSON payload");
        }

        if (row.getCreatedAt() == null) {
            row.setCreatedAt(LocalDate.now());
        }
        row.setUpdatedAt(LocalDate.now());
    }

    private ExamSyllabusSummaryDto toSummary(ExamSyllabus row) {
        ExamSyllabusSummaryDto dto = new ExamSyllabusSummaryDto();
        dto.setId(row.getId());
        dto.setExamName(row.getExamName());
        dto.setExamCode(row.getExamCode());
        dto.setUpdatedAt(row.getUpdatedAt());
        dto.setSlugurl(buildSyllabusSlug(row.getExamName(), row.getId()));
        return dto;
    }

    private ExamSyllabusDetailDto toDetail(ExamSyllabus row) {
        ExamSyllabusDetailDto dto = new ExamSyllabusDetailDto();
        dto.setId(row.getId());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        dto.setSlugurl(buildSyllabusSlug(row.getExamName(), row.getId()));
        dto.setSyllabus(readSyllabusPayload(row));
        return dto;
    }

    private ExamSyllabusMasterDto readSyllabusPayload(ExamSyllabus row) {
        if (row.getPayloadJson() == null || row.getPayloadJson().isBlank()) {
            return fallbackPayload(row);
        }
        try {
            ExamSyllabusMasterDto parsed = objectMapper.readValue(row.getPayloadJson(), ExamSyllabusMasterDto.class);
            if (parsed.getExamCode() == null || parsed.getExamCode().isBlank()) {
                parsed.setExamCode(row.getExamCode());
            }
            if (parsed.getExamName() == null || parsed.getExamName().isBlank()) {
                parsed.setExamName(row.getExamName());
            }
            if (parsed.getConductingBody() == null || parsed.getConductingBody().isBlank()) {
                parsed.setConductingBody(row.getConductingBody());
            }
            if (parsed.getNotificationYear() == null) {
                parsed.setNotificationYear(row.getNotificationYear());
            }
            return parsed;
        } catch (Exception ex) {
            return fallbackPayload(row);
        }
    }

    private ExamSyllabusMasterDto fallbackPayload(ExamSyllabus row) {
        ExamSyllabusMasterDto dto = new ExamSyllabusMasterDto();
        dto.setExamCode(row.getExamCode());
        dto.setExamName(row.getExamName());
        dto.setConductingBody(row.getConductingBody());
        dto.setNotificationYear(row.getNotificationYear());
        return dto;
    }

    private long extractIdFromSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return -1;
        }
        try {
            String idText = slug.substring(slug.lastIndexOf('-') + 1);
            return Long.parseLong(idText);
        } catch (Exception ex) {
            return -1;
        }
    }

    private String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "syllabus";
        }
        String slug = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        return slug.isBlank() ? "syllabus" : slug;
    }

    private String buildSyllabusSlug(String examName, long id) {
        return toSlug(examName) + "-syllabus-" + id;
    }

    private String normalizeExamCode(String examCode, String examName) {
        String code = safe(examCode);
        if (code == null || code.isBlank()) {
            code = safe(examName);
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("examCode or examName is required");
        }
        code = code.toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (code.isBlank()) {
            throw new IllegalArgumentException("examCode is invalid");
        }
        return code;
    }

    private String safe(String value) {
        return value == null ? null : value.trim();
    }

    private void validateRequiredTestSeriesFields(ExamSyllabusMasterDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("syllabus payload is required");
        }
        if (safe(dto.getExamName()) == null) {
            throw new IllegalArgumentException("exam_name should not be empty or null");
        }
        if (dto.getExamPattern() == null) {
            throw new IllegalArgumentException("exam_pattern should not be empty or null");
        }

        ExamSyllabusMasterDto.ExamPatternDto pattern = dto.getExamPattern();
        if (pattern.getTotalQuestions() == null || pattern.getTotalQuestions() <= 0) {
            throw new IllegalArgumentException("exam_pattern.total_questions should not be empty or null");
        }
        if (pattern.getTotalMarks() == null || pattern.getTotalMarks() <= 0) {
            throw new IllegalArgumentException("exam_pattern.total_marks should not be empty or null");
        }
        if (pattern.getMarkPerQuestion() == null || pattern.getMarkPerQuestion() <= 0) {
            throw new IllegalArgumentException("exam_pattern.mark_per_question should not be empty or null");
        }
        if (pattern.getDurationMinutes() == null || pattern.getDurationMinutes() <= 0) {
            throw new IllegalArgumentException("exam_pattern.duration_minutes should not be empty or null");
        }
        if (pattern.getNegativeMarking() == null) {
            throw new IllegalArgumentException("exam_pattern.negative_marking should not be empty or null");
        }
        if (Boolean.TRUE.equals(pattern.getNegativeMarking())
                && (pattern.getNegativeMarkPerQuestion() == null || pattern.getNegativeMarkPerQuestion() <= 0)) {
            throw new IllegalArgumentException(
                    "exam_pattern.negative_mark_per_question should not be empty or null when negative_marking is true");
        }

        if (dto.getPapers() == null || dto.getPapers().isEmpty()) {
            throw new IllegalArgumentException("papers should not be empty or null");
        }

        boolean hasAtLeastOneValidSubject = false;
        for (int paperIndex = 0; paperIndex < dto.getPapers().size(); paperIndex++) {
            ExamSyllabusMasterDto.PaperDto paper = dto.getPapers().get(paperIndex);
            if (paper == null || paper.getSubjects() == null || paper.getSubjects().isEmpty()) {
                throw new IllegalArgumentException("papers[" + paperIndex + "].subjects should not be empty or null");
            }
            for (int subjectIndex = 0; subjectIndex < paper.getSubjects().size(); subjectIndex++) {
                ExamSyllabusMasterDto.SubjectDto subject = paper.getSubjects().get(subjectIndex);
                if (subject == null) {
                    throw new IllegalArgumentException("papers[" + paperIndex + "].subjects[" + subjectIndex + "] should not be null");
                }
                if (safe(subject.getSubjectName()) == null) {
                    throw new IllegalArgumentException("papers[" + paperIndex + "].subjects[" + subjectIndex + "].subject_name should not be empty or null");
                }
                List<String> topicList = normalizeTopics(subject.getTopics());
                if (topicList.isEmpty()) {
                    topicList = normalizeTopics(subject.getOptions());
                }
                if (topicList.isEmpty()) {
                    throw new IllegalArgumentException("papers[" + paperIndex + "].subjects[" + subjectIndex + "].topics should not be empty or null");
                }
                hasAtLeastOneValidSubject = true;
            }
        }

        if (!hasAtLeastOneValidSubject) {
            throw new IllegalArgumentException("at least one valid subject with topics is required");
        }
    }

    private Optional<ExamSyllabus> findByExamKey(String examKey) {
        String key = safe(examKey);
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        Optional<ExamSyllabus> byExactCode = examSyllabusRepository.findByExamCodeIgnoreCase(key);
        if (byExactCode.isPresent()) {
            return byExactCode;
        }

        try {
            String normalizedCode = normalizeExamCode(key, null);
            Optional<ExamSyllabus> byNormalizedCode = examSyllabusRepository.findByExamCodeIgnoreCase(normalizedCode);
            if (byNormalizedCode.isPresent()) {
                return byNormalizedCode;
            }
        } catch (Exception ignored) {
            // If key cannot be normalized as code, continue with exam-name lookup.
        }

        return examSyllabusRepository.findFirstByExamNameIgnoreCaseOrderByUpdatedAtDesc(key);
    }

    private Optional<Exam> resolveQuestionExam(ExamSyllabusMasterDto payload, ExamSyllabus row) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, payload.getExamName());
        addCandidate(candidates, row.getExamName());
        addCandidate(candidates, payload.getExamCode());
        addCandidate(candidates, row.getExamCode());

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
        String normalized = safe(value);
        if (normalized == null || normalized.isBlank()) {
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
                    if (keyText != null && !keyText.isBlank() && !isGenericTopicKey(keyText)) {
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
                .replace("•", "\n")
                .replace("●", "\n")
                .replace("▪", "\n")
                .replace("–", "-");

        String[] parts = normalized.split("[\\n,;|]+");
        if (parts.length == 0) {
            return;
        }
        for (String part : parts) {
            String cleaned = cleanTopicToken(part);
            if (cleaned != null && !cleaned.isBlank() && !isGenericTopicKey(cleaned)) {
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
        if (value.length() < 2) {
            return null;
        }
        return value;
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

    private String normalizeLookupKey(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
