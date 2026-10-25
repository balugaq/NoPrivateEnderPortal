package com.balugaq.noprivateenderportal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Colors} 传统 {@code &} 色码翻译测试。
 */
class ColorsTest {

    @Test
    void translatesValidColorCode() {
        assertEquals("§a绿色", Colors.color("&a绿色"));
    }

    @Test
    void doubleAmpersandEscapesToSingle() {
        // && 转义为字面量单个 &
        assertEquals("&", Colors.color("&&"));
    }

    @Test
    void invalidCodeIsKeptVerbatim() {
        // 'z' 不是合法色码 → 原样保留 &z
        assertEquals("&z不是色码", Colors.color("&z不是色码"));
    }

    @Test
    void noAmpersandReturnsAsIs() {
        assertEquals("纯文本", Colors.color("纯文本"));
    }

    @Test
    void trailingAmpersandIsKept() {
        assertEquals("结尾&", Colors.color("结尾&"));
    }

    @Test
    void nullInputIsNull() {
        assertNull(Colors.color(null));
    }
}
