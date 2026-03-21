package aamscool.backend.aamschoolbackend.service;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.dto.UserRole;
import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.ExamSyllabus;
import aamscool.backend.aamschoolbackend.model.JobMaster;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.UserAccount;
import aamscool.backend.aamschoolbackend.repository.UserAccountRepository;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class FacebookPageNotifierService {

    private static final Logger log = LoggerFactory.getLogger(FacebookPageNotifierService.class);

    private final UserAccountRepository userAccountRepository;
    private final JavaMailSender mailSender;

    @Value("${notifications.facebook.enabled:false}")
    private boolean enabled;

    @Value("${notifications.facebook.api-version:v22.0}")
    private String apiVersion;

    @Value("${notifications.facebook.page-id:}")
    private String pageId;

    @Value("${notifications.facebook.page-access-token:}")
    private String pageAccessToken;

    @Value("${notifications.facebook.delay-minutes:5}")
    private long delayMinutes;

    @Value("${notifications.public-base-url:https://www.aamschool.in}")
    private String publicBaseUrl;

    @Value("${notifications.daily-quiz-url:https://www.aamschool.in/daily-current-affairs-quiz}")
    private String dailyQuizUrl;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    private final OkHttpClient client = new OkHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public FacebookPageNotifierService(UserAccountRepository userAccountRepository, JavaMailSender mailSender) {
        this.userAccountRepository = userAccountRepository;
        this.mailSender = mailSender;
    }

    public void sendJobUpdate(JobPosts job) {
        if (job == null || !isConfigured()) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                String postPageLink = buildPostPageLink(job);
                String message = buildSectionMessage(job, postPageLink);
                postToFacebookPage(message, postPageLink);
            } catch (Exception ex) {
                notifyAdminsOnFailure("Facebook auto-post failed for jobId=" + job.getJobId(), ex.getMessage());
                log.error("Facebook send failed for jobId={}", job.getJobId(), ex);
            }
        }, safeDelayMinutes(), TimeUnit.MINUTES);
    }

    public void sendMasterJobUpdate(JobMaster job) {
        if (job == null || !isConfigured()) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                String postPageLink = buildMasterPostPageLink(job);
                String message = buildSectionMessage(job.getLabel(), job.getTitle(), job.getAdvertisementNo(), postPageLink);
                postToFacebookPage(message, postPageLink);
            } catch (Exception ex) {
                notifyAdminsOnFailure("Facebook auto-post failed for masterJobId=" + job.getId(), ex.getMessage());
                log.error("Facebook send failed for masterJobId={}", job.getId(), ex);
            }
        }, safeDelayMinutes(), TimeUnit.MINUTES);
    }

    public void sendCurrentAffairsQuizUpdate(CurrentAffairsQuiz quiz) {
        if (quiz == null || !isConfigured()) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                String quizLink = buildQuizPageLink(quiz);
                String message = "Daily Current Affairs Quiz\n\n"
                        + "Title: " + defaultString(quiz.getTitle(), "Daily Current Affairs Quiz") + "\n"
                        + "Date: " + (quiz.getCreatedAt() != null ? quiz.getCreatedAt() : "") + "\n"
                        + "Attempt Quiz: " + quizLink;
                postToFacebookPage(message, quizLink);
            } catch (Exception ex) {
                notifyAdminsOnFailure("Facebook auto-post failed for quizId=" + quiz.getQuizId(), ex.getMessage());
                log.error("Facebook quiz send failed for quizId={}", quiz.getQuizId(), ex);
            }
        }, safeDelayMinutes(), TimeUnit.MINUTES);
    }

    public void sendExamSyllabusUpdate(ExamSyllabus syllabus, boolean updated) {
        if (syllabus == null || !isConfigured()) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                String syllabusLink = buildSyllabusPageLink(syllabus);
                String title = defaultString(syllabus.getExamName(), "Exam Syllabus");
                String code = defaultString(syllabus.getExamCode(), "");
                String codeLine = code.isBlank() ? "" : "Exam Code: " + code + "\n";
                String header = updated ? "Syllabus Updated" : "New Syllabus Added";
                String message = header + "\n\n"
                        + "Title: " + title + "\n"
                        + codeLine
                        + "View Syllabus: " + syllabusLink;
                postToFacebookPage(message, syllabusLink);
            } catch (Exception ex) {
                notifyAdminsOnFailure("Facebook auto-post failed for syllabusId=" + syllabus.getId(), ex.getMessage());
                log.error("Facebook syllabus send failed for syllabusId={}", syllabus.getId(), ex);
            }
        }, safeDelayMinutes(), TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }

    private long safeDelayMinutes() {
        return Math.max(0L, delayMinutes);
    }

    private boolean isConfigured() {
        if (!enabled) {
            return false;
        }
        if (pageId == null || pageId.isBlank() || pageAccessToken == null || pageAccessToken.isBlank()) {
            String exactMessage = "Facebook page config missing: page-id/page-access-token is blank while notifications.facebook.enabled=true";
            notifyAdminsOnFailure("Facebook auto-post skipped due to invalid config", exactMessage);
            log.info("Facebook page config missing. Skipping post.");
            return false;
        }
        return true;
    }

    private String buildPostPageLink(JobPosts job) {
        return buildPostPageLink(job.getTitle(), job.getJobId());
    }

    private String buildMasterPostPageLink(JobMaster job) {
        return buildPostPageLink(job.getTitle(), job.getId());
    }

    private String buildPostPageLink(String title, long id) {
        String slug = slugify(title);
        return trimTrailingSlash(publicBaseUrl) + "/job/" + slug + "-" + id;
    }

    private String buildQuizPageLink(CurrentAffairsQuiz quiz) {
        return dailyQuizUrl;
    }

    private String buildSyllabusPageLink(ExamSyllabus syllabus) {
        String slug = buildSyllabusSlug(syllabus.getExamName());
        return trimTrailingSlash(publicBaseUrl) + "/syllabus/" + slug + "-" + syllabus.getId();
    }

    private String buildSyllabusSlug(String examName) {
        return slugify(examName) + "-syllabus";
    }

    private String buildSectionMessage(JobPosts job, String postPageLink) {
        return buildSectionMessage(job.getLabel(), job.getTitle(), job.getAdvertisementNo(), postPageLink);
    }

    private String buildSectionMessage(String labelValue, String titleValue, String adNoValue, String postPageLink) {
        String label = normalizeLabel(labelValue);
        String title = defaultString(titleValue, "New Update");
        String adNo = defaultString(adNoValue, "");
        String adNoLine = (!adNo.isBlank() && !"NA".equalsIgnoreCase(adNo))
                ? "Advertisement No: " + adNo + "\n"
                : "";

        if (label.contains("admit")) {
            return "Admit Card Update\n\n"
                    + "Title: " + title + "\n"
                    + adNoLine
                    + "Download Admit Card: " + postPageLink;
        }

        if (label.contains("result")) {
            return "Result Update\n\n"
                    + "Title: " + title + "\n"
                    + adNoLine
                    + "Check Result: " + postPageLink;
        }

        if (label.contains("answer")) {
            return "Answer Key Update\n\n"
                    + "Title: " + title + "\n"
                    + adNoLine
                    + "Download Answer Key: " + postPageLink;
        }

        if (label.contains("admission")) {
            return "Admission Update\n\n"
                    + "Title: " + title + "\n"
                    + adNoLine
                    + "Apply Link: " + postPageLink + "\n"
                    + "Full Details: " + postPageLink;
        }

        return "Latest Job Update\n\n"
                + "Title: " + title + "\n"
                + adNoLine
                + "Apply Link: " + postPageLink + "\n"
                + "Full Details: " + postPageLink;
    }

    private void postToFacebookPage(String message, String link) throws Exception {
        String url = "https://graph.facebook.com/" + apiVersion + "/" + pageId + "/feed";
        FormBody.Builder bodyBuilder = new FormBody.Builder()
                .add("message", defaultString(message, ""))
                .add("access_token", pageAccessToken);
        if (link != null && !link.isBlank()) {
            bodyBuilder.add("link", link);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException(
                        "Facebook Graph API error: " + response.code() + " | " + response.message() + " | " + errorBody
                );
            }
        }
    }

    private String normalizeLabel(String label) {
        return defaultString(label, "").toLowerCase(Locale.ROOT).trim();
    }

    private String slugify(String input) {
        String value = defaultString(input, "job").toLowerCase(Locale.ROOT).trim();
        value = value.replaceAll("[^a-z0-9\\s-]", "");
        value = value.replaceAll("\\s+", "-");
        value = value.replaceAll("-{2,}", "-");
        return value.replaceAll("^-|-$", "");
    }

    private String trimTrailingSlash(String input) {
        if (input == null) return "";
        return input.endsWith("/") ? input.substring(0, input.length() - 1) : input;
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private void notifyAdminsOnFailure(String context, String exactFailMessage) {
        try {
            String normalizedFailMessage = defaultString(exactFailMessage, "").isBlank()
                    ? "Unknown Facebook auto-post failure"
                    : exactFailMessage;

            java.util.List<String> adminEmails = userAccountRepository.findAllByRole(UserRole.ADMIN).stream()
                    .map(UserAccount::getEmailId)
                    .filter(email -> email != null && !email.isBlank())
                    .toList();

            if (adminEmails.isEmpty()) {
                log.warn("No admin email addresses found for Facebook failure alert. Context={}, exactMessage={}",
                        context, normalizedFailMessage);
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            if (fromEmail != null && !fromEmail.isBlank()) {
                message.setFrom(fromEmail);
            }
            message.setTo(adminEmails.toArray(new String[0]));
            message.setSubject("Facebook Auto Posting Failed");
            message.setText("Context: " + defaultString(context, "N/A") + "\n\n"
                    + "Exact fail message:\n"
                    + normalizedFailMessage);
            mailSender.send(message);
        } catch (Exception mailEx) {
            log.error("Failed to send admin failure email for Facebook auto-post issue. Context={}", context, mailEx);
        }
    }
}
