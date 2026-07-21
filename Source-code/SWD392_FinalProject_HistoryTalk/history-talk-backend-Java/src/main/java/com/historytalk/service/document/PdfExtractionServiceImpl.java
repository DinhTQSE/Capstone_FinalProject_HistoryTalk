package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final long MAX_FILE_BYTES = 50 * 1024 * 1024;
    private static final int EXTRACTION_TIMEOUT_SECONDS = 30;

    private final OcrService ocrService;
    private final PdfTypeClassifier pdfTypeClassifier;

    public PdfExtractionServiceImpl(OcrService ocrService, PdfTypeClassifier pdfTypeClassifier) {
        this.ocrService = ocrService;
        this.pdfTypeClassifier = pdfTypeClassifier;
        log.info("PdfExtractionService initialized with classifier-based routing");
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file) throws IOException {
        validatePdfFile(file);
        
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            file.transferTo(tempFile.toFile());
            
            return extractTextFromFile(tempFile.toFile());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new InvalidRequestException("PDF bytes cannot be empty");
        }

        if (pdfBytes.length > MAX_FILE_BYTES) {
            throw new InvalidRequestException("PDF file size exceeds maximum limit of 50MB");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            Files.write(tempFile, pdfBytes);
            
            return extractTextFromFile(tempFile.toFile());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", tempFile, e);
                }
            }
        }
    }

    private PdfExtractionResponse extractTextFromFile(File pdfFile) throws IOException {
        log.info("=== PDF Extraction Started ===");
        log.info("File: {}", pdfFile.getName());
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                throw new InvalidRequestException("Cannot extract text from encrypted PDF files");
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new InvalidRequestException("PDF file contains no pages");
            }

            log.info("Document loaded successfully, pages: {}", pageCount);

            // Classify PDF type using structural inspection
            PdfType pdfType = pdfTypeClassifier.classify(document);
            log.info("=== Classification Decision: {} ===", pdfType);

            String rawText;

            // Route based on classification
            if (pdfType == PdfType.SCANNED_IMAGE) {
                log.info("=== Routing Decision: OCR PROCESSING ===");
                log.info("Reason: PDF classified as SCANNED_IMAGE");
                try {
                    rawText = ocrService.extractTextFromPdf(pdfFile);
                    if (rawText == null || rawText.isBlank()) {
                        log.error("OCR extraction failed - no text produced");
                        throw new InvalidRequestException("OCR extraction failed to produce text");
                    }
                    rawText = rawText.trim();
                    log.info("OCR extraction successful - extracted {} characters", rawText.length());
                } catch (IOException ocrEx) {
                    log.error("OCR processing failed: {}", ocrEx.getMessage());
                    throw new InvalidRequestException("OCR extraction failed: " + ocrEx.getMessage());
                }
            } else {
                log.info("=== Routing Decision: PDFBOX TEXT EXTRACTION ===");
                log.info("Reason: PDF classified as NATIVE_TEXT");
                PDFTextStripper stripper = new PDFTextStripper();
                rawText = stripper.getText(document);
                
                if (rawText == null || rawText.isBlank()) {
                    log.error("PDFBox extraction failed - no text produced");
                    throw new InvalidRequestException("PDFBox extraction failed to produce text");
                }
                rawText = rawText.trim();
                
                // Calculate word count for PDFBox result
                String[] words = rawText.split("\\s+");
                int wordCount = words.length;
                
                log.info("PDFBox extraction successful - extracted {} characters", rawText.length());
                log.info("PDFBox extraction - word count: {}", wordCount);
                log.info("PDFBox extraction - character density: {:.2f} chars/page", (double) rawText.length() / pageCount);
            }

            log.info("=== Extraction Completed Successfully ===");
            log.info("Final text length: {} characters", rawText.length());
            
            return PdfExtractionResponse.builder()
                    .rawText(rawText)
                    .pageCount(pageCount)
                    .build();

        } catch (IOException ex) {
            log.error("Failed to extract text from PDF: {}", ex.getMessage());
            throw new SystemException("Failed to extract text from PDF: " + ex.getMessage());
        }
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("PDF file cannot be empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new InvalidRequestException("Only PDF files are allowed");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidRequestException("Invalid file type. Expected application/pdf");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidRequestException("PDF file size exceeds maximum limit of 50MB");
        }
    }
}
