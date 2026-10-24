package com.historytalk.service.media;

import com.historytalk.exception.InvalidRequestException;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoUploadServiceImplTest {

    @Mock
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

    @InjectMocks
    private VideoUploadServiceImpl videoUploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(videoUploadService, "supabaseUrl", "https://example.supabase.co/");
        ReflectionTestUtils.setField(videoUploadService, "bucket", "documents");
    }

    @Test
    void uploadVideo_returnsPublicUrlUnderUserFolder() throws Exception {
        String userId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile("file", "Bai giang.MOV", "video/quicktime", new byte[]{1});

        String url = videoUploadService.uploadVideo(file, userId);

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(supabaseDocumentStorageService).uploadFile(path.capture(), any(), eq("video/quicktime"), eq(1L));
        assertThat(path.getValue()).startsWith("videos/" + userId + "/").endsWith(".mov");
        assertThat(url).isEqualTo("https://example.supabase.co/storage/v1/object/public/documents/" + path.getValue());
    }

    @Test
    void uploadVideo_rejectsImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> videoUploadService.uploadVideo(file, UUID.randomUUID().toString()))
                .isInstanceOf(InvalidRequestException.class);
        verify(supabaseDocumentStorageService, never()).uploadFile(anyString(), any(), anyString(), anyLong());
    }

    @Test
    void uploadVideo_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> videoUploadService.uploadVideo(file, UUID.randomUUID().toString()))
                .isInstanceOf(InvalidRequestException.class);
    }
}
