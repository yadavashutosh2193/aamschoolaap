package aamscool.backend.aamschoolbackend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.dto.LoginRequest;
import aamscool.backend.aamschoolbackend.dto.UserCreateRequest;
import aamscool.backend.aamschoolbackend.dto.UserDto;
import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.service.UserAccountService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public List<UserDto> getAllUsers() {
        return userAccountService.getAllUsers();
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
    public ResponseEntity<UserDto> signup(@RequestBody UserCreateRequest request, Authentication authentication) {
        return createUser(request, authentication);
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@RequestBody LoginRequest request) {
        UserDto user = userAccountService.login(request.getEmailId(), request.getPassword());
        return ResponseEntity.ok(user);
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
}
