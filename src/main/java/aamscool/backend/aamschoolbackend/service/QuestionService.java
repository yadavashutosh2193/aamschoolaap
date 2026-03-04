package aamscool.backend.aamschoolbackend.service;

import java.util.ArrayList;
import java.util.List;

import aamscool.backend.aamschoolbackend.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.model.Difficulty;
import aamscool.backend.aamschoolbackend.model.Exam;
import aamscool.backend.aamschoolbackend.model.Language;
import aamscool.backend.aamschoolbackend.model.Question;
import aamscool.backend.aamschoolbackend.model.QuestionChoice;
import aamscool.backend.aamschoolbackend.model.QuestionStatus;
import aamscool.backend.aamschoolbackend.model.QuestionType;
import aamscool.backend.aamschoolbackend.repository.ExamRepository;
import aamscool.backend.aamschoolbackend.repository.QuestionRepository;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;

    public QuestionService(QuestionRepository questionRepository, ExamRepository examRepository) {
        this.questionRepository = questionRepository;
        this.examRepository = examRepository;
    }

    public List<QuestionDto> getAll() {
        return questionRepository.findAll().stream().map(this::toDto).toList();
    }

    public QuestionDto getById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        return toDto(question);
    }

    public QuestionDto mapToDto(Question question) {
        return toDto(question);
    }

    public List<String> getAllTopics() {
        return questionRepository.findDistinctTopics();
    }

    public List<String> getTopicsBySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        return questionRepository.findDistinctTopicsBySubject(subject);
    }

    public List<String> getAllSubjects() {
        return questionRepository.findDistinctSubjects();
    }

    @Transactional
    public QuestionDto create(QuestionDto dto) {
        validate(dto);
        if (questionRepository.existsByQuestionCode(dto.getQuestionCode())) {
            throw new IllegalArgumentException("Question code already exists");
        }
        Question question = new Question();
        applyToEntity(question, dto, true);
        Question saved = questionRepository.save(question);
        return toDto(saved);
    }

    @Transactional
    public QuestionBulkUpsertResult bulkUpsert(List<QuestionDto> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Questions list is required");
        }
        int created = 0;
        int updated = 0;
        List<String> createdCodes = new ArrayList<>();
        List<String> updatedCodes = new ArrayList<>();

        for (QuestionDto dto : questions) {
            Question existing = findExisting(dto);
            if (existing == null) {
                validate(dto);
                Question question = new Question();
                applyToEntity(question, dto, true);
                Question saved = questionRepository.save(question);
                created++;
                createdCodes.add(saved.getQuestionCode());
            } else {
                applyToEntity(existing, dto, false);
                Question saved = questionRepository.save(existing);
                updated++;
                updatedCodes.add(saved.getQuestionCode());
            }
        }

        QuestionBulkUpsertResult result = new QuestionBulkUpsertResult();
        result.setTotal(questions.size());
        result.setCreated(created);
        result.setUpdated(updated);
        result.setDuplicates(updated);
        result.setCreatedCodes(createdCodes);
        result.setUpdatedCodes(updatedCodes);
        result.setMessage("Created: " + created + ", Updated (duplicates): " + updated);
        return result;
    }

    @Transactional
    public QuestionDto update(Long id, QuestionDto dto) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (dto.getQuestionCode() != null && !dto.getQuestionCode().equals(question.getQuestionCode())) {
            if (questionRepository.existsByQuestionCode(dto.getQuestionCode())) {
                throw new IllegalArgumentException("Question code already exists");
            }
        }
        applyToEntity(question, dto, false);
        Question saved = questionRepository.save(question);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new IllegalArgumentException("Question not found");
        }
        questionRepository.deleteById(id);
    }

    private void validate(QuestionDto dto) {
        if (dto.getQuestionCode() == null || dto.getQuestionCode().isBlank()) {
            throw new IllegalArgumentException("Question code is required");
        }
        if (dto.getSubject() == null || dto.getSubject().isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (dto.getTopic() == null || dto.getTopic().isBlank()) {
            throw new IllegalArgumentException("Topic is required");
        }
        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw new IllegalArgumentException("Question type is required");
        }
        if (dto.getChoices() == null || dto.getChoices().isEmpty()) {
            throw new IllegalArgumentException("Choices are required");
        }
        if (dto.getCorrectAnswer() == null || dto.getCorrectAnswer().getChoiceIds() == null
                || dto.getCorrectAnswer().getChoiceIds().isEmpty()) {
            throw new IllegalArgumentException("Correct answer is required");
        }
    }

    private Question findExisting(QuestionDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getQuestionCode() != null && !dto.getQuestionCode().isBlank()) {
            Question byCode = questionRepository.findByQuestionCode(dto.getQuestionCode());
            if (byCode != null) {
                return byCode;
            }
        }
        if (dto.getQuestionText() != null && !dto.getQuestionText().isBlank()) {
            return questionRepository.findByQuestionText(dto.getQuestionText());
        }
        return null;
    }

    private void applyToEntity(Question question, QuestionDto dto, boolean isCreate) {
        if (dto.getQuestionCode() != null) {
            question.setQuestionCode(dto.getQuestionCode());
        }
        List<String> examNames = dto.getExams();
        if (examNames == null || examNames.isEmpty()) {
            examNames = dto.getExamNames();
        }
        if (examNames != null) {
            question.getExams().clear();
            for (String examName : examNames) {
                Exam exam = resolveExamName(examName);
                if (exam != null) {
                    question.getExams().add(exam);
                }
            }
        }
        if (dto.getSubject() != null) {
            question.setSubject(dto.getSubject());
        }
        if (dto.getTopic() != null) {
            question.setTopic(dto.getTopic());
        }
        if (dto.getSubTopic() != null) {
            question.setSubTopic(dto.getSubTopic());
        }
        if (dto.getQuestionText() != null) {
            question.setQuestionText(dto.getQuestionText());
        }
        if (dto.getQuestionImage() != null || isCreate) {
            question.setQuestionImage(dto.getQuestionImage());
        }
        if (dto.getType() != null) {
            question.setType(QuestionType.valueOf(dto.getType()));
        }
        String difficulty = dto.getDifficulty() != null ? dto.getDifficulty() : dto.getDifficultyLevel();
        if (difficulty != null) {
            question.setDifficulty(Difficulty.valueOf(difficulty));
        }
        if (dto.getLanguage() != null) {
            question.setLanguage(Language.from(dto.getLanguage()));
        }
        if (dto.getMarks() != null || isCreate) {
            question.setMarks(dto.getMarks());
        }
        if (dto.getNegativeMarks() != null || isCreate) {
            question.setNegativeMarks(dto.getNegativeMarks());
        }
        if (dto.getTags() != null) {
            question.setTags(dto.getTags());
        }
        if (dto.getStatus() != null) {
            question.setStatus(QuestionStatus.valueOf(dto.getStatus()));
        }
        if (dto.getIsPremium() != null || isCreate) {
            question.setIsPremium(dto.getIsPremium());
        }
        if (dto.getCreatedBy() != null || isCreate) {
            question.setCreatedBy(dto.getCreatedBy());
        }

        if (dto.getExplanation() != null) {
            question.setExplanationText(dto.getExplanation().getText());
            question.setExplanationImage(dto.getExplanation().getImage());
            question.setExplanationVideoUrl(dto.getExplanation().getVideoUrl());
        } else if (dto.getExplanationText() != null) {
            question.setExplanationText(dto.getExplanationText());
        }

        if (dto.getSource() != null) {
            question.setSourceType(dto.getSource().getType());
            question.setSourceYear(dto.getSource().getYear());
            question.setSourceShift(dto.getSource().getShift());
        }

        if (dto.getAttemptStats() != null) {
            if (dto.getAttemptStats().getTotalAttempts() != null) {
                question.setTotalAttempts(dto.getAttemptStats().getTotalAttempts());
            }
            if (dto.getAttemptStats().getCorrectAttempts() != null) {
                question.setCorrectAttempts(dto.getAttemptStats().getCorrectAttempts());
            }
        }

        if (dto.getChoices() != null) {
            question.getChoices().clear();
            for (QuestionChoiceDto choiceDto : dto.getChoices()) {
                QuestionChoice choice = new QuestionChoice();
                choice.setQuestion(question);
                choice.setText(choiceDto.getText());
                choice.setImage(choiceDto.getImage());
                choice.setIsCorrect(false);
                question.getChoices().add(choice);
            }
            markCorrectChoices(question, dto.getCorrectAnswer());
        }
    }

    private Exam resolveExamName(String examName) {
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

    private void markCorrectChoices(Question question, CorrectAnswerDto correctAnswer) {
        if (correctAnswer == null || correctAnswer.getChoiceIds() == null) {
            return;
        }
        List<Long> ids = correctAnswer.getChoiceIds();
        boolean matchedById = false;
        for (QuestionChoice choice : question.getChoices()) {
            if (choice.getId() != null && ids.contains(choice.getId())) {
                choice.setIsCorrect(true);
                matchedById = true;
            }
        }
        if (!matchedById) {
            for (int i = 0; i < question.getChoices().size(); i++) {
                long position = i + 1L;
                if (ids.contains(position)) {
                    question.getChoices().get(i).setIsCorrect(true);
                }
            }
        }
    }

    private QuestionDto toDto(Question question) {
        QuestionDto dto = new QuestionDto();
        dto.setId(question.getId());
        dto.setQuestionCode(question.getQuestionCode());
        if (question.getExams() != null && !question.getExams().isEmpty()) {
            List<String> exams = new ArrayList<>();
            for (Exam exam : question.getExams()) {
                if (exam.getName() != null) {
                    exams.add(exam.getName());
                }
            }
            dto.setExams(exams);
        }
        dto.setSubject(question.getSubject());
        dto.setTopic(question.getTopic());
        dto.setSubTopic(question.getSubTopic());
        dto.setQuestionText(question.getQuestionText());
        dto.setQuestionImage(question.getQuestionImage());
        dto.setType(question.getType() == null ? null : question.getType().name());
        dto.setDifficulty(question.getDifficulty() == null ? null : question.getDifficulty().name());
        dto.setLanguage(question.getLanguage() == null ? null : question.getLanguage().name());
        dto.setMarks(question.getMarks());
        dto.setNegativeMarks(question.getNegativeMarks());
        dto.setTags(question.getTags());
        dto.setStatus(question.getStatus() == null ? null : question.getStatus().name());
        dto.setIsPremium(question.getIsPremium());
        dto.setCreatedBy(question.getCreatedBy());
        dto.setCreatedAt(question.getCreatedAt());
        dto.setUpdatedAt(question.getUpdatedAt());

        List<QuestionChoiceDto> choiceDtos = new ArrayList<>();
        List<Long> correctIds = new ArrayList<>();
        for (QuestionChoice choice : question.getChoices()) {
            QuestionChoiceDto choiceDto = new QuestionChoiceDto();
            choiceDto.setId(choice.getId());
            choiceDto.setText(choice.getText());
            choiceDto.setImage(choice.getImage());
            choiceDtos.add(choiceDto);
            if (Boolean.TRUE.equals(choice.getIsCorrect())) {
                correctIds.add(choice.getId());
            }
        }
        dto.setChoices(choiceDtos);

        CorrectAnswerDto correct = new CorrectAnswerDto();
        correct.setChoiceIds(correctIds);
        dto.setCorrectAnswer(correct);

        ExplanationDto exp = new ExplanationDto();
        exp.setText(question.getExplanationText());
        exp.setImage(question.getExplanationImage());
        exp.setVideoUrl(question.getExplanationVideoUrl());
        dto.setExplanation(exp);

        AttemptStatsDto stats = new AttemptStatsDto();
        stats.setTotalAttempts(question.getTotalAttempts());
        stats.setCorrectAttempts(question.getCorrectAttempts());
        stats.setAccuracy(calculateAccuracy(question.getTotalAttempts(), question.getCorrectAttempts()));
        dto.setAttemptStats(stats);

        SourceDto source = new SourceDto();
        source.setType(question.getSourceType());
        source.setYear(question.getSourceYear());
        source.setShift(question.getSourceShift());
        dto.setSource(source);

        return dto;
    }

    private Double calculateAccuracy(Long total, Long correct) {
        if (total == null || total == 0 || correct == null) {
            return 0.0;
        }
        return (correct * 100.0) / total;
    }
}
