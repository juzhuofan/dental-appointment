package com.dental.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.common.BusinessException;
import com.dental.department.entity.DepartmentEntity;
import com.dental.department.mapper.DepartmentMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 供医生及排班业务使用的科室读取服务。 */
@Service
public class DepartmentQueryService {

    private final DepartmentMapper departmentMapper;

    public DepartmentQueryService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public DepartmentEntity requireEnabled(long id) {
        DepartmentEntity department = departmentMapper.selectById(id);
        if (department == null || !Integer.valueOf(1).equals(department.getStatus())) {
            throw BusinessException.bad("科室不存在或已停用");
        }
        return department;
    }

    /** 与排班创建保持一致，先锁科室，再锁医生。 */
    public DepartmentEntity lockEnabled(long id) {
        DepartmentEntity department = departmentMapper.lockById(id);
        if (department == null || !Integer.valueOf(1).equals(department.getStatus())) {
            throw BusinessException.bad("科室不存在或已停用");
        }
        return department;
    }

    public List<Long> enabledIds() {
        LambdaQueryWrapper<DepartmentEntity> condition = new LambdaQueryWrapper<DepartmentEntity>()
                .select(DepartmentEntity::getId)
                .eq(DepartmentEntity::getStatus, 1);
        return departmentMapper.selectList(condition).stream().map(DepartmentEntity::getId).toList();
    }

    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<DepartmentEntity> condition = new LambdaQueryWrapper<DepartmentEntity>()
                .select(DepartmentEntity::getId, DepartmentEntity::getName)
                .in(DepartmentEntity::getId, ids);
        return departmentMapper.selectList(condition).stream()
                .collect(Collectors.toMap(DepartmentEntity::getId, DepartmentEntity::getName));
    }
}
