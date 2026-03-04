package aamscool.backend.aamschoolbackend.dto;

import java.util.List;

public class CurrentAffairsQuizSubmitRequest {

    private List<CurrentAffairsQuizSubmissionAnswerDto> answers;
    private Integer timeTakenSeconds;

    public List<CurrentAffairsQuizSubmissionAnswerDto> getAnswers() {
        return answers;
    }

    public void setAnswers(List<CurrentAffairsQuizSubmissionAnswerDto> answers) {
        this.answers = answers;
    }

    public Integer getTimeTakenSeconds() {
        return timeTakenSeconds;
    }

    public void setTimeTakenSeconds(Integer timeTakenSeconds) {
        this.timeTakenSeconds = timeTakenSeconds;
    }
}
