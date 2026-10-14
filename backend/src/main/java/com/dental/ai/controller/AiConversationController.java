package com.dental.ai.controller;

import com.dental.ai.dto.AiChatSendDTO;
import com.dental.ai.dto.AiConversationCreateDTO;
import com.dental.ai.service.AiConversationService;
import com.dental.ai.vo.AiChatSendVO;
import com.dental.ai.vo.AiConversationVO;
import com.dental.ai.vo.AiMessagePageVO;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 账号私有聊天接口，客户端不能指定 userId、消息角色或系统提示词。 */
@Validated
@RestController
@RequestMapping("/api/v1/ai/conversations")
public class AiConversationController {

    private final AiConversationService service;

    public AiConversationController(AiConversationService service) {
        this.service = service;
    }

    @GetMapping
    public R<PageResult<AiConversationVO>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(service.list(CurrentUser.require().id(), page, size));
    }

    @PostMapping
    public R<AiConversationVO> create(@Valid @RequestBody R<AiConversationCreateDTO> request) {
        return R.ok(service.create(CurrentUser.require().id()));
    }

    @GetMapping("/{id}/messages")
    public R<AiMessagePageVO> messages(@Positive @PathVariable long id,
            @RequestParam(required = false) Long beforeId) {
        return R.ok(service.messages(CurrentUser.require().id(), id, beforeId));
    }

    @PostMapping("/{id}/messages")
    public R<AiChatSendVO> send(@Positive @PathVariable long id, @Valid @RequestBody R<AiChatSendDTO> request) {
        return R.ok(service.send(CurrentUser.require().id(), id, request.data()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@Positive @PathVariable long id) {
        service.delete(CurrentUser.require().id(), id);
        return R.ok(null);
    }
}
