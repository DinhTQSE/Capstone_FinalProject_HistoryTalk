package com.historytalk.service.document;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public interface OcrService {
    String extractTextFromImage(File imageFile) throws IOException;
    String extractTextFromPdf(File pdfFile) throws IOException;
    default String extractTextFromPdf(File pdfFile, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromPdf(pdfFile);
    }

    default List<PdfLayoutProcessor.ExtractedLine> extractLinesFromPdf(
            File pdfFile,
            int startPage,
            int endPage,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        throw new UnsupportedOperationException("extractLinesFromPdf is not implemented");
    }
}
