package aamscool.backend.aamschoolbackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import aamscool.backend.aamschoolbackend.model.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    @Query("""
            select q from Quiz q
            where q.topic = :topic
            """)
    List<Quiz> findByTopic(
            @Param("topic") String topic);

    @Query("""
            select q.topic as topic, count(q) as quizCount
            from Quiz q
            where q.topic in :topics
            group by q.topic
            """)
    List<Object[]> countByTopicIn(@Param("topics") List<String> topics);

    @Query("""
            select q from Quiz q
            where q.subject = :subject
              and q.topic = :topic
            order by q.createdAt asc
            """)
    List<Quiz> findBySubjectAndTopic(@Param("subject") String subject, @Param("topic") String topic);

    @Query("""
            select q from Quiz q
            where q.subject = :subject
            order by q.createdAt asc
            """)
    List<Quiz> findBySubject(@Param("subject") String subject);

    @Query("""
            select distinct q.subject, q.topic
            from Quiz q
            where q.subject is not null
              and trim(q.subject) <> ''
              and q.topic is not null
              and trim(q.topic) <> ''
            order by q.subject asc, q.topic asc
            """)
    List<Object[]> findDistinctSubjectTopicPairs();

    long countByCreatedByAndExamId(String createdBy, Long examId);

    List<Quiz> findByCreatedByAndExamIdOrderByCreatedAtAsc(String createdBy, Long examId);

    long countByCreatedByAndSubjectAndTopic(String createdBy, String subject, String topic);

    List<Quiz> findByCreatedByAndSubjectAndTopicOrderByCreatedAtAsc(String createdBy, String subject, String topic);
}
