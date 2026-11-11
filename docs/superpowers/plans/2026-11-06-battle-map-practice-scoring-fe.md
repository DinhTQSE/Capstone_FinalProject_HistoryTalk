# Battle Map Practice Scoring (Chấm điểm lược đồ người học tự vẽ, chỉ FE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Người học tự vẽ lại lược đồ trận đánh trên ảnh nền của admin. Khi bấm "So sánh", FE tính ngay % chính xác so với lược đồ admin và hiển thị chỗ đúng, thiếu, thừa. Không gọi API mới và không lưu DB.

**Architecture:**
- Một module thuần TypeScript (`battle-map-scoring.ts`) so hai `BattleMap` trên cùng hệ tọa độ % ảnh. Module này ghép cặp ký hiệu theo loại và phe, chấm vị trí hoặc đường đi, rồi trả về điểm dạng F1.
- Trình soạn `BattleMapEditor` có thêm chế độ `practice`: khóa ảnh nền và phe, bài vẽ lưu trong localStorage bằng cơ chế nháp sẵn có.
- Màn kết quả dựng lại một `BattleMap` tổng hợp (mỗi trạng thái là một "phe" màu) rồi vẽ bằng `BattleMapCanvas` có sẵn.

**Tech Stack:** Next.js 16 / React 19 / TypeScript (repo `HistoryTalk-FE`), Bun (`bun test`, có sẵn, không thêm dependency).

**Spec:** Thiết kế chốt trong hội thoại ngày 2026-11-06, tóm tắt ở mục "Thiết kế đã chốt" bên dưới. Kiểu dữ liệu: `src/components/historical-map/battle-map.types.ts`. Hình học đường: `src/components/historical-map/battle-path.ts`.

> Lưu ý: plan này **thay thế hướng làm** của `2026-11-06-map-practice-scoring.md` cho nhu cầu hiện tại. Plan kia chấm `map_pin` ở BE và lưu bảng `map_practice_attempt`. Plan này chỉ dùng FE và chấm `battleMap.symbols`, tức các mũi tên và ký hiệu người dùng thực sự vẽ. Không xóa plan kia; nhóm tự quyết giữ hay bỏ.

Mọi đường dẫn bên dưới tính từ gốc repo **`HistoryTalk-FE/`**. Ký hiệu `HM/` = `src/components/historical-map/`.

---

## Hiện trạng (đã kiểm tra code)

- Lược đồ admin là JSON `battleMap` lưu ở `historical_context.battle_map` (Flyway V28). Người học đã nhận nó qua `GET /historical-contexts/{id}` (`event.battleMap`), nên FE có sẵn đáp án mà không cần API mới.
- Tọa độ ký hiệu là **% ảnh nền** (`x`, `y` trong khoảng 0–100). Người học vẽ trên **cùng `imageUrl`**, nên hai bản so sánh trực tiếp được.
- Ký hiệu dạng đường (`isLineSymbol`) có `points` theo thứ tự đầu đến cuối, đầu mũi tên nằm ở điểm cuối. Bản cũ không có `points` thì dùng `controlPoints(symbol, aspect)` trong `HM/battle-path.ts` để quy đổi.
- `BattleMapEditor` đã lưu nháp vào localStorage theo `draftId` (khóa `historytalk:battle-map-draft:v2:{uid}:{draftId}`, dòng 81).
- `BattleMapCanvas` nhận `symbolView(symbol) => { hidden?, dim? }`, dùng được để làm mờ ký hiệu đáp án.
- Route bản đồ: `MapRoute = { battle, view: "map" | "detail" | "edit" }` (`HM/HistoricalMapModal.tsx:67`), đồng bộ URL ở `HM/UrlSyncedHistoricalMap.tsx:29`.
- FE **chưa có test runner**. Bun có sẵn `bun test` với API kiểu Jest (`import { describe, expect, test } from "bun:test"`) và tự đọc alias `@/` từ `tsconfig.json`.

## Thiết kế đã chốt

1. **Đầu vào:** `answer` = `event.battleMap` của admin. `attempt` = bản người học vẽ. `aspect` = chiều rộng / chiều cao thật của ảnh nền, lấy khi tải ảnh, thất bại thì dùng `1`.
2. **Ký hiệu được chấm:** mọi loại **trừ `label` và `step`**. Không chấm chữ, `phaseId`, `effect`, `rotation` của ký hiệu dạng điểm, `size`.
3. **Lược đồ chấm được** (`isGradableMap`): `mode === "custom"`, `imageUrl` không rỗng, và có ít nhất 1 ký hiệu được chấm.
4. **Đơn vị khoảng cách:** % chiều rộng ảnh. Điểm `(x, y)` quy đổi thành `(x, y / aspect)`. Dung sai `R = 8`.
5. **Hai ký hiệu ghép được với nhau** khi cả hai cùng là dạng đường hoặc cùng là dạng điểm, **và** cùng loại hoặc cùng nhóm trong `SYMBOL_GROUPS`.
6. **Điểm hình học `geom`** (0–1):
   - Dạng điểm: `d` = khoảng cách hai tâm. `geom = max(0, 1 − d/R)`.
   - Dạng đường: lấy `controlPoints`, quy đổi đơn vị, rồi **lấy mẫu lại 20 điểm cách đều theo chiều dài cung, giữ thứ tự**. `m` = trung bình khoảng cách điểm thứ i với điểm thứ i. `geom = max(0, 1 − m/R)`.
   - Với `defenseLine` và `stakes` (không có đầu mũi tên): `m = min(m xuôi, m của đường người học đảo ngược)`.
   - Với `arrow`, `flank`, `march`, `retreat`: chỉ tính chiều xuôi, nên **vẽ ngược chiều sẽ ra điểm thấp hoặc 0**.
7. **Hệ số:**
   - `typeFactor`: cùng loại = 1, khác loại nhưng cùng nhóm = 0.5.
   - `factionFactor`: cùng `factionId` = 1, khác phe = 0.5.
   - `score = geom × typeFactor × factionFactor`. Chỉ là ứng viên ghép cặp khi `geom > 0`.
8. **Ghép cặp tham lam:**
   - Xếp mọi cặp ứng viên theo `score` giảm dần. Bằng điểm thì xét chỉ số answer, rồi chỉ số attempt, để kết quả ổn định.
   - Duyệt lần lượt, mỗi ký hiệu chỉ ghép một lần.
   - Lược đồ chỉ khoảng 10–40 ký hiệu nên ghép tham lam là đủ.
9. **Điểm tổng (F1):**
   - `percent = 100 × 2·Σscore / (answerCount + attemptCount)`, làm tròn 1 chữ số. Hai số đếm chỉ tính ký hiệu được chấm.
   - Bài vẽ rỗng ra 0.
   - Rải thật nhiều ký hiệu sẽ làm **giảm** điểm.
10. **Trạng thái hiển thị:** cặp có `score ≥ 0.8` là "Đúng", cặp ghép được còn lại là "Gần đúng". Ký hiệu đáp án chưa ghép là "Thiếu". Ký hiệu người học chưa ghép là "Thừa / sai".
11. **Lưu trữ:** bài vẽ chỉ lưu localStorage qua nháp của editor, `draftId = "practice:{eventId}"`. Không lưu điểm.

## Global Constraints

- Không sửa repo BE, không thêm API, không thêm bảng, không thêm package vào `package.json`.
- File test đặt cạnh mã nguồn, đuôi `.test.ts`, chạy bằng `bun test <file>`. Thêm `"**/*.test.ts"` vào `exclude` của `tsconfig.json` để `next build` không type-check `bun:test`.
- Màu của các "phe" so sánh (đúng định dạng `#RRGGBB`):

  | id | Tên | Màu |
  |---|---|---|
  | `cmp-correct` | "Đúng" | `#2E7D32` |
  | `cmp-partial` | "Gần đúng" | `#C98A00` |
  | `cmp-missed` | "Thiếu" | `#B8321F` |
  | `cmp-extra` | "Thừa / sai" | `#6B7280` |
  | `cmp-answer` | "Đáp án" | `#9CA3AF` |

- Chữ trên giao diện:
  - Nút ở trang trận đánh: **"Tự vẽ lược đồ"**.
  - Nút trong editor: **"So sánh"**.
  - Tiêu đề màn kết quả: **"Kết quả so sánh"**.
  - Các nút ở màn kết quả: **"Vẽ tiếp"**, **"Làm lại"**, **"Đóng"**.
- Code theo style hiện có của `HM/` (kiểu dữ liệu từ `battle-map.types.ts`, CSS module của editor). Comment ngắn bằng tiếng Anh như các file lân cận.
- Làm trên nhánh mới từ `develop` (README của FE yêu cầu dùng `develop`). Commit theo Conventional Commit: `feat(map-practice): …`, `test(map-practice): …`.

## Review Focus

1. **Bài vẽ rỗng, hoặc chỉ có `label`/`step`:** ra 0%, mọi ký hiệu đáp án vào `missedIds`. Test ở Task 1: `emptyAttempt_scoresZero_allMissed`.
2. **Rải nhiều ký hiệu trùng lên một đáp án:** chỉ ghép được một, điểm giảm. Test ở Task 1: `spamSameSpot_onlyOneMatched`.
3. **Mũi tên vẽ ngược chiều:** ra 0. Phòng tuyến vẽ ngược chiều vẫn 100. Test ở Task 1: `reversedArrow_noMatch` và `reversedDefenseLine_fullScore`.
4. **Đáp án là lược đồ cũ không có `points`** (chỉ có `rotation`/`size`) trong khi người học vẽ có `points`: vẫn so đúng. Test ở Task 1: `legacyAnswerWithoutPoints_matchesPointsAttempt`.
5. **Ảnh nền không vuông** (`aspect ≠ 1`): khoảng cách dọc được quy đổi. Test ở Task 1: `aspectScalesVerticalDistance`.

---

## File Structure

| File | Trách nhiệm |
|---|---|
| Create `HM/battle-map-scoring.ts` | `isGradableMap`, `scoreBattleMap`, `resamplePath`: chấm điểm thuần |
| Create `HM/battle-map-scoring.test.ts` | Test bộ chấm |
| Create `HM/battle-map-practice.ts` | `toPracticeMap`, `buildCompareMap`, `COMPARE_FACTIONS`: dựng bản luyện tập và bản so sánh |
| Create `HM/battle-map-practice.test.ts` | Test hai hàm dựng |
| Create `HM/BattleMapCompareView.tsx` | Màn "Kết quả so sánh" |
| Modify `HM/BattleMapEditor.tsx` | Prop `mode?: "author" \| "practice"` |
| Modify `HM/BattleDetailView.tsx` | Nút "Tự vẽ lược đồ", mở editor luyện tập, hiện kết quả |
| Modify `HM/HistoricalMapModal.tsx`, `HM/UrlSyncedHistoricalMap.tsx` | Thêm `view: "practice"` vào route |
| Modify `tsconfig.json` | `exclude` file `*.test.ts` |

---

### Task 1: Bộ chấm điểm thuần

**Files:**
- Create: `HM/battle-map-scoring.ts`, `HM/battle-map-scoring.test.ts`
- Modify: `tsconfig.json` (`"exclude": ["node_modules", "**/*.test.ts"]`)

**Interfaces:**
- Consumes: `BattleMap`, `BattleSymbol`, `MapPoint`, `SYMBOL_GROUPS`, `isLineSymbol` từ `./battle-map.types`; `controlPoints` từ `./battle-path`.
- Produces:
  ```ts
  export const SCORE_TOLERANCE = 8;
  export const RESAMPLE_POINTS = 20;
  export interface SymbolMatch { answerId: string; attemptId: string; score: number }
  export interface BattleMapScore {
    percent: number;            // 0–100, 1 decimal
    matches: SymbolMatch[];     // sorted by answer order
    missedIds: string[];        // scored answer symbols left unmatched, answer order
    extraIds: string[];         // scored attempt symbols left unmatched, attempt order
    answerCount: number;        // scored symbols only
    attemptCount: number;
  }
  export function isScoredSymbol(s: BattleSymbol): boolean;           // false for "label" | "step"
  export function isGradableMap(map: BattleMap | null | undefined): boolean;
  export function resamplePath(points: MapPoint[], n: number): MapPoint[];
  /** Throws Error("Lược đồ này chưa chấm được") when !isGradableMap(answer). */
  export function scoreBattleMap(answer: BattleMap, attempt: BattleMap, aspect?: number): BattleMapScore; // aspect default 1
  ```

- [ ] **Step 1: Viết test thất bại** `HM/battle-map-scoring.test.ts`.

  Tạo helper trong file test:
  - `map(symbols)`: `mode: "custom"`, `imageUrl: "x.png"`, `imageSource: ""`, hai phe `a` và `b` màu `#B8321F` và `#1F4E8C`.
  - `pt(id, type, x, y, factionId = "a")`: `size: 7`, `rotation: 0`, `label: ""`.
  - `line(id, type, points, factionId = "a")`: điền `x`, `y`, `size` bằng `withPoints`.

  Tất cả test dùng `aspect = 1` nếu không ghi khác.
  - `identicalMaps_score100`: infantry (50,50) và arrow [(10,10),(40,10)] ở cả hai bên. Kết quả `percent === 100`, `matches.length === 2`.
  - `emptyAttempt_scoresZero_allMissed`: answer có 2 ký hiệu, attempt rỗng (hoặc chỉ có một `label`). Kết quả `percent === 0`, `missedIds` = cả 2 id, `attemptCount === 0`.
  - `pointHalfTolerance_scores50`: infantry (50,50) và (54,50). `matches[0].score === 0.5`, `percent === 50`.
  - `pointBeyondTolerance_noMatch`: (50,50) và (59,50). `matches` rỗng, `missedIds.length === 1`, `extraIds.length === 1`, `percent === 0`.
  - `spamSameSpot_onlyOneMatched`: 1 infantry (50,50) và 3 infantry (50,50). `matches.length === 1`, `extraIds.length === 2`, `percent === 50` (vì 200/4).
  - `sameGroupOtherType_halfCredit`: infantry và cavalry cùng chỗ. `percent === 50`.
  - `otherFaction_halfCredit`: infantry phe `a` và infantry phe `b` cùng chỗ. `percent === 50`.
  - `differentGroup_noMatch`: infantry và fort cùng chỗ. `percent === 0`.
  - `lineVsPoint_noMatch`: defenseLine và fort cùng nhóm "Trận địa", cùng tâm. `percent === 0`.
  - `reversedArrow_noMatch`: arrow [(0,50),(40,50)] và [(40,50),(0,50)]. `percent === 0`. (Khoảng cách trung bình là 8000/19/20 ≈ 21.05, lớn hơn R.)
  - `reversedDefenseLine_fullScore`: như trên nhưng loại `defenseLine`. `percent === 100`.
  - `legacyAnswerWithoutPoints_matchesPointsAttempt`: answer `{ type: "arrow", x: 50, y: 50, size: 40, rotation: 0 }` không có `points`. Attempt là arrow có points [(31.6,50),(68.4,50)]. `percent === 100`.
  - `aspectScalesVerticalDistance`: infantry (50,50) và (50,58) với `aspect = 2`. `percent === 50`.
  - `labelAndStepIgnored`: answer có infantry, `label`, `step`; attempt chỉ có infantry. `percent === 100`, `answerCount === 1`.
  - `isGradableMap_rules`: `mode: "image"` cho false; chỉ có `label` cho false; `imageUrl: ""` cho false; `null` cho false; có 1 infantry cho true.
  - `scoreBattleMap_notGradable_throws`: `expect(() => scoreBattleMap(imageModeMap, attempt)).toThrow("Lược đồ này chưa chấm được")`.
  - `resamplePath_keepsEndpointsAndCount`: `resamplePath([(0,0),(10,0)], 20)` có 20 điểm, điểm đầu (0,0), điểm cuối (10,0), điểm thứ 2 ≈ (10/19, 0).

- [ ] **Step 2: Chạy test, xác nhận thất bại.**
  `bun test src/components/historical-map/battle-map-scoring.test.ts`. Kết quả mong đợi: lỗi `Cannot find module './battle-map-scoring'`.

- [ ] **Step 3: Cài đặt `HM/battle-map-scoring.ts`** theo mục 2–9 của "Thiết kế đã chốt".
  - Đường nét đi qua `controlPoints(s, aspect)` rồi mới quy đổi `y / aspect`.
  - `resamplePath`: lấy mẫu theo chiều dài cung tích lũy. Path chỉ 1 điểm, hoặc độ dài 0, thì lặp lại điểm đầu `n` lần.
  - Cặp hợp lệ chỉ khi `geom > 0` và hai ký hiệu ghép được (mục 5).

- [ ] **Step 4: Chạy lại lệnh ở Step 2.** Kết quả mong đợi: tất cả PASS. Sau đó `bunx tsc --noEmit` không báo lỗi.

- [ ] **Step 5: Commit.**
  ```bash
  git add tsconfig.json src/components/historical-map/battle-map-scoring.ts src/components/historical-map/battle-map-scoring.test.ts
  git commit -m "feat(map-practice): pure battle map scoring against the admin map"
  ```

---

### Task 2: Dựng bản luyện tập và bản so sánh

**Files:**
- Create: `HM/battle-map-practice.ts`, `HM/battle-map-practice.test.ts`

**Interfaces:**
- Consumes: `BattleMap`, `BattleFaction` (types); `BattleMapScore`, `isScoredSymbol` (Task 1).
- Produces:
  ```ts
  export const COMPARE_FACTIONS: BattleFaction[];   // 5 factions, ids/names/colours from Global Constraints, in table order
  export const ANSWER_PREFIX = "ans:";
  export const ATTEMPT_PREFIX = "you:";
  /** Blank sheet for the learner: same mode "custom", imageUrl, imageSource and factions; symbols []; no phases. */
  export function toPracticeMap(answer: BattleMap): BattleMap;
  /**
   * One map showing the result.
   * - Attempt symbols get id ATTEMPT_PREFIX + id and faction cmp-correct (score ≥ 0.8) | cmp-partial | cmp-extra.
   * - Scored answer symbols that were missed get id ANSWER_PREFIX + id and faction cmp-missed.
   * - Every other answer symbol (matched, label, step) gets id ANSWER_PREFIX + id and faction cmp-answer.
   * - phases dropped; imageUrl/imageSource from answer.
   */
  export function buildCompareMap(answer: BattleMap, attempt: BattleMap, score: BattleMapScore): BattleMap;
  ```

- [ ] **Step 1: Viết test thất bại** `HM/battle-map-practice.test.ts`. Dùng lại cách dựng map như Task 1.
  - `toPracticeMap_keepsBackgroundAndFactions_clearsSymbols`: `imageUrl`, `imageSource`, `factions` giữ nguyên. `symbols` là `[]`. `phases` là `undefined`. `mode` là `"custom"`.
  - `buildCompareMap_coloursByStatus`: answer có A1 infantry (50,50) và A2 fort (20,20). Attempt có U1 infantry (52,50) và U2 cavalry (80,80). Chấm bằng `scoreBattleMap` rồi kiểm tra:
    - `you:U1` thuộc `cmp-partial` (score = 1 − 2/8 = 0.75, nhỏ hơn 0.8).
    - `you:U2` thuộc `cmp-extra`.
    - `ans:A2` thuộc `cmp-missed`.
    - `ans:A1` thuộc `cmp-answer`.
    - `factions` bằng `COMPARE_FACTIONS`. `phases` là `undefined`.
  - `buildCompareMap_exactMatchIsCorrect`: U1 ở (50,50) thì `you:U1` thuộc `cmp-correct`.
  - `buildCompareMap_keepsLabelsAsAnswerContext`: một `label` của answer có trong kết quả với id `ans:{id}` và faction `cmp-answer`.
  - `compareFactions_validColours`: mọi `color` khớp `/^#[0-9a-f]{6}$/i`.

- [ ] **Step 2: Chạy test.** `bun test src/components/historical-map/battle-map-practice.test.ts`. Kết quả mong đợi: FAIL (thiếu module).

- [ ] **Step 3: Cài đặt `HM/battle-map-practice.ts`** theo Interfaces.

- [ ] **Step 4: Chạy lại test ở Step 2.** Kết quả mong đợi: PASS.

- [ ] **Step 5: Commit.** `git commit -m "feat(map-practice): practice sheet and compare map builders"`

---

### Task 3: Chế độ luyện tập của `BattleMapEditor`

**Files:**
- Modify: `HM/BattleMapEditor.tsx`

**Interfaces:**
- Consumes: không có gì mới.
- Produces: `BattleMapEditor` nhận thêm `mode?: "author" | "practice"` (mặc định `"author"`). Ở `"practice"`:
  - `onSave(map)` được gọi khi bấm nút **"So sánh"** thay cho nút lưu. Promise reject thì editor giữ nguyên, không xóa nháp.
  - Chỉ hiện tab `"symbols"`. Ẩn tab `"image"`, `"factions"`, `"phases"` và ô chọn giai đoạn của ký hiệu.
  - Không cho thêm, sửa hay xóa phe. Vẫn được chọn phe đang vẽ (gồm cả phím tắt `q`).
  - Nút **"So sánh"** bật khi có ít nhất 1 ký hiệu, không cần `dirty`.
  - **Không** xóa nháp localStorage sau khi `onSave` thành công, để người học bấm "Vẽ tiếp" vẫn còn bài.
  - Bỏ qua `legacyNarration`.

- [ ] **Step 1: Thêm prop `mode`.** Lọc `TABS` theo mode. Khóa các thao tác sửa phe khi `mode === "practice"`. Thay `canSave` bằng `mode === "practice" ? value.symbols.length > 0 : dirty && !!value.imageUrl.trim()`. Đổi nhãn nút lưu. Bỏ bước xóa nháp sau khi lưu khi đang ở chế độ practice.

- [ ] **Step 2: Kiểm tra.** `bunx tsc --noEmit` và `bun run lint` không báo lỗi mới. Mở editor ở chế độ `author` (Content Admin, `/staff/map`, "Soạn lược đồ"): mọi tab và nút "Lưu" vẫn như cũ.

- [ ] **Step 3: Commit.** `git commit -m "feat(map-practice): practice mode for the battle map editor"`

---

### Task 4: Màn kết quả và nối luồng vào trang trận đánh

**Files:**
- Create: `HM/BattleMapCompareView.tsx`
- Modify: `HM/BattleDetailView.tsx`, `HM/HistoricalMapModal.tsx:67` và nơi gọi `openScreen`, `HM/UrlSyncedHistoricalMap.tsx:29`

**Interfaces:**
- Consumes:
  - Task 1: `isGradableMap`, `scoreBattleMap`, `BattleMapScore`.
  - Task 2: `toPracticeMap`, `buildCompareMap`, `ANSWER_PREFIX`.
  - Task 3: `BattleMapEditor` với `mode="practice"`.
- Produces:
  ```ts
  // HistoricalMapModal.tsx
  export type MapRoute = { battle: string | null; view: "map" | "detail" | "edit" | "practice" };
  // BattleMapCompareView.tsx
  export function BattleMapCompareView(props: {
    title: string;                 // event title
    answer: BattleMap; attempt: BattleMap; score: BattleMapScore;
    onContinue: () => void;        // "Vẽ tiếp"
    onRestart: () => void;         // "Làm lại"
    onClose: () => void;           // "Đóng"
  }): JSX.Element;
  ```
  `BattleDetailView` nhận thêm `practicing?: boolean`, `onOpenPractice: () => void`, `onClosePractice: () => void`.

- [ ] **Step 1: Route.**
  - Thêm `"practice"` vào `MapRoute.view`.
  - `UrlSyncedHistoricalMap` chấp nhận `view=practice`.
  - `HistoricalMapModal` truyền `practicing={route.view === "practice"}`, `onOpenPractice` (đi tới `view: "practice"`, kiểu `"push"`) và `onClosePractice` (quay lại `view: "detail"`, kiểu `"back"`).
  - Mọi vai trò mở được `practice`. Không kiểm tra quyền sửa.

- [ ] **Step 2: Nút vào.**
  - Ở `BattleDetailView`, cạnh `BattleMapCanvas` (dòng 189), hiện nút **"Tự vẽ lược đồ"** khi `isGradableMap(battleMap)` đúng và người dùng không đang ở chế độ soạn của admin.
  - Bấm nút thì gọi `onOpenPractice`.

- [ ] **Step 3: Editor luyện tập.**
  - Khi `practicing` đúng, render qua `createPortal` (như editor admin ở dòng 136):
    ```tsx
    <BattleMapEditor mode="practice" initialValue={toPracticeMap(battleMap)} draftId={`practice:${event.id}`}
      title={`Tự vẽ: ${event.title}`} onClose={onClosePractice} onSave={compare} />
    ```
  - Hàm `compare(map)` làm hai việc:
    1. Đọc `aspect` từ ảnh: `new Image()`, đặt `src = battleMap.imageUrl`, `naturalWidth / naturalHeight`. Lỗi hoặc 0 thì dùng `1`.
    2. `setResult({ attempt: map, score: scoreBattleMap(battleMap, map, aspect) })`.

- [ ] **Step 4: `BattleMapCompareView`.** Hiển thị khi đã có `result`, đè lên editor.
  - **Tiêu đề** "Kết quả so sánh".
  - **Số `percent`** cỡ lớn, kèm "% chính xác".
  - **Ba con số:** "Đúng" (số cặp có score ≥ 0.8), "Thiếu" (`missedIds.length`), "Thừa / sai" (`extraIds.length`).
  - **Lược đồ so sánh:** `<BattleMapCanvas value={buildCompareMap(answer, attempt, score)} title=… legendTitle="Chú giải so sánh" symbolView={s => ({ dim: s.factionId === "cmp-answer" })} />`. Legend của canvas sẽ tự liệt kê 5 "phe" màu.
  - **Các nút:**
    - "Vẽ tiếp": đóng màn kết quả, giữ nguyên editor và bài vẽ.
    - "Làm lại": xóa nháp localStorage (khóa `historytalk:battle-map-draft:v2:{uid}:practice:{eventId}`), rồi mở lại editor với `toPracticeMap(battleMap)`. Gợi ý: đổi `key` của editor để nó khởi tạo lại.
    - "Đóng": gọi `onClosePractice`.

- [ ] **Step 5: Kiểm tra.**
  - `bun test src/components/historical-map`: PASS.
  - `bunx tsc --noEmit` và `bun run lint`: không lỗi mới.

  Kiểm tra tay với tài khoản CUSTOMER, trên một trận có lược đồ `custom`:
  1. Vào `/map`, mở trận, thấy nút "Tự vẽ lược đồ". Trận có lược đồ `image` thì **không** có nút này.
  2. Vẽ lại đúng vài ký hiệu, bấm "So sánh": thấy % hợp lý, ký hiệu đúng màu xanh, ký hiệu thiếu màu đỏ.
  3. Bấm "Vẽ tiếp", vẽ thêm, bấm "So sánh" lại: điểm thay đổi.
  4. Tải lại trang (URL có `view=practice`): bài vẽ vẫn còn nhờ nháp.
  5. Bấm "Làm lại": bản vẽ trống.
  6. Bấm "Đóng": quay về trang trận đánh.
  7. Đăng nhập Content Admin, kiểm tra "Soạn lược đồ" vẫn lưu lên server như cũ.

- [ ] **Step 6: Commit.** `git commit -m "feat(map-practice): let learners draw a battle map and compare it with the admin map"`

---

## Ngoài phạm vi (làm sau nếu cần)

- **Lưu điểm hoặc lịch sử:** chỉ cần gửi `percent` lên BE, hàm chấm giữ nguyên.
- **Giao làm bài tập có tính điểm:** cần chuyển phần chấm sang BE và không gửi `battleMap` cho học sinh khi đang làm bài. Hiện đáp án nằm sẵn trong response `GET /historical-contexts/{id}`.
- **Chấm theo giai đoạn (`phaseId`)**, chấm hướng xoay của ký hiệu dạng điểm, và dùng thuật toán Hungarian thay cho ghép tham lam.
