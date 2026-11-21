# Business Flow — Visual Novel cho nội dung địa phương (Sprint 10)

FE contract: [../fe_contracts/local_visual_novel_fe_contract.md](../fe_contracts/local_visual_novel_fe_contract.md)

## 1. Đối xứng Global ↔ Local

| | Global (B2C) | Local (B2B) |
|---|---|---|
| Người soạn | Content Admin | Giáo viên tạo bối cảnh |
| Nơi soạn | `/staff/stories/[contextId]` | Tab "Kể chuyện" → `/teaching/local/[contextId]/story` |
| Người xem | Customer, học sinh (bối cảnh global) | Học sinh của lớp/trường được xem bối cảnh |
| Lưu ở | `historical_context.story_script` | Cùng cột `historical_context.story_script` (bối cảnh có `school_id`) |
| API ghi | `PUT /historical-contexts/{id}` | `PUT /teacher/local-contents/contexts/{id}` |
| Speaker | Nhân vật của bối cảnh (đã xuất bản) | Nhân vật địa phương liên kết bối cảnh (`contextIds`) |
| Bản nháp | Chỉ staff thấy | Chỉ GV / School Admin thấy |

## 2. Luồng soạn (Teacher)

1. GV tạo bối cảnh địa phương và lưu (tab "Kể chuyện" chỉ có sau khi lưu).
2. GV tạo/ liên kết nhân vật địa phương vào bối cảnh → các nhân vật này thành speaker trong editor.
3. GV mở "Soạn kịch bản": thêm lời thoại, lựa chọn, câu hỏi, nền, chọn người kể; có thể dùng AI viết từ tài liệu của bối cảnh.
4. Editor tự lưu (~1,2 s sau mỗi thay đổi) với `status: "draft"` → HS chưa thấy.
5. GV chuyển kịch bản sang **Đã xuất bản** trong editor. BE chỉ cho xuất bản khi **bối cảnh đã xuất bản** và **mọi nhân vật trong truyện (người kể + người nói) đã xuất bản, đã liên kết với bối cảnh và có phạm vi phủ bối cảnh** (SCHOOL_WIDE, hoặc CLASS_ONLY gán đủ các lớp của bối cảnh CLASS_ONLY). Không đạt → báo lỗi nêu tên nhân vật, kịch bản giữ "Nháp".
6. GV có thể xóa kịch bản (gửi `storyScript: null`).

Quyền: chỉ GV tạo bối cảnh được sửa kịch bản. School Admin không sửa, chỉ có thể ẩn cả bối cảnh (khi đó HS không thấy bối cảnh lẫn kịch bản).

## 3. Luồng xem (Student)

HS thấy thẻ "Nghe kể chuyện" khi **đủ cả 3**:
1. Bối cảnh đang ACTIVE và HS được xem (SCHOOL_WIDE, hoặc CLASS_ONLY được gán cho lớp của HS).
2. `storyScript.status = "published"`.
3. Kịch bản có nội dung phát được (> 1 node).

HS nghe truyện, chọn lựa chọn, trả lời câu hỏi; bấm nhân vật để chat. Không ghi nhận tiến độ/điểm (giống bên global).

## 4. Quy tắc dữ liệu

- `storyScript` vắng mặt trong request = giữ nguyên; `null` = xóa; object = thay toàn bộ.
- Sửa kịch bản không đổi trạng thái xuất bản của bối cảnh và không cần School Admin duyệt.
- Xóa mềm bối cảnh → kịch bản đi theo bối cảnh (khôi phục từ thùng rác thì kịch bản còn nguyên).
- **Cascade:** kịch bản đang xuất bản mà một nhân vật trong truyện bị GV xóa / ẩn / gỡ xuất bản / bỏ liên kết / thu hẹp lớp, hoặc bị School Admin ẩn / trả lại → BE tự chuyển kịch bản về **nháp** (giữ nội dung). FE cảnh báo trước, liệt kê kịch bản bị ảnh hưởng; GV sửa nhân vật rồi xuất bản lại kịch bản. Khôi phục nhân vật không tự xuất bản lại kịch bản.
- Bối cảnh global (Content Admin) chưa áp dụng quy tắc xuất bản này.
