package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.exception.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempDir.toString());
    }

    @Test
    void store_savesFileAndReturnsFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "pdf content".getBytes()
        );

        String filename = fileStorageService.store(file);

        assertThat(filename).contains("resume.pdf");
        assertThat(filename).doesNotContain("/");
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_withNullOriginalFilename_usesDefaultName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/pdf", "content".getBytes()
        );

        String filename = fileStorageService.store(file);

        assertThat(filename).isNotNull();
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_withEmptyFile_stillStoresIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        String filename = fileStorageService.store(file);

        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void delete_removesFileFromDisk() throws IOException {
        String filename = "to-delete.pdf";
        Files.write(tempDir.resolve(filename), "content".getBytes());

        fileStorageService.delete(filename);

        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void delete_nonExistentFile_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> fileStorageService.delete("ghost.pdf"));
    }

    @Test
    void resolveAbsolutePath_returnsFullPathForFilename() {
        String filename = "uuid_report.pdf";

        String resolved = fileStorageService.resolveAbsolutePath(filename);

        assertThat(resolved).isEqualTo(tempDir.resolve(filename).toString());
    }

    @Test
    void constructor_throwsFileStorageException_whenDirectoryCannotBeCreated() {
        String invalidPath = "/root/no-permission-dir/uploads";

        assertThatThrownBy(() -> new FileStorageService(invalidPath))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Could not create upload directory");
    }
}
