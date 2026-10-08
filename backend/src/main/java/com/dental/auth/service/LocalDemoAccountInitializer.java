package com.dental.auth.service;

import com.dental.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 仅 local 环境从环境变量幂等创建演示管理员与医生账号。 */
@Component
@Profile("local")
@Order(100)
public class LocalDemoAccountInitializer implements ApplicationRunner {
    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int MAXIMUM_PASSWORD_LENGTH = 72;
    private static final String USERNAME_PATTERN = "[A-Za-z0-9_.-]{3,64}";

    private final UserService userService;
    private final String adminUsername;
    private final String adminPassword;
    private final String doctorUsername;
    private final String doctorPassword;

    public LocalDemoAccountInitializer(
            UserService userService,
            @Value("${DEMO_ADMIN_USERNAME:}") String adminUsername,
            @Value("${DEMO_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${DEMO_DOCTOR_USERNAME:doctor}") String doctorUsername,
            @Value("${DEMO_DOCTOR_PASSWORD:}") String doctorPassword) {
        this.userService = userService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.doctorUsername = doctorUsername;
        this.doctorPassword = doctorPassword;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!adminUsername.isBlank() && !adminPassword.isBlank()) {
            validateCredential(adminUsername, adminPassword);
            userService.initializeLocalAdmin(adminUsername, adminPassword);
        }
        if (!doctorUsername.isBlank() && !doctorPassword.isBlank()) {
            validateCredential(doctorUsername, doctorPassword);
            userService.initializeLocalDoctor(doctorUsername, doctorPassword);
        }
    }

    private static void validateCredential(String username, String password) {
        if (!username.matches(USERNAME_PATTERN)
                || password.length() < MINIMUM_PASSWORD_LENGTH
                || password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("本地演示账号或密码不符合格式与长度要求");
        }
    }
}
