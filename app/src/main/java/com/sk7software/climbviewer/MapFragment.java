package com.sk7software.climbviewer;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
import com.google.android.gms.maps.GoogleMapOptions;
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
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.geo.LatLngInterpolator;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment {

    private GPXRoute climb;
    private GoogleMap map;
    private Map<ClimbController.PointType, Marker> marker;
    private boolean mapReady = false;
    private boolean trackRider = false;
    private PlotType plotType;
    private int mapType = GoogleMap.MAP_TYPE_NORMAL;

    private static final String TAG = MapFragment.class.getSimpleName();
    private static final int MARKER_ANIMATION_MS = 1000;

    public enum PlotType {
        FULL_CLIMB,
        PURSUIT;
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
                    climb = ClimbController.getInstance().getClimb();
                    mapReady = true;
                    addMarker(new LatLng(climb.getPoints().get(0).getLat(), climb.getPoints().get(0).getLon()),
                            ClimbController.PointType.ATTEMPT, Color.CYAN);
                    plotClimb();
                }
            });
        }
    };

    public void setMapType(int type, PlotType plotType) {
        this.mapType = type;
        this.plotType = plotType;
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

    private void plotClimb() {
        Log.d(TAG, "Map ready: " + mapReady);
        if (!mapReady) {
            return;
        }

        List<LatLng> points = new ArrayList<>();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (RoutePoint pt : climb.getPoints()) {
            LatLng point = new LatLng(pt.getLat(), pt.getLon());
            points.add(point);
            builder.include(point);
        }

        // Plot polyline
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(points);
        lineOptions.width(10);
        lineOptions.color(Color.RED);
        map.addPolyline(lineOptions);

        updateView(builder.build());
    }

    private void updateView(LatLngBounds bounds) {
        if (plotType == PlotType.FULL_CLIMB) {
            int padding = 50; // offset from edges of the map in pixels
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            map.animateCamera(cu);
        } else if (plotType == PlotType.PURSUIT) {
            CameraPosition position = new CameraPosition.Builder()
                    .target(new LatLng(climb.getPoints().get(0).getLat(), climb.getPoints().get(0).getLon())) // Sets the new camera position
                    .zoom(20) // Sets the zoom
                    .bearing(0) // Rotate the camera
                    .tilt(67.5f) // Set the camera tilt
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
    }

    public void moveCamera(RoutePoint point) {
        if (!trackRider) return;
        CameraPosition position = new CameraPosition.Builder()
                .target(new LatLng(point.getLat(), point.getLon()))
                .zoom(20)
                .tilt(67.5f)
                .bearing(ClimbController.getInstance().getBearing())
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 800, null);
    }

    public void addMarker(LatLng ll, ClimbController.PointType type, int colour) {
        if (!mapReady) {
            return;
        }

        RoutePoint snappedPosition = ClimbController.getInstance().getSnappedPosition().get(type);

        if (snappedPosition == null) {
            return;
        }

        LatLng snappedLL = new LatLng(snappedPosition.getLat(), snappedPosition.getLon());

        Marker m = marker.get(type);
        if (m != null) {
            animateMarkerToICS(m, snappedLL, new LatLngInterpolator.LinearFixed());
        } else {
            marker.put(type, map.addMarker(new MarkerOptions()
                    .position(snappedLL)
                    .icon(bitmapDescriptorFromVector(getActivity(), R.drawable.ic_biking_solid, colour))));
        }
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId, int colour) {
        Drawable background = ContextCompat.getDrawable(context, R.drawable.ic_map_marker_solid);
        background.setBounds(0, 0, 77, 102);
        background.setTint(Color.BLACK);
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(10, 8, 60, 50);
        vectorDrawable.setTint(colour);
        Bitmap bitmap = Bitmap.createBitmap(77, 102, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    static void animateMarkerToICS(Marker marker, LatLng finalPosition, final LatLngInterpolator latLngInterpolator) {
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