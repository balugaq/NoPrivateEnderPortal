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
package com.balugaq.noprivateenderportal.lang;

import com.balugaq.noprivateenderportal.util.Colors;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 多语言静态门面（范式参考 JustEnoughGuide 的 {@code com.balugaq.jeg.core.lang.Lang}）。
 * 用法：
 * <pre>
 * Lang.sendMessage(player, "denied");
 * String text = Lang.t("usage");
 * </pre>
 * <p>
 * 语言文件位于 {@code resources/lang/<语言>.yml}，由 {@code config.yml} 的 {@code language} 键选择；
 * 插件首次启动时复制到 {@code plugins/NoPrivateEnderPortal/lang/}，之后以磁盘文件为准、
 * 内置版本仅作缺键兜底且<b>不会被改写回磁盘</b>。
 * <p>
 * 键对应的值可以是字符串或字符串列表（{@code sendMessage} 逐行发送）；占位符为
 * {@code {0}}…{@code {n}}。运行时只有一种语言（全局），不需要考虑按玩家切换。
 *
 * @author balugaq
 */
public final class Lang {
    private static final String DEFAULT_LANGUAGE = "zh_CN";

    private static volatile JavaPlugin plugin;
    private static volatile LangRegistry registry;

    private Lang() {
    }

    /**
     * 加载语言文件。须在 {@code saveDefaultConfig()} 之后调用（要读 {@code language} 键）。
     *
     * @param plugin 插件实例
     */
    public static void load(JavaPlugin plugin) {
        Lang.plugin = plugin;

        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create language folder " + folder.getPath()
                    + ", falling back to the built-in language file");
        }

        // 优先用用户文件；配置的语言不存在时回退到默认语言
        File file = new File(folder, language + ".yml");
        if (!file.exists() && plugin.getResource("lang/" + language + ".yml") == null) {
            plugin.getLogger().warning("Language file lang/" + language
                    + ".yml does not exist (not in built-in resources either), falling back to " + DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
            file = new File(folder, language + ".yml");
        }

        if (!file.exists()) {
            // 首次启动：把内置语言文件原样复制到磁盘（保留注释与排版）
            String path = "lang/" + language + ".yml";
            if (plugin.getResource(path) != null) {
                plugin.saveResource(path, false);
            } else {
                plugin.getLogger().severe("Built-in language file missing: " + path
                        + ", all language keys will fail to resolve");
            }
        }

        registry = new LangRegistry(readUser(file), readDefaults(plugin, language));
        registry.onWarn(message -> plugin.getLogger().warning(message));
        plugin.getLogger().info("Loaded language file: lang/" + language + ".yml");
    }

    /**
     * 卸载语言注册表（{@code /npe reload} 与 {@code onDisable} 共用）。
     */
    public static void unload() {
        registry = null;
        plugin = null;
    }

    /**
     * @return 语言系统是否已加载
     */
    public static boolean isLoaded() {
        return registry != null;
    }

    /**
     * 向接收者发送一条消息：键对应字符串时发一行，对应列表时逐行发送。
     *
     * @param sender 接收者
     * @param key    语言键
     */
    public static void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, (Object[]) null);
    }

    /**
     * 向接收者发送一条消息（带占位符参数）。
     *
     * @param sender 接收者
     * @param key    语言键
     * @param args   占位符参数
     */
    public static void sendMessage(CommandSender sender, String key, Object... args) {
        LangRegistry current = registry;
        if (current == null) {
            plugin.getLogger().warning("Lang is not loaded, cannot resolve language key " + key);
            sender.sendMessage(key);
            return;
        }

        for (String line : current.lines(key, args)) {
            sender.sendMessage(Colors.color(line));
        }
    }

    /**
     * 取翻译后的文本（含占位符格式化与 {@code &} 色码上色）。
     *
     * @param key  语言键
     * @param args 占位符参数
     * @return 翻译后的文本；Lang 未加载或键缺失时返回键本身
     */
    public static String t(String key, Object... args) {
        LangRegistry current = registry;
        if (current == null) {
            plugin.getLogger().warning("Lang is not loaded, cannot resolve language key " + key);
            return key;
        }
        return Colors.color(current.text(key, args));
    }

    /**
     * 读取用户文件（磁盘）。文件可能不存在（内置资源也缺失时），返回空配置。
     */
    private static FileConfiguration readUser(File file) {
        if (!file.exists()) {
            return new YamlConfiguration();
        }

        try {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    /**
     * 读取内置兜底配置（jar 内资源，绝不写回磁盘）。
     */
    private static FileConfiguration readDefaults(JavaPlugin plugin, String language) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in != null) {
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read built-in language file lang/" + language + ".yml");
        }
        return defaults;
    }
}
