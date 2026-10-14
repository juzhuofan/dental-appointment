package com.dental.ai.controller;

import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AiGenerateDTO;
import com.dental.ai.dto.AppointmentGuideDTO;
import com.dental.ai.service.AiRateLimitService;
import com.dental.ai.service.AiService;
import com.dental.ai.service.AppointmentGuideService;
import com.dental.ai.vo.AiConfigVO;
import com.dental.ai.vo.AiGenerateVO;
import com.dental.ai.vo.AppointmentGuideVO;
import com.dental.common.R;
import com.dental.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.HashMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通用 AI 接口与智能预约示例，统一使用 R 包装。 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;
    private final AppointmentGuideService guideService;
    private final AiRateLimitService rateLimitService;
    private final AiProperties properties;

    public AiController(AiService aiService, AppointmentGuideService guideService,
            AiRateLimitService rateLimitService, AiProperties properties) {
        this.aiService = aiService;
        this.guideService = guideService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @GetMapping("/config")
    public R<AiConfigVO> config() {
        return R.ok(new AiConfigVO(properties.isEnabled(), 300));
    }

    @PostMapping("/guide")
    public R<AppointmentGuideVO> guide(@Valid @RequestBody R<AppointmentGuideDTO> request) {
        rateLimitService.check(CurrentUser.require().id());
        return R.ok(guideService.guide(request.data()));
    }

    @PostMapping("/generate")
    public R<AiGenerateVO> generate(@Valid @RequestBody R<AiGenerateDTO> request) {
        rateLimitService.check(CurrentUser.requireRole("ADMIN").id());
        String content = aiService.generate(request.data().templateId(), new HashMap<>(request.data().variables()));
        return R.ok(new AiGenerateVO(request.data().templateId(), content));
    }
}
