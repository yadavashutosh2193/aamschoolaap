package aamscool.backend.aamschoolbackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import aamscool.backend.aamschoolbackend.model.Difficulty;
import aamscool.backend.aamschoolbackend.model.Language;
import aamscool.backend.aamschoolbackend.model.Question;
import aamscool.backend.aamschoolbackend.model.QuestionStatus;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    boolean existsByQuestionCode(String questionCode);
    Question findByQuestionCode(String questionCode);
    Question findByQuestionText(String questionText);

    @Query("""
            select q from Question q
            where q.difficulty = :difficulty
              and (:subject is null or q.subject = :subject)
              and (:topic is null or q.topic = :topic)
              and (:subTopic is null or q.subTopic = :subTopic)
              and (:language is null or q.language = :language)
              and (:status is null or q.status = :status)
              and (:isPremium is null or q.isPremium = :isPremium)
              and (:examId is null or exists (select e from q.exams e where e.id = :examId))
            """)
    List<Question> findByDifficultyAndFilters(
            @Param("difficulty") Difficulty difficulty,
            @Param("subject") String subject,
            @Param("topic") String topic,
            @Param("subTopic") String subTopic,
            @Param("language") Language language,
            @Param("status") QuestionStatus status,
            @Param("isPremium") Boolean isPremium,
            @Param("examId") Long examId);

    @Query("select distinct q.topic from Question q where q.topic is not null and q.topic <> '' order by q.topic")
    List<String> findDistinctTopics();

    @Query("select distinct q.topic from Question q where q.subject = :subject and q.topic is not null and q.topic <> '' order by q.topic")
    List<String> findDistinctTopicsBySubject(@Param("subject") String subject);

    @Query("select distinct q.subject from Question q where q.subject is not null and q.subject <> '' order by q.subject")
    List<String> findDistinctSubjects();

    @Query("""
            select q from Question q
            where q.subject = :subject
              and q.topic = :topic
            """)
    List<Question> findBySubjectAndTopic(@Param("subject") String subject, @Param("topic") String topic);
}
