package io.hyperfoil.tools.horreum.changedetection;

public class ChangeDetectionException extends Exception {
    public ChangeDetectionException(String errMsg) {
        super(errMsg);
    }

    public ChangeDetectionException(String errMsg, Exception e) {
        super(errMsg, e);
    }
}
