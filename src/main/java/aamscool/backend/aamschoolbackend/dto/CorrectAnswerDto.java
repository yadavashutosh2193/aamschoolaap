package aamscool.backend.aamschoolbackend.dto;

import java.util.List;

public class CorrectAnswerDto {

    private List<Long> choiceIds;
    private String textAnswer;

    public List<Long> getChoiceIds() {
        return choiceIds;
    }

    public void setChoiceIds(List<Long> choiceIds) {
        this.choiceIds = choiceIds;
    }

    public String getTextAnswer() {
        return textAnswer;
    }

    public void setTextAnswer(String textAnswer) {
        this.textAnswer = textAnswer;
    }
}