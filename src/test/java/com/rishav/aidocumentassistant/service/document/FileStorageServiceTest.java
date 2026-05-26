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
    void store_savesFileAndReturnsAbsolutePath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "pdf content".getBytes()
        );

        String path = fileStorageService.store(file);

        assertThat(path).contains("resume.pdf");
        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    @Test
    void store_withNullOriginalFilename_usesDefaultName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/pdf", "content".getBytes()
        );

        String path = fileStorageService.store(file);

        assertThat(path).isNotNull();
        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    @Test
    void store_withEmptyFile_stillStoresIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        String path = fileStorageService.store(file);

        assertThat(Files.exists(Path.of(path))).isTrue();
    }

    @Test
    void delete_removesFileFromDisk() throws IOException {
        Path file = tempDir.resolve("to-delete.pdf");
        Files.write(file, "content".getBytes());

        fileStorageService.delete(file.toString());

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void delete_nonExistentFile_doesNotThrow() {
        String nonExistent = tempDir.resolve("ghost.pdf").toString();

        assertThatNoException().isThrownBy(() -> fileStorageService.delete(nonExistent));
    }

    @Test
    void constructor_throwsFileStorageException_whenDirectoryCannotBeCreated() {
        String invalidPath = "/root/no-permission-dir/uploads";

        assertThatThrownBy(() -> new FileStorageService(invalidPath))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("Could not create upload directory");
    }
}
