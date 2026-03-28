package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminQuestionGenerationPreviewDto {

    private String examCode;
    private String examName;
    private Long matchedQuestionExamId;
    private String matchedQuestionExamName;
    private String subject;
    private String topic;
    private Integer requestedTotal;
    private Integer requestedEasy;
    private Integer requestedMedium;
    private Integer requestedHard;
    private Long existingQuestionCountInExam;
    private Long existingQuestionCountOverall;
    private Integer skippedExistingDuplicates;
    private Integer skippedGeneratedDuplicates;
    private Integer generatedCount;
    private boolean complete;
    private String message;
    private List<QuestionDto> questions = new ArrayList<>();

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public Long getMatchedQuestionExamId() {
        return matchedQuestionExamId;
    }

    public void setMatchedQuestionExamId(Long matchedQuestionExamId) {
        this.matchedQuestionExamId = matchedQuestionExamId;
    }

    public String getMatchedQuestionExamName() {
        return matchedQuestionExamName;
    }

    public void setMatchedQuestionExamName(String matchedQuestionExamName) {
        this.matchedQuestionExamName = matchedQuestionExamName;
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

    public Integer getRequestedTotal() {
        return requestedTotal;
    }

    public void setRequestedTotal(Integer requestedTotal) {
        this.requestedTotal = requestedTotal;
    }

    public Integer getRequestedEasy() {
        return requestedEasy;
    }

    public void setRequestedEasy(Integer requestedEasy) {
        this.requestedEasy = requestedEasy;
    }

    public Integer getRequestedMedium() {
        return requestedMedium;
    }

    public void setRequestedMedium(Integer requestedMedium) {
        this.requestedMedium = requestedMedium;
    }

    public Integer getRequestedHard() {
        return requestedHard;
    }

    public void setRequestedHard(Integer requestedHard) {
        this.requestedHard = requestedHard;
    }

    public Long getExistingQuestionCountInExam() {
        return existingQuestionCountInExam;
    }

    public void setExistingQuestionCountInExam(Long existingQuestionCountInExam) {
        this.existingQuestionCountInExam = existingQuestionCountInExam;
    }

    public Long getExistingQuestionCountOverall() {
        return existingQuestionCountOverall;
    }

    public void setExistingQuestionCountOverall(Long existingQuestionCountOverall) {
        this.existingQuestionCountOverall = existingQuestionCountOverall;
    }

    public Integer getSkippedExistingDuplicates() {
        return skippedExistingDuplicates;
    }

    public void setSkippedExistingDuplicates(Integer skippedExistingDuplicates) {
        this.skippedExistingDuplicates = skippedExistingDuplicates;
    }

    public Integer getSkippedGeneratedDuplicates() {
        return skippedGeneratedDuplicates;
    }

    public void setSkippedGeneratedDuplicates(Integer skippedGeneratedDuplicates) {
        this.skippedGeneratedDuplicates = skippedGeneratedDuplicates;
    }

    public Integer getGeneratedCount() {
        return generatedCount;
    }

    public void setGeneratedCount(Integer generatedCount) {
        this.generatedCount = generatedCount;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<QuestionDto> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions == null ? new ArrayList<>() : questions;
    }
}
