package com.dental.config;

import static com.dental.common.DataValues.*;

import com.dental.persistence.BusinessMapper;
import com.dental.service.StoreService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/** 仅 local 模式填充虚构展示资料。重复启动不会修改用户已经维护的资料。 */
@Component
@Profile("local")
public class LocalDemoInitializer implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDemoInitializer.class);
    private final StoreService store;
    private final BusinessMapper mapper;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public LocalDemoInitializer(
            StoreService store,
            BusinessMapper mapper,
            PasswordEncoder encoder,
            Environment environment) {
        this.store = store;
        this.mapper = mapper;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        long admin =
                seedUser(
                        environment.getProperty("DEMO_ADMIN_USERNAME", "admin"),
                        "演示管理员",
                        "ADMIN",
                        environment.getProperty("DEMO_ADMIN_PASSWORD"));
        long doctorUser =
                seedUser(
                        "doctor",
                        "林知安医生",
                        "DOCTOR",
                        environment.getProperty("DEMO_DOCTOR_PASSWORD"));
        List<String> names = List.of("牙体牙髓科", "牙周护理科", "口腔修复科");
        long[] departmentIds = new long[names.size()];
        for (int index = 0; index < names.size(); index++) {
            var department = store.findOne("department", fields("name", names.get(index)));
            departmentIds[index] =
                    department == null
                            ? store.insert(
                                    "department",
                                    fields(
                                            "name",
                                            names.get(index),
                                            "description",
                                            "虚构演示科室，提供口腔预约服务",
                                            "sortOrder",
                                            index + 1,
                                            "status",
                                            1))
                            : number(department.get("id"));
        }
        List<String> doctors = List.of("林知安", "周清禾", "许明川");
        for (int index = 0; index < doctors.size(); index++) {
            var doctor =
                    store.findOne(
                            "doctor",
                            fields(
                                    "name",
                                    doctors.get(index),
                                    "departmentId",
                                    departmentIds[index]));
            long doctorId =
                    doctor == null
                            ? store.insert(
                                    "doctor",
                                    fields(
                                            "userId",
                                            index == 0 ? doctorUser : null,
                                            "departmentId",
                                            departmentIds[index],
                                            "name",
                                            doctors.get(index),
                                            "title",
                                            index == 0 ? "主治医师" : "执业医师",
                                            "specialty",
                                            List.of("龋齿检查与牙体护理", "牙周护理与洁牙", "牙齿修复咨询").get(index),
                                            "introduction",
                                            "本人物为毕业设计虚构演示医生。",
                                            "status",
                                            1))
                            : number(doctor.get("id"));
            if (doctor != null && integer(doctor.get("status")) != 1) {
                continue;
            }
            for (int day = 1; day <= 7; day++) {
                LocalDate date = LocalDate.now(CLINIC_ZONE).plusDays(day);
                for (int hour : List.of(9, 14)) {
                    var start =
                            date.atTime(LocalTime.of(hour, 0))
                                    .atZone(CLINIC_ZONE)
                                    .withZoneSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                    var end = start.plusHours(3);
                    if (mapper.overlaps(doctorId, 0, start, end) == 0) {
                        store.insert(
                                "doctor_schedule",
                                fields(
                                        "doctorId",
                                        doctorId,
                                        "departmentId",
                                        departmentIds[index],
                                        "startTime",
                                        start,
                                        "endTime",
                                        end,
                                        "totalSlots",
                                        8,
                                        "bookedSlots",
                                        0,
                                        "status",
                                        "PUBLISHED",
                                        "cancelBeforeMinutes",
                                        120,
                                        "createdBy",
                                        admin));
                    }
                }
            }
        }
        if (store.findOne("clinic_notice", fields("title", "预约就诊温馨提示")) == null) {
            store.insert(
                    "clinic_notice",
                    fields(
                            "title",
                            "预约就诊温馨提示",
                            "content",
                            "请提前10分钟到诊所，预约提交后由工作人员确认。取消预约请在就诊开始前120分钟完成。所有展示资料均为虚构演示数据。",
                            "status",
                            1,
                            "publishAt",
                            now(),
                            "createdBy",
                            admin));
        }
        LOGGER.info(
                "Local demonstration data initialized; credentials are read from environment"
                        + " variables");
    }

    private long seedUser(String username, String displayName, String role, String password) {
        var existing = store.findOne("sys_user", fields("username", username));
        if (existing != null) {
            return number(existing.get("id"));
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException(
                    "Local demo passwords must be provided using environment variables (8 to 72"
                            + " characters)");
        }
        long id =
                store.insert(
                        "sys_user",
                        fields(
                                "username",
                                username,
                                "passwordHash",
                                encoder.encode(password),
                                "displayName",
                                displayName,
                                "status",
                                1));
        mapper.bindRole(id, role);
        return id;
    }
}
