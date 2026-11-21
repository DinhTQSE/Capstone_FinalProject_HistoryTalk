# Local Visual Novel (Kịch bản kể chuyện cho nội dung địa phương) — FE Contract (Sprint 10)

Mục tiêu: mang tính năng **visual novel** (`storyScript`) đang có ở bối cảnh HistoryTalk (Content Admin soạn → Customer xem) sang **bối cảnh địa phương B2B** (Teacher soạn → Student xem).

- Business flow: [../business_flows/local_visual_novel_flow.md](../business_flows/local_visual_novel_flow.md)
- Cấu trúc `storyScript` **giống hệt** bối cảnh global, FE dùng lại toàn bộ `features/story/*` (editor, player, validate).

---

## 1. Trạng thái

| Hạng mục | Trạng thái |
|---|---|
| BE: lưu / xóa / trả `storyScript` cho bối cảnh địa phương | ✅ Xong (kèm unit test) |
| BE: chặn xuất bản kịch bản khi bối cảnh/nhân vật HS chưa thấy (2.1) | ✅ Xong (kèm unit test) |
| BE: cascade kịch bản về nháp khi nhân vật bị ẩn/xóa/bỏ liên kết (2.3) + API liệt kê kịch bản bị ảnh hưởng | ✅ Xong (kèm unit test) |
| FE: cảnh báo trước khi ẩn/xóa/bỏ liên kết nhân vật (GV + School Admin) | ✅ Đã có bản cài đặt, cần review |
| FE: Teacher soạn kịch bản (tab "Kể chuyện" + trang soạn) | ✅ Đã có bản cài đặt, cần review |
| FE: Student nghe kể chuyện (thẻ "Nghe kể chuyện" + player) | ✅ Đã có bản cài đặt, cần review |
| FE: các phần còn thiếu để ngang bằng bên global | ⏳ Xem mục 6 |

Các file FE đã sửa/thêm được liệt kê ở mục 5.

---

## 2. API

Không thêm endpoint mới. Chỉ thêm field `storyScript` vào các API bối cảnh địa phương có sẵn.

### 2.1. Teacher ghi kịch bản

`PUT /api/v1/teacher/local-contents/contexts/{contextId}` — role `TEACHER`, **chỉ GV tạo bối cảnh** (GV khác → `403 MSG11`).

```json
{ "storyScript": { "version": 1, "title": "Hai Bà Trưng kể chuyện", "narratorId": "<characterId|null>",
                   "status": "draft", "updatedAt": "2026-11-21T09:00:00.000Z", "nodes": [ ... ], "edges": [ ... ] } }
```

| Body | Kết quả |
|---|---|
| Không có key `storyScript` | Giữ nguyên kịch bản đã lưu (gửi các field khác như cũ không ảnh hưởng kịch bản) |
| `"storyScript": { ... }` | Thay toàn bộ kịch bản |
| `"storyScript": null` | Xóa kịch bản |

Có thể gửi riêng `storyScript` (autosave của editor chỉ gửi field này). Cập nhật kịch bản **không** đổi `isPublished` / `status` của bối cảnh.

**Validate (400 `InvalidRequestException`)** — cùng quy tắc với bối cảnh global:

| Điều kiện | Message |
|---|---|
| Không phải object | `storyScript phải là một object` |
| JSON lỗi | `storyScript không phải JSON hợp lệ` |
| > 500 KB | `storyScript vượt quá dung lượng tối đa 500 KB` |
| `version` ≠ 1 | `storyScript.version phải là 1` |
| `title` không phải chuỗi | `storyScript.title phải là chuỗi` |
| `status` ∉ {`draft`, `published`} | `storyScript.status phải là 'draft' hoặc 'published'` |
| `narratorId` không phải chuỗi/null | `storyScript.narratorId phải là chuỗi hoặc null` |
| `nodes` / `edges` không phải mảng | `storyScript.nodes phải là mảng` / `storyScript.edges phải là mảng` |
| > 300 node | `storyScript tối đa 300 node` |

BE **không** kiểm tra cấu trúc đồ thị bên trong `nodes`/`edges` (việc của FE — dùng `validateStory`).

**Điều kiện xuất bản kịch bản (400)** — chỉ áp dụng cho bối cảnh địa phương, kiểm tra ở **mọi** request có key `storyScript` với `status: "published"` (kể cả autosave khi kịch bản đang xuất bản). Kiểm tra theo trạng thái cuối cùng của bối cảnh sau request (nên gửi `isPublished: true` cùng request cũng được):

| Điều kiện | Message |
|---|---|
| Bối cảnh chưa xuất bản hoặc bị School Admin ẩn | `Hãy xuất bản bối cảnh trước khi xuất bản kịch bản kể chuyện.` |
| `narratorId` hoặc `nodes[].data.speakerId` trỏ tới nhân vật: đã xóa / chưa liên kết bối cảnh / chưa xuất bản (hoặc bị ẩn) / CLASS_ONLY mà bối cảnh SCHOOL_WIDE / CLASS_ONLY không phủ hết các lớp của bối cảnh | `Chưa xuất bản được kịch bản: học sinh sẽ không thấy nhân vật <Tên> (chưa xuất bản), <Tên> (phạm vi lớp hẹp hơn bối cảnh), ... Hãy xuất bản/liên kết nhân vật hoặc chuyển kịch bản về bản nháp.` |

Kịch bản **nháp** luôn lưu được, kể cả khi bối cảnh/nhân vật chưa xuất bản.
Bối cảnh global (Content Admin) **chưa** áp dụng quy tắc này.

### 2.2. Đọc kịch bản

`storyScript` có mặt trong **mọi** response bối cảnh địa phương (`LocalContentResponse`, `type = "CONTEXT"`):

| Endpoint | Role | Nhận được |
|---|---|---|
| `GET /api/v1/teacher/local-contents/contexts` và `/contexts/{id}` | TEACHER | Kịch bản đầy đủ, **kể cả bản nháp** |
| `GET /api/v1/school-admin/local-contents/contexts` | SCHOOL_ADMIN | Kịch bản đầy đủ, kể cả bản nháp |
| `GET /api/v1/student/local-contents/contexts` và `/contexts/{id}` | SCHOOL_STUDENT | Chỉ khi `storyScript.status = "published"`, ngược lại **không có field** |

> `LocalContentResponse` dùng `@JsonInclude(NON_NULL)`: khi không có kịch bản (hoặc HS không được xem bản nháp) thì **key `storyScript` bị bỏ hẳn**, không phải `null`. FE coi `undefined` và `null` như nhau ("chưa có kịch bản").
> Khác bối cảnh global: ở global key luôn có mặt; `storyRepo` global dùng `storyScript !== undefined` để phát hiện BE hỗ trợ — **không áp dụng logic này cho bối cảnh địa phương**.

Điều kiện để HS phát được kịch bản = `isPlayableScript(item.storyScript)` (published và có > 1 node), và bối cảnh phải đang hiện với HS (API student chỉ trả bối cảnh ACTIVE của lớp/trường).

### 2.3. Cascade khi nhân vật trong kịch bản đang xuất bản bị ẩn / xóa

BE tự **chuyển kịch bản đã xuất bản về `status: "draft"`** (giữ nguyên nội dung) khi một nhân vật nằm trong kịch bản (người kể hoặc người nói) không còn hợp lệ theo điều kiện ở 2.1 sau các thao tác:

| Thao tác | Endpoint |
|---|---|
| GV xóa nhân vật (vào thùng rác) | `DELETE /teacher/local-contents/characters/{id}` |
| GV ẩn nhân vật | `PUT /teacher/local-contents/characters/{id}/status` (`INACTIVE`/`DRAFT`) |
| GV gỡ xuất bản / đổi phạm vi / đổi lớp / đổi `contextIds` | `PUT /teacher/local-contents/characters/{id}` |
| GV bỏ liên kết nhân vật với bối cảnh | `DELETE /teacher/local-contents/characters/{id}/contexts/{contextId}` |
| School Admin ẩn / trả lại nhân vật | `PUT /school-admin/local-contents/characters/{id}/status` |
| GV đổi phạm vi / lớp của **bối cảnh** khiến nhân vật CLASS_ONLY không còn phủ | `PUT /teacher/local-contents/contexts/{id}` (`contentScope` / `classIds`) |

Chỉ kịch bản thực sự bị ảnh hưởng mới chuyển về nháp (vd. chỉ đổi tên nhân vật thì không). Không có thông báo trong response — FE cảnh báo **trước** bằng endpoint dưới.

**Danh sách kịch bản sẽ bị ảnh hưởng (để cảnh báo):**

`GET /api/v1/teacher/local-contents/characters/{characterId}/published-stories` (TEACHER)
`GET /api/v1/school-admin/local-contents/characters/{characterId}/published-stories` (SCHOOL_ADMIN)

```json
[ { "contextId": "…", "contextName": "Khởi nghĩa Hai Bà Trưng", "storyTitle": "Hai Bà Trưng kể chuyện" } ]
```
Trả các kịch bản **đang xuất bản** dùng nhân vật này (trong các bối cảnh nhân vật đang liên kết). Mảng rỗng = không cần cảnh báo.

---

## 3. Nhân vật trong kịch bản (speaker)

Bối cảnh địa phương **không có** endpoint "nhân vật của bối cảnh". FE lấy danh sách nhân vật địa phương rồi lọc theo `contextIds`:

| Ai | Nguồn | Lọc |
|---|---|---|
| Teacher (editor, AI) | `GET /teacher/local-contents/characters?page=1&size=100` | `c.contextIds?.includes(contextId)` |
| Student (player) | `GET /student/local-contents/characters?page=1&size=100` (hook `useStudentLocalContent`) | `c.contextIds?.includes(contextId)` |

Map sang `StoryCharacter` bằng `toStoryCharacter({ id, name, title, imageUrl })` rồi `registerCharacters(...)` trước khi render editor/player.

Bấm nhân vật trong player để chat → `ROUTES.CHAT(characterId)` (cùng route chat nhân vật địa phương đang dùng).

---

## 4. AI viết kịch bản

Dùng lại `POST /api/v1/ai-authoring/story/generate` (role `CONTENT_ADMIN`, `TEACHER`). BE đã cho Teacher gọi với **bối cảnh địa phương của trường mình** (khác trường → 403); nhân vật AI dùng = mọi nhân vật liên kết với bối cảnh (kể cả nháp). FE: dialog AI lấy danh sách bối cảnh/nhân vật từ `repo.listContexts()` / `repo.listCharacters()` thay vì API global.

---

## 5. Bản cài đặt FE hiện có (để review)

| File | Nội dung |
|---|---|
| `services/local-content.service.ts` | Thêm `storyScript?: StoryScript \| null` vào `LocalItem` và `LocalContextPayload` |
| `features/story/story-repo.ts` | Thêm type `StoryRepo` (`load`, `save`, `listContexts`, `listCharacters`, `backHref`, `backLabel`); `storyRepo` (global) giữ nguyên; thêm `localStoryRepo(contextId)` |
| `components/story/editor/story-editor.tsx` | `StoryEditor` nhận prop `repo?: StoryRepo` (mặc định global). Nút quay lại dùng `repo.backHref` |
| `components/story/editor/story-ai-dialog.tsx` | Nhận `repo`, lấy bối cảnh/nhân vật qua repo |
| `app/(app)/teaching/local/[id]/story/page.tsx` | Trang soạn của Teacher (`ROUTES.TEACHING.LOCAL_STORY(id)`); sau mỗi lần lưu invalidate `localKeys.all` |
| `components/saas/local-content-extras.tsx` | `LocalStoryPanel`: trạng thái kịch bản, nút Soạn/Sửa, Xóa (gửi `storyScript: null`) |
| `components/saas/teaching-local-api-editor.tsx` | Tab mới `story` — "Kể chuyện" (chỉ bối cảnh đã lưu) |
| `components/saas/local-context-story.tsx` | `LocalContextStory`: thẻ "Nghe kể chuyện" + player toàn màn hình (Esc / nút X để đóng) |
| `app/(app)/classes/[classId]/local/[contentId]/page.tsx`, `app/(app)/classes/materials/[kind]/[id]/page.tsx` | Gắn `LocalContextStory` dưới thông tin bối cảnh |
| `constants/routes.ts` | `ROUTES.TEACHING.LOCAL_STORY` |
| `components/saas/story-cascade-warning.tsx` | `useStoryCascadeGuard()` (hộp thoại cảnh báo trước khi chạy thao tác), `useStoryUsages()` + `StoryCascadeNotice` (chèn danh sách vào hộp thoại có sẵn) |
| `teaching-local-api-editor.tsx`, `app/(app)/teaching/local/page.tsx`, `local-content-extras.tsx`, `app/school/content/page.tsx` | Cảnh báo cascade khi: ẩn nhân vật (nút trong editor, công tắc ở danh sách), xóa nhân vật (trong hộp thoại xóa), lưu khi đổi phạm vi/lớp, "Thôi chia sẻ toàn trường", bỏ liên kết bối cảnh (chỉ kịch bản của bối cảnh đó), School Admin "Ẩn" / "Trả lại giáo viên" |

---

## 6. Việc FE còn phải làm để ngang bằng bên global

| # | Việc | Bên global tương ứng | Ưu tiên |
|---|---|---|---|
| 1 | **Luồng mở bối cảnh của HS**: màn bìa cho chọn *Nghe kể chuyện / Xem video / Gặp nhân vật*; nghe xong chuyển sang danh sách nhân vật, có nút "Nghe lại kể chuyện". Hiện tại chỉ là thẻ + player, nghe xong thì đóng (nút cuối vẫn ghi "Gặp các nhân vật"). | `event-detail-drawer.tsx` (`ContextIntroCover`, `CharactersReveal`) | Cao |
| 2 | **Hiển thị lý do bị chặn xuất bản** (mục 2.1) rõ ràng hơn toast hiện tại; tùy chọn: kiểm tra trước ở FE và đánh dấu node có speaker chưa hợp lệ. Bản cài đặt hiện tại: toast message của BE + tự chuyển kịch bản về "Nháp". | — | Trung bình |
| 3 | **Trang danh sách kịch bản của Teacher**: tất cả bối cảnh của mình, lọc chưa có / nháp / đã xuất bản, tìm kiếm, số lỗi (`validateStory`). | `/staff/stories` (`app/staff/stories/page-content.tsx`) | Trung bình |
| 4 | **GV xem bối cảnh SCHOOL_WIDE của đồng nghiệp** (màn chỉ đọc `ReadTab`): thêm xem/nghe kịch bản (không cho sửa). | — | Thấp |
| 5 | **School Admin xem kịch bản** trong trang quản lý nội dung địa phương (data đã có trong list API). | — | Thấp |

---

## 7. Rủi ro / lỗi tiềm ẩn đã rà

### 7.1. Speaker hiện thành "Người kể" ở phía HS (đã xử lý)
Player tra nhân vật theo id; không tìm thấy thì hiện tên **"Người kể"**, không chân dung, không nút chat. BE **chặn xuất bản** kịch bản dùng nhân vật HS không thấy (2.1) và **cascade về nháp** khi nhân vật bị ẩn/xóa/bỏ liên kết/thu hẹp lớp sau đó (2.3).
Còn lại: thao tác hàng loạt khác ngoài các endpoint ở 2.3 (nếu sau này thêm) phải gọi lại cascade ở BE. Khôi phục nhân vật từ thùng rác **không** tự xuất bản lại kịch bản.

### 7.1b. Editor khi bị chặn xuất bản (đã xử lý trong bản cài đặt)
`publish()` trong editor: nếu lưu thất bại thì đặt lại `status: "draft"` để các lần autosave sau không bị 400 liên tục. Toast lỗi chỉ hiện ở lần lỗi đầu tiên của một chuỗi lỗi (`saveFailed`).

### 7.2. Cache react-query (đã sửa trong bản cài đặt)
Query client mặc định `staleTime` 5 phút, `refetchOnMount: false`. Editor lưu thẳng qua service nên tab "Kể chuyện" sẽ hiện kịch bản cũ. Trang soạn đã invalidate `localKeys.all` sau mỗi lần lưu — nếu viết lại trang, giữ hành vi này.

### 7.3. Payload danh sách lớn
List API trả cả `storyScript` (tối đa 500 KB/bối cảnh) cho mọi item (size 100). Chưa gây lỗi, nhưng nếu nhiều bối cảnh có kịch bản dài thì trang danh sách sẽ chậm. Hướng xử lý sau (BE): bỏ `storyScript` khỏi list, chỉ trả ở detail — khi đó FE player phải lấy detail.

### 7.4. Giới hạn 100 nhân vật
Danh sách speaker lấy 100 nhân vật địa phương đầu tiên rồi lọc. Trường có > 100 nhân vật có thể thiếu speaker trong editor/player.

### 7.5. Không có khóa sửa đồng thời
Hai tab cùng soạn một kịch bản → bản lưu sau ghi đè (giống bên global).

### 7.6. Nhân vật bị xóa
Xóa/ gỡ liên kết nhân vật đang được dùng làm speaker/narrator: kịch bản **về nháp** (2.3), nội dung giữ nguyên nên id nhân vật cũ vẫn nằm trong node. Khi GV mở lại editor, node đó hiện người nói không xác định; GV chọn lại người nói rồi mới xuất bản lại được (BE chặn ở 2.1). Bên global chưa có cascade này.

---

## 8. Checklist test

**Teacher**
- [ ] Bối cảnh chưa lưu không có tab "Kể chuyện"; bối cảnh đã lưu có tab.
- [ ] Soạn kịch bản mới → tự lưu → quay lại tab thấy đúng tiêu đề, số lời thoại, "Bản nháp".
- [ ] Chuyển kịch bản sang xuất bản trong editor → tab hiện "Đã xuất bản".
- [ ] Danh sách speaker chỉ gồm nhân vật địa phương đã liên kết với bối cảnh.
- [ ] AI viết kịch bản chạy được với bối cảnh địa phương.
- [ ] Xóa kịch bản → tab hiện "chưa có kịch bản"; HS không còn thấy.
- [ ] GV khác (cùng trường) gọi PUT `storyScript` lên bối cảnh không phải của mình → 403.
- [ ] Bối cảnh nháp → bấm "Xuất bản" kịch bản → toast "Hãy xuất bản bối cảnh trước..." và kịch bản vẫn là "Nháp".
- [ ] Kịch bản có lời thoại của nhân vật nháp → xuất bản bị chặn, message nêu tên nhân vật.
- [ ] Bối cảnh CLASS_ONLY gán lớp A, B; nhân vật CLASS_ONLY chỉ gán lớp A → bị chặn ("phạm vi lớp hẹp hơn bối cảnh").
- [ ] Bối cảnh + mọi nhân vật đã xuất bản, đúng phạm vi → xuất bản thành công.
- [ ] Kịch bản đang xuất bản: ẩn nhân vật có lời thoại (nút trong editor hoặc công tắc ở danh sách) → hiện cảnh báo liệt kê kịch bản; xác nhận → nhân vật ẩn, tab "Kể chuyện" của bối cảnh hiện "Bản nháp", nội dung còn nguyên.
- [ ] Xóa nhân vật đó → hộp thoại xóa có danh sách kịch bản sẽ về nháp.
- [ ] Bỏ liên kết nhân vật với bối cảnh A → chỉ cảnh báo/ảnh hưởng kịch bản của A.
- [ ] "Thôi chia sẻ toàn trường" một nhân vật đang dùng trong kịch bản của bối cảnh SCHOOL_WIDE → cảnh báo, xác nhận → kịch bản về nháp.
- [ ] Chỉ đổi tên/ảnh nhân vật → không cảnh báo, kịch bản vẫn xuất bản.
- [ ] Nhân vật không nằm trong kịch bản nào → ẩn/xóa không hiện cảnh báo.
- [ ] School Admin ẩn nhân vật đang dùng trong kịch bản → hộp thoại "Ẩn" có danh sách kịch bản; xác nhận → kịch bản về nháp.
- [ ] Gửi kịch bản sai (vd. `status: "archived"`) → 400 kèm message ở mục 2.1.

**Student**
- [ ] Kịch bản nháp → HS không thấy thẻ "Nghe kể chuyện" (response không có `storyScript`).
- [ ] Kịch bản đã xuất bản + bối cảnh đã xuất bản → có thẻ; phát được đến hết; lựa chọn/câu hỏi hoạt động.
- [ ] Bấm nhân vật trong truyện → mở chat đúng nhân vật.
- [ ] Bối cảnh chưa xuất bản / bị School Admin ẩn → HS không mở được bối cảnh.
- [ ] Sau khi GV ẩn một nhân vật đang nói trong truyện → HS không còn thấy thẻ "Nghe kể chuyện" (kịch bản đã về nháp).
