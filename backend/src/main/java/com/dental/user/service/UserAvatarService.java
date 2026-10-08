package com.dental.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.file.service.FileUploadService;
import com.dental.file.vo.FileUploadVO;
import com.dental.security.CurrentUser;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.vo.UserVO;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 本人微信头像上传与保存；外部上传不占用数据库行锁，更新时再次核验账号状态。 */
@Service
public class UserAvatarService {

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024L;
    private static final Set<String> AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final SysUserMapper userMapper;
    private final UserService userService;
    private final FileUploadService fileUploadService;
    private final OperationLogService audit;
    private final TransactionTemplate transaction;

    public UserAvatarService(SysUserMapper userMapper, UserService userService,
            FileUploadService fileUploadService, OperationLogService audit,
            PlatformTransactionManager transactionManager) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.fileUploadService = fileUploadService;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public UserVO upload(MultipartFile file) {
        Long userId = CurrentUser.requireRole("PATIENT").id();
        requireWechatUser(userService.requireActiveUser(userId));
        validateAvatar(file);
        FileUploadVO upload = fileUploadService.upload(file);
        SysUser saved = transaction.execute(status -> {
            SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getId, userId).last("FOR UPDATE"));
            requireWechatUser(user);
            userService.requireRole(userId, "PATIENT");
            user.setAvatarUrl(upload.url());
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            userMapper.updateById(user);
            audit.record("AVATAR_UPDATE", "sys_user", String.valueOf(userId), "保存本人微信授权选择的头像");
            return user;
        });
        return userService.toUserVO(saved);
    }

    private static void requireWechatUser(SysUser user) {
        if (user == null || !Objects.equals(user.getStatus(), 1) || !StringUtils.hasText(user.getWechatOpenid())) {
            throw BusinessException.forbidden("仅有效微信患者账号可设置头像");
        }
    }

    private static void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.bad("请选择头像");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "头像不能超过 2MB", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !AVATAR_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw BusinessException.bad("头像仅支持 jpg、png、gif 或 webp 图片");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            String type = extension.toLowerCase(Locale.ROOT);
            boolean jpeg = header.length >= 3 && header[0] == (byte) 255
                    && header[1] == (byte) 216 && header[2] == (byte) 255;
            boolean png = header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8), PNG_SIGNATURE);
            boolean gif = header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
            boolean webp = header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B'
                    && header[11] == 'P';
            boolean valid = switch (type) {
                case "jpg", "jpeg" -> jpeg;
                case "png" -> png;
                case "gif" -> gif;
                case "webp" -> webp;
                default -> false;
            };
            if (!valid) {
                throw BusinessException.bad("头像文件内容与图片类型不匹配");
            }
        } catch (IOException exception) {
            throw BusinessException.bad("无法读取头像，请重新选择");
        }
    }
}
