package com.dental;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** 口腔诊所预约服务入口。 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.dental.persistence")
public class DentalAppointmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DentalAppointmentApplication.class, args);
    }
}
