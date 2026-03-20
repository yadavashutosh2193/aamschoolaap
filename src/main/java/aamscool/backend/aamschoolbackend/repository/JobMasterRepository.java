package aamscool.backend.aamschoolbackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import aamscool.backend.aamschoolbackend.model.JobMaster;

@Repository
public interface JobMasterRepository extends JpaRepository<JobMaster, Long> {

    Optional<JobMaster> findBySource(String source);

    List<JobMaster> findByLabelOrderByUpdatedAtDesc(String label);

    List<JobMaster> findByLabelIgnoreCaseOrderByUpdatedAtDesc(String label);
}
