package aamscool.backend.aamschoolbackend.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aamscool.backend.aamschoolbackend.dto.MasterJobResponseDto;
import aamscool.backend.aamschoolbackend.util.SarkariLinkCleaner;

@Service
public class MasterJobScraperService {

    private static final Pattern KEY_VALUE = Pattern.compile("^(.+?)\\s*[:\\-]\\s*(.+)$");
    private static final Pattern ADVERTISEMENT = Pattern.compile("(?i)\\b(CEN|Advt\\.?|Advertisement|Notification|Notice)\\s*(No\\.?|Number)?\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9./\\-]{1,30})");
    private static final Pattern ADVT_TOKEN = Pattern.compile("(?i)\\b(?:CEN\\s*[0-9A-Za-z./\\-]{2,30}|(?:Advt\\.?|Advertisement|Notification|Notice)\\s*(?:No\\.?|Number)?\\s*[:\\-]?\\s*[0-9A-Za-z./\\-]{2,30}|[A-Z]{2,8}[\\-_/][0-9]{2,4}[\\-_/][0-9]{2,4}|[0-9]{1,3}/[0-9]{4})\\b");
    private static final Pattern DATE_PATTERN = Pattern.compile("(?i)(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})");
    private static final Pattern POSTS_PATTERN = Pattern.compile("(?i)(\\d[\\d,]{1,8})\\s*(posts?|vacanc(?:y|ies)|seats?)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:Rs\\.?|INR|\\u20b9)\\s*([0-9][0-9,]{1,7})");
    private static final Pattern CATEGORY_TOKEN_PATTERN = Pattern.compile("(?i)\\b(ur|obc|sc|st|ews|pwd)\\b");
    private static final Pattern GENERAL_FEE_TOKEN_PATTERN = Pattern.compile("(?i)\\b(general|obc|ews)\\b");
    private static final Pattern RESERVED_FEE_TOKEN_PATTERN = Pattern.compile("(?i)\\b(sc|st|female|transgender|ebc|pwd|ph|esm)\\b");
    private static final List<String> BLOCKED_TEXT = List.of(
            "latest posts", "related posts", "important question", "frequently asked",
            "faq", "join now", "follow now", "youtube", "telegram", "whatsapp",
            "download app", "you may also like", "you may also check", "latest jobs"
    );
    private static final List<String> BLOCKED_LINK_DOMAINS = List.of(
            "play.google.com", "youtube.com", "t.me", "telegram", "whatsapp.com",
            "facebook.com", "instagram.com"
    );
    private static final List<String> AD_SENSE_UNSAFE_PHRASES = List.of(
            "guaranteed selection",
            "100% selection",
            "100% guarantee",
            "hurry up",
            "last chance",
            "urgent vacancy",
            "instant joining",
            "apply now!!!",
            "don't miss"
    );
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public MasterJobScraperService(OpenAIService openAIService, ObjectMapper objectMapper) {
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
    }

    public MasterJobResponseDto scrapeToMasterJson(String url) throws IOException {
        return scrapeToMasterJson(url, true, false);
    }

    public MasterJobResponseDto scrapeToMasterJson(String url, boolean aiEnhance) throws IOException {
        return scrapeToMasterJson(url, aiEnhance, false);
    }

    public MasterJobResponseDto scrapeToMasterJson(String url, boolean aiEnhance, boolean includeLegacyPostWise) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(30000)
                .get();

        Element main = doc.selectFirst("#post, .entry-content, article, .container");
        if (main == null) {
            main = doc.body();
        }

        MasterJobResponseDto dto = new MasterJobResponseDto();
        dto.setSource(url);

        Map<String, String> kvLines = collectKeyValueLines(main);
        List<String> textLines = collectTextLines(main);

        fillTitle(doc, main, dto);
        fillShortDescription(textLines, dto);
        fillAdvertisementNo(textLines, kvLines, dto);
        fillPostAndBody(dto, textLines, kvLines);
        fillImportantDates(main, kvLines, textLines, dto);
        fillPostedUpdatedAndLocation(kvLines, textLines, dto);
        fillApplicationFee(main, kvLines, textLines, dto);
        fillEligibility(main, kvLines, textLines, dto);
        fillVacancy(main, kvLines, textLines, dto);
        fillPayScale(textLines, kvLines, dto);
        fillApplicationProcess(main, textLines, dto);
        fillExamSchemeAndSelection(main, textLines, dto);
        fillImportantNotes(textLines, dto);
        fillOfficialLinks(main, dto);
        fillOtherTables(main, dto);
        fillSyllabus(textLines, dto);
        finalizeResponse(dto);
        if (aiEnhance) {
            enhanceWithAi(dto);
        }
        finalizeResponse(dto);
        applySeoAndAdsensePolish(dto);
        applyVacancyResponseShape(dto, includeLegacyPostWise);

        return dto;
    }

    private void applyVacancyResponseShape(MasterJobResponseDto dto, boolean includeLegacyPostWise) {
        if (dto == null || dto.getVacancyDetails() == null) {
            return;
        }
        MasterJobResponseDto.VacancyDetailsDto vacancy = dto.getVacancyDetails();
        boolean hasTableRows = vacancy.getTableRows() != null && !vacancy.getTableRows().isEmpty();
        if (!includeLegacyPostWise && hasTableRows) {
            vacancy.setPostWise(new LinkedHashMap<>());
        }
    }

    private void fillTitle(Document doc, Element main, MasterJobResponseDto dto) {
        String h1 = clean(main.select("h1").stream().findFirst().map(Element::text).orElse(""));
        dto.setTitle(h1.isBlank() ? clean(doc.title()) : h1);
    }

    private void fillShortDescription(List<String> lines, MasterJobResponseDto dto) {
        for (String line : lines) {
            if (line.length() > 80) {
                dto.setShortDescription(line);
                return;
            }
        }
    }

    private void fillAdvertisementNo(List<String> lines, Map<String, String> kvLines, MasterJobResponseDto dto) {
        String extracted = null;
        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String k = lower(entry.getKey());
            if (k.contains("advertisement") || k.contains("advt") || k.contains("notification no") || k.contains("cen")) {
                extracted = extractAdvtNo(entry.getKey() + " " + entry.getValue());
                if (extracted != null) {
                    dto.setAdvertisementNo(extracted);
                    return;
                }
            }
        }

        for (String line : lines) {
            Matcher matcher = ADVERTISEMENT.matcher(line);
            if (matcher.find()) {
                extracted = clean(matcher.group(3));
                if (looksLikeValidAdvt(extracted)) {
                    dto.setAdvertisementNo(extracted);
                    return;
                }
            }
        }

        if (dto.getAdvertisementNo() == null) {
            for (String line : lines) {
                String adv = extractAdvtNo(line);
                if (looksLikeValidAdvt(adv)) {
                    dto.setAdvertisementNo(adv);
                    return;
                }
            }
        }
    }

    private void fillPostAndBody(MasterJobResponseDto dto, List<String> lines, Map<String, String> kvLines) {
        String title = safe(dto.getTitle());
        String cleaned = title
                .replaceAll("(?i)online form|admit card|result|notification|recruitment", "")
                .replaceAll("\\s+", " ")
                .trim();
        dto.setPostName(cleaned.isBlank() ? null : cleaned);
        dto.setConductingBody(extractConductingBody(title, lines, kvLines));
    }

    private void fillPostedUpdatedAndLocation(Map<String, String> kvLines, List<String> lines, MasterJobResponseDto dto) {
        String posted = dto.getDatePosted();
        if (!hasText(posted)) {
            for (Map.Entry<String, String> entry : kvLines.entrySet()) {
                String key = lower(entry.getKey());
                String value = clean(entry.getValue());
                if (!containsAny(key, "posted", "post date", "date posted", "published", "notification date")) {
                    continue;
                }
                if (looksLikeDateValue(value)) {
                    posted = value;
                    break;
                }
            }
        }
        if (!hasText(posted)) {
            posted = getImportantDateString(dto, "notification date", "posted on", "post date", "date posted");
        }
        String today = LocalDate.now().toString();
        dto.setDateUpdated(hasText(dto.getDateUpdated()) ? dto.getDateUpdated() : today);
        dto.setDatePosted(hasText(posted) ? posted : dto.getDateUpdated());

        Map<String, Object> location = new LinkedHashMap<>();
        if (dto.getJobLocation() != null && !dto.getJobLocation().isEmpty()) {
            location.putAll(dto.getJobLocation());
        }

        String locationText = null;
        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String key = lower(entry.getKey());
            if (!containsAny(key, "location", "job location", "posting place", "place of posting", "work location")) {
                continue;
            }
            String value = clean(entry.getValue());
            if (!value.isBlank()) {
                locationText = value;
                break;
            }
        }

        if (!hasText(locationText)) {
            for (String line : lines) {
                String l = lower(line);
                if (containsAny(l, "all india", "across india", "pan india", "multiple locations")) {
                    locationText = "All India";
                    break;
                }
            }
        }

        if (hasText(locationText)) {
            location.put("location_text", locationText);
            String normalized = lower(locationText);
            if (containsAny(normalized, "all india", "across india", "pan india", "multiple locations")) {
                location.putIfAbsent("country", "India");
                location.putIfAbsent("states", List.of("All India"));
                location.putIfAbsent("city", "Multiple");
            }
        }

        if (!location.isEmpty()) {
            dto.setJobLocation(location);
        }
    }

    private void fillImportantDates(Element main, Map<String, String> kvLines, List<String> lines, MasterJobResponseDto dto) {
        Map<String, String> dates = new LinkedHashMap<>();

        // Priority 1: parse under heading "Important Dates" block.
        extractImportantDatesFromHeading(main, dates);

        // Priority 2: fallback from generic key-value lines.
        if (dates.isEmpty()) {
            for (Map.Entry<String, String> entry : kvLines.entrySet()) {
                String rawKey = clean(entry.getKey());
                String rawVal = clean(entry.getValue());
                String key = lower(rawKey);
                if (rawVal.isBlank() || rawVal.equalsIgnoreCase("click here")) continue;
                if (containsAny(key, "date", "apply", "exam", "admit", "result", "correction", "fee payment")) {
                    dates.putIfAbsent(normalizeDateKey(rawKey), rawVal);
                }
            }
        }

        // Priority 3: fallback from text lines (detect "key : value" date lines).
        if (dates.isEmpty()) {
            for (String line : lines) {
                String[] kv = splitKeyValue(line);
                if (kv == null) continue;
                String k = lower(kv[0]);
                if (containsAny(k, "date", "apply", "exam", "admit", "result", "correction", "fee payment")) {
                    dates.putIfAbsent(normalizeDateKey(kv[0]), clean(kv[1]));
                }
            }
        }

        dto.setImportantDates(dates);
    }

    private void extractImportantDatesFromHeading(Element main, Map<String, String> out) {
        for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
            String ht = lower(clean(h.text()));
            if (!ht.contains("important dates")) continue;

            Element sib = h.nextElementSibling();
            while (sib != null && !sib.tagName().matches("h[1-6]")) {
                for (Element li : sib.select("li")) {
                    addDateLine(li.text(), out);
                }
                String own = clean(sib.ownText());
                if (!own.isBlank()) addDateLine(own, out);
                sib = sib.nextElementSibling();
            }

            if (!out.isEmpty()) return;
        }
    }

    private void addDateLine(String text, Map<String, String> out) {
        String line = clean(text);
        if (line.isBlank()) return;
        if (lower(line).contains("candidates are advised")) return;

        String[] kv = splitKeyValue(line);
        if (kv != null) {
            String key = normalizeDateKey(kv[0]);
            String val = clean(kv[1]);
            if (!key.isBlank() && !val.isBlank() && !val.equalsIgnoreCase("click here")) {
                out.putIfAbsent(key, val);
            }
            return;
        }

        Matcher m = DATE_PATTERN.matcher(line);
        if (m.find()) {
            out.putIfAbsent(normalizeDateKey("date"), line);
        }
    }

    private String normalizeDateKey(String key) {
        return clean(key)
                .replaceAll("[:\\-]+$", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private void fillApplicationFee(Element main, Map<String, String> kvLines, List<String> lines, MasterJobResponseDto dto) {
        MasterJobResponseDto.ApplicationFeeDto fee = dto.getApplicationFee();

        List<String> feeSectionLines = extractSectionLinesByHeading(main, "application fee", "application fees", "fee details");
        if (!feeSectionLines.isEmpty()) {
            buildFeeSlabRows(feeSectionLines, fee);
            for (String line : feeSectionLines) {
                applyFeeFromLine(line, fee);
            }
        }

        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String key = lower(entry.getKey());
            String val = clean(entry.getValue());

            if (!containsAny(key, "fee", "payment", "refund")) {
                continue;
            }
            if (looksLikeDateValue(val)) {
                continue;
            }

            if (hasGeneralFeeToken(key) && !key.contains("refund")) {
                fee.setGeneralObc(pickHigherMoney(fee.getGeneralObc(), extractMoney(val)));
            }
            if (hasReservedFeeToken(key) && !key.contains("refund")) {
                fee.setScStEbcFemaleTransgender(pickHigherMoney(fee.getScStEbcFemaleTransgender(), extractMoney(val)));
            }
            if (containsAny(key, "refund") && hasGeneralFeeToken(key)) {
                fee.setRefundGeneralObcAfterCbt(pickHigherMoney(fee.getRefundGeneralObcAfterCbt(), extractMoney(val)));
            }
            if (containsAny(key, "refund") && hasReservedFeeToken(key)) {
                fee.setRefundScStEbcFemaleTransgenderAfterCbt(pickHigherMoney(fee.getRefundScStEbcFemaleTransgenderAfterCbt(), extractMoney(val)));
            }
        }

        Set<String> modes = new LinkedHashSet<>();
        for (String line : lines) {
            String l = lower(line);
            if (looksLikeDateValue(line)) {
                continue;
            }
            if (containsAny(l, "debit card", "credit card", "internet banking", "upi", "wallet", "net banking", "imps")) {
                if (l.contains("debit")) modes.add("Debit Card");
                if (l.contains("credit")) modes.add("Credit Card");
                if (l.contains("internet banking") || l.contains("net banking")) modes.add("Internet Banking");
                if (l.contains("upi")) modes.add("UPI");
                if (l.contains("imps")) modes.add("IMPS");
                if (l.contains("cash card")) modes.add("Cash Card");
                if (l.contains("wallet")) modes.add("Mobile Wallet");
            }

            if (hasGeneralFeeToken(l) && containsAny(l, "fee", "rs", "inr", "/-")) {
                if (!l.contains("refund")) {
                    fee.setGeneralObc(pickHigherMoney(fee.getGeneralObc(), extractMoney(line)));
                }
            }
            if (hasReservedFeeToken(l) && containsAny(l, "fee", "rs", "inr", "/-")) {
                if (!l.contains("refund")) {
                    fee.setScStEbcFemaleTransgender(pickHigherMoney(fee.getScStEbcFemaleTransgender(), extractMoney(line)));
                }
            }
            if (containsAny(l, "refund", "after cbt") && hasGeneralFeeToken(l)) {
                fee.setRefundGeneralObcAfterCbt(pickHigherMoney(fee.getRefundGeneralObcAfterCbt(), extractMoney(line)));
            }
            if (containsAny(l, "refund", "after cbt") && hasReservedFeeToken(l)) {
                fee.setRefundScStEbcFemaleTransgenderAfterCbt(pickHigherMoney(fee.getRefundScStEbcFemaleTransgenderAfterCbt(), extractMoney(line)));
            }
        }

        if (fee.getSlabRows() != null && !fee.getSlabRows().isEmpty()) {
            fee.setGeneralObc(null);
            fee.setScStEbcFemaleTransgender(null);
        }
        fee.setPaymentMode(new ArrayList<>(modes));
    }

    private void buildFeeSlabRows(List<String> feeSectionLines, MasterJobResponseDto.ApplicationFeeDto fee) {
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> current = null;

        for (String raw : feeSectionLines) {
            String line = clean(raw);
            String l = lower(line);
            if (line.isBlank()) {
                continue;
            }
            if (containsAny(l, "payment mode", "debit card", "credit card", "internet banking", "net banking", "upi", "imps", "wallet", "cash card")) {
                continue;
            }

            String amount = extractMoney(line);
            String[] kv = splitKeyValue(line);

            if (amount == null && isFeeSlabHeadingLine(line)) {
                current = new LinkedHashMap<>();
                current.put("applicable_for", normalizeFeeSlabHeading(line));
                rows.add(current);
                continue;
            }

            if (amount != null) {
                if (current == null) {
                    current = new LinkedHashMap<>();
                    current.put("applicable_for", "all_posts");
                    rows.add(current);
                }
                String label = kv != null ? kv[0] : line;
                String key = feeBandKey(label);
                if (!key.isBlank()) {
                    current.put(key, amount);
                }
            }
        }

        rows.removeIf(r -> r.size() <= 1);
        if (!rows.isEmpty()) {
            fee.setSlabRows(rows);
        }
    }

    private boolean isFeeSlabHeadingLine(String line) {
        String l = lower(line);
        if (containsAny(l, "payment mode", "refund")) {
            return false;
        }
        if (extractMoney(line) != null) {
            return false;
        }
        return line.endsWith(":-")
                || line.endsWith(":")
                || (containsAny(l, "assistant", "principal", "vice principal", "pgt", "tgt", "prt", "stenographer", "lab attendant", "multi-tasking", "librarian", "translator")
                && line.length() >= 10 && line.length() <= 220);
    }

    private String normalizeFeeSlabHeading(String line) {
        return clean(line)
                .replaceAll("[:\\-]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String feeBandKey(String label) {
        String l = lower(label);
        if (containsAny(l, "refund") && hasGeneralFeeToken(l)) {
            return "refund_general_obc_after_cbt";
        }
        if (containsAny(l, "refund") && hasReservedFeeToken(l)) {
            return "refund_sc_st_reserved_after_cbt";
        }
        if (hasGeneralFeeToken(l)) {
            return "general_obc_ews";
        }
        if (hasReservedFeeToken(l)) {
            return "sc_st_ph_esm_or_reserved";
        }
        String normalized = normalizeLabel(label);
        return normalized.isBlank() ? "fee" : normalized;
    }

    private void applyFeeFromLine(String line, MasterJobResponseDto.ApplicationFeeDto fee) {
        String l = lower(line);
        if (line.isBlank() || looksLikeDateValue(line)) {
            return;
        }
        if (hasGeneralFeeToken(l) && !containsAny(l, "refund")) {
            fee.setGeneralObc(pickHigherMoney(fee.getGeneralObc(), extractMoney(line)));
        }
        if (hasReservedFeeToken(l) && !containsAny(l, "refund")) {
            fee.setScStEbcFemaleTransgender(pickHigherMoney(fee.getScStEbcFemaleTransgender(), extractMoney(line)));
        }
        if (containsAny(l, "refund") && hasGeneralFeeToken(l)) {
            fee.setRefundGeneralObcAfterCbt(pickHigherMoney(fee.getRefundGeneralObcAfterCbt(), extractMoney(line)));
        }
        if (containsAny(l, "refund") && hasReservedFeeToken(l)) {
            fee.setRefundScStEbcFemaleTransgenderAfterCbt(
                    pickHigherMoney(fee.getRefundScStEbcFemaleTransgenderAfterCbt(), extractMoney(line)));
        }
    }

    private void fillEligibility(Element main, Map<String, String> kvLines, List<String> lines, MasterJobResponseDto dto) {
        MasterJobResponseDto.EligibilityCriteriaDto eligibility = dto.getEligibilityCriteria();
        QualificationExtractionResult structured = extractQualificationFromStructuredBlocks(main);
        if (structured.summary != null) {
            eligibility.setQualification(structured.summary);
        }
        if (!structured.postWise.isEmpty()) {
            eligibility.setPostWiseQualification(structured.postWise);
        }

        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String key = lower(entry.getKey());
            String val = clean(entry.getValue());

            if (containsAny(key, "minimum age", "min age")) {
                eligibility.setMinimumAge(extractAgeValue(val, true));
            }
            if (containsAny(key, "maximum age", "max age")) {
                eligibility.setMaximumAge(extractAgeValue(val, false));
            }
            if (containsAny(key, "age as on")) {
                String asOn = extractAsOnDate(val);
                eligibility.setAgeAsOn(asOn != null ? asOn : val);
            }
            if (containsAny(key, "qualification", "eligibility", "education")) {
                if (eligibility.getQualification() == null && isStrongQualificationCandidate(val)) {
                    eligibility.setQualification(val);
                }
            }
        }

        for (String line : lines) {
            String l = lower(line);
            if (eligibility.getQualification() == null
                    && containsAny(l, "10th", "12th", "graduate", "diploma", "iti", "degree", "nac", "ncvt", "scvt", "eligible")
                    && !containsAny(l, "age relaxation", "minimum age", "maximum age")) {
                if (line.length() <= 220
                        && !containsAny(l, "recruitment", "application form has started", "latest posts", "related posts", "marksheet", "document", "proof")
                        && isStrongQualificationCandidate(line)) {
                    eligibility.setQualification(line);
                }
            }
            if (eligibility.getMinimumAge() == null && l.contains("minimum age")) {
                eligibility.setMinimumAge(extractAgeValue(line, true));
            }
            if (eligibility.getMaximumAge() == null && l.contains("maximum age")) {
                eligibility.setMaximumAge(extractAgeValue(line, false));
            }
            if (eligibility.getAgeAsOn() == null && (l.contains("age as on") || l.contains("as on"))) {
                String asOn = extractAsOnDate(line);
                if (asOn != null) {
                    eligibility.setAgeAsOn(asOn);
                } else {
                    Matcher m = DATE_PATTERN.matcher(line);
                    if (m.find()) {
                        eligibility.setAgeAsOn(m.group(1));
                    }
                }
            }
        }

        if (eligibility.getAgeAsOn() == null) {
            for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
                String t = clean(h.text());
                String lt = lower(t);
                if (!lt.contains("as on")) {
                    continue;
                }
                String asOn = extractAsOnDate(t);
                if (asOn != null) {
                    eligibility.setAgeAsOn(asOn);
                    break;
                }
            }
        }

        if (eligibility.getQualification() != null && !isStrongQualificationCandidate(eligibility.getQualification())) {
            eligibility.setQualification(null);
        }
    }

    private QualificationExtractionResult extractQualificationFromStructuredBlocks(Element main) {
        Set<String> candidates = new LinkedHashSet<>();
        Map<String, String> postWise = new LinkedHashMap<>();

        for (Element table : main.select("table")) {
            List<List<String>> rows = new ArrayList<>();
            for (Element tr : table.select("tr")) {
                List<String> cells = tr.select("th,td").eachText().stream().map(this::clean).toList();
                if (cells.size() >= 2) {
                    rows.add(cells);
                }
            }
            if (rows.isEmpty()) continue;

            int headerRow = -1;
            int qualCol = -1;
            int labelCol = -1;
            for (int i = 0; i < rows.size(); i++) {
                List<String> cells = rows.get(i);
                for (int c = 0; c < cells.size(); c++) {
                    String h = lower(cells.get(c));
                    if (qualCol < 0 && containsAny(h, "eligibility", "qualification", "education", "educational")) {
                        qualCol = c;
                        headerRow = i;
                    }
                    if (labelCol < 0 && containsAny(h, "post", "name", "course", "trade", "stream")) {
                        labelCol = c;
                    }
                }
                if (qualCol >= 0) break;
            }

            if (qualCol < 0) continue;
            if (labelCol < 0) labelCol = 0;

            for (int i = headerRow + 1; i < rows.size(); i++) {
                List<String> cells = rows.get(i);
                if (cells.size() <= qualCol) continue;
                String qual = clean(cells.get(qualCol));
                if (!isStrongQualificationCandidate(qual)) continue;
                String label = cells.size() > labelCol ? clean(cells.get(labelCol)) : "";
                if (!label.isBlank() && !isBlockedText(label) && !looksLikeFooterLabel(label)) {
                    postWise.putIfAbsent(label, qual);
                    candidates.add(label + ": " + qual);
                } else {
                    candidates.add(qual);
                }
            }
        }

        for (Element table : main.select("table")) {
            ParsedTable parsed = parseDynamicTable(table);
            if (parsed == null) continue;

            int qualCol = findHeaderIndex(parsed.headerKeys, "eligibility", "qualification", "education", "educational");
            if (qualCol < 0) continue;
            int labelCol = findHeaderIndex(parsed.headerKeys, "post", "name", "position", "course", "trade");

            for (Map<String, String> row : parsed.rows) {
                String qual = clean(getColumnValue(row, parsed.headerKeys, qualCol));
                if (!isStrongQualificationCandidate(qual)) {
                    continue;
                }
                String label = labelCol >= 0 ? clean(getColumnValue(row, parsed.headerKeys, labelCol)) : "";
                if (!label.isBlank() && !isBlockedText(label) && !looksLikeFooterLabel(label)) {
                    postWise.putIfAbsent(label, qual);
                    candidates.add(label + ": " + qual);
                } else {
                    candidates.add(qual);
                }
            }
        }

        for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
            String ht = lower(clean(h.text()));
            if (!containsAny(ht, "eligibility", "qualification", "education")) {
                continue;
            }
            Element sib = h.nextElementSibling();
            int hops = 0;
            while (sib != null && !sib.tagName().matches("h[1-6]") && hops < 4) {
                for (Element li : sib.select("li")) {
                    String line = clean(li.text());
                    if (isStrongQualificationCandidate(line)) {
                        candidates.add(line);
                    }
                }
                hops++;
                sib = sib.nextElementSibling();
            }
        }

        if (candidates.isEmpty()) {
            return new QualificationExtractionResult(null, postWise);
        }
        for (String c : candidates) {
            if (c.length() <= 320) {
                return new QualificationExtractionResult(c, postWise);
            }
        }
        return new QualificationExtractionResult(candidates.iterator().next(), postWise);
    }

    private boolean isStrongQualificationCandidate(String text) {
        if (text == null) return false;
        String t = clean(text);
        String l = lower(t);
        if (t.length() < 8 || t.length() > 420) return false;
        if (isBlockedText(t) || looksLikeFooterLabel(t)) return false;
        if (containsAny(l, "click here")) return false;
        if (l.matches(".*\\b(read|check|refer)\\b.*\\bnotification\\b.*") && !hasEducationEvidence(l)) return false;
        if (isLikelyPostCountNoise(t)) return false;
        return hasEducationEvidence(l);
    }

    private boolean hasEducationEvidence(String lowerText) {
        return containsAny(lowerText,
                "class 8", "class 10", "class 12", "10th", "12th", "matric", "intermediate",
                "graduate", "graduation", "bachelor", "master", "post graduate", "degree",
                "diploma", "iti", "ncvt", "scvt", "pass", "passed", "engineering", "wpm");
    }

    private boolean isLikelyPostCountNoise(String text) {
        String t = clean(text);
        return t.matches("^[A-Za-z .,&()\\-'/]{3,120}\\s+[0-9]{2,6}$")
                || t.matches("^[0-9]{2,6}$");
    }

    private boolean looksLikeFooterLabel(String text) {
        String l = lower(text);
        return containsAny(l, "check zone wise", "check post wise", "click here", "download");
    }

    private void fillVacancy(Element main, Map<String, String> kvLines, List<String> lines, MasterJobResponseDto dto) {
        MasterJobResponseDto.VacancyDetailsDto vacancy = dto.getVacancyDetails();
        int bestTotal = -1;
        int parsedPostCountTotal = 0;
        List<Map<String, String>> parsedRows = new ArrayList<>();

        for (String line : lines) {
            Matcher m = POSTS_PATTERN.matcher(line);
            if (m.find()) {
                int n = toInt(m.group(1));
                if (n > bestTotal) {
                    bestTotal = n;
                }
            }
        }

        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String key = lower(entry.getKey());
            if ((key.contains("total") && key.contains("vacancy")) || key.contains("total post")) {
                int n = toInt(extractFirstNumber(entry.getValue()));
                if (n > bestTotal) {
                    bestTotal = n;
                }
            }
        }
        if (bestTotal > 0) {
            vacancy.setTotalVacancy(String.valueOf(bestTotal));
        }

        Elements tables = main.select("table");
        for (Element table : tables) {
            ParsedTable parsed = parseDynamicTable(table);
            if (parsed == null || !looksLikeVacancyTable(parsed, table)) {
                continue;
            }

            parsedRows.addAll(parsed.rows);

            int postNameIndex = findHeaderIndex(parsed.headerKeys, "post", "position", "trade", "name");
            int explicitCountIndex = findHeaderIndex(parsed.headerKeys, "no_of_post", "no_of_posts", "vacancy", "vacancies", "seat", "seats", "total_post");
            int countIndex = explicitCountIndex;
            if (countIndex < 0) {
                countIndex = findLikelyCountColumn(parsed);
            }
            if (postNameIndex < 0) {
                postNameIndex = findLikelyLabelColumn(parsed);
            }

            if (postNameIndex >= 0 && countIndex >= 0 && !(explicitCountIndex < 0 && hasMultipleCategoryColumns(parsed))) {
                for (Map<String, String> row : parsed.rows) {
                    String postName = clean(getColumnValue(row, parsed.headerKeys, postNameIndex));
                    String count = extractFirstNumber(getColumnValue(row, parsed.headerKeys, countIndex));
                    if (postName.isBlank()
                            || count == null
                            || isBlockedText(postName)
                            || looksLikeLinkJunk(postName)
                            || postName.length() > 140) {
                        continue;
                    }
                    vacancy.getPostWise().putIfAbsent(postName, count);
                    parsedPostCountTotal += toInt(count);
                }
            }

            populateCategoryWise(vacancy, parsed);
        }

        vacancy.setTableRows(parsedRows);

        if (parsedPostCountTotal > 0) {
            vacancy.setTotalVacancy(String.valueOf(parsedPostCountTotal));
        }
        if (vacancy.getTotalVacancy() == null && !vacancy.getCategoryWise().isEmpty()) {
            int categorySum = 0;
            for (String value : vacancy.getCategoryWise().values()) {
                int n = toInt(value);
                if (n > 0) {
                    categorySum += n;
                }
            }
            if (categorySum > 0) {
                vacancy.setTotalVacancy(String.valueOf(categorySum));
            }
        }

        if (vacancy.getPostWise().isEmpty() && vacancy.getCategoryWise().isEmpty() && vacancy.getTableRows().isEmpty()) {
            vacancy.setTotalVacancy(null);
        }
    }

    private ParsedTable parseDynamicTable(Element table) {
        return parseDynamicTable(table, true);
    }

    private ParsedTable parseDynamicTable(Element table, boolean strictMode) {
        List<List<String>> rows = expandTableRows(table);
        if (rows.isEmpty()) {
            return null;
        }

        int rowStart = 0;
        while (rowStart < rows.size() && isTitleLikeRow(rows.get(rowStart))) {
            rowStart++;
        }
        if (rowStart >= rows.size()) {
            return null;
        }

        List<List<String>> candidates = rows.subList(rowStart, rows.size());
        int firstDataIndex = findFirstDataRow(candidates);
        int bestHeaderRelative = findBestHeaderRow(candidates);
        int headerIndex = firstDataIndex > 0
                ? rowStart + firstDataIndex - 1
                : (bestHeaderRelative >= 0 ? rowStart + bestHeaderRelative : -1);
        if (headerIndex < 0) {
            return null;
        }

        List<String> headers = composeHeaders(rows, headerIndex);
        List<String> headerKeys = normalizeHeaderKeys(headers);
        int expectedCols = headerKeys.size();
        if (expectedCols < 2) {
            return null;
        }

        List<Map<String, String>> mappedRows = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> cells = resizeRow(rows.get(i), expectedCols);
            if (strictMode && (looksLikeHeaderRow(cells) || looksLikeFooterRow(cells))) {
                continue;
            }

            Map<String, String> rowMap = new LinkedHashMap<>();
            boolean hasAnyValue = false;
            for (int c = 0; c < expectedCols; c++) {
                String value = clean(cells.get(c));
                rowMap.put(headerKeys.get(c), value);
                if (!value.isBlank()) {
                    hasAnyValue = true;
                }
            }
            if (hasAnyValue) {
                mappedRows.add(rowMap);
            }
        }

        if (mappedRows.isEmpty()) {
            return null;
        }
        return new ParsedTable(headers, headerKeys, mappedRows);
    }

    private List<String> resizeRow(List<String> cells, int expectedCols) {
        List<String> out = new ArrayList<>();
        if (expectedCols <= 0) {
            return out;
        }
        if (cells == null) {
            for (int i = 0; i < expectedCols; i++) out.add("");
            return out;
        }
        for (int i = 0; i < Math.min(cells.size(), expectedCols); i++) {
            out.add(clean(cells.get(i)));
        }
        while (out.size() < expectedCols) {
            out.add("");
        }
        if (cells.size() > expectedCols) {
            String overflow = String.join(" ", cells.subList(expectedCols - 1, cells.size()));
            out.set(expectedCols - 1, clean(out.get(expectedCols - 1) + " " + overflow));
        }
        return out;
    }

    private List<List<String>> expandTableRows(Element table) {
        List<Map<Integer, String>> gridRows = new ArrayList<>();
        Map<Integer, SpanCell> activeSpans = new TreeMap<>();
        int maxCols = 0;

        for (Element tr : table.select("tr")) {
            Map<Integer, String> rowMap = new HashMap<>();

            if (!activeSpans.isEmpty()) {
                List<Integer> removeCols = new ArrayList<>();
                for (Map.Entry<Integer, SpanCell> e : activeSpans.entrySet()) {
                    int col = e.getKey();
                    SpanCell span = e.getValue();
                    rowMap.put(col, span.text);
                    span.rowsLeft--;
                    if (span.rowsLeft <= 0) {
                        removeCols.add(col);
                    }
                }
                for (Integer col : removeCols) {
                    activeSpans.remove(col);
                }
            }

            int col = 0;
            for (Element child : tr.children()) {
                String tag = child.tagName();
                if (!"td".equals(tag) && !"th".equals(tag)) {
                    continue;
                }
                while (rowMap.containsKey(col)) {
                    col++;
                }

                String text = clean(child.text());
                int colspan = parseSpan(child.attr("colspan"));
                int rowspan = parseSpan(child.attr("rowspan"));

                for (int c = 0; c < colspan; c++) {
                    int colIndex = col + c;
                    rowMap.put(colIndex, text);
                    if (rowspan > 1) {
                        activeSpans.put(colIndex, new SpanCell(text, rowspan - 1));
                    }
                }
                col += colspan;
            }

            if (!rowMap.isEmpty()) {
                int rowColCount = rowMap.keySet().stream().max(Integer::compareTo).orElse(-1) + 1;
                if (rowColCount > maxCols) {
                    maxCols = rowColCount;
                }
                gridRows.add(rowMap);
            }
        }

        List<List<String>> rows = new ArrayList<>();
        for (Map<Integer, String> map : gridRows) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i < maxCols; i++) {
                row.add(clean(map.getOrDefault(i, "")));
            }
            if (row.size() >= 2) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int parseSpan(String raw) {
        try {
            int n = Integer.parseInt(clean(raw));
            return Math.max(1, n);
        } catch (Exception ex) {
            return 1;
        }
    }

    private int findBestHeaderRow(List<List<String>> rows) {
        int headerIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        int bestDiversity = -1;
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int score = scoreHeaderCandidate(row);
            int diversity = nonBlankDistinctCount(row);
            if (score > bestScore || (score == bestScore && diversity > bestDiversity)) {
                bestScore = score;
                bestDiversity = diversity;
                headerIndex = i;
            }
        }
        return headerIndex;
    }

    private int findFirstDataRow(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isTitleLikeRow(row)) {
                continue;
            }
            int numeric = 0;
            int text = 0;
            for (String cell : row) {
                String c = clean(cell);
                if (c.isBlank()) {
                    continue;
                }
                if (c.matches(".*\\d.*")) {
                    numeric++;
                }
                if (c.matches(".*[A-Za-z].*")) {
                    text++;
                }
            }
            if (numeric >= 1 && text >= 1 && !looksLikeHeaderRow(row)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> composeHeaders(List<List<String>> rows, int headerIndex) {
        List<String> headers = new ArrayList<>();
        List<String> primary = rows.get(headerIndex);
        List<String> upper = headerIndex > 0 ? rows.get(headerIndex - 1) : List.of();

        for (int i = 0; i < primary.size(); i++) {
            String p = clean(primary.get(i));
            String u = i < upper.size() ? clean(upper.get(i)) : "";
            String chosen = p;

            if (isLikelyDataCell(p) && isLikelyHeaderCell(u)) {
                chosen = u;
            } else if (isGenericHeaderCell(p) && isLikelyHeaderCell(u) && !isGenericHeaderCell(u)) {
                chosen = u;
            } else if (chosen.isBlank() && !u.isBlank()) {
                chosen = u;
            } else if (!u.isBlank()
                    && !p.isBlank()
                    && !u.equalsIgnoreCase(p)
                    && isGroupHeaderCell(u)
                    && isLikelyHeaderCell(p)) {
                chosen = u + " " + p;
            }

            if (chosen.isBlank()) {
                chosen = "col_" + (i + 1);
            }
            headers.add(chosen);
        }
        return headers;
    }

    private boolean isGroupHeaderCell(String text) {
        String t = lower(text);
        return containsAny(t, "male", "female", "general", "obc", "sc", "st", "type");
    }

    private boolean isLikelyHeaderCell(String text) {
        String t = lower(text);
        if (t.isBlank() || t.matches(".*\\d.*")) {
            return false;
        }
        return containsAny(t, "post", "position", "name", "category", "group", "vacancy", "no of post", "no. of post", "total", "general", "female", "male", "seat")
                || hasCategoryToken(t);
    }

    private boolean isGenericHeaderCell(String text) {
        String t = lower(text);
        return containsAny(t, "category name", "vacancy details", "post wise", "details", "information");
    }

    private boolean isLikelyDataCell(String text) {
        String t = clean(text);
        if (t.isBlank()) {
            return false;
        }
        if (t.matches(".*\\d.*")) {
            return true;
        }
        return !isLikelyHeaderCell(t);
    }

    private int scoreHeaderCandidate(List<String> cells) {
        if (cells.size() < 2) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        if (isTitleLikeRow(cells)) {
            score -= 20;
        }
        int diversity = nonBlankDistinctCount(cells);
        if (diversity <= 1) {
            score -= 8;
        } else {
            score += Math.min(diversity, 4);
        }
        for (String cell : cells) {
            String c = clean(cell);
            if (c.isBlank()) {
                continue;
            }
            String lc = lower(c);
            if (containsAny(lc, "post", "position", "category", "group", "vacancy", "no. of post", "no of post", "total")) {
                score += 5;
            }
            if (containsAny(lc, "type", "male", "female", "height", "chest", "weight", "eligibility criteria")) {
                score += 4;
            }
            if (hasCategoryToken(lc)) {
                score += 5;
            }
            if (c.matches(".*[A-Za-z].*")) {
                score += 1;
            }
            if (c.matches("\\d+")) {
                score -= 2;
            }
            if (c.length() > 80) {
                score -= 2;
            }
            if (containsAny(lc, "online form", "exam details", "physical exam details") && diversity <= 2) {
                score -= 4;
            }
        }
        return score;
    }

    private boolean looksLikeHeaderRow(List<String> cells) {
        int numeric = 0;
        int text = 0;
        for (String cell : cells) {
            String c = clean(cell);
            if (c.matches(".*\\d.*")) numeric++;
            if (c.matches(".*[A-Za-z].*")) text++;
        }
        String joined = lower(String.join(" ", cells));
        if (containsAny(joined, "post name", "no of post", "no. of post", "vacancy", "group", "category")
                || hasCategoryToken(joined)) {
            return true;
        }
        return text == cells.size() && numeric == 0;
    }

    private int nonBlankDistinctCount(List<String> cells) {
        Set<String> unique = new LinkedHashSet<>();
        for (String cell : cells) {
            String c = clean(cell);
            if (!c.isBlank()) unique.add(lower(c));
        }
        return unique.size();
    }

    private boolean looksLikeFooterRow(List<String> cells) {
        String joined = lower(String.join(" ", cells));
        return containsAny(joined, "click here", "check zone wise", "check post wise", "download");
    }

    private List<String> normalizeHeaderKeys(List<String> headers) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = normalizeLabel(headers.get(i));
            if (key.isBlank()) {
                key = "col_" + (i + 1);
            }
            Integer count = seen.get(key);
            if (count == null) {
                seen.put(key, 1);
                keys.add(key);
            } else {
                count = count + 1;
                seen.put(key, count);
                keys.add(key + "_" + count);
            }
        }
        return keys;
    }

    private boolean looksLikeVacancyTable(ParsedTable parsed, Element table) {
        String headerText = lower(String.join(" ", parsed.headers));
        String tableText = lower(clean(table.text()));

        if (containsAny(headerText, "post", "vacancy", "no_of_post", "no_of_posts", "group")
                || hasCategoryToken(headerText)) {
            return true;
        }
        return containsAny(tableText, "vacancy details", "post wise vacancy", "no. of post", "no of post");
    }

    private int findHeaderIndex(List<String> headerKeys, String... headerFragments) {
        for (int i = 0; i < headerKeys.size(); i++) {
            String key = headerKeys.get(i);
            for (String fragment : headerFragments) {
                if (key.contains(fragment)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findLikelyCountColumn(ParsedTable parsed) {
        int bestIndex = -1;
        int bestNumericCount = -1;
        for (int i = 0; i < parsed.headerKeys.size(); i++) {
            int numericCount = 0;
            for (Map<String, String> row : parsed.rows) {
                String v = getColumnValue(row, parsed.headerKeys, i);
                if (extractFirstNumber(v) != null) {
                    numericCount++;
                }
            }
            if (numericCount > bestNumericCount) {
                bestNumericCount = numericCount;
                bestIndex = i;
            }
        }
        return bestNumericCount > 0 ? bestIndex : -1;
    }

    private int findLikelyLabelColumn(ParsedTable parsed) {
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < parsed.headerKeys.size(); i++) {
            int textCount = 0;
            int numericCount = 0;
            for (Map<String, String> row : parsed.rows) {
                String v = clean(getColumnValue(row, parsed.headerKeys, i));
                if (v.matches(".*[A-Za-z].*")) {
                    textCount++;
                }
                if (extractFirstNumber(v) != null) {
                    numericCount++;
                }
            }
            int score = (textCount * 2) - numericCount;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private boolean hasMultipleCategoryColumns(ParsedTable parsed) {
        int count = 0;
        for (String key : parsed.headerKeys) {
            if (hasCategoryToken(key) || containsAny(key, "general", "female", "male")) {
                count++;
            }
        }
        return count >= 2;
    }

    private String getColumnValue(Map<String, String> row, List<String> headerKeys, int index) {
        if (index < 0 || index >= headerKeys.size()) {
            return null;
        }
        return row.get(headerKeys.get(index));
    }

    private void populateCategoryWise(MasterJobResponseDto.VacancyDetailsDto vacancy, ParsedTable parsed) {
        List<Integer> categoryCols = new ArrayList<>();
        for (int i = 0; i < parsed.headerKeys.size(); i++) {
            String h = parsed.headerKeys.get(i);
            if (hasCategoryToken(h) || containsAny(h, "general", "female", "male")) {
                categoryCols.add(i);
            }
        }
        if (categoryCols.isEmpty()) {
            return;
        }

        int labelCol = 0;
        int postLikeCol = findHeaderIndex(parsed.headerKeys, "post", "category", "name");
        if (postLikeCol >= 0) {
            labelCol = postLikeCol;
        }

        for (Map<String, String> row : parsed.rows) {
            String label = clean(getColumnValue(row, parsed.headerKeys, labelCol));
            for (Integer col : categoryCols) {
                String value = clean(getColumnValue(row, parsed.headerKeys, col));
                if (value.isBlank() || !value.matches(".*\\d.*")) {
                    continue;
                }
                String header = clean(parsed.headers.get(col));
                String key = label.isBlank() || label.length() > 100 ? header : (label + " | " + header);
                if (!key.isBlank() && !isBlockedText(key)) {
                    vacancy.getCategoryWise().putIfAbsent(key, value);
                }
            }
        }
    }

    private static final class ParsedTable {
        private final List<String> headers;
        private final List<String> headerKeys;
        private final List<Map<String, String>> rows;

        private ParsedTable(List<String> headers, List<String> headerKeys, List<Map<String, String>> rows) {
            this.headers = headers;
            this.headerKeys = headerKeys;
            this.rows = rows;
        }
    }

    private static final class SpanCell {
        private final String text;
        private int rowsLeft;

        private SpanCell(String text, int rowsLeft) {
            this.text = text;
            this.rowsLeft = rowsLeft;
        }
    }

    private static final class QualificationExtractionResult {
        private final String summary;
        private final Map<String, String> postWise;

        private QualificationExtractionResult(String summary, Map<String, String> postWise) {
            this.summary = summary;
            this.postWise = postWise;
        }
    }

    private static final class PhysicalStandardsExtract {
        private final String stageTitle;
        private final String summary;

        private PhysicalStandardsExtract(String stageTitle, String summary) {
            this.stageTitle = stageTitle;
            this.summary = summary;
        }
    }

    private void fillPayScale(List<String> lines, Map<String, String> kvLines, MasterJobResponseDto dto) {
        for (Map.Entry<String, String> entry : kvLines.entrySet()) {
            String key = lower(entry.getKey());
            if (containsAny(key, "pay scale", "pay level", "salary", "grade pay", "basic pay")) {
                dto.setPayScale(clean(entry.getValue()));
                return;
            }
        }

        for (String line : lines) {
            String l = lower(line);
            if (containsAny(l, "pay level", "salary", "pay scale", "grade pay") && !looksLikeDateValue(line)) {
                dto.setPayScale(line);
                return;
            }
        }
    }

    private void fillApplicationProcess(Element main, List<String> lines, MasterJobResponseDto dto) {
        Set<String> steps = new LinkedHashSet<>();

        steps.addAll(extractApplicationStepsFromHeadings(main));

        for (String line : lines) {
            String l = lower(line);
            if (line.length() > 240 || isBlockedText(line)) {
                continue;
            }
            if (containsAny(l, "apply", "application form", "register", "submission", "submit application", "fill form", "upload", "fee payment")
                    && !containsAny(l, "question:", "answer:", "latest posts", "related posts", "apply start date", "last date",
                    "who have completed", "other useful documents", "printout", "click here", "submit / login", "login button",
                    "login to your account", "registration number and password", "download admit card", "check result")) {
                steps.add(line);
            }
        }
        dto.setApplicationProcess(new ArrayList<>(steps));
    }

    private void fillExamSchemeAndSelection(Element main, List<String> lines, MasterJobResponseDto dto) {
        Map<String, Object> examScheme = new LinkedHashMap<>();
        Set<String> selection = new LinkedHashSet<>();

        selection.addAll(extractSelectionStagesFromBlocks(main));

        for (String line : lines) {
            String l = lower(line);
            if (line.length() > 220 || containsAny(l, "has released", "applications are", "candidates should review", "provided below")) {
                continue;
            }

            if (isCombinedSelectionLine(l)) {
                addSelectionFromText(line, selection);
                continue;
            }

            if (isStandaloneSelectionStage(line)) {
                addSelectionFromText(line, selection);
                continue;
            }

            boolean storeSchemeLine = shouldStoreExamSchemeLine(line, l);

            if (isCbtLine(l)) {
                if (storeSchemeLine && line.length() <= 160 && !looksLikeSectionHeading(line)) {
                    examScheme.putIfAbsent("cbt", line);
                }
                selection.add("Computer Based Test");
            }
            if (isPhysicalTestLine(l)
                    && !containsAny(l, "aadhaar", "certificate", "document required")) {
                if (storeSchemeLine && line.length() <= 160 && !looksLikeSectionHeading(line)) {
                    examScheme.putIfAbsent("physical_test", line);
                }
                selection.add("Physical Test");
            }
            if (containsAny(l, "document verification")
                    && !containsAny(l, "aadhaar", "certificate", "document required")) {
                if (storeSchemeLine && line.length() <= 160 && !looksLikeSectionHeading(line)) {
                    examScheme.putIfAbsent("document_verification", line);
                }
                selection.add("Document Verification");
            }
            if (containsMedicalStage(l)
                    && !containsAny(l, "certificate", "document required")) {
                if (storeSchemeLine && line.length() <= 160 && !looksLikeSectionHeading(line)) {
                    examScheme.putIfAbsent("medical_exam", line);
                }
                selection.add("Medical Examination");
            }
            if (containsAny(l, "screening test", "subject knowledge test", "interview", "viva")) {
                addSelectionFromText(line, selection);
            }
            if (containsAny(l, "duration", "total question", "total marks", "negative marking")) {
                String[] kv = splitKeyValue(line);
                if (kv != null) {
                    examScheme.put(normalizeLabel(kv[0]), kv[1]);
                }
            }
        }

        PhysicalStandardsExtract physical = extractPhysicalStandardsFromTables(main);
        if (physical != null && physical.summary != null && !physical.summary.isBlank()) {
            examScheme.putIfAbsent("physical_standard_test", physical.summary);

            String existingPhysical = asText(examScheme.get("physical_test"));
            if (existingPhysical == null
                    || existingPhysical.isBlank()
                    || existingPhysical.equalsIgnoreCase(physical.summary)
                    || existingPhysical.length() > 180) {
                if (physical.stageTitle != null && !physical.stageTitle.isBlank()) {
                    examScheme.put("physical_test", physical.stageTitle);
                } else {
                    examScheme.put("physical_test", physical.summary);
                }
            }
            selection.add("Physical Test");
        }

        dto.setExamScheme(examScheme);
        dto.setSelectionProcess(new ArrayList<>(selection));
    }

    private PhysicalStandardsExtract extractPhysicalStandardsFromTables(Element main) {
        for (Element table : main.select("table")) {
            List<List<String>> rows = expandTableRows(table);
            if (rows.size() < 3) {
                continue;
            }
            if (!looksLikePhysicalStandardsTable(rows, table.text())) {
                continue;
            }

            List<String> lines = new ArrayList<>();
            List<String> headers = null;
            String currentSection = null;
            String tableTitle = null;

            for (List<String> row : rows) {
                List<String> cells = row.stream().map(this::clean).toList();
                if (cells.stream().allMatch(String::isBlank)) {
                    continue;
                }

                if (isSingleLabelRow(cells)) {
                    String label = clean(cells.get(0));
                    String ll = lower(label);
                    if (containsAny(ll, "physical standard", "physical test", "physical efficiency", "physical exam", "physical details", "pst", "pet")) {
                        tableTitle = normalizePhysicalStageTitle(label);
                    }
                    if (containsAny(ll, "male", "female")) {
                        currentSection = normalizePhysicalSectionLabel(label);
                    }
                    continue;
                }

                if (isPhysicalHeaderRow(cells)) {
                    headers = cells;
                    continue;
                }

                if (headers == null || !isLikelyPhysicalDataRow(cells)) {
                    continue;
                }

                String rowSummary = summarizePhysicalRow(headers, cells);
                if (rowSummary == null || rowSummary.isBlank()) {
                    continue;
                }
                if (currentSection != null && !currentSection.isBlank()) {
                    rowSummary = currentSection + " " + rowSummary;
                }
                lines.add(rowSummary);
            }

            if (!lines.isEmpty()) {
                String summary = String.join("; ", lines.stream().distinct().toList());
                if (summary.length() > 1800) {
                    summary = summary.substring(0, 1800).trim();
                }
                return new PhysicalStandardsExtract(tableTitle, summary);
            }

            PhysicalStandardsExtract matrix = extractPhysicalMatrix(rows);
            if (matrix != null && matrix.summary != null && !matrix.summary.isBlank()) {
                String finalTitle = matrix.stageTitle;
                if ((finalTitle == null || finalTitle.isBlank()) && tableTitle != null && !tableTitle.isBlank()) {
                    finalTitle = tableTitle;
                }
                return new PhysicalStandardsExtract(finalTitle, matrix.summary);
            }
        }
        return null;
    }

    private boolean looksLikePhysicalStandardsTable(List<List<String>> rows, String tableText) {
        String t = lower(clean(tableText));
        boolean hasContext = containsAny(t, "physical standard", "physical test", "physical exam", "physical efficiency", "pst", "pet", "height", "chest", "weight");
        if (!hasContext) {
            return false;
        }

        for (List<String> row : rows) {
            if (isPhysicalHeaderRow(row.stream().map(this::clean).toList())) {
                return true;
            }
        }
        for (List<String> row : rows) {
            String joined = lower(String.join(" ", row.stream().map(this::clean).toList()));
            if (containsAny(joined, "type", "male", "female")) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleLabelRow(List<String> cells) {
        List<String> nonBlank = cells.stream().map(this::clean).filter(s -> !s.isBlank()).toList();
        if (nonBlank.isEmpty()) {
            return false;
        }
        String first = nonBlank.get(0);
        for (String c : nonBlank) {
            if (!c.equalsIgnoreCase(first)) {
                return false;
            }
        }
        return first.length() <= 120;
    }

    private boolean isPhysicalHeaderRow(List<String> cells) {
        String joined = lower(String.join(" ", cells.stream().map(this::clean).toList()));
        return (joined.contains("category") || joined.contains("type"))
                && (joined.contains("height") || joined.contains("male") || joined.contains("female"))
                && (joined.contains("chest") || joined.contains("weight") || joined.contains("male") || joined.contains("female"));
    }

    private boolean isLikelyPhysicalDataRow(List<String> cells) {
        String joined = lower(String.join(" ", cells.stream().map(this::clean).toList()));
        if (joined.isBlank() || joined.contains("category")) {
            return false;
        }
        return joined.matches(".*\\d.*")
                && containsAny(joined, "cm", "kg", "chest", "height", "weight", "ur", "obc", "sc", "st");
    }

    private String summarizePhysicalRow(List<String> headers, List<String> cells) {
        int cols = Math.min(headers.size(), cells.size());
        if (cols < 2) {
            return null;
        }

        String category = clean(cells.get(0));
        if (category.isBlank() || lower(category).contains("category")) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        for (int i = 1; i < cols; i++) {
            String h = clean(headers.get(i));
            String v = clean(cells.get(i));
            if (h.isBlank() || v.isBlank()) {
                continue;
            }
            if (lower(h).contains("category")) {
                continue;
            }
            parts.add(h + " " + v);
        }

        if (parts.isEmpty()) {
            return null;
        }
        return category + ": " + String.join(", ", parts);
    }

    private PhysicalStandardsExtract extractPhysicalMatrix(List<List<String>> rows) {
        int topHeader = -1;
        for (int i = 0; i < rows.size(); i++) {
            String joined = lower(String.join(" ", rows.get(i).stream().map(this::clean).toList()));
            if (containsAny(joined, "type") && containsAny(joined, "male", "female")) {
                topHeader = i;
                break;
            }
        }
        if (topHeader < 0 || topHeader + 2 >= rows.size()) {
            return null;
        }

        List<String> upper = rows.get(topHeader).stream().map(this::clean).toList();
        List<String> lowerHeaders = rows.get(topHeader + 1).stream().map(this::clean).toList();
        int cols = Math.min(upper.size(), lowerHeaders.size());
        if (cols < 3) {
            return null;
        }

        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            String u = clean(upper.get(i));
            String l = clean(lowerHeaders.get(i));
            String h;
            if (i == 0) {
                h = !u.isBlank() ? u : (!l.isBlank() ? l : "type");
            } else if (!u.isBlank() && !l.isBlank() && !u.equalsIgnoreCase(l)) {
                h = u + " " + l;
            } else if (!l.isBlank()) {
                h = l;
            } else {
                h = u;
            }
            headers.add(h.isBlank() ? ("col_" + (i + 1)) : h);
        }

        List<String> lines = new ArrayList<>();
        for (int r = topHeader + 2; r < rows.size(); r++) {
            List<String> cells = resizeRow(rows.get(r), cols);
            String metric = clean(cells.get(0));
            if (metric.isBlank() || looksLikeHeaderRow(cells)) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (int c = 1; c < cols; c++) {
                String value = clean(cells.get(c));
                if (value.isBlank()) continue;
                String h = clean(headers.get(c));
                if (h.isBlank()) h = "col_" + (c + 1);
                parts.add(h + " " + value);
            }
            if (!parts.isEmpty()) {
                lines.add(metric + ": " + String.join(", ", parts));
            }
        }

        if (lines.isEmpty()) {
            return null;
        }
        String summary = String.join("; ", lines.stream().distinct().toList());
        if (summary.length() > 1800) {
            summary = summary.substring(0, 1800).trim();
        }
        return new PhysicalStandardsExtract(null, summary);
    }

    private String normalizePhysicalSectionLabel(String raw) {
        String l = lower(clean(raw));
        if (containsAny(l, "female")) {
            return "Female";
        }
        if (containsAny(l, "male")) {
            return "Male";
        }
        return clean(raw);
    }

    private String normalizePhysicalStageTitle(String raw) {
        String t = clean(raw).replaceAll("\\s*:\\s*", ": ");
        if (t.length() > 140) {
            t = t.substring(0, 140).trim();
        }
        return t;
    }

    private void fillImportantNotes(List<String> lines, MasterJobResponseDto dto) {
        List<String> notes = new ArrayList<>();
        for (String line : lines) {
            String l = lower(line);
            if (looksLikeSectionHeading(line)) {
                continue;
            }
            if (containsAny(l, "click here", "important link section", "useful links")) {
                continue;
            }
            if (containsAny(l, "note", "advised", "read notification", "instruction")
                    || (l.contains("important") && line.length() > 30 && !containsAny(l, "important dates", "important links"))) {
                notes.add(line);
            }
        }
        dto.setImportantNotes(notes.stream().distinct().limit(15).toList());
    }

    private List<String> extractSectionLinesByHeading(Element main, String... headingHints) {
        List<String> out = new ArrayList<>();
        for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
            String ht = lower(clean(h.text()));
            boolean matched = false;
            for (String hint : headingHints) {
                if (ht.contains(lower(hint))) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                continue;
            }

            Element sib = h.nextElementSibling();
            int hops = 0;
            while (sib != null && !sib.tagName().matches("h[1-6]") && hops < 5) {
                for (Element li : sib.select("li")) {
                    String t = clean(li.text());
                    if (!t.isBlank()) out.add(t);
                }
                String own = clean(sib.ownText());
                if (!own.isBlank()) out.add(own);
                hops++;
                sib = sib.nextElementSibling();
            }

            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    private List<String> extractApplicationStepsFromHeadings(Element main) {
        Set<String> steps = new LinkedHashSet<>();
        for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
            String ht = lower(clean(h.text()));
            if (!containsAny(ht, "how to apply", "application process", "steps to apply", "apply process")) {
                continue;
            }
            Element sib = h.nextElementSibling();
            int hops = 0;
            while (sib != null && !sib.tagName().matches("h[1-6]") && hops < 4) {
                for (Element li : sib.select("li")) {
                    String line = clean(li.text());
                    String l = lower(line);
                    if (line.length() < 10 || line.length() > 220 || looksLikeSectionHeading(line)) continue;
                    if (containsAny(l, "apply", "register", "fill", "upload", "pay", "submit")
                            && !containsAny(l, "click here", "latest post", "related post", "download")) {
                        steps.add(line);
                    }
                }
                hops++;
                sib = sib.nextElementSibling();
            }
        }
        return new ArrayList<>(steps);
    }

    private List<String> extractSelectionStagesFromBlocks(Element main) {
        Set<String> stages = new LinkedHashSet<>();

        for (Element table : main.select("table")) {
            String tt = lower(clean(table.text()));
            if (!containsAny(tt, "mode of selection", "selection process")) {
                continue;
            }
            for (Element li : table.select("li")) {
                addSelectionFromText(li.text(), stages);
            }
            if (stages.isEmpty()) {
                addSelectionFromText(table.text(), stages);
            }
        }

        for (Element h : main.select("h1,h2,h3,h4,h5,h6")) {
            String ht = lower(clean(h.text()));
            if (!containsAny(ht, "mode of selection", "selection process")) {
                continue;
            }
            Element sib = h.nextElementSibling();
            int hops = 0;
            while (sib != null && !sib.tagName().matches("h[1-6]") && hops < 4) {
                for (Element li : sib.select("li")) {
                    addSelectionFromText(li.text(), stages);
                }
                hops++;
                sib = sib.nextElementSibling();
            }
        }

        return new ArrayList<>(stages);
    }

    private void addSelectionFromText(String text, Set<String> selection) {
        String l = lower(clean(text));
        if (l.isBlank()) return;
        if (containsAny(l, "screening test")) selection.add("Screening Test");
        if (containsAny(l, "subject knowledge test")) selection.add("Subject Knowledge Test");
        if (containsAny(l, "interview", "viva")) selection.add("Interview");
        if (isCbtLine(l)) selection.add("Computer Based Test");
        if (isPhysicalTestLine(l)) selection.add("Physical Test");
        if (containsAny(l, "document verification")) selection.add("Document Verification");
        if (containsMedicalStage(l)) selection.add("Medical Examination");
        if (containsAny(l, "written examination") && !selection.contains("Computer Based Test")) selection.add("Written Examination");
    }

    private boolean isCombinedSelectionLine(String lowerLine) {
        int count = 0;
        if (isCbtLine(lowerLine)) count++;
        if (isPhysicalTestLine(lowerLine)) count++;
        if (containsAny(lowerLine, "document verification")) count++;
        if (containsMedicalStage(lowerLine)) count++;
        if (containsAny(lowerLine, "screening test")) count++;
        if (containsAny(lowerLine, "subject knowledge test")) count++;
        if (containsAny(lowerLine, "interview", "viva")) count++;
        return count >= 2;
    }

    private boolean looksLikeSectionHeading(String text) {
        String t = clean(text);
        String l = lower(t);
        if (t.length() <= 40 && containsAny(l, "important dates", "important links", "useful links", "apply online link", "mode of selection")) {
            return true;
        }
        return t.matches("^[A-Za-z0-9\\s:/&()'.,-]{1,45}$")
                && !t.matches(".*\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4}.*")
                && containsAny(l, "important", "useful", "links", "dates", "selection process", "application process");
    }

    private boolean isStandaloneSelectionStage(String text) {
        String t = clean(text);
        String l = lower(t);
        if (t.isBlank() || t.length() > 70) return false;
        if (t.contains(":")) return false;
        if (!containsAny(l, "screening test", "subject knowledge test", "interview", "viva", "document verification",
                "medical examination", "physical test", "pet", "pst", "written examination", "computer based test", "cbt")) {
            return false;
        }
        int words = t.split("\\s+").length;
        return words <= 6;
    }

    private boolean shouldStoreExamSchemeLine(String rawLine, String lowerLine) {
        if (looksLikeSectionHeading(rawLine)) return false;
        if (containsAny(lowerLine, "mode of selection", "selection process")) return false;
        if (rawLine.contains(":")) return true;
        if (containsAny(lowerLine, "mode", "duration", "minute", "hours", "questions", "marks", "negative marking")) return true;
        int words = clean(rawLine).split("\\s+").length;
        return words >= 6;
    }

    private void fillOfficialLinks(Element main, MasterJobResponseDto dto) {
        Map<String, Object> links = new LinkedHashMap<>();

        // Rule: TD1 label => key, TD2 anchor href(s) => value(s)
        for (Element row : main.select("table tr")) {
            Elements cells = row.select("th,td");
            if (cells.size() < 2) {
                continue;
            }

            String label = clean(cells.get(0).text());
            String key = normalizeLabel(label);
            if (label.isBlank() || key.isBlank() || isBlockedText(label) || looksLikeLinkJunk(key)) {
                continue;
            }

            Elements anchors = cells.get(1).select("a[href]");
            String deferredMessage = extractDeferredLinkMessage(cells.get(1).text());
            if (anchors.isEmpty()) {
                if (deferredMessage != null && isActionLinkLabel(label)) {
                    links.put(key, deferredMessage);
                }
                continue;
            }

            Map<String, String> nested = new LinkedHashMap<>();
            int linkIndex = 1;
            for (Element a : anchors) {
                String href = clean(a.absUrl("href"));
                String text = clean(a.text());
                if (href.isBlank()
                        || isBlockedDomain(href)
                        || isLikelyRelatedPostLink(href, label + " " + text)
                        || isLikelyInternalArticleLink(href, dto.getSource())
                        || href.equalsIgnoreCase(dto.getSource())) {
                    continue;
                }

                String nestedKey = normalizeLabel(text);
                if (nestedKey.isBlank() || "click_here".equals(nestedKey)) {
                    nestedKey = "link_" + linkIndex;
                }
                if (nested.containsKey(nestedKey)) {
                    nestedKey = nestedKey + "_" + linkIndex;
                }
                nested.put(nestedKey, href);
                linkIndex++;
            }

            if (nested.isEmpty()) {
                if (deferredMessage != null && isActionLinkLabel(label)) {
                    links.put(key, deferredMessage);
                }
                continue;
            }
            if (deferredMessage != null && isActionLinkLabel(label)) {
                // Prefer activation/availability note over placeholder social links.
                links.put(key, deferredMessage);
                continue;
            }
            if (nested.size() == 1) {
                links.put(key, nested.values().iterator().next());
            } else {
                links.put(key, nested);
            }
        }

        if (!links.containsKey("official_website")) {
            for (Element a : main.select("a[href]")) {
                String text = clean(a.text());
                String href = clean(a.absUrl("href"));
                if (!href.isBlank() && lower(text).contains("official website")) {
                    links.put("official_website", href);
                    break;
                }
            }
        }

        dto.setOfficialLinks(links);
    }

    private void fillOtherTables(Element main, MasterJobResponseDto dto) {
        Map<String, List<Map<String, String>>> otherTables = new LinkedHashMap<>();
        int tableIndex = 1;

        for (Element table : main.select("table")) {
            if (!shouldIncludeOtherTable(table)) {
                tableIndex++;
                continue;
            }
            ParsedTable parsed = parseDynamicTable(table, false);
            if (parsed == null) {
                parsed = parseRawTable(table);
            }
            if (parsed == null || parsed.rows == null || parsed.rows.isEmpty()) {
                tableIndex++;
                continue;
            }

            String tableKey = resolveTableKey(table, tableIndex);
            parsed = rebuildOtherTableHeadersIfNeeded(parsed, tableKey);

            List<Map<String, String>> rows = new ArrayList<>();
            for (Map<String, String> row : parsed.rows) {
                Map<String, String> outRow = new LinkedHashMap<>();
                for (int c = 0; c < parsed.headerKeys.size(); c++) {
                    String key = parsed.headerKeys.get(c);
                    String value = clean(row.getOrDefault(key, ""));
                    if (key == null || key.isBlank() || value.isBlank()) {
                        continue;
                    }
                    outRow.put(key, value);
                }
                if (!outRow.isEmpty()) {
                    rows.add(outRow);
                }
            }

            if (rows.isEmpty()) {
                tableIndex++;
                continue;
            }

            String uniqueKey = tableKey;
            int suffix = 2;
            while (otherTables.containsKey(uniqueKey)) {
                uniqueKey = tableKey + "_" + suffix;
                suffix++;
            }
            otherTables.put(uniqueKey, rows);
            tableIndex++;
        }

        dto.setOtherTables(otherTables);
    }

    private ParsedTable rebuildOtherTableHeadersIfNeeded(ParsedTable parsed, String tableKey) {
        if (parsed == null || parsed.rows == null || parsed.rows.isEmpty()) {
            return parsed;
        }
        if (!headersDerivedFromTableKey(parsed.headerKeys, tableKey)) {
            return parsed;
        }

        List<List<String>> rowValues = new ArrayList<>();
        for (Map<String, String> row : parsed.rows) {
            rowValues.add(rowValuesInHeaderOrder(row, parsed.headerKeys));
        }
        if (rowValues.isEmpty()) {
            return parsed;
        }

        int headerDepth = 0;
        if (rowValues.size() >= 3
                && looksLikeHeaderRow(rowValues.get(0))
                && looksLikeHeaderRow(rowValues.get(1))
                && rowHasDigits(rowValues.get(2))) {
            headerDepth = 2;
        } else if (rowValues.size() >= 2 && looksLikeHeaderRow(rowValues.get(0))) {
            headerDepth = 1;
        }

        if (headerDepth == 0 || rowValues.size() <= headerDepth) {
            return compactOtherTableHeaders(parsed, tableKey);
        }

        List<String> rebuiltHeaders = headerDepth == 2
                ? mergeHeaderRows(rowValues.get(0), rowValues.get(1))
                : new ArrayList<>(rowValues.get(0));
        List<String> rebuiltHeaderKeys = normalizeHeaderKeys(rebuiltHeaders);
        List<Map<String, String>> rebuiltRows = new ArrayList<>();

        for (int i = headerDepth; i < rowValues.size(); i++) {
            List<String> values = rowValues.get(i);
            Map<String, String> mapped = new LinkedHashMap<>();
            boolean hasAny = false;
            for (int c = 0; c < rebuiltHeaderKeys.size(); c++) {
                String value = c < values.size() ? clean(values.get(c)) : "";
                mapped.put(rebuiltHeaderKeys.get(c), value);
                if (!value.isBlank()) {
                    hasAny = true;
                }
            }
            if (hasAny) {
                rebuiltRows.add(mapped);
            }
        }

        if (rebuiltRows.isEmpty()) {
            return compactOtherTableHeaders(parsed, tableKey);
        }
        return new ParsedTable(rebuiltHeaders, rebuiltHeaderKeys, rebuiltRows);
    }

    private ParsedTable compactOtherTableHeaders(ParsedTable parsed, String tableKey) {
        if (parsed == null || parsed.rows == null || parsed.rows.isEmpty()) {
            return parsed;
        }
        if (!headersDerivedFromTableKey(parsed.headerKeys, tableKey)) {
            return parsed;
        }
        int cols = parsed.headerKeys.size();
        List<String> compactHeaders = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            compactHeaders.add("col_" + (i + 1));
        }
        List<String> compactHeaderKeys = normalizeHeaderKeys(compactHeaders);
        List<Map<String, String>> compactRows = new ArrayList<>();
        for (Map<String, String> row : parsed.rows) {
            List<String> values = rowValuesInHeaderOrder(row, parsed.headerKeys);
            Map<String, String> mapped = new LinkedHashMap<>();
            boolean hasAny = false;
            for (int i = 0; i < compactHeaderKeys.size(); i++) {
                String value = i < values.size() ? clean(values.get(i)) : "";
                mapped.put(compactHeaderKeys.get(i), value);
                if (!value.isBlank()) {
                    hasAny = true;
                }
            }
            if (hasAny) {
                compactRows.add(mapped);
            }
        }
        if (compactRows.isEmpty()) {
            return parsed;
        }
        return new ParsedTable(compactHeaders, compactHeaderKeys, compactRows);
    }

    private boolean headersDerivedFromTableKey(List<String> headerKeys, String tableKey) {
        if (headerKeys == null || headerKeys.isEmpty()) {
            return false;
        }
        String normalizedTableKey = normalizeLabel(tableKey);
        if (normalizedTableKey.isBlank()) {
            return false;
        }
        int derived = 0;
        String keyPattern = Pattern.quote(normalizedTableKey) + "_\\d+";
        for (String key : headerKeys) {
            String k = clean(key);
            if (k.equals(normalizedTableKey) || k.matches(keyPattern)) {
                derived++;
            }
        }
        return derived == headerKeys.size();
    }

    private List<String> rowValuesInHeaderOrder(Map<String, String> row, List<String> headerKeys) {
        List<String> values = new ArrayList<>();
        if (row == null || headerKeys == null) {
            return values;
        }
        for (String key : headerKeys) {
            values.add(clean(row.getOrDefault(key, "")));
        }
        return values;
    }

    private boolean rowHasDigits(List<String> row) {
        if (row == null) {
            return false;
        }
        for (String cell : row) {
            if (clean(cell).matches(".*\\d.*")) {
                return true;
            }
        }
        return false;
    }

    private List<String> mergeHeaderRows(List<String> top, List<String> bottom) {
        int cols = Math.max(top.size(), bottom.size());
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            String t = i < top.size() ? clean(top.get(i)) : "";
            String b = i < bottom.size() ? clean(bottom.get(i)) : "";
            merged.add(mergeHeaderCell(t, b, i));
        }
        return merged;
    }

    private String mergeHeaderCell(String top, String bottom, int columnIndex) {
        if (top.isBlank()) {
            return bottom.isBlank() ? "col_" + (columnIndex + 1) : bottom;
        }
        if (bottom.isBlank()) {
            return top;
        }
        if (top.equalsIgnoreCase(bottom)) {
            return top;
        }
        return top + " " + bottom;
    }

    private boolean shouldIncludeOtherTable(Element table) {
        String t = lower(clean(table.text()));
        if (t.isBlank()) {
            return false;
        }
        if (containsAny(t, "join our whatsapp", "join our telegram", "follow now", "download app")) {
            return false;
        }
        if (containsAny(t, "latest posts", "related posts", "you may also check")) {
            return false;
        }
        if (containsAny(t, "click here")
                && !t.matches(".*\\d.*")
                && !containsAny(t, "height", "weight", "chest", "age", "post", "vacancy", "eligibility")) {
            return false;
        }
        return true;
    }

    private ParsedTable parseRawTable(Element table) {
        List<List<String>> rows = expandTableRows(table);
        if (rows.isEmpty()) {
            return null;
        }

        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (cols < 2) {
            return null;
        }

        int rowStart = 0;
        while (rowStart < rows.size() && isTitleLikeRow(rows.get(rowStart))) {
            rowStart++;
        }
        if (rowStart >= rows.size()) {
            return null;
        }

        List<String> headers;
        int start;
        List<String> first = resizeRow(rows.get(rowStart), cols);
        if (looksLikeHeaderRow(first)) {
            headers = first;
            start = rowStart + 1;
        } else {
            headers = new ArrayList<>();
            for (int i = 0; i < cols; i++) headers.add("col_" + (i + 1));
            start = rowStart;
        }

        List<String> headerKeys = normalizeHeaderKeys(headers);
        List<Map<String, String>> mapped = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            List<String> cells = resizeRow(rows.get(i), cols);
            Map<String, String> row = new LinkedHashMap<>();
            boolean hasAny = false;
            for (int c = 0; c < cols; c++) {
                String v = clean(cells.get(c));
                row.put(headerKeys.get(c), v);
                if (!v.isBlank()) hasAny = true;
            }
            if (hasAny) {
                mapped.add(row);
            }
        }
        if (mapped.isEmpty()) {
            return null;
        }
        return new ParsedTable(headers, headerKeys, mapped);
    }

    private boolean isTitleLikeRow(List<String> row) {
        if (row == null || row.isEmpty()) return false;
        int distinct = nonBlankDistinctCount(row);
        String joined = lower(clean(String.join(" ", row)));
        if (distinct == 1 && (joined.length() > 25 || containsAny(joined, "online form", "exam details", "physical", "recruitment"))) {
            return true;
        }
        return false;
    }

    private String resolveTableKey(Element table, int tableIndex) {
        String title = extractHeadingNearTable(table);
        if (title == null || title.isBlank()) {
            title = extractTitleFromTableRows(table);
        }
        String normalized = normalizeLabel(title);
        if (normalized == null || normalized.isBlank()) {
            normalized = "table_" + tableIndex;
        }
        return normalized;
    }

    private String extractHeadingNearTable(Element table) {
        Element prev = table.previousElementSibling();
        int hops = 0;
        while (prev != null && hops < 6) {
            String tag = prev.tagName();
            String text = clean(prev.text());
            if (text.isBlank()) {
                prev = prev.previousElementSibling();
                hops++;
                continue;
            }
            if (tag.matches("h[1-6]")) {
                return text;
            }
            if (("p".equalsIgnoreCase(tag) || "div".equalsIgnoreCase(tag))
                    && text.length() <= 140
                    && !isBlockedText(text)) {
                return text;
            }
            prev = prev.previousElementSibling();
            hops++;
        }
        return null;
    }

    private String extractTitleFromTableRows(Element table) {
        List<List<String>> rows = expandTableRows(table);
        int limit = Math.min(3, rows.size());
        for (int i = 0; i < limit; i++) {
            List<String> row = rows.get(i);
            List<String> nonBlank = row.stream().map(this::clean).filter(s -> !s.isBlank()).toList();
            if (nonBlank.isEmpty()) {
                continue;
            }
            String first = nonBlank.get(0);
            boolean allSame = true;
            for (String cell : nonBlank) {
                if (!cell.equalsIgnoreCase(first)) {
                    allSame = false;
                    break;
                }
            }
            if (allSame && first.length() <= 180) {
                return first;
            }
        }
        return null;
    }

    private String extractDeferredLinkMessage(String text) {
        String t = clean(text);
        if (t.isBlank()) {
            return null;
        }
        t = t.replaceFirst("(?i)^click\\s*here\\s*", "").trim();

        Pattern[] patterns = new Pattern[] {
                Pattern.compile("(?i)(link\\s+(?:will\\s+be\\s+)?activ(?:e|ate|ated)\\s+on\\s+[^|;,.]+(?:\\s+\\d{4})?)"),
                Pattern.compile("(?i)((?:will\\s+be\\s+)?activ(?:e|ate|ated)\\s+on\\s+[^|;,.]+(?:\\s+\\d{4})?)"),
                Pattern.compile("(?i)(available\\s+(?:on|from)\\s+[^|;,.]+(?:\\s+\\d{4})?)"),
                Pattern.compile("(?i)(link\\s+(?:will\\s+be\\s+)?activ(?:e|ate|ated)\\s+soon)"),
                Pattern.compile("(?i)((?:link\\s+)?(?:activation|available)\\s+soon)"),
                Pattern.compile("(?i)(coming\\s+soon)")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                return clean(m.group(1));
            }
        }
        String lower = lower(t);
        if (containsAny(lower, "soon", "shortly", "to be announced", "tba")) {
            return t.length() > 120 ? t.substring(0, 120).trim() : t;
        }
        return null;
    }

    private boolean isActionLinkLabel(String label) {
        String l = lower(label);
        return containsAny(l, "apply", "admit", "result", "answer key", "download", "registration", "login", "notification");
    }

    private void fillSyllabus(List<String> lines, MasterJobResponseDto dto) {
        Set<String> syllabus = new LinkedHashSet<>();
        for (String line : lines) {
            String l = lower(line);
            if (containsAny(l, "syllabus", "general science", "mathematics", "reasoning", "general awareness")) {
                syllabus.add(line);
            }
        }
        dto.setSyllabusOverview(new ArrayList<>(syllabus));
    }

    private Map<String, String> collectKeyValueLines(Element main) {
        Map<String, String> map = new LinkedHashMap<>();

        for (Element row : main.select("tr")) {
            List<String> cells = row.select("th,td").eachText().stream().map(this::clean).toList();
            if (cells.size() >= 2) {
                if (!isBlockedText(cells.get(0)) && !isBlockedText(cells.get(1))) {
                    map.put(cells.get(0), cells.get(1));
                }
            }
        }

        for (Element node : main.select("p,li")) {
            String text = clean(node.text());
            if (isBlockedText(text) || text.length() > 240) {
                continue;
            }
            String[] kv = splitKeyValue(text);
            if (kv != null) {
                map.putIfAbsent(kv[0], kv[1]);
            }
        }

        return map;
    }

    private List<String> collectTextLines(Element main) {
        List<String> lines = new ArrayList<>();
        for (Element el : main.select("h1,h2,h3,h4,h5,h6,p,li,tr")) {
            String text = clean(el.text());
            if (text.length() >= 5 && !isNoise(text) && !isBlockedText(text)) {
                lines.add(text);
            }
        }
        return lines.stream().distinct().toList();
    }

    private boolean isNoise(String text) {
        String t = lower(text);
        return t.contains("telegram") || t.contains("whatsapp") || t.contains("download app");
    }

    private boolean isBlockedText(String text) {
        String t = lower(text);
        for (String blocked : BLOCKED_TEXT) {
            if (t.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedDomain(String href) {
        String h = lower(href);
        for (String d : BLOCKED_LINK_DOMAINS) {
            if (h.contains(d)) {
                return true;
            }
        }
        return false;
    }

    private String toLinkKey(String text) {
        String t = lower(text);
        if (t.isBlank()) return null;
        if (t.contains("apply")) return "apply_online";
        if ((t.equals("hindi") || t.contains("notification") || t.contains("notice") || t.contains("corrigendum")) && t.contains("hindi")) {
            return "notification_in_hindi";
        }
        if ((t.equals("english") || t.contains("notification") || t.contains("notice") || t.contains("corrigendum")) && t.contains("english")) {
            return "notification_in_english";
        }
        if (t.contains("notification") || t.contains("notice")) return "official_notification";
        if (t.contains("admit")) return "admit_card";
        if (t.contains("result")) return "result";
        if (t.contains("official website") || t.equals("official website")) return "official_website";
        if (t.contains("answer key")) return "answer_key";
        if (t.contains("syllabus")) return "syllabus";
        return normalizeLabel(text);
    }

    private String normalizeLabel(String input) {
        return clean(input)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String extractMoney(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            return digitsOnly(m.group(1));
        }
        if (text != null && text.matches(".*\\b\\d{2,6}\\b.*") && !looksLikeDateValue(text)) {
            Matcher n = Pattern.compile("\\b\\d{2,6}\\b").matcher(text);
            if (n.find()) return n.group();
        }
        return null;
    }

    private String pickHigherMoney(String existing, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return candidate;
        }
        int e = toInt(existing);
        int c = toInt(candidate);
        if (c < 0) return existing;
        if (e < 0) return candidate;
        return c > e ? candidate : existing;
    }

    private String extractFirstNumber(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("\\d[\\d,]{0,8}").matcher(text);
        return m.find() ? digitsOnly(m.group()) : null;
    }

    private String[] splitKeyValue(String line) {
        Matcher m = KEY_VALUE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String key = clean(m.group(1));
        String value = clean(m.group(2));
        if (key.isBlank() || value.isBlank()) {
            return null;
        }
        return new String[]{key, value};
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    private boolean hasCategoryToken(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return CATEGORY_TOKEN_PATTERN.matcher(text).find();
    }

    private boolean hasGeneralFeeToken(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return GENERAL_FEE_TOKEN_PATTERN.matcher(text).find();
    }

    private boolean hasReservedFeeToken(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return RESERVED_FEE_TOKEN_PATTERN.matcher(text).find();
    }

    private boolean isCbtLine(String lowerLine) {
        return lowerLine.contains("computer based test")
                || lowerLine.matches(".*\\bcbt\\b.*")
                || lowerLine.contains("written examination");
    }

    private boolean isPhysicalTestLine(String lowerLine) {
        return lowerLine.contains("physical efficiency")
                || lowerLine.contains("physical test")
                || lowerLine.matches(".*\\bpet\\b.*")
                || lowerLine.matches(".*\\bpst\\b.*");
    }

    private boolean containsMedicalStage(String lowerLine) {
        if (lowerLine == null || lowerLine.isBlank()) {
            return false;
        }
        if (lowerLine.contains("paramedical")) {
            return false;
        }
        return lowerLine.contains("medical examination")
                || lowerLine.contains("medical test")
                || lowerLine.matches(".*\\bmedical\\b.*");
    }

    private String clean(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String lower(String s) {
        return safe(s).toLowerCase(Locale.ROOT);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String digitsOnly(String s) {
        return s == null ? null : s.replaceAll("[^0-9]", "");
    }

    private int toInt(String s) {
        if (s == null || s.isBlank()) return -1;
        try {
            return Integer.parseInt(digitsOnly(s));
        } catch (Exception ex) {
            return -1;
        }
    }

    private boolean looksLikeDateValue(String s) {
        if (s == null) return false;
        String t = lower(s);
        return DATE_PATTERN.matcher(s).find()
                || containsAny(t, "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
                || t.contains("before exam")
                || t.contains("notify later");
    }

    private String extractAdvtNo(String text) {
        if (text == null) return null;
        String ctx = lower(text);
        boolean hasAdvtContext = containsAny(ctx,
                "advt", "advertisement", "notification no", "notification number",
                "notice no", "cen");
        Matcher m = ADVT_TOKEN.matcher(text);
        String best = null;
        while (m.find()) {
            String token = clean(m.group()).replaceAll("\\s+", " ");
            token = token.replaceAll("(?i)^(advt\\.?|advertisement|notification|notice)\\s*(no\\.?|number)?\\s*[:\\-]?\\s*", "");
            token = token.replace('_', '/');
            if (token.matches("\\d{1,3}/\\d{4}") && !hasAdvtContext) {
                continue;
            }
            if (!looksLikeValidAdvt(token)) continue;
            if (isAdvtJunkToken(token)) continue;
            if (containsAny(lower(token), "cen", "advt")) return token;
            if (best == null) best = token;
        }
        return best;
    }

    private boolean looksLikeValidAdvt(String value) {
        if (value == null || value.length() < 3) return false;
        String v = lower(value);
        if (v.equals("on") || v.equals("no") || v.equals("number")) return false;
        if (!value.matches(".*\\d.*")) return false;
        if (isLikelyPlainYearToken(v)) return false;
        // avoid plain dates being treated as advertisement number
        if (value.matches("\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}")) return false;
        if (isAdvtJunkToken(value)) return false;
        return true;
    }

    private boolean isLikelyRelatedPostLink(String href, String text) {
        String t = lower(text);
        if (containsAny(t, "latest", "related", "post", "today", "start", "last date")) {
            try {
                URI uri = URI.create(href);
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                return host.contains("sarkariresult.com.cm");
            } catch (Exception ex) {
                return true;
            }
        }
        return false;
    }

    private void putLinkWithPriority(Map<String, String> links, String key, String href) {
        String existing = links.get(key);
        if (existing == null) {
            links.put(key, href);
            return;
        }
        if (isBetterLink(key, href, existing)) {
            links.put(key, href);
        }
    }

    private boolean isBetterLink(String key, String candidate, String current) {
        if ("apply_online".equals(key) && isHomePage(current) && !isHomePage(candidate)) return true;
        String c = lower(candidate);
        String cur = lower(current);
        if ("official_notification".equals(key)) {
            boolean cIsPdf = c.endsWith(".pdf");
            boolean curIsPdf = cur.endsWith(".pdf");
            if (cIsPdf && !curIsPdf) return true;
        }
        return false;
    }

    private void finalizeResponse(MasterJobResponseDto dto) {
        if (dto.getAdvertisementNo() == null || dto.getAdvertisementNo().isBlank()) {
            String inferred = inferAdvertisementNo(dto);
            if (looksLikeValidAdvt(inferred)) {
                dto.setAdvertisementNo(inferred);
            }
        }

        if (dto.getEligibilityCriteria() != null) {
            String q = dto.getEligibilityCriteria().getQualification();
            if (q != null) {
                q = q.replaceFirst("(?i)^answer\\s*[:\\-]\\s*", "").trim();
                if (q.toLowerCase(Locale.ROOT).startsWith("question:")) {
                    q = null;
                }
                if ("-".equals(q) || "???".equals(q) || "--".equals(q) || "na".equalsIgnoreCase(q) || "n/a".equalsIgnoreCase(q)) {
                    q = null;
                }
                if (q != null && isBoilerplateQualification(q)) {
                    q = null;
                }
                if (q != null && !isStrongQualificationCandidate(q)) {
                    q = null;
                }
                dto.getEligibilityCriteria().setQualification(q);
            }
            if (dto.getEligibilityCriteria().getQualification() == null || dto.getEligibilityCriteria().getQualification().isBlank()) {
                String inferredQualification = inferQualification(dto);
                if (inferredQualification != null && !inferredQualification.isBlank()) {
                    dto.getEligibilityCriteria().setQualification(inferredQualification);
                }
            }
            if ((dto.getEligibilityCriteria().getQualification() == null || dto.getEligibilityCriteria().getQualification().isBlank())
                    && dto.getEligibilityCriteria().getPostWiseQualification() != null
                    && !dto.getEligibilityCriteria().getPostWiseQualification().isEmpty()) {
                Map.Entry<String, String> first = dto.getEligibilityCriteria().getPostWiseQualification().entrySet().iterator().next();
                dto.getEligibilityCriteria().setQualification(first.getKey() + ": " + first.getValue());
            }

            // If qualification is boilerplate, promote a concrete eligibility line from syllabus.
            String currentQ = dto.getEligibilityCriteria().getQualification();
            if ((currentQ == null || isBoilerplateQualification(currentQ))
                    && dto.getSyllabusOverview() != null
                    && !dto.getSyllabusOverview().isEmpty()) {
                List<String> updatedSyllabus = new ArrayList<>(dto.getSyllabusOverview());
                String picked = null;
                for (String s : updatedSyllabus) {
                    if (looksLikeQualificationLine(s)) {
                        picked = clean(s);
                        break;
                    }
                }
                if (picked != null) {
                    dto.getEligibilityCriteria().setQualification(picked);
                    final String pickedFinal = picked;
                    updatedSyllabus.removeIf(s -> clean(s).equalsIgnoreCase(pickedFinal));
                    dto.setSyllabusOverview(updatedSyllabus);
                }
            }

            if ((dto.getEligibilityCriteria().getQualification() == null || dto.getEligibilityCriteria().getQualification().isBlank())
                    && dto.getOtherTables() != null && !dto.getOtherTables().isEmpty()) {
                String qFromTable = inferQualificationFromOtherTables(dto.getOtherTables());
                if (qFromTable != null && !qFromTable.isBlank()) {
                    dto.getEligibilityCriteria().setQualification(qFromTable);
                }
            }
        }

        populateVacancyFallbackFromOtherTables(dto);

        if (dto.getOfficialLinks() != null) {
            Map<String, Object> cleanedLinks = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : dto.getOfficialLinks().entrySet()) {
                if (e.getValue() == null) continue;
                if (e.getValue() instanceof String s) {
                    if (!s.isBlank()) cleanedLinks.put(e.getKey(), s);
                    continue;
                }
                if (e.getValue() instanceof Map<?, ?> map) {
                    Map<String, String> nested = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> child : map.entrySet()) {
                        if (child.getKey() == null || child.getValue() == null) continue;
                        String k = clean(String.valueOf(child.getKey()));
                        String v = clean(String.valueOf(child.getValue()));
                        if (!k.isBlank() && !v.isBlank()) nested.put(k, v);
                    }
                    if (!nested.isEmpty()) cleanedLinks.put(e.getKey(), nested);
                }
            }
            dto.setOfficialLinks(applyExistingLinkCleaner(cleanedLinks));
        }

        normalizeConductingBody(dto);

        List<String> cleanedSteps = new ArrayList<>();
        for (String s : dto.getApplicationProcess()) {
            String l = lower(s);
            if (containsAny(l, "click here", "submit / login", "login button", "apply online link")) {
                continue;
            }
            cleanedSteps.add(s);
        }
        if (cleanedSteps.isEmpty()) {
            String apply = getOfficialLinkString(dto, "apply_online", "apply online link");
            String lastDate = getImportantDateString(dto, "online apply last date", "online application last date", "last date");
            if (apply != null && !apply.isBlank()) {
                cleanedSteps.add("Visit the apply link and complete registration: " + apply);
            }
            if (lastDate != null && !lastDate.isBlank()) {
                cleanedSteps.add("Complete and submit the application before " + lastDate + ".");
            } else {
                cleanedSteps.add("Complete and submit the application form online before the deadline.");
            }
        }
        dto.setApplicationProcess(cleanedSteps);
    }

    private void applySeoAndAdsensePolish(MasterJobResponseDto dto) {
        if (dto == null) {
            return;
        }

        String title = sanitizeSeoText(dto.getTitle(), true);
        if (title == null || title.length() < 35) {
            title = buildSeoTitleFallback(dto);
        }
        if (title != null && title.length() > 110) {
            title = trimToWordBoundary(title, 110);
        }
        dto.setTitle(title);

        String shortDescription = sanitizeSeoText(dto.getShortDescription(), false);
        if (shortDescription == null || shortDescription.length() < 90) {
            shortDescription = buildSeoDescriptionFallback(dto);
        }
        if (shortDescription != null && shortDescription.length() > 260) {
            shortDescription = trimToWordBoundary(shortDescription, 260);
        }
        dto.setShortDescription(shortDescription);

        dto.setImportantNotes(cleanAndLimitLines(dto.getImportantNotes(), 8, 220));
        dto.setApplicationProcess(cleanAndLimitLines(dto.getApplicationProcess(), 8, 220));
    }

    private String buildSeoTitleFallback(MasterJobResponseDto dto) {
        String postName = clean(safe(dto.getPostName()));
        String body = clean(safe(dto.getConductingBody()));
        String adv = clean(safe(dto.getAdvertisementNo()));
        String year = null;
        if (dto.getDateUpdated() != null && dto.getDateUpdated().matches(".*\\b\\d{4}\\b.*")) {
            year = dto.getDateUpdated().replaceAll(".*?(\\d{4}).*", "$1");
        } else if (dto.getDatePosted() != null && dto.getDatePosted().matches(".*\\b\\d{4}\\b.*")) {
            year = dto.getDatePosted().replaceAll(".*?(\\d{4}).*", "$1");
        }

        List<String> parts = new ArrayList<>();
        if (body != null && !body.isBlank()) {
            parts.add(body);
        }
        if (postName != null && !postName.isBlank()) {
            parts.add(postName);
        } else if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            parts.add(clean(dto.getTitle()));
        }
        if (year != null && !year.isBlank()) {
            parts.add(year);
        }
        if (adv != null && !adv.isBlank()) {
            parts.add("Advt " + adv);
        }
        String joined = String.join(" ");
        String normalized = sanitizeSeoText(joined, true);
        if (normalized == null || normalized.isBlank()) {
            return sanitizeSeoText(dto.getTitle(), true);
        }
        return trimToWordBoundary(normalized, 110);
    }

    private String buildSeoDescriptionFallback(MasterJobResponseDto dto) {
        String title = sanitizeSeoText(dto.getTitle(), true);
        String postName = sanitizeSeoText(dto.getPostName(), false);
        String body = sanitizeSeoText(dto.getConductingBody(), false);
        String lastDate = getImportantDateString(dto,
                "online apply last date",
                "online application last date",
                "last date");
        String applyLink = getOfficialLinkString(dto, "apply_online", "apply online");

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title).append(". ");
        }
        if (postName != null && !postName.isBlank() && (title == null || !lower(title).contains(lower(postName)))) {
            sb.append("Post: ").append(postName).append(". ");
        }
        if (body != null && !body.isBlank()) {
            sb.append("Conducting body: ").append(body).append(". ");
        }
        if (lastDate != null && !lastDate.isBlank()) {
            sb.append("Apply before ").append(lastDate).append(". ");
        }
        if (applyLink != null && !applyLink.isBlank()) {
            sb.append("Check official apply link and notification for exact details.");
        } else {
            sb.append("Check official notification for exact eligibility, fee and process.");
        }

        String out = sanitizeSeoText(sb.toString(), false);
        if (out == null) {
            return null;
        }
        if (out.length() < 120) {
            out = out + " Selection and eligibility are subject to official rules.";
        }
        return trimToWordBoundary(out, 260);
    }

    private List<String> cleanAndLimitLines(List<String> lines, int maxItems, int maxCharsPerItem) {
        List<String> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return out;
        }
        for (String line : lines) {
            String cleanLine = sanitizeSeoText(line, false);
            if (cleanLine == null || cleanLine.isBlank()) {
                continue;
            }
            if (cleanLine.length() > maxCharsPerItem) {
                cleanLine = trimToWordBoundary(cleanLine, maxCharsPerItem);
            }
            out.add(cleanLine);
            if (out.size() >= maxItems) {
                break;
            }
        }
        return out;
    }

    private String sanitizeSeoText(String input, boolean titleMode) {
        if (input == null) {
            return null;
        }
        String value = clean(input);
        if (value == null || value.isBlank()) {
            return null;
        }

        for (String phrase : AD_SENSE_UNSAFE_PHRASES) {
            value = value.replaceAll("(?i)\\b" + Pattern.quote(phrase) + "\\b", " ");
        }
        value = value.replaceAll("(?i)\\bclick\\s+here\\b", " ");
        value = value.replaceAll("!{2,}", "!");
        value = value.replaceAll("[\\r\\n]+", " ");
        value = value.replaceAll("\\s{2,}", " ").trim();
        value = value.replaceAll("^[\\-:|,\\s]+", "").replaceAll("[\\-:|,\\s]+$", "");

        if (titleMode) {
            value = value.replaceAll("(?i)\\b(apply now|hurry|urgent)\\b", "");
            value = value.replaceAll("\\s{2,}", " ").trim();
            if (!value.isBlank() && value.equals(value.toUpperCase(Locale.ROOT)) && value.length() > 8) {
                value = toTitleCase(value.toLowerCase(Locale.ROOT));
            }
        }
        return value.isBlank() ? null : value;
    }

    private String toTitleCase(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String[] parts = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (part.length() == 1) {
                sb.append(part.toUpperCase(Locale.ROOT));
            } else {
                sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String trimToWordBoundary(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        String slice = text.substring(0, Math.max(1, maxLen));
        int cut = slice.lastIndexOf(' ');
        if (cut < 40) {
            return slice.trim();
        }
        return slice.substring(0, cut).trim();
    }

    private void populateVacancyFallbackFromOtherTables(MasterJobResponseDto dto) {
        if (dto.getVacancyDetails() == null || dto.getOtherTables() == null || dto.getOtherTables().isEmpty()) {
            return;
        }

        MasterJobResponseDto.VacancyDetailsDto vacancy = dto.getVacancyDetails();
        boolean alreadyPresent =
                (vacancy.getPostWise() != null && !vacancy.getPostWise().isEmpty())
                        || (vacancy.getTableRows() != null && !vacancy.getTableRows().isEmpty())
                        || (vacancy.getTotalVacancy() != null && !vacancy.getTotalVacancy().isBlank());
        if (alreadyPresent) {
            return;
        }

        Map<String, String> postWise = new LinkedHashMap<>();
        List<Map<String, String>> tableRows = new ArrayList<>();
        int total = 0;

        for (List<Map<String, String>> table : dto.getOtherTables().values()) {
            if (table == null) continue;
            for (Map<String, String> row : table) {
                if (row == null || row.isEmpty()) continue;
                String post = pickFirst(row, "post_name", "post", "position", "name");
                String countRaw = pickFirst(row, "no_of_post", "no_of_posts", "vacancy", "vacancies", "seat", "seats", "total_post");
                String count = extractFirstNumber(countRaw);
                if (post == null || post.isBlank() || count == null || count.isBlank()) continue;

                post = clean(post);
                postWise.putIfAbsent(post, count);

                Map<String, String> r = new LinkedHashMap<>();
                r.put("post_name", post);
                r.put("no_of_post", clean(countRaw == null ? count : countRaw));
                tableRows.add(r);
                total += toInt(count);
            }
        }

        if (!postWise.isEmpty()) {
            vacancy.setPostWise(postWise);
            vacancy.setTableRows(tableRows);
            if (total > 0) {
                vacancy.setTotalVacancy(String.valueOf(total));
            }
        }
    }

    private String inferQualificationFromOtherTables(Map<String, List<Map<String, String>>> otherTables) {
        for (List<Map<String, String>> table : otherTables.values()) {
            if (table == null) continue;
            for (Map<String, String> row : table) {
                if (row == null || row.isEmpty()) continue;
                String q = pickFirst(row, "eligibility_criteria", "eligibility", "qualification", "education");
                if (q != null && isStrongQualificationCandidate(q)) {
                    return q;
                }
            }
        }
        return null;
    }

    private String pickFirst(Map<String, String> row, String... keyHints) {
        if (row == null || row.isEmpty()) return null;
        for (String hint : keyHints) {
            String h = lower(hint);
            for (Map.Entry<String, String> e : row.entrySet()) {
                String k = lower(clean(e.getKey()));
                if (k.contains(h)) {
                    String v = clean(e.getValue());
                    if (!v.isBlank()) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private String inferAdvertisementNo(MasterJobResponseDto dto) {
        List<String> candidates = new ArrayList<>();
        candidates.add(dto.getTitle());
        candidates.add(dto.getShortDescription());
        candidates.add(getOfficialLinkString(dto, "official_notification", "official notification"));

        for (String text : candidates) {
            if (text == null || text.isBlank()) continue;
            String v = extractAdvtNo(text);
            if (looksLikeValidAdvt(v)) return v;
        }

        return null;
    }

    private String extractConductingBody(String title, List<String> lines, Map<String, String> kvLines) {
        for (Map.Entry<String, String> e : kvLines.entrySet()) {
            String k = lower(e.getKey());
            if (containsAny(k, "conducting body", "organization", "department", "board", "commission", "authority", "recruiting")) {
                String v = clean(e.getValue());
                if (!v.isBlank() && !v.equals("-") && looksLikeOrgName(v)) return v;
            }
        }

        Pattern bodyPattern = Pattern.compile("(?i)\\b([A-Z][A-Za-z&().\\-\\s]{3,120}(Board|Commission|Department|Authority|University|Council|Ministry|Police|Army))\\b");
        Matcher tm = bodyPattern.matcher(title);
        if (tm.find()) {
            String candidate = clean(tm.group(1));
            if (looksLikeOrgName(candidate)) return candidate;
        }

        for (String line : lines) {
            if (line.length() > 180) continue;
            Matcher lm = bodyPattern.matcher(line);
            if (lm.find()) {
                String candidate = clean(lm.group(1));
                if (looksLikeOrgName(candidate)) return candidate;
            }
        }

        for (String line : lines) {
            String l = lower(line);
            if (containsAny(l, "has released", "has published", "issued")) {
                int idx = l.indexOf("has ");
                if (idx > 2) {
                    String candidate = clean(line.substring(0, idx));
                    candidate = candidate.replaceAll("\\(.*?\\)", "").trim();
                    if (looksLikeOrgName(candidate)) return candidate;
                }
            }
        }
        return null;
    }

    private boolean isHomePage(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            return path == null || path.isBlank() || "/".equals(path.trim());
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isLikelyInternalArticleLink(String href, String source) {
        try {
            URI h = URI.create(href);
            URI s = URI.create(source);
            String hh = h.getHost() == null ? "" : h.getHost().toLowerCase(Locale.ROOT);
            String sh = s.getHost() == null ? "" : s.getHost().toLowerCase(Locale.ROOT);
            if (!hh.equals(sh)) return false;
            String path = h.getPath() == null ? "" : h.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("/wp-content/uploads/") || path.endsWith(".pdf")) return false;
            if (path.isBlank() || "/".equals(path)) return false;
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean looksLikeOrgName(String value) {
        if (value == null || value.length() < 3 || value.length() > 90) return false;
        String v = lower(value);
        if (containsAny(v, "degree", "subject", "recognized university", "certificate", "document", "marksheet", "candidates must", "go to", "section for", "click", "login", "portal")) {
            return false;
        }
        return containsAny(v, "board", "commission", "department", "authority", "university", "council", "ministry", "army", "police", "agency",
                "sangathan", "samiti", "society", "selection board", "recruitment board");
    }

    private boolean looksLikeLinkJunk(String keyOrText) {
        String t = lower(keyOrText);
        return containsAny(t, "you_may_also", "latest_posts", "related_posts", "today", "last_date", "_click_here_click_here")
                || t.matches("^\\d{1,2}_[a-z]{3,9}_\\d{4}$");
    }

    private String getOfficialLinkString(MasterJobResponseDto dto, String... keyHints) {
        if (dto == null || dto.getOfficialLinks() == null || keyHints == null) return null;

        for (String keyHint : keyHints) {
            if (keyHint == null) continue;

            Object direct = dto.getOfficialLinks().get(keyHint);
            String directValue = firstStringValue(direct);
            if (directValue != null) return directValue;

            String h = lower(keyHint);
            for (Map.Entry<String, Object> e : dto.getOfficialLinks().entrySet()) {
                String k = lower(e.getKey());
                if (k.contains(h)) {
                    String v = firstStringValue(e.getValue());
                    if (v != null) return v;
                }
            }
        }

        return null;
    }

    private String getImportantDateString(MasterJobResponseDto dto, String... keyHints) {
        if (dto == null || dto.getImportantDates() == null || dto.getImportantDates().isEmpty()) return null;
        for (String hint : keyHints) {
            if (hint == null) continue;
            String direct = dto.getImportantDates().get(hint);
            if (direct != null && !direct.isBlank()) return direct;
            String h = lower(hint);
            for (Map.Entry<String, String> e : dto.getImportantDates().entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                if (lower(e.getKey()).contains(h) && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private String firstStringValue(Object value) {
        if (value == null) return null;
        if (value instanceof String s) {
            s = clean(s);
            return s.isBlank() ? null : s;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                String nested = firstStringValue(v);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyExistingLinkCleaner(Map<String, Object> links) {
        if (links == null || links.isEmpty()) {
            return links;
        }
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("official_links", new LinkedHashMap<>(links));

            String cleanedJson = SarkariLinkCleaner.cleanLinks(wrapper);
            JsonNode root = objectMapper.readTree(cleanedJson);
            JsonNode node = root.path("official_links");
            if (!node.isObject()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.convertValue(node, Map.class);
        } catch (Exception ignored) {
            // Keep existing deterministic output if cleaner fails.
            return links;
        }
    }

    private void normalizeConductingBody(MasterJobResponseDto dto) {
        String current = dto.getConductingBody();
        String inferred = inferConductingBody(dto);

        if ((inferred == null || inferred.isBlank())
                && current != null
                && !current.isBlank()
                && !isConductingBodyConsistent(current, dto)) {
            dto.setConductingBody(null);
            return;
        }
        if (inferred == null || inferred.isBlank()) {
            return;
        }
        if (current == null || current.isBlank() || !isConductingBodyConsistent(current, dto)) {
            dto.setConductingBody(inferred);
        }
    }

    private String inferConductingBody(MasterJobResponseDto dto) {
        String fromDescription = extractBodyPrefix(dto.getShortDescription());
        if (looksLikeOrgName(fromDescription)) {
            return fromDescription;
        }

        String fromTitle = extractBodyPrefix(dto.getTitle());
        if (looksLikeOrgName(fromTitle)) {
            return fromTitle;
        }

        String official = getOfficialLinkString(dto, "official_website", "official website");
        if (official != null) {
            String hostBased = inferOrgFromHost(official);
            if (looksLikeOrgName(hostBased)) {
                return hostBased;
            }
        }
        return null;
    }

    private String extractBodyPrefix(String text) {
        if (text == null || text.isBlank()) return null;
        String t = clean(text);
        String lower = t.toLowerCase(Locale.ROOT);
        String[] markers = {" has released", " has announced", " has issued", " invites applications", " invite applications"};
        int cut = -1;
        for (String m : markers) {
            int idx = lower.indexOf(m);
            if (idx > 2 && (cut == -1 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut > 2) {
            String candidate = clean(t.substring(0, cut));
            candidate = candidate.replaceAll("^[\\-:,\\s]+|[\\-:,\\s]+$", "").replaceAll(",+$", "").trim();
            return candidate;
        }
        return null;
    }

    private String inferOrgFromHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank()) return null;
            String[] parts = host.split("\\.");
            if (parts.length < 2) return null;
            String core = parts[parts.length - 2];
            if (core.length() < 3) core = parts[Math.max(0, parts.length - 3)];
            core = core.replaceAll("[^a-z]", " ").trim();
            if (core.isBlank()) return null;
            return core.substring(0, 1).toUpperCase(Locale.ROOT) + core.substring(1);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isConductingBodyConsistent(String body, MasterJobResponseDto dto) {
        if (body == null || body.isBlank()) return false;
        String b = lower(body);
        String t = lower(dto.getTitle());
        String s = lower(dto.getSource());

        List<String> tokens = new ArrayList<>();
        for (String w : b.split("[^a-z0-9]+")) {
            if (w.length() < 4) continue;
            if (containsAny(w, "board", "commission", "department", "authority", "university", "council", "ministry", "competitive", "examination")) {
                continue;
            }
            tokens.add(w);
        }
        if (tokens.isEmpty()) return true;
        for (String token : tokens) {
            if (t.contains(token) || s.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String extractAgeValue(String text, boolean pickMinimum) {
        if (text == null || text.isBlank()) return null;
        String normalized = clean(text);
        String lower = lower(normalized);
        int asOnIndex = lower.indexOf("as on");
        if (asOnIndex > 0) {
            normalized = normalized.substring(0, asOnIndex);
        }

        List<Double> nums = new ArrayList<>();

        Matcher years = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d)?)\\s*(?:years?|yrs?)").matcher(normalized);
        while (years.find()) {
            try {
                double n = Double.parseDouble(years.group(1));
                if (n >= 5 && n <= 80) nums.add(n);
            } catch (Exception ignored) {
            }
        }

        Matcher range = Pattern.compile("(?i)(\\d{1,2}(?:\\.\\d)?)\\s*(?:-|to)\\s*(\\d{1,2}(?:\\.\\d)?)").matcher(normalized);
        while (range.find()) {
            try {
                double a = Double.parseDouble(range.group(1));
                double b = Double.parseDouble(range.group(2));
                if (a >= 5 && a <= 80) nums.add(a);
                if (b >= 5 && b <= 80) nums.add(b);
            } catch (Exception ignored) {
            }
        }

        Matcher m = Pattern.compile("\\d{1,2}(?:\\.\\d)?").matcher(normalized);
        while (m.find()) {
            try {
                double n = Double.parseDouble(m.group());
                if (n >= 5 && n <= 80) nums.add(n);
            } catch (Exception ignored) {
            }
        }
        if (nums.isEmpty()) return null;

        double chosen = nums.get(0);
        for (double n : nums) {
            if (pickMinimum && n < chosen) chosen = n;
            if (!pickMinimum && n > chosen) chosen = n;
        }
        if (Math.floor(chosen) == chosen) {
            return String.valueOf((int) chosen);
        }
        return String.valueOf(chosen);
    }

    private String extractAsOnDate(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = Pattern.compile("(?i)as\\s+on\\s*[:\\-]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})").matcher(text);
        if (m.find()) {
            return clean(m.group(1));
        }
        return null;
    }

    private String inferQualification(MasterJobResponseDto dto) {
        List<String> candidates = new ArrayList<>();
        if (dto.getShortDescription() != null) candidates.add(dto.getShortDescription());
        if (dto.getImportantNotes() != null) candidates.addAll(dto.getImportantNotes());
        if (dto.getSyllabusOverview() != null) candidates.addAll(dto.getSyllabusOverview());

        for (String line : candidates) {
            if (line == null) continue;
            String t = clean(line);
            String l = lower(t);
            if (t.length() < 35 || t.length() > 320) continue;
            if (!containsAny(l, "eligible", "qualification", "class 10", "class 12", "graduate", "bachelor", "master", "degree", "diploma", "iti", "ncvt", "scvt", "matric")) {
                continue;
            }
            if (containsAny(l, "click here", "apply online", "latest post", "related post", "official website")) {
                continue;
            }
            if (isStrongQualificationCandidate(t)) {
                return t;
            }
        }
        return null;
    }

    private boolean looksLikeQualificationLine(String text) {
        if (text == null) return false;
        String l = lower(text);
        return containsAny(l, "eligible", "qualification", "class 10", "class 12", "graduate", "bachelor", "master",
                "degree", "diploma", "iti", "ncvt", "scvt", "matric", "minimum", "marks");
    }

    private boolean isBoilerplateQualification(String text) {
        if (text == null) return true;
        String l = lower(text);
        return containsAny(l,
                "read the official notification",
                "official notification",
                "carefully read",
                "verify details",
                "before applying",
                "last date",
                "age limit",
                "education qualification",
                "eligibility criteria",
                "?????????????????? ?????? ?????? ??????????????????",
                "??????????????? ???????????? ?????? ????????????");
    }

    private boolean isAdvtJunkToken(String token) {
        String t = lower(token);
        return containsAny(t, "uploads", "wp-content", "http", "https", "jpg", "png", "pdf", "download")
                || isLikelyPlainYearToken(t)
                || t.matches("\\d{4}/\\d{2}")
                || t.matches("\\d{2}/\\d{2}");
    }

    private boolean isLikelyPlainYearToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (!t.matches("\\d{4}")) return false;
        try {
            int year = Integer.parseInt(t);
            return year >= 1900 && year <= 2099;
        } catch (Exception ex) {
            return false;
        }
    }

    private void enhanceWithAi(MasterJobResponseDto dto) {
        try {
            String rawJson = objectMapper.writeValueAsString(dto);
            String enhancedJson = openAIService.refineMasterJobContent(rawJson);
            MasterJobResponseDto aiDto = objectMapper.readValue(enhancedJson, MasterJobResponseDto.class);
            mergeEnhancedDto(dto, aiDto);
        } catch (Exception ignored) {
            // keep deterministic scraper output if AI call fails
        }
    }

    private void mergeEnhancedDto(MasterJobResponseDto target, MasterJobResponseDto source) {
        if (target == null || source == null) {
            return;
        }

        if (hasText(source.getTitle())) target.setTitle(clean(source.getTitle()));
        if (hasText(source.getShortDescription())) target.setShortDescription(clean(source.getShortDescription()));
        if (hasText(source.getAdvertisementNo())) target.setAdvertisementNo(clean(source.getAdvertisementNo()));
        if (hasText(source.getPostName())) target.setPostName(clean(source.getPostName()));
        if (hasText(source.getConductingBody())) target.setConductingBody(clean(source.getConductingBody()));
        if (hasText(source.getDatePosted())) target.setDatePosted(clean(source.getDatePosted()));
        if (hasText(source.getDateUpdated())) target.setDateUpdated(clean(source.getDateUpdated()));
        if (hasObjectMapContent(source.getJobLocation())) target.setJobLocation(source.getJobLocation());
        if (hasText(source.getPayScale())) target.setPayScale(clean(source.getPayScale()));

        if (hasStringMapContent(source.getImportantDates())) target.setImportantDates(source.getImportantDates());
        if (hasApplicationFeeContent(source.getApplicationFee())) target.setApplicationFee(source.getApplicationFee());
        if (hasEligibilityContent(source.getEligibilityCriteria())) target.setEligibilityCriteria(source.getEligibilityCriteria());
        if (hasVacancyContent(source.getVacancyDetails())) target.setVacancyDetails(source.getVacancyDetails());
        if (hasStringListContent(source.getApplicationProcess())) target.setApplicationProcess(source.getApplicationProcess());
        if (hasObjectMapContent(source.getExamScheme())) target.setExamScheme(source.getExamScheme());
        if (hasStringListContent(source.getSelectionProcess())) target.setSelectionProcess(source.getSelectionProcess());
        if (hasStringListContent(source.getImportantNotes())) target.setImportantNotes(source.getImportantNotes());
        if (hasObjectMapContent(source.getOfficialLinks())) target.setOfficialLinks(source.getOfficialLinks());
        if (hasStringListContent(source.getSyllabusOverview())) target.setSyllabusOverview(source.getSyllabusOverview());
        if (hasOtherTablesContent(source.getOtherTables())) target.setOtherTables(source.getOtherTables());
    }

    private boolean hasApplicationFeeContent(MasterJobResponseDto.ApplicationFeeDto fee) {
        if (fee == null) {
            return false;
        }
        if (hasText(fee.getGeneralObc())
                || hasText(fee.getScStEbcFemaleTransgender())
                || hasText(fee.getRefundGeneralObcAfterCbt())
                || hasText(fee.getRefundScStEbcFemaleTransgenderAfterCbt())) {
            return true;
        }
        return hasStringListContent(fee.getPaymentMode())
                || (fee.getFeeDetail() != null && !fee.getFeeDetail().isEmpty())
                || (fee.getExtraFields() != null && !fee.getExtraFields().isEmpty());
    }

    private boolean hasEligibilityContent(MasterJobResponseDto.EligibilityCriteriaDto eligibility) {
        if (eligibility == null) {
            return false;
        }
        if (hasText(eligibility.getGender())
                || hasText(eligibility.getMinimumAge())
                || hasText(eligibility.getMaximumAge())
                || hasText(eligibility.getAgeAsOn())
                || hasText(eligibility.getQualification())
                || hasText(eligibility.getResidencyRequirement())) {
            return true;
        }
        return (eligibility.getPostWiseQualification() != null && !eligibility.getPostWiseQualification().isEmpty())
                || (eligibility.getExtraFields() != null && !eligibility.getExtraFields().isEmpty());
    }

    private boolean hasVacancyContent(MasterJobResponseDto.VacancyDetailsDto vacancy) {
        if (vacancy == null) {
            return false;
        }
        if (hasText(vacancy.getTotalVacancy())) {
            return true;
        }
        return (vacancy.getPostWise() != null && !vacancy.getPostWise().isEmpty())
                || (vacancy.getCategoryWise() != null && !vacancy.getCategoryWise().isEmpty())
                || (vacancy.getTableRows() != null && !vacancy.getTableRows().isEmpty())
                || (vacancy.getExtraFields() != null && !vacancy.getExtraFields().isEmpty());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasStringListContent(List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (hasText(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStringMapContent(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (hasText(entry.getKey()) && hasText(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasObjectMapContent(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!hasText(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof String s) {
                if (hasText(s)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean hasOtherTablesContent(Map<String, List<Map<String, String>>> tables) {
        if (tables == null || tables.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, List<Map<String, String>>> entry : tables.entrySet()) {
            if (!hasText(entry.getKey())) {
                continue;
            }
            List<Map<String, String>> rows = entry.getValue();
            if (rows != null && !rows.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            String out = clean(s);
            return out.isBlank() ? null : out;
        }
        return null;
    }
}

