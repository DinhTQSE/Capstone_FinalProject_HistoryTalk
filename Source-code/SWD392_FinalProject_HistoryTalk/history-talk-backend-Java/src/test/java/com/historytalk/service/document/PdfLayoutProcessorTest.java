package com.historytalk.service.document;

import com.historytalk.service.document.PdfLayoutProcessor.ExtractedLine;
import com.historytalk.service.document.PdfLayoutProcessor.PageImagePlacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfLayoutProcessorTest {

    private static ExtractedLine line(int page, float leftX, float rightX, float topY, float bottomY, String text) {
        return new ExtractedLine(page, 595f, 842f, leftX, rightX, topY, bottomY, 12f, text);
    }

    @Test
    void filterHeadersAndFooters_removesRepeatedRunningHeadersOddEvenHeadersAndPageNumbers() {
        List<ExtractedLine> input = List.of(
                // Page 1: odd running header + Scribd boilerplate + body + page number footer
                line(1, 72f, 400f, 30f, 44f, "LỊCH SỬ VIỆT NAM - TẬP 1"),
                line(1, 72f, 350f, 52f, 64f, "https://www.scribd.com/document/999999/test"),
                line(1, 72f, 500f, 160f, 174f, "Năm 1954 quân ta mở chiến dịch Điện Biên Phủ."),
                line(1, 250f, 340f, 800f, 814f, "Trang 1 / 4"),

                // Page 2: even running header + body + page number footer
                line(2, 72f, 420f, 30f, 44f, "CHƯƠNG I: KHÁNG CHIẾN CHỐNG PHÁP"),
                line(2, 72f, 500f, 160f, 174f, "Đại tướng Võ Nguyên Giáp trực tiếp chỉ huy chiến dịch."),
                line(2, 250f, 340f, 800f, 814f, "Trang 2 / 4"),

                // Page 3: odd running header + body + page number footer
                line(3, 72f, 400f, 30f, 44f, "LỊCH SỬ VIỆT NAM - TẬP 1"),
                line(3, 72f, 500f, 160f, 174f, "Đợt tiến công đầu tiên đánh vào cứ điểm Him Lam."),
                line(3, 250f, 340f, 800f, 814f, "Trang 3 / 4"),

                // Page 4: even running header + body + page number footer
                line(4, 72f, 420f, 30f, 44f, "CHƯƠNG I: KHÁNG CHIẾN CHỐNG PHÁP"),
                line(4, 72f, 500f, 160f, 174f, "Chiến thắng lừng lẫy năm châu, chấn động địa cầu."),
                line(4, 250f, 340f, 800f, 814f, "Trang 4 / 4")
        );

        List<ExtractedLine> filtered = PdfLayoutProcessor.filterHeadersAndFooters(input);

        assertThat(filtered)
                .extracting(ExtractedLine::text)
                .containsExactly(
                        "Năm 1954 quân ta mở chiến dịch Điện Biên Phủ.",
                        "Đại tướng Võ Nguyên Giáp trực tiếp chỉ huy chiến dịch.",
                        "Đợt tiến công đầu tiên đánh vào cứ điểm Him Lam.",
                        "Chiến thắng lừng lẫy năm châu, chấn động địa cầu."
                );
    }

    @Test
    void reconstructMarkdown_reflowsSoftWrappedLinesAndContinuesParagraphAcrossPageBreak() {
        List<ExtractedLine> input = List.of(
                // Page 1 paragraph 1 (3 soft-wrapped lines)
                line(1, 72f, 490f, 140f, 152f, "Ngày 13 tháng 3 năm 1954, quân đội ta chính thức mở màn"),
                line(1, 72f, 488f, 156f, 168f, "chiến dịch Điện Biên Phủ bằng trận đánh tiêu diệt cứ điểm"),
                line(1, 72f, 150f, 172f, 184f, "Him Lam."),
                // Page 1 paragraph 2 (large gap before it, continues across page break)
                line(1, 72f, 495f, 220f, 232f, "Trong suốt 56 ngày đêm khoét núi ngủ hầm, bộ đội ta đã"),
                // Page 2 top continuation line
                line(2, 72f, 460f, 140f, 152f, "vượt qua muôn vàn gian khổ để giành thắng lợi trọn vẹn.")
        );

        String markdown = PdfLayoutProcessor.reconstructMarkdown(input, List.of());

        assertThat(markdown).isEqualTo(
                "Ngày 13 tháng 3 năm 1954, quân đội ta chính thức mở màn chiến dịch Điện Biên Phủ bằng trận đánh tiêu diệt cứ điểm Him Lam.\n\n"
                        + "Trong suốt 56 ngày đêm khoét núi ngủ hầm, bộ đội ta đã vượt qua muôn vàn gian khổ để giành thắng lợi trọn vẹn."
        );
    }

    @Test
    void reconstructMarkdown_preservesHeadingsListsAndPlacesImagesAtParagraphBoundaries() {
        List<ExtractedLine> input = List.of(
                line(1, 72f, 350f, 140f, 154f, "CHƯƠNG II: DIỄN BIẾN CHIẾN DỊCH"),
                line(1, 72f, 480f, 178f, 190f, "- Đợt 1: Từ ngày 13/3 đến 17/3 tiêu diệt phân khu Bắc."),
                line(1, 72f, 490f, 220f, 232f, "Bức ảnh tư liệu ghi lại khoảnh khắc lá cờ Quyết chiến Quyết thắng"),
                line(1, 72f, 320f, 236f, 248f, "tung bay trên nóc hầm tướng Đờ Cát chiều ngày 7/5/1954."),
                line(1, 72f, 360f, 275f, 287f, "Hình 1: Cờ chiến thắng trên nóc hầm Đờ Cát.")
        );

        // Image Y coordinate overlaps middle of the 2-line paragraph (topY=230)
        List<PageImagePlacement> images = List.of(
                new PageImagePlacement(1, 230f, 330f, "![Hình 1](https://cdn.test/flag.jpg)")
        );

        String markdown = PdfLayoutProcessor.reconstructMarkdown(input, images);

        assertThat(markdown).contains(
                "Bức ảnh tư liệu ghi lại khoảnh khắc lá cờ Quyết chiến Quyết thắng tung bay trên nóc hầm tướng Đờ Cát chiều ngày 7/5/1954."
        );
        assertThat(markdown).contains("![Hình 1](https://cdn.test/flag.jpg)");
        assertThat(markdown).contains("CHƯƠNG II: DIỄN BIẾN CHIẾN DỊCH");
        assertThat(markdown).contains("- Đợt 1: Từ ngày 13/3 đến 17/3 tiêu diệt phân khu Bắc.");
        assertThat(markdown).contains("Hình 1: Cờ chiến thắng trên nóc hầm Đờ Cát.");
    }

    private static ExtractedLine ocrLine(int page, float leftX, float rightX, float topY, float bottomY, String text) {
        return new ExtractedLine(page, 1275f, 1650f, leftX, rightX, topY, bottomY, 0f, text);
    }

    @Test
    void reconstructMarkdown_stripsNoisyOcrRunningHeadersThinMarginRulesAndReflowsMirrorMarginBook() {
        List<ExtractedLine> input = List.of(
                // Page 1 (odd: left~210, right~1030): symbol dust + body with varying OCR heights
                ocrLine(1, 530f, 750f, 350f, 370f, "ˆ^ „"),
                ocrLine(1, 250f, 1030f, 410f, 448f, "Trong cuộc kháng chiến chống quân xâm lược Nam Hán lần thứ"),
                ocrLine(1, 210f, 1028f, 460f, 505f, "hai năm 938, trận Bạch Đằng do Ngô Quyền chỉ huy là trận quyết"),
                ocrLine(1, 210f, 1030f, 515f, 550f, "chiến chiến lược có ý nghĩa to lớn trên nhiều lĩnh vực và còn"),
                ocrLine(1, 210f, 1028f, 565f, 608f, "ghi dấu ấn sâu đậm trong lịch sử chống ngoại xâm hào hùng của"),

                // Page 2 (even mirror margin: left~280, right~1100): fuzzy OCR parity header + thin rule noise + continuation
                ocrLine(2, 915f, 1095f, 122f, 149f, "ĐĂNG VIỆT THỦY"),
                ocrLine(2, 275f, 1095f, 154f, 170f, "562 2C TP HH"),
                ocrLine(2, 280f, 760f, 198f, 240f, "toàn dân tộc ta từ thuở dựng nước đến nay."),
                ocrLine(2, 320f, 1100f, 255f, 298f, "Đoạn văn mới ở trang chẵn được thụt lề rõ ràng so với lề trái"),
                ocrLine(2, 280f, 1098f, 310f, 352f, "của riêng trang chẵn nên không bị gộp vào đoạn trước đó và"),
                ocrLine(2, 280f, 1098f, 365f, 405f, "cũng không bị tách vụn thành từng dòng ngắn rời rạc nữa."),

                // Page 3 (odd): fuzzy OCR odd header variant 1 + body
                ocrLine(3, 205f, 755f, 125f, 152f, "10 TR.ÂN ĐÁNH NỔI TIẾNG TRONG LịCH SỬ VIỆT NAM"),
                ocrLine(3, 250f, 1030f, 200f, 242f, "Bạch Đằng là dòng sông gắn liền với chiến công năm 938."),
                ocrLine(3, 210f, 1028f, 255f, 296f, "Địa thế hiểm trở giúp quân dân ta mai phục đánh tan thủy quân."),
                ocrLine(3, 210f, 1028f, 310f, 350f, "Thủy triều lên xuống tạo lợi thế chiến thuật quyết định trận đánh."),
                ocrLine(3, 210f, 1028f, 365f, 405f, "Chiến thắng mở ra kỷ nguyên độc lập tự chủ lâu dài cho đất nước."),

                // Page 4 (even): exact ĐẶNG VIỆT THỦY + rule noise + body
                ocrLine(4, 915f, 1095f, 122f, 149f, "ĐẶNG VIỆT THỦY"),
                ocrLine(4, 275f, 1095f, 154f, 170f, "——————Ơ Ơn"),
                ocrLine(4, 280f, 1098f, 200f, 242f, "Trang bốn tiếp tục nội dung lịch sử triều đại nhà Ngô."),

                // Page 5 (odd): fuzzy OCR odd header variant 2 + body
                ocrLine(5, 205f, 755f, 125f, 152f, "10 TRẤN ĐÁNH NỔI TIẾNG TR0NG LỊCH SỬ VIỆT NAM"),
                ocrLine(5, 210f, 1028f, 200f, 242f, "Trang năm phân tích bài học quân sự để lại cho đời sau.")
        );

        String markdown = PdfLayoutProcessor.reconstructMarkdown(input, List.of());

        assertThat(markdown).doesNotContain("ĐĂNG VIỆT THỦY");
        assertThat(markdown).doesNotContain("ĐẶNG VIỆT THỦY");
        assertThat(markdown).doesNotContain("TR.ÂN ĐÁNH");
        assertThat(markdown).doesNotContain("TRẤN ĐÁNH");
        assertThat(markdown).doesNotContain("562 2C TP HH");
        assertThat(markdown).doesNotContain("——————");
        assertThat(markdown).doesNotContain("ˆ^ „");

        assertThat(markdown).contains(
                "Trong cuộc kháng chiến chống quân xâm lược Nam Hán lần thứ hai năm 938, trận Bạch Đằng do Ngô Quyền chỉ huy là trận quyết chiến chiến lược có ý nghĩa to lớn trên nhiều lĩnh vực và còn ghi dấu ấn sâu đậm trong lịch sử chống ngoại xâm hào hùng của toàn dân tộc ta từ thuở dựng nước đến nay."
        );
        assertThat(markdown).contains(
                "Đoạn văn mới ở trang chẵn được thụt lề rõ ràng so với lề trái của riêng trang chẵn nên không bị gộp vào đoạn trước đó và cũng không bị tách vụn thành từng dòng ngắn rời rạc nữa."
        );
    }
}
