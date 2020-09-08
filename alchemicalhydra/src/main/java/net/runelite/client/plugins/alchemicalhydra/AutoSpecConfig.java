// 
// Decompiled by Procyon v0.5.36
// 

package net.runelite.client.plugins.autospec;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.Config;

@ConfigGroup("switch")
public interface AutoSpecConfig extends Config
{
    @ConfigItem(keyName = "hotkeyUtil", name = "Initiate Combo", description = "", position = 0)
    default Keybind combo() {
        return Keybind.NOT_SET;
    }
    
    @ConfigItem(keyName = "autoBM", name = "Auto BM", description = "Enable this to auto type something when you kill someone.", position = 1)
    default boolean autoBM() {
        return false;
    }
    
    @ConfigItem(keyName = "autoBM", name = "Auto BM Text", description = "", position = 2, hidden = true, unhide = "autoBM")
    default String autoBMText() {
        return "Sit kid";
    }
    
    @ConfigItem(keyName = "threshold", name = "Damage Threshold", description = "Minimum amount of damage from an exp drop to trigger the combo.", position = 3)
    default int threshold() {
        return 20;
    }
    
    @ConfigItem(keyName = "hpThreshold", name = "Hp Threshold", description = "Minimum amount of health the opponent has to trigger the combo.", position = 4)
    default int hpThreshold() {
        return 99;
    }
}
