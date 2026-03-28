package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class AdminSyllabusQuestionCoverageDto {

    private String examCode;
    private String examName;
    private Long matchedQuestionExamId;
    private String matchedQuestionExamName;
    private boolean examMappedToQuestionBank;
    private List<SubjectCoverageDto> subjects = new ArrayList<>();

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

    public boolean isExamMappedToQuestionBank() {
        return examMappedToQuestionBank;
    }

    public void setExamMappedToQuestionBank(boolean examMappedToQuestionBank) {
        this.examMappedToQuestionBank = examMappedToQuestionBank;
    }

    public List<SubjectCoverageDto> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectCoverageDto> subjects) {
        this.subjects = subjects == null ? new ArrayList<>() : subjects;
    }

    public static class SubjectCoverageDto {
        private String subjectName;
        private Long availableQuestionsInExam;
        private Long availableQuestionsOverall;
        private List<TopicCoverageDto> topics = new ArrayList<>();

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public Long getAvailableQuestionsInExam() {
            return availableQuestionsInExam;
        }

        public void setAvailableQuestionsInExam(Long availableQuestionsInExam) {
            this.availableQuestionsInExam = availableQuestionsInExam;
        }

        public Long getAvailableQuestionsOverall() {
            return availableQuestionsOverall;
        }

        public void setAvailableQuestionsOverall(Long availableQuestionsOverall) {
            this.availableQuestionsOverall = availableQuestionsOverall;
        }

        public List<TopicCoverageDto> getTopics() {
            return topics;
        }

        public void setTopics(List<TopicCoverageDto> topics) {
            this.topics = topics == null ? new ArrayList<>() : topics;
        }
    }

    public static class TopicCoverageDto {
        private String topicName;
        private Long availableQuestionsInExam;
        private Long availableQuestionsOverall;

        public String getTopicName() {
            return topicName;
        }

        public void setTopicName(String topicName) {
            this.topicName = topicName;
        }

        public Long getAvailableQuestionsInExam() {
            return availableQuestionsInExam;
        }

        public void setAvailableQuestionsInExam(Long availableQuestionsInExam) {
            this.availableQuestionsInExam = availableQuestionsInExam;
        }

        public Long getAvailableQuestionsOverall() {
            return availableQuestionsOverall;
        }

        public void setAvailableQuestionsOverall(Long availableQuestionsOverall) {
            this.availableQuestionsOverall = availableQuestionsOverall;
        }
    }
}
