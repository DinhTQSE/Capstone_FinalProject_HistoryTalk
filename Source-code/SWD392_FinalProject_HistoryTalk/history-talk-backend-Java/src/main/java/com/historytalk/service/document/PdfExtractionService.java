package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.function.BiConsumer;

public interface PdfExtractionService {
    PdfExtractionResponse extractText(MultipartFile file) throws IOException;
    PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage) throws IOException;
    default PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractText(file, startPage, endPage);
    }
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes) throws IOException;
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException;
    default PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage);
    }
}
