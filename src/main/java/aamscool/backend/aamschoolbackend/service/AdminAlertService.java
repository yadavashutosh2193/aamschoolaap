package aamscool.backend.aamschoolbackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.UserAccountRepository;

@Service
public class AdminAlertService {

    private static final Logger log = LoggerFactory.getLogger(AdminAlertService.class);

    private final UserAccountRepository userAccountRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.admin.alerts.enabled:true}")
    private boolean alertsEnabled;

    @Value("${app.security.admin.email:}")
    private String fallbackAdminEmail;

    @Value("${app.admin.alerts.cooldown-seconds:300}")
    private long cooldownSeconds;

    private final Map<String, LocalDateTime> lastSentBySubject = new ConcurrentHashMap<>();

    public AdminAlertService(UserAccountRepository userAccountRepository, JavaMailSender mailSender) {
        this.userAccountRepository = userAccountRepository;
        this.mailSender = mailSender;
    }

    public void sendFailureAlert(String subject, String body) {
        if (!alertsEnabled) {
            return;
        }
        String normalizedSubject = safeSubject(subject);
        if (!canSendNow(normalizedSubject)) {
            return;
        }
        try {
            List<String> recipients = resolveAdminRecipients();
            if (recipients.isEmpty()) {
                log.warn("Admin alert email skipped: no admin recipients. Subject={}", safeOneLine(normalizedSubject));
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(recipients.toArray(new String[0]));
            message.setSubject(normalizedSubject);
            message.setText(body == null || body.isBlank() ? "No failure details provided." : body);
            mailSender.send(message);
            lastSentBySubject.put(normalizedSubject, LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Failed to send admin alert email. Subject={}", safeOneLine(normalizedSubject), ex);
        }
    }

    private List<String> resolveAdminRecipients() {
        Set<String> recipients = new LinkedHashSet<>();
        List<UserAccount> admins = userAccountRepository.findAllByRole(UserRole.ADMIN);
        for (UserAccount admin : admins) {
            if (admin == null) {
                continue;
            }
            String email = normalizeEmail(admin.getEmailId());
            if (email != null) {
                recipients.add(email);
            }
        }

        String fallback = normalizeEmail(fallbackAdminEmail);
        if (fallback != null) {
            recipients.add(fallback);
        }
        return new ArrayList<>(recipients);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("@") ? normalized : null;
    }

    private String safeSubject(String raw) {
        String fallback = "AAMSchool Backend Failure Alert";
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String oneLine = safeOneLine(raw);
        return oneLine.length() > 180 ? oneLine.substring(0, 180) : oneLine;
    }

    private String safeOneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private boolean canSendNow(String subject) {
        if (cooldownSeconds <= 0) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSent = lastSentBySubject.get(subject);
        if (lastSent == null) {
            return true;
        }
        return !lastSent.plusSeconds(cooldownSeconds).isAfter(now);
    }
}
