package com.historytalk.service.document;

import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseDocumentStorageServiceTest {

    @Test
    void uploadPdfStoresPdfAtEntityScopedDocumentPath() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                builder);
        UUID entityId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "pdf-bytes".getBytes());

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/documents/documents/context/" + entityId + "/" + docId + ".pdf"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(header("x-upsert", "true"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        UploadedDocumentFile uploaded = service.uploadPdf(EntityType.CONTEXT, entityId, docId, file);

        assertThat(uploaded.objectPath()).isEqualTo("documents/context/" + entityId + "/" + docId + ".pdf");
        server.verify();
    }

    @Test
    void uploadPdfRejectsNonPdfExtension() {
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                RestClient.builder());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.txt",
                "application/pdf",
                "pdf-bytes".getBytes());

        assertThatThrownBy(() -> service.uploadPdf(EntityType.CONTEXT, UUID.randomUUID(), UUID.randomUUID(), file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only .pdf files are accepted");
    }

    @Test
    void uploadPdfRejectsEmptyFile() {
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                RestClient.builder());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                new byte[0]);

        assertThatThrownBy(() -> service.uploadPdf(EntityType.CONTEXT, UUID.randomUUID(), UUID.randomUUID(), file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("PDF file must not be empty");
    }

    @Test
    void downloadPdfReturnsBytesFromStoredObjectPath() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                builder);
        String objectPath = "documents/context/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".pdf";

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/documents/" + objectPath))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andExpect(header("apikey", "service-role-key"))
                .andRespond(withSuccess("pdf-bytes".getBytes(), MediaType.APPLICATION_PDF));

        DownloadedDocumentFile downloaded = service.downloadPdf(objectPath);

        assertThat(downloaded.bytes()).isEqualTo("pdf-bytes".getBytes());
        assertThat(downloaded.contentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        server.verify();
    }

    @Test
    void downloadPdfRejectsEmptyDownloadedFile() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                builder);
        String objectPath = "documents/context/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".pdf";

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/documents/" + objectPath))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_PDF));

        assertThatThrownBy(() -> service.downloadPdf(objectPath))
                .isInstanceOf(SystemException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void createSignedPdfUrlReturnsSignedUrlFromSupabase() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                builder);
        String objectPath = "documents/context/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".pdf";

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/sign/documents/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andExpect(header("apikey", "service-role-key"))
                .andRespond(withSuccess("{\"signedURL\":\"/storage/v1/object/sign/documents/" + objectPath + "?token=abc\"}", MediaType.APPLICATION_JSON));

        DocumentPdfUrl signedUrl = service.createSignedPdfUrl(objectPath, 300, "document.pdf");

        assertThat(signedUrl.url()).isEqualTo("https://example.supabase.co/storage/v1/object/sign/documents/" + objectPath + "?token=abc&download=document.pdf");
        assertThat(signedUrl.expiresIn()).isEqualTo(300);
        server.verify();
    }

    @Test
    void createSignedPdfUrlNormalizesSupabaseObjectRelativePath() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseDocumentStorageService service = new SupabaseDocumentStorageService(
                "https://example.supabase.co",
                "service-role-key",
                "documents",
                builder);
        String objectPath = "documents/context/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".pdf";

        server.expect(requestTo("https://example.supabase.co/storage/v1/object/sign/documents/" + objectPath))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"signedURL\":\"/object/sign/documents/" + objectPath + "?token=abc\"}", MediaType.APPLICATION_JSON));

        DocumentPdfUrl signedUrl = service.createSignedPdfUrl(objectPath, 300, "my source.pdf");

        assertThat(signedUrl.url()).isEqualTo("https://example.supabase.co/storage/v1/object/sign/documents/" + objectPath + "?token=abc&download=my+source.pdf");
        server.verify();
    }
}
