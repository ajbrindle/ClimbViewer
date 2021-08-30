package com.sk7software.climbviewer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClimbAttempt {
    private LocalDateTime datetime;
    private int duration;
    private boolean inProgress;
    List<AttemptPoint> points;

    public void addPoint(RoutePoint point, LocalDateTime timestamp) {
        if (points == null) {
            points = new ArrayList<>();
        }

        AttemptPoint attemptPoint = new AttemptPoint();
        attemptPoint.setTimestamp(timestamp);
        attemptPoint.setPoint(point);
        points.add(attemptPoint);
    }
}
