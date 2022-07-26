package com.sk7software.climbviewer.model;

import androidx.annotation.NonNull;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Root(strict=false)
@Getter
@Setter
public class GPXRoute {
    @Attribute(required = false)
    private int id;

    @Element
    private String name;

    private String time;

    @ElementList(entry="rtept", inline = true)
    private List<RoutePoint> points;

    public void addPoint(RoutePoint point) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(point);
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
