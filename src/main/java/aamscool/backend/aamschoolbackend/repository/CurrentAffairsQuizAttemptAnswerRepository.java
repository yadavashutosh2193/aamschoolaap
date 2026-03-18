package aamscool.backend.aamschoolbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuizAttemptAnswer;

public interface CurrentAffairsQuizAttemptAnswerRepository extends JpaRepository<CurrentAffairsQuizAttemptAnswer, Long> {

    void deleteByAttempt_Id(Long attemptId);
}
