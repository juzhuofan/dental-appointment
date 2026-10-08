package com.dental.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.audit.dto.OperationLogQueryDTO;
import com.dental.audit.entity.OperationLogEntity;
import com.dental.audit.mapper.OperationLogMapper;
import com.dental.audit.vo.OperationLogVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 操作日志仅新增和查询。日志摘要不得包含密码、令牌、完整手机号或主诉。 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public PageResult<OperationLogVO> list(OperationLogQueryDTO query) {
        checkPage(query.page(), query.size());
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (hasText(query.action())) {
            wrapper.eq(OperationLogEntity::getAction, query.action().trim());
        }
        if (hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(condition -> condition.like(OperationLogEntity::getOperatorName, keyword)
                    .or().like(OperationLogEntity::getSummary, keyword));
        }
        wrapper.orderByDesc(OperationLogEntity::getCreatedAt).orderByDesc(OperationLogEntity::getId);
        Page<OperationLogEntity> result = operationLogMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<OperationLogVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.page(), query.size());
    }

    @Transactional
    public void record(String action, String targetType, String targetId, String summary) {
        CurrentUser user = CurrentUser.require();
        String role = user.hasRole("ADMIN") ? "ADMIN"
                : user.hasRole("DOCTOR") ? "DOCTOR"
                : user.hasRole("PATIENT") ? "PATIENT" : null;
        recordAs(user.id(), user.displayName(), role, action, targetType, targetId, summary);
    }

    /** 登录等尚未创建 Security 上下文的操作可传入已验证身份。 */
    @Transactional
    public void recordAs(Long userId, String operatorName, String operatorRole,
                         String action, String targetType, String targetId, String summary) {
        OperationLogEntity log = new OperationLogEntity();
        log.setOperatorUserId(userId);
        log.setOperatorName(operatorName);
        log.setOperatorRole(operatorRole);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSummary(summary);
        log.setIpAddress(remoteAddress());
        log.setDeleted(0);
        log.setCreatedAt(TimeUtil.nowUtc());
        log.setUpdatedAt(log.getCreatedAt());
        operationLogMapper.insert(log);
    }

    private OperationLogVO toVO(OperationLogEntity log) {
        return new OperationLogVO(log.getId(), log.getOperatorUserId(), log.getOperatorName(),
                log.getOperatorRole(), log.getAction(), log.getTargetType(), log.getTargetId(),
                log.getSummary(), TimeUtil.toClinic(log.getCreatedAt()));
    }

    private String remoteAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String address = request.getRemoteAddr();
        return address == null || address.length() > 45 ? null : address;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void checkPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.bad("分页参数无效");
        }
    }
}
