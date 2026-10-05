package com.dental.common;

import java.util.Arrays;
import java.util.List;

public enum UserRole {
    ADMIN,
    DOCTOR,
    PATIENT;
    public static final String PATTERN = "ADMIN|DOCTOR|PATIENT";

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
