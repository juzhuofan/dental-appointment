package com.dental.ai.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dental.schedule.service.ScheduleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.auth.entity.AuthToken;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.auth.service.LocalDemoAccountInitializer;
import com.dental.common.TimeUtil;
import com.dental.department.service.LocalDemoCatalogInitializer;
import com.dental.security.JwtService;
import com.dental.user.entity.SysRole;
import com.dental.user.entity.SysUser;
import com.dental.user.entity.SysUserRole;
import com.dental.user.mapper.SysRoleMapper;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 显式启用的整体验证：真实数据库/Redis/DeepSeek，测试账号和令牌随事务回滚，不创建预约。 */
@EnabledIfSystemProperty(named = "ai.live", matches = "true")
@SpringBootTest(properties = "app.demo-login.enabled=false")
@AutoConfigureMockMvc
class AiApplicationLiveTest {

    // 本机验证只读取已有目录，避免 ApplicationRunner 刷新演示账号与排班。
    @MockitoBean
    private LocalDemoCatalogInitializer demoCatalogInitializer;
    @MockitoBean
    private LocalDemoAccountInitializer demoAccountInitializer;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private ScheduleService schedules;
    @Autowired
    private SysUserMapper users;
    @Autowired
    private SysRoleMapper roles;
    @Autowired
    private SysUserRoleMapper userRoles;
    @Autowired
    private AuthTokenMapper tokens;
    @Autowired
    private JwtService jwtService;

    @Test
    void publicFlagsAndProtectedGuideUseActualSecurityChain() throws Exception {
        mvc.perform(get("/api/v1/ai/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        mvc.perform(post("/api/v1/ai/guide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"message\":\"洗牙\"}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void authenticatedGuideUsesRealPublishedCatalogAndSchedules() throws Exception {
        SysUser testUser = new SysUser();
        testUser.setUsername("ai-test-" + UUID.randomUUID());
        testUser.setDisplayName("AI 测试账号");
        testUser.setPasswordHash("!test-only");
        testUser.setStatus(1);
        testUser.setDeleted(0);
        testUser.setCreatedAt(TimeUtil.nowUtc());
        testUser.setUpdatedAt(testUser.getCreatedAt());
        users.insert(testUser);
        SysRole role = roles.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "PATIENT"));
        SysUserRole relation = new SysUserRole();
        relation.setUserId(testUser.getId());
        relation.setRoleId(role.getId());
        relation.setDeleted(0);
        relation.setCreatedAt(TimeUtil.nowUtc());
        relation.setUpdatedAt(relation.getCreatedAt());
        userRoles.insert(relation);
        AuthToken session = new AuthToken();
        session.setTokenId(UUID.randomUUID().toString());
        session.setUserId(testUser.getId());
        session.setExpiresAt(TimeUtil.nowUtc().plusMinutes(2));
        session.setRevoked(0);
        session.setDeleted(0);
        session.setCreatedAt(TimeUtil.nowUtc());
        session.setUpdatedAt(session.getCreatedAt());
        tokens.insert(session);
        String token = jwtService.issue(testUser.getId(), session.getTokenId(), Instant.now().plusSeconds(120));
        var response = mvc.perform(post("/api/v1/ai/guide").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"message\":\"未来两周想洗牙，时间不限\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.candidates").isArray()).andReturn().getResponse();
        var candidates = mapper.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("candidates");
        assertTrue(candidates.size() <= 5);
        for (var candidate : candidates) {
            for (var slot : candidate.path("schedules")) {
                var actual = schedules.publicDetail(slot.path("id").asLong());
                assertEquals(actual.doctorId().longValue(), candidate.path("doctorId").asLong());
                assertTrue(actual.remainingSlots() > 0);
                assertEquals("PUBLISHED", actual.status());
            }
        }
    }
}
