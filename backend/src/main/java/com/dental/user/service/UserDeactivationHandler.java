package com.dental.user.service;

import java.util.Set;

/** 在账号停用、删除或失去角色前同步处理关联业务。 */
public interface UserDeactivationHandler {
    void beforeDeactivate(Long userId, Set<String> currentRoles);
}
