package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiClinicalGuidanceServiceTest {

    private final AiClinicalGuidanceService service = new AiClinicalGuidanceService();

    @ParameterizedTest
    @ValueSource(strings = {"牙痛，吞咽困难", "我现在喘不过气，嘴里肿了", "颈部肿胀，牙也疼"})
    void handlesAffirmativeDangerSignalsImmediately(String text) {
        assertTrue(service.hasImmediateRisk(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"没有呼吸困难", "无吞咽困难", "如果出现吞咽困难呢", "牙痛是否会呼吸困难", "吞咽困难吗？", "我没有脖子肿", "不是呼吸困难，是牙龈不舒服", "不觉得吞咽困难"})
    void doesNotTreatDenialsAndHypotheticalsAsPresentSymptoms(String text) {
        assertFalse(service.hasImmediateRisk(text));
    }

    @Test
    void onlyProvidesWhitelistedQuestionsAndSourcedGuidance() {
        assertFalse(service.validQuestions(List.of("自行服药")));
        assertTrue(service.advice("GENERAL", List.of("DURATION")).contains("持续多久"));
        assertTrue(service.references().stream().allMatch(source -> source.url().startsWith("https://www.nhs.uk/")));
        assertFalse(service.advice("GENERAL", List.of()).contains("毫克"));
    }
}
