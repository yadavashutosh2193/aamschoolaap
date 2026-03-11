package aamscool.backend.aamschoolbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.UserAccountRepository;
import aamscool.backend.aamschoolbackend.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.admin.name:admin}")
    private String adminName;

    @Value("${app.security.admin.email:admin@aamschool.local}")
    private String adminEmail;

    @Value("${app.security.admin.password:admin123}")
    private String adminPassword;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
        return emailId -> {
            UserAccount user = userAccountRepository.findByEmailId(emailId)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            UserDetails details = User.withUsername(user.getEmailId())
                    .password(user.getPasswordHash())
                    .disabled(Boolean.TRUE.equals(user.getBlocked()))
                    .roles(user.getRole().name())
                    .build();
            return details;
        };
    }

    @Bean
    public CommandLineRunner seedAdmin(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userAccountRepository.findByEmailId(adminEmail).isEmpty()) {
                UserAccount admin = new UserAccount();
                admin.setUsername(adminName);
                admin.setEmailId(adminEmail);
                admin.setPhone("0000000000");
                admin.setRole(UserRole.ADMIN);
                admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                admin.setSubscriptionPlan("FREE");
                admin.setBlocked(Boolean.FALSE);
                userAccountRepository.save(admin);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/master-jobs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/signup/send-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/signup/verify-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/forgot-password/send-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/forgot-password/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/quizzes/*/submit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/current-affairs-quizzes/*/submit").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/current-affairs-quizzes/*/my-stats").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/current-affairs-quizzes/*/user-stats/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/registered").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/registered/paged").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/*/block").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/*/unblock").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users/**").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/**").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/users/**").hasAnyRole("STUDENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
