# Plan: Update Map Pin API (Partial Update)

## 1. Objective
Create a new API endpoint to allow partial updates of an existing Map Pin. This will fix the issue where the frontend currently has to delete and recreate pins to update descriptions, which causes data loss on errors.

## 2. API Specification
**Endpoint:** `PUT /api/v1/historical-contexts/{contextId}/map-pins/{pinId}`

**Request Body (`UpdateMapPinRequest`):**
Fields are optional. If a field is omitted, the existing value remains unchanged.
- `description` (String): Max 5000 characters. If an empty string `""` is sent, it will clear the description (saved as `null` in the DB).
- `label` (String): Max 200 characters. If provided, must not be blank.
- `latitude` (Double): WGS-84, range [-90, 90].
- `longitude` (Double): WGS-84, range [-180, 180].
- `pinYear` (Integer): The year the pin belongs to.
- `pathGeoJson` (Object): GeoJSON Object. If provided, it will be validated to ensure `type="LineString"`.

**Authorization Rules:**
- Both Admin and regular Users can call this endpoint (requires Bearer JWT).
- **Admin**: Can only update `ADMIN` pins on this context.
- **User**: Can only update their own `USER` pins.
- Mismatching ownership returns `404 Not Found` to prevent information leakage.

## 3. Files to Change / Create

### 3.1. `src/main/java/com/historytalk/dto/map/UpdateMapPinRequest.java` (New File)
Create a new DTO class to handle the request body. All fields are optional.
- Use `@Size(max = 200)` for `label` and `@Size(max = 5000)` for `description`.
- **[Gap 2 Fix]** Use `@DecimalMin`/`@DecimalMax` (NOT `@Min`/`@Max`) for `latitude` and `longitude`,
  because those fields are `Double`, and `@Min`/`@Max` only work on integer types:
  - `latitude`: `@DecimalMin("-90.0")` / `@DecimalMax("90.0")`
  - `longitude`: `@DecimalMin("-180.0")` / `@DecimalMax("180.0")`

### 3.2. `src/main/java/com/historytalk/controller/map/MapPinController.java` (Modify)
Add a new `updatePin` method mapped to `@PutMapping("/{pinId}")`.
- Requires authentication (`@SecurityRequirement(name = "bearerAuth")`).
- Pass `contextId`, `pinId`, `request`, `callerId`, and `role` to the service layer.
- Include appropriate Swagger documentation (`@Operation`).

### 3.3. `src/main/java/com/historytalk/service/map/MapPinService.java` (Modify)
Add the `updatePin` interface method signature:
```java
MapPinResponse updatePin(String contextId, String pinId, UpdateMapPinRequest request, String callerId, String role);
```

### 3.4. `src/main/java/com/historytalk/service/map/MapPinServiceImpl.java` (Modify)
Implement the partial update logic in `updatePin`:
- **[Gap 3 Fix]** Annotate with `@Transactional` (same as `createPin` and `deletePin`) to guarantee the fetch + mutate + save is one atomic DB operation.
- Validate `contextId` and `pinId` UUID formats using the existing `parseUuid` helper.
- Fetch the `MapPin` via `findByPinIdAndDeletedAtIsNull` and ensure it belongs to the given `contextId`.
- Validate ownership (same logic as `deletePin`): Admins update `ADMIN` pins, Users update their own `USER` pins; both return `404` on mismatch.
- Apply updates from the request only for fields that are non-null (partial update semantics).
- Special handling for `description`: if `request.getDescription() != null` and is empty `""`, set entity field to `null`; otherwise set to the trimmed value.
- If `label` is provided, trim and validate it is not blank.
- Re-use the existing `serializePathGeoJson` helper if `pathGeoJson` is provided.
- Save the entity and return the mapped `MapPinResponse`.

## 4. No Repository Changes Needed
The existing `findByPinIdAndDeletedAtIsNull(UUID pinId)` in `MapPinRepository` is sufficient for the pin lookup in the update flow.
