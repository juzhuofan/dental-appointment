package com.dental.leave.service;

import com.dental.appointment.service.AppointmentService;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.leave.dto.DoctorLeaveApplyDTO;
import com.dental.leave.dto.DoctorLeaveFilterDTO;
import com.dental.leave.dto.DoctorLeaveReviewDTO;
import com.dental.leave.entity.DoctorLeaveEntity;
import com.dental.leave.entity.DoctorLeaveStatus;
import com.dental.leave.mapper.DoctorLeaveMapper;
import com.dental.leave.vo.DoctorLeaveVO;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.schedule.vo.DoctorAssignmentVO;
import com.dental.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 与排班生成共用科室→医生互斥锁；随后依次锁申请、排班、预约。 */
@Service
public class DoctorLeaveService {
    private final DoctorLeaveMapper leaves;
    private final ScheduleMapper schedules;
    private final AppointmentService appointments;
    private final OperationLogService audit;

    public DoctorLeaveService(DoctorLeaveMapper leaves, ScheduleMapper schedules,
                               AppointmentService appointments, OperationLogService audit) {
        this.leaves = leaves;
        this.schedules = schedules;
        this.appointments = appointments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<DoctorLeaveVO> list(DoctorLeaveFilterDTO filter) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("ADMIN") && !user.hasRole("DOCTOR")) {
            throw BusinessException.forbidden("仅医生和管理员可查看请假申请");
        }
        validateFilter(filter);
        Long doctorId = filter.getDoctorId();
        if (!user.hasRole("ADMIN")) {
            long ownDoctorId = requireDoctorId(user);
            if (doctorId != null && doctorId != ownDoctorId) {
                throw BusinessException.forbidden("只能查看本人的请假申请");
            }
            doctorId = ownDoctorId;
        }
        long total = leaves.countPage(doctorId, filter.getStatus());
        List<DoctorLeaveVO> records = total == 0 ? List.of()
                : leaves.findPage(doctorId, filter.getStatus(), (long) (filter.getPage() - 1) * filter.getSize(),
                        filter.getSize()).stream().map(DoctorLeaveService::toVO).toList();
        return new PageResult<>(records, total, filter.getPage(), filter.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public DoctorLeaveVO apply(DoctorLeaveApplyDTO input) {
        CurrentUser user = requireApplicant();
        LocalDateTime now = TimeUtil.nowUtc();
        if (input == null || input.getStartTime() == null || input.getEndTime() == null) {
            throw BusinessException.bad("请填写请假的开始和结束时间");
        }
        LocalDateTime start = TimeUtil.toUtc(input.getStartTime());
        LocalDateTime end = TimeUtil.toUtc(input.getEndTime());
        if (!start.isAfter(now) || !end.isAfter(start)) {
            throw BusinessException.bad("请假开始时间须晚于当前时间，结束时间须晚于开始时间");
        }
        String reason = normalizeText(input.getReason(), true, "请假原因");
        lockDoctor(requireDoctorId(user), true);
        DoctorLeaveEntity entity = new DoctorLeaveEntity();
        entity.setDoctorId(user.doctorId());
        entity.setStartTime(start);
        entity.setEndTime(end);
        entity.setReason(reason);
        entity.setStatus(DoctorLeaveStatus.PENDING.name());
        entity.setDeleted(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (leaves.insert(entity) != 1) {
            throw BusinessException.conflict("请假申请提交失败，请重试");
        }
        audit.record("DOCTOR_LEAVE_APPLY", "DOCTOR_LEAVE", String.valueOf(entity.getId()), "医生提交请假申请");
        return toVO(requireDetail(entity.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public DoctorLeaveVO review(long id, DoctorLeaveReviewDTO input) {
        CurrentUser admin = CurrentUser.requireRole("ADMIN");
        if (input == null || input.getApproved() == null) {
            throw BusinessException.bad("请选择审批结果");
        }
        boolean approved = input.getApproved();
        String remark = normalizeText(input.getRemark(), !approved, "审批备注");
        DoctorLeaveEntity observed = requireDetail(id);
        lockDoctor(observed.getDoctorId(), false);
        DoctorLeaveEntity locked = requireLocked(id);
        requireSameDoctor(observed, locked);
        requirePending(locked);

        List<Long> affected = approved ? leaves.lockOverlappingScheduleIds(locked.getDoctorId(),
                locked.getStartTime(), locked.getEndTime()) : List.of();
        // 先持有全部排班锁，再当前读预约数量，避免确认与停诊之间有预约新增/取消。
        long active = affected.isEmpty() ? 0 : leaves.countActiveAppointments(affected);
        if (active > 0 && !Boolean.TRUE.equals(input.getConfirmCancelAppointments())) {
            throw BusinessException.conflict("LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED",
                    "批准此请假将停诊 " + affected.size() + " 个排班，并取消 " + active + " 个未完成预约，请确认后再次审批");
        }
        LocalDateTime now = TimeUtil.nowUtc();
        String target = approved ? DoctorLeaveStatus.APPROVED.name() : DoctorLeaveStatus.REJECTED.name();
        if (leaves.reviewPending(id, target, admin.id(), remark, now) != 1) {
            throw BusinessException.conflict("请假申请状态已变化，请刷新后重试");
        }
        for (Long scheduleId : affected) {
            ScheduleEntity schedule = schedules.lockSchedule(scheduleId);
            if (schedule == null) {
                throw BusinessException.conflict("受影响排班已变化，请刷新后重试");
            }
            // Only previously active shifts are eligible as a future copy source.
            // A draft cancelled for leave must never become a published shift next week.
            if (!"DRAFT".equals(schedule.getStatus())) {
                leaves.linkCancelledSchedule(id, scheduleId);
            }
            if (schedules.updateStatus(scheduleId, "CANCELLED", now) != 1) {
                throw BusinessException.conflict("排班停诊失败，请刷新后重试");
            }
            appointments.cancelActiveBySchedule(scheduleId);
        }
        audit.record("DOCTOR_LEAVE_REVIEW", "DOCTOR_LEAVE", String.valueOf(id),
                approved ? "批准请假，停诊排班 " + affected.size() + " 个，取消未完成预约 " + active + " 个" : "驳回请假申请");
        return toVO(requireDetail(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public DoctorLeaveVO withdraw(long id) {
        CurrentUser user = requireApplicant();
        long doctorId = requireDoctorId(user);
        DoctorLeaveEntity observed = requireDetail(id);
        if (!Objects.equals(observed.getDoctorId(), doctorId)) {
            throw BusinessException.forbidden("只能撤回本人的请假申请");
        }
        lockDoctor(doctorId, false);
        DoctorLeaveEntity locked = requireLocked(id);
        if (!Objects.equals(locked.getDoctorId(), doctorId)) {
            throw BusinessException.forbidden("只能撤回本人的请假申请");
        }
        requirePending(locked);
        if (leaves.withdrawPending(id, doctorId, TimeUtil.nowUtc()) != 1) {
            throw BusinessException.conflict("请假申请状态已变化，请刷新后重试");
        }
        audit.record("DOCTOR_LEAVE_WITHDRAW", "DOCTOR_LEAVE", String.valueOf(id), "医生撤回请假申请");
        return toVO(requireDetail(id));
    }

    private void lockDoctor(long doctorId, boolean requireActive) {
        DoctorAssignmentVO observed = schedules.findDoctor(doctorId);
        if (observed == null) {
            throw BusinessException.conflict("关联医生不存在或已停用，请联系管理员");
        }
        Integer departmentStatus = schedules.lockDepartment(observed.getDepartmentId());
        DoctorAssignmentVO locked = schedules.lockDoctor(doctorId);
        if (locked == null || departmentStatus == null
                || !Objects.equals(observed.getDepartmentId(), locked.getDepartmentId())) {
            throw BusinessException.conflict("医生或科室信息已变化，请刷新后重试");
        }
        if (requireActive && (!Objects.equals(departmentStatus, 1) || !Objects.equals(locked.getDoctorStatus(), 1))) {
            throw BusinessException.conflict("医生或所属科室已停用，暂不能申请请假");
        }
    }

    private DoctorLeaveEntity requireDetail(long id) {
        DoctorLeaveEntity entity = leaves.findDetail(id);
        if (entity == null) { throw BusinessException.missing("请假申请不存在"); }
        return entity;
    }

    private DoctorLeaveEntity requireLocked(long id) {
        DoctorLeaveEntity entity = leaves.lockLeave(id);
        if (entity == null) { throw BusinessException.missing("请假申请不存在"); }
        return entity;
    }

    private static void requireSameDoctor(DoctorLeaveEntity observed, DoctorLeaveEntity locked) {
        if (!Objects.equals(observed.getDoctorId(), locked.getDoctorId())) {
            throw BusinessException.conflict("请假申请信息已变化，请刷新后重试");
        }
    }

    private static void requirePending(DoctorLeaveEntity entity) {
        if (!DoctorLeaveStatus.PENDING.name().equals(entity.getStatus())) {
            throw BusinessException.conflict("仅待审批的请假申请可以审批或撤回");
        }
    }

    private static CurrentUser requireApplicant() {
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("DOCTOR") || user.hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅医生可提交或撤回本人请假申请");
        }
        requireDoctorId(user);
        return user;
    }

    private static long requireDoctorId(CurrentUser user) {
        if (user.doctorId() == null) {
            throw BusinessException.forbidden("当前账号尚未关联医生档案，请联系管理员");
        }
        return user.doctorId();
    }

    private static void validateFilter(DoctorLeaveFilterDTO filter) {
        if (filter == null || filter.getPage() == null || filter.getPage() < 1 || filter.getSize() == null
                || filter.getSize() < 1 || filter.getSize() > 100) {
            throw BusinessException.bad("分页参数无效");
        }
        if (filter.getDoctorId() != null && filter.getDoctorId() < 1) {
            throw BusinessException.bad("医生编号无效");
        }
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            filter.setStatus(DoctorLeaveStatus.parse(filter.getStatus().trim()).name());
        } else {
            filter.setStatus(null);
        }
    }

    private static String normalizeText(String text, boolean required, String label) {
        String normalized = text == null ? "" : text.trim();
        if ((required && normalized.isEmpty()) || normalized.length() > 500) {
            throw BusinessException.bad(label + (required ? "须填写且不能超过 500 字" : "不能超过 500 字"));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static DoctorLeaveVO toVO(DoctorLeaveEntity entity) {
        return new DoctorLeaveVO(entity.getId(), entity.getDoctorId(), entity.getDoctorName(), entity.getDepartmentName(),
                TimeUtil.toClinic(entity.getStartTime()), TimeUtil.toClinic(entity.getEndTime()), entity.getReason(),
                entity.getStatus(), entity.getReviewedBy(), entity.getReviewRemark(), TimeUtil.toClinic(entity.getReviewedAt()),
                TimeUtil.toClinic(entity.getCreatedAt()), TimeUtil.toClinic(entity.getUpdatedAt()));
    }
}
