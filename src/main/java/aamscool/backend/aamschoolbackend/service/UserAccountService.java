package aamscool.backend.aamschoolbackend.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.dto.UserCreateRequest;
import aamscool.backend.aamschoolbackend.dto.UserDto;
import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.UserAccountRepository;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> getAllUsers() {
        return userAccountRepository.findAll().stream().map(this::toDto).toList();
    }

    public UserDto getUser(Long id) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toDto(user);
    }

    public UserDto login(String emailId, String password) {
        if (emailId == null || emailId.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        UserAccount user = userAccountRepository.findByEmailId(emailId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return toDto(user);
    }

    public UserAccount getUserEntity(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getEmailId() == null || request.getEmailId().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (userAccountRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userAccountRepository.findByEmailId(request.getEmailId()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        UserRole role = request.getRole() == null ? UserRole.STUDENT : request.getRole();

        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername());
        user.setEmailId(request.getEmailId());
        user.setPhone(request.getPhone());
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        UserAccount saved = userAccountRepository.save(user);
        return toDto(saved);
    }

    @Transactional
    public UserDto updateUser(Long id, UserCreateRequest request) {
        UserAccount user = getUserEntity(id);

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername())
                    && userAccountRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getEmailId() != null && !request.getEmailId().isBlank()) {
            if (!request.getEmailId().equals(user.getEmailId())
                    && userAccountRepository.findByEmailId(request.getEmailId()).isPresent()) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmailId(request.getEmailId());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        UserAccount saved = userAccountRepository.save(user);
        return toDto(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userAccountRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found");
        }
        userAccountRepository.deleteById(id);
    }

    public UserDto toDto(UserAccount user) {
        UserDto dto = new UserDto();
        dto.setUsername(user.getUsername());
        dto.setEmailId(user.getEmailId());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        return dto;
    }
}
