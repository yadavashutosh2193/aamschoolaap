package aamscool.backend.aamschoolbackend.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExamSyllabusTestSeriesBlueprintDto {

    private long syllabusId;
    private String examCode;
    private String examName;
    private String conductingBody;
    private Integer notificationYear;
    private LocalDate updatedAt;
    private String matchedQuestionExamName;
    private Long matchedQuestionExamId;
    private List<PaperBlueprintDto> papers = new ArrayList<>();

    public long getSyllabusId() {
        return syllabusId;
    }

    public void setSyllabusId(long syllabusId) {
        this.syllabusId = syllabusId;
    }

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

    public String getConductingBody() {
        return conductingBody;
    }

    public void setConductingBody(String conductingBody) {
        this.conductingBody = conductingBody;
    }

    public Integer getNotificationYear() {
        return notificationYear;
    }

    public void setNotificationYear(Integer notificationYear) {
        this.notificationYear = notificationYear;
    }

    public LocalDate getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDate updatedAt) {
        this.updatedAt = updatedAt;
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

    public List<PaperBlueprintDto> getPapers() {
        return papers;
    }

    public void setPapers(List<PaperBlueprintDto> papers) {
        this.papers = papers == null ? new ArrayList<>() : papers;
    }

    public static class PaperBlueprintDto {
        private String paperCode;
        private String paperName;
        private String applicableFor;
        private List<SubjectBlueprintDto> subjects = new ArrayList<>();

        public String getPaperCode() {
            return paperCode;
        }

        public void setPaperCode(String paperCode) {
            this.paperCode = paperCode;
        }

        public String getPaperName() {
            return paperName;
        }

        public void setPaperName(String paperName) {
            this.paperName = paperName;
        }

        public String getApplicableFor() {
            return applicableFor;
        }

        public void setApplicableFor(String applicableFor) {
            this.applicableFor = applicableFor;
        }

        public List<SubjectBlueprintDto> getSubjects() {
            return subjects;
        }

        public void setSubjects(List<SubjectBlueprintDto> subjects) {
            this.subjects = subjects == null ? new ArrayList<>() : subjects;
        }
    }

    public static class SubjectBlueprintDto {
        private String subjectName;
        private String applicableFor;
        private Integer syllabusQuestions;
        private Integer syllabusMarks;
        private List<String> normalizedTopics = new ArrayList<>();
        private long availableQuestionsForSubject;
        private Map<String, Long> availableQuestionCountByTopic = new LinkedHashMap<>();

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public String getApplicableFor() {
            return applicableFor;
        }

        public void setApplicableFor(String applicableFor) {
            this.applicableFor = applicableFor;
        }

        public Integer getSyllabusQuestions() {
            return syllabusQuestions;
        }

        public void setSyllabusQuestions(Integer syllabusQuestions) {
            this.syllabusQuestions = syllabusQuestions;
        }

        public Integer getSyllabusMarks() {
            return syllabusMarks;
        }

        public void setSyllabusMarks(Integer syllabusMarks) {
            this.syllabusMarks = syllabusMarks;
        }

        public List<String> getNormalizedTopics() {
            return normalizedTopics;
        }

        public void setNormalizedTopics(List<String> normalizedTopics) {
            this.normalizedTopics = normalizedTopics == null ? new ArrayList<>() : normalizedTopics;
        }

        public long getAvailableQuestionsForSubject() {
            return availableQuestionsForSubject;
        }

        public void setAvailableQuestionsForSubject(long availableQuestionsForSubject) {
            this.availableQuestionsForSubject = availableQuestionsForSubject;
        }

        public Map<String, Long> getAvailableQuestionCountByTopic() {
            return availableQuestionCountByTopic;
        }

        public void setAvailableQuestionCountByTopic(Map<String, Long> availableQuestionCountByTopic) {
            this.availableQuestionCountByTopic =
                    availableQuestionCountByTopic == null ? new LinkedHashMap<>() : availableQuestionCountByTopic;
        }
    }
}
