package inruncompendium.patches;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.screens.MasterDeckViewScreen;
import inruncompendium.ui.CardCompendium;

/** 同上：图鉴开着时不画原版牌组界面（否则玩家自己的牌组会叠在卡墙上）。 */
@SpirePatch(clz = MasterDeckViewScreen.class, method = "render",
        paramtypes = {"com.badlogic.gdx.graphics.g2d.SpriteBatch"})
public class CompendiumDeckViewRenderBlock {

    @SpirePrefixPatch
    public static SpireReturn<Void> Prefix(MasterDeckViewScreen __instance, SpriteBatch sb) {
        if (CardCompendium.isOpen()) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }
}
