package inruncompendium;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 和字修（{@code zixiu}）的互斥：只有在「玩的是砚秋」且「字修自带的字典开着」时，本模组才让位。
 *
 * <p>字修的字典本身只对砚秋出现，所以其他角色不应该被它挡住——那些角色照常显示本模组的图鉴。
 * 判定用字修 PlayerClass 的枚举名 {@code ZIXIU}（字符串比较，不依赖字修的类）；
 * 配置直接读字修自己的 SpireConfig 文件，不写它的文件，缓存一秒。
 */
public final class ZixiuCompat {

    private static final long CACHE_MS = 1000L;
    private static final String ZIXIU_PLAYER_CLASS = "ZIXIU";

    private static long lastCheck;
    private static boolean zixiuLibraryOn;

    private ZixiuCompat() {
    }

    public static boolean suppressedByZixiu() {
        if (!Loader.isModLoaded("zixiu")) {
            return false;
        }
        if (!isZixiuRun()) {
            // 其他角色：字修的字典根本不会出现，本模组照常工作
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck > CACHE_MS) {
            lastCheck = now;
            zixiuLibraryOn = readZixiuLibraryFlag();
        }
        return zixiuLibraryOn;
    }

    private static boolean isZixiuRun() {
        return AbstractDungeon.player != null
                && AbstractDungeon.player.chosenClass != null
                && ZIXIU_PLAYER_CLASS.equals(AbstractDungeon.player.chosenClass.name());
    }

    private static boolean readZixiuLibraryFlag() {
        try {
            Path path = Paths.get(SpireConfig.makeFilePath("zixiu", "zixiuConfig"));
            if (!Files.exists(path)) {
                return true;
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
            return Boolean.parseBoolean(props.getProperty("cardLibrary", "true"));
        } catch (Exception e) {
            // 读不到就按「字修的字典开着」处理，宁可让位也不要出现两个图鉴。
            return true;
        }
    }
}
