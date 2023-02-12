package com.sk7software.climbviewer.model;

import android.util.Log;

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

    private int projectionId;
    private int zone;
    private String time;
    private int smoothDist;

    @ElementList(entry="rtept", inline = true)
    private List<RoutePoint> points;

    public void addPoint(RoutePoint point) {
        if (points == null) {
            points = new ArrayList<>();
        }
        points.add(point);
    }

    public void adjustRoute(int startIdx) {
        if (startIdx == 0) {
            return;
        }

        List<RoutePoint> adjustedPoints = new ArrayList<>();
        for (int i=startIdx; i<this.getPoints().size(); i++) {
            adjustedPoints.add(this.getPoints().get(i));
        }

        // Add other points onto the end if it is a circular route
        if (isCircular()) {
            for (int i=0; i<startIdx; i++) {
                adjustedPoints.add(this.getPoints().get(i));
            }
        }
        this.setPoints(adjustedPoints);
    }

    private boolean isCircular() {
        int lastIdx = this.getPoints().size() - 1;
        RoutePoint start = this.getPoints().get(0);
        RoutePoint end = this.getPoints().get(lastIdx);

        // Circular if start and end are within 50m of each other
        Log.d("GPXRoute", "CIRCULAR DIST: " + Math.sqrt(Math.pow(end.getEasting() - start.getEasting(),2) + Math.pow(end.getNorthing() - start.getNorthing(),2)));
        return (Math.sqrt(Math.pow(end.getEasting() - start.getEasting(),2) + Math.pow(end.getNorthing() - start.getNorthing(),2)) < 50);
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof GPXRoute)) {
            return false;
        }

        GPXRoute that = (GPXRoute)o;
        return (this.getId() == that.getId() && this.getName().equals(that.getName()));
    }

    @Override
    public int hashCode() {
        return id * name.hashCode();
    }
}
