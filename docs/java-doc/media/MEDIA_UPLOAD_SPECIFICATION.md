# 🖼️ HistoryTalk Java Backend - Polymorphic Media Upload Specification

This specification documents the Polymorphic Media Attachment System (`media_metadata` table) supporting 2D images, 3D GLTF/GLB models, audio clips, and PDF documents attached to Characters, Contexts, and RAG Documents.

---

## 1. Polymorphic Architecture (`media_metadata`)

Instead of storing hardcoded URLs inside entity tables, media files are stored as polymorphic records referencing an `entity_type` (`CHARACTER` | `CONTEXT` | `DOCUMENT`) and an `entity_id`:

```sql
CREATE TABLE IF NOT EXISTS historical_schema.media_metadata (
    media_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50) NOT NULL, -- CHARACTER | CONTEXT | DOCUMENT
    entity_id       UUID NOT NULL,
    media_type      VARCHAR(50) NOT NULL, -- IMAGE | AUDIO | VIDEO | PDF | MODEL_3D
    media_url       VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255),
    mime_type       VARCHAR(100),
    file_size       BIGINT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);
```

---

## 2. File Validation & Size Thresholds

| Media Category | Allowed Extensions | Max File Size | Presigned Storage Expiry |
|---|---|---|---|
| **2D Images** | `.jpg`, `.jpeg`, `.png`, `.webp`, `.gif` | 10 MB | 300 seconds |
| **3D Models** | `.glb`, `.gltf`, `.obj`, `.fbx` | 100 MB | 300 seconds |
| **Audio / Video** | `.mp3`, `.wav`, `.mp4` | 50 MB | 300 seconds |
| **PDF / Text** | `.pdf`, `.txt`, `.md` | 50 MB | 300 seconds |

---

## 3. Media API Endpoints Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `POST` | `/api/v1/media/upload` | `CONTENT_ADMIN` | Upload media file directly to Supabase storage and save `media_metadata`. |
| `GET` | `/api/v1/media/entities/{entityType}/{entityId}` | Public | List all attached media files for a specific character or context. |
| `DELETE` | `/api/v1/media/{mediaId}` | `CONTENT_ADMIN` | Soft-delete a media attachment. |
