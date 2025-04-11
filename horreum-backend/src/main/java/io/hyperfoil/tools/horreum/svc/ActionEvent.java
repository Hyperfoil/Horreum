package io.hyperfoil.tools.horreum.svc;

public enum ActionEvent {
    TEST_NEW("test/new"),
    RUN_NEW("run/new"),
    CHANGE_NEW("change/new"),
    EXPERIMENT_RESULT_NEW("experiment_result/new"),;

    // this represents the value stored in the database
    private final String value;

    ActionEvent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }

    public static ActionEvent fromValue(String value) {
        for (ActionEvent event : ActionEvent.values()) {
            if (event.getValue().equals(value)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
