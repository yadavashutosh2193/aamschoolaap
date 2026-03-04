package aamscool.backend.aamschoolbackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.QuizAttempt;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    Optional<QuizAttempt> findByQuizIdAndUserId(Long quizId, Long userId);

    List<QuizAttempt> findByQuizId(Long quizId);

    List<QuizAttempt> findByQuizIdIn(List<Long> quizIds);
}
