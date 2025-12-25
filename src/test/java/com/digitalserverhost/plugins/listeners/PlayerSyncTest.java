package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private static final Gson GSON = new GsonBuilder().create();

    @BeforeEach
    void setup() {
        // Setup Plugin Mocks
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
        String json = GSON.toJson(sourceData);

        // 2. Setup Listener and DB Mocks
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID targetUuid = UUID.randomUUID();
        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
        when(mockResultSet.getBytes("data")).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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

        // Setup scheduler runTaskTimerAsynchronously
        when(mockScheduler.runTaskTimerAsynchronously(any(org.bukkit.plugin.Plugin.class), any(Runnable.class),
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
        String json = GSON.toJson(sourceData);

        // 2. Disable toggle for Application
        when(mockPlugin.isSyncEnabled("food-level")).thenReturn(false);

        // 3. Setup Listener
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID targetUuid = UUID.randomUUID();
        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
        when(mockResultSet.getBytes("data")).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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

        when(mockScheduler.runTaskTimerAsynchronously(any(org.bukkit.plugin.Plugin.class), any(Runnable.class),
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
        String json = GSON.toJson(sourceData);

        // 2. Setup Listener and DB
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        UUID targetUuid = UUID.randomUUID();

        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(targetUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
        when(mockResultSet.getBytes("data")).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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
        when(targetPlayer.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)).thenReturn(maxHealthAttr);

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

        when(mockScheduler.runTaskTimerAsynchronously(any(), any(Runnable.class),
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
}
