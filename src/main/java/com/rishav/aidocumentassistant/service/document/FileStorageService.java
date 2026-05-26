package com.rishav.aidocumentassistant.service.document;

import com.rishav.aidocumentassistant.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;

    public FileStorageService(@Value("${storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory: " + this.uploadDir, e);
        }
    }

    public String store(MultipartFile file) {
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload"
        );
        String storedName = UUID.randomUUID() + "_" + originalName;
        Path target = uploadDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileStorageException("Could not store file: " + originalName, e);
        }
        return target.toString();
    }

    public void delete(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not delete file: " + filePath, e);
        }
    }
}
