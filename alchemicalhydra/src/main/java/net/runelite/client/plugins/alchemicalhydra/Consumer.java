// 
// Decompiled by Procyon v0.5.36
// 

package net.runelite.client.plugins.autospec;

import org.slf4j.LoggerFactory;
import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;
import net.runelite.api.Point;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.event.MouseEvent;
import net.runelite.client.plugins.stretchedmode.StretchedModeConfig;
import net.runelite.api.widgets.WidgetInfo;
import com.google.inject.Inject;
import net.runelite.api.events.MenuOptionClicked;
import java.util.ArrayList;
import java.util.Iterator;
import net.runelite.api.MenuEntry;
import java.util.List;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.Client;
import org.slf4j.Logger;

public class Consumer implements Runnable
{
    private static final Logger log;
    private final Client client;
    private final ConfigManager configManager;
    private final EventBus eventBus;
    private List<MenuEntry> entries;
    private Iterator<MenuEntry> iter;
    private int delay;
    private boolean run;
    private MenuEntry currentEntry;
    
    @Inject
    Consumer(final Client client, final ConfigManager configManager, final EventBus eventBus) {
        this.delay = 60;
        this.currentEntry = null;
        this.eventBus = eventBus;
        this.client = client;
        this.configManager = configManager;
        this.entries = new ArrayList<MenuEntry>();
        this.run = true;
        eventBus.subscribe((Class)MenuOptionClicked.class, (Object)this, this::onMenuOptionClicked);
    }
    
    @Override
    public void run() {
        while (this.run) {
            if (this.entries.isEmpty()) {
                if (!this.sleepHandler(this.delay)) {
                    continue;
                }
                this.setRun(false);
            }
            else {
                this.currentEntry = this.entries.get(0);
                this.entries.remove(this.currentEntry);
                this.mouseDownUp();
                if (!this.sleepHandler(this.delay)) {
                    continue;
                }
                this.setRun(false);
            }
        }
    }
    
    private void onMenuOptionClicked(final MenuOptionClicked event) {
        if (this.currentEntry == null) {
            return;
        }
        event.setMenuEntry(this.currentEntry);
        this.currentEntry = null;
    }
    
    void addEntry(final MenuEntry entry) {
        this.entries.add(entry);
    }
    
    private boolean sleepHandler(final int duration) {
        try {
            Thread.sleep(duration);
            return false;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            return true;
        }
    }
    
    private void mouseDownUp() {
        Point p = this.client.getMouseCanvasPosition();
        final Widget widget = this.client.getWidget(WidgetInfo.MINIMAP_HEALTH_ORB);
        if (this.client.isStretchedEnabled()) {
            final double scale = 1.0 + ((StretchedModeConfig)this.configManager.getConfig((Class)StretchedModeConfig.class)).scalingFactor() / 100.0;
            if (widget != null) {
                p = this.getClickPoint(widget.getBounds());
            }
            final MouseEvent mousePressed = new MouseEvent(this.client.getCanvas(), 501, System.currentTimeMillis(), 0, (int)(p.getX() * scale), (int)(p.getY() * scale), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mousePressed);
            final MouseEvent mouseReleased = new MouseEvent(this.client.getCanvas(), 502, System.currentTimeMillis(), 0, (int)(p.getX() * scale), (int)(p.getY() * scale), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mouseReleased);
            final MouseEvent mouseClicked = new MouseEvent(this.client.getCanvas(), 500, System.currentTimeMillis(), 0, (int)(p.getX() * scale), (int)(p.getY() * scale), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mouseClicked);
        }
        if (!this.client.isStretchedEnabled()) {
            if (widget != null) {
                p = this.getClickPoint(widget.getBounds());
            }
            final MouseEvent mousePressed2 = new MouseEvent(this.client.getCanvas(), 501, System.currentTimeMillis(), 0, p.getX(), p.getY(), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mousePressed2);
            final MouseEvent mouseReleased2 = new MouseEvent(this.client.getCanvas(), 502, System.currentTimeMillis(), 0, p.getX(), p.getY(), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mouseReleased2);
            final MouseEvent mouseClicked2 = new MouseEvent(this.client.getCanvas(), 500, System.currentTimeMillis(), 0, p.getX(), p.getY(), 1, false, 1);
            this.client.getCanvas().dispatchEvent(mouseClicked2);
        }
    }
    
    private Point getClickPoint(final Rectangle rect) {
        final int x = (int)(rect.getX() + getRandomIntBetweenRange((int)rect.getWidth() / 6 * -1, (int)rect.getWidth() / 6) + rect.getWidth() / 2.0);
        final int y = (int)(rect.getY() + getRandomIntBetweenRange((int)rect.getHeight() / 6 * -1, (int)rect.getHeight() / 6) + rect.getHeight() / 2.0);
        if (this.client.isStretchedEnabled()) {
            final double scale = 1.0 + ((StretchedModeConfig)this.configManager.getConfig((Class)StretchedModeConfig.class)).scalingFactor() / 100.0;
            return new Point((int)(x * scale), (int)(y * scale));
        }
        return new Point(x, y);
    }
    
    private static int getRandomIntBetweenRange(final int min, final int max) {
        return (int)(Math.random() * (max - min + 1) + min);
    }
    
    public Client getClient() {
        return this.client;
    }
    
    public ConfigManager getConfigManager() {
        return this.configManager;
    }
    
    public EventBus getEventBus() {
        return this.eventBus;
    }
    
    public List<MenuEntry> getEntries() {
        return this.entries;
    }
    
    public Iterator<MenuEntry> getIter() {
        return this.iter;
    }
    
    public int getDelay() {
        return this.delay;
    }
    
    public boolean isRun() {
        return this.run;
    }
    
    public MenuEntry getCurrentEntry() {
        return this.currentEntry;
    }
    
    public void setEntries(final List<MenuEntry> entries) {
        this.entries = entries;
    }
    
    public void setIter(final Iterator<MenuEntry> iter) {
        this.iter = iter;
    }
    
    public void setDelay(final int delay) {
        this.delay = delay;
    }
    
    public void setRun(final boolean run) {
        this.run = run;
    }
    
    public void setCurrentEntry(final MenuEntry currentEntry) {
        this.currentEntry = currentEntry;
    }
    
    static {
        log = LoggerFactory.getLogger((Class)Consumer.class);
    }
}
