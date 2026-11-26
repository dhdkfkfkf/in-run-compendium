package inruncompendium.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.ui.panels.TopPanel;
import inruncompendium.ui.CardCompendium;

/**
 * 卡墙 / 排序条 / 颜色 tab 画在顶栏之前：和原版看牌界面同一层次，
 * 顶栏图标和它们的悬浮说明永远盖在图鉴上面。书本图标本身在 {@code AbstractDungeon.render} 之后画。
 */
@SpirePatch(clz = TopPanel.class, method = "render",
        paramtypes = {"com.badlogic.gdx.graphics.g2d.SpriteBatch"})
public class CompendiumPageLayer {

    @SpirePrefixPatch
    public static void Prefix(TopPanel __instance, SpriteBatch sb) {
        CardCompendium.renderPage(sb);
    }
}
