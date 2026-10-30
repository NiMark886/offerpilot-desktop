package com.offerpilot.resume.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorage {

    StoredFile store(MultipartFile file, Long userId);

    Path resolve(String relativePath);

    void delete(String relativePath);
}
