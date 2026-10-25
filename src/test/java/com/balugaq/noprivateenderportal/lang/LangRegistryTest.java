package com.balugaq.noprivateenderportal.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LangRegistry} 纯逻辑测试：占位符、缺键兜底、用户文件优先、列表展开。
 * 不依赖服务器，直接构造 {@link YamlConfiguration}。
 */
class LangRegistryTest {

    private static LangRegistry registry(YamlConfiguration user, YamlConfiguration defaults) {
        LangRegistry registry = new LangRegistry(user, defaults);
        registry.onWarn(message -> { /* 静默：测试单独断言 warnedKeys */ });
        return registry;
    }

    @Test
    void placeholderIsReplacedInOrder() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("denied", "你 {0} 创建 {1}！");
        LangRegistry registry = registry(user, new YamlConfiguration());

        assertEquals("你 没有 创建 末地传送门！", registry.text("denied", "没有", "末地传送门"));
    }

    @Test
    void missingIndexKeepsPlaceholder() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("t", "值={0} 额外={1}");
        LangRegistry registry = registry(user, new YamlConfiguration());

        // 第二个参数缺失 → {1} 原样保留
        assertEquals("值=ok 额外={1}", registry.text("t", "ok"));
    }

    @Test
    void missingKeyReturnsKeyAndWarnsOnce() {
        LangRegistry registry = registry(new YamlConfiguration(), new YamlConfiguration());

        assertEquals("ghost", registry.text("ghost"));
        assertEquals(List.of("ghost"), registry.lines("ghost"));
        // 重复查询只在第一次告警
        assertTrue(registry.warnedKeys().contains("ghost"));
        assertEquals(Set.of("ghost"), registry.warnedKeys());
    }

    @Test
    void userFileWinsOverDefaults() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("x", "用户值");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("x", "默认值");
        LangRegistry registry = registry(user, defaults);

        assertEquals("用户值", registry.text("x"));
    }

    @Test
    void missingUserKeyFallsBackToDefaults() {
        YamlConfiguration user = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("only-default", "默认文本");
        LangRegistry registry = registry(user, defaults);

        assertEquals("默认文本", registry.text("only-default"));
    }

    @Test
    void listKeyProducesMultipleLines() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("multi", List.of("第一行 {0}", "第二行"));
        LangRegistry registry = registry(user, new YamlConfiguration());

        List<String> lines = registry.lines("multi", "参数");
        assertEquals(2, lines.size());
        assertEquals("第一行 参数", lines.get(0));
        assertEquals("第二行", lines.get(1));
    }

    @Test
    void stringKeyProducesSingleLine() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("single", "只有一行");
        LangRegistry registry = registry(user, new YamlConfiguration());

        assertSame(1, registry.lines("single").size());
        assertEquals("只有一行", registry.lines("single").get(0));
    }
}
