package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class ExamTestSeriesGenerateResponseDto {

    private String examCode;
    private String examName;
    private String matchedQuestionExamName;
    private Long matchedQuestionExamId;
    private int questionsPerSeries;
    private int generatedNow;
    private int generatedTotalAfterRun;
    private int totalGeneratableSeriesCount;
    private int additionalGeneratableAfterRun;
    private List<Long> createdQuizIds = new ArrayList<>();
    private String message;

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

    public String getMatchedQuestionExamName() {
        return matchedQuestionExamName;
    }

    public void setMatchedQuestionExamName(String matchedQuestionExamName) {
        this.matchedQuestionExamName = matchedQuestionExamName;
    }

    public Long getMatchedQuestionExamId() {
        return matchedQuestionExamId;
    }

    public void setMatchedQuestionExamId(Long matchedQuestionExamId) {
        this.matchedQuestionExamId = matchedQuestionExamId;
    }

    public int getQuestionsPerSeries() {
        return questionsPerSeries;
    }

    public void setQuestionsPerSeries(int questionsPerSeries) {
        this.questionsPerSeries = questionsPerSeries;
    }

    public int getGeneratedNow() {
        return generatedNow;
    }

    public void setGeneratedNow(int generatedNow) {
        this.generatedNow = generatedNow;
    }

    public int getGeneratedTotalAfterRun() {
        return generatedTotalAfterRun;
    }

    public void setGeneratedTotalAfterRun(int generatedTotalAfterRun) {
        this.generatedTotalAfterRun = generatedTotalAfterRun;
    }

    public int getTotalGeneratableSeriesCount() {
        return totalGeneratableSeriesCount;
    }

    public void setTotalGeneratableSeriesCount(int totalGeneratableSeriesCount) {
        this.totalGeneratableSeriesCount = totalGeneratableSeriesCount;
    }

    public int getAdditionalGeneratableAfterRun() {
        return additionalGeneratableAfterRun;
    }

    public void setAdditionalGeneratableAfterRun(int additionalGeneratableAfterRun) {
        this.additionalGeneratableAfterRun = additionalGeneratableAfterRun;
    }

    public List<Long> getCreatedQuizIds() {
        return createdQuizIds;
    }

    public void setCreatedQuizIds(List<Long> createdQuizIds) {
        this.createdQuizIds = createdQuizIds == null ? new ArrayList<>() : createdQuizIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
