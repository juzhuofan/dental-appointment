package com.dental.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.department.dto.DepartmentQueryDTO;
import com.dental.department.dto.DepartmentSaveDTO;
import com.dental.department.entity.DepartmentEntity;
import com.dental.department.mapper.DepartmentMapper;
import com.dental.department.vo.DepartmentVO;
import com.dental.doctor.service.DoctorService;
import com.dental.schedule.service.ScheduleService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 科室业务。停用或删除时在同一事务关闭相关排班。 */
@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final DoctorService doctorService;
    private final ScheduleService scheduleService;
    private final OperationLogService operationLogService;

    public DepartmentService(DepartmentMapper departmentMapper, DoctorService doctorService,
                             ScheduleService scheduleService, OperationLogService operationLogService) {
        this.departmentMapper = departmentMapper;
        this.doctorService = doctorService;
        this.scheduleService = scheduleService;
        this.operationLogService = operationLogService;
    }

    public PageResult<DepartmentVO> list(DepartmentQueryDTO query, boolean publicOnly) {
        checkPage(query.page(), query.size());
        LambdaQueryWrapper<DepartmentEntity> wrapper = new LambdaQueryWrapper<>();
        if (publicOnly) {
            wrapper.eq(DepartmentEntity::getStatus, 1);
        } else if (query.status() != null) {
            wrapper.eq(DepartmentEntity::getStatus, query.status());
        }
        if (hasText(query.keyword())) {
            wrapper.like(DepartmentEntity::getName, query.keyword().trim());
        }
        wrapper.orderByAsc(DepartmentEntity::getSortOrder).orderByAsc(DepartmentEntity::getId);
        Page<DepartmentEntity> result = departmentMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        List<DepartmentVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.page(), query.size());
    }

    @Transactional
    public DepartmentVO create(DepartmentSaveDTO input) {
        String name = input.name().trim();
        ensureUniqueName(name, null);
        DepartmentEntity department = new DepartmentEntity();
        copy(input, department);
        department.setName(name);
        department.setDeleted(0);
        department.setCreatedAt(TimeUtil.nowUtc());
        department.setUpdatedAt(department.getCreatedAt());
        try {
            departmentMapper.insert(department);
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.conflict("科室名称已存在");
        }
        operationLogService.record("CREATE", "DEPARTMENT", department.getId().toString(), "新增科室");
        return toVO(department);
    }

    @Transactional
    public DepartmentVO update(long id, DepartmentSaveDTO input) {
        DepartmentEntity department = requireLocked(id);
        String name = input.name().trim();
        ensureUniqueName(name, id);
        if (Integer.valueOf(1).equals(department.getStatus()) && input.status() == 0) {
            doctorService.disableByDepartment(id);
            scheduleService.closeByDepartment(id);
        }
        copy(input, department);
        department.setName(name);
        department.setUpdatedAt(TimeUtil.nowUtc());
        try {
            departmentMapper.updateById(department);
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.conflict("科室名称已存在");
        }
        operationLogService.record("UPDATE", "DEPARTMENT", Long.toString(id), "更新科室资料");
        return toVO(department);
    }

    @Transactional
    public void delete(long id) {
        requireLocked(id);
        doctorService.disableByDepartment(id);
        scheduleService.closeByDepartment(id);
        LambdaUpdateWrapper<DepartmentEntity> wrapper = new LambdaUpdateWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getId, id)
                .set(DepartmentEntity::getStatus, 0)
                .set(DepartmentEntity::getDeleted, 1)
                .set(DepartmentEntity::getUpdatedAt, TimeUtil.nowUtc());
        if (departmentMapper.update(null, wrapper) != 1) {
            throw BusinessException.conflict("科室状态已变化，请刷新后重试");
        }
        operationLogService.record("DELETE", "DEPARTMENT", Long.toString(id), "逻辑删除科室");
    }

    private DepartmentEntity requireLocked(long id) {
        DepartmentEntity department = departmentMapper.lockById(id);
        if (department == null) {
            throw BusinessException.missing("科室不存在");
        }
        return department;
    }

    private void ensureUniqueName(String name, Long exceptId) {
        LambdaQueryWrapper<DepartmentEntity> wrapper = new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getName, name);
        if (exceptId != null) {
            wrapper.ne(DepartmentEntity::getId, exceptId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw BusinessException.conflict("科室名称已存在");
        }
    }

    private void copy(DepartmentSaveDTO input, DepartmentEntity department) {
        department.setDescription(input.description());
        department.setSortOrder(input.sortOrder());
        department.setStatus(input.status());
    }

    private DepartmentVO toVO(DepartmentEntity department) {
        return new DepartmentVO(department.getId(), department.getName(), department.getDescription(),
                department.getSortOrder(), department.getStatus());
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
