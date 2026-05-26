package com.rishav.aidocumentassistant.integration;

import com.rishav.aidocumentassistant.model.DocumentStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.openai.api-key=test",
                "spring.ai.anthropic.api-key=test"
        }
)
@Testcontainers
class DocumentPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return new FakeEmbeddingModel();
        }
    }

    static class FakeEmbeddingModel implements EmbeddingModel {
        private static final int DIMS = 1536;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = IntStream.range(0, request.getInstructions().size())
                    .mapToObj(i -> new Embedding(fixedVector(), i))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return fixedVector();
        }

        private float[] fixedVector() {
            float[] v = new float[DIMS];
            Arrays.fill(v, 0.1f);
            return v;
        }
    }

    @Test
    void uploadDocument_ingestsSuccessfully_andIsSearchable() throws Exception {
        // 1. Create a minimal PDF with extractable text
        Path tmpFile = Files.createTempFile("integration-test-", ".pdf");
        createTestPdf(tmpFile, "Testcontainers makes integration testing easy. " +
                "It spins up real Docker containers for databases and services. " +
                "This document is used to verify the end-to-end ingestion pipeline.");

        // 2. Upload the document (Content-Type header on the file part must be application/pdf)
        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.APPLICATION_PDF);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new org.springframework.core.io.FileSystemResource(tmpFile), filePartHeaders));
        body.add("name", "Integration Test Doc");

        ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
                "/api/documents", new HttpEntity<>(body, headers), Map.class);

        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String docId = (String) uploadResponse.getBody().get("id");
        assertThat(docId).isNotNull();

        // 3. Poll until status is READY (async ingestion, up to 30s)
        String status = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                    "/api/documents/" + docId, Map.class);
            status = (String) statusResponse.getBody().get("status");
            if (DocumentStatus.READY.name().equals(status) || DocumentStatus.FAILED.name().equals(status)) break;
        }
        assertThat(status).isEqualTo(DocumentStatus.READY.name());

        // 4. Run a semantic search — should return the ingested chunk
        HttpHeaders searchHeaders = new HttpHeaders();
        searchHeaders.setContentType(MediaType.APPLICATION_JSON);
        String searchBody = """
                {"query": "integration testing with Docker containers", "topK": 3}
                """;
        ResponseEntity<List> searchResponse = restTemplate.exchange(
                "/api/search", HttpMethod.POST,
                new HttpEntity<>(searchBody, searchHeaders), List.class);

        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).isNotEmpty();

        Files.deleteIfExists(tmpFile);
    }

    @Test
    void upload_rejects_unsupportedFileType() throws Exception {
        Path tmpFile = Files.createTempFile("bad-file-", ".csv");
        Files.writeString(tmpFile, "col1,col2\nval1,val2");

        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.TEXT_PLAIN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new org.springframework.core.io.FileSystemResource(tmpFile), filePartHeaders));
        body.add("name", "CSV File");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/documents", new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat((String) response.getBody().get("code")).isEqualTo("UNSUPPORTED_FILE_TYPE");

        Files.deleteIfExists(tmpFile);
    }

    private void createTestPdf(Path path, String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(path.toFile());
        }
    }
}
