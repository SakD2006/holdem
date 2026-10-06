/**
 * Computer players. A bot is given exactly what a person in its seat would be shown, the messages
 * the server sends that seat, and nothing else: never another player's hole cards, never the deck.
 * {@link com.saksham.poker.ai.TableObserver} turns those messages into an
 * {@link com.saksham.poker.ai.Observation}, and a {@link com.saksham.poker.ai.BotStrategy} turns the
 * observation into a {@link com.saksham.poker.ai.Decision}. Nothing here does IO or starts a thread.
 */
package com.saksham.poker.ai;
