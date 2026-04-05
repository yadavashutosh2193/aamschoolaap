package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminSyllabusBatchGenerationResponseDto {

    private String examCode;
    private String examName;
    private Long matchedQuestionExamId;
    private String matchedQuestionExamName;
    private Integer requestedTopicsCount;
    private Integer processedTopicsCount;
    private Integer successfulTopicsCount;
    private Integer failedTopicsCount;
    private Integer requestedQuestionsPerTopic;
    private Integer totalQuestionsRequested;
    private Integer totalQuestionsGenerated;
    private Integer totalQuestionsPersistedCreated;
    private Integer totalQuestionsPersistedUpdated;
    private Integer totalQuestionsPersisted;
    private Integer totalSkippedRunDuplicates;
    private boolean autoGenerateTestSeriesRequested;
    private boolean testSeriesGenerated;
    private String testSeriesMessage;
    private ExamTestSeriesGenerateResponseDto testSeriesGeneration;
    private String artifactId;
    private String artifactDownloadUrl;
    private String message;
    private List<TopicGenerationResultDto> topicResults = new ArrayList<>();

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

    public Integer getRequestedTopicsCount() {
        return requestedTopicsCount;
    }

    public void setRequestedTopicsCount(Integer requestedTopicsCount) {
        this.requestedTopicsCount = requestedTopicsCount;
    }

    public Integer getProcessedTopicsCount() {
        return processedTopicsCount;
    }

    public void setProcessedTopicsCount(Integer processedTopicsCount) {
        this.processedTopicsCount = processedTopicsCount;
    }

    public Integer getSuccessfulTopicsCount() {
        return successfulTopicsCount;
    }

    public void setSuccessfulTopicsCount(Integer successfulTopicsCount) {
        this.successfulTopicsCount = successfulTopicsCount;
    }

    public Integer getFailedTopicsCount() {
        return failedTopicsCount;
    }

    public void setFailedTopicsCount(Integer failedTopicsCount) {
        this.failedTopicsCount = failedTopicsCount;
    }

    public Integer getRequestedQuestionsPerTopic() {
        return requestedQuestionsPerTopic;
    }

    public void setRequestedQuestionsPerTopic(Integer requestedQuestionsPerTopic) {
        this.requestedQuestionsPerTopic = requestedQuestionsPerTopic;
    }

    public Integer getTotalQuestionsRequested() {
        return totalQuestionsRequested;
    }

    public void setTotalQuestionsRequested(Integer totalQuestionsRequested) {
        this.totalQuestionsRequested = totalQuestionsRequested;
    }

    public Integer getTotalQuestionsGenerated() {
        return totalQuestionsGenerated;
    }

    public void setTotalQuestionsGenerated(Integer totalQuestionsGenerated) {
        this.totalQuestionsGenerated = totalQuestionsGenerated;
    }

    public Integer getTotalQuestionsPersistedCreated() {
        return totalQuestionsPersistedCreated;
    }

    public void setTotalQuestionsPersistedCreated(Integer totalQuestionsPersistedCreated) {
        this.totalQuestionsPersistedCreated = totalQuestionsPersistedCreated;
    }

    public Integer getTotalQuestionsPersistedUpdated() {
        return totalQuestionsPersistedUpdated;
    }

    public void setTotalQuestionsPersistedUpdated(Integer totalQuestionsPersistedUpdated) {
        this.totalQuestionsPersistedUpdated = totalQuestionsPersistedUpdated;
    }

    public Integer getTotalQuestionsPersisted() {
        return totalQuestionsPersisted;
    }

    public void setTotalQuestionsPersisted(Integer totalQuestionsPersisted) {
        this.totalQuestionsPersisted = totalQuestionsPersisted;
    }

    public Integer getTotalSkippedRunDuplicates() {
        return totalSkippedRunDuplicates;
    }

    public void setTotalSkippedRunDuplicates(Integer totalSkippedRunDuplicates) {
        this.totalSkippedRunDuplicates = totalSkippedRunDuplicates;
    }

    public boolean isAutoGenerateTestSeriesRequested() {
        return autoGenerateTestSeriesRequested;
    }

    public void setAutoGenerateTestSeriesRequested(boolean autoGenerateTestSeriesRequested) {
        this.autoGenerateTestSeriesRequested = autoGenerateTestSeriesRequested;
    }

    public boolean isTestSeriesGenerated() {
        return testSeriesGenerated;
    }

    public void setTestSeriesGenerated(boolean testSeriesGenerated) {
        this.testSeriesGenerated = testSeriesGenerated;
    }

    public String getTestSeriesMessage() {
        return testSeriesMessage;
    }

    public void setTestSeriesMessage(String testSeriesMessage) {
        this.testSeriesMessage = testSeriesMessage;
    }

    public ExamTestSeriesGenerateResponseDto getTestSeriesGeneration() {
        return testSeriesGeneration;
    }

    public void setTestSeriesGeneration(ExamTestSeriesGenerateResponseDto testSeriesGeneration) {
        this.testSeriesGeneration = testSeriesGeneration;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getArtifactDownloadUrl() {
        return artifactDownloadUrl;
    }

    public void setArtifactDownloadUrl(String artifactDownloadUrl) {
        this.artifactDownloadUrl = artifactDownloadUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<TopicGenerationResultDto> getTopicResults() {
        return topicResults;
    }

    public void setTopicResults(List<TopicGenerationResultDto> topicResults) {
        this.topicResults = topicResults == null ? new ArrayList<>() : topicResults;
    }

    public static class TopicGenerationResultDto {
        private String subject;
        private String topic;
        private Integer requestedTotal;
        private Integer generatedCount;
        private Integer persistedCreated;
        private Integer persistedUpdated;
        private Integer persistedTotal;
        private Integer skippedExistingDuplicates;
        private Integer skippedGeneratedDuplicates;
        private Integer skippedRunDuplicates;
        private boolean complete;
        private boolean success;
        private String message;
        private String error;

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

        public Integer getGeneratedCount() {
            return generatedCount;
        }

        public void setGeneratedCount(Integer generatedCount) {
            this.generatedCount = generatedCount;
        }

        public Integer getPersistedCreated() {
            return persistedCreated;
        }

        public void setPersistedCreated(Integer persistedCreated) {
            this.persistedCreated = persistedCreated;
        }

        public Integer getPersistedUpdated() {
            return persistedUpdated;
        }

        public void setPersistedUpdated(Integer persistedUpdated) {
            this.persistedUpdated = persistedUpdated;
        }

        public Integer getPersistedTotal() {
            return persistedTotal;
        }

        public void setPersistedTotal(Integer persistedTotal) {
            this.persistedTotal = persistedTotal;
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

        public Integer getSkippedRunDuplicates() {
            return skippedRunDuplicates;
        }

        public void setSkippedRunDuplicates(Integer skippedRunDuplicates) {
            this.skippedRunDuplicates = skippedRunDuplicates;
        }

        public boolean isComplete() {
            return complete;
        }

        public void setComplete(boolean complete) {
            this.complete = complete;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
