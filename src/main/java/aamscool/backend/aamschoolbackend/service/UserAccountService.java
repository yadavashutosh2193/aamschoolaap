package aamscool.backend.aamschoolbackend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.dto.PagedResponse;
import aamscool.backend.aamschoolbackend.dto.UserCreateRequest;
import aamscool.backend.aamschoolbackend.dto.UserDto;
import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.exception.ResourceConflictException;
import aamscool.backend.aamschoolbackend.model.OtpPurpose;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.UserAccountRepository;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpService emailOtpService;
    private final GoogleIdTokenService googleIdTokenService;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
            EmailOtpService emailOtpService, GoogleIdTokenService googleIdTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpService = emailOtpService;
        this.googleIdTokenService = googleIdTokenService;
    }

    public List<UserDto> getAllUsers() {
        return userAccountRepository.findAll().stream().map(this::toDto).toList();
    }

    public List<UserDto> getRegisteredUsers() {
        return userAccountRepository.findAll().stream().map(this::toDto).toList();
    }

    public PagedResponse<UserDto> getRegisteredUsers(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);

        Page<UserAccount> userPage = userAccountRepository.findAll(PageRequest.of(normalizedPage, normalizedSize));

        PagedResponse<UserDto> response = new PagedResponse<>();
        response.setContent(userPage.getContent().stream().map(this::toDto).toList());
        response.setPage(userPage.getNumber());
        response.setSize(userPage.getSize());
        response.setTotalElements(userPage.getTotalElements());
        response.setTotalPages(userPage.getTotalPages());
        response.setLast(userPage.isLast());
        return response;
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
        UserAccount user = userAccountRepository.findByEmailId(normalizeEmail(emailId))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("User account is blocked");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return toDto(user);
    }

    @Transactional
    public UserDto loginWithGoogle(String idToken) {
        GoogleIdTokenService.GoogleProfile profile = googleIdTokenService.verify(idToken);
        UserAccount user = findOrCreateGoogleUser(profile);
        if (Boolean.TRUE.equals(user.getBlocked())) {
            throw new IllegalStateException("User account is blocked");
        }
        return toDto(user);
    }

    public UserAccount getUserEntity(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public UserAccount getUserByEmail(String emailId) {
        return userAccountRepository.findByEmailId(normalizeEmail(emailId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        return createUserInternal(request, request.getRole() == null ? UserRole.STUDENT : request.getRole());
    }

    @Transactional
    public UserDto signup(UserCreateRequest request) {
        String emailId = request == null ? null : request.getEmailId();
        emailOtpService.assertVerifiedSignupOtp(emailId);
        UserDto created = createUserInternal(request, UserRole.STUDENT);
        emailOtpService.consumeVerifiedSignupOtp(emailId);
        return created;
    }

    @Transactional
    public void sendSignupOtp(String emailId) {
        String normalizedEmail = normalizeEmail(emailId);
        if (userAccountRepository.findByEmailId(normalizedEmail).isPresent()) {
            throw new ResourceConflictException("Email already exists");
        }
        emailOtpService.sendOtp(normalizedEmail, OtpPurpose.SIGNUP);
    }

    @Transactional
    public void verifySignupOtp(String emailId, String otp) {
        String normalizedEmail = normalizeEmail(emailId);
        if (userAccountRepository.findByEmailId(normalizedEmail).isPresent()) {
            throw new ResourceConflictException("Email already exists");
        }
        emailOtpService.verifySignupOtp(normalizedEmail, otp);
    }

    @Transactional
    public void sendForgotPasswordOtp(String emailId) {
        String normalizedEmail = normalizeEmail(emailId);
        if (userAccountRepository.findByEmailId(normalizedEmail).isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        emailOtpService.sendOtp(normalizedEmail, OtpPurpose.FORGOT_PASSWORD);
    }

    @Transactional
    public void resetPassword(String emailId, String otp, String newPassword) {
        String normalizedEmail = normalizeEmail(emailId);
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }
        UserAccount user = userAccountRepository.findByEmailId(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        emailOtpService.resetPasswordWithOtp(normalizedEmail, otp);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
    }

    @Transactional
    public UserDto updateUser(Long id, UserCreateRequest request) {
        UserAccount user = getUserEntity(id);

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            if (!request.getUsername().equals(user.getUsername())
                    && userAccountRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new ResourceConflictException("Username already exists");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getEmailId() != null && !request.getEmailId().isBlank()) {
            String normalizedEmail = normalizeEmail(request.getEmailId());
            if (!normalizedEmail.equals(user.getEmailId())
                    && userAccountRepository.findByEmailId(normalizedEmail).isPresent()) {
                throw new ResourceConflictException("Email already exists");
            }
            user.setEmailId(normalizedEmail);
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getSubscriptionPlan() != null && !request.getSubscriptionPlan().isBlank()) {
            user.setSubscriptionPlan(normalizeSubscriptionPlan(request.getSubscriptionPlan()));
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

    @Transactional
    public UserDto updateBlockedStatus(Long id, boolean blocked) {
        UserAccount user = getUserEntity(id);
        user.setBlocked(blocked);
        UserAccount saved = userAccountRepository.save(user);
        return toDto(saved);
    }

    public UserDto toDto(UserAccount user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmailId(user.getEmailId());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setSubscriptionPlan(user.getSubscriptionPlan());
        dto.setBlocked(Boolean.TRUE.equals(user.getBlocked()));
        return dto;
    }

    private UserDto createUserInternal(UserCreateRequest request, UserRole role) {
        if (request == null) {
            throw new IllegalArgumentException("User request is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalizedEmail = normalizeEmail(request.getEmailId());
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (userAccountRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new ResourceConflictException("Username already exists");
        }
        if (userAccountRepository.findByEmailId(normalizedEmail).isPresent()) {
            throw new ResourceConflictException("Email already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername());
        user.setEmailId(normalizedEmail);
        user.setPhone(request.getPhone());
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setSubscriptionPlan(normalizeSubscriptionPlan(request.getSubscriptionPlan()));
        user.setBlocked(Boolean.FALSE);

        UserAccount saved = userAccountRepository.save(user);
        return toDto(saved);
    }

    private UserAccount findOrCreateGoogleUser(GoogleIdTokenService.GoogleProfile profile) {
        Optional<UserAccount> byGoogleSubject = userAccountRepository.findByGoogleSubject(profile.googleSubject());
        if (byGoogleSubject.isPresent()) {
            return byGoogleSubject.get();
        }

        String normalizedEmail = normalizeEmail(profile.email());
        Optional<UserAccount> byEmail = userAccountRepository.findByEmailId(normalizedEmail);
        if (byEmail.isPresent()) {
            UserAccount existing = byEmail.get();
            if (existing.getGoogleSubject() != null && !existing.getGoogleSubject().equals(profile.googleSubject())) {
                throw new ResourceConflictException("This email is already linked to another Google account");
            }
            existing.setGoogleSubject(profile.googleSubject());
            if (existing.getSubscriptionPlan() == null || existing.getSubscriptionPlan().isBlank()) {
                existing.setSubscriptionPlan("FREE");
            }
            return userAccountRepository.save(existing);
        }

        UserAccount user = new UserAccount();
        user.setUsername(generateUniqueUsername(profile));
        user.setEmailId(normalizedEmail);
        user.setPhone(null);
        user.setRole(UserRole.STUDENT);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setSubscriptionPlan("FREE");
        user.setGoogleSubject(profile.googleSubject());
        user.setBlocked(Boolean.FALSE);
        return userAccountRepository.save(user);
    }

    private String generateUniqueUsername(GoogleIdTokenService.GoogleProfile profile) {
        String source = profile.name();
        if (source == null || source.isBlank()) {
            int atIndex = profile.email().indexOf('@');
            source = atIndex > 0 ? profile.email().substring(0, atIndex) : profile.email();
        }

        String base = source.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
        if (base.isBlank()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (userAccountRepository.findByUsername(candidate).isPresent()) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private String normalizeEmail(String emailId) {
        if (emailId == null || emailId.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return emailId.trim().toLowerCase();
    }

    private String normalizeSubscriptionPlan(String subscriptionPlan) {
        if (subscriptionPlan == null || subscriptionPlan.isBlank()) {
            return "FREE";
        }
        return subscriptionPlan.trim().toUpperCase();
    }
}
