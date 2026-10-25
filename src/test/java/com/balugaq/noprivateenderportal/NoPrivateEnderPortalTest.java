package com.balugaq.noprivateenderportal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockBukkit 集成测试：验证插件的拦截逻辑（不依赖真实服务端，仅验证「事件来了以后监听器怎么做」）。
 * <p>
 * 注意：MockBukkit 不实现 {@code EnderEyeItem}/{@code ServerPlayerGameMode} 等服务端逻辑，
 * 无法真正「右键激活传送门」，因此这里手动构造对应事件来驱动监听器，覆盖放行/拦截各分支。
 */
class NoPrivateEnderPortalTest {

    private ServerMock server;
    private NoPrivateEnderPortal plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(NoPrivateEnderPortal.class);
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block portalFrame() {
        Block block = server.getWorld("world").getBlockAt(0, 64, 0);
        block.setType(Material.END_PORTAL_FRAME);
        return block;
    }

    private PlayerInteractEvent rightClick(PlayerMock player, Block block, ItemStack item) {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.SOUTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void nonExemptPlayerIsBlockedWithMessage() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);

        PlayerInteractEvent event = rightClick(player, portalFrame(), new ItemStack(Material.ENDER_EYE));

        assertTrue(event.isCancelled(), "非豁免玩家右键末影之眼到门框应被取消");
        var message = player.nextComponentMessage();
        assertNotNull(message, "被拦截玩家应收到提示消息");
        assertTrue(message.toString().contains("[NoPrivateEnderPortal]"),
                "提示消息应包含插件前缀");
    }

    @Test
    void opPlayerIsAllowed() {
        PlayerMock player = server.addPlayer("Admin");
        player.setOp(true);

        PlayerInteractEvent event = rightClick(player, portalFrame(), new ItemStack(Material.ENDER_EYE));

        assertFalse(event.isCancelled(), "OP 应被赦免，事件不被取消");
    }

    @Test
    void wrongItemIsNotBlocked() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);

        PlayerInteractEvent event = rightClick(player, portalFrame(), new ItemStack(Material.DIAMOND));

        assertFalse(event.isCancelled(), "拿钻石右键门框不应触发拦截");
    }

    @Test
    void wrongBlockIsNotBlocked() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);
        Block stone = server.getWorld("world").getBlockAt(1, 64, 0);
        stone.setType(Material.STONE);

        PlayerInteractEvent event = rightClick(player, stone, new ItemStack(Material.ENDER_EYE));

        assertFalse(event.isCancelled(), "对普通方块放末影之眼不应触发拦截");
    }

    @Test
    void emptyHandIsNotBlocked() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);

        PlayerInteractEvent event = rightClick(player, portalFrame(), null);

        assertFalse(event.isCancelled(), "空手右键门框不应触发拦截");
    }

    @Test
    void leftClickIsNotBlocked() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);
        Block block = portalFrame();
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, new ItemStack(Material.ENDER_EYE), block, BlockFace.SOUTH, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled(), "左键不应触发拦截");
    }

    @Test
    void entityChangeBlockFallbackBlocksNonExempt() {
        PlayerMock player = server.addPlayer("Steve");
        player.setOp(false);
        Block block = portalFrame();

        EntityChangeBlockEvent event = new EntityChangeBlockEvent(
                player, block, Bukkit.createBlockData(Material.END_PORTAL_FRAME));
        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "兜底：门框被改成门框（插眼）应被取消");
    }

    @Test
    void entityChangeBlockAllowsOp() {
        PlayerMock player = server.addPlayer("Admin");
        player.setOp(true);
        Block block = portalFrame();

        EntityChangeBlockEvent event = new EntityChangeBlockEvent(
                player, block, Bukkit.createBlockData(Material.END_PORTAL_FRAME));
        server.getPluginManager().callEvent(event);

        assertFalse(event.isCancelled(), "OP 走兜底路径也应被赦免");
    }
}
