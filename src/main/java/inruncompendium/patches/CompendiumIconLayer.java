package inruncompendium.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import inruncompendium.ui.CardCompendium;

/**
 * 顶栏的书本图标画在 {@code AbstractDungeon.render} 之后（顶栏与所有界面之上、鼠标光标之前），
 * 保证它永远看得见、也压在自己的卡墙和原版 tooltip 上面。
 */
@SpirePatch(clz = AbstractDungeon.class, method = "render",
        paramtypes = {"com.badlogic.gdx.graphics.g2d.SpriteBatch"})
public class CompendiumIconLayer {

    @SpirePostfixPatch
    public static void Postfix(AbstractDungeon __instance, SpriteBatch sb) {
        CardCompendium.renderIcon(sb);
    }
}
