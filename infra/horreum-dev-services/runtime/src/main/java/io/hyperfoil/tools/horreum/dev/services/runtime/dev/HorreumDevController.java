package io.hyperfoil.tools.horreum.dev.services.runtime.dev;

public class HorreumDevController {

    private static final HorreumDevController INSTANCE = new HorreumDevController();

    public static HorreumDevController get() {
        return INSTANCE;
    }

    private HorreumDevInfo info = new HorreumDevInfo();

    private HorreumDevController() {
    }

    public HorreumDevInfo getInfo() {
        return info;
    }
}
