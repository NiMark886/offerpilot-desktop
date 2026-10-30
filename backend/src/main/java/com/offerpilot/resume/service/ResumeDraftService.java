package com.offerpilot.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.resume.domain.ResumeDraft;
import com.offerpilot.resume.mapper.ResumeDraftMapper;
import com.offerpilot.resume.web.dto.ResumeDraftContent;
import com.offerpilot.resume.web.dto.ResumeDraftExportView;
import com.offerpilot.resume.web.dto.ResumeDraftRequest;
import com.offerpilot.resume.web.dto.ResumeDraftView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Service
public class ResumeDraftService {

    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    private final ResumeDraftMapper resumeDraftMapper;
    private final ResumeDocxExporter docxExporter;
    private final ObjectMapper objectMapper;

    public ResumeDraftService(ResumeDraftMapper resumeDraftMapper,
                              ResumeDocxExporter docxExporter,
                              ObjectMapper objectMapper) {
        this.resumeDraftMapper = resumeDraftMapper;
        this.docxExporter = docxExporter;
        this.objectMapper = objectMapper;
    }

    public List<ResumeDraftView> list(Long userId) {
        return resumeDraftMapper.selectList(new LambdaQueryWrapper<ResumeDraft>()
                        .eq(ResumeDraft::getUserId, userId)
                        .orderByDesc(ResumeDraft::getUpdatedAt))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ResumeDraftView get(Long userId, Long draftId) {
        return toView(loadOwned(userId, draftId));
    }

    @Transactional
    public ResumeDraftView create(Long userId, ResumeDraftRequest request) {
        ResumeDraft draft = new ResumeDraft();
        draft.setUserId(userId);
        draft.setTitle(request.title().trim());
        draft.setContentJson(writeContent(normalize(request.content())));
        resumeDraftMapper.insert(draft);
        return toView(draft);
    }

    @Transactional
    public ResumeDraftView update(Long userId, Long draftId, ResumeDraftRequest request) {
        ResumeDraft draft = loadOwned(userId, draftId);
        draft.setTitle(request.title().trim());
        draft.setContentJson(writeContent(normalize(request.content())));
        resumeDraftMapper.updateById(draft);
        return toView(draft);
    }

    @Transactional
    public void delete(Long userId, Long draftId) {
        loadOwned(userId, draftId);
        resumeDraftMapper.deleteById(draftId);
    }

    public ResumeDraftExportView exportDocx(Long userId, Long draftId) {
        ResumeDraft draft = loadOwned(userId, draftId);
        ResumeDraftContent content = readContent(draft.getContentJson());
        byte[] bytes = docxExporter.export(content);
        return new ResumeDraftExportView(
                fileName(content, draft.getTitle()),
                Base64.getEncoder().encodeToString(bytes));
    }

    private ResumeDraft loadOwned(Long userId, Long draftId) {
        ResumeDraft draft = resumeDraftMapper.selectById(draftId);
        if (draft == null || !Objects.equals(draft.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "简历草稿不存在");
        }
        return draft;
    }

    private ResumeDraftView toView(ResumeDraft draft) {
        return new ResumeDraftView(
                draft.getId(),
                draft.getTitle(),
                readContent(draft.getContentJson()),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }

    private String writeContent(ResumeDraftContent content) {
        try {
            String json = objectMapper.writeValueAsString(content);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "简历内容过大");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("简历内容序列化失败", ex);
        }
    }

    private ResumeDraftContent readContent(String json) {
        if (json == null || json.isBlank()) {
            return emptyContent();
        }
        try {
            return normalize(objectMapper.readValue(json, ResumeDraftContent.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("简历内容解析失败", ex);
        }
    }

    private ResumeDraftContent normalize(ResumeDraftContent content) {
        if (content == null) {
            return emptyContent();
        }
        return new ResumeDraftContent(
                content.personal() == null ? emptyPersonal() : content.personal(),
                safeList(content.education()),
                safeList(content.internships()),
                safeList(content.projects()),
                safeList(content.skills()),
                safeList(content.awards()),
                content.style());
    }

    private ResumeDraftContent emptyContent() {
        return new ResumeDraftContent(
                emptyPersonal(), List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    private ResumeDraftContent.Personal emptyPersonal() {
        return new ResumeDraftContent.Personal(
                "", "", "", "", "", "", "", "", "", "", "");
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String fileName(ResumeDraftContent content, String title) {
        String name = content.personal() == null ? "" : content.personal().name();
        String base = name == null || name.isBlank() ? title : name;
        if (base == null || base.isBlank()) {
            base = "我的简历";
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (base.isBlank()) {
            base = "我的简历";
        }
        return base + "_简历.docx";
    }
}
