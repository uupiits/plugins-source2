// 
// Decompiled by Procyon v0.5.36
// 

package net.runelite.client.plugins.autospec;

import com.google.common.collect.ImmutableSet;
import org.slf4j.LoggerFactory;
import net.runelite.client.config.Keybind;
import net.runelite.http.api.item.ItemStats;
import net.runelite.api.kit.KitType;
import net.runelite.api.WorldType;
import java.util.Iterator;
import net.runelite.api.widgets.WidgetInfo;
import javax.annotation.Nullable;
import net.runelite.api.Skill;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.Actor;
import java.util.Collection;
import joptsimple.internal.Strings;
import net.runelite.api.Player;
import java.io.IOException;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.input.KeyListener;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import java.util.function.Supplier;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.HashMap;
import net.runelite.client.util.HotkeyListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.BlockingQueue;
import net.runelite.http.api.hiscore.HiscoreResult;
import java.util.Map;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.http.api.hiscore.HiscoreClient;
import java.util.Set;
import net.runelite.api.MenuEntry;
import org.slf4j.Logger;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.Plugin;

@PluginDescriptor(name = "Auto-Spec", description = "Automatically casts Special Attack", tags = { "pd", "spec", "switches", "auto" }, enabledByDefault = false, type = PluginType.EXTERNAL)
public class AutoSpecPlugin extends Plugin
{
    private static final Logger log;
    private static final double HITPOINT_RATIO = 1.33;
    private static final double DMM_MULTIPLIER_RATIO = 10.0;
    private static final MenuEntry SPEC;
    private static final MenuEntry EQUIP_ITEM;
    private static final Set<Integer> ANIMS;
    private static final Set<Integer> SPEC_WEAPONS;
    private static final Set<Integer> GMAUL;
    private static final HiscoreClient HISCORE_CLIENT;
    @Inject
    private Client client;
    @Inject
    private KeyManager keyManager;
    @Inject
    private EventBus eventBus;
    @Inject
    private AutoSpecConfig config;
    @Inject
    private Consumer consumer;
    @Inject
    private ItemManager itemManager;
    private final Map<Integer, HiscoreResult> resultCache;
    private BlockingQueue<Runnable> queue;
    private ThreadPoolExecutor executorService;
    private ExecutorService httpExecutor;
    private int spec;
    private int ticks;
    private int damage;
    private int foeIndex;
    private HiscoreResult hiscoreResult;
    private int sitTimeout;
    private final HotkeyListener hotkeyListener;
    
    public AutoSpecPlugin() {
        this.resultCache = new HashMap<Integer, HiscoreResult>();
        this.queue = new ArrayBlockingQueue<Runnable>(10);
        this.executorService = new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, this.queue, new ThreadPoolExecutor.DiscardPolicy());
        this.httpExecutor = Executors.newFixedThreadPool(100);
        this.spec = 0;
        this.ticks = -1;
        this.hotkeyListener = new HotkeyListener(() -> this.config.combo()) {
            public void hotkeyPressed() {
                AutoSpecPlugin.log.info("Combo Starting");
                AutoSpecPlugin.this.initiateCombo();
            }
        };
    }
    
    @Provides
    AutoSpecConfig getConfig(final ConfigManager configManager) {
        return (AutoSpecConfig)configManager.getConfig((Class)AutoSpecConfig.class);
    }
    
    protected void startUp() {
        this.subscribe();
        this.keyManager.registerKeyListener((KeyListener)this.hotkeyListener);
        this.executorService.submit(this.consumer);
    }
    
    protected void shutDown() {
        this.eventBus.unregister((Object)this);
        this.keyManager.unregisterKeyListener((KeyListener)this.hotkeyListener);
    }
    
    private void subscribe() {
        this.eventBus.subscribe((Class)GameTick.class, (Object)this, this::onGameTick);
        this.eventBus.subscribe((Class)AnimationChanged.class, (Object)this, this::onAnimationChanged);
        this.eventBus.subscribe((Class)ScriptCallbackEvent.class, (Object)this, this::onScriptCallbackEvent);
        this.eventBus.subscribe((Class)VarbitChanged.class, (Object)this, this::onVarbitChanged);
    }
    
    private void onVarbitChanged(final VarbitChanged event) {
        if (event.getIndex() != 1075) {
            return;
        }
        this.foeIndex = (this.client.getVar(VarPlayer.ATTACKING_PLAYER) & 0x7FF);
        if (this.foeIndex == 2047 || this.foeIndex == 0) {
            this.hiscoreResult = null;
            return;
        }
        AutoSpecPlugin.log.info("New Foe: {}", (Object)this.foeIndex);
        final Player player = this.client.getCachedPlayers()[this.foeIndex];
        if (player == null) {
            AutoSpecPlugin.log.info("New foe, but player is null?");
            return;
        }
        if (this.resultCache.containsKey(this.foeIndex)) {
            this.hiscoreResult = this.resultCache.get(this.foeIndex);
            return;
        }
        final Player player2;
        this.httpExecutor.submit(() -> {
            do {
                try {
                    this.hiscoreResult = AutoSpecPlugin.HISCORE_CLIENT.lookup(player2.getName());
                }
                catch (IOException ex) {
                    this.hiscoreResult = null;
                    AutoSpecPlugin.log.info("Timed out from web request.");
                    try {
                        Thread.sleep(50L);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } while (this.hiscoreResult == null);
            this.resultCache.put(this.foeIndex, this.hiscoreResult);
        });
    }
    
    private void onGameTick(final GameTick event) {
        if (this.ticks >= 0) {
            --this.ticks;
        }
        if (this.ticks == 0) {
            this.initiateCombo();
        }
        if (this.sitTimeout > 0) {
            --this.sitTimeout;
        }
        if (this.client.getWidget(160, 31).getText() != null) {
            this.spec = Integer.parseInt(this.client.getWidget(160, 31).getText());
        }
        if (this.config.autoBM() && !Strings.isNullOrEmpty(this.config.autoBMText()) && this.foeIndex != 2047 && this.foeIndex != 0 && this.sitTimeout == 0) {
            final Player player = this.client.getCachedPlayers()[this.foeIndex];
            if (player == null) {
                return;
            }
            if (player.getHealthRatio() == 0) {
                this.client.runScript(new Object[] { 13337, this.config.autoBMText() });
                this.sitTimeout = 5;
            }
        }
    }
    
    private void onAnimationChanged(final AnimationChanged event) {
        final Actor actor = event.getActor();
        if (actor != this.client.getLocalPlayer()) {
            return;
        }
        if (AutoSpecPlugin.ANIMS.contains(actor.getAnimation()) && this.spec >= 50) {
            final WidgetItem item = this.getWidgetItem(AutoSpecPlugin.GMAUL);
            if (item == null) {
                return;
            }
            final MenuEntry entry = AutoSpecPlugin.EQUIP_ITEM.clone();
            entry.setIdentifier(item.getId());
            entry.setParam0(item.getIndex());
            this.consumer.addEntry(entry);
            this.consumer.addEntry(AutoSpecPlugin.SPEC);
        }
    }
    
    private void onScriptCallbackEvent(final ScriptCallbackEvent event) {
        final String eventName2;
        final String eventName = eventName2 = event.getEventName();
        switch (eventName2) {
            case "newXpDrop": {
                this.damage = 0;
                break;
            }
            case "fakeXpDrop": {
                final int[] intStack = this.client.getIntStack();
                final int intStackSize = this.client.getIntStackSize();
                final int skillId = intStack[intStackSize - 2];
                final Skill skill = Skill.values()[skillId];
                if (skill.equals((Object)Skill.HITPOINTS)) {
                    final int exp = intStack[intStackSize - 1];
                    this.calculateDamageDealt(exp);
                    break;
                }
                break;
            }
            case "hpXpGained": {
                final int[] intStack = this.client.getIntStack();
                final int intStackSize = this.client.getIntStackSize();
                final int exp2 = intStack[intStackSize - 1];
                this.calculateDamageDealt(exp2);
                break;
            }
        }
    }
    
    private void initiateCombo() {
        AutoSpecPlugin.log.info("Initiating combo.");
        if (this.spec < 25) {
            AutoSpecPlugin.log.info("Spec is too low, returning.");
            return;
        }
        final WidgetItem item = this.getWidgetItem(AutoSpecPlugin.SPEC_WEAPONS);
        final MenuEntry entry = AutoSpecPlugin.EQUIP_ITEM.clone();
        final MenuEntry attack = this.buildAttackEntry();
        if (item == null || attack == null) {
            AutoSpecPlugin.log.info("Item or Attack is null.");
            return;
        }
        entry.setIdentifier(item.getId());
        entry.setParam0(item.getIndex());
        this.consumer.addEntry(entry);
        this.consumer.addEntry(attack);
        this.consumer.addEntry(AutoSpecPlugin.SPEC);
    }
    
    @Nullable
    private MenuEntry buildAttackEntry() {
        if (this.foeIndex == 2047) {
            return null;
        }
        return new MenuEntry("Attack", "<col=ff0000>Target<col=ffb000>", this.foeIndex, 45, 0, 0, false);
    }
    
    @Nullable
    private WidgetItem getWidgetItem(final Collection<Integer> collection) {
        WidgetItem item = null;
        for (final WidgetItem widgetItem : this.client.getWidget(WidgetInfo.INVENTORY).getWidgetItems()) {
            if (collection.contains(widgetItem.getId()) && widgetItem.getCanvasBounds().getX() > 0.0 && widgetItem.getCanvasBounds().getY() > 0.0) {
                item = widgetItem;
                break;
            }
        }
        return item;
    }
    
    private void calculateDamageDealt(final int diff) {
        if (this.ticks >= 0 || this.spec < 50) {
            return;
        }
        double damageDealt = diff / 1.33;
        if (this.client.getWorldType().contains(WorldType.DEADMAN)) {
            damageDealt /= 10.0;
        }
        final Actor a = this.client.getLocalPlayer().getInteracting();
        final int id = this.client.getLocalPlayer().getPlayerAppearance().getEquipmentId(KitType.WEAPON);
        if (this.hiscoreResult == null) {
            AutoSpecPlugin.log.info("No hiscore result.");
            return;
        }
        if (a instanceof Player) {
            this.damage = (int)Math.rint(damageDealt);
            final int hp = getTrueHp((Player)a, this.hiscoreResult.getHitpoints().getLevel());
            AutoSpecPlugin.log.info("Damage: {}, Hp: {}", (Object)this.damage, (Object)hp);
            if (this.damage >= this.config.threshold() && hp <= this.config.hpThreshold()) {
                AutoSpecPlugin.log.info("Activating Combo as thresholds have been hit.");
                if (id > 0) {
                    final ItemStats i = this.itemManager.getItemStats(id, false);
                    if (i != null && i.getEquipment() != null) {
                        this.ticks = i.getEquipment().getAspeed() - 1;
                        AutoSpecPlugin.log.info("Ticks: {}", (Object)this.ticks);
                    }
                    return;
                }
                this.initiateCombo();
            }
        }
    }
    
    private static int getTrueHp(final Player player, final int hitpoints) {
        final int scale = player.getHealth();
        final int ratio = player.getHealthRatio();
        if (hitpoints == -1) {
            return -1;
        }
        if (ratio > 0) {
            int minHealth = 1;
            int maxHealth;
            if (scale > 1) {
                if (ratio > 1) {
                    minHealth = (hitpoints * (ratio - 1) + scale - 2) / (scale - 1);
                }
                maxHealth = (hitpoints * ratio - 1) / (scale - 1);
                if (maxHealth > hitpoints) {
                    maxHealth = hitpoints;
                }
            }
            else {
                maxHealth = hitpoints;
            }
            return (minHealth + maxHealth + 1) / 2;
        }
        return -1;
    }
    
    static {
        log = LoggerFactory.getLogger((Class)AutoSpecPlugin.class);
        SPEC = new MenuEntry("Use <col=00ff00>Special Attack</col>", "", 1, 57, -1, 38862884, false);
        EQUIP_ITEM = new MenuEntry("Wear", "Wear", -1, 34, -1, 9764864, false);
        ANIMS = (Set)ImmutableSet.of((Object)1062, (Object)7514, (Object)7521, (Object)7644, (Object)7645, (Object)3300, (Object[])new Integer[] { 7642, 426 });
        SPEC_WEAPONS = (Set)ImmutableSet.of((Object)11802, (Object)20368, (Object)11804, (Object)20370, (Object)11808, (Object)20374, (Object[])new Integer[] { 11806, 20372, 1215, 20407, 1231, 5680, 5698, 1434, 11235, 12765, 12766, 12767, 12768, 20408, 13652, 20784, 20849, 21207, 13271, 861, 20558, 12788, 19481});
        GMAUL = (Set)ImmutableSet.of((Object)4153, (Object)12848, (Object)20557, (Object)24225, (Object)24227, (Object)20849);
        HISCORE_CLIENT = new HiscoreClient();
    }
}
