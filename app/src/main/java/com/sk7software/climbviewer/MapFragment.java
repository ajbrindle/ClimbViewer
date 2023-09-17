package com.sk7software.climbviewer;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;
import com.sk7software.climbviewer.geo.LatLngInterpolator;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.Palette;
import com.sk7software.climbviewer.view.PositionMarker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment {

    private GPXRoute track;
    private GoogleMap map;
    private Map<ClimbController.PointType, Map<PositionMarker.Size, Marker>> marker;
    private boolean mapReady = false;
    private boolean trackRider = false;
    private boolean mirror = false;
    private PlotType plotType;
    private int zoom = 20;
    private float tilt = 67.5f;
    private LatLng centre;
    private Circle locationMarker = null;
    private float posMarkerRadius = -1;
    private int mapType = GoogleMap.MAP_TYPE_NORMAL;
    private Polyline climbTrack = null;
    private Polyline localTrack = null;
    private List<Polyline> localTracks = new ArrayList<>();

    private static final String TAG = MapFragment.class.getSimpleName();
    private static final int MARKER_ANIMATION_MS = 1000;

    public enum PlotType {
        FULL_CLIMB,
        PURSUIT,
        ROUTE,
        FOLLOW_ROUTE,
        NORMAL
    }

    private final OnMapReadyCallback callback = new OnMapReadyCallback() {
        @Override
        public void onMapReady(GoogleMap googleMap) {
            Log.d(TAG, "MAP READY");
            googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    Log.d(TAG, "MAP LOADED");
                    map = googleMap;
                    map.setMapType(mapType);
                    if (plotType == PlotType.ROUTE || plotType == PlotType.FOLLOW_ROUTE) {
                        track = ClimbController.getInstance().getRoute();
                    } else {
                        track = ClimbController.getInstance().getClimb();
                    }
                    mapReady = true;

                    if (centre != null) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(centre, zoom));
                    }
                    plotTrack();

                    // Force recalculation of radius if zooming
                    map.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
                        @Override
                        public void onCameraMove() {
                            posMarkerRadius = -1;
                        }
                    });
                }
            });
        }
    };

    public void setMapType(int type, PlotType plotType, boolean mirror) {
        this.mapType = type;
        this.plotType = plotType;
        this.mirror = mirror;
    }

    public void updateMap() {
        map.setMapType(mapType);
        map.clear();
        marker.clear();
    }

    public void setReady(boolean ready) {
        mapReady = ready;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        marker = new HashMap<>();
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }
    }

    public void setPlotType(PlotType plotType) {
        this.plotType = plotType;
    }

    public void setTrackRider(boolean trackRider) {
        this.trackRider = trackRider;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public void setTilt(float tilt) {
        this.tilt = tilt;
    }

    public void setCentre(LatLng centre) {
        this.centre = centre;
    }

    public void plotTrack() {
        if (!mapReady || track == null) {
            return;
        }

        // Determine bounds
        List<LatLng> points = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (RoutePoint pt : track.getPoints()) {
            LatLng point = new LatLng(pt.getLat(), pt.getLon());
            points.add(point);
            boundsBuilder.include(point);
        }
        if (plotType == PlotType.FULL_CLIMB) {
            // Plot gradient shaded line for climb
            int zIdx = track.getPoints().size() + 1;
            for (int i = 1; i < track.getPoints().size(); i++) {
                plotElevationLine(track.getPoints(), i, zIdx--, 180, true);
            }
        } else {
            // Plot basic line for route
            PolylineOptions lineOptions = new PolylineOptions();
            lineOptions.addAll(points)
                       .width(20)
                       .zIndex(2)
                       .color(0xBBFF0000);
            map.addPolyline(lineOptions);
        }
        updateView(boundsBuilder.build());
    }

    private Polyline plotElevationLine(List<RoutePoint> pts, int i, int zIdx, int width, boolean smoothed) {
        PolylineOptions lineOptions = new PolylineOptions();
        List<LatLng> line = new ArrayList<>();
        line.add(new LatLng(pts.get(i - 1).getLat(), pts.get(i - 1).getLon()));
        line.add(new LatLng(pts.get(i).getLat(), pts.get(i).getLon()));

        double elevDiff = pts.get(i).getSmoothedElevation() - pts.get(i - 1).getSmoothedElevation();
        if (!smoothed) {
            elevDiff = pts.get(i).getElevation() - pts.get(i-1).getElevation();
        }
        double distBetween = pts.get(i).getDistFromStart() - pts.get(i - 1).getDistFromStart();
        double gradient = elevDiff * 100 / distBetween;

        lineOptions.width(width)
                .color(Palette.getColour(gradient))
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .addAll(line)
                .zIndex(zIdx);
        return map.addPolyline(lineOptions);
    }
    public void plotLocalSection(int minIdx, int maxIdx) {
        if (!mapReady || track == null) {
            return;
        }

        List<LatLng> points = new ArrayList<>();
        if (minIdx < 0) {
            minIdx = 0;
        }

        if (maxIdx > track.getPoints().size()) {
            maxIdx = track.getPoints().size();
        }

        if (localTrack != null) {
            localTrack.remove();
        }

        if (localTracks != null && !localTracks.isEmpty()) {
            localTracks.forEach(t -> t.remove());
            localTracks.clear();
        }

        for (int i=minIdx+1; i<=maxIdx; i++) {
            localTracks.add(plotElevationLine(track.getPoints(), i, 0, 60, false));
        }
    }

    public void plotClimbTrackFromRoutePoints(List<RoutePoint> points) {
        List<LatLng> pointsLL = new ArrayList<>();
        for (RoutePoint pt : points) {
            LatLng ll = new LatLng(pt.getLat(), pt.getLon());
            pointsLL.add(ll);
        }
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(pointsLL)
                .width(20)
                .color(0xFF555555)
                .zIndex(10);
        map.addPolyline(lineOptions);
    }

    public void plotClimbTrack(List<LatLng> points) {
        if (!mapReady || track == null || points == null) {
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (LatLng point : points) {
            boundsBuilder.include(point);
        }

        if (climbTrack != null) {
            climbTrack.remove();
        }

        // Plot basic line for route
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(points)
                .width(20)
                .color(0xFF555555)
                .zIndex(10);
        climbTrack = map.addPolyline(lineOptions);
        updateView(boundsBuilder.build());
    }

    public void plotOffRouteTrack(double radius, LatLng currentPoint, float bearing) {
        if (!mapReady || track == null) {
            return;
        }

        plotType = PlotType.ROUTE;

        // Increase radius to give a bit of padding but limit to 1km
        if (radius > 500) {
            radius = 500;
        }
        radius *= 1.2;

        double distanceFromCenterToCorner = radius * Math.sqrt(2.0);
        LatLng southwestCorner =
                SphericalUtil.computeOffset(currentPoint, distanceFromCenterToCorner, 225.0);
        LatLng northeastCorner =
                SphericalUtil.computeOffset(currentPoint, distanceFromCenterToCorner, 45.0);
        List<LatLng> points = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (RoutePoint pt : track.getPoints()) {
            LatLng point = new LatLng(pt.getLat(), pt.getLon());
            points.add(point);
        }

        boundsBuilder.include(southwestCorner);
        boundsBuilder.include(northeastCorner);

        // Plot polyline
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(points)
                .width(20)
                .zIndex(2)
                .color(0xBBFF0000);
        map.addPolyline(lineOptions);

        updateOffRouteView(boundsBuilder.build());
    }

    private void updateOffRouteView(LatLngBounds bounds) {
        int padding = 50; // offset from edges of the map in pixels

        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        map.moveCamera(cu);
        zoom = (int) map.getCameraPosition().zoom;
    }

    private void updateView(LatLngBounds bounds) {
        if (plotType == PlotType.ROUTE || plotType == PlotType.NORMAL) {
            updateOffRouteView(bounds);
        } else if (plotType == PlotType.FULL_CLIMB || plotType == PlotType.PURSUIT || plotType == PlotType.FOLLOW_ROUTE) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(track.getPoints().get(0).getLat(), track.getPoints().get(0).getLon()))
                    .zoom(zoom)
                    .bearing(0)
                    .tilt(tilt)
                    .build();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000,new GoogleMap.CancelableCallback() {
                @Override
                public void onFinish() {
                    trackRider = true;
                }
                @Override
                public void onCancel() {
                    trackRider = true;
                }
            });
        }

        if (ClimbController.getInstance().isRouteInProgress()) {
            trackRider = true;
        }
    }

    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB, ClimbController.PointType ptType, float bearing, ClimbViewActivity activity) {

        if (zoomToPB && ptType == ClimbController.PointType.ATTEMPT) {
            float distBetween = Math.abs(ClimbController.getInstance().getDistToPB());
            zoom = 20;

            if (distBetween < 20) {
                zoom = 25;
            } else if (distBetween > 150) {
                zoom = 15;
            }

            if (isMirror) {
                bearing = (bearing + 180) % 360;
                zoom = 17;
            }
        }

        CameraPosition position = new CameraPosition.Builder()
                .target(new LatLng(point.getLat(), point.getLon()))
                .zoom(zoom)
                .tilt(tilt)
                .bearing(bearing)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 800, new GoogleMap.CancelableCallback() {
            @Override
            public void onCancel() {
                if (activity != null) {
                    activity.acceptMoveEvents();
                }
            }

            @Override
            public void onFinish() {
                if (activity != null) {
                    activity.acceptMoveEvents();
                }
            }
        });
    }

    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB) {
        if (!mapReady || !trackRider || plotType == PlotType.NORMAL) return;

        ClimbController.PointType ptType = (ClimbController.getInstance().isAttemptInProgress() ?
                ClimbController.PointType.ATTEMPT : ClimbController.PointType.ROUTE);

        float bearing = ClimbController.getInstance().getAttempts().get(ptType).getBearing();

        moveCamera(point, isMirror, zoomToPB, ptType, bearing, null);
    }

    public void addMarker(LatLng ll, ClimbController.PointType type, int colour, PositionMarker.Size size) {
        if (!mapReady) {
            return;
        }

        Marker m = null;
        Map<PositionMarker.Size, Marker> sizeMarkers = marker.get(type);
        if (sizeMarkers != null) {
            m = sizeMarkers.get(size);
        } else {
            sizeMarkers = new HashMap<>();
        }

        if (m != null) {
            animateMarker(m, ll, new LatLngInterpolator.LinearFixed());
        } else {
            sizeMarkers.put(size, map.addMarker(new MarkerOptions()
                    .position(ll)
                    .icon(BitmapDescriptorFactory.fromBitmap(PositionMarker.getInstance().getIcon(size, colour)))));
            marker.put(type, sizeMarkers);
        }
    }

    public void removeMarker(ClimbController.PointType type, int colour, PositionMarker.Size size) {
        Marker m = null;
        Map<PositionMarker.Size, Marker> sizeMarkers = marker.get(type);
        if (sizeMarkers != null) {
            m = sizeMarkers.get(size);
            if (m != null) {
                m.remove();
                sizeMarkers.remove(size);
            }
        }
    }

    public void showPosition(LatLng ll) {
        if (!mapReady) {
            return;
        }

        if (locationMarker != null) {
            locationMarker.remove();
        }

        if (ll == null) {
            return;
        }

        // Plot location
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(ll);
        circleOptions.radius(calcRadius());
        circleOptions.strokeColor(Color.BLACK);
        circleOptions.fillColor(Color.BLUE);
        circleOptions.strokeWidth(3);
        circleOptions.zIndex(100);
        locationMarker = map.addCircle(circleOptions);
    }

    private float calcRadius() {
        if (posMarkerRadius > 0) return posMarkerRadius;

        // get 2 of the visible diagonal corners of the map (could also use farRight and nearLeft)
        LatLng topLeft = map.getProjection().getVisibleRegion().farLeft;
        LatLng bottomRight = map.getProjection().getVisibleRegion().nearRight;

        // use the Location class to calculate the distance between the 2 diagonal map points
        float[] results = new float[4];
        Location.distanceBetween(topLeft.latitude,topLeft.longitude,bottomRight.latitude,bottomRight.longitude,results);
        posMarkerRadius = results[0]/100;
        return posMarkerRadius;
    }



    private static void animateMarker(Marker marker, LatLng finalPosition, final LatLngInterpolator latLngInterpolator) {
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return latLngInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(MARKER_ANIMATION_MS);
        animator.start();
    }
 }