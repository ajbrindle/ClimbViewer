package com.sk7software.climbviewer.model;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.view.PlotPoint;

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
    private long rating;
    private int startIdx;

    @ElementList(entry="rtept", inline = true)
    private List<RoutePoint> points;

    private List<RoutePoint> smoothedPoints;

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


    // Difficulty rating based on climbbybike formula (multipled by 100 to avoid messing with fractions)
    // https://www.climbbybike.com/climb_difficulty.asp
    public long calcRating() {
        // Work out distances and elevations if it hasn't already been done
        if (getPoints().get(getPoints().size()-1).getDistFromStart() < 0.1) {
            setPointsDist();
        }

        double maxElevation = this.getMaxElevation();
        double elevationChange = this.getElevationChange();
        double dist = this.getPoints().get(this.getPoints().size()-1).getDistFromStart();

        if (dist != 0 && elevationChange > 0) {
            rating = (long)((2 * (elevationChange * 100.0 / dist) + (elevationChange * elevationChange / dist) +
                    (dist / 1000) + (maxElevation > 1000 ? (maxElevation - 1000)/100 : 0)) * 100);
        } else {
            // Don't rate descents
            rating = 0;
        }
        return rating;
    }

    private double getMaxElevation() {
        double maxElevation = Double.MIN_VALUE;

        for (RoutePoint p : this.getPoints()) {
            if (p.getElevation() > maxElevation) {
                maxElevation = p.getElevation();
            }
        }
        return maxElevation;
    }

    public double getElevationChange() {
        double maxElevation = Double.MIN_VALUE;
        double minElevation = Double.MAX_VALUE;
        int minIdx = 0;
        int maxIdx = 0;
        int pointIdx = 0;

        for (RoutePoint p : this.getPoints()) {
            if (p.getElevation() < minElevation) {
                minElevation = p.getElevation();
                minIdx = pointIdx;
            }
            if (p.getElevation() > maxElevation) {
                maxElevation = p.getElevation();
                maxIdx = pointIdx;
            }
            pointIdx++;
        }

        // If the minimum elevation is after the maximum one then this is a descent
        // so the elevation difference is negative
        double multiplier = (minIdx < maxIdx ? 1.0 : -1.0);
        return multiplier * (maxElevation - minElevation);
    }
    public void calcSmoothedPoints() {
        float distFromLast = 0;
        boolean first = true;
        int lastIndex = 0;
        smoothedPoints = new ArrayList<>();

        int smthDist = getSmoothDist();
        if (smthDist == 0) {
            smthDist = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 20);
        }

        // Determine cumulative delta to all points
        for (int i = 0; i < getPoints().size(); i++) {
            RoutePoint pt = getPoints().get(i);

            if (first) {
                first = false;
                pt.setSmoothedElevation(pt.getElevation());
                smoothedPoints.add(pt);
            } else {
                distFromLast += (float)calcDelta(pt, getPoints().get(i-1).getEasting(), getPoints().get(i-1).getNorthing());

                // Not reached smoothed distance, so move on to next point
                if (distFromLast < smthDist && i != getPoints().size() - 1) continue;

                // Work out elevation difference with last smooted point
                double elevDiff = pt.getElevation() - getPoints().get(lastIndex).getElevation();
                smoothedPoints.add(pt);

                // Interpolate along the length and set the interim elevations
                for (int j=lastIndex+1; j<=i; j++) {
                    getPoints().get(j).setSmoothedElevation(getPoints().get(lastIndex).getElevation() +
                            (elevDiff * (float)calcDelta(getPoints().get(j), getPoints().get(lastIndex).getEasting(), getPoints().get(lastIndex).getNorthing()) / distFromLast));
                }
            }

            lastIndex = i;
            distFromLast = 0;
        }
    }

    public void setPointsDist() {
        float dist = 0;
        float elev = 0;

        getPoints().get(0).setDistFromStart(0);
        getPoints().get(0).setElevFromStart(0);

        for (int i=1; i<getPoints().size(); i++) {
            dist += Math.sqrt(Math.pow(getPoints().get(i).getEasting() - getPoints().get(i - 1).getEasting(), 2.0) +
                    Math.pow(getPoints().get(i).getNorthing() - getPoints().get(i - 1).getNorthing(), 2.0));
            getPoints().get(i).setDistFromStart(dist);

            if (getPoints().get(i).getElevation() > getPoints().get(i-1).getElevation()) {
                elev += getPoints().get(i).getElevation() - getPoints().get(i-1).getElevation();
            }
            getPoints().get(i).setElevFromStart(elev);
        }
    }
    private double calcDelta(RoutePoint pt, double e, double n) {
        return Math.sqrt(Math.pow(e - pt.getEasting(), 2.0) + Math.pow(n - pt.getNorthing(), 2.0));
    }
}
