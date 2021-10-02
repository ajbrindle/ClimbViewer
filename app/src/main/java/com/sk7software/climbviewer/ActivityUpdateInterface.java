package com.sk7software.climbviewer;

import com.sk7software.climbviewer.model.RoutePoint;

public interface ActivityUpdateInterface {
    public void locationChanged(RoutePoint point);
    public void setProgress(boolean showProgressDialog, String progressMessage);
}
