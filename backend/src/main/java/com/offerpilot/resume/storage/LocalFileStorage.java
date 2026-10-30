package com.offerpilot.resume.storage;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final long maxSize;

    public LocalFileStorage(Path root, long maxSize) {
        this.root = root.toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    @Override
    public StoredFile store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择要上传的简历文件");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "简历文件过大");
        }

        String originalName = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 PDF 或 DOCX 简历");
        }

        String extension = lower.substring(lower.lastIndexOf('.'));
        String relativePath = userId + "/" + UUID.randomUUID() + extension;

        try {
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的文件路径");
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return new StoredFile(relativePath, originalName, file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "简历保存失败");
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法的文件路径");
        }
        return path;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ignored) {
            // 删除失败不影响主流程
        }
    }
}
