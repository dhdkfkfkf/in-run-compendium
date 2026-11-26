package inruncompendium;

import basemod.BaseMod;
import basemod.ModLabeledToggleButton;
import basemod.ModPanel;
import basemod.interfaces.PostInitializeSubscriber;
import com.badlogic.gdx.graphics.Color;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import inruncompendium.ui.CardCompendium;

/**
 * 局内卡牌图鉴：在任何局内界面查看所有已注册卡牌。
 *
 * <p>只依赖 BaseMod：一个 {@link CardCompendium} 订阅者负责全部界面与输入，
 * 补丁都在 {@code inruncompendium.patches} 里。
 */
@SpireInitializer
public class InRunCompendium implements PostInitializeSubscriber {

    public static final String MOD_ID = "inruncompendium";

    public static void initialize() {
        new InRunCompendium();
    }

    public InRunCompendium() {
        CompendiumConfig.load();
        BaseMod.subscribe(this);
        BaseMod.subscribe(new CardCompendium());
    }

    @Override
    public void receivePostInitialize() {
        ModPanel panel = new ModPanel();
        ModLabeledToggleButton toggle = new ModLabeledToggleButton(
                "启用「局内卡牌图鉴」（顶栏书本图标；若字修自带字典开着，本模组自动让位）",
                400.0F, 700.0F,
                Color.WHITE,
                FontHelper.charDescFont,
                CompendiumConfig.enabled(),
                panel,
                text -> { },
                button -> CompendiumConfig.setEnabled(button.enabled));
        panel.addUIElement(toggle);

        BaseMod.registerModBadge(
                ImageMaster.loadImage("inruncompendium/images/ui/book.png"),
                "局内卡牌图鉴", "dhdkfkfkf",
                "局内查看所有卡牌的图鉴，布局与手感照原版看牌界面。", panel);
    }
}
