package inruncompendium;

import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 和字修（{@code zixiu}）的互斥：字修自带的「字典」开着时，本模组自动让位。
 *
 * <p>用字修自己的 SpireConfig 文件判断（同一个 modid + 文件名，路径由 MTS 决定），
 * 不反射它的类，也不写它的文件。缓存一秒，玩家在字修设置里改完开关后最多一秒生效。
 */
public final class ZixiuCompat {

    private static final long CACHE_MS = 1000L;

    private static long lastCheck;
    private static boolean zixiuLibraryOn;

    private ZixiuCompat() {
    }

    public static boolean suppressedByZixiu() {
        if (!Loader.isModLoaded("zixiu")) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastCheck > CACHE_MS) {
            lastCheck = now;
            zixiuLibraryOn = readZixiuLibraryFlag();
        }
        return zixiuLibraryOn;
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
