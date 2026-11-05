package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PdfExtractionService {
    PdfExtractionResponse extractText(MultipartFile file) throws IOException;
    PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage) throws IOException;
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes) throws IOException;
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException;
}
