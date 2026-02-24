import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 基础玩家，负责维护手牌并定义回合行为。
 */
public abstract class Player {
    protected final String name;
    protected final GameEngine engine;
    protected final List<Card> hand = new ArrayList<>();
    protected final List<Meld> melds = new ArrayList<>();

    protected Player(String name, GameEngine engine) {
        this.name = Objects.requireNonNull(name, "玩家姓名不能为空");
        this.engine = Objects.requireNonNull(engine, "GameEngine 不能为空");
    }

    /**
     * 摸入一张牌。
     */
    public void draw(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("不能摸入空牌");
        }
        hand.add(card);
    }

    /**
     * 返回不可修改的手牌视图。
     */
    public List<Card> getHand() {
        return Collections.unmodifiableList(hand);
    }

    public String getName() {
        return name;
    }

    /**
     * 清空手牌，用于新一局开始。
     */
    public void resetHand() {
        hand.clear();
        melds.clear();
    }

    public List<Meld> getMelds() {
        return Collections.unmodifiableList(melds);
    }

    /**
     * 将手牌与已落地的牌组合，用于胡牌计算。
     */
    public List<Card> getEvaluationTiles() {
        List<Card> tiles = new ArrayList<>(hand);
        for (Meld meld : melds) {
            tiles.addAll(meld.getCards());
        }
        return tiles;
    }

    /**
     * 通用出牌操作，供碰/杠后的弃牌复用。
     */
    public Card discardOne() {
        int index = selectDiscardIndex();
        if (index < 0 || index >= hand.size()) {
            throw new IllegalArgumentException("出牌索引越界: " + index);
        }
        return hand.remove(index);
    }

    protected abstract int selectDiscardIndex();

    /**
     * 响应其他玩家的弃牌，决定是否碰或杠。
     */
    public abstract ReactionType chooseReaction(Card tile, boolean canPeng, boolean canGang);

    /**
     * 执行一次出牌或胡牌决策。
     */
    public abstract TurnAction playTurn();

    /**
     * 判断手牌中是否有足够数量可用于碰的牌。
     */
    public boolean canPeng(Card tile) {
        return countMatchingInHand(tile) >= 2;
    }

    /**
     * 判断手牌中是否有足够数量可用于杠的牌。
     */
    public boolean canGang(Card tile) {
        return countMatchingInHand(tile) >= 3;
    }

    public void claimTile(Card tile, ReactionType type) {
        int needed = type == ReactionType.PENG ? 2 : 3;
        List<Card> cards = new ArrayList<>();
        cards.add(tile);
        cards.addAll(removeMatching(tile, needed));
        MeldType meldType = type == ReactionType.PENG ? MeldType.PENG : MeldType.GANG;
        melds.add(new Meld(cards, meldType, false));
    }

    public boolean shouldHuOnDiscard(Card tile, EnumSet<WinCategory> categories, int fan) {
        return false;
    }

    protected int countMatchingInHand(Card tile) {
        int count = 0;
        for (Card card : hand) {
            if (isSameTile(card, tile)) {
                count++;
            }
        }
        return count;
    }

    protected boolean isSameTile(Card first, Card second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.isHonor() || second.isHonor()) {
            return first.isHonor() && second.isHonor() && first.getHonor() == second.getHonor();
        }
        return first.getSuit() == second.getSuit()
                && first.getRank() != null
                && first.getRank().equals(second.getRank());
    }

    private List<Card> removeMatching(Card tile, int count) {
        List<Card> removed = new ArrayList<>();
        for (int i = hand.size() - 1; i >= 0 && removed.size() < count; i--) {
            Card current = hand.get(i);
            if (isSameTile(current, tile)) {
                removed.add(current);
                hand.remove(i);
            }
        }
        if (removed.size() != count) {
            throw new IllegalStateException("手牌中没有足够的牌用于" + (count == 2 ? "碰" : "杠"));
        }
        return removed;
    }

    /**
     * 回合产生的动作，要么胡牌要么打出一张牌。
     */
    public static final class TurnAction {
        private final boolean hu;
        private final Card discarded;
        private final int fan;

        private TurnAction(boolean hu, Card discarded, int fan) {
            this.hu = hu;
            this.discarded = discarded;
            this.fan = fan;
        }

        public static TurnAction hu(int fan) {
            return new TurnAction(true, null, fan);
        }

        public static TurnAction discard(Card card) {
            return new TurnAction(false, card, 0);
        }

        public boolean isHu() {
            return hu;
        }

        public Card getDiscarded() {
            return discarded;
        }

        public int getFan() {
            return fan;
        }
    }
}
