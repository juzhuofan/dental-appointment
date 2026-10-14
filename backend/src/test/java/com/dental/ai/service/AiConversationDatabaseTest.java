package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.ai.dto.AiChatPlanDTO;
import com.dental.ai.dto.AiChatSendDTO;
import com.dental.ai.entity.AiMessageEntity;
import com.dental.ai.mapper.AiMessageMapper;
import com.dental.auth.service.LocalDemoAccountInitializer;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.department.service.LocalDemoCatalogInitializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 显式启用的独立 MySQL 数据库验证，不调用付费模型、不写人工演示库。 */
@EnabledIfSystemProperty(named = "ai.database", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/dental_appointment_ai_test?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
        "spring.data.redis.database=1",
        "app.ai.enabled=true", "app.oss.enabled=false", "app.wechat.enabled=false"})
class AiConversationDatabaseTest {

    @MockitoBean
    private AiService ai;
    @MockitoBean
    private LocalDemoAccountInitializer demoAccountInitializer;
    @MockitoBean
    private LocalDemoCatalogInitializer demoCatalogInitializer;
    @MockitoSpyBean
    private AiMessageMapper messageMapper;
    @Autowired
    private AiConversationService service;
    @Autowired
    private JdbcTemplate jdbc;
    private long userId;
    private final List<Long> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        when(ai.generateStructured(eq("clinic-chat"), any(), anyList(), eq(AiChatPlanDTO.class)))
                .thenReturn(new AiChatPlanDTO("CLINIC", true, List.of("NAME", "ADDRESS"), List.of(),
                        List.of(), "", null, null, "ANY", List.of(), List.of(), "GENERAL"));
    }

    @AfterEach
    void cleanLogically() {
        for (long id : created) {
            if (jdbc.queryForObject("SELECT deleted FROM ai_conversation WHERE id = ?", Integer.class, id) == 0) {
                service.delete(userId, id);
            }
        }
        created.clear();
    }

    @Test
    void enforcesAccountIsolationBeforeModelOrHistoryAccess() {
        long id = create();
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class, () -> service.messages(userId + 1, id, null)).getCode());
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class, () -> service.send(userId + 1, id,
                new AiChatSendDTO("chat-isolated", "地址"))).getCode());
        assertEquals("NOT_FOUND", assertThrows(BusinessException.class, () -> service.delete(userId + 1, id)).getCode());
        verifyNoInteractions(ai);
    }

    @Test
    void storesPairAtomicallyAndReplaysWithoutAnotherModelCall() {
        long id = create();
        var input = new AiChatSendDTO("chat-repeat-123", "地址");
        var first = service.send(userId, id, input);
        var again = service.send(userId, id, input);
        assertEquals(first.assistantMessage().id(), again.assistantMessage().id());
        assertEquals(2, count(id));
        assertNotNull(first.userMessage().createdAt());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(BusinessException.class,
                () -> service.send(userId, id, new AiChatSendDTO(input.requestId(), "不同内容"))).getCode());
        verify(ai, times(1)).generateStructured(eq("clinic-chat"), any(), anyList(), eq(AiChatPlanDTO.class));
    }

    @Test
    void assistantInsertFailureRollsBackUserMessageAndVersion() {
        long id = create();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated write failure"))
                .when(messageMapper).insert(argThat((AiMessageEntity message) -> "ASSISTANT".equals(message.getRole())));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.send(userId, id, new AiChatSendDTO("chat-write-fail", "地址")));
        assertEquals(0, count(id));
        assertEquals(0, jdbc.queryForObject("SELECT version FROM ai_conversation WHERE id = ?", Integer.class, id));
    }

    @Test
    void deletionMarksBothTablesAndPreservesTimestamps() {
        long id = create();
        service.send(userId, id, new AiChatSendDTO("chat-delete-123", "地址"));
        service.delete(userId, id);
        assertEquals(1, jdbc.queryForObject("SELECT deleted FROM ai_conversation WHERE id = ?", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id = ? AND deleted = 1", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id = ? AND (created_at IS NULL OR updated_at IS NULL)", Integer.class, id));
        assertThrows(BusinessException.class, () -> service.messages(userId, id, null));
    }

    @Test
    void olderMessagesUseStableIdCursorAndAscendingDisplayOrder() {
        long id = create();
        for (int i = 0; i < 102; i++) {
            AiMessageEntity message = new AiMessageEntity();
            message.setConversationId(id); message.setRequestId("fixture-" + i);
            message.setRole(i % 2 == 0 ? "USER" : "ASSISTANT"); message.setContent("历史消息" + i);
            message.setDeleted(0); message.setCreatedAt(TimeUtil.nowUtc()); message.setUpdatedAt(TimeUtil.nowUtc());
            messageMapper.insert(message);
        }
        var last = service.messages(userId, id, null);
        var older = service.messages(userId, id, last.nextBeforeId());
        var first = service.messages(userId, id, older.nextBeforeId());
        assertEquals(50, last.records().size()); assertEquals(50, older.records().size()); assertEquals(2, first.records().size());
        assertTrue(last.hasMore()); assertTrue(older.hasMore()); assertTrue(!first.hasMore());
        assertTrue(older.records().get(49).id() < last.records().get(0).id());
    }

    private long create() {
        long id = service.create(userId).id(); created.add(id); return id;
    }

    private int count(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE conversation_id = ? AND deleted = 0", Integer.class, id);
    }
}
