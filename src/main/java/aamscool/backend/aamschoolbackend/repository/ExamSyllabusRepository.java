package aamscool.backend.aamschoolbackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import aamscool.backend.aamschoolbackend.model.ExamSyllabus;

@Repository
public interface ExamSyllabusRepository extends JpaRepository<ExamSyllabus, Long> {

    List<ExamSyllabus> findAllByOrderByUpdatedAtDesc();

    Optional<ExamSyllabus> findByExamCodeIgnoreCase(String examCode);
}
