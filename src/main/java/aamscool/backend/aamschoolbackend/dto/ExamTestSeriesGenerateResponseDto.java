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
    private int generatedNowFullTestCount;
    private int generatedNowSectionalTestCount;
    private int generatedNowPyqTestCount;
    private int generatedTotalFullTestAfterRun;
    private int generatedTotalSectionalTestAfterRun;
    private int generatedTotalPyqTestAfterRun;
    private int additionalGeneratableFullTestAfterRun;
    private int additionalGeneratableSectionalTestAfterRun;
    private int additionalGeneratablePyqTestAfterRun;
    private List<Long> createdFullTestQuizIds = new ArrayList<>();
    private List<Long> createdSectionalTestQuizIds = new ArrayList<>();
    private List<Long> createdPyqTestQuizIds = new ArrayList<>();
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

    public int getGeneratedNowFullTestCount() {
        return generatedNowFullTestCount;
    }

    public void setGeneratedNowFullTestCount(int generatedNowFullTestCount) {
        this.generatedNowFullTestCount = generatedNowFullTestCount;
    }

    public int getGeneratedNowSectionalTestCount() {
        return generatedNowSectionalTestCount;
    }

    public void setGeneratedNowSectionalTestCount(int generatedNowSectionalTestCount) {
        this.generatedNowSectionalTestCount = generatedNowSectionalTestCount;
    }

    public int getGeneratedNowPyqTestCount() {
        return generatedNowPyqTestCount;
    }

    public void setGeneratedNowPyqTestCount(int generatedNowPyqTestCount) {
        this.generatedNowPyqTestCount = generatedNowPyqTestCount;
    }

    public int getGeneratedTotalFullTestAfterRun() {
        return generatedTotalFullTestAfterRun;
    }

    public void setGeneratedTotalFullTestAfterRun(int generatedTotalFullTestAfterRun) {
        this.generatedTotalFullTestAfterRun = generatedTotalFullTestAfterRun;
    }

    public int getGeneratedTotalSectionalTestAfterRun() {
        return generatedTotalSectionalTestAfterRun;
    }

    public void setGeneratedTotalSectionalTestAfterRun(int generatedTotalSectionalTestAfterRun) {
        this.generatedTotalSectionalTestAfterRun = generatedTotalSectionalTestAfterRun;
    }

    public int getGeneratedTotalPyqTestAfterRun() {
        return generatedTotalPyqTestAfterRun;
    }

    public void setGeneratedTotalPyqTestAfterRun(int generatedTotalPyqTestAfterRun) {
        this.generatedTotalPyqTestAfterRun = generatedTotalPyqTestAfterRun;
    }

    public int getAdditionalGeneratableFullTestAfterRun() {
        return additionalGeneratableFullTestAfterRun;
    }

    public void setAdditionalGeneratableFullTestAfterRun(int additionalGeneratableFullTestAfterRun) {
        this.additionalGeneratableFullTestAfterRun = additionalGeneratableFullTestAfterRun;
    }

    public int getAdditionalGeneratableSectionalTestAfterRun() {
        return additionalGeneratableSectionalTestAfterRun;
    }

    public void setAdditionalGeneratableSectionalTestAfterRun(int additionalGeneratableSectionalTestAfterRun) {
        this.additionalGeneratableSectionalTestAfterRun = additionalGeneratableSectionalTestAfterRun;
    }

    public int getAdditionalGeneratablePyqTestAfterRun() {
        return additionalGeneratablePyqTestAfterRun;
    }

    public void setAdditionalGeneratablePyqTestAfterRun(int additionalGeneratablePyqTestAfterRun) {
        this.additionalGeneratablePyqTestAfterRun = additionalGeneratablePyqTestAfterRun;
    }

    public List<Long> getCreatedFullTestQuizIds() {
        return createdFullTestQuizIds;
    }

    public void setCreatedFullTestQuizIds(List<Long> createdFullTestQuizIds) {
        this.createdFullTestQuizIds = createdFullTestQuizIds == null ? new ArrayList<>() : createdFullTestQuizIds;
    }

    public List<Long> getCreatedSectionalTestQuizIds() {
        return createdSectionalTestQuizIds;
    }

    public void setCreatedSectionalTestQuizIds(List<Long> createdSectionalTestQuizIds) {
        this.createdSectionalTestQuizIds = createdSectionalTestQuizIds == null ? new ArrayList<>() : createdSectionalTestQuizIds;
    }

    public List<Long> getCreatedPyqTestQuizIds() {
        return createdPyqTestQuizIds;
    }

    public void setCreatedPyqTestQuizIds(List<Long> createdPyqTestQuizIds) {
        this.createdPyqTestQuizIds = createdPyqTestQuizIds == null ? new ArrayList<>() : createdPyqTestQuizIds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
