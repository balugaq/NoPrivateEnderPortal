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
package com.balugaq.noprivateenderportal;

import com.balugaq.noprivateenderportal.command.ReloadCommand;
import com.balugaq.noprivateenderportal.config.PluginConfig;
import com.balugaq.noprivateenderportal.core.ExemptionManager;
import com.balugaq.noprivateenderportal.core.MessageThrottler;
import com.balugaq.noprivateenderportal.listener.EnderPortalListener;
import com.balugaq.noprivateenderportal.lang.Lang;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.util.UUID;

/**
 * 阻止非豁免玩家用末影之眼在末地传送门框上私建传送门。
 *
 * @author balugaq
 */
public class NoPrivateEnderPortal extends JavaPlugin {
    private PluginConfig pluginConfig;
    private ExemptionManager exemptionManager;
    private MessageThrottler messageThrottler;

    public NoPrivateEnderPortal() {
        super();
    }

    // MockBukkit 经 JavaPluginLoader 实例化插件，受保护构造器必须保留
    protected NoPrivateEnderPortal(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(getConfig());
        exemptionManager = new ExemptionManager();
        messageThrottler = new MessageThrottler();
        reloadExemptions();

        Lang.load(this);

        getServer().getPluginManager().registerEvents(new EnderPortalListener(this), this);

        final PluginCommand command = getCommand("npe");
        if (command != null) {
            command.setExecutor(new ReloadCommand(this));
        }

        getLogger().info("Enabled. Eyes of ender no longer activate end portal frames for non-exempt players.");
    }

    @Override
    public void onDisable() {
        Lang.unload();
    }

    /**
     * 重新读取 config.yml 与语言文件（{@code /npe reload} 调用）。
     */
    public void reload() {
        reloadConfig();
        pluginConfig = new PluginConfig(getConfig());
        reloadExemptions();
        Lang.load(this);
    }

    private void reloadExemptions() {
        exemptionManager.load(pluginConfig.isExemptOps(), pluginConfig.getExemptPlayers());
    }

    /**
     * @return 该玩家是否可以正常插眼激活传送门
     */
    public boolean isExempt(Player player) {
        return exemptionManager.isExempt(player);
    }

    /**
     * 给被拦截的玩家发提示消息（受冷却控制，见 {@code message-cooldown-ms}）。
     */
    public void notifyDenied(Player player) {
        if (messageThrottler.tryAcquire(player.getUniqueId(), pluginConfig.getMessageCooldownMs())) {
            Lang.sendMessage(player, "denied");
        }
    }

    /**
     * 玩家退出时清理消息冷却记录，避免 Map 无限增长。
     */
    public void forgetThrottle(UUID playerId) {
        messageThrottler.forget(playerId);
    }
}
