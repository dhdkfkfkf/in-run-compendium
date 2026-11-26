package inruncompendium.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.screens.MasterDeckViewScreen;
import inruncompendium.ui.CardCompendium;

/** 图鉴占着 {@code MASTER_DECK_VIEW} 格子，原版牌组界面的 update 必须让开。 */
@SpirePatch(clz = MasterDeckViewScreen.class, method = "update")
public class CompendiumDeckViewUpdateBlock {

    @SpirePrefixPatch
    public static SpireReturn<Void> Prefix() {
        if (CardCompendium.isOpen()) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }
}
