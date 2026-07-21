package com.historytalk.service.document;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class OcrServiceImpl implements OcrService {

    private final String datapath;
    private final String language;
    private final int dpi;

    public OcrServiceImpl(
            @Value("${tesseract.datapath}") String datapath,
            @Value("${tesseract.language}") String language,
            @Value("${tesseract.dpi}") int dpi) {
        this.datapath = datapath;
        this.language = language;
        this.dpi = dpi;
        log.info("OcrService initialized with datapath: {}, language: {}, dpi: {}", datapath, language, dpi);
    }

    private Tesseract createTesseractInstance() throws IOException {
        Tesseract tesseract = new Tesseract();
        
        // Resolve absolute path to tessdata
        String absoluteDatapath;
        if (datapath.startsWith("classpath:")) {
            String resourcePath = datapath.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(resourcePath);
            File tessDataFolder = resource.getFile();
            absoluteDatapath = tessDataFolder.getAbsolutePath();
            log.debug("Resolved classpath tessdata to: {}", absoluteDatapath);
        } else {
            absoluteDatapath = datapath;
        }
        
        // Validate tessdata directory exists
        File tessDataDir = new File(absoluteDatapath);
        if (!tessDataDir.exists() || !tessDataDir.isDirectory()) {
            throw new IOException("Tessdata directory does not exist: " + absoluteDatapath);
        }
        
        // Validate language file exists
        String languageFile = language + ".traineddata";
        File languageDataFile = new File(tessDataDir, languageFile);
        if (!languageDataFile.exists()) {
            String availableFiles = String.join(", ", 
                tessDataDir.list((dir, name) -> name.endsWith(".traineddata")));
            throw new IOException(
                "Language data file not found: " + languageFile + 
                " in directory: " + absoluteDatapath + 
                ". Available language files: [" + availableFiles + "]. " +
                "Please download " + languageFile + " from Tesseract GitHub and place it in the tessdata directory."
            );
        }
        
        log.info("Tessdata directory validated: {}", absoluteDatapath);
        log.info("Language file found: {}", languageFile);
        
        tesseract.setDatapath(absoluteDatapath);
        tesseract.setLanguage(language);
        return tesseract;
    }

    @Override
    public String extractTextFromImage(File imageFile) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IOException("Image file does not exist");
        }

        Tesseract tesseract = null;
        try {
            log.debug("Starting OCR on image: {}", imageFile.getName());
            tesseract = createTesseractInstance();
            String text = tesseract.doOCR(imageFile);
            log.debug("OCR completed, extracted {} characters", text != null ? text.length() : 0);
            return text;
        } catch (TesseractException e) {
            log.error("OCR failed for image: {}", imageFile.getName(), e);
            throw new IOException("OCR processing failed: " + e.getMessage(), e);
        } finally {
            // Tesseract instance will be garbage collected
            // No explicit dispose needed for Tess4J Tesseract
        }
    }

    @Override
    public String extractTextFromPdf(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IOException("PDF file does not exist");
        }

        Path tempImageFile = null;
        StringBuilder fullText = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            log.info("Starting OCR on PDF with {} pages at {} DPI", pageCount, dpi);

            PDFRenderer renderer = new PDFRenderer(document);

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                tempImageFile = null;
                BufferedImage image = null;
                try {
                    tempImageFile = Files.createTempFile("pdf-ocr-page-", ".png");
                    
                    image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                    ImageIO.write(image, "png", tempImageFile.toFile());

                    // Create fresh Tesseract instance for each page
                    Tesseract tesseract = createTesseractInstance();
                    String pageText = tesseract.doOCR(tempImageFile.toFile());
                    
                    if (pageText != null && !pageText.isBlank()) {
                        if (fullText.length() > 0) {
                            fullText.append("\n\n");
                        }
                        fullText.append(pageText);
                    }

                    log.debug("OCR completed for page {}/{}", pageIndex + 1, pageCount);

                } catch (TesseractException e) {
                    log.error("OCR failed for page {}/{}", pageIndex + 1, pageCount, e);
                    throw new IOException("OCR processing failed on page " + (pageIndex + 1) + ": " + e.getMessage(), e);
                } finally {
                    // Properly dispose of BufferedImage
                    if (image != null) {
                        image.flush();
                    }
                    // Delete temp image file
                    if (tempImageFile != null) {
                        try {
                            Files.deleteIfExists(tempImageFile);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp image file: {}", tempImageFile, e);
                        }
                    }
                }
            }

            log.info("PDF OCR completed, extracted {} characters total", fullText.length());
            return fullText.toString();

        } catch (IOException e) {
            log.error("Failed to process PDF for OCR: {}", pdfFile.getName(), e);
            throw new IOException("PDF OCR processing failed: " + e.getMessage(), e);
        }
    }
}
