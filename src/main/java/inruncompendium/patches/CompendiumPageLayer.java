package inruncompendium.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.megacrit.cardcrawl.ui.buttons.CancelButton;
import inruncompendium.ui.CardCompendium;

/**
 * 卡墙 / 颜色 tab / 排序条画在**原版「返回」按钮之前**。
 *
 * <p>{@code AbstractDungeon.render()} 的顺序是：黑幕 → 当前界面 → 渐变 → 「返回」按钮 →
 * 横幅 → 顶栏。我们的页面挂在 {@code CancelButton.render} 的 Prefix 上，于是：
 * <ul>
 *   <li>「返回」按钮盖在卡墙上面（和原版看牌界面一样，点得到、看得清）；</li>
 *   <li>顶栏和它的悬浮说明仍然盖在页面上面（顶栏在更后面画）。</li>
 * </ul>
 */
@SpirePatch(clz = CancelButton.class, method = "render",
        paramtypes = {"com.badlogic.gdx.graphics.g2d.SpriteBatch"})
public class CompendiumPageLayer {

    @SpirePrefixPatch
    public static void Prefix(CancelButton __instance, SpriteBatch sb) {
        CardCompendium.renderPage(sb);
    }
}
