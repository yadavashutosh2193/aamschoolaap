package aamscool.backend.aamschoolbackend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.QuizListItem;

@Repository
public interface CurrentAffairsQuizRepository extends JpaRepository<CurrentAffairsQuiz, Long> {
    Optional<CurrentAffairsQuiz> findFirstByCreatedAtOrderByQuizIdDesc(LocalDate createdAt);

    @Query("SELECT new aamscool.backend.aamschoolbackend.model.QuizListItem(q.title, q.quizId, q.createdAt) FROM CurrentAffairsQuiz q ORDER BY q.quizId DESC")
    List<QuizListItem> findAllQuizTitlesAndIds();
}
