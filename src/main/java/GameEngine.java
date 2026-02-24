import java.util.List;

/**
 * 负责完成胡牌判断和番型计算的简易引擎。
 */
public class GameEngine {
    private static final int TILE_COUNT = 21; // 条、筒各9张以及3张字牌
    private static final int HONOR_OFFSET = 18;
    private static final int INVALID_SCORE = -1;

    /**
     * 判断手牌是否满足四副面子加一对将的胡牌形状。
     */
    public boolean checkHu(List<Card> hand) {
        if (!isHandSizeValid(hand)) {
            return false;
        }
        int[] counts = buildCounts(hand);
        for (int i = 0; i < TILE_COUNT; i++) {
            if (counts[i] >= 2) {
                counts[i] -= 2;
                if (canFormMelds(counts)) {
                    counts[i] += 2;
                    return true;
                }
                counts[i] += 2;
            }
        }
        return false;
    }

    /**
     * 根据顺子、刻子、对子数量给出一个简单番值，若未胡牌则返回0。
     */
    public int calculateFan(List<Card> hand) {
        if (!checkHu(hand)) {
            return 0;
        }
        int[] counts = buildCounts(hand);
        int maxFan = 0;
        for (int i = 0; i < TILE_COUNT; i++) {
            if (counts[i] >= 2) {
                counts[i] -= 2;
                int bodyFan = searchFan(counts);
                if (bodyFan != INVALID_SCORE) {
                    maxFan = Math.max(maxFan, fanForPair(i) + bodyFan);
                }
                counts[i] += 2;
            }
        }
        return maxFan;
    }

    private boolean isHandSizeValid(List<Card> hand) {
        return hand != null && !hand.isEmpty() && hand.size() % 3 == 2;
    }

    private int[] buildCounts(List<Card> hand) {
        int[] counts = new int[TILE_COUNT];
        for (Card card : hand) {
            counts[cardToIndex(card)]++;
        }
        return counts;
    }

    private int cardToIndex(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("牌不能为空");
        }
        if (card.isHonor()) {
            switch (card.getHonor()) {
                case HONG_ZHONG:
                    return HONOR_OFFSET;
                case FA_CAI:
                    return HONOR_OFFSET + 1;
                case BAI_BAN:
                    return HONOR_OFFSET + 2;
                default:
                    throw new IllegalArgumentException("不支持的字牌");
            }
        }
        Card.Suit suit = card.getSuit();
        if (suit == null || card.getRank() == null) {
            throw new IllegalArgumentException("花色牌需要点数");
        }
        int offset = suit == Card.Suit.TIAO ? 0 : 9;
        return offset + card.getRank() - 1;
    }

    private boolean canFormMelds(int[] counts) {
        int index = firstNonZero(counts);
        if (index == -1) {
            return true;
        }
        if (counts[index] >= 3) {
            counts[index] -= 3;
            if (canFormMelds(counts)) {
                counts[index] += 3;
                return true;
            }
            counts[index] += 3;
        }
        if (isSuitIndex(index) && canSequenceStart(index) && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--;
            counts[index + 1]--;
            counts[index + 2]--;
            if (canFormMelds(counts)) {
                counts[index]++;
                counts[index + 1]++;
                counts[index + 2]++;
                return true;
            }
            counts[index]++;
            counts[index + 1]++;
            counts[index + 2]++;
        }
        return false;
    }

    private int searchFan(int[] counts) {
        int index = firstNonZero(counts);
        if (index == -1) {
            return 0;
        }
        int best = INVALID_SCORE;
        if (counts[index] >= 3) {
            counts[index] -= 3;
            int score = searchFan(counts);
            if (score != INVALID_SCORE) {
                best = Math.max(best, fanForPung(index) + score);
            }
            counts[index] += 3;
        }
        if (isSuitIndex(index) && canSequenceStart(index) && counts[index + 1] > 0 && counts[index + 2] > 0) {
            counts[index]--;
            counts[index + 1]--;
            counts[index + 2]--;
            int score = searchFan(counts);
            if (score != INVALID_SCORE) {
                best = Math.max(best, fanForSequence() + score);
            }
            counts[index]++;
            counts[index + 1]++;
            counts[index + 2]++;
        }
        return best;
    }

    private int firstNonZero(int[] counts) {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSuitIndex(int index) {
        return index < HONOR_OFFSET;
    }

    private boolean canSequenceStart(int index) {
        return isSuitIndex(index) && index % 9 <= 6;
    }

    private boolean isHonorIndex(int index) {
        return index >= HONOR_OFFSET;
    }

    private int fanForPung(int index) {
        return isHonorIndex(index) ? 3 : 2;
    }

    private int fanForSequence() {
        return 1;
    }

    private int fanForPair(int index) {
        return isHonorIndex(index) ? 2 : 1;
    }
}
