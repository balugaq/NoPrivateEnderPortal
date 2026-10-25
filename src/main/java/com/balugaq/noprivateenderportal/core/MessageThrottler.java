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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家的提示消息冷却。事件只在主线程触发，这里用 ConcurrentHashMap 只是为了
 * 让 {@code /npe reload} 与退出清理不依赖外部同步。
 *
 * @author balugaq
 */
public final class MessageThrottler {
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();

    /**
     * @param playerId    玩家 UUID
     * @param cooldownMs  冷却毫秒数；{@code <= 0} 表示不冷却
     * @return true 表示这次允许发消息；处于冷却期返回 false
     */
    public boolean tryAcquire(UUID playerId, long cooldownMs) {
        final long now = System.currentTimeMillis();
        if (cooldownMs <= 0) {
            return true;
        }
        final Long previous = lastSent.get(playerId);
        if (previous != null && now - previous < cooldownMs) {
            return false;
        }
        lastSent.put(playerId, now);
        return true;
    }

    public void forget(UUID playerId) {
        lastSent.remove(playerId);
    }
}
