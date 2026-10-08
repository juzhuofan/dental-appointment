package com.dental.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.audit.service.OperationLogService;
import com.dental.auth.entity.AuthToken;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.config.WechatProperties;
import com.dental.file.service.FileUploadService;
import com.dental.security.CurrentUser;
import com.dental.user.dto.PatientProfileSaveDTO;
import com.dental.user.dto.AccountProfileSaveDTO;
import com.dental.user.dto.UserSaveDTO;
import com.dental.user.entity.PatientProfile;
import com.dental.user.entity.SysRole;
import com.dental.user.entity.SysUser;
import com.dental.user.entity.SysUserRole;
import com.dental.user.mapper.PatientProfileMapper;
import com.dental.user.mapper.SysRoleMapper;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.mapper.SysUserRoleMapper;
import com.dental.user.vo.AdminUserVO;
import com.dental.user.vo.PatientProfileVO;
import com.dental.user.vo.UserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 用户账号、角色及默认就诊人业务。 */
@Service
public class UserService {
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_PATIENT = "PATIENT";
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int GENDER_UNKNOWN = 0;
    private static final int TOKEN_ACTIVE = 0;
    private static final int TOKEN_REVOKED = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PatientProfileMapper profileMapper;
    private final AuthTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService audit;
    private final List<UserDeactivationHandler> deactivationHandlers;
    private final FileUploadService fileUploadService;
    private final WechatProperties wechatProperties;

    public UserService(
            SysUserMapper userMapper,
            SysRoleMapper roleMapper,
            SysUserRoleMapper userRoleMapper,
            PatientProfileMapper profileMapper,
            AuthTokenMapper tokenMapper,
            PasswordEncoder passwordEncoder,
            OperationLogService audit,
            List<UserDeactivationHandler> deactivationHandlers,
            FileUploadService fileUploadService,
            WechatProperties wechatProperties) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.profileMapper = profileMapper;
        this.tokenMapper = tokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.deactivationHandlers = deactivationHandlers;
        this.fileUploadService = fileUploadService;
        this.wechatProperties = wechatProperties;
    }

    public SysUser getUser(Long userId) {
        return userMapper.selectById(userId);
    }

    public SysUser requireUser(Long userId) {
        SysUser user = getUser(userId);
        if (user == null) {
            throw BusinessException.missing("账号不存在");
        }
        return user;
    }

    public SysUser requireActiveUser(Long userId) {
        SysUser user = requireUser(userId);
        if (!Objects.equals(user.getStatus(), STATUS_ENABLED)) {
            throw BusinessException.forbidden("账号已停用");
        }
        return user;
    }

    public List<String> rolesOf(Long userId) {
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (links.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = new HashSet<>(links.size());
        for (SysUserRole link : links) {
            roleIds.add(link.getRoleId());
        }
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        return roles.stream().map(SysRole::getRoleCode).sorted().toList();
    }

    public boolean hasRole(Long userId, String roleCode) {
        return rolesOf(userId).contains(roleCode);
    }

    public void requireRole(Long userId, String roleCode) {
        if (!hasRole(userId, roleCode)) {
            throw BusinessException.forbidden("当前账号没有操作权限");
        }
    }

    public SysUser requireEnabledDoctorUser(Long userId) {
        SysUser user = requireActiveUser(userId);
        requireRole(userId, ROLE_DOCTOR);
        return user;
    }

    /** 本地演示数据种子按用户名解析可关联的医生账号。 */
    public Long findEnabledDoctorUserIdByUsername(String username) {
        if (!hasText(username)) {
            return null;
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username.trim()));
        return user != null && Objects.equals(user.getStatus(), STATUS_ENABLED)
                && hasRole(user.getId(), ROLE_DOCTOR) ? user.getId() : null;
    }

    public UserVO toUserVO(SysUser user) {
        boolean wechatBound = hasText(user.getWechatOpenid());
        boolean hasAvatar = hasText(user.getAvatarUrl());
        boolean profileCompleted = wechatBound
                && (!wechatProperties.isPhoneNumberEnabled() || hasText(user.getPhone()));
        return new UserVO(
                user.getId(), user.getUsername(), user.getDisplayName(),
                rolesOf(user.getId()), userMapper.findActiveDoctorId(user.getId()),
                user.getPhone(), user.getAvatarUrl(),
                hasAvatar ? fileUploadService.signedReadUrl(user.getAvatarUrl()) : null,
                wechatBound, profileCompleted);
    }

    public UserVO currentUser() {
        return toUserVO(requireActiveUser(CurrentUser.require().id()));
    }

    public PatientProfile findPatientProfile(Long userId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<PatientProfile>()
                .eq(PatientProfile::getUserId, userId)
                .orderByDesc(PatientProfile::getIsDefault)
                .orderByAsc(PatientProfile::getId).last("LIMIT 1"));
    }

    public List<PatientProfileVO> myPatientProfiles() {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        requireActiveUser(userId);
        return profileMapper.selectList(new LambdaQueryWrapper<PatientProfile>()
                .eq(PatientProfile::getUserId, userId)
                .orderByDesc(PatientProfile::getIsDefault)
                .orderByAsc(PatientProfile::getId))
                .stream().map(profile -> toProfileVO(profile, false)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO saveAccountProfile(AccountProfileSaveDTO request) {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId).last("FOR UPDATE"));
        if (user == null || !Objects.equals(user.getStatus(), STATUS_ENABLED)
                || !hasText(user.getWechatOpenid())) {
            throw BusinessException.forbidden("仅有效微信患者账号可设置昵称");
        }
        String displayName = request.displayName().trim();
        if (displayName.isEmpty()) {
            throw BusinessException.bad("昵称不能为空");
        }
        user.setDisplayName(displayName);
        user.setUpdatedAt(utcNow());
        userMapper.updateById(user);
        audit.record("ACCOUNT_PROFILE_UPDATE", "sys_user", String.valueOf(userId), "更新本人账号昵称");
        return toUserVO(user);
    }

    public PatientProfile requirePatientProfile(Long profileId, Long patientUserId) {
        PatientProfile profile = profileMapper.selectById(profileId);
        if (profile == null || !Objects.equals(profile.getUserId(), patientUserId)) {
            throw BusinessException.missing("就诊人资料不存在");
        }
        return profile;
    }

    public PatientProfileVO currentProfile() {
        requireRole(CurrentUser.require().id(), ROLE_PATIENT);
        PatientProfile profile = findPatientProfile(CurrentUser.require().id());
        if (profile == null) {
            throw BusinessException.missing("就诊人资料不存在");
        }
        return toProfileVO(profile, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public PatientProfileVO saveCurrentProfile(PatientProfileSaveDTO request) {
        Long userId = CurrentUser.require().id();
        requireRole(userId, ROLE_PATIENT);
        requireActiveUser(userId);
        PatientProfile current = findPatientProfile(userId);
        if (current != null) {
            return updatePatientProfile(current.getId(), request);
        }
        return createPatientProfile(request);
    }

    @Transactional(rollbackFor = Exception.class)
    public PatientProfileVO createPatientProfile(PatientProfileSaveDTO request) {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        lockActivePatientAccount(userId);
        LocalDateTime now = utcNow();
        PatientProfile profile = new PatientProfile();
        profile.setUserId(userId);
        profile.setIsDefault(findPatientProfile(userId) == null ? 1 : 0);
        profile.setDeleted(NOT_DELETED);
        profile.setCreatedAt(now);
        applyPatientProfile(profile, request, now);
        profileMapper.insert(profile);
        audit.record("PROFILE_CREATE", "patient_profile", String.valueOf(profile.getId()), "新增就诊人");
        return toProfileVO(profile, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public PatientProfileVO updatePatientProfile(Long profileId, PatientProfileSaveDTO request) {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        lockActivePatientAccount(userId);
        PatientProfile profile = lockOwnedProfile(profileId, userId);
        applyPatientProfile(profile, request, utcNow());
        profileMapper.updateById(profile);
        audit.record("PROFILE_UPDATE", "patient_profile", String.valueOf(profile.getId()), "更新就诊人资料");
        return toProfileVO(profile, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public PatientProfileVO setDefaultPatientProfile(Long profileId) {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        lockActivePatientAccount(userId);
        PatientProfile target = lockOwnedProfile(profileId, userId);
        if (!Objects.equals(target.getIsDefault(), 1)) {
            LocalDateTime now = utcNow();
            profileMapper.update(null, new LambdaUpdateWrapper<PatientProfile>()
                    .eq(PatientProfile::getUserId, userId)
                    .eq(PatientProfile::getIsDefault, 1)
                    .set(PatientProfile::getIsDefault, 0)
                    .set(PatientProfile::getUpdatedAt, now));
            target.setIsDefault(1);
            target.setUpdatedAt(now);
            profileMapper.updateById(target);
            audit.record("PROFILE_DEFAULT", "patient_profile", String.valueOf(profileId), "设置默认就诊人");
        }
        return toProfileVO(target, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePatientProfile(Long profileId) {
        Long userId = CurrentUser.requireRole(ROLE_PATIENT).id();
        lockActivePatientAccount(userId);
        PatientProfile target = lockOwnedProfile(profileId, userId);
        List<PatientProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<PatientProfile>()
                .eq(PatientProfile::getUserId, userId).orderByAsc(PatientProfile::getId));
        if (profiles.size() <= 1) {
            throw BusinessException.conflict("LAST_PATIENT_PROFILE", "请至少保留一位就诊人");
        }
        if (profileMapper.countActiveAppointments(profileId, userId) > 0) {
            throw BusinessException.conflict("PATIENT_HAS_APPOINTMENTS", "该就诊人有未完成预约，请先处理预约");
        }
        LocalDateTime now = utcNow();
        profileMapper.update(null, new LambdaUpdateWrapper<PatientProfile>()
                .eq(PatientProfile::getId, profileId)
                .eq(PatientProfile::getUserId, userId)
                .set(PatientProfile::getDeleted, DELETED)
                .set(PatientProfile::getIsDefault, 0)
                .set(PatientProfile::getUpdatedAt, now));
        if (Objects.equals(target.getIsDefault(), 1)) {
            PatientProfile replacement = profiles.stream()
                    .filter(profile -> !Objects.equals(profile.getId(), profileId))
                    .findFirst().orElseThrow();
            replacement.setIsDefault(1);
            replacement.setUpdatedAt(now);
            profileMapper.updateById(replacement);
        }
        audit.record("PROFILE_DELETE", "patient_profile", String.valueOf(profileId), "逻辑删除就诊人");
    }

    private SysUser lockActivePatientAccount(Long userId) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId).last("FOR UPDATE"));
        if (user == null || !Objects.equals(user.getStatus(), STATUS_ENABLED)) {
            throw BusinessException.forbidden("账号已停用");
        }
        return user;
    }

    private PatientProfile lockOwnedProfile(Long profileId, Long userId) {
        PatientProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<PatientProfile>()
                .eq(PatientProfile::getId, profileId)
                .eq(PatientProfile::getUserId, userId).last("FOR UPDATE"));
        if (profile == null) {
            throw BusinessException.missing("就诊人资料不存在");
        }
        return profile;
    }

    private static void applyPatientProfile(PatientProfile profile, PatientProfileSaveDTO request,
            LocalDateTime now) {
        profile.setRealName(request.realName().trim());
        profile.setPhone(request.phone());
        profile.setGender(request.gender() == null ? GENDER_UNKNOWN : request.gender());
        profile.setBirthDate(request.birthDate());
        profile.setRemark(request.remark());
        profile.setUpdatedAt(now);
    }

    public PageResult<PatientProfileVO> listPatients(String keyword, int page, int size) {
        CurrentUser.requireRole(ROLE_ADMIN);
        validatePage(page, size);
        LambdaQueryWrapper<PatientProfile> query = new LambdaQueryWrapper<PatientProfile>()
                .orderByDesc(PatientProfile::getId);
        if (hasText(keyword)) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper.like(PatientProfile::getRealName, trimmed)
                    .or().like(PatientProfile::getPhone, trimmed));
        }
        Page<PatientProfile> results = profileMapper.selectPage(new Page<>(page, size), query);
        List<PatientProfileVO> records = results.getRecords().stream()
                .map(profile -> toProfileVO(profile, true)).toList();
        return new PageResult<>(records, results.getTotal(), page, size);
    }

    public PageResult<AdminUserVO> listUsers(
            String keyword, String role, Integer status, int page, int size) {
        CurrentUser.requireRole(ROLE_ADMIN);
        validatePage(page, size);
        if (status != null && status != STATUS_DISABLED && status != STATUS_ENABLED) {
            throw BusinessException.bad("账号状态参数不正确");
        }
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getId);
        if (hasText(keyword)) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper.like(SysUser::getUsername, trimmed)
                    .or().like(SysUser::getDisplayName, trimmed));
        }
        if (status != null) {
            query.eq(SysUser::getStatus, status);
        }
        if (hasText(role)) {
            SysRole selected = findRole(role);
            if (selected == null) {
                throw BusinessException.bad("角色参数不正确");
            }
            List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, selected.getId()));
            if (links.isEmpty()) {
                return new PageResult<>(List.of(), 0L, page, size);
            }
            query.in(SysUser::getId, links.stream().map(SysUserRole::getUserId).toList());
        }
        Page<SysUser> results = userMapper.selectPage(new Page<>(page, size), query);
        Map<Long, List<String>> roleMap = rolesForUsers(results.getRecords());
        List<AdminUserVO> records = results.getRecords().stream()
                .map(user -> toAdminUserVO(user, roleMap.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(records, results.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO saveUser(Long userId, UserSaveDTO request) {
        CurrentUser.requireRole(ROLE_ADMIN);
        lockAdminRole();
        SysRole requestedRole = requireKnownRole(request.role());
        LocalDateTime now = utcNow();
        SysUser existing = userId == null ? null : lockActiveUser(userId);
        if (userId != null && existing == null) {
            throw BusinessException.missing("账号不存在");
        }
        if (userId == null && !hasText(request.password())) {
            throw BusinessException.bad("新增账号必须填写至少8位密码");
        }
        String username = request.username().trim();
        SysUser duplicate = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (duplicate != null && !Objects.equals(duplicate.getId(), userId)) {
            throw BusinessException.conflict("USERNAME_EXISTS", "登录账号已存在");
        }
        List<String> oldRoles = existing == null ? List.of() : rolesOf(userId);
        if (existing != null) {
            if (existing.getDemoDeviceHash() != null
                    && (!ROLE_PATIENT.equals(request.role())
                    || !Objects.equals(username, existing.getUsername())
                    || hasText(request.password()))) {
                throw BusinessException.bad("演示患者账号不能更换角色、用户名或设置密码");
            }
            if (hasText(existing.getWechatOpenid())
                    && (!ROLE_PATIENT.equals(request.role())
                    || !Objects.equals(username, existing.getUsername())
                    || hasText(request.password()))) {
                throw BusinessException.bad("微信患者账号不能更换角色、用户名或设置密码");
            }
            protectAdministrator(existing, oldRoles, request.status(), request.role());
            if (Objects.equals(existing.getStatus(), STATUS_ENABLED)
                    && (!Objects.equals(request.status(), STATUS_ENABLED)
                    || !oldRoles.contains(request.role()))) {
                deactivateRelatedBusiness(userId, oldRoles);
            }
        }
        SysUser user = existing == null ? new SysUser() : existing;
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setStatus(request.status());
        user.setUpdatedAt(now);
        if (existing == null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setDeleted(NOT_DELETED);
            user.setCreatedAt(now);
        } else if (hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        try {
            if (existing == null) {
                userMapper.insert(user);
            } else {
                userMapper.updateById(user);
                revokeUserTokens(user.getId());
            }
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("USERNAME_EXISTS", "登录账号已存在");
        }
        replaceRoles(user.getId(), requestedRole, now);
        if (ROLE_PATIENT.equals(request.role())) {
            ensurePatientProfile(user.getId(), user.getDisplayName());
        }
        audit.record(existing == null ? "USER_CREATE" : "USER_UPDATE", "sys_user",
                String.valueOf(user.getId()), "维护登录账号及角色");
        return toAdminUserVO(user, List.of(request.role()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        CurrentUser.requireRole(ROLE_ADMIN);
        lockAdminRole();
        SysUser user = lockActiveUser(userId);
        if (user == null) {
            throw BusinessException.missing("账号不存在");
        }
        List<String> roles = rolesOf(userId);
        protectAdministrator(user, roles, STATUS_DISABLED, "");
        deactivateRelatedBusiness(userId, roles);
        LocalDateTime now = utcNow();
        profileMapper.update(null, new LambdaUpdateWrapper<PatientProfile>()
                .eq(PatientProfile::getUserId, userId)
                .set(PatientProfile::getDeleted, DELETED)
                .set(PatientProfile::getUpdatedAt, now));
        softDeleteRoles(userId, now);
        revokeUserTokens(userId);
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getDeleted, DELETED)
                .set(SysUser::getUpdatedAt, now));
        audit.record("USER_DELETE", "sys_user", String.valueOf(userId), "逻辑删除账号并撤销登录凭证");
    }

    public long countActivePatients() {
        SysRole patientRole = findRole(ROLE_PATIENT);
        if (patientRole == null) {
            return 0L;
        }
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, patientRole.getId()));
        if (links.isEmpty()) {
            return 0L;
        }
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, links.stream().map(SysUserRole::getUserId).toList())
                .eq(SysUser::getStatus, STATUS_ENABLED));
    }

    @Transactional(rollbackFor = Exception.class)
    public void initializeDemoPatient(SysUser user) {
        List<String> existingRoles = rolesOf(user.getId());
        if (!existingRoles.isEmpty() && !existingRoles.equals(List.of(ROLE_PATIENT))) {
            throw BusinessException.forbidden("演示账号角色不正确");
        }
        if (existingRoles.isEmpty()) {
            bindRole(user.getId(), requireKnownRole(ROLE_PATIENT), utcNow());
        }
        ensurePatientProfile(user.getId(), "演示患者" + user.getId());
    }

    /** 微信开户只绑定患者角色，姓名与未授权手机号留空，等待用户填写真实就诊资料。 */
    @Transactional(rollbackFor = Exception.class)
    public void initializeWechatPatient(SysUser user) {
        List<String> roles = rolesOf(user.getId());
        if (!roles.isEmpty() && !roles.equals(List.of(ROLE_PATIENT))) {
            throw BusinessException.forbidden("微信账号角色不正确");
        }
        LocalDateTime now = utcNow();
        if (roles.isEmpty()) {
            bindRole(user.getId(), requireKnownRole(ROLE_PATIENT), now);
        }
        PatientProfile profile = findPatientProfile(user.getId());
        if (profile == null) {
            profile = profileMapper.lockIncludingDeleted(user.getId());
        }
        if (profile == null) {
            profile = new PatientProfile();
            profile.setUserId(user.getId());
            profile.setIsDefault(1);
            profile.setRealName("");
            profile.setPhone(hasText(user.getPhone()) ? user.getPhone() : "");
            profile.setGender(GENDER_UNKNOWN);
            profile.setDeleted(NOT_DELETED);
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            profileMapper.insert(profile);
        } else if (Objects.equals(profile.getDeleted(), DELETED)) {
            throw BusinessException.forbidden("就诊人资料已停用，请联系诊所");
        } else if (!hasText(profile.getPhone()) && hasText(user.getPhone())) {
            profile.setPhone(user.getPhone());
            profile.setUpdatedAt(now);
            profileMapper.updateById(profile);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean initializeLocalAdmin(String username, String rawPassword) {
        if (!hasText(username) || !hasText(rawPassword)) {
            return false;
        }
        lockAdminRole();
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null) {
            return false;
        }
        LocalDateTime now = utcNow();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName("本地演示管理员");
        user.setStatus(STATUS_ENABLED);
        user.setDeleted(NOT_DELETED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        bindRole(user.getId(), requireKnownRole(ROLE_ADMIN), now);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long initializeLocalDoctor(String username, String rawPassword) {
        if (!hasText(username) || !hasText(rawPassword)) {
            return null;
        }
        SysRole doctorRole = requireKnownRole(ROLE_DOCTOR);
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null) {
            if (!Objects.equals(existing.getStatus(), STATUS_ENABLED)
                    || !rolesOf(existing.getId()).contains(ROLE_DOCTOR)) {
                throw BusinessException.conflict("DEMO_DOCTOR_CONFLICT", "演示医生账号名称已被其他账号占用");
            }
            return existing.getId();
        }
        LocalDateTime now = utcNow();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName("本地演示医生");
        user.setStatus(STATUS_ENABLED);
        user.setDeleted(NOT_DELETED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        bindRole(user.getId(), doctorRole, now);
        return user.getId();
    }

    private void protectAdministrator(SysUser user, List<String> oldRoles, int targetStatus, String targetRole) {
        boolean removingAdmin = oldRoles.contains(ROLE_ADMIN)
                && (targetStatus != STATUS_ENABLED || !ROLE_ADMIN.equals(targetRole));
        if (Objects.equals(user.getId(), CurrentUser.require().id()) && removingAdmin) {
            throw BusinessException.bad("不能停用自己或移除自己的管理员角色");
        }
        if (removingAdmin && Objects.equals(user.getStatus(), STATUS_ENABLED)
                && activeAdminCount() <= 1) {
            throw BusinessException.conflict("LAST_ADMIN_PROTECTED", "不能停用或删除最后一个启用管理员");
        }
    }

    private long activeAdminCount() {
        SysRole admin = requireKnownRole(ROLE_ADMIN);
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, admin.getId()));
        if (links.isEmpty()) {
            return 0L;
        }
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, links.stream().map(SysUserRole::getUserId).toList())
                .eq(SysUser::getStatus, STATUS_ENABLED));
    }

    private void deactivateRelatedBusiness(Long userId, List<String> roles) {
        Set<String> currentRoles = Set.copyOf(roles);
        for (UserDeactivationHandler handler : deactivationHandlers) {
            handler.beforeDeactivate(userId, currentRoles);
        }
    }

    private void replaceRoles(Long userId, SysRole role, LocalDateTime now) {
        softDeleteRoles(userId, now);
        bindRole(userId, role, now);
    }

    private void softDeleteRoles(Long userId, LocalDateTime now) {
        userRoleMapper.update(null, new LambdaUpdateWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .set(SysUserRole::getDeleted, DELETED)
                .set(SysUserRole::getUpdatedAt, now));
    }

    private void bindRole(Long userId, SysRole role, LocalDateTime now) {
        SysUserRole existing = userRoleMapper.lockIncludingDeleted(userId, role.getId());
        if (existing == null) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            link.setDeleted(NOT_DELETED);
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            userRoleMapper.insert(link);
        } else if (Objects.equals(existing.getDeleted(), DELETED)) {
            userRoleMapper.restore(existing.getId(), now);
        }
    }

    private void ensurePatientProfile(Long userId, String displayName) {
        if (findPatientProfile(userId) != null) {
            return;
        }
        PatientProfile profile = profileMapper.lockIncludingDeleted(userId);
        if (profile != null) {
            if (Objects.equals(profile.getDeleted(), DELETED)) {
                profileMapper.restore(profile.getId(), utcNow());
            }
            return;
        }
        LocalDateTime now = utcNow();
        PatientProfile created = new PatientProfile();
        created.setUserId(userId);
        created.setIsDefault(1);
        created.setRealName(displayName);
        created.setPhone("13800000000");
        created.setGender(GENDER_UNKNOWN);
        created.setDeleted(NOT_DELETED);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        profileMapper.insert(created);
    }

    private void revokeUserTokens(Long userId) {
        tokenMapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getUserId, userId)
                .eq(AuthToken::getRevoked, TOKEN_ACTIVE)
                .set(AuthToken::getRevoked, TOKEN_REVOKED)
                .set(AuthToken::getUpdatedAt, utcNow()));
    }

    private Map<Long, List<String>> rolesForUsers(List<SysUser> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> roleIds = new HashSet<>(links.size());
        for (SysUserRole link : links) {
            roleIds.add(link.getRoleId());
        }
        Map<Long, String> roleCodes = new HashMap<>(roleIds.size());
        for (SysRole role : roleMapper.selectByIds(roleIds)) {
            roleCodes.put(role.getId(), role.getRoleCode());
        }
        Map<Long, List<String>> result = new HashMap<>(users.size());
        for (SysUserRole link : links) {
            String code = roleCodes.get(link.getRoleId());
            if (code != null) {
                result.computeIfAbsent(link.getUserId(), ignored -> new ArrayList<>()).add(code);
            }
        }
        return result;
    }

    private AdminUserVO toAdminUserVO(SysUser user, List<String> roles) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getDisplayName(),
                roles.isEmpty() ? null : roles.get(0), List.copyOf(roles), user.getStatus(),
                toDisplayTime(user.getCreatedAt()), toDisplayTime(user.getUpdatedAt()));
    }

    private PatientProfileVO toProfileVO(PatientProfile profile, boolean masked) {
        return new PatientProfileVO(profile.getId(), profile.getUserId(),
                Objects.equals(profile.getIsDefault(), 1), profile.getRealName(),
                masked ? maskPhone(profile.getPhone()) : profile.getPhone(), profile.getGender(),
                profile.getBirthDate(), profile.getRemark());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private SysRole findRole(String roleCode) {
        return roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
    }

    private SysRole requireKnownRole(String roleCode) {
        SysRole role = findRole(roleCode);
        if (role == null) {
            throw BusinessException.missing("角色不存在");
        }
        return role;
    }

    private void lockAdminRole() {
        SysRole admin = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, ROLE_ADMIN).last("FOR UPDATE"));
        if (admin == null) {
            throw BusinessException.missing("管理员角色尚未初始化");
        }
    }

    private SysUser lockActiveUser(Long userId) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, userId).last("FOR UPDATE"));
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.bad("分页参数不正确");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static OffsetDateTime toDisplayTime(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE).toOffsetDateTime();
    }
}
