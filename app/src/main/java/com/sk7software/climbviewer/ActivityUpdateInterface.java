package com.sk7software.climbviewer;

import com.sk7software.climbviewer.model.RoutePoint;

public interface ActivityUpdateInterface {
    default void locationChanged(RoutePoint point) {}
    default void setProgress(boolean showProgressDialog, String progressMessage) {}
    default void clearCompletionPanel() {}
    default void updateProgressMessage(String message) {}
}
