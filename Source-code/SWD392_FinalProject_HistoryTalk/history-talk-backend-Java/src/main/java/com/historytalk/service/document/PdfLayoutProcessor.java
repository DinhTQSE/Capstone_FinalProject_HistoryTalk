package com.historytalk.service.document;

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

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
    private static final Pattern DIGITS_RUN = Pattern.compile("\\d+");
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

        List<ExtractedLine> filtered = new ArrayList<>(nonBlank.size());
        for (ExtractedLine line : nonBlank) {
            String trimmed = normalizeWhitespace(line.text());
            MarginBand band = classifyBand(line);

            if (band != MarginBand.BODY) {
                if (isMarginBoilerplateNoise(trimmed)) {
                    continue;
                }

                if (totalPages >= 2 && trimmed.length() <= 160) {
                    String sig = normalizeSignature(trimmed);
                    boolean isImageCaption = IMAGE_CAPTION_LINE.matcher(trimmed).matches();

                    int overallCount = overallPagesBySig
                            .getOrDefault(new BandSignatureKey(band, sig), Set.of())
                            .size();
                    int parityCount = parityPagesBySig
                            .getOrDefault(new ParitySignatureKey(band, line.pageNumber() % 2, sig), Set.of())
                            .size();
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
        float bodyLeftX = computePercentileLeftX(lines, 0.20f);
        float bodyRightX = computePercentileRightX(lines, 0.85f);

        List<ParagraphBlock> paragraphs = new ArrayList<>();
        ParagraphBlock current = new ParagraphBlock(lines.get(0));

        for (int i = 1; i < lines.size(); i++) {
            ExtractedLine prev = lines.get(i - 1);
            ExtractedLine curr = lines.get(i);

            if (shouldStartNewParagraph(prev, curr, medianLineStep, bodyLeftX, bodyRightX)) {
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
            float bodyLeftX,
            float bodyRightX) {
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
        boolean currIndented = (curr.leftX() - bodyLeftX) > indentThreshold;

        float refPageWidth = Math.max(prev.pageWidth(), 100f);
        float contentSpan = Math.max(refPageWidth * 0.4f, bodyRightX - bodyLeftX);
        float shortLineLimit = bodyRightX - Math.max(24f, contentSpan * 0.14f);
        boolean prevIsShort = bodyRightX > bodyLeftX && prev.rightX() < shortLineLimit;
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
}
