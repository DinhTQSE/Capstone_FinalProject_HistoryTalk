package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.media.DirectBinaryUploadStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentMediaServiceImplTest {

    @Mock
    private DocumentMediaMetadataRepository mediaMetadataRepository;

    @Mock
    private HistoricalContextRepository historicalContextRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Mock
    private DirectBinaryUploadStrategy directBinaryUploadStrategy;

    @Mock
    private Environment environment;

    @InjectMocks
    private DocumentMediaServiceImpl mediaService;

    private School school;
    private User teacher;
    private HistoricalContext localContext;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("THPT A").build();
        teacher = User.builder().uid(UUID.randomUUID()).userName("teacher").role(UserRole.TEACHER).school(school).build();
        localContext = HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .name("Khoi nghia Hai Ba Trung")
                .school(school)
                .createdBy(teacher)
                .build();

        when(environment.getProperty("supabase.url")).thenReturn("https://example.supabase.co");
        when(environment.getProperty("supabase.storage.bucket", "documents")).thenReturn("documents");
        when(userRepository.findById(teacher.getUid())).thenReturn(Optional.of(teacher));
        when(historicalContextRepository.findById(localContext.getContextId())).thenReturn(Optional.of(localContext));
        when(mediaMetadataRepository.save(any(DocumentMediaMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MultipartFile video(String name, String contentType) {
        return new MockMultipartFile("file", name, contentType, new byte[]{1, 2, 3});
    }

    @Test
    void teacherUploadsVideoToOwnLocalContext_setsVideoUrl() throws Exception {
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(
                EntityType.CONTEXT, localContext.getContextId(), MediaType.VIDEO)).thenReturn(List.of());

        DocumentMediaMetadataResponse res = mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("intro.mp4", "video/mp4"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER");

        assertThat(res.getMediaType()).isEqualTo("VIDEO");
        assertThat(res.getPublicUrl()).startsWith("https://example.supabase.co/storage/v1/object/public/documents/CONTEXT/");
        assertThat(localContext.getVideoUrl()).isEqualTo(res.getPublicUrl());
        verify(directBinaryUploadStrategy).uploadMultipartFile(anyString(), any(), eq("video/mp4"), eq("VIDEO"));
    }

    @Test
    void videoWithOctetStreamContentType_isResolvedFromExtension() throws Exception {
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(any(), any(), any())).thenReturn(List.of());

        mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("clip.webm", "application/octet-stream"),
                "video", null, null, teacher.getUid().toString(), "TEACHER");

        verify(directBinaryUploadStrategy).uploadMultipartFile(anyString(), any(), eq("video/webm"), eq("VIDEO"));
    }

    @Test
    void videoUpload_rejectsNonVideoFile() {
        assertThatThrownBy(() -> mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("doc.pdf", "application/pdf"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void videoUpload_rejectedForCharacter() {
        assertThatThrownBy(() -> mediaService.uploadDirectBinary(
                UUID.randomUUID(), EntityType.CHARACTER, video("intro.mp4", "video/mp4"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("bối cảnh");
    }

    @Test
    void videoUpload_rejectsFileOver50MB() {
        MultipartFile big = new MockMultipartFile("file", "big.mp4", "video/mp4", new byte[0]) {
            @Override
            public long getSize() {
                return 51L * 1024 * 1024;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };

        assertThatThrownBy(() -> mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, big,
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("50MB");
    }

    @Test
    void teacherCannotUploadMediaOntoPlatformContext() throws Exception {
        HistoricalContext platform = HistoricalContext.builder().contextId(UUID.randomUUID()).name("Bach Dang").build();
        when(historicalContextRepository.findById(platform.getContextId())).thenReturn(Optional.of(platform));

        assertThatThrownBy(() -> mediaService.uploadDirectBinary(
                platform.getContextId(), EntityType.CONTEXT, video("a.png", "image/png"),
                "IMAGE_2D", null, null, teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(UnauthorizedException.class);
        verify(directBinaryUploadStrategy, never()).uploadMultipartFile(anyString(), any(), anyString(), anyString());
    }

    @Test
    void teacherCannotUploadMediaOntoAnotherTeachersContext() {
        User otherTeacher = User.builder().uid(UUID.randomUUID()).role(UserRole.TEACHER).school(school).build();
        localContext.setCreatedBy(otherTeacher);

        assertThatThrownBy(() -> mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("intro.mp4", "video/mp4"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void teacherOfAnotherSchoolCannotDeleteMedia() {
        School otherSchool = School.builder().id(UUID.randomUUID()).build();
        User outsider = User.builder().uid(UUID.randomUUID()).role(UserRole.TEACHER).school(otherSchool).build();
        when(userRepository.findById(outsider.getUid())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> mediaService.deleteMedia(
                localContext.getContextId(), EntityType.CONTEXT, "VIDEO", outsider.getUid().toString(), "TEACHER"))
                .isInstanceOf(UnauthorizedException.class);
        verify(mediaMetadataRepository, never()).delete(any());
    }

    @Test
    void schoolAdminCanUploadOntoAnyLocalContextOfSchool() {
        User schoolAdmin = User.builder().uid(UUID.randomUUID()).role(UserRole.SCHOOL_ADMIN).school(school).build();
        when(userRepository.findById(schoolAdmin.getUid())).thenReturn(Optional.of(schoolAdmin));
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(any(), any(), any())).thenReturn(List.of());

        DocumentMediaMetadataResponse res = mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("cover.png", "image/png"),
                "IMAGE_2D", null, null, schoolAdmin.getUid().toString(), "SCHOOL_ADMIN");

        assertThat(localContext.getImageUrl()).isEqualTo(res.getPublicUrl());
    }

    @Test
    void reUploadSameMediaType_replacesPreviousFile() {
        DocumentMediaMetadata old = DocumentMediaMetadata.builder()
                .metadataId(UUID.randomUUID())
                .entityType(EntityType.CONTEXT)
                .entityId(localContext.getContextId())
                .mediaType(MediaType.VIDEO)
                .storagePath("CONTEXT/" + localContext.getContextId() + "/old_intro.mp4")
                .build();
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(
                EntityType.CONTEXT, localContext.getContextId(), MediaType.VIDEO)).thenReturn(List.of(old));

        mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("intro.mp4", "video/mp4"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER");

        verify(supabaseDocumentStorageService).deleteFile(old.getStoragePath());
        verify(mediaMetadataRepository).delete(old);
    }

    @Test
    void storagePath_isSanitizedAgainstTraversal() throws Exception {
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(any(), any(), any())).thenReturn(List.of());

        DocumentMediaMetadataResponse res = mediaService.uploadDirectBinary(
                localContext.getContextId(), EntityType.CONTEXT, video("../../etc/bài giảng.mp4", "video/mp4"),
                "VIDEO", null, null, teacher.getUid().toString(), "TEACHER");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(directBinaryUploadStrategy).uploadMultipartFile(path.capture(), any(), anyString(), anyString());
        assertThat(path.getValue())
                .startsWith("CONTEXT/" + localContext.getContextId() + "/")
                .doesNotContain("..")
                .doesNotContain(" ")
                .endsWith(".mp4");
        assertThat(res.getStoragePath()).isEqualTo(path.getValue());
    }

    @Test
    void typedDelete_clearsOnlyVideoUrl_evenWithoutMetadata() {
        localContext.setImageUrl("https://img");
        localContext.setVideoUrl("https://youtube.com/watch?v=x");
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(
                EntityType.CONTEXT, localContext.getContextId(), MediaType.VIDEO)).thenReturn(List.of());

        mediaService.deleteMedia(localContext.getContextId(), EntityType.CONTEXT, "VIDEO",
                teacher.getUid().toString(), "TEACHER");

        assertThat(localContext.getVideoUrl()).isNull();
        assertThat(localContext.getImageUrl()).isEqualTo("https://img");
    }

    @Test
    void untypedDelete_withoutAnyMedia_throwsNotFound() {
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityId(EntityType.CONTEXT, localContext.getContextId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> mediaService.deleteMedia(localContext.getContextId(), EntityType.CONTEXT,
                teacher.getUid().toString(), "TEACHER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void viewUrl_ofLocalContext_hiddenFromOtherSchool() {
        User outsider = User.builder().uid(UUID.randomUUID()).role(UserRole.CUSTOMER).build();
        when(userRepository.findById(outsider.getUid())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> mediaService.generateSignedViewUrl(localContext.getContextId(), EntityType.CONTEXT,
                null, null, outsider.getUid().toString(), "CUSTOMER"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(supabaseDocumentStorageService, never()).createSignedUrl(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void viewUrl_withMediaType_picksThatMedia() {
        DocumentMediaMetadata videoMeta = DocumentMediaMetadata.builder()
                .entityType(EntityType.CONTEXT).entityId(localContext.getContextId())
                .mediaType(MediaType.VIDEO).storagePath("CONTEXT/x/video.mp4").build();
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(
                EntityType.CONTEXT, localContext.getContextId(), MediaType.VIDEO)).thenReturn(List.of(videoMeta));
        when(supabaseDocumentStorageService.createSignedUrl("CONTEXT/x/video.mp4", 3600)).thenReturn("https://signed");

        var res = mediaService.generateSignedViewUrl(localContext.getContextId(), EntityType.CONTEXT, "VIDEO",
                null, null, teacher.getUid().toString(), "TEACHER");

        assertThat(res.getViewUrl()).isEqualTo("https://signed");
    }

    @Test
    void platformAdminCanUploadModelForPlatformCharacter() {
        Character platformChar = Character.builder().characterId(UUID.randomUUID()).name("Tran Hung Dao").build();
        when(characterRepository.findById(platformChar.getCharacterId())).thenReturn(Optional.of(platformChar));
        when(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(any(), any(), any())).thenReturn(List.of());

        mediaService.uploadDirectBinary(platformChar.getCharacterId(), EntityType.CHARACTER,
                video("model.glb", "model/gltf-binary"), "MODEL_3D", null, null,
                UUID.randomUUID().toString(), "CONTENT_ADMIN");

        assertThat(platformChar.getModelUrl()).isNotNull();
    }
}
