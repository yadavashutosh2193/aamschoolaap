package aamscool.backend.aamschoolbackend.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.dto.LoginRequest;
import aamscool.backend.aamschoolbackend.dto.LoginResponse;
import aamscool.backend.aamschoolbackend.dto.EmailOtpRequest;
import aamscool.backend.aamschoolbackend.dto.EmailOtpVerifyRequest;
import aamscool.backend.aamschoolbackend.dto.ForgotPasswordResetRequest;
import aamscool.backend.aamschoolbackend.dto.GoogleLoginRequest;
import aamscool.backend.aamschoolbackend.dto.MessageResponse;
import aamscool.backend.aamschoolbackend.dto.PagedResponse;
import aamscool.backend.aamschoolbackend.dto.UserCreateRequest;
import aamscool.backend.aamschoolbackend.dto.UserDto;
import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.security.JwtService;
import aamscool.backend.aamschoolbackend.security.JwtTokenBlacklistService;
import aamscool.backend.aamschoolbackend.service.UserAccountService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserAccountService userAccountService;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final JwtTokenBlacklistService blacklistService;

    public UserController(UserAccountService userAccountService, UserDetailsService userDetailsService,
            JwtService jwtService, JwtTokenBlacklistService blacklistService) {
        this.userAccountService = userAccountService;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userAccountService.getAllUsers());
    }

    @GetMapping("/registered")
    public ResponseEntity<List<UserDto>> getRegisteredUsers(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userAccountService.getRegisteredUsers());
    }

    @GetMapping("/registered/paged")
    public ResponseEntity<PagedResponse<UserDto>> getRegisteredUsersPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userAccountService.getRegisteredUsers(page, size));
    }

    @GetMapping("/{id}")
    public UserDto getUser(@PathVariable Long id) {
        return userAccountService.getUser(id);
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserCreateRequest request, Authentication authentication) {
        UserRole role = request.getRole() == null ? UserRole.STUDENT : request.getRole();
        if (role == UserRole.ADMIN && !hasRole(authentication, "ROLE_ADMIN")) {
           return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        request.setRole(role);
        UserDto created = userAccountService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/signup")
    public ResponseEntity<UserDto> signup(@RequestBody UserCreateRequest request) {
        UserDto created = userAccountService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/signup/send-otp")
    public ResponseEntity<MessageResponse> sendSignupOtp(@RequestBody EmailOtpRequest request) {
        userAccountService.sendSignupOtp(request == null ? null : request.getEmailId());
        return ResponseEntity.ok(new MessageResponse("Signup OTP sent to email"));
    }

    @PostMapping("/signup/verify-otp")
    public ResponseEntity<MessageResponse> verifySignupOtp(@RequestBody EmailOtpVerifyRequest request) {
        userAccountService.verifySignupOtp(request == null ? null : request.getEmailId(), request == null ? null : request.getOtp());
        return ResponseEntity.ok(new MessageResponse("Email OTP verified"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        UserDto user = userAccountService.login(request.getEmailId(), request.getPassword());
        UserAccount account = userAccountService.getUserByEmail(request.getEmailId());
        return ResponseEntity.ok(buildLoginResponse(account, user));
    }

    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        UserDto user = userAccountService.loginWithGoogle(request == null ? null : request.getIdToken());
        UserAccount account = userAccountService.getUserByEmail(user.getEmailId());
        return ResponseEntity.ok(buildLoginResponse(account, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String token = authorization.substring(7);
        blacklistService.blacklist(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<MessageResponse> sendForgotPasswordOtp(@RequestBody EmailOtpRequest request) {
        userAccountService.sendForgotPasswordOtp(request == null ? null : request.getEmailId());
        return ResponseEntity.ok(new MessageResponse("Password reset OTP sent to email"));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<MessageResponse> resetPassword(@RequestBody ForgotPasswordResetRequest request) {
        userAccountService.resetPassword(
                request == null ? null : request.getEmailId(),
                request == null ? null : request.getOtp(),
                request == null ? null : request.getNewPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successful"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody UserCreateRequest request,
            Authentication authentication) {
        if (request.getRole() == UserRole.ADMIN && !hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!hasRole(authentication, "ROLE_ADMIN") && !canAccessUser(authentication, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserDto updated = userAccountService.updateUser(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN") && !canAccessUser(authentication, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userAccountService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/block")
    public ResponseEntity<UserDto> blockUser(@PathVariable Long id, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserDto updated = userAccountService.updateBlockedStatus(id, true);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/unblock")
    public ResponseEntity<UserDto> unblockUser(@PathVariable Long id, Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UserDto updated = userAccountService.updateBlockedStatus(id, false);
        return ResponseEntity.ok(updated);
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean canAccessUser(Authentication authentication, Long userId) {
        if (authentication == null) {
            return false;
        }
        String emailId = authentication.getName();
        try {
            UserAccount user = userAccountService.getUserEntity(userId);
            return user.getEmailId().equals(emailId);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private LoginResponse buildLoginResponse(UserAccount account, UserDto user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(account.getEmailId());
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", account.getRole().name());
        claims.put("username", account.getUsername());
        String token = jwtService.generateToken(userDetails, claims);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(user);
        return response;
    }
}
