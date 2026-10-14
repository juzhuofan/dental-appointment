package com.dental.ai.vo;

import java.util.List;

/** 服务器组装的事实回答和经过目录验证的医生/号源卡片。 */
public record AiChatAnswerVO(String content, String topic, String guidance,
        List<AppointmentGuideVO.Candidate> candidates, List<AiReferenceVO> references) { }
