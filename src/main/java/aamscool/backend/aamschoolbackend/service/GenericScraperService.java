package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;
import aamscool.backend.aamschoolbackend.model.Category;
import aamscool.backend.aamschoolbackend.model.Notification;
import aamscool.backend.aamschoolbackend.model.Post;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.util.HomepageScraperService;
import aamscool.backend.aamschoolbackend.util.OpenAIBatchProcessor;

@Service
public class GenericScraperService {

    @Autowired
    private ScrapeCache cache;

    @Autowired
    OpenAIBatchProcessor openAiBatch;

    private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);

    // 🔥 Improved KV Pattern (supports ":" and "-")
    private static final Pattern KV_PATTERN =
            Pattern.compile("^(.+?)\\s*[:\\-]\\s*(.+)$");

    private static final List<String> BLOCK_WORDS = List.of(
            "telegram", "whatsapp", "download app"
    );

    /* ============================================================
       ================ EXISTING LIST SCRAPER =====================
       ============================================================ */

    public List<Map<String, Object>> scrape(String url,
                                            String itemSelector,
                                            String titleSelector,
                                            String linkSelector,
                                            int limit) {

        List<Map<String, Object>> response = new ArrayList<>();

        if (url == null) return response;

        try {

            Document doc = Jsoup.connect(url).timeout(10000).get();
            Elements items = doc.select(itemSelector);

            int count = 0;

            for (Element item : items) {

                if (count >= limit) break;

                String title = item.select(titleSelector).text().trim();

                Element linkEl = item.selectFirst(linkSelector);
                String link = "";

                if (linkEl != null) {
                    link = linkEl.absUrl("href").trim();
                }

                Notification n = new Notification();
                n.setTitle(title);
                n.setLink(link);
                n.setScrapedDate(LocalDate.now());

                if (!cache.isProcessed(link)) {

                    Map<String, Object> result = scrape(link);
                    response.add(result);
                    cache.markProcessed(link);
                }

                count++;
            }

        } catch (Exception e) {
            log.error("Error in list scrape", e);
        }

        try {
            if (!response.isEmpty()) {
                openAiBatch.processAndUpload(response, url);
            }
        } catch (Exception e) {
            log.error("Error in processAndUpload", e);
        }

        return response;
    }

    /* ============================================================
       ================= HOMEPAGE SCRAPER =========================
       ============================================================ */

    public void scrape() {

        List<Category> scrapedData = new ArrayList<>();
        System.out.println("acrapper started ++++=");
        try {
            scrapedData = HomepageScraperService.scrapeHomepage();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("acrapper started ++++=1111");
        for (Category category : scrapedData) {
        	System.out.println("inside loop acrapper started ++++=");
            List<Map<String, Object>> response = new ArrayList<>();

            for (Post post : category.getPosts()) {

                if (!cache.isProcessed(post.getUrl())) {

                    Map<String, Object> result = scrape(post.getUrl());
                    System.out.println(result);
                    response.add(result);
                    cache.markProcessed(post.getUrl());
                }
            }

            try {
                if (!response.isEmpty()) {
                    openAiBatch.processAndUpload(response, category.getCategoryUrl());
                }
            } catch (Exception e) {
                log.error("Error in batch upload", e);
            }
        }
    }

    /* ============================================================
       =================== SINGLE PAGE SCRAPER ====================
       ============================================================ */

    public Map<String, Object> scrape(String url) {

        Map<String, Object> result = new LinkedHashMap<>();

        try {

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(20000)
                    .get();

            Element main = doc.selectFirst("#post, .entry-content, article, .container");
            if (main == null) main = doc.body();

            result.put("pageTitle", doc.title());
            result.put("url", url);
            result.put("scrapedDate", LocalDate.now().toString());

            result.put("content", parse(main));

        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Scraping failed");
        }

        return result;
    }

    /* ============================================================
       ======================= CORE PARSER ========================
       ============================================================ */

    private Map<String, Object> parse(Element root) {

        Map<String, Object> json = new LinkedHashMap<>();

        String sectionName = "General";
        Map<String, Object> section = new LinkedHashMap<>();
        json.put(sectionName, section);

        for (Element el : root.select("*")) {

            String tag = el.tagName();
            String text = clean(el.text());

            if (isNoise(text)) continue;

            // Headings
            if (tag.matches("h[1-6]")) {

                // ❌ skip bad sections like related/faq
                if (isBadSection(text)) {
                    continue;
                }

                sectionName = text;
                section = new LinkedHashMap<>();
                json.put(sectionName, section);
                continue;
            }

            // Tables (vacancy / important links)
            if (tag.equals("table")) {

                List<Map<String, Object>> tableData = parseTable(el);

                if (!tableData.isEmpty()) {

                    List<Object> tables = (List<Object>) section
                            .computeIfAbsent("tables", k -> new ArrayList<>());

                    tables.add(tableData);
                }

                continue;
            }

            // Links (apply/admit/result)
            if (tag.equals("a")) {

                String href = el.absUrl("href");
                if (href.isEmpty()) continue;

                List<Map<String, String>> links =
                        (List<Map<String, String>>) section
                                .computeIfAbsent("links", k -> new ArrayList<>());

                Map<String, String> linkObj = new LinkedHashMap<>();
                linkObj.put("text", text.isEmpty() ? "Link" : text);
                linkObj.put("url", href);

                links.add(linkObj);
                continue;
            }

            // Text parsing
            if (tag.equals("p") || tag.equals("li")) {
                extractKeyValue(text, section);
            }
        }

        return cleanSections(json);
    }
    
    private boolean isBadSection(String text) {

        if (text == null || text.length() < 4)
            return true;

        String t = text.toLowerCase();

        return t.contains("important question")
                || t.contains("latest job")
                || t.contains("latest post")
                || t.contains("related")
                || t.contains("faq")
                || t.contains("frequently asked")
                || t.contains("result.com.cm")
                || t.contains("click here")
                || t.contains("you may also like");
    }

    /* ============================================================
       =================== ADVANCED TABLE PARSER ==================
       ============================================================ */

    private List<Map<String, Object>> parseTable(Element table) {

        List<Map<String, Object>> tableData = new ArrayList<>();
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return tableData;

        List<String> headers = new ArrayList<>();
        for (Element th : rows.get(0).select("th,td")) {
            headers.add(clean(th.text()));
        }

        for (int i = 1; i < rows.size(); i++) {

            Elements cols = rows.get(i).select("td,th");
            if (cols.isEmpty()) continue;

            Map<String, Object> rowMap = new LinkedHashMap<>();

            for (int j = 0; j < cols.size(); j++) {

                String key = (j < headers.size() && !headers.get(j).isEmpty())
                        ? headers.get(j)
                        : "col_" + j;

                Element col = cols.get(j);
                Element link = col.selectFirst("a[href]");

                if (link != null) {

                    Map<String, String> linkObj = new LinkedHashMap<>();
                    linkObj.put("text", clean(link.text()));
                    linkObj.put("url", link.absUrl("href"));

                    rowMap.put(key, linkObj);

                } else {
                    rowMap.put(key, clean(col.text()));
                }
            }

            tableData.add(rowMap);
        }

        return tableData;
    }

    /* ============================================================
       ================== KEY VALUE EXTRACTION ====================
       ============================================================ */

    private void extractKeyValue(String text, Map<String, Object> section) {

        if (text == null || text.length() < 2) return;

        Matcher m = KV_PATTERN.matcher(text);

        if (m.matches()) {

            String key = clean(m.group(1));
            String val = clean(m.group(2));

            if (!key.isEmpty() && !val.isEmpty()) {

                Object existing = section.get(key);

                if (existing == null) {
                    section.put(key, val);
                } else if (existing instanceof List) {
                    ((List<Object>) existing).add(val);
                } else {
                    List<Object> list = new ArrayList<>();
                    list.add(existing);
                    list.add(val);
                    section.put(key, list);
                }
            }

        } else {

            List<String> list =
                    (List<String>) section.computeIfAbsent("list", k -> new ArrayList<>());

            list.add(text);
        }
    }

    /* ============================================================
       ========================= HELPERS ==========================
       ============================================================ */

    private boolean isNoise(String text) {

        if (text == null) return true;

        String t = text.toLowerCase();

        for (String w : BLOCK_WORDS) {
            if (t.contains(w)) return true;
        }

        return false;
    }

    private String clean(String text) {
        return text.replaceAll("\\s+", " ")
                .replace(":", "")
                .trim();
    }
    
    private Map<String, Object> cleanSections(Map<String, Object> data) {

        Map<String, Object> clean = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : data.entrySet()) {

            if (!(e.getValue() instanceof Map))
                continue;

            Map<String, Object> section = (Map<String, Object>) e.getValue();

            if (section.isEmpty())
                continue;

            // 🔥 THIS is what old code used
            if (shouldDropSection(e.getKey(), section))
                continue;

            clean.put(e.getKey(), section);
        }

        return clean;
    }
    
    private boolean shouldDropSection(String name, Map<String, Object> section) {

        if (name == null) return true;

        String t = name.toLowerCase();

        // 🔥 remove unwanted sections exactly like old code
        if (t.contains("official website")) return true;
        if (t.contains("total post")) return true;
        if (t.contains("question")) return true;
        if (t.contains("faq")) return true;
        if (t.contains("frequently")) return true;
        if (t.contains("related")) return true;
        if (t.contains("latest")) return true;
        if (t.contains("disclaimer")) return true;
        if (t.contains("important question")) return true;
        if (t.contains("you may also like")) return true;

        // 🔥 If section only contains list of random jobs → remove
        if (section.containsKey("list")) {

            List<?> list = (List<?>) section.get("list");

            long jobLinks = list.stream()
                    .filter(o -> o.toString().contains("202"))
                    .count();

            if (jobLinks > 5) return true;
        }

        return false;
    }
}