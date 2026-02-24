import java.util.List;

/**
 * 负责完成胡牌判断和番型计算的简易引擎。
 */
public class GameEngine {
    private static final int TILE_COUNT = 21; // 条、筒各9张以及3张字牌
    private static final int HONOR_OFFSET = 18;
    private static final int INVALID_SCORE = -1;
    private static final int SEVEN_PAIRS_FAN = 6;
    private static final int LONG_SEVEN_PAIRS_FAN = 8;

    /**
     * 判断手牌是否满足胡牌形状（四副面子一对将或七对）。
     */
    public boolean checkHu(List<Card> hand) {
        if (!isHandSizeValid(hand)) {
            return false;
        }
        return checkStandardPattern(hand) || isSevenPairs(hand);
    }

    /**
     * 判断是否为四副面子加一对将的标准胡牌。
     */
    public boolean checkStandardPattern(List<Card> hand) {
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
     * 根据牌型给出一个简易番值。
     */
    public int calculateFan(List<Card> hand) {
        if (!isHandSizeValid(hand)) {
            return 0;
        }
        if (isSevenPairs(hand)) {
            return isLongSevenPairs(hand) ? LONG_SEVEN_PAIRS_FAN : SEVEN_PAIRS_FAN;
        }
        if (!checkStandardPattern(hand)) {
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

    public boolean isSevenPairs(List<Card> hand) {
        if (hand == null || hand.size() != 14) {
            return false;
        }
        int[] counts = buildCounts(hand);
        int pairs = 0;
        for (int count : counts) {
            if (count == 0) {
                continue;
            }
            if (count % 2 != 0) {
                return false;
            }
            pairs += count / 2;
        }
        return pairs == 7;
    }

    public boolean isLongSevenPairs(List<Card> hand) {
        if (!isSevenPairs(hand)) {
            return false;
        }
        int[] counts = buildCounts(hand);
        for (int count : counts) {
            if (count >= 4) {
                return true;
            }
        }
        return false;
    }

    public int[] countTiles(List<Card> hand) {
        return buildCounts(hand);
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
