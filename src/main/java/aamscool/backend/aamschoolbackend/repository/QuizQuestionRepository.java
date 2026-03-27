package aamscool.backend.aamschoolbackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import aamscool.backend.aamschoolbackend.model.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    List<QuizQuestion> findByQuizIdOrderByOrderIndexAsc(Long quizId);

    void deleteByQuizIdIn(List<Long> quizIds);

    @Query("select qq.question.id from QuizQuestion qq where qq.quiz.id in :quizIds")
    List<Long> findQuestionIdsByQuizIdIn(@Param("quizIds") List<Long> quizIds);
}
