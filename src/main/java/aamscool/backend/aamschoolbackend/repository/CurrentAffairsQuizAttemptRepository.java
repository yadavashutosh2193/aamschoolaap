package aamscool.backend.aamschoolbackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuizAttempt;

public interface CurrentAffairsQuizAttemptRepository extends JpaRepository<CurrentAffairsQuizAttempt, Long> {
    Optional<CurrentAffairsQuizAttempt> findByQuizQuizIdAndUserId(Long quizId, Long userId);
}
