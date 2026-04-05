package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminSyllabusBatchGenerationRequestDto {

    private Integer totalQuestionsPerTopic;
    private Integer easyCount;
    private Integer mediumCount;
    private Integer hardCount;
    private String language;
    private Integer marks;
    private Double negativeMarks;
    private Boolean isPremium;
    private String createdBy;
    private Boolean autoGenerateTestSeries;
    private Boolean stopOnFirstFailure;
    private List<String> subjects = new ArrayList<>();

    public Integer getTotalQuestionsPerTopic() {
        return totalQuestionsPerTopic;
    }

    public void setTotalQuestionsPerTopic(Integer totalQuestionsPerTopic) {
        this.totalQuestionsPerTopic = totalQuestionsPerTopic;
    }

    public Integer getEasyCount() {
        return easyCount;
    }

    public void setEasyCount(Integer easyCount) {
        this.easyCount = easyCount;
    }

    public Integer getMediumCount() {
        return mediumCount;
    }

    public void setMediumCount(Integer mediumCount) {
        this.mediumCount = mediumCount;
    }

    public Integer getHardCount() {
        return hardCount;
    }

    public void setHardCount(Integer hardCount) {
        this.hardCount = hardCount;
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

    public Boolean getIsPremium() {
        return isPremium;
    }

    public void setIsPremium(Boolean premium) {
        isPremium = premium;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Boolean getAutoGenerateTestSeries() {
        return autoGenerateTestSeries;
    }

    public void setAutoGenerateTestSeries(Boolean autoGenerateTestSeries) {
        this.autoGenerateTestSeries = autoGenerateTestSeries;
    }

    public Boolean getStopOnFirstFailure() {
        return stopOnFirstFailure;
    }

    public void setStopOnFirstFailure(Boolean stopOnFirstFailure) {
        this.stopOnFirstFailure = stopOnFirstFailure;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects == null ? new ArrayList<>() : subjects;
    }
}
