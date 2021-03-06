package net.varunramesh.hnefatafl.simulator.rulesets;

import net.varunramesh.hnefatafl.simulator.Action;
import net.varunramesh.hnefatafl.simulator.Board;
import net.varunramesh.hnefatafl.simulator.EventHandler;
import net.varunramesh.hnefatafl.simulator.History;

import java.util.List;
import java.util.Set;

/**
 * Created by Varun on 8/17/2015.
 */
public interface Ruleset {
    /** Get the starting configuration of the board */
    Board getStartingConfiguration();

    /** Get the full name of this ruleset. */
    String getRulesetName();

    /** Get the HTML description of the rules. */
    String getRulesHTML();

    /**
     * Step the game forward by one action, returning a new Board without modifying the original one.
     * We need to get the entire history, as some rulesets call for draws after perpetual repetition.
     * @param history The history of the game state, as a list of boards.
     * @param action The action to simulate.
     * @param eventHandler Optional EventHandler to send events to.
     * @return The new board state.
     */
    Board step(History history, Action action, EventHandler eventHandler);

    /**
     * Get a set of all of the valid actions that the current player can take.
     * @param history The history of the game state, as a list of boards.
     * @return The set of legal actions.
     */
    List<Action> getActions(History history);

    /**
     * Get the recommended amount of Plys for the Minimax AI to search on this game type.
     * @return The integer number of Plys that the AI should search for.
     */
    int getAISearchDepth();
}
