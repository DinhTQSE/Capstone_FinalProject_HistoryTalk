# Character Portrait (Lip-sync) Settings API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm `GET` / `PUT /api/v1/characters/{characterId}/media/portrait-settings` để FE lưu và đọc thiết lập chân dung nhép môi (JSON object) của nhân vật.

**Architecture:** Lưu JSON vào cột `jsonb` mới `portrait_settings` trên bảng `historical_schema."character"` (Flyway), theo đúng tiền lệ `HistoricalContext.battleMap` (`@JdbcTypeCode(SqlTypes.JSON) String`). Một service mới `CharacterPortraitSettingsService` (layer `service/character/`) kiểm tra body là JSON object ≤ 8KB rồi lưu nguyên. Quyền đọc dùng lại rule hiển thị của `GET /characters/{id}`; quyền ghi dùng lại rule `assertCanManageMedia` của upload media (được mở thành public method trên `MediaService`) — không nhân bản rule.

**Tech Stack:** Spring Boot 3.2 / Java 21, Spring Data JPA (Hibernate 6), Flyway, Jackson, JUnit 5 + Mockito + standalone MockMvc.

**Spec:** Yêu cầu FE (trong conversation, tóm tắt ở Global Constraints) + `history-talk-backend-Java/docs/historytalk-overall-business-spec.md` + `history-talk-backend-Java/docs/historytalk-overall-technical-guide.md`.

**Java root (viết tắt `JAVA/`):** `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`

## Decision: DB column thay vì Supabase file

FE đề xuất `CHARACTER/{id}/portrait.json` trên Supabase Storage "để khỏi migrate DB", và chấp nhận cột JSON. Plan chọn **cột `jsonb`** vì:
- Project đã có Flyway (`spring.flyway.enabled=true`), migrate chỉ là 1 file SQL — giống `V20261106143000__add_character_pronouns.sql`.
- Đã có tiền lệ y hệt: `HistoricalContext.battle_map jsonb`.
- `GET` public sẽ được gọi bởi mọi người dùng ở màn chat; đọc DB tránh 1 round-trip Supabase mỗi lần và tránh phải phân biệt "file chưa tồn tại" (Supabase trả 400/404) với lỗi mạng thật.
- Ghi trong transaction, không có trạng thái nửa vời giữa DB và Storage; soft-delete/trash của nhân vật tự động bao luôn settings.

Lưu ý: `jsonb` chuẩn hoá thứ tự key/khoảng trắng. FE không phụ thuộc thứ tự key nên không ảnh hưởng.

## Global Constraints

- `GET` không cần đăng nhập — đã được `SecurityConfig` cho phép qua `.requestMatchers(HttpMethod.GET, "/api/v1/characters/**").permitAll()`. **Không sửa `SecurityConfig`** (shared-risk file theo AGENTS.md).
- `GET`: nhân vật chưa lưu → `success: true`, `data: null` (JSON phải có `"data":null`, không được lỗi). Không có nhân vật → 404.
- `PUT` roles: `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")`. TEACHER chỉ sửa nhân vật local do mình tạo (cùng rule upload media). Lưu ý: `upload-direct` hiện chỉ có `CONTENT_ADMIN, TEACHER` — endpoint mới phải có thêm `SYSTEM_ADMIN` theo spec.
- `PUT`: `consumes = application/json`; body phải là JSON object, tối đa **8KB = 8192 bytes** (đo trên raw bytes của body). Không phải object hoặc > 8192 bytes → **400**.
- BE không validate từng trường (`mode`, `points`, `imageUrl`...) — chỉ kiểm tra là JSON object rồi lưu nguyên.
- Response thành công dùng `ApiResponse.success(data, message)` → `{ success, data, message, timestamp }`. Lỗi đi qua `GlobalExceptionHandler` có sẵn (`InvalidRequestException`→400, `ResourceNotFoundException`→404, `UnauthorizedException`→401, `ForbiddenException`/`AccessDeniedException`→403, `IllegalArgumentException`→400).
- Soft-delete theo technical guide §2.3: lọc thủ công `deletedAt` ở service, không dùng `@Where`/`@SQLDelete`.
- Business logic không đặt trong controller. Test đặt dưới `JAVA/src/test/java/...`, tên `*Test.java`.
- Commit theo Conventional Commits, ví dụ `feat(java): ...`.

## Review Focus

1. **Body đúng 8192 bytes được chấp nhận, 8193 bytes bị 400** — và giới hạn đo theo bytes UTF-8, không theo số ký tự (chuỗi tiếng Việt nhiều byte). → test `save_acceptsExactly8192Bytes`, `save_rejects8193Bytes` (Task 1).
2. **JSON lỗi cú pháp / có rác phía sau (`{"a":1} xyz`) / `null` / mảng / chuỗi** phải trả 400, không phải 500 và không lưu một phần. → test `save_rejectsNonObjectOrMalformedJson` (Task 1).
3. **Nhân vật bị ẩn với người gọi** (soft-deleted, chưa publish, hoặc local của trường khác khi gọi ẩn danh) → `GET` trả 404 giống `GET /characters/{id}`, không lộ settings. → test `get_hiddenCharacter_throwsNotFound` (Task 1).
4. **TEACHER sửa nhân vật platform (school = null) hoặc nhân vật local của giáo viên khác** → 403, settings không bị ghi. → test `save_teacherNotOwner_throwsUnauthorized_andDoesNotSave` (Task 1).
5. **`characterId` không phải UUID** → 400 (qua `IllegalArgumentException` handler), không phải 500. → test `get_invalidUuid_returns400` (Task 2).

---

### Task 1: Persistence + `CharacterPortraitSettingsService`

**Files:**
- Create: `JAVA/src/main/resources/db/migration/V20261111100000__add_character_portrait_settings.sql`
- Modify: `JAVA/src/main/java/com/historytalk/entity/character/Character.java` (thêm field cạnh `modelUrl`)
- Modify: `JAVA/src/main/java/com/historytalk/service/media/MediaService.java` (thêm method `assertCanManageMedia`)
- Modify: `JAVA/src/main/java/com/historytalk/service/document/DocumentMediaServiceImpl.java:197` (`private` → `public @Override`, giữ nguyên thân)
- Modify: `JAVA/src/main/java/com/historytalk/service/character/CharacterService.java` + `CharacterServiceImpl.java:76-108` (tách phần tìm + kiểm tra hiển thị ra `findVisibleCharacter`)
- Create: `JAVA/src/main/java/com/historytalk/service/character/CharacterPortraitSettingsService.java`
- Create: `JAVA/src/main/java/com/historytalk/service/character/CharacterPortraitSettingsServiceImpl.java`
- Test: `JAVA/src/test/java/com/historytalk/service/character/CharacterPortraitSettingsServiceImplTest.java`

**Interfaces:**
- Consumes: `CharacterRepository.findById(UUID)`, `ObjectMapper` bean, `EntityType.CHARACTER`.
- Produces:
  - Entity: `Character.portraitSettings : String` — `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "portrait_settings", columnDefinition = "jsonb")`.
  - `MediaService.assertCanManageMedia(EntityType entityType, UUID entityId, String userId, String userRole) : void` — giữ nguyên thứ tự tham số của method private hiện tại.
  - `CharacterService.findVisibleCharacter(String characterId, String role) : Character` — ném `ResourceNotFoundException` đúng như `getCharacterById` hiện tại; `getCharacterById` trở thành `mapToResponse(findVisibleCharacter(characterId, role))`.
  - `CharacterPortraitSettingsService`:
    ```java
    int MAX_SETTINGS_BYTES = 8 * 1024;
    JsonNode getPortraitSettings(String characterId, String role);          // null khi chưa lưu
    JsonNode savePortraitSettings(String characterId, byte[] rawBody, String userId, String userRole);
    ```

- [ ] **Step 1: Viết migration + entity field**

```sql
-- =============================================================================
-- Migration: Lip-sync portrait settings of each character
-- Date: 2026-11-11
-- Opaque JSON object saved by the FE portrait editor (mode, mouthOpen, zoom,
-- points, imageUrl...). NULL means the character has no saved settings.
-- =============================================================================

ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS portrait_settings JSONB;
```

- [ ] **Step 2: Viết test thất bại** — `CharacterPortraitSettingsServiceImplTest`, style giống `DocumentMediaServiceImplTest` (`@ExtendWith(MockitoExtension.class)`, `@MockitoSettings(strictness = LENIENT)`), mock `CharacterRepository`, `CharacterService`, `MediaService`; dùng `ObjectMapper` thật (khởi tạo tay rồi truyền vào constructor, không `@InjectMocks` cho nó).

```java
@Test void get_noSavedSettings_returnsNull()
    // findVisibleCharacter trả character có portraitSettings = null
    assertThat(service.getPortraitSettings(id, null)).isNull();

@Test void get_savedSettings_returnsJsonObject()
    // portraitSettings = "{\"mode\":\"AUTO\",\"zoom\":1}"
    JsonNode n = service.getPortraitSettings(id, null);
    assertThat(n.isObject()).isTrue();
    assertThat(n.get("mode").asText()).isEqualTo("AUTO");

@Test void get_hiddenCharacter_throwsNotFound()
    // characterService.findVisibleCharacter ném ResourceNotFoundException
    assertThatThrownBy(() -> service.getPortraitSettings(id, null)).isInstanceOf(ResourceNotFoundException.class);

@Test void save_validObject_persistsAndReturnsSameJson()
    // body = ví dụ của FE (mode AUTO, mouthOpen, zoom, points{eyeLeft..chin}, imageUrl)
    JsonNode out = service.savePortraitSettings(id, body, userId, "CONTENT_ADMIN");
    verify(mediaService).assertCanManageMedia(EntityType.CHARACTER, uuid, userId, "CONTENT_ADMIN");
    // ArgumentCaptor<Character> trên characterRepository.save:
    assertThat(objectMapper.readTree(captured.getPortraitSettings())).isEqualTo(objectMapper.readTree(body));
    assertThat(out).isEqualTo(objectMapper.readTree(body));

@Test void save_acceptsExactly8192Bytes()
    // object {"pad":"<chuỗi>"} được độn sao cho body.length == 8192 → không ném

@Test void save_rejects8193Bytes()
    // body.length == 8193, dùng ký tự nhiều byte ("ệ") để số ký tự < 8193
    assertThatThrownBy(...).isInstanceOf(InvalidRequestException.class);
    verify(characterRepository, never()).save(any());

@Test void save_rejectsNonObjectOrMalformedJson()   // @ParameterizedTest @ValueSource
    // "[1,2]", "\"text\"", "null", "42", "{\"a\":1} xyz", "{\"a\":", ""
    → InvalidRequestException, never save

@Test void save_teacherNotOwner_throwsUnauthorized_andDoesNotSave()
    // doThrow(new UnauthorizedException("...")).when(mediaService).assertCanManageMedia(...)
    → UnauthorizedException, never save

@Test void save_softDeletedCharacter_throwsNotFound()
    // characterRepository.findById trả character có deletedAt != null
    → ResourceNotFoundException, never save
```

- [ ] **Step 3: Chạy test, xác nhận FAIL**

Run (từ `JAVA/`): `mvn -q test -Dtest=CharacterPortraitSettingsServiceImplTest`
Expected: compile error — `CharacterPortraitSettingsServiceImpl` / `findVisibleCharacter` / `assertCanManageMedia` chưa tồn tại.

- [ ] **Step 4: Implement**

- `MediaService`: khai báo `assertCanManageMedia(...)` kèm Javadoc "Platform roles manage any media; TEACHER only local content they created; SCHOOL_ADMIN any local content of their school". `DocumentMediaServiceImpl`: đổi `private` → `public` + `@Override`, không đổi logic.
- `CharacterServiceImpl.findVisibleCharacter`: chuyển nguyên khối `findById` + 3 kiểm tra (`isPublished`, `deletedAt`, school isolation) từ `getCharacterById` vào; `@Transactional(readOnly = true)`.
- `CharacterPortraitSettingsServiceImpl` (`@Service @RequiredArgsConstructor @Slf4j`), thứ tự xử lý của `savePortraitSettings` (`@Transactional`):
  1. `rawBody == null || rawBody.length == 0` hoặc `rawBody.length > MAX_SETTINGS_BYTES` → `InvalidRequestException("Thiết lập chân dung phải là JSON object và không vượt quá 8KB")`.
  2. Parse bằng `objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(rawBody)`; `IOException` hoặc `!node.isObject()` → cùng `InvalidRequestException`.
  3. `UUID uuid = UUID.fromString(characterId)`; `mediaService.assertCanManageMedia(EntityType.CHARACTER, uuid, userId, userRole)`.
  4. `characterRepository.findById(uuid)` lọc `deletedAt == null`, không có → `ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + characterId)`.
  5. `character.setPortraitSettings(objectMapper.writeValueAsString(node))`, `characterRepository.save(character)`, trả `node` — cùng cơ chế load-sửa-save như các API sửa Character hiện có.
- `getPortraitSettings` (`@Transactional(readOnly = true)`): `findVisibleCharacter` → cột null thì trả `null`, ngược lại `objectMapper.readTree(...)` (lỗi parse dữ liệu cũ → `SystemException`).

- [ ] **Step 5: Chạy test, xác nhận PASS (gồm cả test cũ bị ảnh hưởng)**

Run: `mvn -q test -Dtest='CharacterPortraitSettingsServiceImplTest,DocumentMediaServiceImplTest,CharacterService*Test'`
Expected: tất cả PASS (test `getCharacterById` hiện có chứng minh refactor không đổi hành vi).

- [ ] **Step 6: Commit**

```bash
git add <các file trên>
git commit -m "feat(java): persist character portrait settings as jsonb"
```

---

### Task 2: Endpoints trên `CharacterMediaController`

**Files:**
- Modify: `JAVA/src/main/java/com/historytalk/controller/character/CharacterMediaController.java`
- Test: `JAVA/src/test/java/com/historytalk/controller/character/CharacterMediaControllerPortraitSettingsTest.java`

**Interfaces:**
- Consumes: `CharacterPortraitSettingsService.getPortraitSettings(String, String)`, `savePortraitSettings(String, byte[], String, String)` từ Task 1; `SecurityUtils.getUserId()`, `SecurityUtils.getRoleName()`.
- Produces:
  ```java
  @GetMapping("/portrait-settings")   // không @PreAuthorize, không @SecurityRequirement
  ResponseEntity<ApiResponse<JsonNode>> getPortraitSettings(@PathVariable String characterId)
      // message: "Portrait settings retrieved successfully"

  @PutMapping(value = "/portrait-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')")
  @SecurityRequirement(name = "bearerAuth")
  ResponseEntity<ApiResponse<JsonNode>> savePortraitSettings(@PathVariable String characterId,
                                                             @RequestBody byte[] body)
      // message: "Portrait settings saved successfully"
  ```
  Nhận `byte[]` (không phải DTO/`Map`) để đo đúng kích thước raw body và lưu nguyên. Swagger: `@Operation` mô tả rule 8KB/JSON object/TEACHER-owner, và `@io.swagger.v3.oas.annotations.parameters.RequestBody` với `@Schema(type = "object")` + example body của FE để Swagger UI không hiển thị dạng binary.

- [ ] **Step 1: Viết test thất bại** — standalone MockMvc như `B2bQuoteControllerTest`, `.setControllerAdvice(new GlobalExceptionHandler())`, mock `MediaService` và `CharacterPortraitSettingsService`.

```java
@Test void get_unsaved_returnsSuccessWithNullData()
    // service trả null
    status 200; jsonPath("$.success").value(true);
    content().string(containsString("\"data\":null"));

@Test void get_saved_returnsJsonObject()
    jsonPath("$.data.mode").value("AUTO"); jsonPath("$.data.points.mouth[0]").value(0.5);

@Test void get_characterMissing_returns404()
    // service ném ResourceNotFoundException → status 404

@Test void get_invalidUuid_returns400()
    // service ném IllegalArgumentException (UUID.fromString) → status 400

@Test void put_validBody_passesRawBytesAndReturnsSavedJson()
    // contentType(APPLICATION_JSON).content(exampleBody)
    // verify(service).savePortraitSettings(eq(id), eq(exampleBody.getBytes(UTF_8)), any(), any())
    status 200; jsonPath("$.data.mode").value("AUTO"); jsonPath("$.message").value("Portrait settings saved successfully");

@Test void put_invalidBody_returns400()
    // service ném InvalidRequestException → status 400

@Test void put_nonJsonContentType_returns415()
    // contentType(TEXT_PLAIN) → status 415
```

Kiểm tra `@PreAuthorize` roles bằng reflection (standalone MockMvc không chạy method security):
```java
@Test void put_isRestrictedToContentAdminSystemAdminTeacher()
    PreAuthorize pa = CharacterMediaController.class.getMethod("savePortraitSettings", String.class, byte[].class)
                        .getAnnotation(PreAuthorize.class);
    assertThat(pa.value()).isEqualTo("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')");
@Test void get_hasNoPreAuthorize()
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `mvn -q test -Dtest=CharacterMediaControllerPortraitSettingsTest`
Expected: compile error — `getPortraitSettings` / `savePortraitSettings` chưa có trên controller.

- [ ] **Step 3: Implement 2 handler** — inject `CharacterPortraitSettingsService` qua constructor (`@RequiredArgsConstructor` có sẵn), log theo mẫu `log.info("PUT /api/v1/characters/{}/media/portrait-settings by user {}", ...)`, chỉ gọi service + bọc `ApiResponse.success(...)`.

- [ ] **Step 4: Chạy test, xác nhận PASS**

Run: `mvn -q test -Dtest=CharacterMediaControllerPortraitSettingsTest`
Expected: PASS.

- [ ] **Step 5: Validation toàn service**

Run: `mvn -q -DskipTests compile` rồi `mvn test`
Expected: BUILD SUCCESS, không test nào hỏng.

Thủ công (khi chạy được local với DB): `mvn spring-boot:run`, mở Swagger `http://localhost:8080/Historical-tell/api/v1/swagger-ui`:
- `GET .../portrait-settings` không token với nhân vật chưa lưu → `"data": null`.
- `PUT` với token CONTENT_ADMIN và body ví dụ → 200, `GET` lại trả đúng JSON.
- `PUT` với body `[1]` → 400; không token → 401/403.
- Flyway log có `V20261111100000__add_character_portrait_settings` applied.

Nếu không chạy được DB local thì ghi rõ trong báo cáo là bước thủ công chưa chạy.

- [ ] **Step 6: Commit**

```bash
git add <các file trên>
git commit -m "feat(java): add character portrait settings GET/PUT endpoints"
```

---

## Ngoài phạm vi

- Không validate `mode` (`AUTO | PORTRAIT | MODEL_3D`) hay `points` ở BE — FE yêu cầu lưu nguyên.
- Không thêm `portraitSettings` vào `CharacterResponse` / local-content response (FE gọi endpoint riêng).
- Không sửa `SecurityConfig`, không đổi quyền của `upload-direct`.
