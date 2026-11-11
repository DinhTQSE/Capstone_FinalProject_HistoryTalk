# Map Practice Scoring (Chấm điểm custom map) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chấm điểm (%) bản đồ người học tự vẽ (USER map pins) so với bản đồ chuẩn của admin (ADMIN map pins) theo từng context + năm, lưu lịch sử để người học ôn tập.

**Architecture:** Một bộ chấm điểm thuần (không Spring, không DB) so hai tập pin trên mặt phẳng tọa độ, ghép cặp bằng thuật toán Hungarian, tính điểm vị trí, đường đi và nhãn. `MapPracticeService` lấy đáp án và bài làm bằng cách gọi lại `MapPinService.getPins(...)`, nên dùng chung toàn bộ kiểm tra quyền (published, cô lập trường học). Kết quả được snapshot vào bảng `map_practice_attempt` (cột `result_json` kiểu jsonb).

**Tech Stack:** Java 21, Spring Boot 3.2, Spring Data JPA, Flyway, PostgreSQL jsonb, JUnit 5, Mockito, AssertJ, MockMvc standalone.

**Spec:** Thiết kế chốt trong hội thoại ngày 2026-11-06, tóm tắt ở mục "Thiết kế đã chốt" bên dưới. Hiện trạng code: [map-api.md](../../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/docs/map-api.md), `MapPinServiceImpl`.

Mọi đường dẫn bên dưới tính từ `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`. Ký hiệu `…/` = `src/main/java/com/historytalk/`, `T…/` = `src/test/java/com/historytalk/`.

---

## Hiện trạng (đã kiểm tra code)

- Custom map của user và global map của admin **cùng nằm trong bảng `map_pin`**, phân biệt bằng `pin_owner_type` (`ADMIN` | `USER`). Mỗi pin có `label`, `description`, `latitude`, `longitude`, `pin_year`, `path_geojson` (LineString `[[lng,lat],...]`) và `pin_type`.
- `pin_type` hiện **luôn là `null`** với cả hai loại pin (`MapPinServiceImpl.createPin`, dòng 128–140). Vì vậy **không chấm điểm phe**.
- `GET /historical-contexts/{id}/map-pins?year=` với user thường trả cả pin ADMIN lẫn pin USER của chính user đó (`findVisiblePinsForUser`). Hàm này kiểm tra context đã published, chưa xóa, và cô lập trường học.
- FE hiển thị pin trên **ảnh cố định có zoom**. Tọa độ lưu trong cột `latitude`/`longitude`, nên cả hai loại pin cùng hệ tọa độ, và zoom không ảnh hưởng dữ liệu.
- `SecurityConfig` có `.anyRequest().authenticated()` (dòng 147), nên endpoint mới **không cần sửa `SecurityConfig`**.

## Thiết kế đã chốt

1. **Phạm vi một lần chấm:** một `(contextId, pinYear)`. Đáp án là các pin ADMIN chưa xóa của context và năm đó.
2. **Bài làm:**
   - Request có `pins` (≠ null): chấm danh sách đó. Đây là chế độ luyện tập, FE ẩn lớp ADMIN khi user vẽ.
   - Request **không** có `pins` (null): chấm **custom map hiện tại** của user, tức các pin USER của chính họ trong context và năm đó.
3. **Hệ tọa độ:** khoảng cách Euclid trên mặt phẳng `(x = longitude, y = latitude)`. Dùng được cho cả ảnh tĩnh lẫn WGS-84 trên vùng nhỏ.
4. **Dung sai `R`:** `R = max(0.15 × đường chéo bbox của các pin đáp án, fallbackTolerance)`. `fallbackTolerance` đọc từ `@Value("${historytalk.map-practice.fallback-tolerance:0.02}")`. **Không sửa `application.properties`.**
5. **Điểm một cặp (target, answer):**
   - `d` = khoảng cách giữa hai vị trí. `pos = max(0, 1 − d/R)`.
   - `label` = Jaccard trên tập token sau khi chuẩn hóa: NFD, bỏ dấu (`\p{M}`), `đ→d`, lowercase, tách theo ký tự không phải chữ/số. Hai tập rỗng thì `label = 0`.
   - Nếu target có path ≥ 2 điểm:
     - answer có path < 2 điểm thì `path = 0`.
     - Ngược lại, lấy mẫu lại mỗi path thành **20 điểm cách đều theo chiều dài cung, giữ thứ tự**. `m` = trung bình khoảng cách điểm thứ i với điểm thứ i. `path = max(0, 1 − m/R)`. Vẽ ngược chiều sẽ có điểm thấp.
     - `score = 0.60·pos + 0.25·path + 0.15·label`.
   - Nếu target không có path: `score = 0.85·pos + 0.15·label`.
6. **Ghép cặp:** Hungarian trên ma trận vuông đã đệm. Chi phí = `1 − score` khi `pos > 0`, ngược lại = `1.0` (cấm). Cặp có chi phí `1.0` hoặc chạm hàng/cột đệm thì không tính là cặp.
7. **Tổng điểm:**
   - `percent = 100 × Σscore / (targetCount + 0.5 × extraCount)`, làm tròn 2 chữ số, kẹp trong [0, 100].
   - `extraCount` = số pin bài làm không được ghép.
   - Pin đáp án không được ghép vào `missed`. Pin bài làm thừa vào `extras`.
8. **Quyền:** chỉ role `CUSTOMER`, `SCHOOL_STUDENT`, `TEACHER`. Các role khác nhận `ForbiddenException`. Xem/lấy attempt của người khác trả 404.
9. **Ngoài phạm vi plan này:** chấm `battleMap.symbols`, XP/quest, giáo viên giao bài, ẩn pin ADMIN ở phía server khi đang làm bài.

## Global Constraints

- Không sửa `SecurityConfig`, `JwtAuthenticationFilter`, `application.properties`, `pom.xml`.
- Không sửa hành vi hiện có của `MapPinService` hay `MapPinServiceImpl`. Chỉ gọi lại `getPins`.
- Migration theo dạng timestamp: `V20261106160000__map_practice_attempt.sql`. Bảng nằm ở schema mặc định, giống `map_pin` ở V27.
- Thông báo lỗi bằng tiếng Việt, theo giọng văn của `MapPinServiceImpl`.
- Response bọc trong `ApiResponse.success(data, message)`. Lấy caller qua `SecurityUtils.getUserId()` và `SecurityUtils.getRoleName()`.
- Commit theo Conventional Commit: `feat(map): ...`, `test(map): ...`.

## Review Focus

1. **User gửi 0 pin, hoặc chấm custom map khi chưa có pin USER nào:** phải ra 0%, toàn bộ đáp án nằm trong `missed`, attempt vẫn được lưu. Thuộc Task 1 (`emptyAnswers_scoresZero_allMissed`) và Task 3 (`submit_withoutPins_usesOwnUserPins`).
2. **Rải nhiều pin trùng lên cùng một đáp án:** chỉ một pin được ghép, các pin còn lại là `extras` và làm giảm %. Thuộc Task 1 (`duplicateAnswers_onlyOneMatched_extrasPenalise`).
3. **Nhãn tiếng Việt khác dấu hoặc hoa thường** ("Đồi A1" và "doi a1"): `labelScore` = 1. Thuộc Task 1 (`labelSimilarity_ignoresDiacriticsAndCase`).
4. **Context/năm chỉ có 1 pin đáp án, hoặc các pin trùng tọa độ** (bbox = 0): không chia cho 0, dùng `fallbackTolerance`. Thuộc Task 1 (`singleTarget_usesFallbackTolerance`).
5. **Context/năm không có pin ADMIN nào:** trả `InvalidRequestException` với message "Bối cảnh này chưa có bản đồ chuẩn cho năm {year}", và **không** lưu attempt. Thuộc Task 3 (`submit_noAdminPins_throwsAndSavesNothing`).

---

## File Structure

| File | Trách nhiệm |
|---|---|
| Create `…/service/map/scoring/HungarianAssignment.java` | Thuật toán Kuhn–Munkres O(n³) thuần |
| Create `…/service/map/scoring/MapScoringEngine.java` | Records `Point`, `Pin`, `PairResult`, `ScoreResult`, công thức chấm, chuẩn hóa nhãn, lấy mẫu lại path |
| Create `…/service/map/scoring/PathGeoJsonParser.java` | `Object` (Map hoặc chuỗi JSON) chuyển thành `List<Point>` |
| Create `src/main/resources/db/migration/V20261106160000__map_practice_attempt.sql` | Bảng attempt |
| Create `…/entity/map/MapPracticeAttempt.java` | Entity |
| Create `…/repository/MapPracticeAttemptRepository.java` | Repository |
| Create `…/dto/map/SubmitMapPracticeRequest.java`, `MapPracticePinInput.java`, `MapPracticeResultResponse.java`, `MapPracticeAttemptSummaryResponse.java` | DTO |
| Create `…/service/map/MapPracticeService.java`, `MapPracticeServiceImpl.java` | Nghiệp vụ |
| Create `…/controller/map/MapPracticeController.java` | REST |
| Test `T…/service/map/scoring/HungarianAssignmentTest.java`, `MapScoringEngineTest.java`, `PathGeoJsonParserTest.java`, `T…/service/map/MapPracticeServiceImplTest.java`, `T…/controller/map/MapPracticeControllerTest.java` | |
| Modify `docs/map-api.md` | Thêm mục "6. Luyện tập & chấm điểm bản đồ" |

---

### Task 0: Xác nhận hệ tọa độ với FE (không code)

- [ ] **Step 1:** Hỏi người phụ trách FE xem pin trên ảnh cố định lưu `latitude`/`longitude` theo đơn vị nào: pixel ảnh, % ảnh, hay độ thật với ảnh đặt theo bounds.
- [ ] **Step 2:** Ghi đơn vị vào Javadoc của `MapScoringEngine`. Chọn giá trị mặc định cho `fallbackTolerance` bằng khoảng 5% chiều rộng ảnh theo đơn vị đó, ví dụ `5` nếu là %, `0.02` nếu là độ. Cập nhật default trong `@Value` ở Task 3.

---

### Task 1: Bộ chấm điểm thuần (Hungarian, engine, parser)

**Files:**
- Create: `…/service/map/scoring/HungarianAssignment.java`, `…/service/map/scoring/MapScoringEngine.java`, `…/service/map/scoring/PathGeoJsonParser.java`
- Test: `T…/service/map/scoring/HungarianAssignmentTest.java`, `MapScoringEngineTest.java`, `PathGeoJsonParserTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class HungarianAssignment {
      /** cost is n×n (square). Returns assignment[row] = column, minimising total cost. */
      public static int[] solve(double[][] cost);
  }

  public final class MapScoringEngine {
      public record Point(double x, double y) {}                 // x = longitude, y = latitude
      public record Pin(String id, String label, Point position, List<Point> path) {}
      public record PairResult(Pin target, Pin answer, double distance,
                               double positionScore, Double pathScore,   // null if the target has no path
                               double labelScore, double score) {}
      public record ScoreResult(double percent, double tolerance, List<PairResult> pairs,
                                List<Pin> missed, List<Pin> extras) {}

      public static double toleranceFor(List<Pin> targets, double fallbackTolerance);
      public static ScoreResult score(List<Pin> targets, List<Pin> answers, double tolerance); // targets non-empty, else IllegalArgumentException
      static double labelSimilarity(String a, String b);          // package-private for tests
      static List<Point> resample(List<Point> path, int n);       // package-private for tests
  }

  public final class PathGeoJsonParser {
      /** Accepts a Map {"coordinates":[[lng,lat],...]} or a JSON string; returns an empty list for any invalid input. */
      public static List<Point> parse(Object geoJson, ObjectMapper mapper);
  }
  ```

- [ ] **Step 1: Viết test thất bại.** `R = 10` trong mọi test của engine trừ khi ghi khác.

  `HungarianAssignmentTest`:
  - `solve_3x3_picksMinimumCost`: cost `{{4,1,3},{2,0,5},{3,2,2}}` cho kết quả `[1,0,2]` (tổng 5).
  - `solve_1x1`: kết quả `[0]`.

  `MapScoringEngineTest`:
  - `exactMatch_scores100`: target và answer cùng vị trí `(50,50)`, cùng nhãn "Đồi A1", không path. Kết quả `percent == 100.0`, `pairs.size()==1`.
  - `distanceHalfTolerance_positionScoreHalf`: target `(0,0)`, answer `(5,0)`, cùng nhãn. `positionScore == 0.5`, `score == 0.85*0.5 + 0.15`.
  - `beyondTolerance_notMatched`: answer `(20,0)`. `pairs` rỗng, `missed.size()==1`, `extras.size()==1`, `percent == 0`.
  - `emptyAnswers_scoresZero_allMissed`: 2 target, 0 answer. `percent == 0`, `missed.size()==2`.
  - `duplicateAnswers_onlyOneMatched_extrasPenalise`: 1 target `(0,0)`, 3 answer cùng ở `(0,0)` cùng nhãn. `pairs.size()==1`, `extras.size()==2`, `percent == 100*1.0/(1+0.5*2) == 50.0`.
  - `hungarian_beatsGreedy`: target A `(0,0)`, B `(6,0)`; answer P `(3,0)`, Q `(9,0)`, nhãn rỗng hết. Ghép phải là A–P và B–Q (`pairs.size()==2`).
  - `reversedPath_scoresLowerThanSameDirection`: target path `[(0,0),(10,0)]`. Answer path cùng chiều có `pathScore == 1.0`. Answer path ngược chiều `[(10,0),(0,0)]` có `m = 100/19 ≈ 5.263`, nên `pathScore ≈ 0.474`. Assert `isCloseTo(0.474, within(0.001))`.
  - `targetHasPath_answerWithout_pathScoreZero`: `pathScore == 0.0`, `score == 0.60*pos + 0.15*label`.
  - `labelSimilarity_ignoresDiacriticsAndCase`: `labelSimilarity("Đồi A1", "doi a1") == 1.0`; `labelSimilarity("Đồi A1", "Đồi Him Lam")` bằng 1/4; `labelSimilarity("", "") == 0.0`.
  - `singleTarget_usesFallbackTolerance`: `toleranceFor(List.of(oneTarget), 0.02) == 0.02`; hai target cùng tọa độ cũng `== 0.02`.
  - `toleranceFor_usesBboxDiagonal`: target `(0,0)` và `(30,40)`. Kết quả `== 0.15*50 == 7.5`.
  - `resample_keepsEndpointsAndCount`: `resample([(0,0),(10,0)], 20)` có 20 điểm, điểm đầu `(0,0)`, điểm cuối `(10,0)`.
  - `score_emptyTargets_throws`: `IllegalArgumentException`.

  `PathGeoJsonParserTest`:
  - `parse_map`: `Map.of("type","LineString","coordinates",List.of(List.of(106.8,20.9),List.of(106.9,21.0)))` cho 2 điểm, `x==106.8`.
  - `parse_jsonString`: kết quả như trên.
  - `parse_nullOrInvalid_returnsEmpty`: `null`, `"abc"`, `Map.of()` đều cho list rỗng.

- [ ] **Step 2: Chạy test, xác nhận thất bại.**
  `mvn -q test -Dtest="HungarianAssignmentTest,MapScoringEngineTest,PathGeoJsonParserTest"`. Kết quả mong đợi: lỗi biên dịch, chưa có class.

- [ ] **Step 3: Cài đặt** theo "Thiết kế đã chốt" mục 4–7.
  - `HungarianAssignment`: Kuhn–Munkres chuẩn với potentials u/v, O(n³).
  - `score`:
    - Đệm ma trận lên `n = max(targets, answers)`, ô đệm có cost `1.0`.
    - Cặp hợp lệ khi row < answers, col < targets, `pos > 0` và `cost < 1.0`.
    - `missed` giữ thứ tự `targets`, `extras` giữ thứ tự `answers`.
  - `resample`: path có 1 điểm thì lặp lại điểm đó n lần. Path có độ dài 0 thì cũng lặp điểm đầu.

- [ ] **Step 4: Chạy lại lệnh ở Step 2.** Kết quả mong đợi: PASS hết.

- [ ] **Step 5: Commit.** `git commit -m "feat(map): add pure map scoring engine with hungarian matching"`

---

### Task 2: Migration, entity, repository

**Files:**
- Create: `src/main/resources/db/migration/V20261106160000__map_practice_attempt.sql`, `…/entity/map/MapPracticeAttempt.java`, `…/repository/MapPracticeAttemptRepository.java`

**Interfaces:**
- Produces:
  ```java
  @Entity @Table(name = "map_practice_attempt")
  public class MapPracticeAttempt {          // Lombok @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor, same as MapPin
      UUID attemptId;                         // @UuidGenerator, column attempt_id
      User user;                              // @ManyToOne LAZY, user_id
      HistoricalContext historicalContext;    // @ManyToOne LAZY, context_id
      Integer pinYear;
      String source;                          // "SUBMITTED" | "MY_PINS"
      Double scorePercent; Double tolerance;
      Integer targetCount; Integer matchedCount; Integer extraCount;
      String resultJson;                      // @JdbcTypeCode(SqlTypes.JSON), jsonb, same as MapPin.pathGeoJson
      LocalDateTime createdAt;                // @CreationTimestamp
  }

  public interface MapPracticeAttemptRepository extends JpaRepository<MapPracticeAttempt, UUID> {
      List<MapPracticeAttempt> findByUser_UidAndHistoricalContext_ContextIdOrderByCreatedAtDesc(UUID uid, UUID contextId);
      List<MapPracticeAttempt> findByUser_UidAndHistoricalContext_ContextIdAndPinYearOrderByCreatedAtDesc(UUID uid, UUID contextId, Integer pinYear);
      Optional<MapPracticeAttempt> findByAttemptIdAndUser_Uid(UUID attemptId, UUID uid);
  }
  ```

- [ ] **Step 1: Viết migration.** Header comment theo mẫu `V20261106143000__add_character_pronouns.sql`.
  ```sql
  CREATE TABLE IF NOT EXISTS map_practice_attempt (
      attempt_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id        UUID NOT NULL REFERENCES "user"(uid),
      context_id     UUID NOT NULL REFERENCES historical_context(context_id),
      pin_year       INTEGER NOT NULL,
      source         VARCHAR(20) NOT NULL CHECK (source IN ('SUBMITTED','MY_PINS')),
      score_percent  NUMERIC(5,2) NOT NULL CHECK (score_percent BETWEEN 0 AND 100),
      tolerance      DOUBLE PRECISION NOT NULL,
      target_count   INTEGER NOT NULL,
      matched_count  INTEGER NOT NULL,
      extra_count    INTEGER NOT NULL,
      result_json    JSONB NOT NULL,
      created_at     TIMESTAMP NOT NULL DEFAULT now()
  );
  CREATE INDEX IF NOT EXISTS idx_map_practice_attempt_user_ctx
      ON map_practice_attempt (user_id, context_id, pin_year, created_at DESC);
  ```
  Kiểm tra V27 xem `gen_random_uuid()` có được dùng không. Nếu V27 dùng cách tạo UUID khác thì theo V27.

- [ ] **Step 2: Tạo entity và repository** theo Interfaces. Map `scorePercent` thành `Double` với `columnDefinition = "numeric(5,2)"`.

- [ ] **Step 3: Kiểm tra biên dịch và migration.**
  - `mvn -q -DskipTests compile`: phải thành công.
  - `mvn spring-boot:run` trên DB local: log Flyway phải có `Migrating schema ... to version "20261106160000 - map practice attempt"`, không có lỗi Hibernate validate.

- [ ] **Step 4: Commit.** `git commit -m "feat(map): add map_practice_attempt table and entity"`

---

### Task 3: DTO và `MapPracticeService`

**Files:**
- Create: `…/dto/map/SubmitMapPracticeRequest.java`, `…/dto/map/MapPracticePinInput.java`, `…/dto/map/MapPracticeResultResponse.java`, `…/dto/map/MapPracticeAttemptSummaryResponse.java`, `…/service/map/MapPracticeService.java`, `…/service/map/MapPracticeServiceImpl.java`
- Test: `T…/service/map/MapPracticeServiceImplTest.java`

**Interfaces:**
- Consumes:
  - Task 1: `MapScoringEngine.score`, `toleranceFor`, `PathGeoJsonParser.parse`.
  - Task 2: repository.
  - Có sẵn: `MapPinService.getPins(String contextId, Integer year, String callerId, String role) -> List<MapPinResponse>`.
- Produces:
  ```java
  // DTOs (Lombok @Data; responses use @Builder)
  SubmitMapPracticeRequest { @NotNull Integer pinYear; @Valid @Size(max = 200) List<MapPracticePinInput> pins; } // pins == null → MY_PINS
  MapPracticePinInput { String label; @NotNull Double latitude; @NotNull Double longitude; Object pathGeoJson; }
  MapPracticeResultResponse {
      String attemptId; String contextId; Integer pinYear; String source;
      Double scorePercent; Double tolerance; Integer targetCount; Integer matchedCount; Integer extraCount;
      LocalDateTime createdAt;
      List<PairDto> pairs; List<PinDto> missed; List<PinDto> extras;
      // nested static classes:
      PinDto  { String pinId; String label; Double latitude; Double longitude; List<List<Double>> path; } // path as [[lng,lat],...]
      PairDto { PinDto target; PinDto answer; Double distance; Double positionScore; Double pathScore; Double labelScore; Double score; }
  }
  MapPracticeAttemptSummaryResponse { String attemptId; Integer pinYear; String source; Double scorePercent;
                                      Integer targetCount; Integer matchedCount; Integer extraCount; LocalDateTime createdAt; }

  public interface MapPracticeService {
      MapPracticeResultResponse submitAttempt(String contextId, SubmitMapPracticeRequest request, String callerId, String role);
      List<MapPracticeAttemptSummaryResponse> listAttempts(String contextId, Integer pinYear, String callerId);
      MapPracticeResultResponse getAttempt(String contextId, String attemptId, String callerId);
  }
  ```
  `result_json` lưu đúng `{pairs, missed, extras}` của `MapPracticeResultResponse`, serialize bằng `ObjectMapper`. `getAttempt` đọc lại và ghép với các cột còn lại.

- [ ] **Step 1: Viết test thất bại** (Mockito, theo mẫu `HistoricalContextServiceImplTest`).
  - Mock: `MapPinService`, `MapPracticeAttemptRepository`, `HistoricalContextRepository`, `UserRepository`.
  - `ObjectMapper` thật, có `findAndRegisterModules()`.
  - `fallbackTolerance` gán bằng `ReflectionTestUtils.setField(service, "fallbackTolerance", 0.02)`.

  Các test:
  - `submit_withPins_scoresAgainstAdminPinsOnly`: `getPins` trả 1 ADMIN `(20.0,106.0,"Đồi A1")` và 1 USER ở chỗ khác. Request có 1 pin trùng ADMIN. Kết quả `scorePercent == 100.0`, `source == "SUBMITTED"`. Repository `save` được gọi 1 lần, ArgumentCaptor xác nhận `targetCount==1`. Pin USER **không** được tính là đáp án hay bài làm.
  - `submit_withoutPins_usesOwnUserPins`: `pins == null`. Bài làm là các pin USER có `createdBy == callerId`. Kết quả `source == "MY_PINS"`. Trường hợp không có pin USER nào thì `scorePercent == 0.0` và `missed.size()==1`.
  - `submit_noAdminPins_throwsAndSavesNothing`: `getPins` chỉ trả pin USER. Ném `InvalidRequestException` với message `"Bối cảnh này chưa có bản đồ chuẩn cho năm 1954"`. `verify(repository, never()).save(any())`.
  - `submit_forbiddenRole_throws`: role `CONTENT_ADMIN` ném `ForbiddenException`, `getPins` không được gọi.
  - `submit_propagatesAccessErrors`: `getPins` ném `ResourceNotFoundException` (context chưa published). Ngoại lệ được ném tiếp.
  - `getAttempt_otherUsersAttempt_notFound`: `findByAttemptIdAndUser_Uid` trả empty, ném `ResourceNotFoundException`.
  - `getAttempt_contextMismatch_notFound`: attempt thuộc context khác `contextId` trên path, ném `ResourceNotFoundException`.
  - `listAttempts_filtersByYearWhenGiven`: có `pinYear` thì gọi method `...AndPinYear...`, không có thì gọi method không lọc năm.

- [ ] **Step 2: Chạy test.** `mvn -q test -Dtest=MapPracticeServiceImplTest`. Kết quả mong đợi: FAIL (thiếu class).

- [ ] **Step 3: Cài đặt `MapPracticeServiceImpl`** (`@Service`, `@RequiredArgsConstructor`, `@Transactional` cho submit, `readOnly` cho các hàm đọc).
  - Kiểm tra role trước. Chỉ cho `CUSTOMER`, `SCHOOL_STUDENT`, `TEACHER`, ngược lại ném `ForbiddenException("Bạn không có quyền luyện tập bản đồ")`.
  - Gọi `mapPinService.getPins` một lần, rồi tách đáp án và bài làm theo `pinOwnerType` và `createdBy`.
  - Chuyển `MapPinResponse` và `MapPracticePinInput` sang `MapScoringEngine.Pin`: `Point(longitude, latitude)`, path qua `PathGeoJsonParser`.
  - Gắn entity bằng `contextRepository.getReferenceById` và `userRepository.getReferenceById`.
  - Trường `fallbackTolerance` dùng `@Value("${historytalk.map-practice.fallback-tolerance:<giá trị chốt ở Task 0>}")`.

- [ ] **Step 4: Chạy lại test ở Step 2.** Kết quả mong đợi: PASS.

- [ ] **Step 5: Commit.** `git commit -m "feat(map): add map practice service that scores user maps against admin map"`

---

### Task 4: `MapPracticeController` và cập nhật tài liệu API

**Files:**
- Create: `…/controller/map/MapPracticeController.java`
- Test: `T…/controller/map/MapPracticeControllerTest.java`
- Modify: `docs/map-api.md` (thêm mục 6)

**Interfaces:**
- Consumes: `MapPracticeService` (Task 3).
- Produces. Base path `/api/v1/historical-contexts/{contextId}/map-practice/attempts`, `@SecurityRequirement(name = "bearerAuth")`, `@Tag(name = "Map Practice")`:

  | Method | Path | Body/Query | Status | Message |
  |---|---|---|---|---|
  | POST | `` | `@Valid SubmitMapPracticeRequest` | 201 | "Chấm điểm bản đồ thành công" |
  | GET | `` | `?pinYear=` (không bắt buộc) | 200 | "Lấy lịch sử luyện tập thành công" |
  | GET | `/{attemptId}` | – | 200 | "Lấy kết quả luyện tập thành công" |

- [ ] **Step 1: Viết test thất bại** (MockMvc standalone theo mẫu `ClassroomControllerTest`, `setControllerAdvice(new GlobalExceptionHandler())`, `Mockito.mockStatic(SecurityUtils.class)` cho `getUserId` và `getRoleName`).
  - `post_returns201AndScore`: `jsonPath("$.data.scorePercent").value(80.0)`.
  - `post_missingPinYear_returns400`.
  - `post_pinMissingLatitude_returns400`.
  - `list_passesPinYear`: `verify(service).listAttempts(ctxId, 1954, userId)`.
  - `get_notFound_returns404`: service ném `ResourceNotFoundException`.

- [ ] **Step 2: Chạy test.** `mvn -q test -Dtest=MapPracticeControllerTest`. Kết quả mong đợi: FAIL.

- [ ] **Step 3: Cài đặt controller.** Mỏng, không chứa logic, theo kiểu `MapPinController`.

- [ ] **Step 4: Cập nhật `docs/map-api.md`.** Thêm "## 6. Luyện tập & chấm điểm bản đồ", gồm:
  - Ba endpoint trên.
  - Ví dụ request/response.
  - Công thức (mục "Thiết kế đã chốt" 4–7).
  - Hướng dẫn FE:
    - Ở chế độ luyện tập, ẩn lớp `pinOwnerType == "ADMIN"`. Khi nộp, gửi `pins`.
    - Nút "Chấm custom map của tôi" thì gửi request **không** có `pins`.
    - Màn kết quả vẽ chồng `pairs[].target` (màu chuẩn) và `pairs[].answer`, nối bằng nét đứt, tô màu theo `score` (≥0.8 xanh, ≥0.4 vàng, còn lại đỏ). Hiển thị `missed` và `extras`.
  - Ghi rõ giới hạn: `GET map-pins` vẫn trả pin ADMIN, nên chế độ luyện tập chỉ phù hợp cho tự ôn tập.

- [ ] **Step 5: Kiểm tra toàn bộ.**
  - `mvn test`: tất cả PASS.
  - Chạy `mvn spring-boot:run`, mở Swagger `http://localhost:8080/Historical-tell/api/v1/swagger-ui`.
  - Với một context đã published có pin ADMIN: POST có pins trùng vị trí phải ra `scorePercent` 100; POST không có pins phải trả `source: "MY_PINS"`; GET lịch sử phải thấy hai attempt.

- [ ] **Step 6: Commit.** `git commit -m "feat(map): expose map practice scoring endpoints and document API"`

---

## Việc tiếp theo (ngoài plan)

- Ẩn pin ADMIN ở phía server khi đang luyện tập (`GET map-pins?layer=mine`), cần thiết nếu dùng để giao bài có tính điểm.
- Chấm `battleMap.symbols` (x/y theo %, có phe và hướng) bằng cùng engine, thêm điểm phe và hướng.
- Cộng XP/quest qua `GamificationService`. Giáo viên giao bài luyện bản đồ.
