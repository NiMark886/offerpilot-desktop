package com.offerpilot.track;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerpilot.track.domain.InterviewTrack;
import com.offerpilot.track.domain.RoleProfile;
import com.offerpilot.track.service.RoleProfileParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileParserTest {

    private final RoleProfileParser parser = new RoleProfileParser(new ObjectMapper());

    @Test
    void parsesSalesCompetenciesAndDimensions() {
        InterviewTrack track = new InterviewTrack();
        track.setCode("sales");
        track.setCategory("GENERAL");
        track.setRubricCode("SALES");
        track.setConfigJson("""
                {"retrievalMode":"BANK_AI","competencies":[
                  {"name":"客户开拓","weight":20,"description":"寻找客户"},
                  {"name":"需求挖掘","weight":20,"description":"挖掘需求"},
                  {"name":"方案呈现","weight":15,"description":"呈现方案"},
                  {"name":"异议处理","weight":15,"description":"处理异议"},
                  {"name":"谈判成交","weight":15,"description":"推进成交"},
                  {"name":"数据复盘","weight":15,"description":"复盘数据"}],
                 "reportDimensions":{"customerInsight":"客户洞察","communication":"沟通说服"},
                 "questionMix":{"behavioral":50,"scenario":30}}
                """);

        RoleProfile profile = parser.parse(track);

        assertThat(profile.isGeneral()).isTrue();
        assertThat(profile.retrievalMode()).isEqualTo("BANK_AI");
        assertThat(profile.competencyNames()).containsExactly(
                "客户开拓", "需求挖掘", "方案呈现", "异议处理", "谈判成交", "数据复盘");
        assertThat(profile.reportDimensions()).containsEntry("customerInsight", "客户洞察");
        assertThat(profile.dimensionLabel("communication")).isEqualTo("沟通说服");
        assertThat(profile.questionMix()).containsEntry("scenario", 30);
    }

    @Test
    void nullTrackFallsBackToTechProfile() {
        RoleProfile profile = parser.parse(null);

        assertThat(profile.isGeneral()).isFalse();
        assertThat(profile.competencyNames()).isEmpty();
        assertThat(profile.reportDimensions()).containsKey("accuracy");
    }

    @Test
    void invalidConfigFallsBackWithoutThrowing() {
        InterviewTrack track = new InterviewTrack();
        track.setCategory("GENERAL");
        track.setRubricCode("SALES");
        track.setConfigJson("not-json");

        RoleProfile profile = parser.parse(track);

        assertThat(profile.isGeneral()).isTrue();
        assertThat(profile.competencies()).isEmpty();
        assertThat(profile.reportDimensions()).containsKey("accuracy");
    }

    @Test
    void techTrackUsesCategoryAndDefaultDimensions() {
        InterviewTrack track = new InterviewTrack();
        track.setCategory("TECH");
        track.setRubricCode("BACKEND");
        track.setConfigJson("{\"focusTags\":[\"Java\"]}");

        RoleProfile profile = parser.parse(track);

        assertThat(profile.isGeneral()).isFalse();
        assertThat(profile.reportDimensions().keySet()).containsExactlyInAnyOrder(
                "accuracy", "depth", "project", "expression", "match");
        assertThat(profile.competencyNames()).isEqualTo(List.of());
    }
}
