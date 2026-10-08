package com.dental.appointment.service;

import com.dental.appointment.dto.AppointmentCreateDTO;
import com.dental.appointment.dto.AppointmentFilterDTO;
import com.dental.appointment.entity.AppointmentEntity;
import com.dental.appointment.entity.AppointmentIdempotencyEntity;
import com.dental.appointment.entity.AppointmentStatus;
import com.dental.appointment.mapper.AppointmentIdempotencyMapper;
import com.dental.appointment.mapper.AppointmentMapper;
import com.dental.appointment.vo.AppointmentVO;
import com.dental.appointment.vo.BookableScheduleVO;
import com.dental.appointment.vo.DailyTrendVO;
import com.dental.appointment.vo.DashboardVO;
import com.dental.appointment.vo.PatientSnapshotVO;
import com.dental.audit.service.OperationLogService;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.TimeUtil;
import com.dental.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 预约核心流程。所有号源写操作在一个事务内按排班、预约顺序上锁。 */
@Service
public class AppointmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int DEFAULT_CANCEL_BEFORE_MINUTES = 120;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AppointmentMapper appointmentMapper;
    private final AppointmentIdempotencyMapper idempotencyMapper;
    private final OperationLogService operationLogService;

    public AppointmentService(AppointmentMapper appointmentMapper,
                              AppointmentIdempotencyMapper idempotencyMapper,
                              OperationLogService operationLogService) {
        this.appointmentMapper = appointmentMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.operationLogService = operationLogService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO create(AppointmentCreateDTO input, String rawIdempotencyKey) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("PATIENT")) {
            throw BusinessException.forbidden("仅患者可提交预约");
        }
        if (input == null || input.getScheduleId() == null || input.getPatientProfileId() == null) {
            throw BusinessException.bad("排班与就诊人不能为空");
        }
        Integer userStatus = appointmentMapper.lockActiveUser(user.id());
        if (!Objects.equals(userStatus, 1)) {
            throw BusinessException.forbidden("账号不可预约");
        }
        String complaint = normalizeComplaint(input.getChiefComplaint());
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        AppointmentIdempotencyEntity idempotency = null;
        if (idempotencyKey != null) {
            String requestHash = requestHash(input, complaint);
            LocalDateTime now = TimeUtil.nowUtc();
            idempotencyMapper.reserve(user.id(), idempotencyKey, requestHash, now);
            idempotency = idempotencyMapper.lock(user.id(), idempotencyKey);
            if (idempotency == null || !Objects.equals(requestHash, idempotency.getRequestHash())) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "幂等键已用于其他预约请求");
            }
            if (idempotency.getAppointmentId() != null) {
                AppointmentEntity previous = appointmentMapper.findDetail(idempotency.getAppointmentId());
                if (previous == null) {
                    throw BusinessException.conflict("原预约记录已不可查看");
                }
                return toVO(previous);
            }
        }

        BookableScheduleVO schedule = appointmentMapper.lockScheduleForBooking(input.getScheduleId());
        if (schedule == null || !"PUBLISHED".equals(schedule.getStatus())
                || !Objects.equals(schedule.getDoctorStatus(), 1)
                || !Objects.equals(schedule.getDepartmentStatus(), 1)) {
            throw BusinessException.conflict("SCHEDULE_CLOSED", "该时段暂不可预约");
        }
        LocalDateTime now = TimeUtil.nowUtc();
        if (!schedule.getStartTime().minusMinutes(schedule.getCancelBeforeMinutes()).isAfter(now)) {
            throw BusinessException.conflict("SCHEDULE_CLOSED", "已超过该时段预约截止时间");
        }
        if (schedule.getBookedSlots() >= schedule.getTotalSlots()) {
            throw BusinessException.conflict("APPOINTMENT_FULL", "该时段号源已满");
        }
        PatientSnapshotVO patient = appointmentMapper.findPatientProfile(input.getPatientProfileId(), user.id());
        if (patient == null) {
            throw BusinessException.forbidden("就诊人资料不存在或不属于当前账号");
        }
        if (appointmentMapper.reserveSlot(schedule.getId(), now) != 1) {
            throw BusinessException.conflict("APPOINTMENT_FULL", "该时段号源已满或已关闭");
        }

        AppointmentEntity appointment = new AppointmentEntity();
        appointment.setAppointmentNo(nextAppointmentNo(now));
        appointment.setPatientUserId(user.id());
        appointment.setPatientProfileId(patient.getId());
        appointment.setScheduleId(schedule.getId());
        appointment.setDoctorId(schedule.getDoctorId());
        appointment.setDepartmentId(schedule.getDepartmentId());
        appointment.setPatientNameSnapshot(patient.getRealName());
        appointment.setPatientPhoneSnapshot(patient.getPhone());
        appointment.setDoctorNameSnapshot(schedule.getDoctorName());
        appointment.setDepartmentNameSnapshot(schedule.getDepartmentName());
        appointment.setStartTimeSnapshot(schedule.getStartTime());
        appointment.setEndTimeSnapshot(schedule.getEndTime());
        appointment.setChiefComplaint(complaint);
        appointment.setStatus(AppointmentStatus.PENDING.name());
        appointment.setAppointmentActiveKey(patient.getId() + ":" + schedule.getId());
        appointment.setDeleted(0);
        appointment.setCreatedAt(now);
        appointment.setUpdatedAt(now);
        try {
            appointmentMapper.insert(appointment);
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("DUPLICATE_APPOINTMENT", "您已预约该时段");
        }
        if (idempotency != null && idempotencyMapper.attachAppointment(idempotency.getId(), appointment.getId(), now) != 1) {
            throw BusinessException.conflict("预约幂等记录发生冲突");
        }
        operationLogService.record("APPOINTMENT_CREATE", "APPOINTMENT", String.valueOf(appointment.getId()), "患者创建预约");
        return toVO(requireDetail(appointment.getId()));
    }

    @Transactional(readOnly = true)
    public PageResult<AppointmentVO> myList(AppointmentFilterDTO filter) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("PATIENT")) {
            throw BusinessException.forbidden("仅患者可查看本人预约列表");
        }
        return list(filter, user.id(), null);
    }

    @Transactional(readOnly = true)
    public PageResult<AppointmentVO> adminList(AppointmentFilterDTO filter) {
        CurrentUser user = requireStaff();
        Long doctorScope = user.hasRole("ADMIN") ? null : user.doctorId();
        if (!user.hasRole("ADMIN") && doctorScope == null) {
            validateFilter(filter);
            return new PageResult<>(List.of(), 0L, filter.getPage(), filter.getSize());
        }
        return list(filter, null, doctorScope);
    }

    @Transactional(readOnly = true)
    public AppointmentVO detail(long id) {
        AppointmentEntity appointment = requireDetail(id);
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("ADMIN") && !Objects.equals(user.id(), appointment.getPatientUserId())
                && !(user.hasRole("DOCTOR") && Objects.equals(user.doctorId(), appointment.getDoctorId()))) {
            throw BusinessException.forbidden("无权查看该预约");
        }
        return toVO(appointment);
    }

    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO cancel(long id, String reason) {
        CurrentUser user = CurrentUser.require();
        boolean admin = user.hasRole("ADMIN");
        if (!admin && !user.hasRole("PATIENT")) {
            throw BusinessException.forbidden("无权取消该预约");
        }
        AppointmentEntity observed = requireDetail(id);
        if (!admin && !Objects.equals(observed.getPatientUserId(), user.id())) {
            throw BusinessException.forbidden("无权取消该预约");
        }
        if (admin && (reason == null || reason.isBlank())) {
            throw BusinessException.bad("管理员取消预约须填写原因");
        }
        lockSchedule(observed.getScheduleId());
        AppointmentEntity locked = requireLocked(id);
        if (!admin && !Objects.equals(locked.getPatientUserId(), user.id())) {
            throw BusinessException.forbidden("无权取消该预约");
        }
        AppointmentStatus source = AppointmentStatus.parse(locked.getStatus());
        if (!source.isActive()) {
            throw BusinessException.conflict("APPOINTMENT_NOT_CANCELLABLE", "该预约当前不可取消");
        }
        LocalDateTime now = TimeUtil.nowUtc();
        int minutes = locked.getCancelBeforeMinutes() == null
                ? DEFAULT_CANCEL_BEFORE_MINUTES : locked.getCancelBeforeMinutes();
        if (!admin && !locked.getStartTimeSnapshot().minusMinutes(minutes).isAfter(now)) {
            throw BusinessException.conflict("APPOINTMENT_NOT_CANCELLABLE", "已超过患者取消截止时间");
        }
        String normalized = normalizeReason(reason);
        transition(locked, AppointmentStatus.CANCELLED, null, normalized, now, admin ? user.id() : null);
        releaseSlot(locked.getScheduleId(), now);
        operationLogService.record("APPOINTMENT_CANCEL", "APPOINTMENT", String.valueOf(id), "取消预约");
        return toVO(requireDetail(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public AppointmentVO changeStatus(long id, String targetText, String reason) {
        CurrentUser user = requireStaff();
        AppointmentStatus target = AppointmentStatus.parse(targetText);
        if (target == AppointmentStatus.CANCELLED) {
            if (!user.hasRole("ADMIN")) {
                throw BusinessException.forbidden("医生不能取消预约");
            }
            return cancel(id, reason);
        }
        AppointmentEntity observed = requireDetail(id);
        if (!user.hasRole("ADMIN") && !Objects.equals(user.doctorId(), observed.getDoctorId())) {
            throw BusinessException.forbidden("只能处理本人接诊的预约");
        }
        lockSchedule(observed.getScheduleId());
        AppointmentEntity locked = requireLocked(id);
        if (!user.hasRole("ADMIN") && !Objects.equals(user.doctorId(), locked.getDoctorId())) {
            throw BusinessException.forbidden("只能处理本人接诊的预约");
        }
        AppointmentStatus source = AppointmentStatus.parse(locked.getStatus());
        if (!user.hasRole("ADMIN") && source != AppointmentStatus.CONFIRMED) {
            throw BusinessException.forbidden("医生只能处理已确认的预约");
        }
        boolean valid = (source == AppointmentStatus.PENDING && target == AppointmentStatus.CONFIRMED)
                || (source == AppointmentStatus.CONFIRMED
                    && (target == AppointmentStatus.COMPLETED || target == AppointmentStatus.NO_SHOW));
        if (!valid) {
            throw BusinessException.conflict("该预约状态不允许此操作");
        }
        LocalDateTime now = TimeUtil.nowUtc();
        String activeKey = target.isActive() ? locked.getAppointmentActiveKey() : null;
        transition(locked, target, activeKey, null, now, user.id());
        if (!target.isActive()) {
            releaseSlot(locked.getScheduleId(), now);
        }
        operationLogService.record("APPOINTMENT_STATUS", "APPOINTMENT", String.valueOf(id), "更新预约状态为" + target.name());
        return toVO(requireDetail(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可删除预约");
        }
        AppointmentEntity observed = requireDetail(id);
        lockSchedule(observed.getScheduleId());
        AppointmentEntity locked = requireLocked(id);
        LocalDateTime now = TimeUtil.nowUtc();
        if (AppointmentStatus.parse(locked.getStatus()).isActive()) {
            transition(locked, AppointmentStatus.CANCELLED, null, "管理员删除预约", now, CurrentUser.require().id());
            releaseSlot(locked.getScheduleId(), now);
        }
        if (appointmentMapper.softDelete(id, now) != 1) {
            throw BusinessException.conflict("预约删除失败，请刷新后重试");
        }
        operationLogService.record("APPOINTMENT_DELETE", "APPOINTMENT", String.valueOf(id), "逻辑删除预约");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveBySchedule(long scheduleId) {
        lockSchedule(scheduleId);
        cancelActiveOnLockedSchedule(scheduleId, null, "排班关闭或停诊");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveByDepartment(long departmentId) {
        for (Long scheduleId : appointmentMapper.findActiveScheduleIdsByDepartment(departmentId)) {
            lockSchedule(scheduleId);
            cancelActiveOnLockedSchedule(scheduleId, null, "科室停诊");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveByDoctor(long doctorId) {
        for (Long scheduleId : appointmentMapper.findActiveScheduleIdsByDoctor(doctorId)) {
            lockSchedule(scheduleId);
            cancelActiveOnLockedSchedule(scheduleId, null, "医生停诊");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelActiveByPatient(long patientUserId) {
        appointmentMapper.lockActiveUser(patientUserId);
        for (Long scheduleId : appointmentMapper.findActiveScheduleIdsByPatient(patientUserId)) {
            lockSchedule(scheduleId);
            cancelActiveOnLockedSchedule(scheduleId, patientUserId, "患者账号停用");
        }
    }

    @Transactional(readOnly = true)
    public DashboardVO dashboard() {
        CurrentUser user = requireStaff();
        Long doctorScope = user.hasRole("ADMIN") ? null : user.doctorId();
        if (!user.hasRole("ADMIN") && doctorScope == null) {
            return new DashboardVO(0, 0, 0, 0, List.of(), zeroTrend());
        }
        LocalDate today = TimeUtil.clinicToday();
        LocalDateTime from = clinicStartAsUtc(today);
        LocalDateTime until = clinicStartAsUtc(today.plusDays(1));
        LocalDateTime trendFrom = clinicStartAsUtc(today.minusDays(6));
        Map<String, Long> counts = new HashMap<>();
        for (DailyTrendVO item : appointmentMapper.dailyTrend(doctorScope, trendFrom)) {
            counts.put(item.date(), item.count());
        }
        List<DailyTrendVO> trend = new ArrayList<>(7);
        for (int day = 6; day >= 0; day--) {
            String date = today.minusDays(day).toString();
            trend.add(new DailyTrendVO(date, counts.getOrDefault(date, 0L)));
        }
        return new DashboardVO(appointmentMapper.countToday(doctorScope, from, until),
                appointmentMapper.countPending(doctorScope), appointmentMapper.countPatients(doctorScope),
                appointmentMapper.countDoctors(doctorScope), appointmentMapper.statusCounts(doctorScope), trend);
    }

    public Long findDoctorIdByUserId(long userId) {
        return appointmentMapper.findDoctorIdByUserId(userId);
    }

    private void cancelActiveOnLockedSchedule(long scheduleId, Long patientUserId, String reason) {
        List<AppointmentEntity> appointments = appointmentMapper.findActiveByScheduleForUpdate(scheduleId, patientUserId);
        LocalDateTime now = TimeUtil.nowUtc();
        for (AppointmentEntity appointment : appointments) {
            transition(appointment, AppointmentStatus.CANCELLED, null, reason, now, null);
            releaseSlot(scheduleId, now);
            operationLogService.record("APPOINTMENT_CANCEL", "APPOINTMENT",
                    String.valueOf(appointment.getId()), reason);
        }
    }

    private PageResult<AppointmentVO> list(AppointmentFilterDTO filter, Long patientScope, Long doctorScope) {
        validateFilter(filter);
        LocalDateTime from = clinicStartAsUtc(filter.getDateFrom());
        LocalDateTime until = filter.getDateTo() == null ? null : clinicStartAsUtc(filter.getDateTo().plusDays(1));
        long offset = (long) (filter.getPage() - 1) * filter.getSize();
        long total = appointmentMapper.countPage(filter, patientScope, doctorScope, from, until);
        List<AppointmentEntity> entities = total == 0 ? List.of()
                : appointmentMapper.findPage(filter, patientScope, doctorScope, from, until, offset, filter.getSize());
        List<AppointmentVO> records = new ArrayList<>(entities.size());
        for (AppointmentEntity entity : entities) {
            records.add(toVO(entity));
        }
        return new PageResult<>(records, total, filter.getPage(), filter.getSize());
    }

    private static void validateFilter(AppointmentFilterDTO filter) {
        if (filter.getPage() == null || filter.getPage() < 1 || filter.getSize() == null
                || filter.getSize() < 1 || filter.getSize() > MAX_PAGE_SIZE) {
            throw BusinessException.bad("分页参数无效");
        }
        if (filter.getDateFrom() != null && filter.getDateTo() != null
                && filter.getDateFrom().isAfter(filter.getDateTo())) {
            throw BusinessException.bad("开始日期不得晚于结束日期");
        }
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            AppointmentStatus.parse(filter.getStatus());
        }
        if (filter.getKeyword() != null && filter.getKeyword().length() > 100) {
            throw BusinessException.bad("搜索关键词过长");
        }
    }

    private AppointmentEntity requireDetail(long id) {
        AppointmentEntity appointment = appointmentMapper.findDetail(id);
        if (appointment == null) {
            throw BusinessException.missing("预约不存在");
        }
        return appointment;
    }

    private AppointmentEntity requireLocked(long id) {
        AppointmentEntity appointment = appointmentMapper.lockAppointment(id);
        if (appointment == null) {
            throw BusinessException.missing("预约不存在");
        }
        return appointment;
    }

    private void lockSchedule(long scheduleId) {
        if (appointmentMapper.lockScheduleId(scheduleId) == null) {
            throw BusinessException.conflict("预约关联的排班不存在");
        }
    }

    private void releaseSlot(long scheduleId, LocalDateTime now) {
        if (appointmentMapper.releaseSlot(scheduleId, now) != 1) {
            throw BusinessException.conflict("号源数量不一致，请联系管理员");
        }
    }

    private void transition(AppointmentEntity current, AppointmentStatus target, String activeKey,
                            String cancelReason, LocalDateTime now, Long handledBy) {
        LocalDateTime cancelledAt = target == AppointmentStatus.CANCELLED ? now : null;
        LocalDateTime handledAt = handledBy == null ? null : now;
        if (appointmentMapper.transition(current.getId(), current.getStatus(), target.name(), activeKey,
                cancelReason, cancelledAt, handledBy, handledAt, now) != 1) {
            throw BusinessException.conflict("预约状态已变化，请刷新后重试");
        }
    }

    private static AppointmentVO toVO(AppointmentEntity appointment) {
        return new AppointmentVO(appointment.getId(), appointment.getAppointmentNo(),
                appointment.getPatientUserId(), appointment.getPatientProfileId(), appointment.getScheduleId(),
                appointment.getDoctorId(), appointment.getDepartmentId(), appointment.getPatientNameSnapshot(),
                appointment.getPatientPhoneSnapshot(), appointment.getDoctorNameSnapshot(),
                appointment.getDepartmentNameSnapshot(), TimeUtil.toClinic(appointment.getStartTimeSnapshot()),
                TimeUtil.toClinic(appointment.getEndTimeSnapshot()), appointment.getChiefComplaint(),
                appointment.getStatus(), appointment.getCancelBeforeMinutes() == null
                    ? DEFAULT_CANCEL_BEFORE_MINUTES : appointment.getCancelBeforeMinutes(),
                appointment.getCancelReason(), TimeUtil.toClinic(appointment.getCancelledAt()),
                TimeUtil.toClinic(appointment.getCreatedAt()));
    }

    private static String normalizeComplaint(String complaint) {
        if (complaint == null || complaint.isBlank()) {
            return null;
        }
        String cleaned = complaint.trim();
        if (cleaned.length() > 500) {
            throw BusinessException.bad("就诊诉求不能超过 500 字");
        }
        return cleaned;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String cleaned = reason.trim();
        if (cleaned.length() > 255) {
            throw BusinessException.bad("取消原因不能超过 255 字");
        }
        return cleaned;
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        String cleaned = key.trim();
        if (cleaned.isEmpty() || cleaned.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw BusinessException.bad("幂等键长度必须在 1 至 100 字符之间");
        }
        return cleaned;
    }

    private static String requestHash(AppointmentCreateDTO input, String complaint) {
        String canonical = input.getScheduleId() + "|" + input.getPatientProfileId() + "|"
                + (complaint == null ? "" : complaint);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 未提供 SHA-256", exception);
        }
    }

    private static String nextAppointmentNo(LocalDateTime now) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "AP" + NUMBER_TIME.format(now) + random;
    }

    private static LocalDateTime clinicStartAsUtc(LocalDate day) {
        return day == null ? null : day.atStartOfDay(CLINIC_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static List<DailyTrendVO> zeroTrend() {
        LocalDate today = TimeUtil.clinicToday();
        List<DailyTrendVO> result = new ArrayList<>(7);
        for (int day = 6; day >= 0; day--) {
            result.add(new DailyTrendVO(today.minusDays(day).toString(), 0));
        }
        return result;
    }

    private static CurrentUser requireStaff() {
        CurrentUser user = CurrentUser.require();
        if (!user.hasRole("ADMIN") && !user.hasRole("DOCTOR")) {
            throw BusinessException.forbidden("无权查看管理端预约");
        }
        return user;
    }
}
