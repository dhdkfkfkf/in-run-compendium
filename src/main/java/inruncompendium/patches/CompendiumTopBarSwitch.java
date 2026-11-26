package inruncompendium.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.ui.panels.TopPanel;
import inruncompendium.ui.CardCompendium;

/**
 * 图鉴开着时，点顶栏的地图 / 牌组 / 设置可以直接切过去。
 *
 * <p>先在顶栏处理这一帧的点击之前把图鉴收起来（走原版 {@code closeCurrentScreen}），
 * 再让原版照常打开对应界面——于是三个按钮的行为和「没开图鉴时」完全一样。
 */
@SpirePatch(clz = TopPanel.class, method = "update")
public class CompendiumTopBarSwitch {

    @SpirePrefixPatch
    public static void Prefix(TopPanel __instance) {
        if (!CardCompendium.isOpen() || !InputHelper.justClickedLeft) {
            return;
        }
        if (inHb(__instance.mapHb) || inHb(__instance.deckHb) || inHb(__instance.settingsHb)) {
            CardCompendium.closeForTopBarSwitch();
        }
    }

    private static boolean inHb(Hitbox hb) {
        return hb != null
                && InputHelper.mX >= hb.x && InputHelper.mX <= hb.x + hb.width
                && InputHelper.mY >= hb.y && InputHelper.mY <= hb.y + hb.height;
    }
}
