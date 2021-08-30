package com.sk7software.climbviewer.model;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Root
@Getter
@Setter
public class TrackSegment {
    @ElementList(entry="trkpt", inline = true)
    private List<RoutePoint> points;
}
