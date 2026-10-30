package com.offerpilot.resume.service;

import com.offerpilot.resume.web.dto.ResumeDraftContent;
import com.offerpilot.resume.web.dto.ResumeStyle;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Generates an A4 resume DOCX matching the single-column, navy-heading
 * template used by the desktop resume builder.
 */
@Component
public class ResumeDocxExporter {

    private static final String FONT = "微软雅黑";
    private static final String ACCENT = "1F4E79";
    private static final String GRAY = "666666";

    private Style currentStyle;

    private record Style(
            double body,
            double name,
            double sectionTitle,
            double entryTitle,
            double meta,
            double lineHeight,
            double letterSpacing,
            Map<String, Double> sectionSizes
    ) {
        double sectionSize(String key) {
            Double value = sectionSizes == null ? null : sectionSizes.get(key);
            return value == null || value <= 0 ? body : value;
        }
    }

    public synchronized byte[] export(ResumeDraftContent content) {
        ResumeDraftContent data = content == null
                ? new ResumeDraftContent(null, List.of(), List.of(), List.of(), List.of(), List.of(), null)
                : content;
        currentStyle = resolveStyle(data.style());
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            setPage(document);
            renderHeader(document, data);
            renderEducation(document, data.education());
            List<ResumeDraftContent.Experience> experiences = safe(data.internships()).stream()
                    .filter(item -> item != null)
                    .toList();
            renderExperiences(document, "工作经历",
                    experiences.stream().filter(this::isWorkExperience).toList());
            renderExperiences(document, "实习经历",
                    experiences.stream().filter(item -> !isWorkExperience(item)).toList());
            renderProjects(document, data.projects());
            renderSkills(document, data.skills());
            renderAwards(document, data.awards());
            document.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 DOCX 简历失败", ex);
        } finally {
            currentStyle = null;
        }
    }

    private Style resolveStyle(ResumeStyle style) {
        if (style == null) {
            return new Style(10.5, 23, 12.5, 10.8, 9.8, 1.55, 0, Map.of());
        }
        return new Style(
                positive(style.bodyFontSize(), 10.5),
                positive(style.nameFontSize(), 23),
                positive(style.sectionTitleFontSize(), 12.5),
                positive(style.entryTitleFontSize(), 10.8),
                positive(style.metaFontSize(), 9.8),
                positive(style.lineHeight(), 1.55),
                nonNegative(style.letterSpacing(), 0),
                style.sectionFontSizes() == null ? Map.of() : style.sectionFontSizes());
    }

    private double positive(Double value, double fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private double nonNegative(Double value, double fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private void setPage(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margins.setTop(BigInteger.valueOf(800));
        margins.setBottom(BigInteger.valueOf(720));
        margins.setLeft(BigInteger.valueOf(850));
        margins.setRight(BigInteger.valueOf(850));
    }

    private void renderHeader(XWPFDocument document, ResumeDraftContent content) {
        ResumeDraftContent.Personal personal = content.personal();
        XWPFTable table = document.createTable(1, 2);
        table.setWidth("100%");
        table.setCellMargins(0, 0, 0, 0);
        removeBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0);
        XWPFTableCell right = table.getRow(0).getCell(1);
        left.setWidth("74%");
        right.setWidth("26%");
        right.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.TOP);

        XWPFParagraph nameParagraph = firstParagraph(left);
        nameParagraph.setSpacingAfter(100);
        nameParagraph.setSpacingBetween(1.0);
        addRun(nameParagraph, text(personal == null ? null : personal.name(), "你的姓名"),
                currentStyle.name(), true, ACCENT);

        List<String> firstLine = new ArrayList<>();
        addIfPresent(firstLine, personal == null ? null : personal.gender());
        addIfPresent(firstLine, personal == null ? null : personal.birthDate());
        addIfPresent(firstLine, personal == null ? null : personal.ethnicity());
        addIfPresent(firstLine, personal == null ? null : personal.politicalStatus());
        addLabeled(firstLine, "出生地", personal == null ? null : personal.birthplace());
        addLabeled(firstLine, "现居", personal == null ? null : personal.currentCity());
        if (!firstLine.isEmpty()) {
            addInfoLine(left, String.join(" | ", firstLine));
        }

        List<String> secondLine = new ArrayList<>();
        addLabeled(secondLine, "电话", personal == null ? null : personal.phone());
        addLabeled(secondLine, "邮箱", personal == null ? null : personal.email());
        addLabeled(secondLine, "社区", personal == null ? null : personal.communityUrl());
        if (!secondLine.isEmpty()) {
            addInfoLine(left, String.join(" | ", secondLine));
        }

        String educationSummary = educationSummary(content.education());
        if (!educationSummary.isBlank()) {
            addInfoLine(left, educationSummary);
        }

        if (personal != null && hasText(personal.photo())) {
            addPhoto(right, personal.photo());
        }
    }

    private void renderEducation(XWPFDocument document, List<ResumeDraftContent.Education> education) {
        List<ResumeDraftContent.Education> items = safe(education);
        if (items.isEmpty()) {
            return;
        }
        sectionTitle(document, "教育背景");
        for (ResumeDraftContent.Education item : items) {
            if (item == null) {
                continue;
            }
            String title = join(" ", item.school(), item.college());
            if (hasText(item.major()) || hasText(item.degree())) {
                title = title.isBlank()
                        ? join(" ", item.major(), item.degree())
                        : title + " · " + join(" ", item.major(), item.degree());
            }
            entryHead(document, title, dateRange(item.startDate(), item.endDate()));
            if (hasText(item.courses())) {
                XWPFParagraph paragraph = paragraph(document);
                addRun(paragraph, "核心课程：", currentStyle.sectionSize("education"), true, null);
                addRun(paragraph, item.courses().trim(), currentStyle.sectionSize("education"), false, null);
            }
            if (hasText(item.gpa()) || hasText(item.ranking())) {
                XWPFParagraph paragraph = paragraph(document);
                if (hasText(item.gpa())) {
                    addRun(paragraph, "GPA：", currentStyle.sectionSize("education"), true, null);
                    addRun(paragraph, item.gpa().trim(), currentStyle.sectionSize("education"), false, null);
                }
                if (hasText(item.gpa()) && hasText(item.ranking())) {
                    addRun(paragraph, "  |  ", currentStyle.sectionSize("education"), false, GRAY);
                }
                if (hasText(item.ranking())) {
                    addRun(paragraph, "排名：", currentStyle.sectionSize("education"), true, null);
                    addRun(paragraph, item.ranking().trim(), currentStyle.sectionSize("education"), false, null);
                }
            }
            spacer(document, 40);
        }
    }

    private void renderExperiences(XWPFDocument document, String heading,
                                   List<ResumeDraftContent.Experience> experiences) {
        List<ResumeDraftContent.Experience> items = safe(experiences);
        if (items.isEmpty()) {
            return;
        }
        sectionTitle(document, heading);
        for (ResumeDraftContent.Experience item : items) {
            if (item == null) {
                continue;
            }
            entryHead(document,
                    join(" · ", item.company(), item.role()),
                    dateRange(item.startDate(), item.endDate()));
            if (hasText(item.techStack())) {
                XWPFParagraph tech = paragraph(document);
                tech.setSpacingAfter(40);
                addRun(tech, item.techStack().trim(), currentStyle.sectionSize("experience") * 0.9, false, GRAY);
            }
            bullets(document, item.bullets(), "experience");
            spacer(document, 80);
        }
    }

    private void renderProjects(XWPFDocument document, List<ResumeDraftContent.Project> projects) {
        List<ResumeDraftContent.Project> items = safe(projects);
        if (items.isEmpty()) {
            return;
        }
        sectionTitle(document, "项目经历");
        for (ResumeDraftContent.Project item : items) {
            if (item == null) {
                continue;
            }
            entryHead(document, item.name(), dateRange(item.startDate(), item.endDate()));
            if (hasText(item.techStack())) {
                XWPFParagraph tech = paragraph(document);
                tech.setSpacingAfter(40);
                addRun(tech, item.techStack().trim(), currentStyle.sectionSize("projects") * 0.9, false, GRAY);
            }
            if (hasText(item.repoUrl())) {
                XWPFParagraph repo = paragraph(document);
                repo.setSpacingAfter(40);
                addRun(repo, "仓库：" + item.repoUrl().trim(), currentStyle.sectionSize("projects") * 0.88, false, ACCENT);
            }
            bullets(document, item.bullets(), "projects");
            spacer(document, 80);
        }
    }

    private boolean isWorkExperience(ResumeDraftContent.Experience experience) {
        String type = experience.type();
        return "WORK".equalsIgnoreCase(type) || "工作".equals(type);
    }

    private void renderSkills(XWPFDocument document, List<ResumeDraftContent.Skill> skills) {
        List<ResumeDraftContent.Skill> items = safe(skills);
        if (items.isEmpty()) {
            return;
        }
        sectionTitle(document, "专业技能");
        for (ResumeDraftContent.Skill item : items) {
            if (item == null || (!hasText(item.label()) && !hasText(item.content()))) {
                continue;
            }
            XWPFParagraph paragraph = bulletParagraph(document);
            if (hasText(item.label())) {
                addRun(paragraph, item.label().trim() + "：", currentStyle.sectionSize("skills"), true, null);
            }
            addRun(paragraph, text(item.content(), ""), currentStyle.sectionSize("skills"), false, null);
        }
    }

    private void renderAwards(XWPFDocument document, List<String> awards) {
        List<String> items = safe(awards).stream().filter(this::hasText).toList();
        if (items.isEmpty()) {
            return;
        }
        sectionTitle(document, "荣誉奖项");
        bullets(document, items, "awards");
    }

    private void entryHead(XWPFDocument document, String title, String date) {
        XWPFTable table = document.createTable(1, 2);
        table.setWidth("100%");
        table.setCellMargins(0, 0, 0, 0);
        removeBorders(table);
        XWPFTableCell left = table.getRow(0).getCell(0);
        XWPFTableCell right = table.getRow(0).getCell(1);
        left.setWidth("74%");
        right.setWidth("26%");
        XWPFParagraph titleParagraph = firstParagraph(left);
        titleParagraph.setSpacingAfter(20);
        addRun(titleParagraph, text(title, ""), currentStyle.entryTitle(), true, null);
        XWPFParagraph dateParagraph = firstParagraph(right);
        dateParagraph.setAlignment(ParagraphAlignment.RIGHT);
        dateParagraph.setSpacingAfter(20);
        addRun(dateParagraph, text(date, ""), currentStyle.entryTitle() * 0.95, true, ACCENT);
    }

    private void sectionTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(240);
        paragraph.setSpacingAfter(100);
        paragraph.setSpacingBetween(1.0);
        addRun(paragraph, title, currentStyle.sectionTitle(), true, ACCENT);
        addBottomBorder(paragraph);
    }

    private void bullets(XWPFDocument document, List<String> values, String sectionKey) {
        for (String value : safe(values)) {
            if (!hasText(value)) {
                continue;
            }
            XWPFParagraph paragraph = bulletParagraph(document);
            addRun(paragraph, "• " + value.trim(), currentStyle.sectionSize(sectionKey), false, null);
        }
    }

    private XWPFParagraph bulletParagraph(XWPFDocument document) {
        XWPFParagraph paragraph = paragraph(document);
        paragraph.setIndentationLeft(260);
        paragraph.setIndentationHanging(180);
        paragraph.setSpacingAfter(30);
        return paragraph;
    }

    private XWPFParagraph paragraph(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(20);
        paragraph.setSpacingBetween(currentStyle.lineHeight());
        return paragraph;
    }

    private void spacer(XWPFDocument document, int spacing) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingAfter(spacing);
        paragraph.setSpacingBetween(1.0);
    }

    private void addInfoLine(XWPFTableCell cell, String value) {
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setSpacingAfter(20);
        paragraph.setSpacingBetween(1.1);
        addRun(paragraph, value, currentStyle.meta(), false, "333333");
    }

    private XWPFParagraph firstParagraph(XWPFTableCell cell) {
        XWPFParagraph paragraph = cell.getParagraphArray(0);
        return paragraph == null ? cell.addParagraph() : paragraph;
    }

    private void addPhoto(XWPFTableCell cell, String dataUrl) {
        byte[] bytes = decodeDataUrl(dataUrl);
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return;
            }
            XWPFParagraph paragraph = firstParagraph(cell);
            paragraph.setAlignment(ParagraphAlignment.RIGHT);
            XWPFRun run = paragraph.createRun();
            run.addPicture(new ByteArrayInputStream(bytes), pictureType(dataUrl),
                    "photo", Units.toEMU(74), Units.toEMU(96));
        } catch (Exception ignored) {
            // A broken or unsupported photo should not block the resume export.
        }
    }

    private int pictureType(String dataUrl) {
        String lower = dataUrl.toLowerCase();
        if (lower.startsWith("data:image/png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        if (lower.startsWith("data:image/gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }

    private byte[] decodeDataUrl(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || !dataUrl.substring(0, comma).contains(";base64")) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private XWPFRun addRun(XWPFParagraph paragraph, String text, double size,
                           boolean bold, String color) {
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily(FONT);
        run.setFontFamily(FONT, XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setBold(bold);
        if (currentStyle != null && currentStyle.letterSpacing() > 0) {
            run.setCharacterSpacing((int) Math.round(currentStyle.letterSpacing() * 20));
        }
        if (color != null) {
            run.setColor(color);
        }
        return run;
    }

    private void removeBorders(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
        table.setBottomBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
        table.setLeftBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
        table.setRightBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NIL, 0, 0, null);
    }

    private void addBottomBorder(XWPFParagraph paragraph) {
        CTP ctp = paragraph.getCTP();
        CTPPr ppr = ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
        CTPBdr border = ppr.isSetPBdr() ? ppr.getPBdr() : ppr.addNewPBdr();
        CTBorder bottom = border.isSetBottom() ? border.getBottom() : border.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(6));
        bottom.setSpace(BigInteger.valueOf(1));
        bottom.setColor(ACCENT);
    }

    private String educationSummary(List<ResumeDraftContent.Education> education) {
        return safe(education).stream()
                .filter(item -> item != null && hasText(item.school()))
                .findFirst()
                .map(item -> {
                    String summary = item.school().trim();
                    String major = join(" ", item.major(), item.degree());
                    if (hasText(major)) {
                        summary += " · " + major;
                    }
                    String year = graduationYear(item.endDate());
                    if (!year.isBlank()) {
                        summary += "（" + year + " 届）";
                    }
                    return summary;
                })
                .orElse("");
    }

    private String graduationYear(String endDate) {
        if (!hasText(endDate)) {
            return "";
        }
        String digits = endDate.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            return digits.substring(0, 4);
        }
        return "";
    }

    private String dateRange(String start, String end) {
        if (hasText(start) && hasText(end)) {
            return start.trim() + " - " + end.trim();
        }
        return hasText(start) ? start.trim() : text(end, "");
    }

    private void addIfPresent(List<String> values, String value) {
        if (hasText(value)) {
            values.add(value.trim());
        }
    }

    private void addLabeled(List<String> values, String label, String value) {
        if (hasText(value)) {
            values.add(label + "：" + value.trim());
        }
    }

    private String join(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(separator, parts);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
