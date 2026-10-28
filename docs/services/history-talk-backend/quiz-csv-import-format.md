# Định dạng CSV import quiz

Endpoint (quyền `CONTENT_ADMIN`, `SYSTEM_ADMIN`, `TEACHER`, `SCHOOL_ADMIN`):

| Mục đích | Endpoint |
|---|---|
| Tải file mẫu | `GET /api/v1/staff/quizzes/import/template` |
| Import | `POST /api/v1/staff/quizzes/import` (multipart, field `file`) |

Quiz được import luôn ở trạng thái nháp (`isPublished = false`).

## Cột

| Cột | Bắt buộc | Ý nghĩa |
|---|---|---|
| `title` | ✅ | Tên quiz. Các dòng cùng `title` thuộc cùng một quiz, mỗi dòng là một câu hỏi. |
| `contextId` | ✅ | UUID của bối cảnh lịch sử (chưa bị xoá). |
| `level` | ✅ | `EASY`, `MEDIUM` hoặc `HARD` — độ khó của **cả quiz**. |
| `questionContent` | ✅ | Nội dung câu hỏi. |
| `questionType` | | `MULTIPLE_CHOICE` (mặc định khi để trống), `FILL_IN_BLANK`, `MATCHING`. Không phân biệt hoa thường. |
| `option1` … `option6` | | Lựa chọn (MC) hoặc cặp nối (MATCHING). |
| `correctAnswer` | | Đáp án đúng (MC, FILL). |
| `caseSensitive` | | Chỉ FILL_IN_BLANK: `true`/`false`, mặc định `false`. |
| `trimWhitespace` | | Chỉ FILL_IN_BLANK: `true`/`false`, mặc định `true`. |
| `explanation` | | Giải thích đáp án. Không bắt buộc, nhưng nên điền (business spec §2.5). Ô trống được lưu `null`. |

Thứ tự cột tự do. Nhiều giá trị trong một ô cách nhau bằng `|` — vì vậy nội dung đáp án không được chứa ký tự `|`.

## Theo từng loại câu hỏi

| Loại | option1..option6 | correctAnswer | caseSensitive / trimWhitespace |
|---|---|---|---|
| `MULTIPLE_CHOICE` | 2–6 lựa chọn, điền liên tục từ `option1` (không bỏ trống xen giữa) | Số thứ tự lựa chọn **bắt đầu từ 0** (`option1` = 0). Nhiều đáp án đúng: `0\|2` — người làm phải chọn đúng và đủ. | Để trống |
| `FILL_IN_BLANK` | Để trống | Các đáp án được chấp nhận: `938\|năm 938` | Tuỳ chọn |
| `MATCHING` | 2–6 cặp, mỗi ô dạng `Vế trái => Vế phải`, điền liên tục từ `option1` | Để trống | Để trống |

Với MATCHING, vế phải được xáo trộn trước khi lưu nên thứ tự hiển thị không lộ đáp án. Không được trùng vế trái hoặc vế phải.

## Quy tắc theo quiz

- Một quiz được phép trộn nhiều loại câu hỏi.
- Mọi dòng cùng `title` phải có **cùng `contextId` và cùng `level`**. Độ khó được tính cho cả quiz; câu khác độ khó cần đưa sang quiz khác.
- Chỉ cần **một dòng lỗi** là cả quiz đó bị bỏ qua (không lưu gì). Các quiz khác trong file vẫn được import. Lỗi trả về trong `errors` dạng `Quiz '<title>' skipped — validation error: Dòng N: ...`, với N là số dòng trong file (dòng tiêu đề = dòng 1).
- Tiêu đề trùng với quiz đã có → quiz bị bỏ qua.
- Dòng có `title` trống bị bỏ qua.

## Ví dụ

```csv
title,contextId,level,questionType,questionContent,option1,option2,option3,option4,correctAnswer,explanation
Bạch Đằng 938,<uuid>,EASY,MULTIPLE_CHOICE,Ai chỉ huy?,Ngô Quyền,Lê Lợi,Trần Hưng Đạo,Lý Thường Kiệt,0,
Bạch Đằng 938,<uuid>,EASY,FILL_IN_BLANK,Trận Bạch Đằng diễn ra năm nào?,,,,,938|năm 938,
Bạch Đằng 938,<uuid>,EASY,MATCHING,Nối nhân vật với trận đánh,Ngô Quyền => Bạch Đằng 938,Lê Lợi => Chi Lăng,,,,
```

File CSV cũ (`title,contextId,level,questionContent,option1..option4,correctAnswer,explanation`, không có `questionType`) vẫn import được như trước.

## Dữ liệu lưu vào DB (`question.question_data`)

```json
// MULTIPLE_CHOICE
{"options":[{"id":"0","text":"Ngô Quyền"},{"id":"1","text":"Lê Lợi"}],"correctOptionIds":["0"]}
// FILL_IN_BLANK
{"acceptedAnswers":["938","năm 938"],"caseSensitive":false,"trimWhitespace":true}
// MATCHING
{"leftItems":[{"id":"L1","text":"Ngô Quyền"}],"rightItems":[{"id":"R1","text":"Bạch Đằng 938"}],
 "correctPairs":[{"leftId":"L1","rightId":"R1"}]}
```

Cùng bộ kiểm tra này được áp dụng cho API thêm/sửa câu hỏi thủ công, nên dữ liệu sai cấu trúc không vào được DB.

## Excel và lỗi font tiếng Việt

Excel trên Windows mở file CSV UTF-8 **không có BOM** bằng bảng mã ANSI, khiến `Trận Bạch Đằng` hiển thị thành `Tráº­n Báº¡ch Äáº±ng`. Nếu bấm Save lúc đó, chữ lỗi được ghi thật vào file.

- Bắt đầu từ file mẫu (`/import/template`): file có BOM nên Excel hiển thị đúng.
- Khi lưu: **File > Save As > CSV UTF-8 (Comma delimited) (\*.csv)**. Định dạng "CSV (Comma delimited)" thường lưu bằng ANSI và sẽ bị từ chối với thông báo yêu cầu lưu lại bằng UTF-8.
- Nếu đã thấy chữ lỗi kiểu `Tráº­n`: **không Save**. Mở lại bằng **Data > From Text/CSV**, chọn *File Origin: 65001: Unicode (UTF-8)*.
- Hệ thống từ chối file chứa chữ đã bị lỗi font (báo số dòng) thay vì lưu dữ liệu sai.
- Nếu Excel lưu với dấu `;` (thiết lập vùng miền), hệ thống báo lỗi và gợi ý lưu lại với dấu phẩy.
