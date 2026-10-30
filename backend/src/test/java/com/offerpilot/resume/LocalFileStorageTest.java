package com.offerpilot.resume;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.resume.storage.LocalFileStorage;
import com.offerpilot.resume.storage.StoredFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndDeletesFile() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "hello".getBytes());

        StoredFile stored = storage.store(file, 7L);

        assertThat(stored.originalName()).isEqualTo("resume.pdf");
        assertThat(stored.size()).isEqualTo(5);
        assertThat(storage.resolve(stored.relativePath())).exists();

        storage.delete(stored.relativePath());
        assertThat(Files.exists(storage.resolve(stored.relativePath()))).isFalse();
    }

    @Test
    void rejectsUnsupportedExtension() {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> storage.store(file, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void rejectsOversizedFile() {
        LocalFileStorage storage = new LocalFileStorage(tempDir, 2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "hello".getBytes());

        assertThatThrownBy(() -> storage.store(file, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历文件过大");
    }
}
