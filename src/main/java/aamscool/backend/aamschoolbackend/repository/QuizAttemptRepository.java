package aamscool.backend.aamschoolbackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByQuizId(Long quizId);

    List<QuizAttempt> findByQuizIdIn(List<Long> quizIds);

    void deleteByQuizIdIn(List<Long> quizIds);

    List<QuizAttempt> findByQuizIdAndUserIdOrderBySubmittedAtDesc(Long quizId, Long userId);

    long countByQuizIdAndUserId(Long quizId, Long userId);
}
