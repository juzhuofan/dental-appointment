package com.dental.service;

import com.dental.common.BusinessException;
import com.dental.common.DataValues;
import com.dental.common.PageResult;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务层统一维护逻辑删除及创建/更新时间，Mapper 不暴露给 Controller。 */
@Service
public class StoreService {
    private final BusinessMapper mapper;

    public StoreService(BusinessMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> require(String table, long id, boolean lock) {
        var row = mapper.find(table, id, lock);
        if (row == null) {
            throw BusinessException.missing();
        }
        return row;
    }

    public Map<String, Object> findOne(String table, Map<String, Object> filter) {
        var rows = mapper.list(table, filter, null, false, 0, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insert(String table, Map<String, Object> values) {
        values.put("deleted", 0);
        values.put("createdAt", DataValues.now());
        values.put("updatedAt", DataValues.now());
        mapper.insert(table, values);
        return DataValues.number(values.get("id"));
    }

    public void update(String table, long id, Map<String, Object> values) {
        values.put("updatedAt", DataValues.now());
        if (mapper.update(table, id, values) != 1) {
            throw BusinessException.missing();
        }
    }

    public void softDelete(String table, long id) {
        update(table, id, DataValues.fields("deleted", 1));
    }

    public PageResult<Map<String, Object>> page(
            String table,
            Map<String, Object> filters,
            String keyword,
            boolean publicOnly,
            int page,
            int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw BusinessException.bad("page 必须大于0，size 范围为1–100");
        }
        if (keyword != null && keyword.length() > 100) {
            throw BusinessException.bad("搜索关键词过长");
        }
        var normalized = new LinkedHashMap<String, Object>();
        filters.forEach(
                (key, value) -> {
                    if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                        normalized.put(key, value);
                    }
                });
        long total = mapper.count(table, normalized, keyword, publicOnly);
        List<Map<String, Object>> rows =
                mapper.list(table, normalized, keyword, publicOnly, (long) (page - 1) * size, size);
        return new PageResult<>(rows, total, page, size);
    }

    public void audit(String action, String targetType, long targetId, String summary) {
        CurrentUser user = CurrentUser.get();
        insert(
                "operation_log",
                DataValues.fields(
                        "operatorUserId",
                        user.id(),
                        "operatorName",
                        user.displayName(),
                        "operatorRole",
                        user.isAdmin() ? "ADMIN" : user.isDoctor() ? "DOCTOR" : "PATIENT",
                        "action",
                        action,
                        "targetType",
                        targetType,
                        "targetId",
                        Long.toString(targetId),
                        "summary",
                        summary));
    }
}
