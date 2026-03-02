package aamscool.backend.aamschoolbackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.Exam;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByName(String name);
    Optional<Exam> findByNameIgnoreCase(String name);
}
