package aamscool.backend.aamschoolbackend.controllers;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import aamscool.backend.aamschoolbackend.model.HomePageLinksModel;
import aamscool.backend.aamschoolbackend.repository.JobsRepository;
import aamscool.backend.aamschoolbackend.repository.QuizRepository;

@RestController
public class SitemapController {

    @Autowired
    JobsRepository jobDao;
    @Autowired
    QuizRepository quizRepository;

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public String generateSitemap() {

        String baseUrl = "https://www.aamschool.in";

        List<HomePageLinksModel> jobs = jobDao.findAllJobTitlesAndIds();
        if (jobs == null) jobs = List.of();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

        // formatter for lastmod
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // ⭐ Homepage
        xml.append("<url>");
        xml.append("<loc>").append(baseUrl).append("</loc>");
        xml.append("<changefreq>hourly</changefreq>");
        xml.append("<priority>1.0</priority>");
        xml.append("</url>");

        // ⭐ Main sections
        addStaticUrl(xml, baseUrl + "/latest-jobs", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/admit-card", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/results", "hourly", "0.9");
        addStaticUrl(xml, baseUrl + "/answer-key", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/syllabus", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/admission", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/quiz-zone", "daily", "0.8");
        addStaticUrl(xml, baseUrl + "/daily-current-affairs-quiz", "daily", "0.8");
        addTopicQuizListingUrls(xml, baseUrl);

        // ⭐ Job detail pages
        for (HomePageLinksModel job : jobs) {

            String slug = toSlug(job.getTitle());
            Long id = job.getId();

            xml.append("<url>");
            xml.append("<loc>")
               .append(baseUrl)
               .append("/job/")
               .append(slug)
               .append("-")
               .append(id)
               .append("</loc>");

            // lastmod from DB created date
            if (job.getPostDate() != null) {
                xml.append("<lastmod>")
                   .append(job.getPostDate().format(formatter))
                   .append("</lastmod>");
            }

            xml.append("<changefreq>daily</changefreq>");
            xml.append("<priority>0.8</priority>");
            xml.append("</url>");
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    // helper for static urls
    private void addStaticUrl(StringBuilder xml, String url, String freq, String priority) {
        xml.append("<url>");
        xml.append("<loc>").append(url).append("</loc>");
        xml.append("<changefreq>").append(freq).append("</changefreq>");
        xml.append("<priority>").append(priority).append("</priority>");
        xml.append("</url>");
    }

    private void addTopicQuizListingUrls(StringBuilder xml, String baseUrl) {
        List<Object[]> subjectTopicPairs = quizRepository.findDistinctSubjectTopicPairs();
        if (subjectTopicPairs == null || subjectTopicPairs.isEmpty()) {
            return;
        }
        Set<String> emittedUrls = new LinkedHashSet<>();
        for (Object[] pair : subjectTopicPairs) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            String subject = pair[0] == null ? "" : pair[0].toString().trim();
            String topic = pair[1] == null ? "" : pair[1].toString().trim();
            if (subject.isBlank() || topic.isBlank()) {
                continue;
            }
            String encodedSubject = encodePathComponent(subject);
            String encodedTopic = encodePathComponent(topic);
            String url = baseUrl + "/quiz-zone/" + encodedSubject + "/" + encodedTopic + "/quizzes";
            if (emittedUrls.add(url)) {
                addStaticUrl(xml, url, "daily", "0.8");
            }
        }
    }

    // Mirrors frontend encodeURIComponent behavior used in route builders.
    private String encodePathComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~")
                .replace("%2A", "*");
    }

    // slug generator
    private String toSlug(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    @GetMapping(value = "/robots.txt", produces = "text/plain")
    public String robots() {
        return "User-agent: *\nAllow: /\nSitemap: https://www.aamschool.in/sitemap.xml";
    }
}

