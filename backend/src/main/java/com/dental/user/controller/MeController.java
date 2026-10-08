package com.dental.user.controller;

import com.dental.auth.service.AuthService;
import com.dental.common.R;
import com.dental.user.dto.PasswordChangeDTO;
import com.dental.user.dto.AccountProfileSaveDTO;
import com.dental.user.dto.PatientProfileSaveDTO;
import com.dental.user.service.UserService;
import com.dental.user.vo.PatientProfileVO;
import com.dental.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/** 当前登录账号与默认就诊人入口。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final UserService userService;
    private final AuthService authService;

    public MeController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping
    public R<UserVO> me() {
        return R.ok(userService.currentUser());
    }

    @PutMapping("/account-profile")
    public R<UserVO> saveAccountProfile(@Valid @RequestBody R<AccountProfileSaveDTO> request) {
        return R.ok(userService.saveAccountProfile(request.data()));
    }

    @GetMapping("/patient-profiles")
    public R<List<PatientProfileVO>> profiles() {
        return R.ok(userService.myPatientProfiles());
    }

    @PostMapping("/patient-profiles")
    public R<PatientProfileVO> createProfile(@Valid @RequestBody R<PatientProfileSaveDTO> request) {
        return R.ok(userService.createPatientProfile(request.data()));
    }

    @PutMapping("/patient-profiles/{id}")
    public R<PatientProfileVO> updateProfile(@PathVariable Long id,
            @Valid @RequestBody R<PatientProfileSaveDTO> request) {
        return R.ok(userService.updatePatientProfile(id, request.data()));
    }

    @PutMapping("/patient-profiles/{id}/default")
    public R<PatientProfileVO> setDefaultProfile(@PathVariable Long id) {
        return R.ok(userService.setDefaultPatientProfile(id));
    }

    @DeleteMapping("/patient-profiles/{id}")
    public R<Map<String, Object>> deleteProfile(@PathVariable Long id) {
        userService.deletePatientProfile(id);
        return R.ok(Map.of());
    }

    @GetMapping("/patient-profile")
    public R<PatientProfileVO> profile() {
        return R.ok(userService.currentProfile());
    }

    @PutMapping("/patient-profile")
    public R<PatientProfileVO> saveProfile(@Valid @RequestBody R<PatientProfileSaveDTO> request) {
        return R.ok(userService.saveCurrentProfile(request.data()));
    }

    @PostMapping("/password")
    public R<Map<String, Object>> changePassword(@Valid @RequestBody R<PasswordChangeDTO> request) {
        authService.changePassword(request.data());
        return R.ok(Map.of());
    }
}
