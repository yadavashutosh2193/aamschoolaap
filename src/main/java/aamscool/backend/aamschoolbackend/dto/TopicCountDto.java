package aamscool.backend.aamschoolbackend.dto;

public class TopicCountDto {

    private String topic;
    private Long quizCount;

    public TopicCountDto() {
    }

    public TopicCountDto(String topic, Long quizCount) {
        this.topic = topic;
        this.quizCount = quizCount;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Long getQuizCount() {
        return quizCount;
    }

    public void setQuizCount(Long quizCount) {
        this.quizCount = quizCount;
    }
}