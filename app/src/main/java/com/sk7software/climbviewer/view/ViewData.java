package com.sk7software.climbviewer.view;

import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.model.RoutePoint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ViewData {
    private ClimbController.PointType type;
    private RoutePoint actual;
    private PlotPoint plot;

    public ViewData(ClimbController.PointType type) {
        this.type = type;
    }
}
