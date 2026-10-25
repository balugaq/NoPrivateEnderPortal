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

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多语言注册表：键解析、占位符格式化。设计要点（对齐 JustEnoughGuide 的 LangRegistry）：
 * <ul>
 *   <li><b>用户文件只读</b>：{@code user}（磁盘上的用户文件）与 {@code defaults}（插件内置资源）
 *       分开保存，读取时先查 user、缺键再查 defaults，<b>绝不把合并结果写回磁盘</b>，
 *       避免毁掉用户文件里的注释与排版。</li>
 *   <li><b>无 MessageFormat</b>：占位符用自实现的 {@code {0}}…{@code {n}} 简单替换——
 *       {@code MessageFormat} 会把文本里的单引号当转义符（必须写成 {@code ''}），运营改文案必踩坑。</li>
 *   <li><b>可单测</b>：本类只依赖 Bukkit 的纯 Java 部分（{@code YamlConfiguration}），
 *       不需要服务器实例即可在 JUnit 里跑。</li>
 * </ul>
 * 缺键策略：同一条键只告警一次（避免刷屏），{@link #text} 返回键本身、{@link #lines} 返回单元素列表，
 * 保证调用方永远不会拿到 null。
 *
 * @author balugaq
 */
public final class LangRegistry {
    /**
     * 占位符：{@code {0}}、{@code {1}}……
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final FileConfiguration user;
    private final FileConfiguration defaults;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /**
     * 告警出口：生产环境由 {@link Lang} 接到 {@code getLogger().warning}，测试里可换成收集器断言。
     */
    private Consumer<String> warnSink = message -> {
    };

    public LangRegistry(FileConfiguration user, FileConfiguration defaults) {
        this.user = user;
        this.defaults = defaults;
    }

    /**
     * 注册告警出口（重复调用以最后一次为准）。
     *
     * @param sink 告警消费者
     */
    public void onWarn(Consumer<String> sink) {
        this.warnSink = sink;
    }

    /**
     * @return 已告警过的键集合（测试断言用）
     */
    public Set<String> warnedKeys() {
        return Set.copyOf(warned);
    }

    /**
     * 原始取值：先查用户文件，缺键再查内置兜底。
     *
     * @param key 语言键
     * @return 原始值；两个来源都没有时返回 null
     */
    public Object raw(String key) {
        Object value = user.get(key);
        if (value == null) {
            value = defaults.get(key);
        }
        return value;
    }

    /**
     * 取一条翻译文本（含占位符格式化）。
     * <p>
     * 若该键实际是列表，会把各行用换行符拼接后统一格式化；
     * 若该键是配置节点（既非字符串也非列表），按缺键处理。
     *
     * @param key  语言键
     * @param args 占位符参数
     * @return 翻译后的文本；缺键时返回键本身
     */
    public String text(String key, Object... args) {
        Object value = raw(key);
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object line : list) {
                lines.add(format(String.valueOf(line), args));
            }
            return String.join("\n", lines);
        }
        if (!(value instanceof String string)) {
            warnOnce(key);
            return key;
        }
        return format(string, args);
    }

    /**
     * 取一个消息键的全部行（用于 {@code sendMessage}）。
     * <p>
     * 键对应字符串时返回单元素列表，对应列表时逐行返回——两种 yml 写法等价。
     *
     * @param key  语言键
     * @param args 占位符参数
     * @return 各行文本；缺键时返回只含键本身的列表
     */
    public List<String> lines(String key, Object... args) {
        Object value = raw(key);
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object line : list) {
                lines.add(format(String.valueOf(line), args));
            }
            return lines;
        }
        if (value instanceof String string) {
            return List.of(format(string, args));
        }
        warnOnce(key);
        return List.of(key);
    }

    /**
     * 把模板里的 {@code {n}} 占位符替换成对应参数。
     * <p>
     * 越界或对应的参数为 null 时，占位符原样保留（方便排查参数个数错误）；
     * 文本中的单引号等字符不做任何转义处理。
     *
     * @param template 模板文本
     * @param args     参数
     * @return 格式化后的文本
     */
    public static String format(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            String replacement = matcher.group();
            if (index >= 0 && index < args.length && args[index] != null) {
                replacement = String.valueOf(args[index]);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void warnOnce(String key) {
        if (warned.add(key)) {
            warnSink.accept("Language key missing: " + key);
        }
    }
}
