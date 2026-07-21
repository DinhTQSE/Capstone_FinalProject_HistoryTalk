package com.historytalk.service.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class PdfTypeClassifier {

    private static final int MIN_CHARS_PER_PAGE_THRESHOLD = 150;
    private static final int MIN_WORD_COUNT_THRESHOLD = 20;

    private String stripNoise(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;

        // Remove URLs (http/https)
        cleaned = cleaned.replaceAll("https?://\\S+", "");

        // Remove dates/timestamps (MM/DD/YYYY, DD/MM/YYYY, HH:MM AM/PM)
        cleaned = cleaned.replaceAll("\\d{1,2}/\\d{1,2}/\\d{2,4}", "");
        cleaned = cleaned.replaceAll("\\d{1,2}:\\d{2}\\s*(AM|PM)?", "");

        // Remove page index patterns (e.g., "112/731")
        cleaned = cleaned.replaceAll("\\d+/\\d+", "");

        // Remove specific web print headers (case-insensitive)
        cleaned = cleaned.replaceAll("(?i)scribd", "");
        cleaned = cleaned.replaceAll("(?i)print", "");

        // Clean up extra whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }

    public PdfType classify(PDDocument document) throws IOException {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }

        int pageCount = document.getNumberOfPages();
        if (pageCount == 0) {
            log.warn("PDF has no pages, defaulting to NATIVE_TEXT");
            return PdfType.NATIVE_TEXT;
        }

        log.info("=== PDF Classification Started ===");
        log.info("Total pages: {}", pageCount);

        // Step 1 & 2: Check for fonts and images across all pages
        boolean hasFonts = false;
        boolean hasImages = false;
        int totalFonts = 0;
        int totalImages = 0;

        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            if (resources != null) {
                Iterable<COSName> fontNames = resources.getFontNames();
                int pageFontCount = 0;
                if (fontNames != null) {
                    for (COSName fontName : fontNames) {
                        pageFontCount++;
                    }
                }
                totalFonts += pageFontCount;
                if (pageFontCount > 0) {
                    hasFonts = true;
                }

                // Check for image XObjects
                Iterable<COSName> xObjectNames = resources.getXObjectNames();
                int pageImageCount = 0;
                if (xObjectNames != null) {
                    for (COSName xObjectName : xObjectNames) {
                        if (resources.isImageXObject(xObjectName)) {
                            pageImageCount++;
                            hasImages = true;
                        }
                    }
                }
                totalImages += pageImageCount;
            }

            // Early exit if we have both
            if (hasFonts && hasImages) {
                break;
            }
        }

        log.info("Structural Metrics - Fonts detected: {}, hasFonts: {}", totalFonts, hasFonts);
        log.info("Structural Metrics - Images detected: {}, hasImages: {}", totalImages, hasImages);

        // Step 2: If no fonts exist AND images exist, classify as SCANNED_IMAGE
        if (!hasFonts && hasImages) {
            log.info("Classification: SCANNED_IMAGE (no fonts, has images)");
            return PdfType.SCANNED_IMAGE;
        }

        // Step 3: If fonts exist, check text density
        if (hasFonts) {
            log.info("Fonts detected, proceeding to text density analysis");
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            
            if (rawText != null) {
                rawText = rawText.trim();
                int rawChars = rawText.length();
                
                // Strip noise before evaluation
                String cleanedText = stripNoise(rawText);
                int cleanedChars = cleanedText.length();
                double cleanedCharsPerPage = (double) cleanedChars / pageCount;
                
                // Calculate word count from cleaned text
                String[] words = cleanedText.split("\\s+");
                int cleanedWordCount = words.length;

                log.info("Text Evaluation Metrics - Raw text length: {} chars", rawChars);
                log.info("Text Evaluation Metrics - Cleaned text length: {} chars", cleanedChars);
                log.info("Text Evaluation Metrics - Cleaned word count: {}", cleanedWordCount);
                log.info("Text Evaluation Metrics - Cleaned character density: {:.2f} chars/page", cleanedCharsPerPage);
                log.info("Text Evaluation Metrics - Char density threshold: {} chars/page", MIN_CHARS_PER_PAGE_THRESHOLD);
                log.info("Text Evaluation Metrics - Word count threshold: {} words", MIN_WORD_COUNT_THRESHOLD);

                // Step 4: Classify based on cleaned text density and word count
                boolean lowDensity = cleanedCharsPerPage < MIN_CHARS_PER_PAGE_THRESHOLD;
                boolean lowWordCount = cleanedWordCount < MIN_WORD_COUNT_THRESHOLD;

                if (lowDensity || lowWordCount) {
                    log.info("=== Classification Result: SCANNED_IMAGE ===");
                    if (lowDensity && lowWordCount) {
                        log.info("Reason: Low text density ({:.2f} < {}) AND low word count ({} < {})", 
                                cleanedCharsPerPage, MIN_CHARS_PER_PAGE_THRESHOLD, cleanedWordCount, MIN_WORD_COUNT_THRESHOLD);
                    } else if (lowDensity) {
                        log.info("Reason: Low text density ({:.2f} < {} chars/page)", 
                                cleanedCharsPerPage, MIN_CHARS_PER_PAGE_THRESHOLD);
                    } else {
                        log.info("Reason: Low word count ({} < {} words)", 
                                cleanedWordCount, MIN_WORD_COUNT_THRESHOLD);
                    }
                    return PdfType.SCANNED_IMAGE;
                } else {
                    log.info("=== Classification Result: NATIVE_TEXT ===");
                    log.info("Reason: Sufficient text density ({:.2f} >= {}) AND sufficient word count ({} >= {})", 
                            cleanedCharsPerPage, MIN_CHARS_PER_PAGE_THRESHOLD, cleanedWordCount, MIN_WORD_COUNT_THRESHOLD);
                    return PdfType.NATIVE_TEXT;
                }
            } else {
                log.info("Text extraction returned null, defaulting to NATIVE_TEXT");
            }
        } else {
            log.info("No fonts detected, defaulting to NATIVE_TEXT");
        }

        // Default fallback
        log.info("=== Classification Result: NATIVE_TEXT (default fallback) ===");
        return PdfType.NATIVE_TEXT;
    }
}
