package com.sk7software.climbviewer.model;

import java.time.LocalDateTime;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttemptPoint {
    private RoutePoint point;
    private LocalDateTime timestamp;
    private long secondsFromStart;
}
