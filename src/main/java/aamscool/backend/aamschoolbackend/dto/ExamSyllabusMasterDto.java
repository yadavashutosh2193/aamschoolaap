package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExamSyllabusMasterDto {

    @JsonAlias({"examName", "exam_name"})
    private String examName;

    @JsonAlias({"examCode", "exam_code"})
    private String examCode;

    @JsonAlias({"conductingBody", "conducting_body"})
    private String conductingBody;

    @JsonAlias({"notificationYear", "notification_year"})
    private Integer notificationYear;

    private List<String> level = new ArrayList<>();

    @JsonAlias({"examPattern", "exam_pattern"})
    private ExamPatternDto examPattern = new ExamPatternDto();

    private List<PaperDto> papers = new ArrayList<>();

    @JsonAlias({"languageOptions", "language_options"})
    private Map<String, Object> languageOptions = new LinkedHashMap<>();

    @JsonAlias({"selectionRule", "selection_rule"})
    private Map<String, Object> selectionRule = new LinkedHashMap<>();

    private Map<String, Object> meta = new LinkedHashMap<>();

    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public String getExamName() {
        return examName;
    }

    public void setExamName(String examName) {
        this.examName = examName;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
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

    public void setNotificationYear(Object notificationYear) {
        if (notificationYear == null) {
            this.notificationYear = null;
            return;
        }
        if (notificationYear instanceof Number number) {
            this.notificationYear = number.intValue();
            return;
        }
        String text = String.valueOf(notificationYear).trim();
        if (text.isBlank()) {
            this.notificationYear = null;
            return;
        }
        this.notificationYear = Integer.parseInt(text);
    }

    public List<String> getLevel() {
        return level;
    }

    public void setLevel(List<?> level) {
        this.level = toStringList(level);
    }

    public ExamPatternDto getExamPattern() {
        return examPattern;
    }

    public void setExamPattern(ExamPatternDto examPattern) {
        this.examPattern = examPattern == null ? new ExamPatternDto() : examPattern;
    }

    public List<PaperDto> getPapers() {
        return papers;
    }

    public void setPapers(List<PaperDto> papers) {
        this.papers = papers == null ? new ArrayList<>() : papers;
    }

    public Map<String, Object> getLanguageOptions() {
        return languageOptions;
    }

    public void setLanguageOptions(Map<String, ?> languageOptions) {
        this.languageOptions = toObjectMap(languageOptions);
    }

    public Map<String, Object> getSelectionRule() {
        return selectionRule;
    }

    public void setSelectionRule(Map<String, ?> selectionRule) {
        this.selectionRule = toObjectMap(selectionRule);
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, ?> meta) {
        this.meta = toObjectMap(meta);
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExamPatternDto {

        private String mode;

        @JsonAlias({"questionType", "question_type"})
        private String questionType;

        @JsonAlias({"durationMinutes", "duration_minutes"})
        private Integer durationMinutes;

        @JsonAlias({"totalQuestions", "total_questions"})
        private Integer totalQuestions;

        @JsonAlias({"totalMarks", "total_marks"})
        private Integer totalMarks;

        @JsonAlias({"markPerQuestion", "mark_per_question"})
        private Double markPerQuestion;

        @JsonAlias({"negativeMarking", "negative_marking"})
        private Boolean negativeMarking;

        @JsonAlias({"negativeMarkPerQuestion", "negative_mark_per_question"})
        private Double negativeMarkPerQuestion;

        @JsonAlias({"qualifyingMarks", "qualifying_marks"})
        private Map<String, Object> qualifyingMarks = new LinkedHashMap<>();

        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getQuestionType() {
            return questionType;
        }

        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Object durationMinutes) {
            this.durationMinutes = toInteger(durationMinutes);
        }

        public Integer getTotalQuestions() {
            return totalQuestions;
        }

        public void setTotalQuestions(Object totalQuestions) {
            this.totalQuestions = toInteger(totalQuestions);
        }

        public Integer getTotalMarks() {
            return totalMarks;
        }

        public void setTotalMarks(Object totalMarks) {
            this.totalMarks = toInteger(totalMarks);
        }

        public Double getMarkPerQuestion() {
            return markPerQuestion;
        }

        public void setMarkPerQuestion(Object markPerQuestion) {
            this.markPerQuestion = toDouble(markPerQuestion);
        }

        public Boolean getNegativeMarking() {
            return negativeMarking;
        }

        public void setNegativeMarking(Object negativeMarking) {
            if (negativeMarking == null) {
                this.negativeMarking = null;
            } else if (negativeMarking instanceof Boolean boolValue) {
                this.negativeMarking = boolValue;
            } else {
                this.negativeMarking = Boolean.parseBoolean(String.valueOf(negativeMarking));
            }
        }

        public Double getNegativeMarkPerQuestion() {
            return negativeMarkPerQuestion;
        }

        public void setNegativeMarkPerQuestion(Object negativeMarkPerQuestion) {
            this.negativeMarkPerQuestion = toDouble(negativeMarkPerQuestion);
        }

        public Map<String, Object> getQualifyingMarks() {
            return qualifyingMarks;
        }

        public void setQualifyingMarks(Map<String, ?> qualifyingMarks) {
            this.qualifyingMarks = toObjectMap(qualifyingMarks);
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaperDto {

        @JsonAlias({"paperCode", "paper_code"})
        private String paperCode;

        @JsonAlias({"paperName", "paper_name"})
        private String paperName;

        @JsonAlias({"applicableFor", "applicable_for"})
        private String applicableFor;

        private List<SubjectDto> subjects = new ArrayList<>();

        private final Map<String, Object> extraFields = new LinkedHashMap<>();

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

        public List<SubjectDto> getSubjects() {
            return subjects;
        }

        public void setSubjects(List<SubjectDto> subjects) {
            this.subjects = subjects == null ? new ArrayList<>() : subjects;
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubjectDto {

        @JsonAlias({"subjectName", "subject_name"})
        private String subjectName;

        private Integer questions;

        private Integer marks;

        @JsonAlias({"applicableFor", "applicable_for"})
        private String applicableFor;

        private Object topics;

        private List<String> options = new ArrayList<>();

        private Object notes;

        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public String getSubjectName() {
            return subjectName;
        }

        public void setSubjectName(String subjectName) {
            this.subjectName = subjectName;
        }

        public Integer getQuestions() {
            return questions;
        }

        public void setQuestions(Object questions) {
            this.questions = toInteger(questions);
        }

        public Integer getMarks() {
            return marks;
        }

        public void setMarks(Object marks) {
            this.marks = toInteger(marks);
        }

        public String getApplicableFor() {
            return applicableFor;
        }

        public void setApplicableFor(String applicableFor) {
            this.applicableFor = applicableFor;
        }

        public Object getTopics() {
            return topics;
        }

        public void setTopics(Object topics) {
            this.topics = topics;
        }

        public List<String> getOptions() {
            return options;
        }

        public void setOptions(List<?> options) {
            this.options = toStringList(options);
        }

        public Object getNotes() {
            return notes;
        }

        public void setNotes(Object notes) {
            this.notes = notes;
        }

        @JsonAnySetter
        public void putExtraField(String key, Object value) {
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    private static List<String> toStringList(List<?> source) {
        List<String> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        for (Object value : source) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private static Map<String, Object> toObjectMap(Map<String, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            out.put(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            String digitsOnly = text.replaceAll("[^0-9-]", "");
            if (digitsOnly.isBlank() || "-".equals(digitsOnly)) {
                return null;
            }
            try {
                return Integer.parseInt(digitsOnly);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            String normalized = text.replaceAll("[^0-9.\\-]", "");
            if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) {
                return null;
            }
            try {
                return Double.parseDouble(normalized);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
