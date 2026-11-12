package com.historytalk.service.document;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

@Service
@Slf4j
public class OcrServiceImpl implements OcrService {

    private final String datapath;
    private final String language;
    private final int dpi;
    private volatile String cachedDatapath;
    private final Object datapathLock = new Object();

    public OcrServiceImpl(
            @Value("${tesseract.datapath}") String datapath,
            @Value("${tesseract.language}") String language,
            @Value("${tesseract.dpi}") int dpi) {
        this.datapath = datapath;
        this.language = language;
        this.dpi = dpi;
        log.info("OcrService initialized with datapath: {}, language: {}, dpi: {}", datapath, language, dpi);
    }

    private String resolveDatapath() throws IOException {
        String existing = cachedDatapath;
        if (existing != null) {
            return existing;
        }
        synchronized (datapathLock) {
            if (cachedDatapath != null) {
                return cachedDatapath;
            }
            String effectiveLang = (language != null && !language.isBlank()) ? language : "vie";
            String languageFile = effectiveLang + ".traineddata";
            String absoluteDatapath = null;

            // 1. Thử dùng thư mục tessdata gốc trên ổ cứng nếu đang chạy local (IDE / dev mode)
            try {
                ClassPathResource directResource = new ClassPathResource("tessdata");
                File directFolder = directResource.getFile();
                if (directFolder.exists() && directFolder.isDirectory()) {
                    File directLang = new File(directFolder, languageFile);
                    if (directLang.exists() && directLang.length() > 0) {
                        absoluteDatapath = directFolder.getAbsolutePath();
                        log.info("Using local filesystem tessdata directory: {}", absoluteDatapath);
                    }
                }
            } catch (Exception ignored) {
                // Đang chạy bên trong file JAR (trên Docker/Server), getFile() sẽ văng lỗi.
            }

            // 2. Nếu chạy trong JAR hoặc getFile() không được: Trích xuất .traineddata ra thư mục tạm
            if (absoluteDatapath == null) {
                File tempTessDir = new File(System.getProperty("java.io.tmpdir"), "historytalk_tessdata");
                if (!tempTessDir.exists()) {
                    tempTessDir.mkdirs();
                }

                File targetLangFile = new File(tempTessDir, languageFile);
                if (!targetLangFile.exists() || targetLangFile.length() == 0) {
                    String[] potentialPaths = {
                            "tessdata/" + languageFile,
                            languageFile
                    };

                    for (String path : potentialPaths) {
                        ClassPathResource resource = new ClassPathResource(path);
                        if (resource.exists()) {
                            try (java.io.InputStream in = resource.getInputStream();
                                 java.io.FileOutputStream out = new java.io.FileOutputStream(targetLangFile)) {
                                in.transferTo(out);
                                log.info("Extracted {} from JAR to temporary filesystem: {}", path, targetLangFile.getAbsolutePath());
                                break;
                            }
                        }
                    }
                }

                if (targetLangFile.exists() && targetLangFile.length() > 0) {
                    absoluteDatapath = tempTessDir.getAbsolutePath();
                }
            }

            // 3. Fallback: Đường dẫn tuyệt đối ngoài hệ điều hành
            if (absoluteDatapath == null && datapath != null && !datapath.isBlank() && !datapath.startsWith("classpath:")) {
                File customDir = new File(datapath);
                if (customDir.exists() && customDir.isDirectory()) {
                    absoluteDatapath = customDir.getAbsolutePath();
                }
            }

            if (absoluteDatapath == null) {
                throw new IOException("Cannot resolve tessdata directory for language: " + effectiveLang);
            }

            File tessDataDir = new File(absoluteDatapath);
            if (!tessDataDir.exists() || !tessDataDir.isDirectory()) {
                throw new IOException("Tessdata directory does not exist: " + absoluteDatapath);
            }

            File languageDataFile = new File(tessDataDir, languageFile);
            if (!languageDataFile.exists()) {
                throw new IOException("Language data file not found: " + languageFile + " in directory: " + absoluteDatapath);
            }

            log.info("Tesseract configured with datapath: {}, language: {}", absoluteDatapath, effectiveLang);
            cachedDatapath = absoluteDatapath;
            return absoluteDatapath;
        }
    }

    private Tesseract createTesseractInstance() throws IOException {
        String effectiveLang = (language != null && !language.isBlank()) ? language : "vie";
        String resolved = resolveDatapath();
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(resolved);
        tesseract.setLanguage(effectiveLang);
        return tesseract;
    }

    @Override
    public String extractTextFromImage(File imageFile) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IOException("Image file does not exist");
        }

        try {
            log.debug("Starting OCR on image: {}", imageFile.getName());
            Tesseract tesseract = createTesseractInstance();
            String text = tesseract.doOCR(imageFile);
            log.debug("OCR completed, extracted {} characters", text != null ? text.length() : 0);
            return text;
        } catch (TesseractException e) {
            log.error("OCR failed for image: {}", imageFile.getName(), e);
            throw new IOException("OCR processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractTextFromPdf(File pdfFile) throws IOException {
        return extractTextFromPdf(pdfFile, null);
    }

    @Override
    public String extractTextFromPdf(File pdfFile, BiConsumer<Integer, Integer> onProgress) throws IOException {
        List<PdfLayoutProcessor.ExtractedLine> lines = extractLinesFromPdf(
                pdfFile, 1, Integer.MAX_VALUE, onProgress, null);
        return PdfLayoutProcessor.reconstructMarkdown(lines, List.of());
    }

    @Override
    public List<PdfLayoutProcessor.ExtractedLine> extractLinesFromPdf(
            File pdfFile,
            int startPage,
            int endPage,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        if (pdfFile == null || !pdfFile.exists()) {
            throw new IOException("PDF file does not exist");
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int totalDocPages = document.getNumberOfPages();
            if (totalDocPages == 0) {
                return List.of();
            }

            int effectiveStart = Math.max(1, startPage);
            int effectiveEnd = Math.min(totalDocPages, Math.max(effectiveStart, endPage));
            int pagesToProcess = effectiveEnd - effectiveStart + 1;

            int workers = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
            if (pagesToProcess <= 1) {
                workers = 1;
            }

            log.info("Starting OCR on PDF pages [{} - {}] ({} pages) at {} DPI using {} worker(s)",
                    effectiveStart, effectiveEnd, pagesToProcess, dpi, workers);

            PDFRenderer renderer = new PDFRenderer(document);

            if (workers == 1) {
                return runSingleThreadedOcr(renderer, effectiveStart, effectiveEnd, pagesToProcess, onProgress, isCancelled);
            }
            return runParallelOcr(renderer, effectiveStart, effectiveEnd, pagesToProcess, workers, onProgress, isCancelled);
        } catch (CancellationException ce) {
            throw ce;
        } catch (IOException e) {
            log.error("Failed to process PDF for OCR: {}", pdfFile.getName(), e);
            throw e;
        }
    }

    private List<PdfLayoutProcessor.ExtractedLine> runSingleThreadedOcr(
            PDFRenderer renderer,
            int effectiveStart,
            int effectiveEnd,
            int pagesToProcess,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        Tesseract tesseract = createTesseractInstance();
        List<PdfLayoutProcessor.ExtractedLine> allLines = new ArrayList<>();

        for (int pageNo = effectiveStart; pageNo <= effectiveEnd; pageNo++) {
            checkCancelled(isCancelled);
            BufferedImage image = null;
            try {
                image = renderer.renderImageWithDPI(pageNo - 1, dpi, ImageType.GRAY);
                allLines.addAll(recognizePageLines(tesseract, image, pageNo));
                notifyProgress(onProgress, pageNo - effectiveStart + 1, pagesToProcess);
            } finally {
                if (image != null) {
                    image.flush();
                }
            }
        }
        return allLines;
    }

    private List<PdfLayoutProcessor.ExtractedLine> runParallelOcr(
            PDFRenderer renderer,
            int effectiveStart,
            int effectiveEnd,
            int pagesToProcess,
            int workers,
            BiConsumer<Integer, Integer> onProgress,
            BooleanSupplier isCancelled) throws IOException {
        // Pre-resolve datapath before spawning worker threads
        resolveDatapath();

        @SuppressWarnings("unchecked")
        List<PdfLayoutProcessor.ExtractedLine>[] pageBuckets = new List[pagesToProcess];
        ConcurrentLinkedQueue<Tesseract> tesseractPool = new ConcurrentLinkedQueue<>();
        Semaphore inFlightImages = new Semaphore(workers * 2);
        AtomicInteger completedCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<CompletableFuture<Void>> futures = new ArrayList<>(pagesToProcess);

        try {
            for (int pageNo = effectiveStart; pageNo <= effectiveEnd; pageNo++) {
                checkCancelled(isCancelled);
                try {
                    inFlightImages.acquire();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("OCR bị gián đoạn");
                }

                final int currentPageNo = pageNo;
                final int bucketIndex = pageNo - effectiveStart;
                final BufferedImage renderedImage;
                try {
                    renderedImage = renderer.renderImageWithDPI(currentPageNo - 1, dpi, ImageType.GRAY);
                } catch (Throwable t) {
                    inFlightImages.release();
                    throw t;
                }

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        checkCancelled(isCancelled);
                        Tesseract tess = tesseractPool.poll();
                        if (tess == null) {
                            tess = createTesseractInstance();
                        }
                        try {
                            pageBuckets[bucketIndex] = recognizePageLines(tess, renderedImage, currentPageNo);
                        } finally {
                            tesseractPool.offer(tess);
                        }
                        int done = completedCount.incrementAndGet();
                        notifyProgress(onProgress, done, pagesToProcess);
                    } catch (IOException ioEx) {
                        throw new java.io.UncheckedIOException(ioEx);
                    } finally {
                        renderedImage.flush();
                        inFlightImages.release();
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            cancelFutures(futures);
            throw new CancellationException("OCR bị gián đoạn");
        } catch (ExecutionException ee) {
            cancelFutures(futures);
            Throwable cause = ee.getCause();
            if (cause instanceof CancellationException ce) {
                throw ce;
            }
            if (cause instanceof java.io.UncheckedIOException uio && uio.getCause() != null) {
                throw uio.getCause();
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException("PDF OCR processing failed: " + cause.getMessage(), cause);
        } finally {
            executor.shutdownNow();
            tesseractPool.clear();
        }

        List<PdfLayoutProcessor.ExtractedLine> orderedLines = new ArrayList<>();
        for (List<PdfLayoutProcessor.ExtractedLine> bucket : pageBuckets) {
            if (bucket != null) {
                orderedLines.addAll(bucket);
            }
        }
        return orderedLines;
    }

    private List<PdfLayoutProcessor.ExtractedLine> recognizePageLines(
            Tesseract tesseract,
            BufferedImage image,
            int pageNumber) throws IOException {
        float pageWidth = image.getWidth();
        float pageHeight = image.getHeight();
        List<PdfLayoutProcessor.ExtractedLine> result = new ArrayList<>();

        try {
            List<Word> lines = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
            if (lines != null && !lines.isEmpty()) {
                for (Word line : lines) {
                    if (line == null || line.getText() == null || line.getText().isBlank()) {
                        continue;
                    }
                    Rectangle box = line.getBoundingBox();
                    if (box == null || (box.width <= 0 && box.height <= 0)) {
                        continue;
                    }
                    String text = line.getText().trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    float approxFontSize = Math.max(8f, box.height * 0.72f);
                    result.add(new PdfLayoutProcessor.ExtractedLine(
                            pageNumber,
                            pageWidth,
                            pageHeight,
                            box.x,
                            box.x + box.width,
                            box.y,
                            box.y + box.height,
                            approxFontSize,
                            text
                    ));
                }
                return result;
            }

            // Fallback if getWords returned empty on a non-empty page
            String fallbackText = tesseract.doOCR(image);
            if (fallbackText != null && !fallbackText.isBlank()) {
                String[] rawLines = fallbackText.split("\\R");
                float step = pageHeight / Math.max(1, rawLines.length);
                for (int i = 0; i < rawLines.length; i++) {
                    String t = rawLines[i].trim();
                    if (!t.isEmpty()) {
                        float y = i * step;
                        result.add(new PdfLayoutProcessor.ExtractedLine(
                                pageNumber, pageWidth, pageHeight,
                                0f, pageWidth, y, y + step, 12f, t
                        ));
                    }
                }
            }
            return result;
        } catch (TesseractException e) {
            throw new IOException("OCR processing failed on page " + pageNumber + ": " + e.getMessage(), e);
        } catch (RuntimeException re) {
            if (re instanceof CancellationException) {
                throw re;
            }
            throw new IOException("OCR processing failed on page " + pageNumber + ": " + re.getMessage(), re);
        }
    }

    private void checkCancelled(BooleanSupplier isCancelled) {
        if (isCancelled != null && isCancelled.getAsBoolean()) {
            throw new CancellationException("Quá trình OCR đã bị hủy bởi client ngắt kết nối");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Quá trình OCR đã bị gián đoạn");
        }
    }

    private void notifyProgress(BiConsumer<Integer, Integer> onProgress, int current, int total) {
        if (onProgress == null) {
            return;
        }
        try {
            onProgress.accept(current, total);
        } catch (CancellationException ce) {
            throw ce;
        } catch (Exception ex) {
            log.warn("Progress callback failed for page {}/{}: {}", current, total, ex.getMessage());
        }
    }

    private void cancelFutures(List<CompletableFuture<Void>> futures) {
        for (CompletableFuture<Void> f : futures) {
            f.cancel(true);
        }
    }
}
