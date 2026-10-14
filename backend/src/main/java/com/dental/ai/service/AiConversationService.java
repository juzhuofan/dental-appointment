package com.dental.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AiChatSendDTO;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.ai.entity.AiConversationEntity;
import com.dental.ai.entity.AiMessageEntity;
import com.dental.ai.mapper.AiConversationMapper;
import com.dental.ai.mapper.AiMessageMapper;
import com.dental.ai.vo.AiChatAnswerVO;
import com.dental.ai.vo.AiChatMessageVO;
import com.dental.ai.vo.AiChatSendVO;
import com.dental.ai.vo.AiConversationVO;
import com.dental.ai.vo.AiMessagePageVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 账号隔离、会话和消息持久化。普通查询用 MyBatis-Plus，所有删除均为逻辑删除。 */
@Service
public class AiConversationService {

    private static final int MAX_MESSAGES = 200;
    private final AiConversationMapper conversations;
    private final AiMessageMapper messages;
    private final AiConversationLockService lockService;
    private final AiChatAnswerService answerService;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final AiProperties properties;

    public AiConversationService(AiConversationMapper conversations, AiMessageMapper messages,
            AiConversationLockService lockService, AiChatAnswerService answerService,
            TransactionTemplate transactions, ObjectMapper mapper, AiProperties properties) {
        this.conversations = conversations;
        this.messages = messages;
        this.lockService = lockService;
        this.answerService = answerService;
        this.transactions = transactions;
        this.mapper = mapper;
        this.properties = properties;
    }

    public PageResult<AiConversationVO> list(long userId, int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw BusinessException.bad("会话分页参数不正确");
        }
        Page<AiConversationEntity> result = conversations.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AiConversationEntity>().eq(AiConversationEntity::getUserId, userId)
                        .orderByDesc(AiConversationEntity::getUpdatedAt).orderByDesc(AiConversationEntity::getId));
        return new PageResult<>(result.getRecords().stream().map(this::conversationVO).toList(), result.getTotal(), page, size);
    }

    public AiConversationVO create(long userId) {
        if (conversations.selectCount(new LambdaQueryWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getUserId, userId)) >= 100) {
            throw BusinessException.bad("最多保留 100 个会话，请删除不需要的会话后再创建");
        }
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setUserId(userId);
        conversation.setTitle("新会话");
        conversation.setVersion(0);
        conversation.setCreatedAt(TimeUtil.nowUtc());
        conversation.setUpdatedAt(conversation.getCreatedAt());
        conversation.setDeleted(0);
        conversations.insert(conversation);
        return conversationVO(conversation);
    }

    public AiMessagePageVO messages(long userId, long conversationId, Long beforeId) {
        requireOwned(userId, conversationId);
        if (beforeId != null && beforeId < 1) {
            throw BusinessException.bad("消息分页标识不正确");
        }
        List<AiMessageEntity> records = messages.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .lt(beforeId != null, AiMessageEntity::getId, beforeId)
                .orderByDesc(AiMessageEntity::getId).last("LIMIT 51"));
        boolean hasMore = records.size() > 50;
        List<AiMessageEntity> visible = new ArrayList<>(records.subList(0, Math.min(records.size(), 50)));
        Collections.reverse(visible);
        return new AiMessagePageVO(visible.stream().map(this::messageVO).toList(), hasMore,
                visible.isEmpty() ? null : visible.get(0).getId());
    }

    public AiChatSendVO send(long userId, long conversationId, AiChatSendDTO input) {
        AiConversationEntity conversation = requireOwned(userId, conversationId);
        AiChatSendVO replay = replay(conversation, input);
        if (replay != null) {
            return replay;
        }
        if (!properties.isEnabled()) {
            throw new BusinessException("AI_DISABLED", "智能助手暂未开放，请手动选择医生", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return lockService.withLock(conversationId, () -> {
            AiConversationEntity current = requireOwned(userId, conversationId);
            AiChatSendVO previous = replay(current, input);
            if (previous != null) {
                return previous;
            }
            if (messages.selectCount(new LambdaQueryWrapper<AiMessageEntity>()
                    .eq(AiMessageEntity::getConversationId, conversationId)) >= MAX_MESSAGES) {
                throw BusinessException.bad("这个会话已达到 100 轮，请创建新会话继续交流");
            }
            List<AiChatTurnDTO> history = history(conversationId);
            AiChatAnswerVO answer = answerService.answer(userId, input.content().trim(), history);
            return savePair(current, input, answer);
        });
    }

    public void delete(long userId, long conversationId) {
        requireOwned(userId, conversationId);
        lockService.withLock(conversationId, () -> transactions.execute(status -> {
            AiConversationEntity conversation = requireOwned(userId, conversationId);
            int affected = conversations.update(null, new LambdaUpdateWrapper<AiConversationEntity>()
                    .eq(AiConversationEntity::getId, conversationId).eq(AiConversationEntity::getUserId, userId)
                    .eq(AiConversationEntity::getVersion, conversation.getVersion())
                    .set(AiConversationEntity::getDeleted, 1).set(AiConversationEntity::getUpdatedAt, TimeUtil.nowUtc())
                    .set(AiConversationEntity::getVersion, conversation.getVersion() + 1));
            if (affected != 1) {
                throw BusinessException.conflict("会话状态已变化，请刷新后重试");
            }
            messages.update(null, new LambdaUpdateWrapper<AiMessageEntity>()
                    .eq(AiMessageEntity::getConversationId, conversationId).set(AiMessageEntity::getDeleted, 1)
                    .set(AiMessageEntity::getUpdatedAt, TimeUtil.nowUtc()));
            return null;
        }));
    }

    private AiChatSendVO savePair(AiConversationEntity conversation, AiChatSendDTO input, AiChatAnswerVO answer) {
        String metadata = json(answer);
        return transactions.execute(status -> {
            String title = "新会话".equals(conversation.getTitle())
                    ? input.content().trim().substring(0, Math.min(24, input.content().trim().length())) : conversation.getTitle();
            var now = TimeUtil.nowUtc();
            int affected = conversations.update(null, new LambdaUpdateWrapper<AiConversationEntity>()
                    .eq(AiConversationEntity::getId, conversation.getId())
                    .eq(AiConversationEntity::getUserId, conversation.getUserId())
                    .eq(AiConversationEntity::getVersion, conversation.getVersion())
                    .set(AiConversationEntity::getTitle, title).set(AiConversationEntity::getUpdatedAt, now)
                    .set(AiConversationEntity::getVersion, conversation.getVersion() + 1));
            if (affected != 1) {
                throw BusinessException.conflict("会话状态已变化，请重新加载后再发送");
            }
            AiMessageEntity user = newMessage(conversation.getId(), input.requestId(), "USER", input.content().trim(), null);
            AiMessageEntity assistant = newMessage(conversation.getId(), input.requestId(), "ASSISTANT", answer.content(), metadata);
            messages.insert(user);
            messages.insert(assistant);
            conversation.setTitle(title);
            conversation.setUpdatedAt(now);
            conversation.setVersion(conversation.getVersion() + 1);
            return new AiChatSendVO(conversationVO(conversation), messageVO(user), messageVO(assistant));
        });
    }

    private AiChatSendVO replay(AiConversationEntity conversation, AiChatSendDTO input) {
        List<AiMessageEntity> pair = messages.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversation.getId())
                .eq(AiMessageEntity::getRequestId, input.requestId()));
        if (pair.isEmpty()) {
            return null;
        }
        AiMessageEntity user = pair.stream().filter(message -> "USER".equals(message.getRole())).findFirst().orElse(null);
        AiMessageEntity assistant = pair.stream().filter(message -> "ASSISTANT".equals(message.getRole())).findFirst().orElse(null);
        if (user == null || assistant == null) {
            throw BusinessException.conflict("聊天记录暂未完整保存，请重新加载");
        }
        if (!input.content().trim().equals(user.getContent())) {
            throw BusinessException.conflict("IDEMPOTENCY_CONFLICT", "同一次发送重试的内容不能改变");
        }
        return new AiChatSendVO(conversationVO(conversation), messageVO(user), messageVO(assistant));
    }

    private List<AiChatTurnDTO> history(long conversationId) {
        List<AiMessageEntity> records = new ArrayList<>(messages.selectList(new LambdaQueryWrapper<AiMessageEntity>()
                .eq(AiMessageEntity::getConversationId, conversationId)
                .orderByDesc(AiMessageEntity::getId).last("LIMIT 12")));
        Collections.reverse(records);
        return records.stream().map(message -> {
            String content = message.getContent();
            if ("ASSISTANT".equals(message.getRole()) && message.getMetadataJson() != null) {
                AiChatAnswerVO answer = metadata(message.getMetadataJson());
                if (answer != null && !answer.candidates().isEmpty()) {
                    content += "\n本轮医生卡片：" + answer.candidates().stream()
                            .map(item -> item.doctorName() + "(ID=" + item.doctorId() + ",科室=" + item.departmentName() + ")")
                            .collect(java.util.stream.Collectors.joining("；"));
                }
            }
            return new AiChatTurnDTO(message.getRole(), content.substring(0, Math.min(content.length(), 2400)));
        }).toList();
    }

    private AiConversationEntity requireOwned(long userId, long id) {
        AiConversationEntity conversation = conversations.selectOne(new LambdaQueryWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getId, id).eq(AiConversationEntity::getUserId, userId));
        if (conversation == null) {
            throw BusinessException.missing("会话不存在或已删除");
        }
        return conversation;
    }

    private AiMessageEntity newMessage(long conversationId, String requestId, String role, String content, String metadata) {
        AiMessageEntity message = new AiMessageEntity();
        message.setConversationId(conversationId);
        message.setRequestId(requestId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadata);
        message.setCreatedAt(TimeUtil.nowUtc());
        message.setUpdatedAt(message.getCreatedAt());
        message.setDeleted(0);
        return message;
    }

    private AiConversationVO conversationVO(AiConversationEntity conversation) {
        return new AiConversationVO(conversation.getId(), conversation.getTitle(),
                TimeUtil.toClinic(conversation.getCreatedAt()), TimeUtil.toClinic(conversation.getUpdatedAt()));
    }

    private AiChatMessageVO messageVO(AiMessageEntity message) {
        return new AiChatMessageVO(message.getId(), message.getRequestId(), message.getRole(), message.getContent(),
                metadata(message.getMetadataJson()), TimeUtil.toClinic(message.getCreatedAt()));
    }

    private AiChatAnswerVO metadata(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, AiChatAnswerVO.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("聊天回答快照读取失败");
        }
    }

    private String json(AiChatAnswerVO answer) {
        try {
            return mapper.writeValueAsString(answer);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("聊天回答快照序列化失败");
        }
    }
}
