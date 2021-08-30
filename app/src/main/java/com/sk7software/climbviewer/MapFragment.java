package com.sk7software.climbviewer;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {

    private GPXRoute climb;
    private GoogleMap map;
    private Marker markerCurrent;
    private Marker markerPB;
    private boolean mapReady = false;

    private static final String TAG = MapFragment.class.getSimpleName();

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
                    int climbId = getActivity().getIntent().getIntExtra("id", 0);
                    climb = Database.getInstance().getClimb(climbId);
                    mapReady = true;
                    plotClimb(googleMap);
                    markerCurrent = googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(climb.getPoints().get(0).getLat(), climb.getPoints().get(0).getLon()))
                            .title("Start"));
                }
            });
        }


    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
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

    private void plotClimb(GoogleMap googleMap) {
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
        googleMap.addPolyline(lineOptions);

        LatLngBounds bounds = builder.build();

        int padding = 50; // offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        googleMap.animateCamera(cu);
    }

    public void addMarker(LatLng ll, float hue) {
        if (!mapReady) {
            return;
        }

        if (hue == BitmapDescriptorFactory.HUE_BLUE) {
            // Current pos
            if (markerCurrent != null) {
                markerCurrent.remove();
            }
            markerCurrent = map.addMarker(new MarkerOptions()
                    .position(ll)
                    .icon(bitmapDescriptorFromVector(getActivity(), R.drawable.ic_biking_solid, Color.BLUE))
//                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .title("Position"));
        } else {
            // PB
            if (markerPB != null) {
                markerPB.remove();
            }
            markerPB = map.addMarker(new MarkerOptions()
                    .position(ll)
                    .icon(bitmapDescriptorFromVector(getActivity(), R.drawable.ic_biking_solid, Color.GREEN))
//                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .title("Position"));
        }
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId, int colour) {
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds(0, 0, 60, 50);
        vectorDrawable.setTint(colour);
        Bitmap bitmap = Bitmap.createBitmap(60, 50, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}