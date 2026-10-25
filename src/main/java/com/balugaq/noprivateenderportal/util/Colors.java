/*
 * Copyright (c) 2026 balugaq
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.balugaq.noprivateenderportal.util;

/**
 * {@code &} 系传统色码翻译：{@code &x} → {@code §x}，{@code &&} → 字面量 {@code &}。
 * 本插件只有纯文本消息，不需要 Adventure 组件，用最传统的通道即可。
 *
 * @author balugaq
 */
public final class Colors {
    private static final char COLOR_CHAR = '\u00A7';
    private static final char AMPERSAND = '&';

    /**
     * 合法的颜色/格式代码：0-9 a-f 颜色，k-o 格式，r 重置。
     */
    private static final String VALID_CODES = "0123456789abcdefklmnor";

    private Colors() {
    }

    public static String color(String text) {
        if (text == null || text.indexOf(AMPERSAND) == -1) {
            return text;
        }

        char[] chars = text.toCharArray();
        StringBuilder builder = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            char current = chars[i];
            if (current == AMPERSAND && i + 1 < chars.length) {
                char next = chars[i + 1];
                if (next == AMPERSAND) {
                    builder.append(AMPERSAND);
                    i++;
                } else if (VALID_CODES.indexOf(next) >= 0) {
                    builder.append(COLOR_CHAR).append(next);
                    i++;
                } else {
                    builder.append(current);
                }
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
