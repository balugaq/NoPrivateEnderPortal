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
package com.balugaq.noprivateenderportal.listener;

import com.balugaq.noprivateenderportal.NoPrivateEnderPortal;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 末地传送门守卫。两层拦截，都刻意避开「取消放置类事件」：
 * <ol>
 *   <li>{@link PlayerInteractEvent}（主闸）：在 {@code ServerPlayerGameMode.useItemOn}
 *       最开头拒绝整次交互，早于任何块/物品逻辑。此时 Paper 还没打开
 *       {@code captureBlockStates}，不会进入 #13586 / #13536 描述的
 *       「取消 BlockMultiPlaceEvent / BlockPlaceEvent 导致物品复制」的路径。</li>
 *   <li>{@link EntityChangeBlockEvent}（兜底）：拦非右键路径（其他插件/命令触发的插眼）。
 *       该事件在 {@code setBlock} 之前触发，取消它方块从未被修改，同样没有复制风险。</li>
 * </ol>
 * 反过来说：直接在 {@code BlockMultiPlaceEvent} / {@code BlockPlaceEvent} 上取消，
 * 正是 PaperMC/Paper#13586（容器物品复制）和 #13536（无限复制）踩的坑，本插件不碰。
 *
 * @author balugaq
 */
public final class EnderPortalListener implements Listener {
    private final NoPrivateEnderPortal plugin;

    public EnderPortalListener(NoPrivateEnderPortal plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        final ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) {
            return;
        }
        final Player player = event.getPlayer();
        if (plugin.isExempt(player)) {
            return;
        }

        // 拒绝块侧与物品侧交互并取消事件：后续一切（消耗眼睛 / 门框 HAS_EYE / 3x3 传送门方块）都不发生
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        plugin.notifyDenied(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // 只拦「门框 -> 门框」的变化，即末影之眼插入门框（HAS_EYE false -> true）
        if (event.getBlock().getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        if (event.getTo() != Material.END_PORTAL_FRAME) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.isExempt(player)) {
            return;
        }

        event.setCancelled(true);
        plugin.notifyDenied(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.forgetThrottle(event.getPlayer().getUniqueId());
    }
}
