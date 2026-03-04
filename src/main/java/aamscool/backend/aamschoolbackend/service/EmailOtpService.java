package aamscool.backend.aamschoolbackend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import aamscool.backend.aamschoolbackend.model.EmailOtp;
import aamscool.backend.aamschoolbackend.model.OtpPurpose;
import aamscool.backend.aamschoolbackend.repository.EmailOtpRepository;

@Service
public class EmailOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String WEBSITE_NAME = "aamschool.in";

    private final EmailOtpRepository emailOtpRepository;
    private final JavaMailSender mailSender;
    private final Map<String, Deque<LocalDateTime>> otpSendHistory = new ConcurrentHashMap<>();

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.otp.cooldown-seconds:60}")
    private long otpCooldownSeconds;

    @Value("${app.otp.max-per-hour:5}")
    private int otpMaxPerHour;

    public EmailOtpService(EmailOtpRepository emailOtpRepository, JavaMailSender mailSender) {
        this.emailOtpRepository = emailOtpRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public void sendOtp(String emailId, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(emailId);
        enforceRateLimit(normalizedEmail, purpose);

        EmailOtp otp = new EmailOtp();
        otp.setEmailId(normalizedEmail);
        otp.setOtpCode(generateOtp());
        otp.setPurpose(purpose);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        emailOtpRepository.save(otp);

        sendEmail(normalizedEmail, purpose, otp.getOtpCode());
    }

    @Transactional
    public void verifySignupOtp(String emailId, String otpCode) {
        EmailOtp otp = getLatestOtp(emailId, OtpPurpose.SIGNUP);
        validateOtp(otp, otpCode);
        otp.setVerifiedAt(LocalDateTime.now());
        emailOtpRepository.save(otp);
    }

    @Transactional
    public void assertVerifiedSignupOtp(String emailId) {
        EmailOtp otp = getLatestOtp(emailId, OtpPurpose.SIGNUP);
        assertVerifiedSignupOtp(otp);
    }

    @Transactional
    public void consumeVerifiedSignupOtp(String emailId) {
        EmailOtp otp = getLatestOtp(emailId, OtpPurpose.SIGNUP);
        assertVerifiedSignupOtp(otp);
        otp.setConsumedAt(LocalDateTime.now());
        emailOtpRepository.save(otp);
    }

    private void assertVerifiedSignupOtp(EmailOtp otp) {
        if (otp.getVerifiedAt() == null) {
            throw new IllegalArgumentException("Email OTP is not verified");
        }
        if (isExpired(otp)) {
            throw new IllegalArgumentException("Email OTP has expired");
        }
        if (otp.getConsumedAt() != null) {
            throw new IllegalArgumentException("Email OTP is already used");
        }
    }

    @Transactional
    public void resetPasswordWithOtp(String emailId, String otpCode) {
        EmailOtp otp = getLatestOtp(emailId, OtpPurpose.FORGOT_PASSWORD);
        validateOtp(otp, otpCode);
        otp.setConsumedAt(LocalDateTime.now());
        emailOtpRepository.save(otp);
    }

    private void validateOtp(EmailOtp otp, String otpCode) {
        if (otpCode == null || otpCode.isBlank()) {
            throw new IllegalArgumentException("OTP is required");
        }
        if (otp.getConsumedAt() != null) {
            throw new IllegalArgumentException("OTP is already used");
        }
        if (isExpired(otp)) {
            throw new IllegalArgumentException("OTP has expired");
        }
        if (!otp.getOtpCode().equals(otpCode.trim())) {
            throw new IllegalArgumentException("Invalid OTP");
        }
    }

    private EmailOtp getLatestOtp(String emailId, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(emailId);
        return emailOtpRepository.findTopByEmailIdAndPurposeOrderByCreatedAtDesc(normalizedEmail, purpose)
                .orElseThrow(() -> new IllegalArgumentException("OTP not found"));
    }

    private boolean isExpired(EmailOtp otp) {
        return otp.getExpiresAt() == null || LocalDateTime.now().isAfter(otp.getExpiresAt());
    }

    private String normalizeEmail(String emailId) {
        if (emailId == null || emailId.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return emailId.trim().toLowerCase();
    }

    private String generateOtp() {
        int value = 100000 + RANDOM.nextInt(900000);
        return Integer.toString(value);
    }

    private void sendEmail(String emailId, OtpPurpose purpose, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(emailId);
        message.setSubject(purpose == OtpPurpose.SIGNUP
                ? "aamschool.in signup verification code"
                : "aamschool.in password reset verification code");
        message.setText(buildMessageBody(purpose, otpCode));
        mailSender.send(message);
    }

    private String buildMessageBody(OtpPurpose purpose, String otpCode) {
        String actionLine = purpose == OtpPurpose.SIGNUP
                ? "Use this code to complete your signup on " + WEBSITE_NAME + "."
                : "Use this code to reset your password for " + WEBSITE_NAME + ".";
        return "Hello,\n\n"
                + "Your verification code for " + WEBSITE_NAME + " is: " + otpCode + "\n\n"
                + actionLine + "\n"
                + "This code will expire in " + otpExpiryMinutes + " minutes.\n\n"
                + "If you did not request this code, you can ignore this email.\n\n"
                + "Team " + WEBSITE_NAME;
    }

    private void enforceRateLimit(String emailId, OtpPurpose purpose) {
        String key = purpose.name() + ":" + emailId;
        LocalDateTime now = LocalDateTime.now();
        Deque<LocalDateTime> history = otpSendHistory.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (history) {
            LocalDateTime hourlyCutoff = now.minusHours(1);
            while (!history.isEmpty() && history.peekFirst().isBefore(hourlyCutoff)) {
                history.removeFirst();
            }

            if (!history.isEmpty()) {
                LocalDateTime lastSentAt = history.peekLast();
                if (lastSentAt != null && lastSentAt.plusSeconds(otpCooldownSeconds).isAfter(now)) {
                    throw new IllegalArgumentException(
                            "Please wait " + otpCooldownSeconds + " seconds before requesting another OTP");
                }
            }

            if (history.size() >= otpMaxPerHour) {
                throw new IllegalArgumentException("OTP request limit exceeded. Please try again later");
            }

            history.addLast(now);
        }
    }
}
