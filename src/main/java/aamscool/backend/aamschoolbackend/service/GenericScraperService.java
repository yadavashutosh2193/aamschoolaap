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

import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.controllers.ScraperScheduler;
import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.model.Category;
import aamscool.backend.aamschoolbackend.model.JobPosts;
import aamscool.backend.aamschoolbackend.model.Post;
import aamscool.backend.aamschoolbackend.model.ScrapeCache;
import aamscool.backend.aamschoolbackend.util.HomepageScraperService;
import aamscool.backend.aamschoolbackend.util.LabelUtil;

@Service
public class GenericScraperService {

    @Autowired
    private ScrapeCache cache;

    @Autowired
    private MasterJobScraperService masterJobScraperService;

    @Autowired
    private JobsService jobsService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);

    // 🔥 Improved KV Pattern (supports ":" and "-")
    private static final Pattern KV_PATTERN =
            Pattern.compile("^(.+?)\\s*(?:[:\\-]|\\s{2,})\\s*(.+)$");

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
            String label = LabelUtil.extractLabel(url);

            int count = 0;

            for (Element item : items) {

                if (limit > 0 && count >= limit) break;

                Element linkEl = item.selectFirst(linkSelector);
                String link = "";

                if (linkEl != null) {
                    link = linkEl.absUrl("href").trim();
                }

                if (link.isBlank()) {
                    continue;
                }

                if (!cache.isProcessed(link)) {
                    Map<String, Object> result = scrapeAndPersist(link, label);
                    if (!result.isEmpty()) {
                        response.add(result);
                        cache.markProcessed(link);
                    }
                }

                count++;
            }

        } catch (Exception e) {
            log.error("Error in list scrape", e);
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
            String label = LabelUtil.normalizeCategoryLabel(category.getName());

            for (Post post : category.getPosts()) {
                if (post.getUrl() == null || post.getUrl().isBlank()) {
                    continue;
                }

                if (!cache.isProcessed(post.getUrl())) {

                    Map<String, Object> result = scrapeAndPersist(post.getUrl(), label);
                    System.out.println(result);
                    if (!result.isEmpty()) {
                        cache.markProcessed(post.getUrl());
                    }
                }
            }
        }
    }

    /* ============================================================
       =================== SINGLE PAGE SCRAPER ====================
       ============================================================ */

    public Map<String, Object> scrape(String url) {
        try {
            MasterJobResponseDto dto = masterJobScraperService.scrapeToMasterJson(url, true, false);
            return objectMapper.convertValue(dto, Map.class);

        } catch (Exception e) {
            log.error("Single page scrape failed for {}", url, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "Scraping failed");
            return result;
        }
    }

    private Map<String, Object> scrapeAndPersist(String link, String label) {
        try {
            MasterJobResponseDto dto = masterJobScraperService.scrapeToMasterJson(link, true, false);
            String normalizedLabel = LabelUtil.normalizeCategoryLabel(label);
            saveMasterDto(dto, normalizedLabel);
            return objectMapper.convertValue(dto, Map.class);
        } catch (Exception ex) {
            log.error("Failed to scrape/persist {}", link, ex);
            return new LinkedHashMap<>();
        }
    }

    private void saveMasterDto(MasterJobResponseDto dto, String label) throws Exception {
        if (dto == null) {
            return;
        }

        String title = dto.getTitle() != null && !dto.getTitle().isBlank() ? dto.getTitle() : "Untitled Job";
        String adv = dto.getAdvertisementNo();
        String safeLabel = (label == null || label.isBlank()) ? "job" : label;

        JobPosts job = new JobPosts();
        job.setLabel(safeLabel);
        job.setTitle(title);
        job.setAdvertisementNo(adv);
        job.setCreatedAt(LocalDate.now());
        job.setContent(objectMapper.writeValueAsString(dto));
        job.setApproved(false);

        jobsService.savePost(job);
        ScrapeCache.dataCache.invalidate(safeLabel);
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

                // parse table normally
                List<Map<String, Object>> tableData = parseTable(el);

                if (!tableData.isEmpty()) {

                    List<Object> tables = (List<Object>) section
                            .computeIfAbsent("tables", k -> new ArrayList<>());

                    tables.add(tableData);
                }

                // 🔥 NEW: extract important dates from table
                for (Element row : el.select("tr")) {

                    String rowText = clean(row.text());

                    if (looksLikeDate(rowText)) {

                        Map<String,Object> imp =
                                (Map<String,Object>) json.computeIfAbsent("Important Dates",
                                        k -> new LinkedHashMap<>());

                        extractKeyValue(rowText, imp);
                    }
                }

                continue;
            }

            // Links (apply/admit/result)
            if (tag.equals("a")) {

                String href = el.absUrl("href").trim();
                String linkText = clean(el.text());

                if (href.isEmpty())
                    continue;

                if (linkText.isEmpty())
                    linkText = "Link";

                // 🔥 If "Click Here", use parent heading/text as key
                if (linkText.equalsIgnoreCase("click here")
                        || linkText.equalsIgnoreCase("download")
                        || linkText.equalsIgnoreCase("here")) {

                    Element parent = el.parent();

                    if (parent != null) {
                        String parentText = clean(parent.ownText());

                        if (!parentText.isEmpty())
                            linkText = parentText;
                    }
                }

                // 🔥 store direct key → url (no nested structure)
                if (section.containsKey(linkText)) {

                    Object existing = section.get(linkText);

                    if (existing instanceof List) {

                        List<Object> list = (List<Object>) existing;

                        if (!list.isEmpty() && list.get(0) instanceof Map) {

                            Map<String, String> linkMap = (Map<String, String>) list.get(0);
                            int nextIndex = linkMap.size() + 1;
                            linkMap.put("link " + nextIndex, href);

                        }

                    } else if (existing instanceof String) {

                        // Convert single string to required structure
                        Map<String, String> linkMap = new LinkedHashMap<>();
                        linkMap.put("link 1", (String) existing);
                        linkMap.put("link 2", href);

                        List<Object> list = new ArrayList<>();
                        list.add(linkMap);

                        section.put(linkText, list);
                    }

                } else {

                    // First time link found
                    Map<String, String> linkMap = new LinkedHashMap<>();
                    linkMap.put("link 1", href);

                    List<Object> list = new ArrayList<>();
                    list.add(linkMap);

                    section.put(linkText, list);
                }

                continue;
            }

            // Text parsing
         // 🔥 auto-detect important dates anywhere
            if(looksLikeDate(text)){

                Map<String,Object> imp =
                    (Map<String,Object>) json.computeIfAbsent("Important Dates", k-> new LinkedHashMap<>());

                extractKeyValue(text, imp);
            }
        }

        return cleanSections(json);
    }
    
    private boolean looksLikeDate(String text){

        String t = text.toLowerCase();

        return t.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b.*")  // 12/03/2026
                || t.matches(".*\\b\\d{1,2}\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec).*")
                || t.contains("date")
                || t.contains("exam")
                || t.contains("last")
                || t.contains("apply")
                || t.contains("admit");
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
