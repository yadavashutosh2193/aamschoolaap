package aamscool.backend.aamschoolbackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.model.EmailOtp;
import aamscool.backend.aamschoolbackend.model.OtpPurpose;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findTopByEmailIdAndPurposeOrderByCreatedAtDesc(String emailId, OtpPurpose purpose);
}
