package com.dental.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.department.service.ClinicService;
import com.dental.schedule.dto.ScheduleFilterDTO;
import com.dental.schedule.dto.ScheduleSaveDTO;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.entity.ScheduleStatus;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import com.dental.schedule.vo.ScheduleVO;
import com.dental.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 排班业务。写操作通过数据库行锁保证停诊与预约互斥。 */
@Service
public class ScheduleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    private final ScheduleMapper scheduleMapper;
    private final AppointmentService appointmentService;
    private final OperationLogService operationLogService;
    private final ClinicService clinicService;

    public ScheduleService(ScheduleMapper scheduleMapper, AppointmentService appointmentService,
                           OperationLogService operationLogService, ClinicService clinicService) {
        this.scheduleMapper = scheduleMapper;
        this.appointmentService = appointmentService;
        this.operationLogService = operationLogService;
        this.clinicService = clinicService;
    }

    @Transactional(readOnly = true)
    public PageResult<ScheduleVO> publicList(ScheduleFilterDTO filter) {
        return list(filter, true);
    }

    @Transactional(readOnly = true)
    public ScheduleVO publicDetail(long id) {
        ScheduleEntity schedule = scheduleMapper.findPublicSchedule(id, TimeUtil.nowUtc());
        if (schedule == null) {
            throw BusinessException.missing("可预约排班不存在或已关闭");
        }
        return toVO(schedule);
    }

    @Transactional(readOnly = true)
    public PageResult<ScheduleVO> adminList(ScheduleFilterDTO filter) {
        requireStaff();
        return list(filter, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO create(ScheduleSaveDTO input) {
        requireAdmin();
        ScheduleStatus status = ScheduleStatus.parse(input.getStatus());
        if (status != ScheduleStatus.DRAFT && status != ScheduleStatus.PUBLISHED) {
            throw BusinessException.bad("新排班只能保存为草稿或已发布");
        }
        LocalDateTime start = TimeUtil.toUtc(input.getStartTime());
        LocalDateTime end = TimeUtil.toUtc(input.getEndTime());
        validateTimeAndSlots(start, end, input.getTotalSlots(), 0);
        int cancelMinutes = cancelMinutes(input.getCancelBeforeMinutes());
        requireFuturePublication(status, start, cancelMinutes);

        DoctorAssignmentVO doctor = lockActiveDoctor(input.getDoctorId());
        ensureNoOverlap(doctor.getDoctorId(), start, end, null);

        LocalDateTime now = TimeUtil.nowUtc();
        ScheduleEntity schedule = new ScheduleEntity();
        schedule.setDoctorId(doctor.getDoctorId());
        schedule.setDepartmentId(doctor.getDepartmentId());
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setTotalSlots(input.getTotalSlots());
        schedule.setBookedSlots(0);
        schedule.setStatus(status.name());
        schedule.setCancelBeforeMinutes(cancelMinutes);
        schedule.setVersion(0);
        schedule.setCreatedBy(CurrentUser.require().id());
        schedule.setDeleted(0);
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        scheduleMapper.insert(schedule);
        operationLogService.record("SCHEDULE_CREATE", "SCHEDULE", String.valueOf(schedule.getId()), "创建排班");
        return detailForAdmin(schedule.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO update(long id, ScheduleSaveDTO input) {
        requireAdmin();
        ScheduleEntity existing = scheduleMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.missing("排班不存在");
        }
        ScheduleStatus requestedStatus = ScheduleStatus.parse(input.getStatus());
        if (!Objects.equals(existing.getStatus(), requestedStatus.name())) {
            throw BusinessException.bad("请使用排班状态接口改变状态");
        }
        LocalDateTime start = TimeUtil.toUtc(input.getStartTime());
        LocalDateTime end = TimeUtil.toUtc(input.getEndTime());
        int cancelMinutes = cancelMinutes(input.getCancelBeforeMinutes());

        DoctorAssignmentVO doctor = lockActiveDoctor(input.getDoctorId());
        ScheduleEntity locked = scheduleMapper.lockSchedule(id);
        if (locked == null) {
            throw BusinessException.missing("排班不存在");
        }
        if (!Objects.equals(locked.getStatus(), requestedStatus.name())) {
            throw BusinessException.conflict("排班状态已变化，请刷新后重试");
        }
        int booked = locked.getBookedSlots();
        validateTimeAndSlots(start, end, input.getTotalSlots(), booked);
        if (booked > 0 && (!Objects.equals(locked.getDoctorId(), doctor.getDoctorId())
                || !Objects.equals(locked.getStartTime(), start) || !Objects.equals(locked.getEndTime(), end))) {
            throw BusinessException.conflict("已有预约时不可修改医生或就诊时间");
        }
        requireFuturePublication(requestedStatus, start, cancelMinutes);
        ensureNoOverlap(doctor.getDoctorId(), start, end, id);

        locked.setDoctorId(doctor.getDoctorId());
        locked.setDepartmentId(doctor.getDepartmentId());
        locked.setStartTime(start);
        locked.setEndTime(end);
        locked.setTotalSlots(input.getTotalSlots());
        locked.setCancelBeforeMinutes(cancelMinutes);
        locked.setVersion(locked.getVersion() + 1);
        locked.setUpdatedAt(TimeUtil.nowUtc());
        scheduleMapper.updateById(locked);
        operationLogService.record("SCHEDULE_UPDATE", "SCHEDULE", String.valueOf(id), "修改排班");
        return detailForAdmin(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO changeStatus(long id, String newStatus) {
        requireAdmin();
        ScheduleStatus target = ScheduleStatus.parse(newStatus);
        ScheduleEntity locked = scheduleMapper.lockSchedule(id);
        if (locked == null) {
            throw BusinessException.missing("排班不存在");
        }
        ScheduleStatus source = ScheduleStatus.parse(locked.getStatus());
        if (source == target) {
            return detailForAdmin(id);
        }
        if (!allowedTransition(source, target)) {
            throw BusinessException.conflict("排班状态不允许此变更");
        }
        if (target == ScheduleStatus.PUBLISHED) {
            DoctorAssignmentVO doctor = scheduleMapper.findDoctor(locked.getDoctorId());
            if (doctor == null || doctor.getDoctorStatus() != 1
                    || !Objects.equals(scheduleMapper.findDepartmentStatus(doctor.getDepartmentId()), 1)) {
                throw BusinessException.conflict("所属医生或科室已停用");
            }
            requireFuturePublication(target, locked.getStartTime(), locked.getCancelBeforeMinutes());
        }
        scheduleMapper.updateStatus(id, target.name(), TimeUtil.nowUtc());
        if (target == ScheduleStatus.CLOSED || target == ScheduleStatus.CANCELLED) {
            appointmentService.cancelActiveBySchedule(id);
        }
        operationLogService.record("SCHEDULE_STATUS", "SCHEDULE", String.valueOf(id), "更新排班状态为" + target.name());
        return detailForAdmin(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        requireAdmin();
        ScheduleEntity locked = scheduleMapper.lockSchedule(id);
        if (locked == null) {
            throw BusinessException.missing("排班不存在");
        }
        scheduleMapper.updateStatus(id, ScheduleStatus.CANCELLED.name(), TimeUtil.nowUtc());
        appointmentService.cancelActiveBySchedule(id);
        scheduleMapper.softDelete(id, TimeUtil.nowUtc());
        operationLogService.record("SCHEDULE_DELETE", "SCHEDULE", String.valueOf(id), "逻辑删除排班");
    }

    /** 医生停用时先关闭号源，再取消已占用预约。调用者须先锁医生行。 */
    @Transactional(rollbackFor = Exception.class)
    public void closeByDoctor(long doctorId) {
        for (Long scheduleId : scheduleMapper.findScheduleIdsByDoctor(doctorId)) {
            closeForDeactivation(scheduleId);
        }
    }

    /** 科室停用时调用者先锁科室及该科室医生行。 */
    @Transactional(rollbackFor = Exception.class)
    public void closeByDepartment(long departmentId) {
        for (Long scheduleId : scheduleMapper.findScheduleIdsByDepartment(departmentId)) {
            closeForDeactivation(scheduleId);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAnyScheduleForDoctor(long doctorId) {
        LambdaQueryWrapper<ScheduleEntity> query = new LambdaQueryWrapper<>();
        query.eq(ScheduleEntity::getDoctorId, doctorId);
        return scheduleMapper.selectCount(query) > 0;
    }

    private void closeForDeactivation(long id) {
        ScheduleEntity locked = scheduleMapper.lockSchedule(id);
        if (locked == null) {
            return;
        }
        if (!ScheduleStatus.CANCELLED.name().equals(locked.getStatus())) {
            scheduleMapper.updateStatus(id, ScheduleStatus.CANCELLED.name(), TimeUtil.nowUtc());
        }
        appointmentService.cancelActiveBySchedule(id);
    }

    private PageResult<ScheduleVO> list(ScheduleFilterDTO filter, boolean publicOnly) {
        validatePage(filter.getPage(), filter.getSize());
        if (filter.getDateFrom() != null && filter.getDateTo() != null
                && filter.getDateFrom().isAfter(filter.getDateTo())) {
            throw BusinessException.bad("开始日期不得晚于结束日期");
        }
        if (!publicOnly && filter.getStatus() != null && !filter.getStatus().isBlank()) {
            ScheduleStatus.parse(filter.getStatus());
        }
        Long doctorScope = null;
        if (!publicOnly && !CurrentUser.require().hasRole("ADMIN")) {
            doctorScope = CurrentUser.require().doctorId();
            if (doctorScope == null) {
                return new PageResult<>(List.of(), 0L, filter.getPage(), filter.getSize());
            }
        }
        LocalDateTime from = clinicStartAsUtc(filter.getDateFrom());
        LocalDateTime until = filter.getDateTo() == null ? null : clinicStartAsUtc(filter.getDateTo().plusDays(1));
        long offset = (long) (filter.getPage() - 1) * filter.getSize();
        LocalDateTime now = TimeUtil.nowUtc();
        long total = scheduleMapper.countPage(filter, publicOnly, doctorScope, from, until, now);
        List<ScheduleEntity> schedules = total == 0 ? List.of()
                : scheduleMapper.findPage(filter, publicOnly, doctorScope, from, until, now, offset, filter.getSize());
        List<ScheduleVO> records = new ArrayList<>(schedules.size());
        for (ScheduleEntity schedule : schedules) {
            records.add(toVO(schedule));
        }
        return new PageResult<>(records, total, filter.getPage(), filter.getSize());
    }

    private ScheduleVO detailForAdmin(long id) {
        ScheduleEntity schedule = scheduleMapper.lockSchedule(id);
        if (schedule == null) {
            throw BusinessException.missing("排班不存在");
        }
        return toVO(schedule);
    }

    private static ScheduleVO toVO(ScheduleEntity schedule) {
        return new ScheduleVO(schedule.getId(), schedule.getDoctorId(), schedule.getDoctorName(),
                schedule.getDepartmentId(), schedule.getDepartmentName(), TimeUtil.toClinic(schedule.getStartTime()),
                TimeUtil.toClinic(schedule.getEndTime()), schedule.getTotalSlots(), schedule.getBookedSlots(),
                schedule.getTotalSlots() - schedule.getBookedSlots(), schedule.getStatus(),
                schedule.getCancelBeforeMinutes());
    }

    private DoctorAssignmentVO lockActiveDoctor(Long doctorId) {
        DoctorAssignmentVO initial = scheduleMapper.findDoctor(doctorId);
        if (initial == null) {
            throw BusinessException.missing("医生不存在");
        }
        Integer departmentStatus = scheduleMapper.lockDepartment(initial.getDepartmentId());
        DoctorAssignmentVO locked = scheduleMapper.lockDoctor(doctorId);
        if (locked == null || !Objects.equals(initial.getDepartmentId(), locked.getDepartmentId())
                || !Objects.equals(departmentStatus, 1) || !Objects.equals(locked.getDoctorStatus(), 1)) {
            throw BusinessException.conflict("医生或科室不可排班");
        }
        return locked;
    }

    private void ensureNoOverlap(long doctorId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (scheduleMapper.countOverlaps(doctorId, start, end, excludeId) > 0) {
            throw BusinessException.conflict("医生在该时段已有排班");
        }
    }

    private static void validateTimeAndSlots(LocalDateTime start, LocalDateTime end, Integer totalSlots, int booked) {
        if (start == null || end == null || !start.isBefore(end) || !start.isAfter(TimeUtil.nowUtc())) {
            throw BusinessException.bad("请填写未来的有效就诊时段");
        }
        if (totalSlots == null || totalSlots < 1 || totalSlots > 1000 || totalSlots < booked) {
            throw BusinessException.bad("号源总量必须在 1 至 1000 之间且不少于已预约数");
        }
    }

    private static void requireFuturePublication(ScheduleStatus status, LocalDateTime start, int cancelMinutes) {
        if (status == ScheduleStatus.PUBLISHED && !start.minusMinutes(cancelMinutes).isAfter(TimeUtil.nowUtc())) {
            throw BusinessException.conflict("该排班已超过开放预约时间");
        }
    }

    private int cancelMinutes(Integer value) {
        return value == null ? clinicService.defaultCancelBeforeMinutes() : value;
    }

    private static boolean allowedTransition(ScheduleStatus from, ScheduleStatus to) {
        return switch (from) {
            case DRAFT -> to == ScheduleStatus.PUBLISHED || to == ScheduleStatus.CANCELLED;
            case PUBLISHED -> to == ScheduleStatus.CLOSED || to == ScheduleStatus.CANCELLED;
            case CLOSED -> to == ScheduleStatus.PUBLISHED || to == ScheduleStatus.CANCELLED;
            case CANCELLED -> false;
        };
    }

    private static void validatePage(Integer page, Integer size) {
        if (page == null || page < 1 || size == null || size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.bad("分页参数无效");
        }
    }

    private static LocalDateTime clinicStartAsUtc(LocalDate day) {
        return day == null ? null : day.atStartOfDay(CLINIC_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static void requireAdmin() {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可管理排班");
        }
    }

    private static void requireStaff() {
        if (!CurrentUser.require().hasRole("ADMIN") && !CurrentUser.require().hasRole("DOCTOR")) {
            throw BusinessException.forbidden("无权查看管理端排班");
        }
    }
}
