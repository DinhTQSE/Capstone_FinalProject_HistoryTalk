package com.historytalk.service.document;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Layout-aware PDF text post-processor shared by both NATIVE_TEXT (PDFBox) and SCANNED_IMAGE (OCR) pipelines.
 *
 * <p>Solves two systemic PDF extraction defects without brittle regex workarounds:
 * <ol>
 *   <li>Cross-page geometric header/footer detection (top/bottom margin bands + digit-normalized repetition).</li>
 *   <li>Layout-aware paragraph reflow and paragraph-boundary image placement (eliminating mid-sentence line breaks).</li>
 * </ol>
 */
public final class PdfLayoutProcessor {

    private static final float TOP_MARGIN_RATIO = 0.12f;
    private static final float BOTTOM_MARGIN_RATIO = 0.88f;
    private static final float LINE_GAP_PARAGRAPH_MULTIPLIER = 1.45f;
    private static final float THIN_MARGIN_HEIGHT_RATIO = 0.55f;
    private static final double MARGIN_FUZZY_SIMILARITY_THRESHOLD = 0.76d;

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
    private static final Pattern DIGITS_RUN = Pattern.compile("\\d+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern RULE_CHAR_RUN = Pattern.compile("[-—_=~]{4,}");
    private static final Pattern PURE_PAGE_NUMBER = Pattern.compile("^\\d{1,4}$");
    private static final Pattern PAGE_FRACTION = Pattern.compile("^\\d{1,4}\\s*/\\s*\\d{1,4}$");
    private static final Pattern PAGE_LABEL = Pattern.compile("^(?:trang|page|tr\\.)\\s*\\d{1,4}(?:\\s*(?:/|of|trên)\\s*\\d{1,4})?$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WEB_PRINT_URL = Pattern.compile("^(?:https?://\\S+|www\\.\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEB_PRINT_TIMESTAMP = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2,4}(?:,?\\s+\\d{1,2}:\\d{2}(?:\\s*(?:AM|PM))?)?.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIBD_PRINT_NOISE = Pattern.compile("(?i).*\\b(?:scribd|docdownloader|pdfcoffee)\\b.*");

    private static final Pattern IMAGE_CAPTION_LINE = Pattern.compile(
            "^(?:Hình|Lược đồ|Bản đồ|Ảnh tư liệu|Sơ đồ|Tranh|Di tích)\\s*\\d*[.:]?\\s*.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LIST_ITEM_LINE = Pattern.compile(
            "^(?:[-*•–—]|\\d{1,3}[.)]|[a-zA-Z][.)])\\s+.*$"
    );
    private static final Pattern SECTION_HEADING_LINE = Pattern.compile(
            "^(?:#{1,6}\\s+.*|(?:PHẦN|CHƯƠNG|MỤC|BÀI|TIẾT)\\s+[\\p{L}\\dIVXLCDM]+.*|[IVXLCDM]+\\.\\s+\\S.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private PdfLayoutProcessor() {
    }

    public record ExtractedLine(
            int pageNumber,
            float pageWidth,
            float pageHeight,
            float leftX,
            float rightX,
            float topY,
            float bottomY,
            float fontSize,
            String text
    ) {
        public float height() {
            return Math.max(1f, bottomY - topY);
        }
    }

    public record PageImagePlacement(
            int pageNumber,
            float topY,
            float bottomY,
            String markdownBlock
    ) {
    }

    private enum MarginBand {
        TOP,
        BODY,
        BOTTOM
    }

    private record BandSignatureKey(MarginBand band, String signature) {
    }

    private record ParitySignatureKey(MarginBand band, int pageParity, String signature) {
    }

    private record PageMargins(float leftX, float rightX) {
    }

    private static final class PageSpan {
        final int pageNumber;
        float minY;
        float maxY;

        PageSpan(int pageNumber, float minY, float maxY) {
            this.pageNumber = pageNumber;
            this.minY = minY;
            this.maxY = maxY;
        }

        void expand(float topY, float bottomY) {
            this.minY = Math.min(this.minY, topY);
            this.maxY = Math.max(this.maxY, bottomY);
        }
    }

    private static final class ParagraphBlock {
        final StringBuilder text = new StringBuilder();
        final List<PageSpan> pageSpans = new ArrayList<>();
        ExtractedLine lastLine;

        ParagraphBlock(ExtractedLine firstLine) {
            this.text.append(firstLine.text().trim());
            this.pageSpans.add(new PageSpan(firstLine.pageNumber(), firstLine.topY(), firstLine.bottomY()));
            this.lastLine = firstLine;
        }

        void appendLine(ExtractedLine line) {
            String trimmed = line.text().trim();
            if (!trimmed.isEmpty()) {
                if (this.text.length() > 0) {
                    this.text.append(' ');
                }
                this.text.append(trimmed);
            }
            PageSpan currentSpan = pageSpans.get(pageSpans.size() - 1);
            if (currentSpan.pageNumber == line.pageNumber()) {
                currentSpan.expand(line.topY(), line.bottomY());
            } else {
                pageSpans.add(new PageSpan(line.pageNumber(), line.topY(), line.bottomY()));
            }
            this.lastLine = line;
        }

        boolean hasPage(int pageNumber) {
            for (PageSpan span : pageSpans) {
                if (span.pageNumber == pageNumber) {
                    return true;
                }
            }
            return false;
        }

        PageSpan spanForPage(int pageNumber) {
            for (PageSpan span : pageSpans) {
                if (span.pageNumber == pageNumber) {
                    return span;
                }
            }
            return null;
        }
    }

    /**
     * Filter out running headers, footers, margin page numbers, and web-print metadata
     * using margin band geometry and cross-page normalized repetition statistics.
     */
    public static List<ExtractedLine> filterHeadersAndFooters(List<ExtractedLine> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return List.of();
        }

        List<ExtractedLine> nonBlank = rawLines.stream()
                .filter(l -> l != null && l.text() != null && !l.text().isBlank())
                .sorted(Comparator.comparingInt(ExtractedLine::pageNumber)
                        .thenComparingDouble(ExtractedLine::topY)
                        .thenComparingDouble(ExtractedLine::leftX))
                .toList();

        if (nonBlank.isEmpty()) {
            return List.of();
        }

        Set<Integer> distinctPages = new HashSet<>();
        int evenPagesCount = 0;
        int oddPagesCount = 0;
        for (ExtractedLine line : nonBlank) {
            if (distinctPages.add(line.pageNumber())) {
                if (line.pageNumber() % 2 == 0) {
                    evenPagesCount++;
                } else {
                    oddPagesCount++;
                }
            }
        }
        int totalPages = distinctPages.size();

        Map<BandSignatureKey, Set<Integer>> overallPagesBySig = new HashMap<>();
        Map<ParitySignatureKey, Set<Integer>> parityPagesBySig = new HashMap<>();

        for (ExtractedLine line : nonBlank) {
            MarginBand band = classifyBand(line);
            if (band == MarginBand.BODY) {
                continue;
            }
            String trimmed = normalizeWhitespace(line.text());
            if (trimmed.length() > 160) {
                continue;
            }
            String sig = normalizeSignature(trimmed);
            if (sig.isEmpty()) {
                continue;
            }
            overallPagesBySig.computeIfAbsent(new BandSignatureKey(band, sig), k -> new HashSet<>())
                    .add(line.pageNumber());
            parityPagesBySig.computeIfAbsent(new ParitySignatureKey(band, line.pageNumber() % 2, sig), k -> new HashSet<>())
                    .add(line.pageNumber());
        }

        int overallThreshold = (totalPages == 2)
                ? 2
                : Math.max(2, (int) Math.ceil(totalPages * 0.35));

        float medianBodyHeight = computeMedianBodyHeight(nonBlank);

        Map<MarginBand, Map<String, Integer>> overallClusterCountsByBand = buildFuzzyClusterCountsByBand(overallPagesBySig);
        Map<String, Map<String, Integer>> parityClusterCounts = buildFuzzyClusterCountsByParity(parityPagesBySig);

        List<ExtractedLine> filtered = new ArrayList<>(nonBlank.size());
        for (ExtractedLine line : nonBlank) {
            String trimmed = normalizeWhitespace(line.text());
            if (isSymbolOrRuleNoise(trimmed)) {
                continue;
            }

            MarginBand band = classifyBand(line);
            if (band != MarginBand.BODY) {
                if (isMarginBoilerplateNoise(trimmed)
                        || isThinMarginArtifact(line, trimmed, medianBodyHeight)) {
                    continue;
                }

                if (totalPages >= 2 && trimmed.length() <= 160) {
                    String sig = normalizeSignature(trimmed);
                    String canonical = canonicalMarginKey(trimmed);
                    boolean isImageCaption = IMAGE_CAPTION_LINE.matcher(trimmed).matches();

                    int exactOverall = overallPagesBySig
                            .getOrDefault(new BandSignatureKey(band, sig), Set.of())
                            .size();
                    int fuzzyOverall = canonical.isEmpty()
                            ? 0
                            : overallClusterCountsByBand.getOrDefault(band, Map.of()).getOrDefault(canonical, 0);
                    int overallCount = Math.max(exactOverall, fuzzyOverall);

                    int exactParity = parityPagesBySig
                            .getOrDefault(new ParitySignatureKey(band, line.pageNumber() % 2, sig), Set.of())
                            .size();
                    String parityBucketKey = band.name() + ":" + (line.pageNumber() % 2);
                    int fuzzyParity = canonical.isEmpty()
                            ? 0
                            : parityClusterCounts.getOrDefault(parityBucketKey, Map.of()).getOrDefault(canonical, 0);
                    int parityCount = Math.max(exactParity, fuzzyParity);

                    int parityTotal = (line.pageNumber() % 2 == 0) ? evenPagesCount : oddPagesCount;
                    int parityThreshold = Math.max(2, (int) Math.ceil(parityTotal * 0.5));

                    boolean repeatedOverall = overallCount >= overallThreshold;
                    boolean repeatedParity = totalPages >= 3 && parityTotal >= 2 && parityCount >= parityThreshold;

                    if ((repeatedOverall || repeatedParity) && (!isImageCaption || overallCount >= 3)) {
                        continue;
                    }
                }
            }

            filtered.add(new ExtractedLine(
                    line.pageNumber(),
                    line.pageWidth(),
                    line.pageHeight(),
                    line.leftX(),
                    line.rightX(),
                    line.topY(),
                    line.bottomY(),
                    line.fontSize(),
                    trimmed
            ));
        }

        return filtered;
    }

    /**
     * Reconstruct clean Markdown paragraphs from layout-filtered lines and insert page images
     * strictly at paragraph boundaries closest to their real Y-coordinates.
     */
    public static String reconstructMarkdown(
            List<ExtractedLine> rawLines,
            List<PageImagePlacement> images) {
        List<ExtractedLine> lines = filterHeadersAndFooters(rawLines);
        List<PageImagePlacement> sortedImages = (images == null) ? List.of() : images.stream()
                .filter(img -> img != null && img.markdownBlock() != null && !img.markdownBlock().isBlank())
                .sorted(Comparator.comparingInt(PageImagePlacement::pageNumber)
                        .thenComparingDouble(PageImagePlacement::topY))
                .toList();

        if (lines.isEmpty() && sortedImages.isEmpty()) {
            return "";
        }

        List<ParagraphBlock> paragraphs = buildParagraphs(lines);

        // Group images by page for boundary placement
        Map<Integer, List<PageImagePlacement>> imagesByPage = new HashMap<>();
        for (PageImagePlacement img : sortedImages) {
            imagesByPage.computeIfAbsent(img.pageNumber(), k -> new ArrayList<>()).add(img);
        }

        if (paragraphs.isEmpty()) {
            StringBuilder onlyImages = new StringBuilder();
            for (PageImagePlacement img : sortedImages) {
                if (onlyImages.length() > 0) {
                    onlyImages.append("\n\n");
                }
                onlyImages.append(img.markdownBlock().trim());
            }
            return onlyImages.toString();
        }

        // Map each image to an anchor slot: either BEFORE paragraph index i, or AFTER paragraph index i.
        // Using (paragraphIndex -> list of beforeImages / afterImages) preserves paragraph integrity.
        Map<Integer, List<String>> beforeParagraphImages = new HashMap<>();
        Map<Integer, List<String>> afterParagraphImages = new HashMap<>();

        List<Integer> pagesWithImages = new ArrayList<>(imagesByPage.keySet());
        pagesWithImages.sort(Integer::compareTo);

        for (Integer page : pagesWithImages) {
            List<PageImagePlacement> pageImgs = imagesByPage.get(page);
            List<Integer> paraIndicesOnPage = new ArrayList<>();
            for (int i = 0; i < paragraphs.size(); i++) {
                if (paragraphs.get(i).hasPage(page)) {
                    paraIndicesOnPage.add(i);
                }
            }

            if (paraIndicesOnPage.isEmpty()) {
                // Find nearest paragraph from an earlier page, or append at end
                int anchorIdx = -1;
                for (int i = paragraphs.size() - 1; i >= 0; i--) {
                    if (paragraphs.get(i).pageSpans.get(0).pageNumber < page) {
                        anchorIdx = i;
                        break;
                    }
                }
                for (PageImagePlacement img : pageImgs) {
                    if (anchorIdx >= 0) {
                        afterParagraphImages.computeIfAbsent(anchorIdx, k -> new ArrayList<>())
                                .add(img.markdownBlock().trim());
                    } else {
                        beforeParagraphImages.computeIfAbsent(0, k -> new ArrayList<>())
                                .add(img.markdownBlock().trim());
                    }
                }
                continue;
            }

            int firstParaIdx = paraIndicesOnPage.get(0);
            PageSpan firstSpan = paragraphs.get(firstParaIdx).spanForPage(page);

            for (PageImagePlacement img : pageImgs) {
                if (firstSpan != null && img.topY() < firstSpan.minY) {
                    beforeParagraphImages.computeIfAbsent(firstParaIdx, k -> new ArrayList<>())
                            .add(img.markdownBlock().trim());
                } else {
                    int targetIdx = firstParaIdx;
                    for (int idx : paraIndicesOnPage) {
                        PageSpan span = paragraphs.get(idx).spanForPage(page);
                        if (span != null && span.minY <= img.topY()) {
                            targetIdx = idx;
                        }
                    }
                    afterParagraphImages.computeIfAbsent(targetIdx, k -> new ArrayList<>())
                            .add(img.markdownBlock().trim());
                }
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            List<String> before = beforeParagraphImages.get(i);
            if (before != null) {
                for (String block : before) {
                    appendBlock(out, block);
                }
            }
            appendBlock(out, paragraphs.get(i).text.toString());
            List<String> after = afterParagraphImages.get(i);
            if (after != null) {
                for (String block : after) {
                    appendBlock(out, block);
                }
            }
        }

        return out.toString().trim();
    }

    private static List<ParagraphBlock> buildParagraphs(List<ExtractedLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }

        float medianLineStep = computeMedianLineStep(lines);
        PageMargins globalMargins = new PageMargins(
                computePercentileLeftX(lines, 0.20f),
                computePercentileRightX(lines, 0.85f));
        Map<Integer, PageMargins> marginsByPage = computePageMarginsMap(lines, globalMargins);

        List<ParagraphBlock> paragraphs = new ArrayList<>();
        ParagraphBlock current = new ParagraphBlock(lines.get(0));

        for (int i = 1; i < lines.size(); i++) {
            ExtractedLine prev = lines.get(i - 1);
            ExtractedLine curr = lines.get(i);
            PageMargins prevMargins = marginsByPage.getOrDefault(prev.pageNumber(), globalMargins);
            PageMargins currMargins = marginsByPage.getOrDefault(curr.pageNumber(), globalMargins);

            if (shouldStartNewParagraph(prev, curr, medianLineStep, prevMargins, currMargins)) {
                paragraphs.add(current);
                current = new ParagraphBlock(curr);
            } else {
                current.appendLine(curr);
            }
        }
        paragraphs.add(current);
        return paragraphs;
    }

    private static boolean shouldStartNewParagraph(
            ExtractedLine prev,
            ExtractedLine curr,
            float medianLineStep,
            PageMargins prevMargins,
            PageMargins currMargins) {
        String prevText = prev.text().trim();
        String currText = curr.text().trim();

        if (isStandaloneStructuralLine(prevText) || isStandaloneStructuralLine(currText)) {
            return true;
        }

        if (prev.fontSize() > 0 && curr.fontSize() > 0 && Math.abs(curr.fontSize() - prev.fontSize()) > 1.5f) {
            return true;
        }

        float effectiveFont = curr.fontSize() > 0 ? curr.fontSize() : Math.max(10f, curr.height() * 0.85f);
        float indentThreshold = Math.max(10f, effectiveFont * 0.9f);
        boolean currIndented = (curr.leftX() - currMargins.leftX()) > indentThreshold;

        float refPageWidth = Math.max(prev.pageWidth(), 100f);
        float contentSpan = Math.max(refPageWidth * 0.4f, prevMargins.rightX() - prevMargins.leftX());
        float shortLineLimit = prevMargins.rightX() - Math.max(24f, contentSpan * 0.14f);
        boolean prevIsShort = prevMargins.rightX() > prevMargins.leftX() && prev.rightX() < shortLineLimit;
        boolean prevEndsSentence = endsWithSentencePunctuation(prevText);
        boolean prevEndsColon = prevText.endsWith(":");

        if (curr.pageNumber() == prev.pageNumber()) {
            float verticalStep = curr.topY() - prev.topY();
            if (verticalStep < -prev.height()) {
                return true;
            }
            if (verticalStep > medianLineStep * LINE_GAP_PARAGRAPH_MULTIPLIER) {
                return true;
            }
            if (prevEndsColon && prevIsShort) {
                return true;
            }
            if (prevEndsSentence && (prevIsShort || currIndented)) {
                return true;
            }
            if (currIndented && !endsWithMidClauseConnector(prevText)) {
                return true;
            }
            return false;
        }

        // Across page boundary: join mid-sentence wraps seamlessly; break only when sentence clearly ended
        if (prevEndsColon && prevIsShort) {
            return true;
        }
        if (prevEndsSentence && (prevIsShort || currIndented)) {
            return true;
        }
        return false;
    }

    private static boolean isStandaloneStructuralLine(String text) {
        if (text.isEmpty()) {
            return true;
        }
        if (LIST_ITEM_LINE.matcher(text).matches()
                || SECTION_HEADING_LINE.matcher(text).matches()
                || IMAGE_CAPTION_LINE.matcher(text).matches()) {
            return true;
        }
        // Short ALL-CAPS titles (at least 4 letters, no lowercase letters)
        if (text.length() <= 100) {
            int letters = 0;
            boolean hasLower = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isLetter(c)) {
                    letters++;
                    if (Character.isLowerCase(c)) {
                        hasLower = true;
                        break;
                    }
                }
            }
            if (letters >= 4 && !hasLower) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithSentencePunctuation(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        if (last == '"' || last == '\'' || last == '”' || last == '’' || last == ')' || last == '»') {
            if (text.length() < 2) {
                return false;
            }
            last = text.charAt(text.length() - 2);
        }
        return last == '.' || last == '!' || last == '?' || last == '…';
    }

    private static boolean endsWithMidClauseConnector(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return last == ',' || last == ';' || last == '-' || last == '–';
    }

    private static MarginBand classifyBand(ExtractedLine line) {
        float h = line.pageHeight();
        if (h <= 0f) {
            return MarginBand.BODY;
        }
        if (line.topY() <= h * TOP_MARGIN_RATIO) {
            return MarginBand.TOP;
        }
        if (line.bottomY() >= h * BOTTOM_MARGIN_RATIO) {
            return MarginBand.BOTTOM;
        }
        return MarginBand.BODY;
    }

    private static boolean isMarginBoilerplateNoise(String text) {
        if (PURE_PAGE_NUMBER.matcher(text).matches()
                || PAGE_FRACTION.matcher(text).matches()
                || PAGE_LABEL.matcher(text).matches()
                || WEB_PRINT_URL.matcher(text).matches()
                || SCRIBD_PRINT_NOISE.matcher(text).matches()) {
            return true;
        }
        return WEB_PRINT_TIMESTAMP.matcher(text).matches() && text.length() <= 80;
    }

    private static String normalizeWhitespace(String s) {
        return WHITESPACE_RUN.matcher(s.trim()).replaceAll(" ");
    }

    private static String normalizeSignature(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        String digitsMasked = DIGITS_RUN.matcher(lowered).replaceAll("#");
        return WHITESPACE_RUN.matcher(digitsMasked).replaceAll(" ").trim();
    }

    private static float computeMedianLineStep(List<ExtractedLine> lines) {
        List<Float> steps = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            ExtractedLine prev = lines.get(i - 1);
            ExtractedLine curr = lines.get(i);
            if (curr.pageNumber() == prev.pageNumber()) {
                float step = curr.topY() - prev.topY();
                float maxReasonableStep = (curr.pageHeight() > 0) ? curr.pageHeight() * 0.12f : 80f;
                if (step > 2f && step < maxReasonableStep) {
                    steps.add(step);
                }
            }
        }
        if (steps.isEmpty()) {
            float avgHeight = 0f;
            for (ExtractedLine line : lines) {
                avgHeight += line.height();
            }
            avgHeight /= lines.size();
            return Math.max(10f, avgHeight * 1.25f);
        }
        steps.sort(Float::compareTo);
        return steps.get(steps.size() / 2);
    }

    private static float computePercentileLeftX(List<ExtractedLine> lines, float percentile) {
        List<Float> vals = new ArrayList<>(lines.size());
        for (ExtractedLine l : lines) {
            if (!isStandaloneStructuralLine(l.text())) {
                vals.add(l.leftX());
            }
        }
        if (vals.isEmpty()) {
            for (ExtractedLine l : lines) {
                vals.add(l.leftX());
            }
        }
        vals.sort(Float::compareTo);
        int idx = Math.min(vals.size() - 1, Math.max(0, (int) Math.floor(vals.size() * percentile)));
        return vals.get(idx);
    }

    private static float computePercentileRightX(List<ExtractedLine> lines, float percentile) {
        List<Float> vals = new ArrayList<>(lines.size());
        for (ExtractedLine l : lines) {
            if (!isStandaloneStructuralLine(l.text())) {
                vals.add(l.rightX());
            }
        }
        if (vals.isEmpty()) {
            for (ExtractedLine l : lines) {
                vals.add(l.rightX());
            }
        }
        vals.sort(Float::compareTo);
        int idx = Math.min(vals.size() - 1, Math.max(0, (int) Math.floor(vals.size() * percentile)));
        return vals.get(idx);
    }

    private static void appendBlock(StringBuilder out, String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append(block.trim());
    }

    private static Map<Integer, PageMargins> computePageMarginsMap(
            List<ExtractedLine> lines,
            PageMargins fallback) {
        Map<Integer, List<ExtractedLine>> byPage = new HashMap<>();
        for (ExtractedLine line : lines) {
            if (!isStandaloneStructuralLine(line.text())) {
                byPage.computeIfAbsent(line.pageNumber(), k -> new ArrayList<>()).add(line);
            }
        }
        Map<Integer, PageMargins> result = new HashMap<>();
        for (Map.Entry<Integer, List<ExtractedLine>> entry : byPage.entrySet()) {
            List<ExtractedLine> pageLines = entry.getValue();
            if (pageLines.size() >= 4) {
                result.put(entry.getKey(), new PageMargins(
                        computePercentileLeftX(pageLines, 0.20f),
                        computePercentileRightX(pageLines, 0.85f)));
            } else {
                result.put(entry.getKey(), fallback);
            }
        }
        return result;
    }

    private static float computeMedianBodyHeight(List<ExtractedLine> lines) {
        List<Float> heights = new ArrayList<>();
        for (ExtractedLine line : lines) {
            if (classifyBand(line) == MarginBand.BODY && line.height() > 2f) {
                heights.add(line.height());
            }
        }
        if (heights.isEmpty()) {
            return 0f;
        }
        heights.sort(Float::compareTo);
        return heights.get(heights.size() / 2);
    }

    private static boolean isThinMarginArtifact(ExtractedLine line, String text, float medianBodyHeight) {
        if (medianBodyHeight <= 0f) {
            return false;
        }
        if (line.height() >= medianBodyHeight * THIN_MARGIN_HEIGHT_RATIO) {
            return false;
        }
        return countWordTokensOfLength(text, 3) <= 1;
    }

    private static boolean isSymbolOrRuleNoise(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        int threeLetterWords = countWordTokensOfLength(text, 3);
        if (RULE_CHAR_RUN.matcher(text).find() && threeLetterWords < 3) {
            return true;
        }
        int twoLetterWords = countWordTokensOfLength(text, 2);
        if (twoLetterWords > 0) {
            return false;
        }
        int nonSpace = 0;
        int symbolCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                nonSpace++;
                if (!Character.isLetterOrDigit(c)) {
                    symbolCount++;
                }
            }
        }
        return nonSpace > 0 && ((double) symbolCount / nonSpace) >= 0.35d;
    }

    private static int countWordTokensOfLength(String text, int minLetters) {
        String[] tokens = WHITESPACE_RUN.split(text.trim());
        int count = 0;
        for (String token : tokens) {
            int letters = 0;
            for (int i = 0; i < token.length(); i++) {
                if (Character.isLetter(token.charAt(i))) {
                    letters++;
                }
            }
            if (letters >= minLetters) {
                count++;
            }
        }
        return count;
    }

    private static String canonicalMarginKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String foldedD = text.replace('Đ', 'D').replace('đ', 'd');
        String decomposed = Normalizer.normalize(foldedD, Normalizer.Form.NFD);
        String noMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String digitsMasked = DIGITS_RUN.matcher(noMarks.toLowerCase(Locale.ROOT)).replaceAll("#");
        StringBuilder sb = new StringBuilder(digitsMasked.length());
        boolean lastSpace = true;
        for (int i = 0; i < digitsMasked.length(); i++) {
            char c = digitsMasked.charAt(i);
            if (Character.isLetter(c) || c == '#') {
                sb.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                sb.append(' ');
                lastSpace = true;
            }
        }
        return sb.toString().trim();
    }

    private static Map<MarginBand, Map<String, Integer>> buildFuzzyClusterCountsByBand(
            Map<BandSignatureKey, Set<Integer>> overallPagesBySig) {
        Map<MarginBand, Map<String, Set<Integer>>> canonicalPagesByBand = new HashMap<>();
        for (Map.Entry<BandSignatureKey, Set<Integer>> entry : overallPagesBySig.entrySet()) {
            String canonical = canonicalMarginKey(entry.getKey().signature());
            if (canonical.length() < 8) {
                continue;
            }
            canonicalPagesByBand
                    .computeIfAbsent(entry.getKey().band(), k -> new HashMap<>())
                    .computeIfAbsent(canonical, k -> new HashSet<>())
                    .addAll(entry.getValue());
        }
        Map<MarginBand, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<MarginBand, Map<String, Set<Integer>>> entry : canonicalPagesByBand.entrySet()) {
            result.put(entry.getKey(), mergeFuzzyClusterCounts(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Map<String, Integer>> buildFuzzyClusterCountsByParity(
            Map<ParitySignatureKey, Set<Integer>> parityPagesBySig) {
        Map<String, Map<String, Set<Integer>>> canonicalPagesByParity = new HashMap<>();
        for (Map.Entry<ParitySignatureKey, Set<Integer>> entry : parityPagesBySig.entrySet()) {
            String canonical = canonicalMarginKey(entry.getKey().signature());
            if (canonical.length() < 8) {
                continue;
            }
            String bucket = entry.getKey().band().name() + ":" + entry.getKey().pageParity();
            canonicalPagesByParity
                    .computeIfAbsent(bucket, k -> new HashMap<>())
                    .computeIfAbsent(canonical, k -> new HashSet<>())
                    .addAll(entry.getValue());
        }
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Set<Integer>>> entry : canonicalPagesByParity.entrySet()) {
            result.put(entry.getKey(), mergeFuzzyClusterCounts(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Integer> mergeFuzzyClusterCounts(Map<String, Set<Integer>> pagesByCanonical) {
        List<Map.Entry<String, Set<Integer>>> entries = new ArrayList<>(pagesByCanonical.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        List<String> representatives = new ArrayList<>();
        List<Set<Integer>> clusterPages = new ArrayList<>();
        List<List<String>> clusterMembers = new ArrayList<>();

        for (Map.Entry<String, Set<Integer>> entry : entries) {
            String key = entry.getKey();
            int bestCluster = -1;
            double bestSim = 0d;

            for (int c = 0; c < representatives.size(); c++) {
                double sim = editSimilarity(key, representatives.get(c));
                if (sim >= MARGIN_FUZZY_SIMILARITY_THRESHOLD && sim > bestSim) {
                    bestSim = sim;
                    bestCluster = c;
                }
            }

            if (bestCluster >= 0) {
                clusterPages.get(bestCluster).addAll(entry.getValue());
                clusterMembers.get(bestCluster).add(key);
            } else {
                representatives.add(key);
                clusterPages.add(new HashSet<>(entry.getValue()));
                List<String> members = new ArrayList<>();
                members.add(key);
                clusterMembers.add(members);
            }
        }

        Map<String, Integer> counts = new HashMap<>();
        for (int c = 0; c < clusterMembers.size(); c++) {
            int totalDistinctPages = clusterPages.get(c).size();
            for (String member : clusterMembers.get(c)) {
                counts.put(member, totalDistinctPages);
            }
        }
        return counts;
    }

    private static double editSimilarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        int minLen = Math.min(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0d;
        }
        if (minLen == 0 || maxLen > minLen * 1.35d) {
            return 0.0d;
        }
        int dist = levenshteinDistance(a, b);
        return 1.0d - ((double) dist / maxLen);
    }

    private static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
