package com.historytalk.service.document;

import java.io.File;
import java.io.IOException;

public interface OcrService {
    String extractTextFromImage(File imageFile) throws IOException;
    String extractTextFromPdf(File pdfFile) throws IOException;
}
