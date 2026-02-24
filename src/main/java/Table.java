import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表示一张卡五星麻将的桌面。
 */
public class Table {
    private final List<Card> deck = new ArrayList<>();
    private final List<Card> tableCards = new ArrayList<>();

    public Table() {
        initializeDeck();
    }

    /**
     * 构建牌堆并为每种牌准备四张。
     */
    private void initializeDeck() {
        deck.clear();
        tableCards.clear();
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 1; rank <= 9; rank++) {
                addSuitCopies(suit, rank);
            }
        }
        for (Card.Honor honor : Card.Honor.values()) {
            addHonorCopies(honor);
        }
    }

    private void addSuitCopies(Card.Suit suit, int rank) {
        for (int i = 0; i < 4; i++) {
            deck.add(new Card(suit, rank));
        }
    }

    private void addHonorCopies(Card.Honor honor) {
        for (int i = 0; i < 4; i++) {
            deck.add(new Card(honor));
        }
    }

    /**
     * 将牌堆随机打乱。
     */
    public void shuffle() {
        Collections.shuffle(deck);
    }

    /**
     * 从牌堆发出一张牌到桌面。
     *
     * @return 发出的牌
     */
    public Card deal() {
        if (deck.isEmpty()) {
            throw new IllegalStateException("No cards left in the deck.");
        }
        Card card = deck.remove(deck.size() - 1);
        tableCards.add(card);
        return card;
    }

    public List<Card> getDeck() {
        return Collections.unmodifiableList(deck);
    }

    public List<Card> getTableCards() {
        return Collections.unmodifiableList(tableCards);
    }
}
