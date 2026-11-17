package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public interface PdfExtractionService {
    PdfExtractionResponse extractText(MultipartFile file) throws IOException;
    default PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage) throws IOException {
        return extractText(file, startPage, endPage, false, null);
    }
    default PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractText(file, startPage, endPage, false, onProgress);
    }
    PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage, Boolean enableAiVision, BiConsumer<Integer, Integer> onProgress) throws IOException;
    default PdfExtractionResponse extractText(
            MultipartFile file,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        return extractText(file, startPage, endPage, enableAiVision, false, onProgress, isCancelled);
    }
    default PdfExtractionResponse extractText(
            MultipartFile file,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            Boolean extractImages,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        return extractText(file, startPage, endPage, enableAiVision, onProgress);
    }

    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes) throws IOException;
    default PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, null);
    }
    default PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, onProgress);
    }
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, Boolean enableAiVision, BiConsumer<Integer, Integer> onProgress) throws IOException;
    default PdfExtractionResponse extractTextFromBytes(
            byte[] pdfBytes,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            Boolean extractImages,
            BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, enableAiVision, onProgress);
    }
}
