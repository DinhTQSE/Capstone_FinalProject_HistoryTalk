package com.historytalk.service.document;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

public interface OcrService {
    String extractTextFromImage(File imageFile) throws IOException;
    String extractTextFromPdf(File pdfFile) throws IOException;
    default String extractTextFromPdf(File pdfFile, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromPdf(pdfFile);
    }
}
