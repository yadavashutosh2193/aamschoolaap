package aamscool.backend.aamschoolbackend.service;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.model.CurrentAffairsQuiz;
import aamscool.backend.aamschoolbackend.model.ExamSyllabus;
import aamscool.backend.aamschoolbackend.model.JobMaster;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
public class TelegramNotifierService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifierService.class);

    private static final String BOT_TOKEN = "8668837518:AAF-g9F1cank72HawFO1gJ3OGuqTavNslb4";
    private static final String CHAT_ID = "@aamschool";
    private static final String PUBLIC_BASE_URL = "https://www.aamschool.in";
    private static final String DAILY_QUIZ_URL = "https://www.aamschool.in/daily-current-affairs-quiz";
    private static final long TELEGRAM_DELAY_MINUTES = 5;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void sendJobUpdate(JobPosts job) {
        if (BOT_TOKEN.isBlank() || CHAT_ID.isBlank()) {
            log.info("Telegram config missing. Skipping notify for jobId={}", job.getJobId());
            return;
        }

        scheduler.schedule(() -> {
            try {
                String postPageLink = buildPostPageLink(job);
                String message = buildSectionMessage(job, postPageLink);
                String buttonText = buttonTextForLabel(normalizeLabel(job.getLabel()));
                sendTelegramMessage(message, buttonText, postPageLink);
            } catch (Exception ex) {
                log.error("Telegram send failed for jobId={}", job.getJobId(), ex);
            }
        }, TELEGRAM_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    public void sendMasterJobUpdate(JobMaster job) {
        if (BOT_TOKEN.isBlank() || CHAT_ID.isBlank()) {
            log.info("Telegram config missing. Skipping notify for masterJobId={}", job.getId());
            return;
        }

        scheduler.schedule(() -> {
            try {
                String postPageLink = buildMasterPostPageLink(job);
                String message = buildSectionMessage(job.getLabel(), job.getTitle(), job.getAdvertisementNo(), postPageLink);
                String buttonText = buttonTextForLabel(normalizeLabel(job.getLabel()));
                sendTelegramMessage(message, buttonText, postPageLink);
            } catch (Exception ex) {
                log.error("Telegram send failed for masterJobId={}", job.getId(), ex);
            }
        }, TELEGRAM_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    public void sendCurrentAffairsQuizUpdate(CurrentAffairsQuiz quiz) {
        if (BOT_TOKEN.isBlank() || CHAT_ID.isBlank()) {
            log.info("Telegram config missing. Skipping quiz notify for quizId={}", quiz.getQuizId());
            return;
        }

        scheduler.schedule(() -> {
            try {
                String quizLink = buildQuizPageLink(quiz);
                String message = "Daily Current Affairs Quiz\n\n"
                        + "Title: " + defaultString(quiz.getTitle(), "Daily Current Affairs Quiz") + "\n"
                        + "Date: " + (quiz.getCreatedAt() != null ? quiz.getCreatedAt() : "") + "\n"
                        + "Attempt Quiz: " + quizLink;
                sendTelegramMessage(message, "Attempt Quiz", quizLink);
            } catch (Exception ex) {
                log.error("Telegram quiz send failed for quizId={}", quiz.getQuizId(), ex);
            }
        }, TELEGRAM_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    public void sendExamSyllabusUpdate(ExamSyllabus syllabus, boolean updated) {
        if (BOT_TOKEN.isBlank() || CHAT_ID.isBlank()) {
            log.info("Telegram config missing. Skipping syllabus notify for syllabusId={}", syllabus.getId());
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
                sendTelegramMessage(message, "View Syllabus", syllabusLink);
            } catch (Exception ex) {
                log.error("Telegram syllabus send failed for syllabusId={}", syllabus.getId(), ex);
            }
        }, TELEGRAM_DELAY_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
    }

    private String buildPostPageLink(JobPosts job) {
        return buildPostPageLink(job.getTitle(), job.getJobId());
    }

    private String buildMasterPostPageLink(JobMaster job) {
        return buildPostPageLink(job.getTitle(), job.getId());
    }

    private String buildPostPageLink(String title, long id) {
        String slug = slugify(title);
        return trimTrailingSlash(PUBLIC_BASE_URL) + "/job/" + slug + "-" + id;
    }

    private String buildQuizPageLink(CurrentAffairsQuiz quiz) {
        return DAILY_QUIZ_URL;
    }

    private String buildSyllabusPageLink(ExamSyllabus syllabus) {
        String slug = buildSyllabusSlug(syllabus.getExamName());
        return trimTrailingSlash(PUBLIC_BASE_URL) + "/syllabus/" + slug + "-" + syllabus.getId();
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

    private String buttonTextForLabel(String label) {
        if (label.contains("admit")) return "Download Admit Card";
        if (label.contains("result")) return "Check Result";
        if (label.contains("answer")) return "Download Answer Key";
        if (label.contains("admission")) return "Apply Now";
        return "Apply Now";
    }

    private void sendTelegramMessage(String text, String buttonText, String buttonUrl) throws Exception {
        String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
        String payload = "{"
                + "\"chat_id\":\"" + escapeJson(CHAT_ID) + "\","
                + "\"text\":\"" + escapeJson(text) + "\","
                + "\"disable_web_page_preview\":true,"
                + "\"reply_markup\":{"
                + "\"inline_keyboard\":[[{"
                + "\"text\":\"" + escapeJson(buttonText) + "\","
                + "\"url\":\"" + escapeJson(buttonUrl) + "\""
                + "}]]"
                + "}"
                + "}";

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, JSON))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new RuntimeException(
                        "Telegram API error: " + response.code() + " | " + response.message() + " | " + errorBody
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

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}
