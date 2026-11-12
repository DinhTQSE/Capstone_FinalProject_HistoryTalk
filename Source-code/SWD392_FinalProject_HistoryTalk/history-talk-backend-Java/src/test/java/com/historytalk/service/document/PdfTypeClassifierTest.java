package com.historytalk.service.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTypeClassifierTest {

    private final PdfTypeClassifier classifier = new PdfTypeClassifier();

    private void writeLines(PDDocument doc, PDPage page, ListLinesWriter writer) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            cs.setFont(font, 11);
            writer.write(cs);
        }
    }

    @FunctionalInterface
    private interface ListLinesWriter {
        void write(PDPageContentStream cs) throws IOException;
    }

    @Test
    void classify_distinguishesNativePagesFromScannedRangeWithinSameDocument() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // Pages 1-6: rich native text
            for (int p = 1; p <= 6; p++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                final int pageNum = p;
                writeLines(doc, page, cs -> {
                    cs.beginText();
                    cs.newLineAtOffset(60, 720);
                    cs.showText("Page " + pageNum + ": Historical campaign chronicles describe strategic encirclement and");
                    cs.newLineAtOffset(0, -18);
                    cs.showText("artillery deployment across difficult mountainous terrain during the spring offensive.");
                    cs.newLineAtOffset(0, -18);
                    cs.showText("Commanders coordinated supply lines, engineering battalions, and defensive trenches.");
                    cs.endText();
                });
            }

            // Pages 7-8: scanned image pages (embedded image XObjects, no fonts)
            BufferedImage scanImg = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scanImg.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, 240, 180);
            g.dispose();
            PDImageXObject pdScanImg = LosslessFactory.createFromImage(doc, scanImg);
            for (int p = 7; p <= 8; p++) {
                PDPage scanPage = new PDPage();
                doc.addPage(scanPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, scanPage)) {
                    cs.drawImage(pdScanImg, 50, 400, 240, 180);
                }
            }

            assertThat(classifier.classify(doc, 1, 8)).isEqualTo(PdfType.NATIVE_TEXT);
            assertThat(classifier.classify(doc, 7, 8)).isEqualTo(PdfType.SCANNED_IMAGE);
        }
    }
}
