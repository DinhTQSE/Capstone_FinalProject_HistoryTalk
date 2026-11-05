package com.historytalk.service.document;

import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import com.historytalk.service.chat.AiServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.MemoryUsageSetting;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final long MAX_FILE_BYTES = 100 * 1024 * 1024; // Hỗ trợ tài liệu chuyên khảo lên tới 100MB

    private final OcrService ocrService;
    private final PdfTypeClassifier pdfTypeClassifier;
    private final AiServiceClient aiServiceClient;

    public PdfExtractionServiceImpl(
            OcrService ocrService,
            PdfTypeClassifier pdfTypeClassifier,
            AiServiceClient aiServiceClient) {
        this.ocrService = ocrService;
        this.pdfTypeClassifier = pdfTypeClassifier;
        this.aiServiceClient = aiServiceClient;
        log.info("PdfExtractionService initialized with In-Place VLM Image Enrichment and Unbounded Page Range");
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file) throws IOException {
        return extractText(file, null, null);
    }

    @Override
    public PdfExtractionResponse extractText(MultipartFile file, Integer startPage, Integer endPage) throws IOException {
        validatePdfFile(file);
        
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("pdf-extraction-", ".pdf");
            file.transferTo(tempFile.toFile());
            
            return extractTextFromFile(tempFile.toFile(), startPage, endPage);
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
        return extractTextFromBytes(pdfBytes, null, null);
    }

    @Override
    public PdfExtractionResponse extractTextFromBytes(byte[] pdfBytes, Integer startPage, Integer endPage) throws IOException {
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
            
            return extractTextFromFile(tempFile.toFile(), startPage, endPage);
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

    private PdfExtractionResponse extractTextFromFile(File pdfFile, Integer reqStartPage, Integer reqEndPage) throws IOException {
        log.info("=== PDF Extraction Started (Production In-Place Pipeline) ===");
        log.info("File: {}, reqStartPage: {}, reqEndPage: {}", pdfFile.getName(), reqStartPage, reqEndPage);
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            if (document.isEncrypted()) {
                throw new InvalidRequestException("Không thể trích xuất tài liệu PDF được mã hóa hoặc khóa mật khẩu");
            }

            int totalDocPages = document.getNumberOfPages();
            if (totalDocPages == 0) {
                throw new InvalidRequestException("File PDF không có trang nào");
            }

            // Mở rộng toàn bộ số trang: Nếu không truyền, tự động đọc từ trang 1 đến hết sách (ví dụ cả 261 trang)
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
                    String rawOcr = ocrService.extractTextFromPdf(pdfFile);
                    if (rawOcr == null || rawOcr.isBlank()) {
                        throw new InvalidRequestException("Không thể nhận diện ký tự từ tài liệu scan");
                    }
                    finalResultText = rawOcr.trim();
                } catch (IOException ocrEx) {
                    throw new InvalidRequestException("Lỗi OCR tài liệu: " + ocrEx.getMessage());
                }
            } else {
                log.info("=== Routing Decision: NATIVE EXTRACTION WITH IN-PLACE VLM BINDING ===");
                
                // 1. Quét và thu thập toàn bộ ảnh tư liệu có giá trị lịch sử
                List<AiServiceClient.ImageToDescribeDto> allImages = extractAllHistoricalImages(document, startPage, endPage);
                Map<Integer, List<String>> pageDescriptionsMap = new HashMap<>();

                if (!allImages.isEmpty()) {
                    log.info("Found {} historical images/battle maps across pages [{} - {}]. Processing via Vision AI...",
                            allImages.size(), startPage, endPage);
                    try {
                        // Gọi sang AI Service theo từng đợt để đảm bảo an toàn cho GPU/API
                        int batchSize = 5;
                        for (int i = 0; i < allImages.size(); i += batchSize) {
                            List<AiServiceClient.ImageToDescribeDto> batch = allImages.subList(i, Math.min(i + batchSize, allImages.size()));
                            List<AiServiceClient.DescribedImageDto> batchResult = aiServiceClient.describeImages(batch);
                            if (batchResult != null) {
                                for (AiServiceClient.DescribedImageDto desc : batchResult) {
                                    if (desc.descriptionBlock() != null && !desc.descriptionBlock().isBlank()) {
                                        pageDescriptionsMap.computeIfAbsent(desc.pageNumber(), k -> new ArrayList<>())
                                                .add(desc.descriptionBlock());
                                    }
                                }
                            }
                        }
                        log.info("Successfully received {} image descriptions from AI", pageDescriptionsMap.size());
                    } catch (Exception e) {
                        log.warn("Failed during Vision AI processing, falling back to text-only: {}", e.getMessage());
                    }
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

                    // Nếu trang này có ảnh tư liệu / lược đồ, ghim trực tiếp vào ngay sau nội dung trang đó
                    if (pageDescriptionsMap.containsKey(p)) {
                        List<String> imgBlocks = pageDescriptionsMap.get(p);
                        for (String block : imgBlocks) {
                            fullDocumentBuilder.append("\n").append(block).append("\n\n");
                        }
                    }
                }

                finalResultText = fullDocumentBuilder.toString().trim();
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
     * Quét và trích xuất TOÀN BỘ hình ảnh có ý nghĩa lịch sử (lược đồ, bản đồ, di vật, chân dung)
     * Không giới hạn số lượng ảnh để đảm bảo không bỏ sót bất kỳ chi tiết lịch sử nào.
     */
    private List<AiServiceClient.ImageToDescribeDto> extractAllHistoricalImages(
            PDDocument document, int startPage, int endPage) {
        List<AiServiceClient.ImageToDescribeDto> result = new ArrayList<>();
        Pattern captionPattern = Pattern.compile("(?i)(?:H\\u00ecnh|L\\u01b0\\u1ee3c \\u0111\\u1ed3|B\\u1ea3n \\u0111\\u1ed3|\\u1ea2nh t\\u01b0 li\\u1ec7u|S\\u01a1 \\u0111\\u1ed3|Tranh|Di t\\u00edch|Chi\\u1ebfn d\\u1ecbch|Tr\\u1eadn)\\s*\\d*[.:]?[^\\n\\r]+");

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

                        BufferedImage bImage = image.getImage();
                        if (bImage == null) continue;

                        // Tối ưu: Chuẩn hóa về RGB nền trắng và resize về tối đa 1024px để tăng tốc độ AI gấp 10 lần
                        BufferedImage processedImage = scaleDownAndNormalize(bImage, 1024);

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(processedImage, "jpeg", baos);

                        // Tuyệt đối không gửi chuỗi rỗng 0 bytes sang AI service
                        if (baos.size() == 0) {
                            log.debug("Skipping empty encoded image on page {}", p);
                            continue;
                        }

                        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

                        // Tìm chú thích (caption) của bức ảnh trên trang sách
                        String caption = null;
                        Matcher matcher = captionPattern.matcher(pageTextSnippet);
                        if (matcher.find()) {
                            caption = matcher.group().trim();
                        }

                        result.add(new AiServiceClient.ImageToDescribeDto(base64Image, caption, p));
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
     * Giúp xử lý triệt để lớp nền trong suốt (ARGB), giảm dung lượng base64 và chống timeout cho VLM
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
