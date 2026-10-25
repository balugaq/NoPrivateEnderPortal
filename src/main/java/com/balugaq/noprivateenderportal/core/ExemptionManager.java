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
package com.balugaq.noprivateenderportal.core;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 赦免判定：OP（可配置开关）、config.yml 赦免名单（游戏名/UUID）、
 * {@code noprivateenderportal.bypass} 权限，三者命中其一即放行。
 * <p>
 * 判定核心 {@link #isExempt(boolean, boolean, String, UUID, boolean)} 是纯函数，
 * 不碰 Bukkit 运行时，可以在无服务器环境下单测。
 *
 * @author balugaq
 */
public final class ExemptionManager {
    /**
     * 绕过权限，默认不给任何人（见 plugin.yml）。
     */
    public static final String BYPASS_PERMISSION = "noprivateenderportal.bypass";

    private final Set<String> names = new HashSet<>();
    private final Set<UUID> uuids = new HashSet<>();
    private boolean exemptOps = true;

    /**
     * 从配置加载赦免名单。游戏名统一转小写存储（匹配时同样转小写，做到大小写不敏感）；
     * 能按 UUID 解析的条目按 UUID 精确匹配。
     *
     * @param exemptOps OP 是否豁免
     * @param entries   config.yml 的 exempt-players 列表
     */
    public void load(boolean exemptOps, List<String> entries) {
        this.exemptOps = exemptOps;
        names.clear();
        uuids.clear();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            final String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final UUID uuid = parseUuid(trimmed);
            if (uuid != null) {
                uuids.add(uuid);
            } else {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
    }

    public boolean isExempt(Player player) {
        return isExempt(
                exemptOps,
                player.isOp(),
                player.getName(),
                player.getUniqueId(),
                player.hasPermission(BYPASS_PERMISSION)
        );
    }

    /**
     * 纯判定逻辑：不依赖服务器运行时，供单测直接调用。
     *
     * @param exemptOps        OP 豁免开关
     * @param op               玩家是否 OP
     * @param name             玩家游戏名（可为 null）
     * @param uuid             玩家 UUID（可为 null）
     * @param bypassPermission 玩家是否持有绕过权限
     * @return true 表示放行
     */
    public boolean isExempt(boolean exemptOps, boolean op, String name, UUID uuid, boolean bypassPermission) {
        if (exemptOps && op) {
            return true;
        }
        if (bypassPermission) {
            return true;
        }
        return (name != null && names.contains(name.toLowerCase(Locale.ROOT)))
                || (uuid != null && uuids.contains(uuid));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            // 不是合法 UUID，按游戏名处理
            return null;
        }
    }
}
