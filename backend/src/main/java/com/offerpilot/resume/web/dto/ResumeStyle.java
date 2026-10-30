package com.offerpilot.resume.web.dto;

import java.util.Map;

public record ResumeStyle(
        Double bodyFontSize,
        Double nameFontSize,
        Double sectionTitleFontSize,
        Double entryTitleFontSize,
        Double metaFontSize,
        Double lineHeight,
        Double letterSpacing,
        Map<String, Double> sectionFontSizes
) {
}
