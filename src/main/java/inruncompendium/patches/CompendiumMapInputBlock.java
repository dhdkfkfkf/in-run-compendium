package inruncompendium.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePrefixPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpireReturn;
import com.megacrit.cardcrawl.screens.DungeonMapScreen;
import inruncompendium.ui.CardCompendium;

/** 图鉴开着时不跑地图的 update，底下的节点不会被 hover / 点到。 */
@SpirePatch(clz = DungeonMapScreen.class, method = "update")
public class CompendiumMapInputBlock {

    @SpirePrefixPatch
    public static SpireReturn<Void> Prefix(DungeonMapScreen __instance) {
        if (CardCompendium.isOpen()) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }
}
