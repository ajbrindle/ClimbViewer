package com.sk7software.climbviewer;

import com.sk7software.climbviewer.model.RoutePoint;

public interface ActivityUpdateInterface {
    void locationChanged(RoutePoint point);
    void setProgress(boolean showProgressDialog, String progressMessage);
}
