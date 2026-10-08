package com.dental.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.TimeUtil;
import com.dental.department.dto.ClinicDTO;
import com.dental.department.entity.SystemConfigEntity;
import com.dental.department.mapper.SystemConfigMapper;
import com.dental.department.vo.ClinicVO;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 诊所配置按白名单键读取和保存。 */
@Service
public class ClinicService {

    private static final String NAME = "clinic.name";
    private static final String PHONE = "clinic.phone";
    private static final String ADDRESS = "clinic.address";
    private static final String OPENING_HOURS = "clinic.openingHours";
    private static final String INTRODUCTION = "clinic.introduction";
    private static final String CANCEL_BEFORE = "appointment.cancel_before_minutes";
    private static final Set<String> PUBLIC_KEYS = Set.of(NAME, PHONE, ADDRESS, OPENING_HOURS,
            INTRODUCTION, CANCEL_BEFORE);
    private static final int DEFAULT_CANCEL_BEFORE_MINUTES = 120;

    private final SystemConfigMapper systemConfigMapper;
    private final OperationLogService operationLogService;

    public ClinicService(SystemConfigMapper systemConfigMapper, OperationLogService operationLogService) {
        this.systemConfigMapper = systemConfigMapper;
        this.operationLogService = operationLogService;
    }

    public ClinicVO get() {
        Map<String, SystemConfigEntity> values = readRows();
        return new ClinicVO(value(values, NAME), value(values, PHONE), value(values, ADDRESS),
                value(values, OPENING_HOURS), value(values, INTRODUCTION), cancelBefore(values));
    }

    public int defaultCancelBeforeMinutes() {
        return cancelBefore(readRows());
    }

    @Transactional
    public ClinicVO save(ClinicDTO input) {
        Map<String, String> requested = new LinkedHashMap<>();
        requested.put(NAME, input.name().trim());
        requested.put(PHONE, input.phone().trim());
        requested.put(ADDRESS, input.address().trim());
        requested.put(OPENING_HOURS, input.openingHours().trim());
        requested.put(INTRODUCTION, input.introduction().trim());
        requested.put(CANCEL_BEFORE, input.cancelBeforeMinutes().toString());
        Map<String, SystemConfigEntity> existing = readRows();
        Long currentId = CurrentUser.require().id();
        LocalDateTime now = TimeUtil.nowUtc();
        for (Map.Entry<String, String> entry : requested.entrySet()) {
            SystemConfigEntity row = existing.get(entry.getKey());
            if (row == null) {
                row = new SystemConfigEntity();
                row.setConfigKey(entry.getKey());
                row.setConfigValue(entry.getValue());
                row.setUpdatedBy(currentId);
                row.setDeleted(0);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                systemConfigMapper.insert(row);
            } else {
                row.setConfigValue(entry.getValue());
                row.setUpdatedBy(currentId);
                row.setUpdatedAt(now);
                systemConfigMapper.updateById(row);
            }
        }
        operationLogService.record("UPDATE", "SYSTEM_CONFIG", "clinic", "更新诊所公开资料与取消规则");
        return get();
    }

    private Map<String, SystemConfigEntity> readRows() {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .in(SystemConfigEntity::getConfigKey, PUBLIC_KEYS);
        List<SystemConfigEntity> rows = systemConfigMapper.selectList(wrapper);
        return rows.stream().collect(Collectors.toMap(SystemConfigEntity::getConfigKey, Function.identity()));
    }

    private String value(Map<String, SystemConfigEntity> rows, String key) {
        SystemConfigEntity row = rows.get(key);
        return row == null ? "" : row.getConfigValue();
    }

    private int cancelBefore(Map<String, SystemConfigEntity> rows) {
        String value = value(rows, CANCEL_BEFORE);
        if (value.isBlank()) {
            return DEFAULT_CANCEL_BEFORE_MINUTES;
        }
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < 0 || minutes > 10080) {
                throw BusinessException.bad("诊所取消规则配置无效");
            }
            return minutes;
        } catch (NumberFormatException invalid) {
            throw BusinessException.bad("诊所取消规则配置无效");
        }
    }
}
