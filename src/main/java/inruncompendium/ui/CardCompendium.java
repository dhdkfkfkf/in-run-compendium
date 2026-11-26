package inruncompendium.ui;

import basemod.interfaces.PostUpdateSubscriber;
import basemod.patches.com.megacrit.cardcrawl.screens.mainMenu.ColorTabBar.ColorTabBarFix;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.GameCursor;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.MasterDeckViewScreen;
import com.megacrit.cardcrawl.screens.SingleCardViewPopup;
import com.megacrit.cardcrawl.screens.compendium.CardLibSortHeader;
import com.megacrit.cardcrawl.screens.mainMenu.ColorTabBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.screens.mainMenu.SortHeaderButton;
import com.megacrit.cardcrawl.screens.mainMenu.SortHeaderButtonListener;
import com.megacrit.cardcrawl.screens.mainMenu.TabBarListener;
import inruncompendium.CompendiumConfig;
import inruncompendium.ZixiuCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 局内卡牌图鉴：在任何局内界面查看所有已注册的卡牌（原版 + 各模组）。
 *
 * <p>颜色分页直接用原版 {@link ColorTabBar} 组件（BaseMod 已经给它打过补丁：
 * 模组颜色会自动多出 tab，颜色和名字都取模组注册的值），所以我们拿到的是和原版图鉴
 * 完全一样的 tab 图、悬停/点击手感、排序条染色，以及「看升级版」勾选框。
 *
 * <p>因为局内顶栏占了画面上方，卡墙整体比原版图鉴低一点（{@code 0.63 * HEIGHT} 起），
 * 好让原版 tab 完整露在顶栏下面。
 *
 * <p>交互照原版看牌界面：按住空白 1:1 拖动、滚轮、原版滚动条、原版排序按钮、点卡开大图。
 *
 * <p>和字修的「字典」互斥：字修配置里「字典」开着时本模组自动隐藏（见 {@link ZixiuCompat}）。
 */
public class CardCompendium implements PostUpdateSubscriber {

    // ---- 布局 ----

    private static final int COLS = 5;
    private static final float CARD_SCALE = 0.75F;
    private static final float HEADER_LIFT = 230.0F;
    /** 原版是 0.66；局内要低一点，给顶栏下的颜色 tab 留出完整高度。 */
    private static final float GRID_TOP_RATIO = 0.63F;

    private static final float ICON_SIZE = 64.0F;
    private static final float ICON_SLOT_SPACING = 74.0F;

    private static float drawStartX;
    private static float drawStartY;
    private static float padX;
    private static float padY;

    // ---- 数据 ----

    private static boolean dataBuilt;
    private static final List<AbstractCard.CardColor> COLORS = new ArrayList<AbstractCard.CardColor>();
    private static final Map<AbstractCard.CardColor, CardGroup> GROUPS =
            new LinkedHashMap<AbstractCard.CardColor, CardGroup>();
    private static AbstractCard.CardColor selectedColor;
    private static CardGroup currentGroup;
    private static int totalCardCount;

    // ---- 滚动状态 ----

    private static float currentDiffY;
    private static float grabStartY;
    private static boolean grabbedScreen;
    private static float scrollLowerBound;
    private static float scrollUpperBound;
    private static boolean justSorted;

    private static AbstractCard hoveredCard;
    private static AbstractCard clickStartedCard;
    private static ColorTabBar colorBar;
    private static ScrollBar scrollBar;
    private static SortHeaderButton[] sortButtons;
    private static int[] sortKinds;

    private static final int SORT_RARITY = 0;
    private static final int SORT_TYPE = 1;
    private static final int SORT_COST = 2;
    private static final int SORT_NAME = 3;

    private static int sortKind = SORT_TYPE;
    private static boolean lastAscending = true;

    // ---- 开关状态 ----

    private static boolean wantOpen;

    private static final Hitbox ICON_HB = new Hitbox(ICON_SIZE, ICON_SIZE);
    private static Texture iconTexture;
    private static float iconAngle;
    private static float iconRotateTimer;

    private static final ScrollBarListener BAR_LISTENER = new ScrollBarListener() {
        @Override
        public void scrolledUsingBar(float percent) {
            CardCompendium.scrolledUsingBar(percent);
        }
    };

    private static final SortHeaderButtonListener SORT_LISTENER = new SortHeaderButtonListener() {
        @Override
        public void didChangeOrder(SortHeaderButton button, boolean ascending) {
            CardCompendium.didChangeOrder(button, ascending);
        }
    };

    private static final TabBarListener TAB_LISTENER = new TabBarListener() {
        @Override
        public void didChangeTab(ColorTabBar bar, ColorTabBar.CurrentTab tab) {
            CardCompendium.changeTab(tab);
        }
    };

    // ---- 查询 ----

    public static boolean isOpen() {
        return wantOpen;
    }

    /** 原版牌组按钮的禁用清单（{@code TopPanel.updateDeckViewButtonLogic()}）。 */
    private static boolean deckButtonAvailable() {
        AbstractDungeon.CurrentScreen screen = AbstractDungeon.screen;
        if (screen == null) {
            return false;
        }
        return screen == AbstractDungeon.CurrentScreen.NONE
                || screen == AbstractDungeon.CurrentScreen.GRID
                || screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD
                || screen == AbstractDungeon.CurrentScreen.MASTER_DECK_VIEW
                || screen == AbstractDungeon.CurrentScreen.DEATH
                || screen == AbstractDungeon.CurrentScreen.VICTORY
                || screen == AbstractDungeon.CurrentScreen.BOSS_REWARD
                || screen == AbstractDungeon.CurrentScreen.SHOP
                || screen == AbstractDungeon.CurrentScreen.SETTINGS
                || screen == AbstractDungeon.CurrentScreen.INPUT_SETTINGS
                || screen == AbstractDungeon.CurrentScreen.MAP
                || screen == AbstractDungeon.CurrentScreen.HAND_SELECT
                || screen == AbstractDungeon.CurrentScreen.CARD_REWARD;
    }

    private static boolean canUseCompendium() {
        if (!CompendiumConfig.enabled()) {
            return false;
        }
        if (ZixiuCompat.suppressedByZixiu()) {
            return false;
        }
        if (CardCrawlGame.mode != CardCrawlGame.GameMode.GAMEPLAY
                && CardCrawlGame.mode != CardCrawlGame.GameMode.DUNGEON_TRANSITION) {
            return false;
        }
        if (AbstractDungeon.currMapNode == null || AbstractDungeon.getCurrRoom() == null) {
            return false;
        }
        return deckButtonAvailable();
    }

    // ---- 输入 ----

    @Override
    public void receivePostUpdate() {
        if (wantOpen) {
            updateOpen();
            return;
        }
        if (!canUseCompendium() || CardCrawlGame.isPopupOpen) {
            return;
        }
        buildData();
        updateLayout();
        updateIconHb();
        updateIconAngle();
        if (ICON_HB.hovered && InputHelper.justClickedLeft) {
            open();
        }
    }

    private static void updateOpen() {
        if (!canUseCompendium()
                || AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MASTER_DECK_VIEW) {
            abandon();
            return;
        }

        updateLayout();
        updateIconHb();
        updateIconAngle();

        if (CardCrawlGame.isPopupOpen) {
            return;
        }

        if (ICON_HB.hovered && InputHelper.justClickedLeft) {
            close();
            return;
        }

        updateHeader();
        boolean barDragging = scrollBar.update();
        if (!barDragging) {
            updateScrolling();
        }
        updateCards();
        updateClicking();

        if (InputHelper.pressedEscape) {
            InputHelper.pressedEscape = false;
            close();
        }
    }

    private static void open() {
        buildData();
        if (COLORS.isEmpty()) {
            return;
        }
        wantOpen = true;
        updateLayout();
        buildWidgets();

        if (selectedColor == null || !GROUPS.containsKey(selectedColor)) {
            selectedColor = COLORS.get(0);
        }
        currentGroup = GROUPS.get(selectedColor);
        sortGroup(currentGroup, sortKind, lastAscending);
        calculateScrollBounds();

        grabbedScreen = false;
        hoveredCard = null;
        clickStartedCard = null;
        justSorted = false;
        currentDiffY = scrollLowerBound;
        grabStartY = currentDiffY;

        snapCards();
        updateHeader();

        // 和原版牌组界面 MasterDeckViewScreen.open() 一样接管屏幕
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.NONE
                && AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MASTER_DECK_VIEW) {
            AbstractDungeon.previousScreen = AbstractDungeon.screen;
        }
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.MASTER_DECK_VIEW;
        AbstractDungeon.isScreenUp = true;

        AbstractDungeon.overlayMenu.showBlackScreen();
        AbstractDungeon.overlayMenu.hideCombatPanels();
        AbstractDungeon.overlayMenu.proceedButton.hide();
        AbstractDungeon.overlayMenu.cancelButton.show(MasterDeckViewScreen.TEXT[1]);
        AbstractDungeon.dynamicBanner.hide();

        updateBarPosition();
    }

    private static void close() {
        wantOpen = false;
        grabbedScreen = false;
        hoveredCard = null;
        clickStartedCard = null;
        if (AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null) {
            AbstractDungeon.closeCurrentScreen();
        } else {
            AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NONE;
            AbstractDungeon.isScreenUp = false;
            AbstractDungeon.overlayMenu.hideBlackScreen();
        }
    }

    /** 给顶栏切换补丁用：图鉴开着时点地图 / 牌组 / 设置，先收起再交给原版。 */
    public static void closeForTopBarSwitch() {
        if (wantOpen) {
            close();
        }
    }

    private static void abandon() {
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.MASTER_DECK_VIEW) {
            close();
            return;
        }
        wantOpen = false;
        grabbedScreen = false;
        hoveredCard = null;
        clickStartedCard = null;
    }

    // ---- 数据 ----

    private static void buildData() {
        if (dataBuilt) {
            return;
        }
        dataBuilt = true;

        Map<AbstractCard.CardColor, List<AbstractCard>> byColor =
                new HashMap<AbstractCard.CardColor, List<AbstractCard>>();
        int total = 0;
        for (AbstractCard card : CardLibrary.cards.values()) {
            if (card == null || card.color == null) {
                continue;
            }
            total++;
            List<AbstractCard> list = byColor.get(card.color);
            if (list == null) {
                list = new ArrayList<AbstractCard>();
                byColor.put(card.color, list);
            }
            list.add(card);
        }
        totalCardCount = total;

        List<AbstractCard.CardColor> ordered = new ArrayList<AbstractCard.CardColor>();
        addIfPresent(ordered, byColor, AbstractCard.CardColor.RED);
        addIfPresent(ordered, byColor, AbstractCard.CardColor.GREEN);
        addIfPresent(ordered, byColor, AbstractCard.CardColor.BLUE);
        addIfPresent(ordered, byColor, AbstractCard.CardColor.PURPLE);
        addIfPresent(ordered, byColor, AbstractCard.CardColor.COLORLESS);
        addIfPresent(ordered, byColor, AbstractCard.CardColor.CURSE);

        List<AbstractCard.CardColor> others = new ArrayList<AbstractCard.CardColor>();
        for (AbstractCard.CardColor color : byColor.keySet()) {
            if (!isVanillaColor(color)) {
                others.add(color);
            }
        }
        others.sort((a, b) -> a.name().compareTo(b.name()));
        ordered.addAll(others);

        COLORS.clear();
        COLORS.addAll(ordered);
        for (AbstractCard.CardColor color : COLORS) {
            CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
            group.group.addAll(byColor.get(color));
            sortGroup(group, SORT_TYPE, true);
            GROUPS.put(color, group);
        }
    }

    private static void addIfPresent(List<AbstractCard.CardColor> out,
                                     Map<AbstractCard.CardColor, List<AbstractCard>> byColor,
                                     AbstractCard.CardColor color) {
        if (byColor.containsKey(color)) {
            out.add(color);
        }
    }

    private static boolean isVanillaColor(AbstractCard.CardColor color) {
        return color == AbstractCard.CardColor.RED
                || color == AbstractCard.CardColor.GREEN
                || color == AbstractCard.CardColor.BLUE
                || color == AbstractCard.CardColor.PURPLE
                || color == AbstractCard.CardColor.COLORLESS
                || color == AbstractCard.CardColor.CURSE;
    }

    // ---- 颜色 tab：直接用原版 ColorTabBar ----

    private static void changeTab(ColorTabBar.CurrentTab tab) {
        AbstractCard.CardColor color = colorForTab(tab);
        if (color != null) {
            switchToColor(color);
        }
    }

    private static AbstractCard.CardColor colorForTab(ColorTabBar.CurrentTab tab) {
        if (tab == ColorTabBar.CurrentTab.RED) {
            return AbstractCard.CardColor.RED;
        }
        if (tab == ColorTabBar.CurrentTab.GREEN) {
            return AbstractCard.CardColor.GREEN;
        }
        if (tab == ColorTabBar.CurrentTab.BLUE) {
            return AbstractCard.CardColor.BLUE;
        }
        if (tab == ColorTabBar.CurrentTab.PURPLE) {
            return AbstractCard.CardColor.PURPLE;
        }
        if (tab == ColorTabBar.CurrentTab.COLORLESS) {
            return AbstractCard.CardColor.COLORLESS;
        }
        if (tab == ColorTabBar.CurrentTab.CURSE) {
            return AbstractCard.CardColor.CURSE;
        }
        // BaseMod 追加的「模组颜色」tab：枚举值是 MOD，点中的具体颜色存在它的静态列表里
        if ("MOD".equals(tab.name())) {
            ColorTabBarFix.ModColorTab modTab = ColorTabBarFix.Fields.getModTab();
            return modTab == null ? null : modTab.color;
        }
        return null;
    }

    private static void switchToColor(AbstractCard.CardColor color) {
        CardGroup group = GROUPS.get(color);
        if (group == null) {
            return;
        }
        selectedColor = color;
        currentGroup = group;
        sortGroup(currentGroup, sortKind, lastAscending);
        currentDiffY = scrollLowerBound;
        grabStartY = currentDiffY;
        grabbedScreen = false;
        hoveredCard = null;
        clickStartedCard = null;
        justSorted = false;
        calculateScrollBounds();
        snapCards();
        updateBarPosition();
    }

    // ---- 滚动 ----

    private static void updateScrolling() {
        int y = InputHelper.mY;
        if (!grabbedScreen) {
            if (InputHelper.scrolledDown) {
                currentDiffY += Settings.SCROLL_SPEED;
            } else if (InputHelper.scrolledUp) {
                currentDiffY -= Settings.SCROLL_SPEED;
            }
            if (InputHelper.justClickedLeft) {
                grabbedScreen = true;
                grabStartY = y - currentDiffY;
            }
        } else if (InputHelper.isMouseDown) {
            currentDiffY = y - grabStartY;
        } else {
            grabbedScreen = false;
        }
        resetScrolling();
        updateBarPosition();
    }

    private static void resetScrolling() {
        if (currentDiffY < scrollLowerBound) {
            currentDiffY = MathHelper.scrollSnapLerpSpeed(currentDiffY, scrollLowerBound);
        } else if (currentDiffY > scrollUpperBound) {
            currentDiffY = MathHelper.scrollSnapLerpSpeed(currentDiffY, scrollUpperBound);
        }
    }

    private static void scrolledUsingBar(float percent) {
        currentDiffY = MathHelper.valueFromPercentBetween(scrollLowerBound, scrollUpperBound, percent);
        updateBarPosition();
    }

    private static void updateBarPosition() {
        float percent = MathHelper.percentFromValueBetween(
                scrollLowerBound, scrollUpperBound, currentDiffY);
        scrollBar.parentScrolledToPercent(percent);
    }

    private static boolean shouldShowScrollBar() {
        return scrollUpperBound > 500.0F * Settings.scale;
    }

    // ---- 卡墙 ----

    private static void updateCards() {
        hoveredCard = null;
        List<AbstractCard> cards = currentGroup.group;
        int row = 0;
        for (int i = 0; i < cards.size(); i++) {
            int col = i % COLS;
            if (col == 0 && i != 0) {
                row++;
            }
            AbstractCard card = cards.get(i);
            card.target_x = drawStartX + col * padX;
            card.target_y = drawStartY + currentDiffY - row * padY;
            card.update();
            card.updateHoverLogic();
            if (card.hb.hovered) {
                hoveredCard = card;
            }
        }
        if (justSorted) {
            for (AbstractCard card : cards) {
                card.current_x = card.target_x;
                card.current_y = card.target_y;
            }
            justSorted = false;
        }
    }

    private static void updateClicking() {
        if (hoveredCard == null) {
            clickStartedCard = null;
            return;
        }
        CardCrawlGame.cursor.changeType(GameCursor.CursorType.INSPECT);
        if (InputHelper.justClickedLeft) {
            clickStartedCard = hoveredCard;
        }
        if (InputHelper.justReleasedClickLeft
                && hoveredCard == clickStartedCard
                && !CardCrawlGame.isPopupOpen) {
            InputHelper.justReleasedClickLeft = false;
            CardCrawlGame.cardPopup.open(hoveredCard, currentGroup);
            clickStartedCard = null;
        }
    }

    private static void snapCards() {
        List<AbstractCard> cards = currentGroup.group;
        int row = 0;
        for (int i = 0; i < cards.size(); i++) {
            int col = i % COLS;
            if (col == 0 && i != 0) {
                row++;
            }
            AbstractCard card = cards.get(i);
            card.current_x = drawStartX + col * padX;
            card.current_y = drawStartY + currentDiffY - row * padY;
            card.target_x = card.current_x;
            card.target_y = card.current_y;
            card.drawScale = CARD_SCALE;
            card.targetDrawScale = CARD_SCALE;
            card.angle = 0.0F;
            card.targetAngle = 0.0F;
            card.transparency = 1.0F;
            card.targetTransparency = 1.0F;
        }
    }

    // ---- 顶栏：颜色 tab + 排序按钮 ----

    private static float headerY() {
        List<AbstractCard> cards = currentGroup == null ? null : currentGroup.group;
        float topY = (cards == null || cards.isEmpty())
                ? drawStartY + currentDiffY : cards.get(0).current_y;
        return topY + HEADER_LIFT * Settings.yScale;
    }

    private static void updateHeader() {
        float y = headerY();
        // 原版 ColorTabBar：tab 的悬停/点击、模组颜色 tab、颜色条、看升级版勾选框全在里面
        colorBar.update(y);
        for (SortHeaderButton button : sortButtons) {
            button.updateScrollPosition(y);
            button.update();
        }
    }

    private static void didChangeOrder(SortHeaderButton button, boolean ascending) {
        int kind = SORT_TYPE;
        for (int i = 0; i < sortButtons.length; i++) {
            if (sortButtons[i] == button) {
                kind = sortKinds[i];
            }
        }
        sortKind = kind;
        lastAscending = ascending;
        sortGroup(currentGroup, kind, ascending);
        currentGroup.sortByStatus(false);
        justSorted = true;
        for (int i = 0; i < sortButtons.length; i++) {
            sortButtons[i].setActive(sortKinds[i] == sortKind);
        }
    }

    private static void sortGroup(CardGroup group, int kind, boolean ascending) {
        if (kind == SORT_RARITY) {
            group.sortByRarity(ascending);
        } else if (kind == SORT_COST) {
            group.sortByCost(ascending);
        } else if (kind == SORT_NAME) {
            group.sortAlphabetically(ascending);
        } else {
            group.sortByType(ascending);
        }
    }

    // ---- 绘制 ----

    public static void renderIcon(SpriteBatch sb) {
        if (!canUseCompendium()) {
            return;
        }
        if (iconTexture == null) {
            iconTexture = ImageMaster.loadImage("inruncompendium/images/ui/book.png");
        }
        if (iconTexture == null) {
            return;
        }

        float s = Settings.scale;
        float x = ICON_HB.cX - ICON_SIZE / 2.0F;
        float y = ICON_HB.cY - ICON_SIZE / 2.0F;

        sb.setColor(Color.WHITE);
        drawIcon(sb, x, y, s);

        if (ICON_HB.hovered || wantOpen) {
            sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            sb.setColor(1.0F, 1.0F, 1.0F, 0.25F);
            drawIcon(sb, x, y, s);
            sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        sb.setColor(Color.WHITE);

        if (ICON_HB.hovered && !CardCrawlGame.isPopupOpen) {
            TipHelper.renderGenericTip(1550.0F * Settings.scale,
                    Settings.HEIGHT - 120.0F * Settings.scale,
                    "局内卡牌图鉴",
                    "查看所有卡牌（" + totalCardCount + "）");
        }
    }

    private static void drawIcon(SpriteBatch sb, float x, float y, float s) {
        sb.draw(iconTexture, x, y, ICON_SIZE / 2.0F, ICON_SIZE / 2.0F,
                ICON_SIZE, ICON_SIZE, s, s, iconAngle, 0, 0, 128, 128, false, false);
    }

    /** 由补丁在 {@code TopPanel.render} 之前调用：卡墙压在顶栏下面，原版 tooltip 才盖得住。 */
    public static void renderPage(SpriteBatch sb) {
        if (!wantOpen || sortButtons == null || currentGroup == null) {
            return;
        }
        updateLayout();

        float y = headerY();
        colorBar.render(sb, y);
        for (SortHeaderButton button : sortButtons) {
            button.updateScrollPosition(y);
            button.render(sb);
        }

        currentGroup.renderInLibrary(sb);
        if (hoveredCard != null) {
            hoveredCard.renderHoverShadow(sb);
            hoveredCard.renderInLibrary(sb);
        }
        currentGroup.renderTip(sb);

        if (shouldShowScrollBar()) {
            scrollBar.render(sb);
        }
    }

    // ---- 布局 ----

    private static void updateLayout() {
        drawStartY = Settings.HEIGHT * GRID_TOP_RATIO;
        float w = AbstractCard.IMG_WIDTH * CARD_SCALE;
        padX = w + Settings.CARD_VIEW_PAD_X;
        padY = AbstractCard.IMG_HEIGHT * CARD_SCALE + Settings.CARD_VIEW_PAD_Y;
        drawStartX = (Settings.WIDTH - COLS * w - (COLS - 1) * Settings.CARD_VIEW_PAD_X) / 2.0F
                + w / 2.0F;
        scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    }

    private static void updateIconHb() {
        float slot = (ICON_SIZE + ICON_SLOT_SPACING) * Settings.scale;
        ICON_HB.width = ICON_SIZE * Settings.scale;
        ICON_HB.height = ICON_SIZE * Settings.scale;
        ICON_HB.move(Settings.WIDTH - slot * 4.0F + ICON_SIZE * Settings.scale / 2.0F,
                Settings.HEIGHT - ICON_SIZE * Settings.scale / 2.0F);
        ICON_HB.update();
    }

    private static void updateIconAngle() {
        float target;
        if (wantOpen) {
            iconRotateTimer += Gdx.graphics.getDeltaTime() * 4.0F;
            target = (float) Math.sin(iconRotateTimer) * 15.0F;
        } else if (ICON_HB.hovered) {
            target = 15.0F;
        } else {
            target = 0.0F;
        }
        iconAngle = MathHelper.angleLerpSnap(iconAngle, target);
    }

    private static void calculateScrollBounds() {
        int size = currentGroup == null ? 0 : currentGroup.size();
        if (size > COLS * 2) {
            int rows = size / COLS - 2;
            if (size % COLS != 0) {
                rows++;
            }
            scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT + rows * padY;
        } else {
            scrollUpperBound = Settings.DEFAULT_SCROLL_LIMIT;
        }
    }

    private static void buildWidgets() {
        // ColorTabBar 只建一次：它自己记着选中的 tab（含 BaseMod 的模组颜色 tab），重开图鉴能保持
        if (colorBar == null) {
            colorBar = new ColorTabBar(TAB_LISTENER);
        }
        scrollBar = new ScrollBar(BAR_LISTENER);
        scrollBar.move(0.0F, -30.0F * Settings.scale);

        sortKinds = Settings.removeAtoZSort
                ? new int[]{SORT_RARITY, SORT_TYPE, SORT_COST}
                : new int[]{SORT_RARITY, SORT_TYPE, SORT_COST, SORT_NAME};
        sortButtons = new SortHeaderButton[sortKinds.length];
        for (int i = 0; i < sortKinds.length; i++) {
            sortButtons[i] = new SortHeaderButton(sortLabel(sortKinds[i]), 0.0F, 0.0F, SORT_LISTENER);
            sortButtons[i].hb.move(
                    CardLibSortHeader.START_X + i * CardLibSortHeader.SPACE_X,
                    sortButtons[i].hb.cY);
            sortButtons[i].setActive(sortKinds[i] == sortKind);
        }
    }

    private static String sortLabel(int kind) {
        if (kind == SORT_RARITY) {
            return CardLibSortHeader.TEXT[0];
        }
        if (kind == SORT_COST) {
            return CardLibSortHeader.TEXT[3];
        }
        if (kind == SORT_NAME) {
            return CardLibSortHeader.TEXT[2];
        }
        return CardLibSortHeader.TEXT[1];
    }
}
