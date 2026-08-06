package com.aiassistant.agent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract ReAct (Reasoning and Acting) agent base class.
 * Implements the Think → Act loop pattern for step-by-step autonomous reasoning.
 * <p>
 * Subclasses implement {@link #think()} to decide whether to act,
 * and {@link #act()} to execute the chosen action.
 */
@Data
@Slf4j
public abstract class ReActAgent {

    private String name;
    private String systemPrompt;
    private int currentStep = 0;
    private int maxSteps = 10;

    /**
     * Analyse current state and decide whether an action is needed.
     *
     * @return true if an action should be taken, false if done
     */
    public abstract boolean think();

    /**
     * Execute the decided action.
     *
     * @return the result of the action
     */
    public abstract String act();

    /**
     * Execute a single Think → Act step.
     *
     * @return step result description
     */
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                return "[think] no action needed — task complete";
            }
            String actionResult = act();
            return "[act] " + actionResult;
        } catch (Exception e) {
            log.error("{}: step execution failed: {}", name, e.getMessage());
            return "[error] " + e.getMessage();
        }
    }

    /**
     * Run the full agent loop up to maxSteps.
     *
     * @return concatenated results of all steps
     */
    public String run() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxSteps; i++) {
            currentStep = i + 1;
            log.info("{}: executing step {}/{}", name, currentStep, maxSteps);
            String stepResult = step();
            result.append("Step ").append(currentStep).append(": ").append(stepResult).append("\n");
            if (stepResult.contains("no action needed")) {
                break;
            }
        }
        if (currentStep >= maxSteps) {
            result.append("[terminated] reached max steps (").append(maxSteps).append(")");
        }
        return result.toString();
    }
}
