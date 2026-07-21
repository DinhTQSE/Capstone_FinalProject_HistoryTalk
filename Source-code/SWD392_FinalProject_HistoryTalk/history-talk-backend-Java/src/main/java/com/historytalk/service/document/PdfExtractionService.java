package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface PdfExtractionService {
    PdfExtractionResponse extractText(MultipartFile file) throws IOException;
    PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes) throws IOException;
}
