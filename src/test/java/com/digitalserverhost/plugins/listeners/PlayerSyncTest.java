package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class PlayerSyncTest {

    @Mock
    private MCDataBridge mockPlugin;
    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;
    @Mock
    private ResultSet mockResultSet;
    @Mock
    private org.bukkit.Server mockServer;
    @Mock
    private org.bukkit.scheduler.BukkitScheduler mockScheduler;

    private org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> mockedSchedulerUtils;

    @BeforeEach
    void setup() {
        // Initialize MockBukkit
        if (org.bukkit.Bukkit.getServer() == null) {
            org.mockmc.mockmc.MockMC.mock();
        }

        // Mock SchedulerUtils to return BukkitScheduler and NOT Folia
        @SuppressWarnings("null")
        org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> staticMock = mockStatic(
                com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mockedSchedulerUtils = staticMock;
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::isFolia).thenReturn(false);
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getScheduler)
                .thenReturn(new com.digitalserverhost.plugins.utils.BukkitScheduler());
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge)
                .thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());
        mockedSchedulerUtils
                .when(() -> com.digitalserverhost.plugins.utils.SchedulerUtils.runOnEntity(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(2);
                    runnable.run();
                    return null;
                });
        mockedSchedulerUtils.when(() -> com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(any(), any()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                });
        mockedSchedulerUtils
                .when(() -> com.digitalserverhost.plugins.utils.SchedulerUtils.runLater(any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                });

        // Setup Plugin Mocks
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(
                org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        lenient().when(mockConfig.getBoolean(anyString())).thenReturn(true);

        lenient().doReturn(true).when(mockPlugin).isEnabled();
        lenient().when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MCDataBridge"));
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getLockHeartbeatSeconds()).thenReturn(30);
        lenient().when(mockPlugin.isDebugMode()).thenReturn(true);
        lenient().when(mockPlugin.getServer()).thenReturn(mockServer);
        lenient().when(mockServer.getScheduler()).thenReturn(mockScheduler);

        // Default toggles
        lenient().when(mockPlugin.isSyncEnabled("food-level")).thenReturn(true);
        lenient().when(mockPlugin.isSyncEnabled(anyString())).thenReturn(true);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (mockedSchedulerUtils != null) {
            mockedSchedulerUtils.close();
        }
        org.mockmc.mockmc.MockMC.unmock();
    }

    @Test
    void testSaturationAndExhaustionApplied() throws Exception {
        // 1. Prepare Data JSON using Mocked Player
        org.bukkit.entity.Player sourcePlayer = mock(org.bukkit.entity.Player.class);
        when(sourcePlayer.getFoodLevel()).thenReturn(15);
        when(sourcePlayer.getSaturation()).thenReturn(5.0f);
        when(sourcePlayer.getExhaustion()).thenReturn(2.0f);

        // Mock inventory behavior for PlayerData constructor
        org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(sourcePlayer.getInventory()).thenReturn(mockInventory);

        // Setup toggles for capture
        when(mockPlugin.isSyncEnabled("food-level")).thenReturn(true);

        PlayerData sourceData = new PlayerData(sourcePlayer, mockPlugin);

        // 2. Setup Listener and DB Mocks
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID targetUuid = UUID.randomUUID();
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.loadPlayerDataComponents(any(), eq(targetUuid), any())).thenReturn(sourceData);

        // 3. Run PreLogin (Loads data into cache)
        AsyncPlayerPreLoginEvent preLoginEvent = mock(AsyncPlayerPreLoginEvent.class);
        when(preLoginEvent.getUniqueId()).thenReturn(targetUuid);
        when(preLoginEvent.getName()).thenReturn("TargetPlayer");
        // We don't need to mock getAddress for this test as it's not used in current
        // logic,
        // but if it were, we'd mock it.

        listener.onAsyncPlayerPreLogin(preLoginEvent);

        // 4. Run Join (Applies data from cache to Target Player)
        org.bukkit.entity.Player targetPlayer = mock(org.bukkit.entity.Player.class);
        when(targetPlayer.getUniqueId()).thenReturn(targetUuid);
        when(targetPlayer.getName()).thenReturn("TargetPlayer");
        when(targetPlayer.isOnline()).thenReturn(true);

        org.bukkit.World mockWorld = mock(org.bukkit.World.class);
        when(targetPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");
        // Mock inventory for application
        when(targetPlayer.getInventory()).thenReturn(mockInventory);

        // Mock Attributes
        org.bukkit.attribute.AttributeInstance maxHealthAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        when(targetPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);
        when(maxHealthAttr.getValue()).thenReturn(20.0);

        // Setup scheduler runTaskTimerAsynchronously
        lenient()
                .when(mockScheduler.runTaskTimerAsynchronously(any(org.bukkit.plugin.Plugin.class), any(Runnable.class),
                        anyLong(), anyLong()))
                .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(targetPlayer);

        listener.onPlayerJoin(joinEvent);

        // 5. Assertions
        verify(targetPlayer).setFoodLevel(15);
        verify(targetPlayer).setSaturation(5.0f);
        verify(targetPlayer).setExhaustion(2.0f);
    }

    @Test
    void testSaturationAndExhaustionNotAppliedIfDisabled() throws Exception {
        // 1. Prepare Data JSON
        org.bukkit.entity.Player sourcePlayer = mock(org.bukkit.entity.Player.class);
        when(sourcePlayer.getFoodLevel()).thenReturn(15);
        when(sourcePlayer.getSaturation()).thenReturn(5.0f);
        when(sourcePlayer.getExhaustion()).thenReturn(2.0f);
        org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(sourcePlayer.getInventory()).thenReturn(mockInventory);

        // Capture with enabled toggle
        when(mockPlugin.isSyncEnabled("food-level")).thenReturn(true);
        PlayerData sourceData = new PlayerData(sourcePlayer, mockPlugin);

        // 2. Disable toggle for Application
        when(mockPlugin.isSyncEnabled("food-level")).thenReturn(false);

        // 3. Setup Listener
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID targetUuid = UUID.randomUUID();
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.loadPlayerDataComponents(any(), eq(targetUuid), any())).thenReturn(sourceData);

        // 4. PreLogin
        AsyncPlayerPreLoginEvent preLoginEvent = mock(AsyncPlayerPreLoginEvent.class);
        when(preLoginEvent.getUniqueId()).thenReturn(targetUuid);
        when(preLoginEvent.getName()).thenReturn("TargetPlayer");
        listener.onAsyncPlayerPreLogin(preLoginEvent);

        // 5. Join
        org.bukkit.entity.Player targetPlayer = mock(org.bukkit.entity.Player.class);
        when(targetPlayer.getUniqueId()).thenReturn(targetUuid);
        when(targetPlayer.getName()).thenReturn("TargetPlayer");
        when(targetPlayer.isOnline()).thenReturn(true);
        org.bukkit.World mockWorld = mock(org.bukkit.World.class);
        when(targetPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");
        when(targetPlayer.getInventory()).thenReturn(mockInventory);

        lenient()
                .when(mockScheduler.runTaskTimerAsynchronously(any(org.bukkit.plugin.Plugin.class), any(Runnable.class),
                        anyLong(), anyLong()))
                .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(targetPlayer);

        listener.onPlayerJoin(joinEvent);

        // 6. Assertions - Should NOT set saturation/exhaustion
        // Verify setFoodLevel is NOT called (or called with something else? No, just
        // not called)
        verify(targetPlayer, never()).setFoodLevel(anyInt());
        verify(targetPlayer, never()).setSaturation(anyFloat());
        verify(targetPlayer, never()).setExhaustion(anyFloat());
    }

    @Test
    void testHealthAndPotionEffectsSyncOrder() throws Exception {
        // 1. Prepare Data with 25.0 Health + Health Boost
        org.bukkit.entity.Player sourcePlayer = mock(org.bukkit.entity.Player.class);
        when(sourcePlayer.getHealth()).thenReturn(25.0);

        // Health Boost Potion
        org.bukkit.potion.PotionEffect healthBoost = new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 1000, 4);
        when(sourcePlayer.getActivePotionEffects()).thenReturn(java.util.Collections.singletonList(healthBoost));

        org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(sourcePlayer.getInventory()).thenReturn(mockInventory);

        PlayerData sourceData = new PlayerData(sourcePlayer, mockPlugin);

        // 2. Setup Listener and DB
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        UUID targetUuid = UUID.randomUUID();

        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.loadPlayerDataComponents(any(), eq(targetUuid), any())).thenReturn(sourceData);

        // PreLogin
        AsyncPlayerPreLoginEvent preLoginEvent = mock(AsyncPlayerPreLoginEvent.class);
        when(preLoginEvent.getUniqueId()).thenReturn(targetUuid);
        when(preLoginEvent.getName()).thenReturn("TargetPlayer");
        listener.onAsyncPlayerPreLogin(preLoginEvent);

        // 3. Join
        org.bukkit.entity.Player targetPlayer = mock(org.bukkit.entity.Player.class);
        when(targetPlayer.getUniqueId()).thenReturn(targetUuid);
        when(targetPlayer.getName()).thenReturn("TargetPlayer");
        when(targetPlayer.isOnline()).thenReturn(true);
        org.bukkit.World mockWorld = mock(org.bukkit.World.class);
        when(targetPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");
        when(targetPlayer.getInventory()).thenReturn(mockInventory);

        // Mock Attributes
        org.bukkit.attribute.AttributeInstance maxHealthAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        when(targetPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);

        // Initial max health
        when(maxHealthAttr.getValue()).thenReturn(20.0);

        doAnswer(invocation -> {
            // updates max health
            when(maxHealthAttr.getValue()).thenReturn(40.0);
            return true;
        }).when(targetPlayer).addPotionEffect(any(org.bukkit.potion.PotionEffect.class));

        // Mock setHealth to NOT throw if value <= max
        doAnswer(invocation -> {
            double val = invocation.getArgument(0);
            double max = maxHealthAttr.getValue();
            if (val > max)
                throw new IllegalArgumentException("Health must be between 0 and " + max);
            return null;
        }).when(targetPlayer).setHealth(anyDouble());

        lenient().when(mockScheduler.runTaskTimerAsynchronously(any(), any(Runnable.class),
                anyLong(), anyLong()))
                .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(targetPlayer);

        listener.onPlayerJoin(joinEvent);

        // 4. Verification
        org.mockito.InOrder inOrder = inOrder(targetPlayer);

        // Verify Potion Effect is added FIRST
        inOrder.verify(targetPlayer).addPotionEffect(any(org.bukkit.potion.PotionEffect.class));

        // Verify Health is set SECOND
        inOrder.verify(targetPlayer).setHealth(25.0);

        // Verify NO kick happened
        verify(targetPlayer, never()).kick(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @SuppressWarnings("null")
    void testAllStatisticsSync() throws Exception {
        // 1. Prepare Source Player Stats
        org.bukkit.entity.Player sourcePlayer = mock(org.bukkit.entity.Player.class);

        // Stub source stats (only these three are non-zero)
        lenient().when(sourcePlayer.getStatistic(org.bukkit.Statistic.DEATHS)).thenReturn(5);
        lenient().when(sourcePlayer.getStatistic(org.bukkit.Statistic.MINE_BLOCK, org.bukkit.Material.STONE))
                .thenReturn(100);
        lenient().when(sourcePlayer.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.ZOMBIE))
                .thenReturn(15);

        // Mock inventory behavior for PlayerData constructor
        org.bukkit.inventory.PlayerInventory mockInventory = mock(org.bukkit.inventory.PlayerInventory.class);
        when(sourcePlayer.getInventory()).thenReturn(mockInventory);

        // Capture
        lenient().when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(false);
        lenient().when(mockPlugin.isSyncEnabledNewFeature("statistics")).thenReturn(true);
        PlayerData sourceData = new PlayerData(sourcePlayer, mockPlugin);

        // Verify captured map
        java.util.Map<String, Integer> capturedStats = sourceData.getStatistics();
        org.junit.jupiter.api.Assertions.assertEquals(3, capturedStats.size());
        org.junit.jupiter.api.Assertions.assertEquals(5, capturedStats.get("DEATHS"));
        org.junit.jupiter.api.Assertions.assertEquals(100, capturedStats.get("MINE_BLOCK:STONE"));
        org.junit.jupiter.api.Assertions.assertEquals(15, capturedStats.get("KILL_ENTITY:ZOMBIE"));

        // 2. Setup Target Player Stats
        org.bukkit.entity.Player targetPlayer = mock(org.bukkit.entity.Player.class);
        when(targetPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(targetPlayer.getName()).thenReturn("TargetPlayer");
        when(targetPlayer.isOnline()).thenReturn(true);
        org.bukkit.World mockWorld = mock(org.bukkit.World.class);
        when(targetPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");
        when(targetPlayer.getInventory()).thenReturn(mockInventory);

        // Mock Attributes
        org.bukkit.attribute.AttributeInstance maxHealthAttr = mock(org.bukkit.attribute.AttributeInstance.class);
        when(targetPlayer.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maxHealthAttr);
        when(maxHealthAttr.getValue()).thenReturn(20.0);

        // Stub target player stats (representing some matches, some overrides, some
        // resets)
        // Stale values:
        lenient().when(targetPlayer.getStatistic(org.bukkit.Statistic.DEATHS)).thenReturn(5); // identical to DB
        lenient().when(targetPlayer.getStatistic(org.bukkit.Statistic.MINE_BLOCK, org.bukkit.Material.STONE))
                .thenReturn(50); // needs update to 100
        lenient().when(targetPlayer.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.ZOMBIE))
                .thenReturn(15); // identical to DB
        lenient().when(targetPlayer.getStatistic(org.bukkit.Statistic.JUMP)).thenReturn(10); // needs reset to 0
        lenient().when(targetPlayer.getStatistic(org.bukkit.Statistic.MINE_BLOCK, org.bukkit.Material.DIAMOND_ORE))
                .thenReturn(3); // needs reset to 0
        lenient().when(
                targetPlayer.getStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.SKELETON))
                .thenReturn(2); // needs reset to 0

        // 3. Run PreLogin and Join
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        UUID targetUuid = targetPlayer.getUniqueId();
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.loadPlayerDataComponents(any(), eq(targetUuid), any())).thenReturn(sourceData);

        AsyncPlayerPreLoginEvent preLoginEvent = mock(AsyncPlayerPreLoginEvent.class);
        when(preLoginEvent.getUniqueId()).thenReturn(targetUuid);
        when(preLoginEvent.getName()).thenReturn("TargetPlayer");
        listener.onAsyncPlayerPreLogin(preLoginEvent);

        lenient().when(mockScheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

        PlayerJoinEvent joinEvent = mock(PlayerJoinEvent.class);
        when(joinEvent.getPlayer()).thenReturn(targetPlayer);

        listener.onPlayerJoin(joinEvent);

        // 4. Assertions / Verifications
        // Verify updates applied:
        verify(targetPlayer).setStatistic(org.bukkit.Statistic.MINE_BLOCK, org.bukkit.Material.STONE, 100);

        // Verify resets applied:
        verify(targetPlayer).setStatistic(org.bukkit.Statistic.JUMP, 0);
        verify(targetPlayer).setStatistic(org.bukkit.Statistic.MINE_BLOCK, org.bukkit.Material.DIAMOND_ORE, 0);
        verify(targetPlayer).setStatistic(org.bukkit.Statistic.KILL_ENTITY, org.bukkit.entity.EntityType.SKELETON, 0);

        // Verify identical values were NOT set (no redundant calls)
        verify(targetPlayer, never()).setStatistic(org.bukkit.Statistic.DEATHS, 5);
        verify(targetPlayer, never()).setStatistic(org.bukkit.Statistic.KILL_ENTITY,
                org.bukkit.entity.EntityType.ZOMBIE, 15);

        // Verify other statistics were NOT changed (since their db value was 0 and
        // target player had 0)
        verify(targetPlayer, never()).setStatistic(eqStat(org.bukkit.Statistic.DAMAGE_DEALT), anyInt());
    }

    @SuppressWarnings("null")
    private static @org.jetbrains.annotations.NotNull org.bukkit.Statistic eqStat(org.bukkit.Statistic stat) {
        return eq(stat);
    }
}
