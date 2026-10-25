package com.balugaq.noprivateenderportal.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExemptionManager} 判定逻辑测试。核心 {@link ExemptionManager#isExempt(boolean, boolean, String, UUID, boolean)}
 * 是纯函数，不碰服务器运行时，这里直接覆盖四条赦免路径与大小写/UUID 解析。
 */
class ExemptionManagerTest {

    private static final UUID SOME_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void opIsExemptWhenExemptOpsEnabled() {
        ExemptionManager m = new ExemptionManager();
        m.load(true, List.of());
        assertTrue(m.isExempt(true, true, "Steve", SOME_UUID, false));
    }

    @Test
    void opNotExemptWhenExemptOpsDisabled() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of());
        assertFalse(m.isExempt(false, true, "Steve", SOME_UUID, false));
    }

    @Test
    void nameMatchIsCaseInsensitive() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of("steve"));
        // 名单存小写，匹配时转小写：混合大小写的玩家名应命中
        assertTrue(m.isExempt(false, false, "Steve", SOME_UUID, false));
        assertTrue(m.isExempt(false, false, "STEVE", SOME_UUID, false));
        assertFalse(m.isExempt(false, false, "Alex", SOME_UUID, false));
    }

    @Test
    void uuidEntryMatchesExactly() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of(SOME_UUID.toString()));
        assertTrue(m.isExempt(false, false, "Other", SOME_UUID, false));

        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertFalse(m.isExempt(false, false, "Other", other, false));
    }

    @Test
    void uuidEntryIsParsedEvenWhenMixedWithNames() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of("Steve", SOME_UUID.toString()));
        assertTrue(m.isExempt(false, false, "Steve", SOME_UUID, false));
    }

    @Test
    void bypassPermissionAlwaysExempt() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of());
        assertTrue(m.isExempt(false, false, "Stranger", SOME_UUID, true));
    }

    @Test
    void reloadClearsPreviousEntries() {
        ExemptionManager m = new ExemptionManager();
        m.load(false, List.of("Steve"));
        assertTrue(m.isExempt(false, false, "Steve", SOME_UUID, false));

        m.load(false, List.of("Alex"));
        assertFalse(m.isExempt(false, false, "Steve", SOME_UUID, false));
        assertTrue(m.isExempt(false, false, "Alex", SOME_UUID, false));
    }

    @Test
    void emptyAndNullEntriesAreIgnored() {
        ExemptionManager m = new ExemptionManager();
        // List.of 不允许 null，这里用 Arrays.asList 构造含 null 的列表以覆盖 null 条目
        m.load(false, Arrays.asList("", "   ", null));
        assertFalse(m.isExempt(false, false, "Nobody", SOME_UUID, false));
    }
}
