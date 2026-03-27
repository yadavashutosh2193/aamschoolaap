package aamscool.backend.aamschoolbackend.dto;

public class ExamTestSeriesOverviewDto {

    private String examCode;
    private String examName;
    private String matchedQuestionExamName;
    private Long matchedQuestionExamId;
    private int generatedSeriesCount;
    private int totalGeneratableSeriesCount;
    private int additionalGeneratableSeriesCount;
    private int questionsPerSeries;

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

    public int getGeneratedSeriesCount() {
        return generatedSeriesCount;
    }

    public void setGeneratedSeriesCount(int generatedSeriesCount) {
        this.generatedSeriesCount = generatedSeriesCount;
    }

    public int getTotalGeneratableSeriesCount() {
        return totalGeneratableSeriesCount;
    }

    public void setTotalGeneratableSeriesCount(int totalGeneratableSeriesCount) {
        this.totalGeneratableSeriesCount = totalGeneratableSeriesCount;
    }

    public int getAdditionalGeneratableSeriesCount() {
        return additionalGeneratableSeriesCount;
    }

    public void setAdditionalGeneratableSeriesCount(int additionalGeneratableSeriesCount) {
        this.additionalGeneratableSeriesCount = additionalGeneratableSeriesCount;
    }

    public int getQuestionsPerSeries() {
        return questionsPerSeries;
    }

    public void setQuestionsPerSeries(int questionsPerSeries) {
        this.questionsPerSeries = questionsPerSeries;
    }
}
