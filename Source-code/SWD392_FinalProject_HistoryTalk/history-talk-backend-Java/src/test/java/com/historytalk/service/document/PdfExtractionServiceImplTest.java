package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.DescribedImageDto;
import com.historytalk.service.document.PdfLayoutProcessor.ExtractedLine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfExtractionServiceImplTest {

    private OcrService ocrService;
    private AiServiceClient aiServiceClient;
    private SupabaseDocumentStorageService supabaseDocumentStorageService;
    private PdfExtractionServiceImpl service;

    @BeforeEach
    void setUp() {
        ocrService = Mockito.mock(OcrService.class);
        aiServiceClient = Mockito.mock(AiServiceClient.class);
        supabaseDocumentStorageService = Mockito.mock(SupabaseDocumentStorageService.class);
        service = new PdfExtractionServiceImpl(
                ocrService,
                new PdfTypeClassifier(),
                aiServiceClient,
                supabaseDocumentStorageService
        );
    }

    @AfterEach
    void tearDown() {
        service.shutdownExecutor();
    }

    private byte[] buildNativePdfBytes() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            BufferedImage img = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, 240, 180);
            g.dispose();
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);

            for (int p = 1; p <= 3; p++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setFont(font, 11);
                    // Top repeated header (top 12% margin band)
                    cs.beginText();
                    cs.newLineAtOffset(72, 770);
                    cs.showText("History Textbook Archive - Chapter One");
                    cs.endText();

                    // Wrapped body lines that must reflow cleanly
                    cs.beginText();
                    cs.newLineAtOffset(72, 660);
                    cs.showText("Page " + p + " describes how coordinated logistics and artillery positions shaped");
                    cs.newLineAtOffset(0, -15);
                    cs.showText("the decisive battle outcome across multiple fortified hills during the campaign.");
                    cs.newLineAtOffset(0, -15);
                    cs.showText("Commanders carefully adapted fortifications and supply routes throughout the operation.");
                    cs.endText();

                    if (p == 1) {
                        cs.drawImage(pdImage, 100, 420, 120, 90);
                        cs.beginText();
                        cs.newLineAtOffset(100, 400);
                        cs.showText("Hinh 1: Chien truong lich su Dien Bien Phu.");
                        cs.endText();
                    }

                    // Bottom footer with changing page number (bottom 12% margin band)
                    cs.beginText();
                    cs.newLineAtOffset(260, 35);
                    cs.showText("Trang " + p + " / 3");
                    cs.endText();
                }
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildBlankScannedPdfBytes(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage img = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.GRAY);
            g.fillRect(0, 0, 240, 180);
            g.dispose();
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(pdImage, 50, 400, 240, 180);
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractText_nativePdfStripsHeadersReflowsLinesAndAttachesSpatialCaptionsAndVision() throws Exception {
        byte[] pdfBytes = buildNativePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "history.pdf", "application/pdf", pdfBytes);

        when(supabaseDocumentStorageService.uploadHistoricalImage(any(byte[].class), eq("jpg"), eq(true)))
                .thenReturn("https://cdn.test/img-1.jpg");
        when(aiServiceClient.describeImages(anyList()))
                .thenReturn(List.of(new DescribedImageDto(1, "Hinh 1: Chien truong lich su Dien Bien Phu.", "\n[VISION_DESC]\n")));

        List<Integer> reportedPages = new ArrayList<>();
        PdfExtractionResponse response = service.extractText(
                file,
                1,
                3,
                true,
                (page, total) -> reportedPages.add(page),
                () -> false
        );

        assertThat(response.getPageCount()).isEqualTo(3);
        assertThat(reportedPages).containsExactly(1, 2, 3);
        assertThat(response.getRawText()).doesNotContain("History Textbook Archive - Chapter One");
        assertThat(response.getRawText()).doesNotContain("Trang 1 / 3");
        assertThat(response.getRawText()).doesNotContain("Trang 2 / 3");
        assertThat(response.getRawText()).contains(
                "Page 1 describes how coordinated logistics and artillery positions shaped the decisive battle outcome across multiple fortified hills during the campaign."
        );
        assertThat(response.getRawText()).contains("https://cdn.test/img-1.jpg");
        assertThat(response.getRawText()).contains("[VISION_DESC]");
        verify(aiServiceClient).describeImages(Mockito.argThat(list ->
                list.size() == 1 && list.get(0).caption().contains("Hinh 1")
        ));
    }

    @Test
    void extractText_whenCancelledDuringProgress_throwsCancellationException() throws Exception {
        byte[] pdfBytes = buildNativePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "history.pdf", "application/pdf", pdfBytes);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        assertThatThrownBy(() -> service.extractText(
                file,
                1,
                3,
                false,
                (page, total) -> cancelled.set(true),
                cancelled::get
        )).isInstanceOf(CancellationException.class);
    }

    @Test
    void extractText_whenExtractImagesDisabled_skipsImageExtractionAndStorageUpload() throws Exception {
        byte[] pdfBytes = buildNativePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "history.pdf", "application/pdf", pdfBytes);

        PdfExtractionResponse response = service.extractText(
                file,
                1,
                3,
                false,
                false,
                null,
                () -> false
        );

        assertThat(response.getPageCount()).isEqualTo(3);
        assertThat(response.getRawText()).contains(
                "Page 1 describes how coordinated logistics and artillery positions shaped the decisive battle outcome across multiple fortified hills during the campaign."
        );
        assertThat(response.getRawText()).doesNotContain("![");
        verify(supabaseDocumentStorageService, never()).uploadHistoricalImage(any(byte[].class), any(), any(Boolean.class));
        verify(aiServiceClient, never()).describeImages(anyList());
    }

    @Test
    void extractText_whenExtractImagesEnabledWithoutVision_uploadsImagesWithoutCallingAiVision() throws Exception {
        byte[] pdfBytes = buildNativePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "history.pdf", "application/pdf", pdfBytes);

        when(supabaseDocumentStorageService.uploadHistoricalImage(any(byte[].class), eq("jpg"), eq(true)))
                .thenReturn("https://cdn.test/img-1.jpg");

        PdfExtractionResponse response = service.extractText(
                file,
                1,
                3,
                false,
                true,
                null,
                () -> false
        );

        assertThat(response.getPageCount()).isEqualTo(3);
        assertThat(response.getRawText()).contains("![Hinh 1: Chien truong lich su Dien Bien Phu.](https://cdn.test/img-1.jpg)");
        verify(supabaseDocumentStorageService).uploadHistoricalImage(any(byte[].class), eq("jpg"), eq(true));
        verify(aiServiceClient, never()).describeImages(anyList());
    }

    @Test
    void extractText_scannedPdfDelegatesToOcrAndReflowsLines() throws Exception {
        byte[] pdfBytes = buildBlankScannedPdfBytes(2);
        MockMultipartFile file = new MockMultipartFile("file", "scanned.pdf", "application/pdf", pdfBytes);

        when(ocrService.extractLinesFromPdf(any(), eq(1), eq(2), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    var onProgress = (java.util.function.BiConsumer<Integer, Integer>) invocation.getArgument(3);
                    if (onProgress != null) {
                        onProgress.accept(1, 2);
                        onProgress.accept(2, 2);
                    }
                    return List.of(
                            new ExtractedLine(1, 595f, 842f, 70f, 380f, 30f, 44f, 12f, "TAI LIEU LICH SU"),
                            new ExtractedLine(1, 595f, 842f, 70f, 480f, 160f, 174f, 12f, "Quân dân cả nước đoàn kết đứng lên"),
                            new ExtractedLine(1, 595f, 842f, 70f, 310f, 178f, 192f, 12f, "bảo vệ nền độc lập dân tộc."),
                            new ExtractedLine(2, 595f, 842f, 70f, 380f, 30f, 44f, 12f, "TAI LIEU LICH SU"),
                            new ExtractedLine(2, 595f, 842f, 70f, 420f, 160f, 174f, 12f, "Chiến thắng mở ra kỷ nguyên mới.")
                    );
                });

        List<Integer> reportedPages = new ArrayList<>();
        PdfExtractionResponse response = service.extractText(file, 1, 2, false, (p, t) -> reportedPages.add(p), () -> false);

        assertThat(reportedPages).containsExactly(1, 2);

        assertThat(response.getPageCount()).isEqualTo(2);
        assertThat(response.getRawText()).doesNotContain("TAI LIEU LICH SU");
        assertThat(response.getRawText()).contains("Quân dân cả nước đoàn kết đứng lên bảo vệ nền độc lập dân tộc.");
        assertThat(response.getRawText()).contains("Chiến thắng mở ra kỷ nguyên mới.");
    }
}
