package com.sk7software.climbviewer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.sk7software.climbviewer.geo.LatLngInterpolator;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MapFragment extends Fragment {

    private GPXRoute track;
    private GoogleMap map;
    private Map<ClimbController.PointType, Marker> marker;
    private boolean mapReady = false;
    private boolean trackRider = false;
    private boolean mirror = false;
    private PlotType plotType;
    private int zoom = 20;
    private float tilt = 67.5f;
    private int mapType = GoogleMap.MAP_TYPE_NORMAL;

    private static final String TAG = MapFragment.class.getSimpleName();
    private static final int MARKER_ANIMATION_MS = 1000;

    public enum PlotType {
        FULL_CLIMB,
        PURSUIT,
        ROUTE;
    }

    private OnMapReadyCallback callback = new OnMapReadyCallback() {

        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        @Override
        public void onMapReady(GoogleMap googleMap) {
            googleMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    map = googleMap;
                    map.setMapType(mapType);
                    track = (plotType == PlotType.ROUTE ? ClimbController.getInstance().getRoute() : ClimbController.getInstance().getClimb());
                    mapReady = true;
                    plotTrack();

                    if (ClimbController.getInstance().isAttemptInProgress()) {
                        addMarker(new LatLng(track.getPoints().get(0).getLat(), track.getPoints().get(0).getLon()),
                                ClimbController.PointType.ATTEMPT, Color.CYAN, plotType == PlotType.PURSUIT);
                    }
                }
            });
        }
    };

    public void setMapType(int type, PlotType plotType, boolean mirror) {
        this.mapType = type;
        this.plotType = plotType;
        this.mirror = mirror;
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

    public void setTrackRider(boolean trackRider) {
        this.trackRider = trackRider;
    }

    public void setZoom(int zoom) {
        this.zoom = zoom;
    }

    public void setTilt(float tilt) {
        this.tilt = tilt;
    }

    public void plotTrack(LatLng... position) {
        Log.d(TAG, "Map ready: " + mapReady);
        if (!mapReady || track == null) {
            return;
        }

        List<LatLng> points = new ArrayList<>();
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        for (RoutePoint pt : track.getPoints()) {
            LatLng point = new LatLng(pt.getLat(), pt.getLon());
            points.add(point);
            boundsBuilder.include(point);
        }
        if (position != null && position.length > 0) {
            boundsBuilder.include(position[0]);
        }

        // Plot polyline
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(points);

        lineOptions.width(15);
        lineOptions.color(Color.RED);
        map.addPolyline(lineOptions);

        updateView(boundsBuilder.build());
    }

    private void updateView(LatLngBounds bounds) {
        if (plotType == PlotType.FULL_CLIMB || plotType == PlotType.ROUTE) {
            int padding = 50; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            map.animateCamera(cu);
        } else if (plotType == PlotType.PURSUIT) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(track.getPoints().get(0).getLat(), track.getPoints().get(0).getLon())) // Sets the new camera position
                    .zoom(zoom) // Sets the zoom
                    .bearing(0) // Rotate the camera
                    .tilt(tilt) // Set the camera tilt
                    .build(); // Creates a CameraPosition from the builder
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

    public void moveCamera(RoutePoint point, boolean isMirror) {
        if (!trackRider) return;

        ClimbController.PointType ptType = (ClimbController.getInstance().isAttemptInProgress() ?
                ClimbController.PointType.ATTEMPT : ClimbController.PointType.ROUTE);
        float bearing = ClimbController.getInstance().getAttempts().get(ptType).getBearing();

        if (ptType == ClimbController.PointType.ATTEMPT) {
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
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 800, null);
    }

    public void addMarker(LatLng ll, ClimbController.PointType type, int colour, boolean large) {
        if (!mapReady) {
            return;
        }

        Marker m = marker.get(type);
        if (m != null) {
            animateMarker(m, ll, new LatLngInterpolator.LinearFixed());
        } else {
            marker.put(type, map.addMarker(new MarkerOptions()
                    .position(ll)
                    .icon(bitmapDescriptorFromVector(getActivity(), R.drawable.ic_biking_solid, colour, large))));
        }
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId, int colour, boolean large) {
        float scaleFac = large ? 1.5f : 1;
        int right = (int)(77 * scaleFac);
        int bottom = (int)(102 * scaleFac);
        Drawable background = ContextCompat.getDrawable(context, R.drawable.ic_map_marker_solid);
        background.setBounds(0, 0, right, bottom);
        background.setTint(Color.BLACK);
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds((int)(10 * scaleFac), (int)(8 * scaleFac), (int)(60 * scaleFac), (int)(50 * scaleFac));
        vectorDrawable.setTint(colour);
        Bitmap bitmap = Bitmap.createBitmap(right, bottom, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
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