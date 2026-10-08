package com.dental.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dental.audit.service.OperationLogService;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
import com.dental.file.service.FileUploadService;
import com.dental.security.CurrentUser;
import com.dental.user.dto.AccountProfileSaveDTO;
import com.dental.user.dto.PatientProfileSaveDTO;
import com.dental.user.entity.PatientProfile;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.PatientProfileMapper;
import com.dental.user.mapper.SysRoleMapper;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.mapper.SysUserRoleMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 多就诊人归属、默认档案与账号昵称的业务边界。 */
@ExtendWith(MockitoExtension.class)
class UserPatientProfilesTest {

    private static final long USER_ID = 41L;

    @Mock private SysUserMapper userMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private PatientProfileMapper profileMapper;
    @Mock private AuthTokenMapper tokenMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OperationLogService audit;
    @Mock private FileUploadService fileUploadService;

    private UserService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                PatientProfile.class);
        service = new UserService(userMapper, roleMapper, userRoleMapper, profileMapper,
                tokenMapper, passwordEncoder, audit, List.of(), fileUploadService,
                new WechatProperties());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new CurrentUser(USER_ID, "wechat", "微信用户", List.of("PATIENT"), null, "test"),
                        null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addsSecondPatientWithoutReplacingDefault() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        PatientProfile existing = profile(1L, 1);
        when(profileMapper.selectOne(any())).thenReturn(existing);

        service.createPatientProfile(details("患者乙", "13900000002"));

        ArgumentCaptor<PatientProfile> captured = ArgumentCaptor.forClass(PatientProfile.class);
        verify(profileMapper).insert(captured.capture());
        assertEquals(USER_ID, captured.getValue().getUserId());
        assertEquals(0, captured.getValue().getIsDefault());
        assertEquals("患者乙", captured.getValue().getRealName());
        assertEquals(0, captured.getValue().getDeleted());
        assertTrue(captured.getValue().getUpdatedAt() != null);
    }

    @Test
    void rejectsEditingAnotherAccountsPatient() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.updatePatientProfile(999L, details("访客", "13900000002")));
        assertEquals(404, failure.getHttpStatus());
        verify(profileMapper, never()).updateById(any(PatientProfile.class));
    }

    @Test
    void refusesToDeletePatientWithActiveAppointment() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        when(profileMapper.selectOne(any())).thenReturn(profile(2L, 0));
        when(profileMapper.selectList(any())).thenReturn(List.of(profile(1L, 1), profile(2L, 0)));
        when(profileMapper.countActiveAppointments(2L, USER_ID)).thenReturn(1L);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.deletePatientProfile(2L));
        assertEquals(409, failure.getHttpStatus());
        verify(profileMapper, never()).update(any(), any());
    }

    @Test
    void movesDefaultToSelectedPatient() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        PatientProfile selected = profile(2L, 0);
        when(profileMapper.selectOne(any())).thenReturn(selected);

        service.setDefaultPatientProfile(2L);

        assertEquals(1, selected.getIsDefault());
        verify(profileMapper).update(any(), any());
        verify(profileMapper).updateById(selected);
    }

    @Test
    void savesNicknameOnAccountOnly() {
        SysUser user = activeUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        var result = service.saveAccountProfile(new AccountProfileSaveDTO("  小林  "));

        assertEquals("小林", result.displayName());
        verify(userMapper).updateById(user);
        verify(profileMapper, never()).updateById(any(PatientProfile.class));
    }

    private static SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setStatus(1);
        user.setWechatOpenid("test-openid");
        user.setDisplayName("微信用户");
        return user;
    }

    private static PatientProfile profile(Long id, int isDefault) {
        PatientProfile profile = new PatientProfile();
        profile.setId(id);
        profile.setUserId(USER_ID);
        profile.setIsDefault(isDefault);
        profile.setRealName("患者甲");
        profile.setPhone("13900000001");
        return profile;
    }

    private static PatientProfileSaveDTO details(String name, String phone) {
        return new PatientProfileSaveDTO(name, phone, 0, null, null);
    }
}
