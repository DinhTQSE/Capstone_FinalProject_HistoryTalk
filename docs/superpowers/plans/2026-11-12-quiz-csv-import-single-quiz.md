# Quiz CSV Import: One Quiz per Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change `POST /api/v1/staff/quizzes/import` so the UI sends `contextId` + `title` as form fields and the CSV holds only question rows. Each upload creates exactly one draft quiz, all-or-nothing, and `#` lines in the CSV are treated as guidance comments.

**Architecture:** The controller takes three multipart parts (`file`, `contextId`, `title`). `QuizServiceImpl.importQuizFromCsv` validates title and context first, then reads the file. It parses **every** row and collects all row errors before saving anything, and returns the created `QuizStaffResponse`. `QuizCsvFileReader` gets a `#` comment marker, and its required headers become `level` + `questionContent`. The per-type row parsers (`MultipleChoiceCsvParser`, `FillInBlankCsvParser`, `MatchingCsvParser`, `QuizCsvRowParser`) and `QuestionDataValidator` are **unchanged**.

**Tech Stack:** Spring Boot 3.2, Java 21, Apache Commons CSV, JUnit 5 + Mockito.

**Spec:** User request (2026-11-12). On the UI the user picks a context, types the quiz title, then imports a CSV with the question content for that quiz. Update the sample CSV `docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv` to the new format, with guidance/explanation/notes lines inside the file. The design agreed in chat:
- Change the existing endpoint in place (breaking change; the old multi-quiz mode is removed).
- The CSV no longer has `title`/`contextId`. If an old file still has them, they are ignored.
- Validate all rows first; if any row is wrong, return 400 listing every bad row and save nothing.
- The response is the created quiz.

Project rules: `history-talk-backend-Java/docs/historytalk-overall-business-spec.md`, `historytalk-overall-technical-guide.md`.

## Global Constraints

- Java service root: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`. Paths below are relative to it unless they start with `docs/`.
- Endpoint path and roles stay as they are: `POST /api/v1/staff/quizzes/import`, class-level `@PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN','TEACHER','SCHOOL_ADMIN')")`.
- Imported quiz is always a draft (`isPublished = false`).
- `correctAnswer` for MULTIPLE_CHOICE stays **0-based**. Not changed in this plan.
- Quiz title max length `QUIZ_TITLE_MAX_LENGTH = 255` (existing constant in `QuizServiceImpl`). Title uniqueness stays `quizRepository.existsByTitleIgnoreCase`.
- User-facing error messages stay in Vietnamese, matching existing style.
- No business logic in the controller (TECH-GUIDE §2.1). Soft-deleted contexts (`deletedAt != null`) are treated as not found (TECH-GUIDE §2.3).
- No `@Version`, `@DynamicUpdate` or column-only update queries.
- New CSV column order (exact):
  `level,questionType,questionContent,option1,option2,option3,option4,option5,option6,correctAnswer,caseSensitive,trimWhitespace,explanation`
- The guidance comment block is the `#` lines at the top of `docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv` (already written, see "Done before execution"). The template builder (Task 3) emits the same lines verbatim.

## Done before execution

- `docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv` is rewritten. It is UTF-8 with BOM and has:
  - 33 `#` guidance lines;
  - the new 13-column header;
  - 6 example rows for one quiz (Điện Biên Phủ): 2 MULTIPLE_CHOICE (one with multiple answers `0|1|3`), 1 with blank `questionType` (defaults to MC), 2 FILL_IN_BLANK, and 1 MATCHING.
  - The guidance lines contain no double quotes, so an Excel re-save never wraps them in quotes (a wrapped line would no longer start with `#`).
- This file only imports successfully once Tasks 1–2 land, because the current reader has no comment marker.

## Review Focus

1. **File re-saved by Excel**: Excel splits a comment line at its commas and writes it back as `# text, more text,,,,,`. It must still be skipped, because the line still starts with `#`. Covered in Task 1.
2. **Old-format file** (still has `title`,`contextId` columns with arbitrary values): it must import into the form's title/context and ignore those columns. Covered in Task 2.
3. **Several bad rows**: the user must see every bad row in one response, labelled by question number, not just the first. Covered in Task 2.
4. **Bad form fields with a valid file**: a blank or too-long title, a non-UUID contextId, a deleted context or a duplicate title must fail **before** the file is parsed, and nothing is saved. Covered in Task 2.
5. **File with only comments + header, no data rows**: return 400 "không chứa hàng dữ liệu hợp lệ nào" and do not create an empty quiz. Covered in Task 1.

---

### Task 1: CSV reader: comment lines, new required headers, question-number labels

**Files:**
- Modify: `src/main/java/com/historytalk/service/quiz/csv/QuizCsvFileReader.java`
- Test: `src/test/java/com/historytalk/service/quiz/csv/QuizCsvFileReaderTest.java`

**Interfaces:**
- Produces:
  - `QuizCsvFileReader.REQUIRED_HEADERS == List.of("level", "questionContent")`
  - `FORMAT` adds `.setCommentMarker('#')`
  - `QuizCsvFileReader.lineLabel(CSVRecord row)` returns `"Câu hỏi " + row.getRecordNumber()`. The first data row is `"Câu hỏi 1"`. Comment and header lines are not counted, because physical line numbers no longer match once `#` lines exist.

- [ ] **Step 1: Write/adjust tests** in `QuizCsvFileReaderTest`:
  - `commentLinesAreSkippedBeforeHeaderAndBetweenRows`: the input is `"# guide\n#\nlevel,questionContent\nEASY,Câu 1\n# note\nMEDIUM,Câu 2\n"`. It returns 2 records, and `records.get(1).get("questionContent")` equals `"Câu 2"`.
  - `excelResavedCommentWithCommasIsSkipped`: input `"# Lưu ý, đọc kỹ,,,,\nlevel,questionContent\nEASY,Câu 1\n"` returns 1 record.
  - `commentsAndHeaderOnlyFileFails`: input `"# guide\nlevel,questionContent\n"` throws `InvalidRequestException` containing `"không chứa hàng dữ liệu hợp lệ nào"`.
  - `fileWithoutTitleAndContextIdColumnsIsAccepted`: input `"level,questionContent\nEASY,Câu 1\n"` returns 1 record.
  - `missingLevelHeaderFails`: input `"questionContent\nCâu 1\n"` throws, with a message containing `"'level'"`.
  - Rename `lineLabelCountsFromHeader` to `lineLabelIsQuestionNumber`. The first data record's label must be `"Câu hỏi 1"`, and the label for the record after a comment line must be `"Câu hỏi 2"`.
  - Update any existing test inputs that relied on `title`/`contextId` being required. Keep their intent.
- [ ] **Step 2: Run** `mvn test -Dtest=QuizCsvFileReaderTest`. Expected: the new tests FAIL. Comment lines are parsed as data or break the header, and the label is still `"Dòng 2"`.
- [ ] **Step 3: Implement** the three changes listed under Produces. Update the class javadoc to mention `#` comments.
- [ ] **Step 4: Run** `mvn test -Dtest=QuizCsvFileReaderTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(java): quiz csv reader supports # comments and question-only headers`

### Task 2: Service + controller: one quiz per upload, all-or-nothing

**Files:**
- Modify: `src/main/java/com/historytalk/service/quiz/QuizService.java` (line ~58)
- Modify: `src/main/java/com/historytalk/service/quiz/QuizServiceImpl.java` (`importQuizzesFromCsv` ~543–605, `saveQuizGroup` ~615–675)
- Modify: `src/main/java/com/historytalk/controller/quiz/StaffQuizController.java` (~175–205)
- Delete: `src/main/java/com/historytalk/dto/quiz/QuizImportResponse.java` (no other users after this task; confirm with `grep -rn QuizImportResponse src`)
- Test: `src/test/java/com/historytalk/service/quiz/QuizServiceImplImportTest.java` (rewrite)

**Interfaces:**
- Consumes: Task 1's `REQUIRED_HEADERS`, comment marker and `lineLabel` ("Câu hỏi N").
- Produces:
  - `QuizService`: `QuizStaffResponse importQuizFromCsv(MultipartFile file, String contextId, String title, UUID userId)` (replaces `importQuizzesFromCsv`)
  - Controller: `importQuiz(@RequestPart("file") MultipartFile file, @RequestParam("contextId") String contextId, @RequestParam("title") String title)` → `ResponseEntity<ApiResponse<QuizStaffResponse>>`, message `"Quiz imported successfully"`

**Service order of checks (fail fast, nothing saved on any failure):**
1. `title` trimmed. If blank: `InvalidRequestException("Tên quiz không được để trống")`. If longer than 255: the existing message format `"title dài N ký tự, tối đa 255 ký tự"`.
2. `contextId`: if not a UUID, `InvalidRequestException("contextId không phải là UUID hợp lệ: '<value>'")`. If missing or soft-deleted, `ResourceNotFoundException("Không tìm thấy bối cảnh lịch sử với ID: <value>")`.
3. Duplicate title (`existsByTitleIgnoreCase`): `DataConflictException("Quiz với tiêu đề '<title>' đã tồn tại")`.
4. `quizCsvFileReader.read(file)`. Its 400s pass through.
5. For **every** record, run `quizCsvRowParser.parse(row)` + `questionDataValidator.validate(...)`. Catch `InvalidRequestException` per row and collect `lineLabel(row) + ": " + message`. If any errors were collected, throw `InvalidRequestException("File có " + n + " câu hỏi lỗi, chưa lưu câu nào:\n" + String.join("\n", errors))`.
6. Save the quiz (draft, `createdBy` = user) and link the questions in file order via the existing `buildQuestion` + `linkQuestion`. Return `mapToStaffResponse(quiz)`.

Any `title`/`contextId` cells in the file are never read.

- [ ] **Step 1: Rewrite `QuizServiceImplImportTest`.** Keep the existing mock setup and the `row(...)` helper idea. Use the new header without title/contextId, and add a helper `importCsv(String csv)` that calls `importQuizFromCsv(file, contextId.toString(), "Điện Biên Phủ", userId)`. Tests:
  - `importsAllThreeTypesIntoOneQuiz`: MC + FILL_IN_BLANK + MATCHING rows give a response with title `"Điện Biên Phủ"`, contextId = the form context, 3 questions in file order, and `isPublished` false. `quizRepository.save` is called exactly once.
  - `guidanceCommentLinesAreIgnored`: `#` lines before the header and between rows still give 2 questions.
  - `legacyTitleAndContextIdColumnsAreIgnored`: the CSV has the old header `title,contextId,level,...`, with title `"Khác"` and a random UUID in those cells. The saved quiz still has the form title and context, and `historicalContextRepository.findById` is called only with the form contextId.
  - `reportsEveryInvalidRowAndSavesNothing`: rows 2 and 4 have `correctAnswer` `9` (out of range). The call throws `InvalidRequestException`, whose message contains `"2 câu hỏi lỗi"`, `"Câu hỏi 2:"` and `"Câu hỏi 4:"`. `quizRepository.save` is never called and `questionRepository.save` is never called.
  - `blankTitleIsRejectedBeforeReadingFile`: title `"   "` throws `InvalidRequestException` with `"Tên quiz không được để trống"`. `historicalContextRepository.findById` is never called.
  - `titleLongerThan255IsRejected`: `"Q".repeat(256)` gives a message containing `"255"`, and nothing is saved.
  - `invalidContextIdFormatIsRejected`: `"abc"` throws `InvalidRequestException`.
  - `softDeletedContextIsRejected`: a context with `deletedAt` set throws `ResourceNotFoundException`.
  - `duplicateTitleIsRejected`: `existsByTitleIgnoreCase` returns true, so `DataConflictException` is thrown and nothing is saved.
  - `mixedLevelsKeepPerQuestionDifficulty`: carry over the existing test's assertions.
- [ ] **Step 2: Run** `mvn test -Dtest=QuizServiceImplImportTest`. Expected: FAIL to compile (`importQuizFromCsv` is undefined).
- [ ] **Step 3: Implement** the service per the order above. Remove the title-grouping loop, `saveQuizGroup`'s contextId-consistency loop, and `QuizImportResponse`. Update the interface javadoc.
- [ ] **Step 4: Update the controller.** Change the signature to the one in Produces. The Swagger `@Operation` description must state:
  - the multipart parts `file`, `contextId`, `title`;
  - one quiz per upload, created as a draft;
  - CSV columns = the exact column list from Global Constraints;
  - `#` lines are comments;
  - any bad row gives a 400 listing all bad rows, and nothing is saved;
  - template: `GET /import/template`.
- [ ] **Step 5: Run** `mvn -q -DskipTests compile` then `mvn test -Dtest='QuizServiceImplImportTest,QuizServiceImplTest'`. Expected: PASS. Then run `grep -rn "QuizImportResponse\|importQuizzesFromCsv" src`. Expected: no output.
- [ ] **Step 6: Commit** `feat(java)!: quiz csv import creates one quiz from form contextId and title`

### Task 3: Template download matches the new format

**Files:**
- Modify: `src/main/java/com/historytalk/service/quiz/csv/QuizCsvTemplateBuilder.java`
- Test: `src/test/java/com/historytalk/service/quiz/csv/QuizCsvTemplateBuilderTest.java`

**Interfaces:**
- Produces: `byte[] build()`. Output is UTF-8 BOM, then the guidance `#` lines (verbatim from the sample file), then the new 13-column header, then 3 example rows (MC / FILL_IN_BLANK / MATCHING; reuse today's example content without `SAMPLE_TITLE`/`SAMPLE_CONTEXT_ID`).

- [ ] **Step 1: Update tests**:
  - Keep `templateStartsWithUtf8Bom` and `templateContainsVietnameseIntact`.
  - Add `templateStartsWithGuidanceComments`: after the BOM, the text starts with `"# "`, and it contains `"ĐẾM TỪ 0"`.
  - Add `templateHeaderHasNoTitleOrContextId`: the first non-`#` line equals the exact 13-column header.
  - Keep `templateRoundTripsThroughReaderAndParser`: the template read through `QuizCsvFileReader` gives 3 records, and each parses through `QuizCsvRowParser` without error.
- [ ] **Step 2: Run** `mvn test -Dtest=QuizCsvTemplateBuilderTest`. Expected: the new tests FAIL.
- [ ] **Step 3: Implement.** Write the comment lines with `CSVPrinter.printComment` (set `.setCommentMarker('#')` on the printer format), or write them raw before the printer. Remove `SAMPLE_TITLE` / `SAMPLE_CONTEXT_ID`. Update the controller's template `@Operation` description: drop "Replace contextId before importing" and say that the `#` lines are guidance.
- [ ] **Step 4: Run** `mvn test -Dtest=QuizCsvTemplateBuilderTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(java): quiz csv template has guidance comments and question-only columns`

### Task 4: FE contract + business flow docs, full validation

**Files:**
- Modify: `docs/sprints/sprint_6/fe_contracts/quiz/quiz_fe_contracts.md` (section `POST /staff/quizzes/import`, ~line 385)
- Modify: `docs/sprints/sprint_6/fe_contracts/quiz/quiz_import_template.csv` (replace its content with a byte-identical copy of `docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv`)
- Modify: `docs/sprints/sprint_6/business_flows/quiz/quiz_module_flows.md` (import sequence, ~lines 90–92)

- [ ] **Step 1: FE contract section.** Document:
  - **Request:** multipart parts `file` (.csv), `contextId` (UUID string from the context dropdown) and `title` (string, ≤255).
  - **Response 200:** `QuizStaffResponse` (the created draft quiz).
  - **Errors:**
    - 400 for a blank or too-long title, a bad UUID, a bad file, or row errors. The message lists every bad row as `Câu hỏi N: ...` separated by `\n`, so the FE should render it with line breaks.
    - 404 when the context is not found or deleted.
    - 409 for a duplicate title.
  - **Notes:**
    - The CSV columns (exact list).
    - `#` lines are comments.
    - MC `correctAnswer` is 0-based.
    - Breaking change: the old `totalQuizzesAttempted/successCount/skippedCount/errors/imported` response is removed.
- [ ] **Step 2: Copy the sample CSV** over `quiz_import_template.csv`. Run `cmp` on the two files. Expected: no output.
- [ ] **Step 3: Update the mermaid sequence.** The request line becomes `POST /api/v1/staff/quizzes/import (multipart: file, contextId, title)`. The note becomes: `Validate title/context → parse all rows → any error: 400 list, nothing saved → save 1 draft quiz`. The response becomes `QuizStaffResponse`.
- [ ] **Step 4: Full suite.** Run `mvn test`. Expected: the only failures are the 18 known pre-existing ones (TeacherAssignmentServiceImplTest, ClassroomStudentServiceImplTest, AnalyticsDashboardServiceImplTest, DocumentFileServiceImplTest, SupabaseDocumentStorageServiceTest, PasswordResetEmailServiceImplTest). No quiz test fails.
- [ ] **Step 5: Manual check (if app + DB available).** In Swagger, call `POST /staff/quizzes/import` with `docs/sprints/sprint_6/business_flows/quiz/quiz-import-sample.csv`, a real contextId and a new title. Expected: 200, and the quiz has 6 questions. Then re-upload with the same title. Expected: 409. Report this as not run if no app/DB is available.
- [ ] **Step 6: Commit** `docs: update quiz csv import contract, template and flow for single-quiz import`

## Out of scope (noted, not changed)

- TEACHER/SCHOOL_ADMIN can import or create a quiz in a context that doesn't belong to their school. Neither `createQuiz` nor the import checks school ownership. This existed before; it needs a separate decision.
- `createQuiz` doesn't reject soft-deleted contexts (the import does).
- MC `correctAnswer` stays 0-based. Switching to 1-based or letters would break existing CSVs.
- `existsByTitleIgnoreCase` is global, not per context or per school (existing behaviour).
