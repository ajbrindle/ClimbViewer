package com.sk7software.climbviewer.maps;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.ClimbViewActivity;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.PositionMarker;

import java.util.List;

public interface IMapFragment {
    enum PlotType {
        FULL_CLIMB,
        PURSUIT,
        CLIMB_3D,
        ROUTE,
        FOLLOW_ROUTE,
        NORMAL
    }

    static final int MARKER_ANIMATION_MS = 2000;

    void setMapType(MapType type, IMapFragment.PlotType plotType, boolean mirror);

    void updateMap();

    void show(boolean visible);

    void setReady(boolean ready);

    @Nullable
    View onCreateView(@NonNull LayoutInflater inflater,
                      @Nullable ViewGroup container,
                      @Nullable Bundle savedInstanceState);

    void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState);

    void setPlotType(IMapFragment.PlotType plotType);

    void setTrackRider(boolean trackRider);

    void setZoom(int zoom);

    void setZoomForFollowing();

    void setPitch(int pitch);

    void setTilt(int tiltIdx);

    void setCentre(LatLng centre);

    void plotTrack();

    void plotLocalSection(int minIdx, int maxIdx);

    boolean plotClimbTrackFromRoutePoints(String name, List<RoutePoint> points);
    default void reloadMap() {}

    void setSingleClimbIcon(String name, LatLng ll);

    void showClimbLabels(List<GPXRoute> climbs);

    void clearClimbTracks();

    void clearClimbMarkers();

    void plotClimbTrack(List<LatLng> points);

    void plotOffRouteTrack(double radius, LatLng currentPoint, float bearing);

    void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB, boolean keepZoomAndPitch, ClimbController.PointType ptType, float bearing, ClimbViewActivity activity);

    void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB);

    void addMarker(LatLng ll, ClimbController.PointType type, int colour, PositionMarker.Size size);

    void addMarker(List<LatLng> lls, ClimbController.PointType type, int colour, PositionMarker.Size size);

    void removeMarker(ClimbController.PointType type, int colour, PositionMarker.Size size);

    void showPosition(LatLng ll);
    void downloadRoute(int routeId, ActivityUpdateInterface activity);
    void clearDownload();
}
