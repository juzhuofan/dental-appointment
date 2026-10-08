package com.dental.department.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.common.TimeUtil;
import com.dental.department.entity.DepartmentEntity;
import com.dental.department.entity.SystemConfigEntity;
import com.dental.department.mapper.DepartmentMapper;
import com.dental.department.mapper.SystemConfigMapper;
import com.dental.doctor.entity.DoctorEntity;
import com.dental.doctor.mapper.DoctorMapper;
import com.dental.schedule.entity.ScheduleEntity;
import com.dental.schedule.mapper.ScheduleMapper;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅 local 环境注入虚构科室、医生和未来号源；已有数据及人工停用状态会保留。 */
@Component
@Profile("local")
@Order(200)
public class LocalDemoCatalogInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDemoCatalogInitializer.class);
    private static final String SEED_KEY_PREFIX = "local.seed.catalog.";
    private static final String PUBLISHED = "PUBLISHED";
    private static final int SEED_DAYS = 3;
    private static final int DEMO_TOTAL_SLOTS = 8;

    private static final List<DemoProfile> DEMO_PROFILES = List.of(
            new DemoProfile("endodontics", "牙体牙髓科", "牙痛检查、牙体修复与根管治疗（演示）",
                    "林沐（演示）", "主治医师", "牙体牙髓疾病与根管治疗", 9),
            new DemoProfile("restorative", "口腔修复科", "牙体缺损与修复咨询（演示）",
                    "周安（演示）", "主治医师", "牙体修复与义齿咨询", 11),
            new DemoProfile("orthodontic", "口腔正畸科", "牙列不齐与正畸方案咨询（演示）",
                    "陈星（演示）", "主治医师", "牙列矫治与正畸咨询", 14));

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final SysUserMapper sysUserMapper;
    private final UserService userService;
    private final ClinicService clinicService;
    private final TransactionTemplate transactionTemplate;
    private final String doctorUsername;
    private final String adminUsername;

    public LocalDemoCatalogInitializer(DepartmentMapper departmentMapper, DoctorMapper doctorMapper,
                                       ScheduleMapper scheduleMapper, SystemConfigMapper systemConfigMapper,
                                       SysUserMapper sysUserMapper, UserService userService,
                                       ClinicService clinicService, TransactionTemplate transactionTemplate,
                                       @Value("${DEMO_DOCTOR_USERNAME:doctor}") String doctorUsername,
                                       @Value("${DEMO_ADMIN_USERNAME:admin}") String adminUsername) {
        this.departmentMapper = departmentMapper;
        this.doctorMapper = doctorMapper;
        this.scheduleMapper = scheduleMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.sysUserMapper = sysUserMapper;
        this.userService = userService;
        this.clinicService = clinicService;
        this.transactionTemplate = transactionTemplate;
        this.doctorUsername = doctorUsername;
        this.adminUsername = adminUsername;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        transactionTemplate.executeWithoutResult(transaction -> seed());
    }

    private void seed() {
        Long doctorUserId = userService.findEnabledDoctorUserIdByUsername(doctorUsername);
        Long creatorId = findUserId(adminUsername);
        if (creatorId == null) {
            creatorId = doctorUserId == null ? 0L : doctorUserId;
        }
        int cancelBeforeMinutes = clinicService.defaultCancelBeforeMinutes();
        for (int index = 0; index < DEMO_PROFILES.size(); index++) {
            DemoProfile profile = DEMO_PROFILES.get(index);
            DepartmentEntity department = seedDepartment(profile, index);
            if (department == null || !Integer.valueOf(1).equals(department.getStatus())) {
                continue;
            }
            Long assignedUser = index == 0 ? doctorUserId : null;
            DoctorEntity doctor = seedDoctor(profile, department, assignedUser);
            if (doctor == null || !Integer.valueOf(1).equals(doctor.getStatus())) {
                continue;
            }
            seedFutureSchedules(profile, doctor, creatorId, cancelBeforeMinutes);
        }
        LOGGER.info("本地虚构科室、医生及未来排班种子检查完成");
    }

    private DepartmentEntity seedDepartment(DemoProfile profile, int sortOrder) {
        String marker = SEED_KEY_PREFIX + "department." + profile.key();
        Long markedId = markedId(marker);
        if (markedId != null) {
            return departmentMapper.selectById(markedId);
        }
        DepartmentEntity existing = departmentMapper.selectOne(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getName, profile.departmentName()));
        if (existing != null) {
            saveMarker(marker, existing.getId());
            return existing;
        }
        DepartmentEntity department = new DepartmentEntity();
        department.setName(profile.departmentName());
        department.setDescription(profile.description());
        department.setSortOrder(sortOrder);
        department.setStatus(1);
        department.setDeleted(0);
        department.setCreatedAt(TimeUtil.nowUtc());
        department.setUpdatedAt(department.getCreatedAt());
        departmentMapper.insert(department);
        saveMarker(marker, department.getId());
        return department;
    }

    private DoctorEntity seedDoctor(DemoProfile profile, DepartmentEntity department, Long userId) {
        String marker = SEED_KEY_PREFIX + "doctor." + profile.key();
        Long markedId = markedId(marker);
        if (markedId != null) {
            DoctorEntity doctor = doctorMapper.selectById(markedId);
            linkDemoDoctor(doctor, userId);
            return doctor;
        }
        List<DoctorEntity> existing = doctorMapper.selectList(new LambdaQueryWrapper<DoctorEntity>()
                .eq(DoctorEntity::getDepartmentId, department.getId())
                .eq(DoctorEntity::getName, profile.doctorName())
                .orderByAsc(DoctorEntity::getId));
        if (!existing.isEmpty()) {
            DoctorEntity doctor = existing.get(0);
            saveMarker(marker, doctor.getId());
            linkDemoDoctor(doctor, userId);
            return doctor;
        }
        DoctorEntity doctor = new DoctorEntity();
        doctor.setDepartmentId(department.getId());
        doctor.setUserId(freeDoctorUserId(userId));
        doctor.setName(profile.doctorName());
        doctor.setTitle(profile.title());
        doctor.setSpecialty(profile.specialty());
        doctor.setIntroduction("毕业设计使用的虚构演示医生资料，不对应真实医务人员。");
        doctor.setStatus(1);
        doctor.setDeleted(0);
        doctor.setCreatedAt(TimeUtil.nowUtc());
        doctor.setUpdatedAt(doctor.getCreatedAt());
        doctorMapper.insert(doctor);
        saveMarker(marker, doctor.getId());
        return doctor;
    }

    private void linkDemoDoctor(DoctorEntity doctor, Long userId) {
        if (doctor != null && doctor.getUserId() == null && freeDoctorUserId(userId) != null) {
            doctor.setUserId(userId);
            doctor.setUpdatedAt(TimeUtil.nowUtc());
            doctorMapper.updateById(doctor);
        }
    }

    private Long freeDoctorUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        Long linkedCount = doctorMapper.selectCount(new LambdaQueryWrapper<DoctorEntity>()
                .eq(DoctorEntity::getUserId, userId));
        return linkedCount == 0 ? userId : null;
    }

    private void seedFutureSchedules(DemoProfile profile, DoctorEntity doctor, Long creatorId,
                                     int cancelBeforeMinutes) {
        long futureCount = scheduleMapper.selectCount(new LambdaQueryWrapper<ScheduleEntity>()
                .eq(ScheduleEntity::getDoctorId, doctor.getId())
                .ge(ScheduleEntity::getStartTime, TimeUtil.nowUtc()));
        if (futureCount > 0) {
            return;
        }
        LocalDate firstDate = TimeUtil.clinicToday().plusDays(1);
        for (int dayOffset = 0; dayOffset < SEED_DAYS; dayOffset++) {
            OffsetDateTime clinicTime = firstDate.plusDays(dayOffset).atTime(profile.startHour(), 0)
                    .atZone(TimeUtil.CLINIC_ZONE).toOffsetDateTime();
            LocalDateTime start = TimeUtil.toUtc(clinicTime);
            LocalDateTime end = start.plusMinutes(30);
            long overlapCount = scheduleMapper.selectCount(new LambdaQueryWrapper<ScheduleEntity>()
                    .eq(ScheduleEntity::getDoctorId, doctor.getId())
                    .lt(ScheduleEntity::getStartTime, end)
                    .gt(ScheduleEntity::getEndTime, start));
            if (overlapCount > 0) {
                continue;
            }
            ScheduleEntity schedule = new ScheduleEntity();
            schedule.setDoctorId(doctor.getId());
            schedule.setDepartmentId(doctor.getDepartmentId());
            schedule.setStartTime(start);
            schedule.setEndTime(end);
            schedule.setTotalSlots(DEMO_TOTAL_SLOTS);
            schedule.setBookedSlots(0);
            schedule.setStatus(PUBLISHED);
            schedule.setCancelBeforeMinutes(cancelBeforeMinutes);
            schedule.setVersion(0);
            schedule.setCreatedBy(creatorId);
            schedule.setDeleted(0);
            schedule.setCreatedAt(TimeUtil.nowUtc());
            schedule.setUpdatedAt(schedule.getCreatedAt());
            scheduleMapper.insert(schedule);
        }
    }

    private Long findUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return user == null ? null : user.getId();
    }

    private Long markedId(String key) {
        SystemConfigEntity row = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getConfigKey, key));
        return row == null ? null : Long.parseLong(row.getConfigValue());
    }

    private void saveMarker(String key, Long id) {
        SystemConfigEntity marker = new SystemConfigEntity();
        marker.setConfigKey(key);
        marker.setConfigValue(id.toString());
        marker.setDescription("仅 local 环境的虚构演示种子标识");
        marker.setDeleted(0);
        marker.setCreatedAt(TimeUtil.nowUtc());
        marker.setUpdatedAt(marker.getCreatedAt());
        systemConfigMapper.insert(marker);
    }

    private record DemoProfile(String key, String departmentName, String description,
                               String doctorName, String title, String specialty, int startHour) {
    }
}
