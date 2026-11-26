package inruncompendium.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.ui.panels.TopPanel;
import inruncompendium.ui.CardCompendium;

import java.lang.reflect.Field;

/**
 * 图鉴开着时别让原版牌组图标跟着晃（它以为 {@code MASTER_DECK_VIEW} 是牌组界面）。
 * 只在方法跑完后把 {@code deckAngle} 归零，hover / 悬浮说明 / 点击都不受影响。
 */
@SpirePatch(clz = TopPanel.class, method = "updateDeckViewButtonLogic")
public class CompendiumDeckIconFreeze {

    private static Field deckAngleField;

    @SpirePostfixPatch
    public static void Postfix(TopPanel __instance) {
        if (!CardCompendium.isOpen()) {
            return;
        }
        Field field = deckAngleField;
        if (field == null) {
            try {
                field = TopPanel.class.getDeclaredField("deckAngle");
                field.setAccessible(true);
                deckAngleField = field;
            } catch (Exception e) {
                return;
            }
        }
        try {
            field.setFloat(__instance, 0.0F);
        } catch (Exception ignored) {
            // 拿不到就保持原样，不影响功能。
        }
    }
}
