package com.saksham.poker.client.state;

import com.saksham.poker.common.card.Card;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Everything the screen shows for one seat. The views bind to these properties, so a seat redraws
 * itself whenever {@link RoomState} changes one.
 */
public final class SeatViewModel {

    private final int seat;

    // ---- who sits here
    private final BooleanProperty occupied = new SimpleBooleanProperty();
    private final LongProperty userId = new SimpleLongProperty(-1);
    private final StringProperty username = new SimpleStringProperty("");
    private final LongProperty stack = new SimpleLongProperty();
    private final BooleanProperty sittingOut = new SimpleBooleanProperty();
    private final BooleanProperty connected = new SimpleBooleanProperty(true);

    // ---- their part in the current hand
    private final BooleanProperty inHand = new SimpleBooleanProperty();
    private final LongProperty streetBet = new SimpleLongProperty();
    private final BooleanProperty folded = new SimpleBooleanProperty();
    private final BooleanProperty allIn = new SimpleBooleanProperty();
    private final BooleanProperty button = new SimpleBooleanProperty();
    private final BooleanProperty turn = new SimpleBooleanProperty();
    /** The cards to show face up: your own, or anyone's once shown at showdown. Empty otherwise. */
    private final ObservableList<Card> cards = FXCollections.observableArrayList();
    /** What the player last did on this street, such as "Raise 300"; empty if nothing yet. */
    private final StringProperty lastAction = new SimpleStringProperty("");
    /** Chips won in the hand that just ended; 0 if none. */
    private final LongProperty won = new SimpleLongProperty();
    /** The kind of hand shown at showdown, such as "Two pair"; empty if not shown. */
    private final StringProperty shownHand = new SimpleStringProperty("");
    /** This seat's place in the order hands are turned over at showdown, from 0; -1 if not shown. */
    private final IntegerProperty revealOrder = new SimpleIntegerProperty(-1);
    private final IntegerProperty dealPosition = new SimpleIntegerProperty();

    SeatViewModel(int seat) {
        this.seat = seat;
    }

    /** The seat number as the server counts it, from 0. */
    public int seat() {
        return seat;
    }

    public BooleanProperty occupiedProperty() {
        return occupied;
    }

    public LongProperty userIdProperty() {
        return userId;
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public LongProperty stackProperty() {
        return stack;
    }

    public BooleanProperty sittingOutProperty() {
        return sittingOut;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public BooleanProperty inHandProperty() {
        return inHand;
    }

    public LongProperty streetBetProperty() {
        return streetBet;
    }

    public BooleanProperty foldedProperty() {
        return folded;
    }

    public BooleanProperty allInProperty() {
        return allIn;
    }

    public BooleanProperty buttonProperty() {
        return button;
    }

    public BooleanProperty turnProperty() {
        return turn;
    }

    public ObservableList<Card> cards() {
        return cards;
    }

    public StringProperty lastActionProperty() {
        return lastAction;
    }

    public LongProperty wonProperty() {
        return won;
    }

    public StringProperty shownHandProperty() {
        return shownHand;
    }

    public IntegerProperty revealOrderProperty() {
        return revealOrder;
    }

    /** This seat's turn in the deal, counted from the player on the button's left; 0 is first. */
    public IntegerProperty dealPositionProperty() {
        return dealPosition;
    }

    /** Empties the seat. */
    void clear() {
        occupied.set(false);
        userId.set(-1);
        username.set("");
        stack.set(0);
        sittingOut.set(false);
        connected.set(true);
        clearHand();
    }

    /** Forgets the hand, keeping who sits here. */
    void clearHand() {
        inHand.set(false);
        streetBet.set(0);
        folded.set(false);
        allIn.set(false);
        button.set(false);
        turn.set(false);
        cards.clear();
        lastAction.set("");
        won.set(0);
        shownHand.set("");
        revealOrder.set(-1);
    }
}
