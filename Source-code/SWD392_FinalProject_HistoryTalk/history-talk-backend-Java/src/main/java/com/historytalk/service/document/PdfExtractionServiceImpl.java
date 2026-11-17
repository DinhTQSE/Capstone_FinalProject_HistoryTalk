package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.service.chat.AiServiceClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final long MAX_FILE_BYTES = 100 * 1024 * 1024; // Hỗ trợ tài liệu chuyên khảo lên tới 100MB
    private static final int IMAGE_IO_CONCURRENCY = 6;
    private static final int VISION_BATCH_SIZE = 3;
    private static final int VISION_CONCURRENT_BATCHES = 2;

    // Pattern nhận diện chú thích ảnh để ghim metadata cho đúng bức ảnh gần nhất
    private static final Pattern CAPTION_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:H[iì]nh|L[uư][oợ]c [đd][oồ]|B[aả]n [đd][oồ]|[AẢaả]nh t[uư] li[eệ]u|S[oơ] [đd][oồ]|Tranh|Di t[ií]ch)\\s*\\d*[.:]?.*$",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private final OcrService ocrService;
    private final PdfTypeClassifier pdfTypeClassifier;
    private final AiServiceClient aiServiceClient;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;
    private final ExecutorService imageIoExecutor;

    public PdfExtractionServiceImpl(
            OcrService ocrService,
            PdfTypeClassifier pdfTypeClassifier,
            AiServiceClient aiServiceClient,
            SupabaseDocumentStorageService supabaseDocumentStorageService) {
        this.ocrService = ocrService;
        this.pdfTypeClassifier = pdfTypeClassifier;
        this.aiServiceClient = aiServiceClient;
        this.supabaseDocumentStorageService = supabaseDocumentStorageService;
        AtomicInteger threadSeq = new AtomicInteger(1);
        this.imageIoExecutor = Executors.newFixedThreadPool(IMAGE_IO_CONCURRENCY, r -> {
            Thread t = new Thread(r, "pdf-image-io-" + threadSeq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        log.info("PdfExtractionService initialized with Single-Pass Layout Pipeline & Bounded Parallel I/O");
    }

    @PreDestroy
    public void shutdownExecutor() {
        imageIoExecutor.shutdown();
    }

    private record ImageBounds(float topY, float bottomY) {
    }

    private record ExtractedHistoricalImage(
            AiServiceClient.ImageToDescribeDto describeDto,
            String imageUrl,
            int pageNumber,
            String caption,
            float topY,
            float bottomY
    ) {
    }

    private static class PageImageLocationLocator extends PDFGraphicsStreamEngine {
        private final Map<COSDictionary, ImageBounds> imageBoundsMap = new IdentityHashMap<>();
        private final float upperY;

        public PageImageLocationLocator(PDPage page) {
            super(page);
            PDRectangle cropBox = page.getCropBox();
            this.upperY = (cropBox != null) ? cropBox.getUpperRightY() : 792f;
        }

        public Map<COSDictionary, ImageBounds> locateImages() {
            try {
                processPage(getPage());
            } catch (Exception e) {
                log.debug("Error tracing page image locations: {}", e.getMessage());
            }
            return imageBoundsMap;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            if (pdImage == null || pdImage.getCOSObject() == null) {
                return;
            }
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            Point2D.Float p00 = ctm.transformPoint(0f, 0f);
            Point2D.Float p01 = ctm.transformPoint(0f, 1f);
            Point2D.Float p10 = ctm.transformPoint(1f, 0f);
            Point2D.Float p11 = ctm.transformPoint(1f, 1f);

            float maxUserY = Math.max(Math.max(p00.y, p01.y), Math.max(p10.y, p11.y));
            float minUserY = Math.min(Math.min(p00.y, p01.y), Math.min(p10.y, p11.y));

            float topY = Math.max(0f, upperY - maxUserY);
            float bottomY = Math.max(topY, upperY - minUserY);
            imageBoundsMap.put(pdImage.getCOSObject(), new ImageBounds(topY, bottomY));
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {}
        @Override public void clip(int windingRule) {}
        @Override public void moveTo(float x, float y) {}
        @Override public void lineTo(float x, float y) {}
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
        @Override public Point2D getCurrentPoint() { return new Point2D.Float(0, 0); }
        @Override public void closePath() {}
        @Override public void endPath() {}
        @Override public void strokePath() {}
        @Override public void fillPath(int windingRule) {}
        @Override public void fillAndStrokePath(int windingRule) {}
        @Override public void shadingFill(COSName shadingName) {}
    }

    /**
     * Single-pass PDFTextStripper collecting exact line geometry (page, leftX, rightX, topY, bottomY, fontSize, text)
     * without allocating unused concatenated page strings.
     */
    private static class SinglePassLayoutStripper extends PDFTextStripper {
        private final List<PdfLayoutProcessor.ExtractedLine> collectedLines = new ArrayList<>();
        private final StringBuilder currentText = new StringBuilder();
        private float minLeftX = Float.MAX_VALUE;
        private float maxRightX = 0f;
        private float minTopY = Float.MAX_VALUE;
        private float maxBottomY = 0f;
        private float maxFontSize = 0f;
        private float pageWidth = 0f;
        private float pageHeight = 0f;

        public SinglePassLayoutStripper() throws IOException {
            super();
            setSortByPosition(true);
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            resetLineBuffer();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (text == null || text.isEmpty()) {
                return;
            }
            if (textPositions != null && !textPositions.isEmpty()) {
                for (TextPosition tp : textPositions) {
                    float x = tp.getXDirAdj();
                    float width = tp.getWidthDirAdj();
                    float baselineY = tp.getYDirAdj();
                    float height = Math.max(1f, tp.getHeightDir());

                    minLeftX = Math.min(minLeftX, x);
                    maxRightX = Math.max(maxRightX, x + width);
                    minTopY = Math.min(minTopY, Math.max(0f, baselineY - height));
                    maxBottomY = Math.max(maxBottomY, baselineY);
                    maxFontSize = Math.max(maxFontSize, tp.getFontSizeInPt());
                    pageWidth = tp.getPageWidth();
                    pageHeight = tp.getPageHeight();
                }
            }
            currentText.append(text);
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            flushCurrentLine();
            super.writeLineSeparator();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flushCurrentLine();
            super.endPage(page);
        }

        private void flushCurrentLine() {
            String lineText = currentText.toString().trim();
            if (!lineText.isEmpty()) {
                float left = (minLeftX == Float.MAX_VALUE) ? 0f : minLeftX;
                float top = (minTopY == Float.MAX_VALUE) ? 0f : minTopY;
                float right = Math.max(left, maxRightX);
                float bottom = Math.max(top + 1f, maxBottomY);
                collectedLines.add(new PdfLayoutProcessor.ExtractedLine(
                        getCurrentPageNo(),
                        pageWidth,
                        pageHeight,
                        left,
                        right,
                        top,
                        bottom,
                        maxFontSize,
                        lineText
                ));
            }
            resetLineBuffer();
        }

        private void resetLineBuffer() {
            currentText.setLength(0);
            minLeftX = Float.MAX_VALUE;
            maxRightX = 0f;
            minTopY = Float.MAX_VALUE;
            maxBottomY = 0f;
            maxFontSize = 0f;
        }

        public List<PdfLayoutProcessor.ExtractedLine> getCollectedLines() {
            return collectedLines;
        }
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file) throws IOException {
        return extractText(file, null, null, false, null);
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage) throws IOException {
        return extractText(file, startPage, endPage, false, null);
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractText(file, startPage, endPage, false, onProgress);
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage, Boolean enableAiVision, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractText(file, startPage, endPage, enableAiVision, false, onProgress, null);
    }

    @Override
    public PdfExtractionResponse extractText(
            MultipartFile file,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        return extractText(file, startPage, endPage, enableAiVision, false, onProgress, isCancelled);
    }

    @Override
    public PdfExtractionResponse extractText(
            MultipartFile file,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            Boolean extractImages,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        validatePdfFile(file);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            file.transferTo(tempFile.toFile());

            return extractTextFromFile(tempFile.toFile(), startPage, endPage, enableAiVision, extractImages, onProgress, isCancelled);
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
        return extractTextFromBytes(pdfBytes, null, null, false, false, null);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, false, null);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, false, onProgress);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, Boolean enableAiVision, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, enableAiVision, false, onProgress);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(
            byte[] pdfBytes,
            Integer startPage,
            Integer endPage,
            Boolean enableAiVision,
            Boolean extractImages,
            BiConsumer<Integer, Integer> onProgress) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new InvalidRequestException("PDF bytes cannot be empty");
        }

        if (pdfBytes.length > MAX_FILE_BYTES) {
            throw new InvalidRequestException("Dung lượng file PDF vượt quá giới hạn tối đa 100MB");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            Files.write(tempFile, pdfBytes);

            return extractTextFromFile(tempFile.toFile(), startPage, endPage, enableAiVision, extractImages, onProgress, null);
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

    private PdfExtractionResponse extractTextFromFile(
            File pdfFile,
            Integer reqStartPage,
            Integer reqEndPage,
            Boolean enableAiVision,
            Boolean extractImages,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        boolean visionEnabled = Boolean.TRUE.equals(enableAiVision);
        boolean shouldExtractImages = Boolean.TRUE.equals(extractImages) || visionEnabled;
        log.info("=== PDF Extraction Started (extractImages={}, enableAiVision={}) ===", shouldExtractImages, visionEnabled);
        log.info("File: {}, reqStartPage: {}, reqEndPage: {}", pdfFile.getName(), reqStartPage, reqEndPage);

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                throw new InvalidRequestException("Không thể trích xuất tài liệu PDF được mã hóa hoặc khóa mật khẩu");
            }

            int totalDocPages = document.getNumberOfPages();
            if (totalDocPages == 0) {
                throw new InvalidRequestException("File PDF không có trang nào");
            }

            int startPage = (reqStartPage != null && reqStartPage >= 1) ? reqStartPage : 1;
            int endPage = (reqEndPage != null && reqEndPage >= startPage) ? Math.min(reqEndPage, totalDocPages) : totalDocPages;

            if (startPage > totalDocPages) {
                throw new InvalidRequestException("Trang bắt đầu (" + startPage + ") vượt quá tổng số trang của tài liệu (" + totalDocPages + ")");
            }

            if (endPage < startPage) {
                throw new InvalidRequestException("Trang kết thúc phải lớn hơn hoặc bằng trang bắt đầu");
            }

            int pageCount = endPage - startPage + 1;
            log.info("Document loaded successfully. Total pages: {}, extracting range: [{} - {}] (count: {})",
                    totalDocPages, startPage, endPage, pageCount);

            PdfType pdfType = pdfTypeClassifier.classify(document, startPage, endPage);
            log.info("=== Classification Decision: {} ===", pdfType);

            String finalResultText;

            if (pdfType == PdfType.SCANNED_IMAGE) {
                log.info("=== Routing Decision: OCR PROCESSING (Pages [{} - {}]) ===", startPage, endPage);
                try {
                    List<PdfLayoutProcessor.ExtractedLine> ocrLines = ocrService.extractLinesFromPdf(
                            pdfFile,
                            startPage,
                            endPage,
                            onProgress,
                            () -> Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.getAsBoolean())
                    );
                    finalResultText = PdfLayoutProcessor.reconstructMarkdown(ocrLines, List.of());
                    if (finalResultText.isBlank()) {
                        throw new InvalidRequestException("Không thể nhận diện ký tự từ tài liệu scan");
                    }
                } catch (CancellationException ce) {
                    throw ce;
                } catch (IOException ocrEx) {
                    throw new InvalidRequestException("Lỗi OCR tài liệu: " + ocrEx.getMessage());
                }
            } else {
                // 1. Single-pass text & layout extraction across [startPage..endPage]
                SinglePassLayoutStripper layoutStripper = new SinglePassLayoutStripper();
                Writer nullWriter = Writer.nullWriter();
                for (int p = startPage; p <= endPage; p++) {
                    checkAborted(isCancelled);
                    layoutStripper.setStartPage(p);
                    layoutStripper.setEndPage(p);
                    layoutStripper.writeText(document, nullWriter);
                    notifyProgress(onProgress, p - startPage + 1, pageCount);
                }

                List<PdfLayoutProcessor.ExtractedLine> extractedLines = layoutStripper.getCollectedLines();
                List<PdfLayoutProcessor.PageImagePlacement> imagePlacements = List.of();

                if (shouldExtractImages) {
                    Map<Integer, List<PdfLayoutProcessor.ExtractedLine>> linesByPage = new HashMap<>();
                    for (PdfLayoutProcessor.ExtractedLine line : extractedLines) {
                        linesByPage.computeIfAbsent(line.pageNumber(), k -> new ArrayList<>()).add(line);
                    }

                    // 2. Extract images once (with MD5 cache + parallel JPEG encode/upload + spatial caption matching)
                    List<ExtractedHistoricalImage> allImages = extractAllHistoricalImages(
                            document, startPage, endPage, linesByPage, isCancelled);

                    // 3. Build image placements (optionally enriched via bounded parallel Vision AI batches)
                    imagePlacements = buildImagePlacements(allImages, visionEnabled, isCancelled);
                } else {
                    log.info("=== NATIVE EXTRACTION (IMAGE EXTRACTION DISABLED) ===");
                }

                // 4. Filter cross-page headers/footers, reflow paragraphs, and place images cleanly at paragraph boundaries
                finalResultText = PdfLayoutProcessor.reconstructMarkdown(extractedLines, imagePlacements);
                if (finalResultText.isBlank()) {
                    throw new InvalidRequestException("Không tìm thấy nội dung văn bản trong các trang đã chọn");
                }
            }

            log.info("=== Extraction Completed Successfully (Length: {} chars, Pages: {}) ===",
                    finalResultText.length(), pageCount);

            return PdfExtractionResponse.builder()
                    .rawText(finalResultText)
                    .pageCount(pageCount)
                    .build();

        } catch (CancellationException ce) {
            throw ce;
        } catch (IOException ex) {
            log.error("Failed to extract text from PDF: {}", ex.getMessage());
            throw new SystemException("Failed to extract text from PDF: " + ex.getMessage());
        }
    }

    private List<PdfLayoutProcessor.PageImagePlacement> buildImagePlacements(
            List<ExtractedHistoricalImage> allImages,
            boolean visionEnabled,
            BooleanSupplier isCancelled) {
        if (allImages.isEmpty()) {
            return List.of();
        }

        if (!visionEnabled) {
            log.info("=== NATIVE EXTRACTION (IMAGE STORED, AI VISION DISABLED) ===");
            return toFallbackPlacements(allImages);
        }

        log.info("=== NATIVE EXTRACTION WITH VLM ANALYSIS ({} images) ===", allImages.size());
        List<List<ExtractedHistoricalImage>> batches = new ArrayList<>();
        for (int i = 0; i < allImages.size(); i += VISION_BATCH_SIZE) {
            batches.add(allImages.subList(i, Math.min(i + VISION_BATCH_SIZE, allImages.size())));
        }

        @SuppressWarnings("unchecked")
        List<PdfLayoutProcessor.PageImagePlacement>[] batchOutputs = new List[batches.size()];
        Semaphore concurrencyLimiter = new Semaphore(VISION_CONCURRENT_BATCHES);
        List<CompletableFuture<Void>> futures = new ArrayList<>(batches.size());

        for (int bIdx = 0; bIdx < batches.size(); bIdx++) {
            checkAborted(isCancelled);
            final int currentBatchIndex = bIdx;
            final List<ExtractedHistoricalImage> batch = batches.get(bIdx);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    concurrencyLimiter.acquire();
                    try {
                        checkAborted(isCancelled);
                        batchOutputs[currentBatchIndex] = describeBatchWithFallback(batch);
                    } finally {
                        concurrencyLimiter.release();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Vision AI processing interrupted");
                }
            }, imageIoExecutor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof CancellationException cancelEx) {
                throw cancelEx;
            }
            log.warn("Vision AI batch coordination error, falling back to basic image tags: {}", ce.getMessage());
            return toFallbackPlacements(allImages);
        }

        List<PdfLayoutProcessor.PageImagePlacement> placements = new ArrayList<>(allImages.size());
        for (int bIdx = 0; bIdx < batches.size(); bIdx++) {
            if (batchOutputs[bIdx] != null) {
                placements.addAll(batchOutputs[bIdx]);
            } else {
                placements.addAll(toFallbackPlacements(batches.get(bIdx)));
            }
        }
        return placements;
    }

    private List<PdfLayoutProcessor.PageImagePlacement> describeBatchWithFallback(List<ExtractedHistoricalImage> batch) {
        try {
            List<AiServiceClient.ImageToDescribeDto> describeBatch = batch.stream()
                    .map(ExtractedHistoricalImage::describeDto)
                    .toList();
            List<AiServiceClient.DescribedImageDto> batchResult = aiServiceClient.describeImages(describeBatch);
            if (batchResult == null || batchResult.isEmpty()) {
                return toFallbackPlacements(batch);
            }

            List<PdfLayoutProcessor.PageImagePlacement> placements = new ArrayList<>(batch.size());
            for (int j = 0; j < batch.size(); j++) {
                ExtractedHistoricalImage item = batch.get(j);
                AiServiceClient.DescribedImageDto desc = (j < batchResult.size()) ? batchResult.get(j) : null;
                String descBlock = (desc != null) ? desc.descriptionBlock() : null;

                if (descBlock != null && !descBlock.isBlank()) {
                    if (descBlock.contains("Biểu trưng (Logo)")
                            || descBlock.contains("Biểu trưng lưu trữ")
                            || descBlock.contains("VĂN THƯ LƯU TRỮ VN")) {
                        log.debug("Filtering out archive logo/watermark block on page {}", item.pageNumber());
                        continue;
                    }
                    String captionTitle = (item.caption() != null && !item.caption().isBlank())
                            ? item.caption()
                            : "Ảnh tư liệu lịch sử";
                    String formattedBlock = (item.imageUrl() != null)
                            ? String.format("![%s](%s)\n%s", captionTitle, item.imageUrl(), descBlock)
                            : descBlock;
                    placements.add(new PdfLayoutProcessor.PageImagePlacement(
                            item.pageNumber(), item.topY(), item.bottomY(), formattedBlock));
                } else if (item.imageUrl() != null) {
                    placements.add(toFallbackPlacement(item));
                }
            }
            return placements;
        } catch (Exception e) {
            log.warn("Failed during Vision AI batch processing, falling back to basic image tags: {}", e.getMessage());
            return toFallbackPlacements(batch);
        }
    }

    private List<PdfLayoutProcessor.PageImagePlacement> toFallbackPlacements(List<ExtractedHistoricalImage> items) {
        List<PdfLayoutProcessor.PageImagePlacement> list = new ArrayList<>(items.size());
        for (ExtractedHistoricalImage item : items) {
            if (item.imageUrl() != null) {
                list.add(toFallbackPlacement(item));
            }
        }
        return list;
    }

    private PdfLayoutProcessor.PageImagePlacement toFallbackPlacement(ExtractedHistoricalImage item) {
        String captionTitle = (item.caption() != null && !item.caption().isBlank())
                ? item.caption()
                : "Ảnh tư liệu lịch sử";
        String tag = String.format("![%s](%s)", captionTitle, item.imageUrl());
        return new PdfLayoutProcessor.PageImagePlacement(item.pageNumber(), item.topY(), item.bottomY(), tag);
    }

    private String computeImageHashCached(PDImageXObject image, Map<COSBase, String> hashCache) {
        COSBase cosObj = image.getCOSObject();
        if (cosObj != null) {
            String cached = hashCache.get(cosObj);
            if (cached != null) {
                return cached;
            }
        }
        String hash = computeImageHash(image);
        if (cosObj != null) {
            hashCache.put(cosObj, hash);
        }
        return hash;
    }

    private String computeImageHash(PDImageXObject image) {
        try (InputStream is = image.createInputStream()) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return image.getWidth() + "x" + image.getHeight();
        }
    }

    /**
     * Quét và trích xuất hình ảnh có ý nghĩa lịch sử (lược đồ, bản đồ, di vật, chân dung):
     * - Cache MD5 hash để không giải mã stream 2 lần.
     * - Đọc BufferedImage tuần tự trên thread PDFBox, sau đó resize/encode/upload Supabase song song có giới hạn.
     * - Bắt caption gần tọa độ Y của từng ảnh từ tập dòng đã trích xuất (không gọi PDFTextStripper lại).
     */
    private List<ExtractedHistoricalImage> extractAllHistoricalImages(
            PDDocument document,
            int startPage,
            int endPage,
            Map<Integer, List<PdfLayoutProcessor.ExtractedLine>> linesByPage,
            BooleanSupplier isCancelled) {
        Map<COSBase, String> hashCache = new IdentityHashMap<>();
        Map<String, Integer> hashOccurrences = new HashMap<>();

        int totalPages = document.getNumberOfPages();
        int scanEnd = Math.min(totalPages, Math.max(endPage, startPage + 3));
        for (int p = 1; p <= scanEnd; p++) {
            PDPage page = document.getPage(p - 1);
            PDResources res = page.getResources();
            if (res == null) {
                continue;
            }
            for (COSName xName : res.getXObjectNames()) {
                try {
                    if (res.isImageXObject(xName)) {
                        PDImageXObject imgObj = (PDImageXObject) res.getXObject(xName);
                        if (imgObj.getWidth() < 150 || imgObj.getHeight() < 150) {
                            continue;
                        }
                        String hash = computeImageHashCached(imgObj, hashCache);
                        hashOccurrences.merge(hash, 1, Integer::sum);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<CompletableFuture<ExtractedHistoricalImage>> uploadFutures = new ArrayList<>();
        Semaphore inFlightUploads = new Semaphore(IMAGE_IO_CONCURRENCY * 2);

        for (int p = startPage; p <= endPage; p++) {
            checkAborted(isCancelled);
            PDPage page = document.getPage(p - 1);
            PDResources resources = page.getResources();
            if (resources == null) {
                continue;
            }

            Map<COSDictionary, ImageBounds> boundsMap = Collections.emptyMap();
            try {
                PageImageLocationLocator locator = new PageImageLocationLocator(page);
                boundsMap = locator.locateImages();
            } catch (Exception ex) {
                log.debug("Failed locating image coordinates on page {}: {}", p, ex.getMessage());
            }

            List<PdfLayoutProcessor.ExtractedLine> pageLines = linesByPage.getOrDefault(p, List.of());
            Set<String> pageExtractedHashes = new HashSet<>();

            for (COSName xObjectName : resources.getXObjectNames()) {
                try {
                    if (!resources.isImageXObject(xObjectName)) {
                        continue;
                    }
                    PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                    int width = image.getWidth();
                    int height = image.getHeight();

                    if (width < 150 || height < 150) {
                        continue;
                    }
                    double ratio = (double) width / height;
                    if (ratio > 5.5 || ratio < 0.18) {
                        continue;
                    }

                    String imgHash = computeImageHashCached(image, hashCache);
                    if (hashOccurrences.getOrDefault(imgHash, 0) > 1) {
                        log.debug("Skipping repeating watermark/logo (hash: {}) on page {}", imgHash, p);
                        continue;
                    }
                    if (!pageExtractedHashes.add(imgHash)) {
                        continue;
                    }

                    BufferedImage bImage = image.getImage();
                    if (bImage == null) {
                        continue;
                    }

                    ImageBounds bounds = boundsMap.getOrDefault(
                            image.getCOSObject(),
                            new ImageBounds((float) uploadFutures.size(), (float) uploadFutures.size() + 100f));
                    String caption = findNearestCaption(pageLines, bounds);
                    final int currentPage = p;

                    inFlightUploads.acquire();
                    CompletableFuture<ExtractedHistoricalImage> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            checkAborted(isCancelled);
                            BufferedImage processedImage = scaleDownAndNormalize(bImage, 1024);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(processedImage, "jpeg", baos);
                            processedImage.flush();

                            if (baos.size() == 0) {
                                return null;
                            }

                            byte[] imageBytes = baos.toByteArray();
                            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                            String imageUrl = null;
                            try {
                                imageUrl = supabaseDocumentStorageService.uploadHistoricalImage(imageBytes, "jpg", true);
                            } catch (Exception uploadEx) {
                                log.warn("Could not upload historical image on page {} to Supabase: {}", currentPage, uploadEx.getMessage());
                            }

                            return new ExtractedHistoricalImage(
                                    new AiServiceClient.ImageToDescribeDto(base64Image, caption, currentPage),
                                    imageUrl,
                                    currentPage,
                                    caption,
                                    bounds.topY(),
                                    bounds.bottomY()
                            );
                        } catch (CancellationException ce) {
                            throw ce;
                        } catch (Exception ex) {
                            log.debug("Failed encoding/uploading image on page {}: {}", currentPage, ex.getMessage());
                            return null;
                        } finally {
                            bImage.flush();
                            inFlightUploads.release();
                        }
                    }, imageIoExecutor);

                    uploadFutures.add(future);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("PDF image extraction interrupted");
                } catch (CancellationException ce) {
                    throw ce;
                } catch (Exception e) {
                    log.debug("Skipping unreadable image object in page {}: {}", p, e.getMessage());
                }
            }
        }

        List<ExtractedHistoricalImage> result = new ArrayList<>(uploadFutures.size());
        for (CompletableFuture<ExtractedHistoricalImage> future : uploadFutures) {
            try {
                ExtractedHistoricalImage item = future.join();
                if (item != null) {
                    result.add(item);
                }
            } catch (CompletionException ce) {
                if (ce.getCause() instanceof CancellationException cancelEx) {
                    throw cancelEx;
                }
            }
        }
        return result;
    }

    private String findNearestCaption(List<PdfLayoutProcessor.ExtractedLine> pageLines, ImageBounds bounds) {
        if (pageLines == null || pageLines.isEmpty()) {
            return null;
        }
        String bestCaption = null;
        float bestDistance = Float.MAX_VALUE;

        for (PdfLayoutProcessor.ExtractedLine line : pageLines) {
            String text = line.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = CAPTION_PATTERN.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            // Prefer captions right below the image (within 90pt) or right above (within 45pt)
            float distance;
            if (line.topY() >= bounds.bottomY() - 10f && line.topY() <= bounds.bottomY() + 90f) {
                distance = Math.abs(line.topY() - bounds.bottomY());
            } else if (line.bottomY() <= bounds.topY() + 10f && line.bottomY() >= bounds.topY() - 45f) {
                distance = Math.abs(bounds.topY() - line.bottomY()) + 20f;
            } else {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestCaption = text.trim();
            }
        }
        return bestCaption;
    }

    private void notifyProgress(BiConsumer<Integer, Integer> onProgress, int current, int total) {
        if (onProgress == null) {
            return;
        }
        try {
            onProgress.accept(current, total);
        } catch (CancellationException ce) {
            throw ce;
        } catch (Exception ignored) {
        }
    }

    private void checkAborted(BooleanSupplier isCancelled) {
        if (Thread.currentThread().isInterrupted() || (isCancelled != null && isCancelled.getAsBoolean())) {
            throw new CancellationException("Quá trình trích xuất PDF đã bị hủy bởi client ngắt kết nối");
        }
    }

    /**
     * Thu nhỏ kích thước ảnh nếu vượt quá maxDimension và vẽ lên nền trắng chuẩn RGB
     */
    private BufferedImage scaleDownAndNormalize(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();

        int targetWidth = width;
        int targetHeight = height;
        if (width > maxDimension || height > maxDimension) {
            double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
            targetWidth = (int) Math.round(width * scale);
            targetHeight = (int) Math.round(height * scale);
        }

        BufferedImage rgbImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, Color.WHITE, null);
        g.dispose();
        return rgbImage;
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File PDF không được để trống");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new InvalidRequestException("Hệ thống chỉ hỗ trợ định dạng file PDF");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidRequestException("Kiểu file không hợp lệ. Vui lòng chọn application/pdf");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidRequestException("Dung lượng file PDF vượt quá giới hạn tối đa 100MB");
        }
    }
}
