package com.dental.user.controller;

import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.user.dto.UserSaveDTO;
import com.dental.user.service.UserService;
import com.dental.user.vo.AdminUserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理员账号维护入口。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public R<PageResult<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return R.ok(userService.listUsers(keyword, role, status, page, size));
    }

    @PostMapping
    public R<AdminUserVO> create(@Valid @RequestBody R<UserSaveDTO> request) {
        return R.ok(userService.saveUser(null, request.data()));
    }

    @PutMapping("/{id}")
    public R<AdminUserVO> update(
            @PathVariable Long id,
            @Valid @RequestBody R<UserSaveDTO> request) {
        return R.ok(userService.saveUser(id, request.data()));
    }

    @DeleteMapping("/{id}")
    public R<Map<String, Object>> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok(Map.of());
    }
}
