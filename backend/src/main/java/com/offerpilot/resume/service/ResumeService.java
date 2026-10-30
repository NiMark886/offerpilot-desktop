package com.offerpilot.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.resume.domain.Resume;
import com.offerpilot.resume.domain.ResumeProfile;
import com.offerpilot.resume.mapper.ResumeMapper;
import com.offerpilot.resume.storage.FileStorage;
import com.offerpilot.resume.storage.StoredFile;
import com.offerpilot.resume.web.dto.ResumeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeMapper resumeMapper;
    private final FileStorage fileStorage;
    private final ResumeTextExtractor textExtractor;
    private final ResumeProfileService profileService;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeMapper resumeMapper,
                         FileStorage fileStorage,
                         ResumeTextExtractor textExtractor,
                         ResumeProfileService profileService,
                         ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResumeView upload(Long userId, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历文件读取失败");
        }

        StoredFile stored = fileStorage.store(file, userId);

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(stored.originalName());
        resume.setFilePath(stored.relativePath());
        resume.setContentType(stored.contentType());
        resume.setFileSize(stored.size());

        ResumeProfile profile = null;
        try {
            String rawText = textExtractor.extract(content, stored.originalName());
            resume.setRawText(rawText);
            profile = profileService.parse(rawText);
            resume.setParsedJson(objectMapper.writeValueAsString(profile));
            resume.setParseStatus("PARSED");
        } catch (Exception ex) {
            log.warn("Resume parsing failed for {}", stored.originalName(), ex);
            resume.setParseStatus("FAILED");
        }

        resumeMapper.insert(resume);
        return ResumeView.from(resume, profile);
    }

    public List<ResumeView> list(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getCreatedAt))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ResumeView get(Long userId, Long resumeId) {
        return toView(loadOwned(userId, resumeId));
    }

    public void delete(Long userId, Long resumeId) {
        loadOwned(userId, resumeId);
        resumeMapper.deleteById(resumeId);
    }

    public Resume loadOwned(Long userId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历不存在");
        }
        return resume;
    }

    private ResumeView toView(Resume resume) {
        ResumeProfile profile = null;
        if (resume.getParsedJson() != null) {
            try {
                profile = objectMapper.readValue(resume.getParsedJson(), ResumeProfile.class);
            } catch (Exception ex) {
                log.warn("Failed to parse stored resume profile {}", resume.getId(), ex);
            }
        }
        return ResumeView.from(resume, profile);
    }
}
