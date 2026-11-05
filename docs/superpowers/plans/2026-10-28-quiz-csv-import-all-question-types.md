# Quiz CSV Import — All Question Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /api/v1/staff/quizzes/import` tạo được câu hỏi `MULTIPLE_CHOICE`, `FILL_IN_BLANK`, `MATCHING` (trộn được trong 1 quiz) từ CSV; chặn độ khó lẫn lộn trong 1 quiz; chặn file bị lỗi font tiếng Việt do Excel; mọi `question_data` ghi vào DB đều đúng shape mà `gradeAnswer` / `sanitizeQuestionDataForLearner` đọc được.

**Architecture:** Toàn bộ thay đổi nằm trong module quiz. Thêm package `service/quiz/csv/`: `QuizCsvFileReader` (đọc bytes → records: BOM, UTF-8 strict, phát hiện lỗi font, gợi ý dấu `;`), `QuizCsvRowParser` + 3 strategy theo `QuestionType` (giống Strategy/Factory của document processing — technical guide §2.4), `QuizCsvTemplateBuilder` (file mẫu có BOM). Thêm `service/quiz/QuestionDataValidator` dùng chung cho CSV và API thêm/sửa câu hỏi. `QuizServiceImpl.importQuizzesFromCsv` chỉ điều phối: đọc → gom nhóm theo `title` → validate cả nhóm → lưu.

**Tech Stack:** Java 21, Spring Boot 3.2, Apache Commons CSV 1.11.0 (đã có), Jackson, JUnit 5 + Mockito + AssertJ.

**Spec:**
- Yêu cầu user (chat 2026-10-28): import CSV cho cả 3 loại câu hỏi, đúng format DB; báo lỗi khi 1 quiz có câu hỏi khác độ khó; xử lý lỗi font khi mở CSV bằng Excel; chỉ sửa trong module quiz.
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/docs/historytalk-overall-business-spec.md` (§2.5 Quiz, §2.9 Trash, §3 RBAC)
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/docs/historytalk-overall-technical-guide.md` (§2.1 package, §2.3 soft-delete, §2.4 Strategy/Factory)

Mọi lệnh `mvn` chạy từ `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`. Viết tắt: `…/` = `src/main/java/com/historytalk/`, `T/` = `src/test/java/com/historytalk/`.

## Global Constraints

- **Chỉ module quiz.** File được phép tạo/sửa: `…/service/quiz/**`, `…/controller/quiz/StaffQuizController.java`, `…/dto/quiz/**` (chỉ nếu cần), `T/service/quiz/**`, `T/controller/quiz/**`, và tài liệu ở `docs/services/history-talk-backend/`. **Không** sửa gamification, assignment, repository ngoài quiz, `SecurityConfig`, `application.properties`, `pom.xml`, migration. Nếu một bước buộc phải chạm file khác → dừng và hỏi.
- Không đổi logic submit/time-limit/XP (`submitQuiz`, `gamificationService.recordProgress`) — ngoài phạm vi.
- Package theo technical guide §2.1: logic trong `service`, controller chỉ nhận/trả HTTP; lỗi dùng exception có sẵn (`InvalidRequestException`, `ResourceNotFoundException`, `DataConflictException`) để `@ControllerAdvice` hiện có xử lý.
- Soft-delete theo technical guide §2.3: lọc thủ công `deletedAt` ở service. Import vào `HistoricalContext` có `deletedAt != null` → lỗi như context không tồn tại.
- RBAC: endpoint mới kế thừa `@PreAuthorize` cấp class của `StaffQuizController` (`CONTENT_ADMIN, SYSTEM_ADMIN, TEACHER, SCHOOL_ADMIN`), không thêm rule riêng.
- `explanation` **không bắt buộc** (nhất quán với `QuestionRequest`/`POST /staff/quizzes` hiện tại — không có `@NotBlank`, cột DB nullable). Ô trống → lưu `null`. Business spec §2.5 khuyến nghị có giải thích cho mỗi câu; quyết định của user (2026-10-28): chưa ép buộc ở bất kỳ endpoint nào.
- Độ khó (`level`: EASY/MEDIUM/HARD) là thuộc tính của **quiz** (`quiz.level`); `question` không có cột độ khó. Không thêm cột.
- File CSV cũ (header `title,contextId,level,questionContent,option1..option4,correctAnswer,explanation`, không có `questionType`) vẫn import được: MC, `correctAnswer` index **0-based**.
- Shape JSON lưu vào `question.question_data` (đây là "format DB"):
  - MC: `{"options":[{"id":"0","text":"..."},...],"correctOptionIds":["0"]}` — option id = index 0-based dạng chuỗi.
  - FILL: `{"acceptedAnswers":["938","năm 938"],"caseSensitive":false,"trimWhitespace":true}`
  - MATCHING: `{"leftItems":[{"id":"L1","text":".."}],"rightItems":[{"id":"R1","text":".."}],"correctPairs":[{"leftId":"L1","rightId":"R2"}]}`
- Thông báo lỗi tiếng Việt, dạng `"Dòng N: ..."` với N = số dòng thật trong file (header = dòng 1).
- Quiz import vẫn là draft (`isPublished=false`); quiz lỗi bị skip và báo trong `QuizImportResponse.errors`, không ảnh hưởng quiz khác trong file.

### Format CSV (chốt)

Header bắt buộc: `title, contextId, level, questionContent`. Tùy chọn: `questionType, option1..option6, correctAnswer, explanation, caseSensitive, trimWhitespace`. Thứ tự cột tự do. Phân cách nhiều giá trị: `|` (không dùng được `|` trong nội dung đáp án). Encoding: **UTF-8** (có hoặc không BOM). Dấu phân cách cột: `,`.

| questionType (rỗng = MULTIPLE_CHOICE, không phân biệt hoa thường) | option1..option6 | correctAnswer | caseSensitive / trimWhitespace |
|---|---|---|---|
| `MULTIPLE_CHOICE` | ≥2, liên tục từ option1 | index 0-based, nhiều đáp án `0\|2` | để trống |
| `FILL_IN_BLANK` | để trống | đáp án chấp nhận `938\|năm 938` | `true`/`false`, rỗng = `false` / `true` |
| `MATCHING` | ≥2 cặp, mỗi ô `Vế trái => Vế phải`, liên tục từ option1 | để trống | để trống |

Các dòng cùng `title` = 1 quiz; được trộn `questionType`, nhưng **phải cùng `contextId` và cùng `level`**.

### Lỗi font khi mở CSV bằng Excel (nguyên nhân & xử lý)

Excel trên Windows mở file `.csv` **không có BOM** bằng bảng mã ANSI (Windows-1252/1258) → chữ UTF-8 hiện thành `Tráº­n Báº¡ch Äáº±ng`. Nếu người dùng bấm Save, chuỗi lỗi đó được ghi thật vào file (dạng UTF-8 hợp lệ hoặc ANSI). Code không sửa được Excel, nhưng xử lý được 3 đầu:
1. **Phát file mẫu đúng chuẩn:** `GET /api/v1/staff/quizzes/import/template` trả CSV UTF-8 **có BOM** (`EF BB BF`) → Excel tự nhận UTF-8, hiển thị đúng; Save trong Excel giữ định dạng "CSV UTF-8".
2. **Từ chối file không phải UTF-8** (Excel "CSV (Comma delimited)" lưu ANSI): decode với `CodingErrorAction.REPORT`; lỗi → `"File CSV không phải UTF-8. Trong Excel chọn File > Save As > 'CSV UTF-8 (Comma delimited) (*.csv)'"`.
3. **Từ chối file đã bị lỗi font** (UTF-8 hợp lệ nhưng chứa chuỗi mojibake): regex `Ã[\u0080-¿]|á[º»]|Æ°|Ä[\u0090‘]` trên toàn nội dung → lỗi `"Dòng N: phát hiện ký tự lỗi font ('<đoạn>'). File đã bị Excel đọc sai bảng mã — tải lại file mẫu và nhập lại bằng 'CSV UTF-8'"`. Không tự động "sửa ngược" vì Windows-1252 mất byte `0x81, 0x8D, 0x8F, 0x90, 0x9D` → sửa có thể ra sai chữ và lưu dữ liệu lịch sử sai.

## Review Focus

1. **File lưu từ Excel có BOM** → import bình thường, không báo "thiếu cột 'title'". Test: Task 2 `stripsUtf8Bom`.
2. **Một dòng lỗi ở giữa nhóm** → không lưu quiz lẫn câu hỏi nào của nhóm (hiện tại quiz + các câu trước bị lưu dở). Test: Task 4 `invalidRowSkipsWholeQuizWithoutSaving`.
3. **Cùng `title` khác `level`** (EASY ở dòng 2, HARD ở dòng 3) → skip quiz, lỗi nêu rõ dòng và 2 giá trị; hiện tại lặng lẽ lấy dòng đầu. Test: Task 4 `mixedLevelInOneQuizIsRejected`.
4. **Chuỗi lỗi font hợp lệ UTF-8** (`Tráº­n`) → bị chặn, không ghi rác vào DB. Test: Task 2 `rejectsMojibakeText`; chữ Việt đúng như `NGÃ BA`, `Ngô Quyền`, `Đ` không bị chặn nhầm: Task 2 `acceptsLegitVietnamese`.
5. **MATCHING lộ đáp án qua thứ tự/ID** → right items xáo trộn trước khi gán `R1..Rn`. Test: Task 3 `matchingShufflesRightItemsBeforeAssigningIds`.

---

### Task 1: `QuestionDataValidator` + gắn vào add/update câu hỏi

**Files:**
- Create: `…/service/quiz/QuestionDataValidator.java`
- Modify: `…/service/quiz/QuizServiceImpl.java` (`buildQuestion` ~L722, `updateQuestion` ~L463-492, thêm field)
- Test: `T/service/quiz/QuestionDataValidatorTest.java`, `T/service/quiz/QuizServiceImplTest.java`

**Interfaces:**
- Produces: `@Component public class QuestionDataValidator { public QuestionDataValidator(ObjectMapper objectMapper); public void validate(QuestionType type, JsonNode questionData) }` — ném `InvalidRequestException` (lỗi đầu tiên), không trả gì khi hợp lệ.

Rules (đủ để các hàm `grade*` không bao giờ rơi vào nhánh `catch`/NPE):
- `questionData` null → `"Yêu cầu questionData cho câu hỏi loại <TYPE>"` (giữ message cũ). Không deserialize được → `"questionData không đúng định dạng cho loại <TYPE>"`.
- MC: `options` ≥ 2; `id`, `text` không blank; `id` không trùng; `correctOptionIds` ≥ 1, không trùng, đều có trong `options`.
- FILL: `acceptedAnswers` ≥ 1, không phần tử null/blank.
- MATCHING: `leftItems`, `rightItems` mỗi bên ≥ 2, id/text không blank, id không trùng trong mỗi bên; `correctPairs.size() == leftItems.size()`; mỗi `leftId` đúng 1 lần, mỗi `rightId` tối đa 1 lần, mọi id tham chiếu tồn tại.

- [ ] **Step 1: Viết test thất bại** — `QuestionDataValidatorTest` với `new QuestionDataValidator(new ObjectMapper())`:
  - `validMultipleChoicePasses`, `validFillInBlankPasses`, `validMatchingPasses` → `assertThatCode(...).doesNotThrowAnyException()`
  - `mcCorrectIdNotInOptionsFails` (`["9"]`, message chứa `"9"`), `mcSingleOptionFails`, `mcDuplicateOptionIdFails`
  - `fillBlankAcceptedAnswerFails` (`["938",""]`), `fillEmptyListFails`
  - `matchingPairCountMismatchFails`, `matchingRightIdUsedTwiceFails`, `matchingUnknownLeftIdFails`
  - `nullDataFails` → message `"Yêu cầu questionData cho câu hỏi loại FILL_IN_BLANK"`
- [ ] **Step 2:** `mvn -q test -Dtest=QuestionDataValidatorTest` → FAIL (class chưa có).
- [ ] **Step 3: Implement** — `objectMapper.treeToValue(node, XxxQuestionData.class)` với DTO trong `dto/quiz/quizdata`.
- [ ] **Step 4:** chạy lại → PASS.
- [ ] **Step 5: Gắn vào service.** Field `private final QuestionDataValidator questionDataValidator;`.
  - `buildQuestion`: thay check null bằng `questionDataValidator.validate(type, request.getQuestionData())`.
  - `updateQuestion`: tính effective type (request hoặc cũ) + effective data (request hoặc `deserializeJsonNode(question.getQuestionData())`), validate **trước** khi set entity.
  - Test `QuizServiceImplTest` (thêm `@Mock QuestionDataValidator`): `updateQuestionTypeOnlyRevalidatesExistingData` — câu MC cũ, request chỉ `questionType=MATCHING` → `verify(validator).validate(eq(QuestionType.MATCHING), any())`; mock ném `InvalidRequestException` → `questionRepository.save` không được gọi.
- [ ] **Step 6:** `mvn -q test -Dtest='QuestionDataValidatorTest,QuizServiceImplTest'` → PASS.
- [ ] **Step 7: Commit** `feat(java): validate quiz questionData per question type`

---

### Task 2: `QuizCsvFileReader` — encoding, BOM, lỗi font, dấu `;`

**Files:**
- Create: `…/service/quiz/csv/QuizCsvFileReader.java`
- Test: `T/service/quiz/csv/QuizCsvFileReaderTest.java`

**Interfaces:**
- Produces:
  - `@Component public class QuizCsvFileReader`
  - `public static final List<String> REQUIRED_HEADERS = List.of("title","contextId","level","questionContent");`
  - `public List<CSVRecord> read(MultipartFile file)` — trả các record dữ liệu (bỏ dòng trống), header đã kiểm tra.
  - `public static String lineLabel(CSVRecord row)` → `"Dòng " + N`, N = dòng thật (header = 1, dữ liệu đầu = 2). Xác nhận offset của `getRecordNumber()` trong commons-csv 1.11 bằng test, không đoán.

Hành vi, theo thứ tự:
1. File rỗng / không đuôi `.csv` → giữ 2 message hiện có.
2. Đọc toàn bộ bytes; bỏ BOM `EF BB BF` nếu có.
3. Decode bằng `StandardCharsets.UTF_8.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT)`; `CharacterCodingException` → message "không phải UTF-8" ở mục Lỗi font.
4. Parse bằng `CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build()`.
5. Thiếu header bắt buộc → `"File CSV thiếu cột tiêu đề bắt buộc: '<col>'"`; nếu header map chỉ có 1 cột và tên chứa `;` → nối thêm `". File có vẻ dùng dấu ';' — hãy lưu CSV với dấu phẩy (,)"`.
6. Quét từng record (mọi ô) bằng regex mojibake ở mục Lỗi font; gặp → `InvalidRequestException` với `lineLabel` + đoạn 10 ký tự quanh vị trí khớp.
7. Không có record nào → `"File CSV không chứa hàng dữ liệu hợp lệ nào"`.

- [ ] **Step 1: Viết test thất bại** (`MockMultipartFile("file","q.csv","text/csv", bytes)`, header đủ 4 cột bắt buộc):
  - `readsPlainUtf8` → 1 record, `get("title")=="Trận Bạch Đằng"`, `lineLabel==“Dòng 2”`
  - `stripsUtf8Bom` → bytes `EF BB BF` + nội dung → header `title` nhận đúng
  - `rejectsWindows1252Bytes` → nội dung encode `windows-1258`/`ISO-8859-1` có ký tự `ê` → message chứa `"CSV UTF-8"`
  - `rejectsMojibakeText` → ô `Tráº­n Báº¡ch Äáº±ng` (UTF-8 hợp lệ) → message chứa `"Dòng 2"` và `"lỗi font"`
  - `acceptsLegitVietnamese` → `NGÃ BA ĐỒNG LỘC`, `Ngô Quyền`, `Điện Biên Phủ`, `Đ` → không lỗi
  - `semicolonDelimitedFileGivesHint` → header nối bằng `;` → message chứa `"dấu phẩy"`
  - `missingQuestionContentHeaderFails` → message `"File CSV thiếu cột tiêu đề bắt buộc: 'questionContent'"`
  - `missingExplanationHeaderIsAllowed` → header không có `explanation` → đọc bình thường
- [ ] **Step 2:** `mvn -q test -Dtest=QuizCsvFileReaderTest` → FAIL.
- [ ] **Step 3: Implement** theo "Hành vi".
- [ ] **Step 4:** chạy lại → PASS.
- [ ] **Step 5: Commit** `feat(java): harden quiz CSV reading against BOM, non-UTF-8 and mojibake`

---

### Task 3: Parser theo Strategy — 1 dòng CSV → `QuestionRequest`

**Files:**
- Create: `…/service/quiz/csv/QuestionCsvParserStrategy.java` (interface)
- Create: `…/service/quiz/csv/MultipleChoiceCsvParser.java`, `FillInBlankCsvParser.java`, `MatchingCsvParser.java`
- Create: `…/service/quiz/csv/QuizCsvRowParser.java` (factory/dispatcher)
- Test: `T/service/quiz/csv/QuizCsvRowParserTest.java`

**Interfaces:**
- Consumes: `QuizCsvFileReader.lineLabel(CSVRecord)` (Task 2).
- Produces:
  - `public interface QuestionCsvParserStrategy { QuestionType supports(); JsonNode parseQuestionData(CSVRecord row); }` — lỗi ném `InvalidRequestException` **chưa** có tiền tố dòng (dispatcher thêm).
  - Mỗi impl là `@Component`, constructor nhận `ObjectMapper`; `MatchingCsvParser` có thêm constructor package-private `(ObjectMapper, java.util.Random)` cho test, constructor public `@Autowired` dùng `new Random()`.
  - `@Component public class QuizCsvRowParser { public QuizCsvRowParser(List<QuestionCsvParserStrategy> strategies); public QuestionRequest parse(CSVRecord row) }` — dựng `EnumMap<QuestionType, QuestionCsvParserStrategy>`; đọc `questionType` (rỗng → MC, `valueOf(toUpperCase())`, sai → `"questionType không hợp lệ: '<x>'. Giá trị hợp lệ: MULTIPLE_CHOICE, FILL_IN_BLANK, MATCHING"`); kiểm `questionContent` không blank; `explanation` rỗng/không có cột → `null`; trả `QuestionRequest(content, explanation, questionType, questionData)`. Mọi lỗi được bọc thành `"Dòng N: " + message`.
- Cột tùy chọn không có trong header = rỗng (`row.isMapped(col) ? row.get(col).trim() : ""`).

- [ ] **Step 1: Viết test thất bại.** Helper `CSVRecord rec(String csvWithHeader)` dùng `QuizCsvFileReader.read(new MockMultipartFile(...)).get(0)`. Dispatcher dựng bằng `new QuizCsvRowParser(List.of(new MultipleChoiceCsvParser(om), new FillInBlankCsvParser(om), new MatchingCsvParser(om, new Random(42))))`.
  - `legacyFourOptionRowParsesAsMultipleChoice`: không cột `questionType`, `correctAnswer=2` → MC, `questionData` == `{"options":[{"id":"0","text":"A"},{"id":"1","text":"B"},{"id":"2","text":"C"},{"id":"3","text":"D"}],"correctOptionIds":["2"]}`
  - `mcMultipleCorrectAndSixOptions`: `correctAnswer=0|5` → `["0","5"]`
  - `mcGapInOptionsFails`: option2 trống, option3 có → `"Dòng 2: option2 không được để trống khi option3 có giá trị"`
  - `mcCorrectIndexOutOfRangeFails`: 3 option, `correctAnswer=3` → chứa `"Dòng 2"` và `"0-2"`
  - `fillParsesAcceptedAnswersWithDefaults`: `938| năm 938 ` → `{"acceptedAnswers":["938","năm 938"],"caseSensitive":false,"trimWhitespace":true}`
  - `fillHonoursCaseSensitiveColumn`: `TRUE` → `true`; `maybe` → lỗi chứa `"caseSensitive"`
  - `fillWithOptionsFails`: chứa `"FILL_IN_BLANK"` và `"option"`
  - `matchingParsesPairs`: 3 cặp → `leftItems` `L1,L2,L3` theo input; `correctPairs[i]` trỏ đến right item có text = vế phải cặp i
  - `matchingShufflesRightItemsBeforeAssigningIds`: `Random(42)`, 4 cặp → thứ tự text `rightItems` khác input; `rightItems[k].id == "R"+(k+1)`
  - `matchingCellWithoutArrowFails`, `matchingDuplicateRightTextFails`, `matchingWithCorrectAnswerFails`
  - `unknownQuestionTypeFails` (`ESSAY`), `lowercaseQuestionTypeAccepted` (`matching`)
  - `blankQuestionContentFails`
  - `blankExplanationBecomesNull` → ô `explanation` rỗng → `QuestionRequest.explanation == null`, không lỗi
- [ ] **Step 2:** `mvn -q test -Dtest=QuizCsvRowParserTest` → FAIL.
- [ ] **Step 3: Implement.** Build DTO có sẵn (`MultipleChoiceQuestionData`, `FillInBlankQuestionData`, `MatchingQuestionData`, `QuestionOption`, `MatchingItem`, `MatchingPair`) rồi `objectMapper.valueToTree(...)`. Split `|` bằng `Pattern.quote("|")`. MATCHING tách theo `=>` lần đầu; `Collections.shuffle(rights, random)` rồi gán `R1..Rn`.
- [ ] **Step 4:** chạy lại → PASS.
- [ ] **Step 5: Commit** `feat(java): parse quiz CSV rows for all question types via strategies`

---

### Task 4: Viết lại `importQuizzesFromCsv` — validate cả nhóm trước khi lưu

**Files:**
- Modify: `…/service/quiz/QuizServiceImpl.java:512-718` (`importQuizzesFromCsv`, `saveQuizGroup`)
- Test: `T/service/quiz/QuizServiceImplImportTest.java` (file mới — `QuizServiceImplTest` mock `ObjectMapper`, import cần `ObjectMapper` thật)

**Interfaces:**
- Consumes: `QuizCsvFileReader.read/lineLabel` (Task 2), `QuizCsvRowParser.parse` (Task 3), `QuestionDataValidator.validate` (Task 1).
- Produces: chữ ký public không đổi `QuizImportResponse importQuizzesFromCsv(MultipartFile file, UUID userId)`.

`saveQuizGroup(title, rows, user)` theo thứ tự, lỗi nào cũng `InvalidRequestException`/`ResourceNotFoundException`/`DataConflictException` (vòng lặp ngoài giữ nguyên cách gom lỗi):
1. **Độ khó thống nhất:** parse `level` của **mọi** dòng bằng `parseLevel` (lỗi → tiền tố `lineLabel`). Dòng nào khác level dòng đầu → `"Dòng N: level 'HARD' khác với level 'EASY' của các câu trước trong quiz '<title>'. Độ khó được tính cho cả quiz — hãy tách câu này sang quiz khác hoặc sửa level cho giống nhau"`.
2. **Context thống nhất:** dòng nào `contextId` (trim) khác dòng đầu → `"Dòng N: contextId khác với dòng đầu của quiz '<title>'"`.
3. Context: UUID hợp lệ; `historicalContextRepository.findById` có và `getDeletedAt() == null` (technical guide §2.3), không thì `ResourceNotFoundException("HistoricalContext not found for contextId: ...")`.
4. Trùng title (`existsByTitleIgnoreCase`) như cũ.
5. `quizCsvRowParser.parse` **mọi** dòng + `questionDataValidator.validate(type, data)` (bọc lỗi validator thành `lineLabel + ": " + message`).
6. Chỉ khi 1-5 qua hết: `quizRepository.save(quiz)` rồi lưu từng câu bằng `buildQuestion`.

Thêm field `QuizCsvFileReader quizCsvFileReader`, `QuizCsvRowParser quizCsvRowParser`; xóa code đọc file/parse option cũ; cập nhật Javadoc.

- [ ] **Step 1: Viết test thất bại** (`@ExtendWith(MockitoExtension.class)`, mock repository + `GamificationService`; `QuizServiceImpl` dựng thủ công với `ObjectMapper` thật, validator thật, reader thật, parser thật với `Random(1)`; context mock trả `HistoricalContext` có `deletedAt=null`):
  - `importsAllThreeTypesInOneQuiz`: file mẫu (Task 5) → `successCount==1`; captor `questionRepository.save` nhận 3 `Question` MC/FILL/MATCHING, `questionData` đọc lại đúng DTO.
  - `legacyCsvStillImports`: header cũ, 1 dòng có `explanation` rỗng → 1 câu MC, `correctOptionIds==["0"]`, `explanation == null`.
  - `mixedLevelInOneQuizIsRejected`: dòng 2 `EASY`, dòng 3 `HARD` cùng title → `skippedCount==1`, `errors[0]` chứa `"Dòng 3"`, `"HARD"`, `"EASY"`; `verify(quizRepository, never()).save(any())`.
  - `invalidLevelInLaterRowIsRejected`: dòng 3 `abc` → lỗi chứa `"Dòng 3"` và `"EASY, MEDIUM, HARD"`.
  - `invalidRowSkipsWholeQuizWithoutSaving`: dòng 3 FILL có `correctAnswer` rỗng → skipped, lỗi chứa `"Dòng 3"`; `never()` save quiz lẫn question.
  - `inconsistentContextIdSkipsQuiz`: lỗi chứa `"Dòng 3"` và `"contextId"`.
  - `softDeletedContextIsRejected`: context có `deletedAt` → skipped, lỗi chứa `"HistoricalContext not found"`.
  - `oneBadQuizDoesNotBlockOthers`: title A lỗi, title B đúng → `successCount==1`, `skippedCount==1`.
- [ ] **Step 2:** `mvn -q test -Dtest=QuizServiceImplImportTest` → FAIL.
- [ ] **Step 3: Implement** theo các bước 1-6.
- [ ] **Step 4:** `mvn -q test -Dtest='Quiz*Test,QuestionDataValidatorTest'` → PASS.
- [ ] **Step 5: Commit** `feat(java): import mixed question types per quiz and reject mixed difficulty`

---

### Task 5: Endpoint tải file mẫu (UTF-8 BOM) + Swagger

**Files:**
- Create: `…/service/quiz/csv/QuizCsvTemplateBuilder.java`
- Modify: `…/controller/quiz/StaffQuizController.java` (thêm endpoint, sửa description import ~L180-188)
- Test: `T/service/quiz/csv/QuizCsvTemplateBuilderTest.java`

**Interfaces:**
- Produces:
  - `@Component public class QuizCsvTemplateBuilder { public byte[] build() }` — bytes = `EF BB BF` + CSV UTF-8, line ending `\r\n`, header theo thứ tự `title,contextId,level,questionType,questionContent,option1,option2,option3,option4,option5,option6,correctAnswer,caseSensitive,trimWhitespace,explanation` và 3 dòng ví dụ (MC, FILL, MATCHING) cùng 1 title, `contextId` = `00000000-0000-0000-0000-000000000000` (placeholder, ghi rõ trong docs phải thay).
  - `GET /api/v1/staff/quizzes/import/template` → `200`, `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="quiz-import-template.csv"`, body = `build()`. Trả `ResponseEntity<byte[]>` trực tiếp (không bọc `ApiResponse`, vì là file).

- [ ] **Step 1: Viết test thất bại:**
  - `templateStartsWithUtf8Bom` → 3 byte đầu `EF BB BF`
  - `templateRoundTripsThroughReaderAndParser` → đưa bytes vào `QuizCsvFileReader.read` + `QuizCsvRowParser.parse` → 3 `QuestionRequest` loại MC/FILL/MATCHING, không lỗi (đảm bảo file mẫu luôn hợp lệ với code import)
  - `templateContainsVietnameseIntact` → decode lại chứa `"Trận Bạch Đằng"`
- [ ] **Step 2:** `mvn -q test -Dtest=QuizCsvTemplateBuilderTest` → FAIL.
- [ ] **Step 3: Implement** builder + endpoint. Cập nhật `@Operation.description` của import: `"Required columns: title, contextId, level, questionContent. Optional: questionType (MULTIPLE_CHOICE | FILL_IN_BLANK | MATCHING, default MULTIPLE_CHOICE), option1..option6, correctAnswer, explanation, caseSensitive, trimWhitespace. File must be UTF-8 comma-separated; download GET /import/template. All rows of one title must share contextId and level."`
- [ ] **Step 4:** `mvn -q test -Dtest=QuizCsvTemplateBuilderTest` → PASS; `mvn -q -DskipTests compile` → OK.
- [ ] **Step 5: Commit** `feat(java): add UTF-8 BOM quiz CSV template endpoint`

---

### Task 6: Tài liệu + kiểm tra end-to-end

**Files:**
- Create: `docs/services/history-talk-backend/quiz-csv-import-format.md` (root repo)

- [ ] **Step 1:** Viết doc: bảng Format CSV, ví dụ, shape JSON DB, quy tắc "cùng title = cùng contextId + level", `explanation` tùy chọn (khuyến nghị điền theo business spec §2.5), `|` không dùng trong đáp án, MC `correctAnswer` 0-based, "1 dòng lỗi → cả quiz bị bỏ qua", và hướng dẫn Excel: tải file mẫu từ `/import/template`; khi lưu chọn **File > Save As > CSV UTF-8 (Comma delimited)**; nếu đã thấy chữ kiểu `Tráº­n` thì **đừng Save** — mở lại bằng *Data > From Text/CSV > File Origin: 65001 Unicode (UTF-8)*.
- [ ] **Step 2: Kiểm tra thực tế:** `mvn spring-boot:run`; Swagger `http://localhost:8080/Historical-tell/api/v1/swagger-ui`, đăng nhập CONTENT_ADMIN:
  - tải `/staff/quizzes/import/template`, mở bằng Excel → tiếng Việt hiển thị đúng; thay `contextId` bằng một context thật chưa xóa, Save → upload `/staff/quizzes/import` → `successCount=1`.
  - sửa 1 dòng thành `HARD` → upload lại (đổi title) → `skippedCount=1`, lỗi nêu dòng + 2 level.
  - publish quiz, `POST /quizzes/{id}/start` → MC có `options`, FILL có `questionData=null`, MATCHING có `leftItems/rightItems`, không có `correctPairs`; submit đúng cả 3 → `score=3`.
- [ ] **Step 3: Commit** `docs: document quiz CSV import format and Excel UTF-8 workflow`
