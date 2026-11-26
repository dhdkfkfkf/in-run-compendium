package inruncompendium.ui;

import basemod.interfaces.PostUpdateSubscriber;
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
import com.megacrit.cardcrawl.screens.compendium.CardLibraryScreen;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBar;
import com.megacrit.cardcrawl.screens.mainMenu.ScrollBarListener;
import com.megacrit.cardcrawl.screens.mainMenu.SortHeaderButton;
import com.megacrit.cardcrawl.screens.mainMenu.SortHeaderButtonListener;
import inruncompendium.CompendiumConfig;
import inruncompendium.ZixiuCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 局内卡牌图鉴：在任何局内界面查看所有已注册的卡牌（原版 + 各模组）。
 *
 * <p>交互和原版看牌界面（{@code CardLibraryScreen} / {@code MasterDeckViewScreen}）完全一致：
 * 按住空白 1:1 拖动、滚轮、原版滚动条、原版排序按钮（稀有度 / 类型 / 耗能 / A-Z，点一下切升降序）、
 * 原版「看升级版」勾选框、点卡开原版大图并可连续翻阅。
 *
 * <p>内容按卡牌颜色分页：原版六色用原版本地化的名字，模组自定义颜色用枚举名（如 {@code Zixiu}）。
 * 颜色 tab 画在排序条上方，超过 12 个自动换行。
 *
 * <p>和字修的「字典」互斥：字修的配置里「字典」开着时，本模组自动隐藏（见 {@link ZixiuCompat}）。
 *
 * <p>可用时机和顶栏「牌组」按钮一致；打开时占用 {@code MASTER_DECK_VIEW} 屏幕格子，
 * 收起走原版 {@code closeCurrentScreen()}，顶栏地图 / 牌组 / 设置可以直接切走。
 */
public class CardCompendium implements PostUpdateSubscriber {

    // ---- 布局（照抄 CardLibraryScreen / ColorTabBar） ----

    private static final int COLS = 5;
    private static final float CARD_SCALE = 0.75F;
    private static final float HEADER_LIFT = 230.0F;
    private static final float BAR_W = 1334.0F;
    private static final float BAR_H = 102.0F;

    private static final float ICON_SIZE = 64.0F;
    private static final float ICON_SLOT_SPACING = 74.0F;

    private static final float UPGRADE_HB_X = 1410.0F;
    private static final float UPGRADE_HB_W = 360.0F;
    private static final float UPGRADE_HB_H = 48.0F;
    private static final float UPGRADE_LABEL_X = 1546.0F;
    private static final float UPGRADE_BOX_X = 1532.0F;

    /**
     * 颜色列表放在卡墙左边一列（局内顶栏下方没有空间放原版那种横向 tab）。
     * 每列最多 12 个，颜色多了就并排第二列。
     */
    private static final int COLOR_ROWS = 12;
    private static final float COLOR_COL_W = 150.0F;
    private static final float COLOR_ROW_H = 42.0F;

    private static float drawStartX;
    private static float drawStartY;
    private static float padX;
    private static float padY;

    // ---- 数据 ----

    private static boolean dataBuilt;
    private static final List<AbstractCard.CardColor> COLORS = new ArrayList<AbstractCard.CardColor>();
    private static final Map<AbstractCard.CardColor, CardGroup> GROUPS =
            new LinkedHashMap<AbstractCard.CardColor, CardGroup>();
    private static final Map<AbstractCard.CardColor, Color> BAR_COLORS =
            new HashMap<AbstractCard.CardColor, Color>();
    private static final List<Hitbox> TAB_HBS = new ArrayList<Hitbox>();
    private static int tabIndex;
    private static int totalCardCount;
    private static CardGroup currentGroup;

    // ---- 滚动状态 ----

    private static float currentDiffY;
    private static float grabStartY;
    private static boolean grabbedScreen;
    private static float scrollLowerBound;
    private static float scrollUpperBound;
    private static boolean justSorted;

    private static AbstractCard hoveredCard;
    private static AbstractCard clickStartedCard;
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
    private static final Hitbox UPGRADE_HB = new Hitbox(UPGRADE_HB_W, UPGRADE_HB_H);

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
        // 字修自带字典开着时，这里让位（在字修里关掉字典即可用本模组）
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
        if (tabIndex >= COLORS.size()) {
            tabIndex = 0;
        }
        wantOpen = true;
        updateLayout();
        currentGroup = GROUPS.get(COLORS.get(tabIndex));
        sortGroup(currentGroup, sortKind, lastAscending);
        calculateScrollBounds();

        grabbedScreen = false;
        hoveredCard = null;
        clickStartedCard = null;
        justSorted = false;
        currentDiffY = scrollLowerBound;
        grabStartY = currentDiffY;

        buildWidgets();
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

    // ---- 数据：所有颜色 + 每色一组卡 ----

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

        // 原版六色按原版顺序排前面，自定义颜色按枚举名字母序
        List<AbstractCard.CardColor> vanilla = new ArrayList<AbstractCard.CardColor>();
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.RED);
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.GREEN);
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.BLUE);
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.PURPLE);
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.COLORLESS);
        addIfPresent(vanilla, byColor, AbstractCard.CardColor.CURSE);

        List<AbstractCard.CardColor> others = new ArrayList<AbstractCard.CardColor>();
        for (AbstractCard.CardColor color : byColor.keySet()) {
            if (!isVanillaColor(color)) {
                others.add(color);
            }
        }
        others.sort((a, b) -> a.name().compareTo(b.name()));

        COLORS.clear();
        COLORS.addAll(vanilla);
        COLORS.addAll(others);

        for (AbstractCard.CardColor color : COLORS) {
            CardGroup group = new CardGroup(CardGroup.CardGroupType.UNSPECIFIED);
            group.group.addAll(byColor.get(color));
            sortGroup(group, SORT_TYPE, true);
            GROUPS.put(color, group);
            BAR_COLORS.put(color, barColorFor(color));
            TAB_HBS.add(new Hitbox(1.0F, 1.0F));
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

    private static Color barColorFor(AbstractCard.CardColor color) {
        if (color == AbstractCard.CardColor.RED) {
            return new Color(0.5F, 0.1F, 0.1F, 1.0F);
        }
        if (color == AbstractCard.CardColor.GREEN) {
            return new Color(0.25F, 0.55F, 0.0F, 1.0F);
        }
        if (color == AbstractCard.CardColor.BLUE) {
            return new Color(0.01F, 0.34F, 0.52F, 1.0F);
        }
        if (color == AbstractCard.CardColor.PURPLE) {
            return new Color(0.37F, 0.22F, 0.49F, 1.0F);
        }
        if (color == AbstractCard.CardColor.COLORLESS) {
            return new Color(0.4F, 0.4F, 0.4F, 1.0F);
        }
        if (color == AbstractCard.CardColor.CURSE) {
            return new Color(0.18F, 0.18F, 0.16F, 1.0F);
        }
        return new Color(0.4F, 0.4F, 0.4F, 1.0F);
    }

    private static String tabLabel(AbstractCard.CardColor color) {
        if (color == AbstractCard.CardColor.RED) {
            return CardLibraryScreen.TEXT[1];
        }
        if (color == AbstractCard.CardColor.GREEN) {
            return CardLibraryScreen.TEXT[2];
        }
        if (color == AbstractCard.CardColor.BLUE) {
            return CardLibraryScreen.TEXT[3];
        }
        if (color == AbstractCard.CardColor.PURPLE) {
            return CardLibraryScreen.TEXT[8];
        }
        if (color == AbstractCard.CardColor.COLORLESS) {
            return CardLibraryScreen.TEXT[4];
        }
        if (color == AbstractCard.CardColor.CURSE) {
            return CardLibraryScreen.TEXT[5];
        }
        return prettyName(color.name());
    }

    private static String prettyName(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        String name = sb.toString();
        return name.length() > 12 ? name.substring(0, 11) + "…" : name;
    }

    private static void switchTab(int index) {
        if (index == tabIndex || index < 0 || index >= COLORS.size()) {
            return;
        }
        tabIndex = index;
        currentGroup = GROUPS.get(COLORS.get(tabIndex));
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

    // ---- 顶栏：颜色 tab / 排序按钮 / 看升级版 ----

    private static float headerY() {
        List<AbstractCard> cards = currentGroup == null ? null : currentGroup.group;
        float topY = (cards == null || cards.isEmpty())
                ? drawStartY + currentDiffY : cards.get(0).current_y;
        return topY + HEADER_LIFT * Settings.yScale;
    }

    private static void updateTabs() {
        if (TAB_HBS.isEmpty()) {
            return;
        }
        int count = TAB_HBS.size();
        int columns = Math.max(1, (count + COLOR_ROWS - 1) / COLOR_ROWS);
        columns = Math.min(columns, 2);   // 最多两列，多了就把行距压紧（第三列会压到卡墙）
        int rows = (count + columns - 1) / columns;
        float rowH = Math.min(COLOR_ROW_H, 520.0F / Math.max(1, rows)) * Settings.scale;
        float topY = Settings.HEIGHT - 170.0F * Settings.scale;
        for (int i = 0; i < count; i++) {
            int col = i / rows;
            int row = i % rows;
            Hitbox hb = TAB_HBS.get(i);
            hb.width = COLOR_COL_W * Settings.scale;
            hb.height = rowH;
            hb.move(110.0F * Settings.scale + col * COLOR_COL_W * Settings.scale,
                    topY - row * rowH);
            hb.update();
        }
        if (InputHelper.justClickedLeft) {
            for (int i = 0; i < count; i++) {
                if (TAB_HBS.get(i).hovered) {
                    switchTab(i);
                    return;
                }
            }
        }
    }

    private static void updateHeader() {
        updateTabs();
        float y = headerY();
        for (SortHeaderButton button : sortButtons) {
            button.updateScrollPosition(y);
            button.update();
        }

        UPGRADE_HB.width = UPGRADE_HB_W * Settings.xScale;
        UPGRADE_HB.height = UPGRADE_HB_H * Settings.scale;
        UPGRADE_HB.move(UPGRADE_HB_X * Settings.xScale, y);
        UPGRADE_HB.update();
        if (UPGRADE_HB.hovered && InputHelper.justClickedLeft) {
            UPGRADE_HB.clickStarted = true;
        }
        if (UPGRADE_HB.clicked) {
            UPGRADE_HB.clicked = false;
            SingleCardViewPopup.isViewingUpgrade = !SingleCardViewPopup.isViewingUpgrade;
            CardCrawlGame.sound.playA("UI_CLICK_1", -0.2F);
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
        renderTabs(sb);
        renderHeader(sb);

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

    private static void renderTabs(SpriteBatch sb) {
        for (int i = 0; i < TAB_HBS.size(); i++) {
            Hitbox hb = TAB_HBS.get(i);
            boolean active = i == tabIndex;
            Color color = active || hb.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
            FontHelper.renderFontCentered(sb, FontHelper.topPanelInfoFont,
                    tabLabel(COLORS.get(i)), hb.cX, hb.cY, color);
            if (active) {
                sb.setColor(Settings.GOLD_COLOR);
                sb.draw(ImageMaster.WHITE_SQUARE_IMG,
                        hb.x - 6.0F * Settings.scale, hb.cY - 12.0F * Settings.scale,
                        3.0F * Settings.scale, 24.0F * Settings.scale);
                sb.setColor(Color.WHITE);
            }
        }
    }

    private static void renderHeader(SpriteBatch sb) {
        float y = headerY();
        for (SortHeaderButton button : sortButtons) {
            button.updateScrollPosition(y);
        }

        sb.setColor(BAR_COLORS.get(COLORS.get(tabIndex)));
        sb.draw(ImageMaster.COLOR_TAB_BAR,
                Settings.WIDTH / 2.0F - BAR_W * Settings.scale / 2.0F,
                y - BAR_H * Settings.scale / 2.0F,
                BAR_W * Settings.scale, BAR_H * Settings.scale);
        sb.setColor(Color.WHITE);

        for (SortHeaderButton button : sortButtons) {
            button.render(sb);
        }
        renderViewUpgrade(sb, y);
    }

    private static void renderViewUpgrade(SpriteBatch sb, float y) {
        String label = CardLibraryScreen.TEXT[7];
        Color color = UPGRADE_HB.hovered ? Settings.GOLD_COLOR : Settings.CREAM_COLOR;
        FontHelper.renderFontRightAligned(sb, FontHelper.topPanelInfoFont, label,
                UPGRADE_LABEL_X * Settings.xScale, y, color);

        Texture box = SingleCardViewPopup.isViewingUpgrade
                ? ImageMaster.COLOR_TAB_BOX_TICKED
                : ImageMaster.COLOR_TAB_BOX_UNTICKED;
        float textWidth = FontHelper.getSmartWidth(
                FontHelper.topPanelInfoFont, label, 9999.0F, 0.0F);
        sb.setColor(color);
        sb.draw(box,
                UPGRADE_BOX_X * Settings.xScale - textWidth - 24.0F * Settings.scale,
                y - 24.0F * Settings.scale,
                48.0F * Settings.scale, 48.0F * Settings.scale);
        sb.setColor(Color.WHITE);
    }

    // ---- 布局 ----

    private static void updateLayout() {
        drawStartY = Settings.HEIGHT * 0.66F;
        float w = AbstractCard.IMG_WIDTH * CARD_SCALE;
        padX = w + Settings.CARD_VIEW_PAD_X;
        padY = AbstractCard.IMG_HEIGHT * CARD_SCALE + Settings.CARD_VIEW_PAD_Y;
        drawStartX = (Settings.WIDTH - COLS * w - (COLS - 1) * Settings.CARD_VIEW_PAD_X) / 2.0F
                + w / 2.0F;
        scrollLowerBound = -Settings.DEFAULT_SCROLL_LIMIT;
    }

    private static void updateIconHb() {
        float slot = (ICON_SIZE + 10.0F) * Settings.scale;
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
