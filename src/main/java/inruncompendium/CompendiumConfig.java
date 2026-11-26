package inruncompendium;

import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;

import java.util.Properties;

/** 本模组自己的设置（独立于字修）。 */
public final class CompendiumConfig {

    private static final String FILE = "inrunCompendiumConfig";
    private static final String ENABLED = "enabled";

    private static SpireConfig config;
    private static boolean enabled = true;

    private CompendiumConfig() {
    }

    public static void load() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty(ENABLED, "true");
            config = new SpireConfig(InRunCompendium.MOD_ID, FILE, defaults);
            enabled = config.getBool(ENABLED);
        } catch (Exception e) {
            config = null;
            enabled = true;
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (config == null) {
            return;
        }
        try {
            config.setBool(ENABLED, value);
            config.save();
        } catch (Exception ignored) {
            // 保存失败只影响下次启动的默认值。
        }
    }
}
