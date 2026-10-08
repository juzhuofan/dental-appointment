package com.dental.audit.controller;

import com.dental.audit.dto.OperationLogQueryDTO;
import com.dental.audit.service.OperationLogService;
import com.dental.audit.vo.OperationLogVO;
import com.dental.common.BusinessException;
import com.dental.common.PageResult;
import com.dental.common.R;
import com.dental.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员只读查看操作日志。 */
@RestController
@RequestMapping("/api/v1/admin/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public R<PageResult<OperationLogVO>> list(@RequestParam(required = false) String action,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        if (!CurrentUser.require().hasRole("ADMIN")) {
            throw BusinessException.forbidden("仅管理员可查看操作日志");
        }
        return R.ok(operationLogService.list(new OperationLogQueryDTO(action, keyword, page, size)));
    }
}
