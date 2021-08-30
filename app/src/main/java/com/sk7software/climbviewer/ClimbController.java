package com.sk7software.climbviewer;

public class ClimbController {
    private static ClimbController INSTANCE = null;

    private ClimbController() {
        super();
    }

    public ClimbController getInstance() {
        if (INSTANCE == null) {
            ClimbController INSTANCE = new ClimbController();
        }
        return INSTANCE;
    }
}
