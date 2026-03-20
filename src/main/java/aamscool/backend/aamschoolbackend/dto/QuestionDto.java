package aamscool.backend.aamschoolbackend.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

public class QuestionDto {

    private Long id;
    private String questionCode;
    private List<String> exams;
    @JsonAlias("examNames")
    private List<String> examNames;
    private String subject;
    private String topic;
    private String subTopic;
    private String questionText;
    private String questionImage;
    private String type;
    private String difficulty;
    @JsonAlias("difficultyLevel")
    private String difficultyLevel;
    private String language;
    private Integer marks;
    private Double negativeMarks;
    private List<String> tags;
    private List<QuestionChoiceDto> choices;
    private CorrectAnswerDto correctAnswer;
    private ExplanationDto explanation;
    @JsonAlias({"explanationText", "descriptionText"})
    private String explanationText;
    private AttemptStatsDto attemptStats;
    private SourceDto source;
    private String status;
    private Boolean isPremium;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestionCode() {
        return questionCode;
    }

    public void setQuestionCode(String questionCode) {
        this.questionCode = questionCode;
    }

    public List<String> getExams() {
        return exams;
    }

    public void setExams(List<String> exams) {
        this.exams = exams;
    }

    public List<String> getExamNames() {
        return examNames;
    }

    public void setExamNames(List<String> examNames) {
        this.examNames = examNames;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getSubTopic() {
        return subTopic;
    }

    public void setSubTopic(String subTopic) {
        this.subTopic = subTopic;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getQuestionImage() {
        return questionImage;
    }

    public void setQuestionImage(String questionImage) {
        this.questionImage = questionImage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getMarks() {
        return marks;
    }

    public void setMarks(Integer marks) {
        this.marks = marks;
    }

    public Double getNegativeMarks() {
        return negativeMarks;
    }

    public void setNegativeMarks(Double negativeMarks) {
        this.negativeMarks = negativeMarks;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<QuestionChoiceDto> getChoices() {
        return choices;
    }

    public void setChoices(List<QuestionChoiceDto> choices) {
        this.choices = choices;
    }

    public CorrectAnswerDto getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(CorrectAnswerDto correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public ExplanationDto getExplanation() {
        return explanation;
    }

    public void setExplanation(ExplanationDto explanation) {
        this.explanation = explanation;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }

    public AttemptStatsDto getAttemptStats() {
        return attemptStats;
    }

    public void setAttemptStats(AttemptStatsDto attemptStats) {
        this.attemptStats = attemptStats;
    }

    public SourceDto getSource() {
        return source;
    }

    public void setSource(SourceDto source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsPremium() {
        return isPremium;
    }

    public void setIsPremium(Boolean isPremium) {
        this.isPremium = isPremium;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
