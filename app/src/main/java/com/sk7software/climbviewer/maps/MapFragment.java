package com.sk7software.climbviewer.maps;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.graphics.Color;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.ui.IconGenerator;
import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.ClimbViewActivity;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.geo.LatLngInterpolator;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.Palette;
import com.sk7software.climbviewer.view.PositionMarker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment implements IMapFragment {

    private GoogleMap map;
    private GPXRoute track;
    private Map<ClimbController.PointType, Map<PositionMarker.Size, Marker>> marker;
    private boolean mapReady = false;
    private boolean trackRider = false;
    private IMapFragment.PlotType plotType;
    private int zoom = 20;
    private float tilt = 67.5f;
    private LatLng centre;
    private Marker locationMarkerIcon = null;
    private int mapType = GoogleMap.MAP_TYPE_NORMAL;
    private Polyline climbTrack = null;
    private Polyline localTrack = null;
    private List<Polyline> localTracks = new ArrayList<>();
    private List<Polyline> climbSections = new ArrayList<>();
    private List<Marker> climbMarkers = new ArrayList<>();
    private static final double[] TILTS = {0.0, 45.0, 67.5};

    private static final String TAG = MapFragment.class.getSimpleName();

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
                        }
                    });
                }
            });
        }
    };

    @Override
    public void setMapType(MapType type, IMapFragment.PlotType plotType, boolean mirror) {
        switch(type) {
            case NORMAL:
                this.mapType = GoogleMap.MAP_TYPE_NORMAL;
                break;
            case HYBRID:
            case SATELLITE:
                this.mapType = GoogleMap.MAP_TYPE_HYBRID;
                break;
            default:
                this.mapType = GoogleMap.MAP_TYPE_NORMAL;
        }
        this.plotType = plotType;
    }

    @Override
    public void updateMap() {
        map.setMapType(mapType);
        map.clear();
        marker.clear();
    }

    @Override
    public void setReady(boolean ready) {
        mapReady = ready;
    }

    @Override
    public void show(boolean visible) {
        if (visible) {
            getView().setVisibility(View.VISIBLE);
        } else {
            getView().setVisibility(View.GONE);
        }
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

    @Override
    public void setPlotType(IMapFragment.PlotType plotType) {
        this.plotType = plotType;
    }

    @Override
    public void setTrackRider(boolean trackRider) {
        this.trackRider = trackRider;
    }

    @Override
    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    @Override
    public void setZoomForFollowing() {
        this.zoom = 18;
    }

    @Override
    public void setPitch(int pitch) {
        this.tilt = (float)pitch;
    }

    @Override
    public void setTilt(int tiltIdx) {
        this.tilt = (float)TILTS[tiltIdx];
    }

    @Override
    public void setCentre(LatLng centre) {
        this.centre = centre;
    }

    @Override
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

    @Override
    public void plotLocalSection(int minIdx, int maxIdx) {
        if (!mapReady || track == null) {
            return;
        }

        if (minIdx < 0) {
            minIdx = 0;
        }

        if (maxIdx >= track.getPoints().size()) {
            maxIdx = track.getPoints().size()-1;
        }

        if (localTrack != null) {
            localTrack.remove();
        }

        if (localTracks != null && !localTracks.isEmpty()) {
            localTracks.forEach(t -> t.remove());
            localTracks.clear();
        } else {
            localTracks = new ArrayList<>();
        }

        for (int i=minIdx+1; i<=maxIdx; i++) {
            localTracks.add(plotRouteBoundary(track.getPoints(), i, -1, 66));
            localTracks.add(plotElevationLine(track.getPoints(), i, 0, 60, false));
        }
    }

    @Override
    public boolean plotClimbTrackFromRoutePoints(String name, List<RoutePoint> points) {
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
        climbSections.add(map.addPolyline(lineOptions));
        return false;
    }

    private Polyline plotElevationLine(List<RoutePoint> pts, int i, int zIdx, int width, boolean smoothed) {
        PolylineOptions lineOptions = new PolylineOptions();
        List<LatLng> line = new ArrayList<>();
        line.add(new LatLng(pts.get(i - 1).getLat(), pts.get(i - 1).getLon()));
        line.add(new LatLng(pts.get(i).getLat(), pts.get(i).getLon()));

        double elevDiff = pts.get(i).getSmoothedElevation() - pts.get(i - 1).getSmoothedElevation();
        if (!smoothed) {
            elevDiff = pts.get(i).getElevation() - pts.get(i - 1).getElevation();
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

    private Polyline plotRouteBoundary(List<RoutePoint> pts, int i, int zIdx, int width) {
        PolylineOptions lineOptions = new PolylineOptions();
        List<LatLng> line = new ArrayList<>();
        line.add(new LatLng(pts.get(i - 1).getLat(), pts.get(i - 1).getLon()));
        line.add(new LatLng(pts.get(i).getLat(), pts.get(i).getLon()));

        lineOptions.width(width)
                .color(Color.BLACK)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .addAll(line)
                .zIndex(zIdx);
        return map.addPolyline(lineOptions);
    }

    private void addIcon(IconGenerator iconFactory, CharSequence text, LatLng position) {
        MarkerOptions markerOptions = new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(text)))
                .position(position)
                .draggable(false)
                .anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());
        climbMarkers.add(map.addMarker(markerOptions));
    }

    private void updateOffRouteView(LatLngBounds bounds) {
        int padding = 50; // offset from edges of the map in pixels

        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        map.moveCamera(cu);
        zoom = (int) map.getCameraPosition().zoom;
    }

    private void updateView(LatLngBounds bounds) {
        if (plotType == IMapFragment.PlotType.ROUTE || plotType == IMapFragment.PlotType.NORMAL) {
            updateOffRouteView(bounds);
        } else if (plotType == IMapFragment.PlotType.FULL_CLIMB || plotType == IMapFragment.PlotType.PURSUIT ||
                plotType == IMapFragment.PlotType.FOLLOW_ROUTE || plotType == PlotType.CLIMB_3D) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(track.getPoints().get(0).getLat(), track.getPoints().get(0).getLon()))
                    .zoom(zoom)
                    .bearing(0)
                    .tilt(tilt)
                    .build();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 2000, new GoogleMap.CancelableCallback() {
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

    @Override
    public void setSingleClimbIcon(String name, LatLng ll) {
        Marker m = null;
        if (climbMarkers != null && !climbMarkers.isEmpty()) {
            m = climbMarkers.get(0);
        }
        setClimbIcon(name, ll, true);
        if (m != null) {
            climbMarkers.remove(m);
            m.remove();
        }
    }

    @Override
    public void showClimbLabels(List<GPXRoute> climbs) {
        for (GPXRoute climb : climbs) {
            List<RoutePoint> pts = climb.getPoints();
            int midIndex = pts.size()/2;
            setClimbIcon(climb.getName(), new LatLng(pts.get(midIndex).getLat(), pts.get(midIndex).getLon()), false);
        }
    }

    private void setClimbIcon(String name, LatLng ll, boolean under) {
        IconGenerator iconFactory = new IconGenerator(ApplicationContextProvider.getContext());
        iconFactory.setStyle(IconGenerator.STYLE_WHITE);
        iconFactory.setColor(Color.parseColor("#455A64"));
        iconFactory.setContentPadding(4,0,4,0);
        iconFactory.setTextAppearance(R.style.climbMarkerTextStyle);
        if (under) {
            iconFactory.setRotation(180);
            iconFactory.setContentRotation(180);
        }
        addIcon(iconFactory, name, ll);
    }

    @Override
    public void clearClimbTracks() {
        climbSections.forEach(c -> c.remove());
        climbSections.clear();
        clearClimbMarkers();
    }

    @Override
    public void clearClimbMarkers() {
        if (climbMarkers != null) {
            climbMarkers.forEach(m -> m.remove());
            climbMarkers.clear();
        }
    }

    @Override
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

    @Override
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

    @Override
    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB, boolean keepZoomAndPitch, ClimbController.PointType ptType, float bearing, ClimbViewActivity activity) {

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

    @Override
    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB) {
        if (!mapReady || !trackRider || plotType == PlotType.NORMAL) return;

        ClimbController.PointType ptType = (ClimbController.getInstance().isAttemptInProgress() ?
                ClimbController.PointType.ATTEMPT : ClimbController.PointType.ROUTE);

        float bearing = ClimbController.getInstance().getAttempts().get(ptType).getBearing();

        moveCamera(point, isMirror, zoomToPB, false, ptType, bearing, null);
    }

    @Override
    public void addMarker(LatLng ll, ClimbController.PointType type, int colour, PositionMarker.Size size) {
        List<LatLng> lls = new ArrayList<>();
        lls.add(ll);
        addMarker(lls, type, colour, size);
    }

    @Override
    public void addMarker(List<LatLng> lls, ClimbController.PointType type, int colour, PositionMarker.Size size) {
        if (!mapReady || lls.isEmpty()) {
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
            animateMarker(m, lls, new LatLngInterpolator.Linear());
        } else {
            sizeMarkers.put(size, map.addMarker(new MarkerOptions()
                    .position(lls.get(0))
                    .icon(BitmapDescriptorFactory.fromBitmap(PositionMarker.getInstance().getIcon(size, colour)))));
            marker.put(type, sizeMarkers);
        }
    }

    @Override
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

    @Override
    public void showPosition(LatLng ll) {
        if (!mapReady) {
            return;
        }

        if (ll == null) {
            return;
        }

        Marker lastMarker = null;

        if (locationMarkerIcon != null) {
            lastMarker = locationMarkerIcon;
        }

        // Plot location
        locationMarkerIcon = map.addMarker(new MarkerOptions()
                .position(ll)
                .zIndex(101)
                .icon(BitmapDescriptorFactory.fromBitmap(PositionMarker.getInstance().getIcon(PositionMarker.Size.TINY, Color.YELLOW))));

        if (lastMarker != null) {
            lastMarker.remove();
        }
    }

    private static void animateMarker(Marker marker, List<LatLng> positions, LatLngInterpolator latLngInterpolator) {
        if (positions.isEmpty()) {
            return;
        }

        LatLng[] lls = new LatLng[positions.size()];
        lls = positions.toArray(lls);
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return latLngInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, lls);
        animator.setDuration(MARKER_ANIMATION_MS);
        animator.start();
    }

    public void downloadRoute(int routeId, ActivityUpdateInterface activity) {
        // Not implemented for Google Maps
    }

    public void clearDownload() {
        // Not implemented for Goodle Maps
    }
}