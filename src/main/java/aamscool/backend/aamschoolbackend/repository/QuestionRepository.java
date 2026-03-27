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
              and (:subject is null or lower(trim(q.subject)) = lower(trim(:subject)))
              and (:topic is null or lower(trim(q.topic)) = lower(trim(:topic)))
              and (:subTopic is null or lower(trim(q.subTopic)) = lower(trim(:subTopic)))
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

    @Query("select distinct q.topic from Question q where lower(trim(q.subject)) = lower(trim(:subject)) and q.topic is not null and q.topic <> '' order by q.topic")
    List<String> findDistinctTopicsBySubject(@Param("subject") String subject);

    @Query("select distinct q.subject from Question q where q.subject is not null and q.subject <> '' order by q.subject")
    List<String> findDistinctSubjects();

    @Query("""
            select q from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
              and lower(trim(q.topic)) = lower(trim(:topic))
            """)
    List<Question> findBySubjectAndTopic(@Param("subject") String subject, @Param("topic") String topic);

    @Query("""
            select q from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
            order by q.id asc
            """)
    List<Question> findBySubject(@Param("subject") String subject);

    @Query("""
            select q from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
              and lower(trim(q.topic)) = lower(trim(:topic))
              and exists (select e from q.exams e where e.id = :examId)
            order by q.id asc
            """)
    List<Question> findBySubjectAndTopicAndExamId(
            @Param("subject") String subject,
            @Param("topic") String topic,
            @Param("examId") Long examId);

    @Query("""
            select q from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
              and exists (select e from q.exams e where e.id = :examId)
            order by q.id asc
            """)
    List<Question> findBySubjectAndExamId(
            @Param("subject") String subject,
            @Param("examId") Long examId);

    @Query("""
            select count(q) from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
              and (:examId is null or exists (select e from q.exams e where e.id = :examId))
            """)
    long countBySubjectAndExam(@Param("subject") String subject, @Param("examId") Long examId);

    @Query("""
            select q.topic as topic, count(q) as questionCount
            from Question q
            where lower(trim(q.subject)) = lower(trim(:subject))
              and q.topic is not null
              and q.topic <> ''
              and (:examId is null or exists (select e from q.exams e where e.id = :examId))
            group by q.topic
            """)
    List<Object[]> countTopicCoverageBySubjectAndExam(@Param("subject") String subject, @Param("examId") Long examId);
}
