import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基础玩家，负责维护手牌并定义回合行为。
 */
public abstract class Player {
    protected final String name;
    protected final GameEngine engine;
    protected final List<Card> hand = new ArrayList<>();

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
    }

    /**
     * 执行一次出牌或胡牌决策。
     */
    public abstract TurnAction playTurn();

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
