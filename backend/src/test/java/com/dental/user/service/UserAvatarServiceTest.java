package com.dental.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.file.service.FileUploadService;
import com.dental.file.vo.FileUploadVO;
import com.dental.security.CurrentUser;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.vo.UserVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

/** 验证本人头像写入、真实文件校验及外部上传后的账号状态复核，不访问 OSS 或数据库。 */
@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

    private static final Long USER_ID = 41L;
    private static final String STORED_URL = "https://dental-test.oss-cn-beijing.aliyuncs.com/"
            + "dental/uploads/2026/10/07/test.png";
    private static final byte[] PNG_BYTES = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0};

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private FileUploadService uploadService;
    @Mock
    private OperationLogService audit;
    @Mock
    private PlatformTransactionManager transactionManager;

    private UserAvatarService service;

    @BeforeEach
    void setUp() {
        service = new UserAvatarService(userMapper, userService, uploadService, audit, transactionManager);
        authenticate(List.of("PATIENT"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistsUnsignedOssLinkForCurrentUserAndReturnsDisplayLink() {
        SysUser account = wechatUser();
        LocalDateTime createdAt = account.getCreatedAt();
        LocalDateTime oldUpdatedAt = account.getUpdatedAt();
        MockMultipartFile file = pngFile();
        when(userService.requireActiveUser(USER_ID)).thenReturn(account);
        when(uploadService.upload(file)).thenReturn(uploadResult());
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(userMapper.selectOne(any())).thenReturn(account);
        UserVO expected = new UserVO(USER_ID, "wechat-test", "微信用户", List.of("PATIENT"), null,
                null, STORED_URL, STORED_URL + "?Expires=123&Signature=test", true, true);
        when(userService.toUserVO(account)).thenReturn(expected);

        UserVO result = service.upload(file);

        assertSame(expected, result);
        ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(saved.capture());
        assertEquals(USER_ID, saved.getValue().getId());
        assertEquals(STORED_URL, saved.getValue().getAvatarUrl());
        assertFalse(saved.getValue().getAvatarUrl().contains("?"));
        assertEquals(createdAt, saved.getValue().getCreatedAt());
        assertNotNull(saved.getValue().getUpdatedAt());
        assertTrue(saved.getValue().getUpdatedAt().isAfter(oldUpdatedAt));
        verify(userService).requireRole(USER_ID, "PATIENT");
        verify(audit).record(eq("AVATAR_UPDATE"), eq("sys_user"), eq("41"), anyString());
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @ParameterizedTest
    @MethodSource("invalidAvatars")
    void rejectsInvalidImageBeforeOssOrDatabaseWrite(MultipartFile file) {
        when(userService.requireActiveUser(USER_ID)).thenReturn(wechatUser());

        BusinessException failure = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(400, failure.getHttpStatus());
        verifyNoInteractions(uploadService, userMapper, audit, transactionManager);
    }

    private static Stream<Arguments> invalidAvatars() {
        return Stream.of(
                Arguments.of((MultipartFile) null),
                Arguments.of(new MockMultipartFile("file", "empty.png", "image/png", new byte[0])),
                Arguments.of(new MockMultipartFile("file", "record.pdf", "application/pdf", PNG_BYTES)),
                Arguments.of(new MockMultipartFile("file", "fake.png", "image/png",
                        "not an image".getBytes(StandardCharsets.UTF_8))),
                Arguments.of(new MockMultipartFile("file", "wrong.jpg", "image/jpeg", PNG_BYTES)),
                Arguments.of(new MockMultipartFile("file", "image.svg", "image/svg+xml", PNG_BYTES)));
    }

    @Test
    void rejectsAvatarLargerThanTwoMegabytes() {
        when(userService.requireActiveUser(USER_ID)).thenReturn(wechatUser());
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png",
                new byte[2 * 1024 * 1024 + 1]);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(413, failure.getHttpStatus());
        assertEquals("FILE_TOO_LARGE", failure.getCode());
        verifyNoInteractions(uploadService, userMapper, audit, transactionManager);
    }

    @Test
    void rejectsPatientWithoutVerifiedWechatIdentity() {
        SysUser patient = wechatUser();
        patient.setWechatOpenid(null);
        when(userService.requireActiveUser(USER_ID)).thenReturn(patient);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.upload(pngFile()));

        assertEquals(403, failure.getHttpStatus());
        verifyNoInteractions(uploadService, userMapper, audit, transactionManager);
    }

    @Test
    void requiresAuthenticatedPatientRole() {
        SecurityContextHolder.clearContext();
        assertEquals(401, assertThrows(BusinessException.class, () -> service.upload(pngFile())).getHttpStatus());
        authenticate(List.of("ADMIN"));
        assertEquals(403, assertThrows(BusinessException.class, () -> service.upload(pngFile())).getHttpStatus());
        verifyNoInteractions(userService, uploadService, userMapper, audit, transactionManager);
    }

    @Test
    void rollsBackIfAccountIsDisabledWhileAvatarUploads() {
        when(userService.requireActiveUser(USER_ID)).thenReturn(wechatUser());
        when(uploadService.upload(any())).thenReturn(uploadResult());
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        SysUser disabled = wechatUser();
        disabled.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        BusinessException failure = assertThrows(BusinessException.class, () -> service.upload(pngFile()));

        assertEquals(403, failure.getHttpStatus());
        verify(uploadService).upload(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verifyNoInteractions(audit);
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
    }

    private static void authenticate(List<String> roles) {
        CurrentUser principal = new CurrentUser(USER_ID, "wechat-test", "微信用户", roles, null, "token-test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static SysUser wechatUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("wechat-test");
        user.setDisplayName("微信用户");
        user.setWechatOpenid("openid-test-only");
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES);
    }

    private static FileUploadVO uploadResult() {
        return new FileUploadVO(STORED_URL, "dental/uploads/2026/10/07/test.png", "avatar.png",
                PNG_BYTES.length, "image/png");
    }
}
