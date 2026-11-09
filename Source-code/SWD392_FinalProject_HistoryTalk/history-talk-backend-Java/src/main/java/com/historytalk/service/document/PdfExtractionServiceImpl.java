package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.service.chat.AiServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final long MAX_FILE_BYTES = 100 * 1024 * 1024; // Hỗ trợ tài liệu chuyên khảo lên tới 100MB

    // Pattern dọn sạch siêu dữ liệu lưu trữ / con dấu trung tâm lưu trữ văn thư quốc gia
    private static final Pattern ARCHIVE_METADATA_PATTERN = Pattern.compile(
            "(?im)^.*(?:Trung tâm L[uư]u tr[uữ]|L[uư]u tr[uữ] qu[oố]c gia|M[uụ]c l[uụ]c [aả]nh|ph[oô]ng [Đđ][oơ]n v[iị]|h[oồ] s[oơ] \\d+|t[oờ] \\d+|SLT\\s*\\d+).*$\\R?",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Pattern dọn chú thích ảnh đơn độc khi tắt tính năng AI Vision
    private static final Pattern LONE_CAPTION_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:H[iì]nh|L[uư][oợ]c [đd][oồ]|B[aả]n [đd][oồ]|[AẢaả]nh t[uư] li[eệ]u|S[oơ] [đd][oồ]|Tranh|Di t[ií]ch)\\s*\\d*[.:]?[^\\n\\r]*$\\R?",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Pattern dọn các số trang nằm đơn độc trên một dòng
    private static final Pattern STANDALONE_PAGE_NUMBER_PATTERN = Pattern.compile(
            "(?m)^\\s*\\d{1,4}\\s*$\\R?"
    );

    private final OcrService ocrService;
    private final PdfTypeClassifier pdfTypeClassifier;
    private final AiServiceClient aiServiceClient;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    public PdfExtractionServiceImpl(
            OcrService ocrService,
            PdfTypeClassifier pdfTypeClassifier,
            AiServiceClient aiServiceClient,
            SupabaseDocumentStorageService supabaseDocumentStorageService) {
        this.ocrService = ocrService;
        this.pdfTypeClassifier = pdfTypeClassifier;
        this.aiServiceClient = aiServiceClient;
        this.supabaseDocumentStorageService = supabaseDocumentStorageService;
        log.info("PdfExtractionService initialized with In-Place VLM Image Enrichment and Permanent Storage");
    }

    private record ExtractedHistoricalImage(
            AiServiceClient.ImageToDescribeDto describeDto,
            String imageUrl,
            int pageNumber,
            String caption
    ) {}

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
        validatePdfFile(file);
        
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            file.transferTo(tempFile.toFile());
            
            return extractTextFromFile(tempFile.toFile(), startPage, endPage, enableAiVision, onProgress);
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
        return extractTextFromBytes(pdfBytes, null, null, false, null);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, null);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, BiConsumer<Integer, Integer> onProgress) throws IOException {
        return extractTextFromBytes(pdfBytes, startPage, endPage, false, onProgress);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage, Boolean enableAiVision, BiConsumer<Integer, Integer> onProgress) throws IOException {
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
            
            return extractTextFromFile(tempFile.toFile(), startPage, endPage, enableAiVision, onProgress);
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
            BiConsumer<Integer, Integer> onProgress) throws IOException {
        boolean visionEnabled = Boolean.TRUE.equals(enableAiVision);
        log.info("=== PDF Extraction Started (enableAiVision={}) ===", visionEnabled);
        log.info("File: {}, reqStartPage: {}, reqEndPage: {}", pdfFile.getName(), reqStartPage, reqEndPage);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                throw new InvalidRequestException("Không thể trích xuất tài liệu PDF được mã hóa hoặc khóa mật khẩu");
            }

            int totalDocPages = document.getNumberOfPages();
            if (totalDocPages == 0) {
                throw new InvalidRequestException("File PDF không có trang nào");
            }

            // Mở rộng toàn bộ số trang: Nếu không truyền, tự động đọc từ trang 1 đến hết sách
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

            PdfType pdfType = pdfTypeClassifier.classify(document);
            log.info("=== Classification Decision: {} ===", pdfType);

            String finalResultText;

            if (pdfType == PdfType.SCANNED_IMAGE) {
                log.info("=== Routing Decision: OCR PROCESSING ===");
                try {
                    String rawOcr = ocrService.extractTextFromPdf(pdfFile, onProgress);
                    if (rawOcr == null || rawOcr.isBlank()) {
                        throw new InvalidRequestException("Không thể nhận diện ký tự từ tài liệu scan");
                    }
                    finalResultText = cleanDocumentNoise(rawOcr.trim(), !visionEnabled);
                } catch (IOException ocrEx) {
                    throw new InvalidRequestException("Lỗi OCR tài liệu: " + ocrEx.getMessage());
                }
            } else {
                Map<Integer, List<String>> pageDescriptionsMap = new HashMap<>();

                if (visionEnabled) {
                    log.info("=== Routing Decision: NATIVE EXTRACTION WITH IN-PLACE VLM BINDING ===");
                    // 1. Quét và thu thập toàn bộ ảnh tư liệu có giá trị lịch sử
                    List<ExtractedHistoricalImage> allImages = extractAllHistoricalImages(document, startPage, endPage);

                    if (!allImages.isEmpty()) {
                        log.info("Found {} historical images/battle maps across pages [{} - {}]. Processing via Vision AI...",
                                allImages.size(), startPage, endPage);
                        try {
                            // Gọi sang AI Service theo từng đợt để đảm bảo an toàn cho GPU/API
                            int batchSize = 5;
                            for (int i = 0; i < allImages.size(); i += batchSize) {
                                List<ExtractedHistoricalImage> batch = allImages.subList(i, Math.min(i + batchSize, allImages.size()));
                                List<AiServiceClient.ImageToDescribeDto> describeBatch = batch.stream()
                                        .map(ExtractedHistoricalImage::describeDto)
                                        .toList();
                                List<AiServiceClient.DescribedImageDto> batchResult = aiServiceClient.describeImages(describeBatch);
                                if (batchResult != null) {
                                    for (int j = 0; j < batchResult.size(); j++) {
                                        AiServiceClient.DescribedImageDto desc = batchResult.get(j);
                                        ExtractedHistoricalImage item = (j < batch.size()) ? batch.get(j) : null;
                                        String descBlock = desc.descriptionBlock();
                                        if (descBlock != null && !descBlock.isBlank()) {
                                            // Lọc bỏ nếu AI nhận diện ra là Biểu trưng / Logo lưu trữ
                                            if (descBlock.contains("Biểu trưng (Logo)") 
                                                    || descBlock.contains("Biểu trưng lưu trữ") 
                                                    || descBlock.contains("VĂN THƯ LƯU TRỮ VN")) {
                                                log.debug("Filtering out archive logo/watermark block on page {}", desc.pageNumber());
                                                continue;
                                            }

                                            String formattedBlock = descBlock;
                                            if (item != null && item.imageUrl() != null) {
                                                String captionTitle = (item.caption() != null && !item.caption().isBlank()) 
                                                        ? item.caption() 
                                                        : "Ảnh tư liệu lịch sử";
                                                formattedBlock = String.format("![%s](%s)\n%s\n- Đường dẫn ảnh tư liệu: %s",
                                                        captionTitle, item.imageUrl(), descBlock, item.imageUrl());
                                            }

                                            pageDescriptionsMap.computeIfAbsent(desc.pageNumber(), k -> new ArrayList<>())
                                                    .add(formattedBlock);
                                        }
                                    }
                                }
                            }
                            log.info("Successfully received {} image descriptions from AI", pageDescriptionsMap.size());
                        } catch (Exception e) {
                            log.warn("Failed during Vision AI processing, falling back to text-only: {}", e.getMessage());
                        }
                    }
                } else {
                    log.info("=== Routing Decision: NATIVE EXTRACTION (TEXT ONLY - AI VISION DISABLED) ===");
                }

                // 2. Trích xuất Text từng trang và ghim khối mô tả ảnh ngay tại trang đó (In-Place Binding)
                StringBuilder fullDocumentBuilder = new StringBuilder();
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);

                for (int p = startPage; p <= endPage; p++) {
                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    String pageText = stripper.getText(document);

                    if (pageText != null && !pageText.isBlank()) {
                        fullDocumentBuilder.append(pageText.trim()).append("\n\n");
                    }

                    if (onProgress != null) {
                        try {
                            onProgress.accept(p - startPage + 1, pageCount);
                        } catch (Exception ignored) {
                        }
                    }

                    // Nếu bật AI Vision và trang này có ảnh tư liệu / lược đồ, ghim trực tiếp vào ngay sau nội dung trang đó
                    if (visionEnabled && pageDescriptionsMap.containsKey(p)) {
                        List<String> imgBlocks = pageDescriptionsMap.get(p);
                        for (String block : imgBlocks) {
                            fullDocumentBuilder.append("\n").append(block).append("\n\n");
                        }
                    }
                }

                String rawText = fullDocumentBuilder.toString().trim();
                finalResultText = cleanDocumentNoise(rawText, !visionEnabled);
                if (finalResultText.isBlank()) {
                    throw new InvalidRequestException("Không tìm thấy nội dung văn bản trong các trang đã chọn");
                }
            }

            log.info("=== Extraction Completed Successfully (Length: {} chars, Pages: {}) ===", finalResultText.length(), pageCount);

            return PdfExtractionResponse.builder()
                    .rawText(finalResultText)
                    .pageCount(pageCount)
                    .build();

        } catch (IOException ex) {
            log.error("Failed to extract text from PDF: {}", ex.getMessage());
            throw new SystemException("Failed to extract text from PDF: " + ex.getMessage());
        }
    }

    /**
     * Dọn dẹp nhiễu văn bản: metadata lưu trữ, số trang đứng lẻ, và caption ảnh đơn độc (khi tắt AI Vision)
     */
    private String cleanDocumentNoise(String text, boolean removeCaptions) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = ARCHIVE_METADATA_PATTERN.matcher(text).replaceAll("");
        cleaned = STANDALONE_PAGE_NUMBER_PATTERN.matcher(cleaned).replaceAll("");
        if (removeCaptions) {
            cleaned = LONE_CAPTION_PATTERN.matcher(cleaned).replaceAll("");
        }
        cleaned = cleaned.replaceAll("(?m)^[ \\t]+$", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
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
     * Quét và trích xuất TOÀN BỘ hình ảnh có ý nghĩa lịch sử (lược đồ, bản đồ, di vật, chân dung)
     * Tự động loại trừ watermark/logo lặp lại và tải ảnh lên Supabase Storage staging folder.
     */
    private List<ExtractedHistoricalImage> extractAllHistoricalImages(
            PDDocument document, int startPage, int endPage) {
        List<ExtractedHistoricalImage> result = new ArrayList<>();
        Pattern captionPattern = Pattern.compile("(?im)(?:^|[\\.\\n\\r])\\s*(?:H\\u00ecnh|L\\u01b0\\u1ee3c \\u0111\\u1ed3|B\\u1ea3n \\u0111\\u1ed3|\\u1ea2nh t\\u01b0 li\\u1ec7u|S\\u01a1 \\u0111\\u1ed3|Tranh|Di t\\u00edch)\\s*\\d*[.:]?[^\\n\\r]+");

        // 1. Phân tích trước tần suất hash ảnh trên các trang để loại trừ watermark/logo lặp lại
        Map<String, Integer> hashOccurrences = new HashMap<>();
        int totalPages = document.getNumberOfPages();
        int scanEnd = Math.min(totalPages, Math.max(endPage, startPage + 3));
        for (int p = 1; p <= scanEnd; p++) {
            PDPage page = document.getPage(p - 1);
            PDResources res = page.getResources();
            if (res == null) continue;
            for (COSName xName : res.getXObjectNames()) {
                try {
                    if (res.isImageXObject(xName)) {
                        PDImageXObject imgObj = (PDImageXObject) res.getXObject(xName);
                        String hash = computeImageHash(imgObj);
                        hashOccurrences.merge(hash, 1, Integer::sum);
                    }
                } catch (Exception ignored) {}
            }
        }

        for (int p = startPage; p <= endPage; p++) {
            PDPage page = document.getPage(p - 1);
            PDResources resources = page.getResources();
            if (resources == null) continue;

            // Đọc text của trang để tìm caption phụ cận
            String pageTextSnippet = "";
            try {
                PDFTextStripper snippetStripper = new PDFTextStripper();
                snippetStripper.setStartPage(p);
                snippetStripper.setEndPage(p);
                pageTextSnippet = snippetStripper.getText(document);
            } catch (Exception ignored) {}

            Set<String> pageExtractedHashes = new HashSet<>();

            for (COSName xObjectName : resources.getXObjectNames()) {
                try {
                    if (resources.isImageXObject(xObjectName)) {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                        int width = image.getWidth();
                        int height = image.getHeight();

                        // 1. Loại bỏ các chi tiết đồ họa layout, icon nhỏ (< 150x150)
                        if (width < 150 || height < 150) {
                            continue;
                        }

                        // 2. Loại bỏ các dải viền trang trí quá dài mỏng
                        double ratio = (double) width / height;
                        if (ratio > 5.5 || ratio < 0.18) {
                            continue;
                        }

                        String imgHash = computeImageHash(image);
                        // Bỏ qua nếu ảnh là watermark/logo lặp lại trên nhiều trang
                        if (hashOccurrences.getOrDefault(imgHash, 0) > 1) {
                            log.debug("Skipping repeating watermark/logo (hash: {}) on page {}", imgHash, p);
                            continue;
                        }

                        // Tránh xử lý trùng lặp trên cùng 1 trang
                        if (!pageExtractedHashes.add(imgHash)) {
                            continue;
                        }

                        BufferedImage bImage = image.getImage();
                        if (bImage == null) continue;

                        // Chuẩn hóa về RGB nền trắng và resize về tối đa 1024px để tăng tốc độ AI gấp 10 lần
                        BufferedImage processedImage = scaleDownAndNormalize(bImage, 1024);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(processedImage, "jpeg", baos);

                        if (baos.size() == 0) {
                            log.debug("Skipping empty encoded image on page {}", p);
                            continue;
                        }

                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                        // Upload ảnh vào staging directory của Supabase Storage (sẽ tự hủy nếu không save)
                        String imageUrl = null;
                        try {
                            imageUrl = supabaseDocumentStorageService.uploadHistoricalImage(imageBytes, "jpg", true);
                        } catch (Exception uploadEx) {
                            log.warn("Could not upload historical image on page {} to Supabase: {}", p, uploadEx.getMessage());
                        }

                        // Tìm chú thích (caption) của bức ảnh trên trang sách
                        String caption = null;
                        Matcher matcher = captionPattern.matcher(pageTextSnippet);
                        if (matcher.find()) {
                            caption = matcher.group().replaceAll("^[\\.\\n\\r\\s]+", "").trim();
                        }

                        result.add(new ExtractedHistoricalImage(
                                new AiServiceClient.ImageToDescribeDto(base64Image, caption, p),
                                imageUrl,
                                p,
                                caption
                        ));
                    }
                } catch (Exception e) {
                    log.debug("Skipping unreadable image object in page {}: {}", p, e.getMessage());
                }
            }
        }
        return result;
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
