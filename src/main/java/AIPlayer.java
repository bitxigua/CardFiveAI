import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 简单的AI玩家，遵循胡牌优先并尽量留下安全牌。
 */
public class AIPlayer extends Player {
    private static final int SIMULATIONS_PER_CARD = 200;
    private static final int MAX_TURNS_PER_SIM = 30;

    public AIPlayer(String name, GameEngine engine) {
        super(name, engine);
    }

    @Override
    public TurnAction playTurn() {
        if (engine.checkHu(hand)) {
            int fan = engine.calculateFan(hand);
            return TurnAction.hu(fan);
        }
        int discardIndex = selectDiscardIndex();
        Card discarded = hand.remove(discardIndex);
        return TurnAction.discard(discarded);
    }

    private int selectDiscardIndex() {
        List<Card> remainingTiles = buildRemainingTilePool();
        int bestIndex = 0;
        double bestExpectation = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < hand.size(); i++) {
            double expectation = simulateDiscard(remainingTiles, i);
            if (expectation > bestExpectation) {
                bestExpectation = expectation;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private double simulateDiscard(List<Card> basePool, int discardIndex) {
        List<Card> candidateHand = new ArrayList<>(hand);
        candidateHand.remove(discardIndex);
        double totalScore = 0;
        for (int i = 0; i < SIMULATIONS_PER_CARD; i++) {
            totalScore += runSimulation(candidateHand, basePool);
        }
        return totalScore / SIMULATIONS_PER_CARD;
    }

    private double runSimulation(List<Card> candidateHand, List<Card> basePool) {
        if (basePool.isEmpty()) {
            return 0;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Card> deck = new ArrayList<>(basePool);
        List<Card> currentHand = new ArrayList<>(candidateHand);
        int turns = Math.min(MAX_TURNS_PER_SIM, deck.size());
        for (int i = 0; i < turns; i++) {
            if (deck.isEmpty()) {
                break;
            }
            Card drawn = deck.remove(random.nextInt(deck.size()));
            currentHand.add(drawn);
            if (engine.checkHu(currentHand)) {
                return engine.calculateFan(currentHand);
            }
            int discardIdx = random.nextInt(currentHand.size());
            currentHand.remove(discardIdx);
        }
        return 0;
    }

    private List<Card> buildRemainingTilePool() {
        List<Card> pool = new ArrayList<>();
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 1; rank <= 9; rank++) {
                for (int copy = 0; copy < 4; copy++) {
                    pool.add(new Card(suit, rank));
                }
            }
        }
        for (Card.Honor honor : Card.Honor.values()) {
            for (int copy = 0; copy < 4; copy++) {
                pool.add(new Card(honor));
            }
        }
        for (Card card : hand) {
            removeFirstMatch(pool, card);
        }
        return pool;
    }

    private void removeFirstMatch(List<Card> pool, Card target) {
        for (int i = 0; i < pool.size(); i++) {
            if (isSameTile(pool.get(i), target)) {
                pool.remove(i);
                return;
            }
        }
        throw new IllegalStateException("牌堆中没有足够的牌可供模拟");
    }

    private boolean isSameTile(Card first, Card second) {
        if (first.isHonor() || second.isHonor()) {
            return first.isHonor() && second.isHonor() && first.getHonor() == second.getHonor();
        }
        return first.getSuit() == second.getSuit()
                && first.getRank() != null
                && first.getRank().equals(second.getRank());
    }
}
