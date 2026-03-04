package aamscool.backend.aamschoolbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class QuizSubmitRequest {

    private Long userId;
    private Integer timeTakenSeconds;
    private List<QuizSubmissionAnswerDto> answers = new ArrayList<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public void setTimeTakenSeconds(Integer timeTakenSeconds) {
        this.timeTakenSeconds = timeTakenSeconds;
    }

    public List<QuizSubmissionAnswerDto> getAnswers() {
        return answers;
    }

    public void setAnswers(List<QuizSubmissionAnswerDto> answers) {
        this.answers = answers;
    }
}
