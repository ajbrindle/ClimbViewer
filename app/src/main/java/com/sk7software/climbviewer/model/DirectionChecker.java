package com.sk7software.climbviewer.model;

import android.graphics.PointF;
import android.util.Log;

import com.sk7software.climbviewer.LocationMonitor;
import com.sk7software.climbviewer.view.AttemptData;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DirectionChecker {
    private static final int SEARCH_TO_DEFAULT = 20;
    private boolean directionOK;
    private float segmentDist;
    private int startIndex;
    private int searchTo;

    private static final String TAG = DirectionChecker.class.getSimpleName();

    public DirectionChecker() {
        this.searchTo = SEARCH_TO_DEFAULT;
    }
    public DirectionChecker(PointF first, PointF second) {
        // Get distance between points and adjust look-ahead number if necessary
        this.searchTo = (int)Math.sqrt((Math.pow(first.x - second.x, 2.0) + Math.pow(first.y - second.y, 2.0)));
        if (this.searchTo < SEARCH_TO_DEFAULT) {
            this.searchTo = SEARCH_TO_DEFAULT;
        }
        Log.d(TAG, "Search to: " + searchTo);
    }

    public DirectionChecker(int startIndex, PointF first, PointF second, PointF start) {
        this.searchTo = SEARCH_TO_DEFAULT;
        this.startIndex = startIndex;
        this.calcSegmentDist(start, first, second);
    }
    public boolean check(PointF checkPoint, List<RoutePoint> track, int multiplier) {
        int searchToIndex = limitIndex(track);
        directionOK = false;

        for (int i = startIndex + 1; i < searchToIndex; i++) {
            PointF lastPointPt = new PointF((float) track.get(i - 1).getEasting(), (float) track.get(i - 1).getNorthing());
            PointF currentPointPt = new PointF((float) track.get(i).getEasting(), (float) track.get(i).getNorthing());

            // Determine if location is between this one and last one. Also consider last but one point
            // to cover changes in direction for points that are very close to a vertex
            if (LocationMonitor.pointWithinLineSegmentWithTolerance(checkPoint, lastPointPt, currentPointPt, multiplier)) {
                Log.d(TAG, "Found next point within line segment: " + (i - 1) + " to " + i);

                if (i - 1 > startIndex) {
                    // Moved into a more distant segment, so distance must have increased
                    startIndex = i-1;
                    segmentDist = 0;
                    directionOK = true;
                } else {
                    // Find the point on the track
                    PointF nearestPt = LocationMonitor.getXXYY(checkPoint, lastPointPt, currentPointPt);
                    RoutePoint routePt = new RoutePoint();
                    routePt.setEasting(nearestPt.x);
                    routePt.setNorthing(nearestPt.y);
                    float calculatedDist = (float) AttemptData.calcDelta(routePt, track.get(i - 1));

                    if (calculatedDist > segmentDist) {
                        segmentDist = calculatedDist;
                        directionOK = true;
                    }
                }

                if (directionOK) {
                    break;
                }
            }
        }

        return directionOK;
    }

    // Work out how far along a segment the specified point is
    // It is already known that the point is in the segment somewhere
    public void calcSegmentDist(PointF pt, PointF segStart, PointF segEnd) {
        PointF nearestPt = LocationMonitor.getXXYY(pt, segStart, segEnd);
        RoutePoint routePt = new RoutePoint();
        routePt.setEasting(nearestPt.x);
        routePt.setNorthing(nearestPt.y);

        RoutePoint segStartRoutePoint = new RoutePoint();
        segStartRoutePoint.setEasting(segStart.x);
        segStartRoutePoint.setNorthing(segStart.y);
        segmentDist = (float) AttemptData.calcDelta(routePt, segStartRoutePoint);
    }

    int limitIndex(List<RoutePoint> track) {
        if (startIndex + searchTo >= track.size()) {
            return track.size()-1;
        } else {
            return startIndex + searchTo;
        }
    }
}
