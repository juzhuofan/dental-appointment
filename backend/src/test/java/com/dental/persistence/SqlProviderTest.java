package com.dental.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SqlProviderTest {
    @Test
    void queriesBindUserValuesAndFilterLogicalDeletion() {
        String sql =
                SqlProvider.list(
                        Map.of(
                                "table",
                                "department",
                                "filters",
                                Map.of("status", 1),
                                "keyword",
                                "';DROP TABLE department;--"));
        assertTrue(sql.contains("deleted=0"));
        assertTrue(sql.contains("#{keyword}"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    @Test
    void unknownIdentifiersCannotBeUsedInDynamicSql() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlProvider.list(
                                Map.of(
                                        "table",
                                        "sys_user;DROP TABLE sys_role",
                                        "filters",
                                        Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SqlProvider.update(
                                Map.of(
                                        "table",
                                        "sys_user",
                                        "values",
                                        Map.of("password_hash=(SELECT 1)", "x"))));
    }
}
