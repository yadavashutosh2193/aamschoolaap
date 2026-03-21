package aamscool.backend.aamschoolbackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByEmailId(String emailId);
    Optional<UserAccount> findByGoogleSubject(String googleSubject);
    List<UserAccount> findAllByRole(UserRole role);
}
