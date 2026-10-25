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
package com.balugaq.noprivateenderportal.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * config.yml 的类型化视图。所有键在这里集中定义，缺键时回退默认值。
 *
 * @author balugaq
 */
public final class PluginConfig {
    private static final String DEFAULT_LANGUAGE = "zh_CN";
    private static final long DEFAULT_MESSAGE_COOLDOWN_MS = 2000L;

    private final FileConfiguration config;

    public PluginConfig(FileConfiguration config) {
        this.config = config;
    }

    public String getLanguage() {
        return config.getString("language", DEFAULT_LANGUAGE);
    }

    public boolean isExemptOps() {
        return config.getBoolean("exempt-ops", true);
    }

    public List<String> getExemptPlayers() {
        return config.getStringList("exempt-players");
    }

    /**
     * @return 提示消息冷却毫秒数；配置里写负数时按 0 处理
     */
    public long getMessageCooldownMs() {
        return Math.max(0L, config.getLong("message-cooldown-ms", DEFAULT_MESSAGE_COOLDOWN_MS));
    }
}
