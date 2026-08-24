package com.intellimove.common.exception;

public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String currentState, String attemptedAction) {
        super("INVALID_STATE_TRANSITION",
              String.format("Cannot '%s' when in state '%s'", attemptedAction, currentState));
    }

    public InvalidStateTransitionException(String entity, String currentState, String attemptedAction) {
        super("INVALID_STATE_TRANSITION",
              String.format("Cannot '%s' %s when in state '%s'", attemptedAction, entity, currentState));
    }
}
