package com.sk7software.climbviewer.maps;

import android.animation.Animator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.ui.IconGenerator;
import com.mapbox.bindgen.Expected;
import com.mapbox.bindgen.None;
import com.mapbox.bindgen.Value;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.maps.AsyncOperationResultCallback;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.observable.eventdata.CameraChangedEventData;
import com.mapbox.maps.extension.style.StyleContract;
import com.mapbox.maps.extension.style.StyleExtensionImpl;
import com.mapbox.maps.extension.style.expressions.generated.Expression;
import com.mapbox.maps.extension.style.image.ImageExtensionImpl;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.maps.plugin.animation.CameraAnimationsPlugin;
import com.mapbox.maps.plugin.animation.CameraAnimationsUtils;
import com.mapbox.maps.plugin.animation.Cancelable;
import com.mapbox.maps.plugin.animation.MapAnimationOptions;
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.ClimbViewActivity;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.LatLngInterpolator;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.Palette;
import com.sk7software.climbviewer.view.PositionMarker;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class MapBoxFragment extends Fragment implements IMapFragment{
    private MapboxMap map;
    private MapView mapView;
    private GPXRoute track;
    private Map<ClimbController.PointType, Map<PositionMarker.Size, Marker>> marker;
    private boolean mapReady = false;
    private boolean trackRider = false;
    private IMapFragment.PlotType plotType;
    private int zoom = 20;
    private double tilt;
    private LatLng centre;
    private String mapType;
    private List<SymbolLayer> climbMarkers = new ArrayList<>();
    private StyleExtensionImpl.Builder styleBuilder;
    Map<String, LoadedLayer> loadedLayers = new HashMap<>();
    private CameraAnimationsPlugin camera;
    private long styleLoadedTimeout = -1;
    private static final String TAG = MapBoxFragment.class.getSimpleName();
    private static final String TRACK_PREFIX = "line-";
    private static final String RIDER_PREFIX = "rider-";
    private static final String TRACK_RIDER_PREFIX = "rider-track-";
    private static final String CLIMB_TRACK_PREFIX = "climb-track-";
    private static final String CLIMB_LABEL_PREFIX = "climb-label-";
    private static final String LOCAL_TRACK_PREFIX = "local-track-";
    private static final String SOURCE = "source";
    private static final String LAYER = "layer";
    private static final String IMAGE = "img";
    private static final String MAPBOX_STYLE_PREFIX = "mapbox://styles/ajbrindle/";

    private static final double[] TILTS = {0.0, 45.0, 80.0};

    // or to get actual object of animation plugin and call functions directly with it

    @Override
    public void setMapType(MapType type, IMapFragment.PlotType plotType, boolean mirror) {
        String mapId = null;
        this.plotType = plotType;

        if (plotType == null) {
            plotType = PlotType.NORMAL;
        }
        switch(plotType) {
            case NORMAL:
            case ROUTE:
                mapId = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_2D_MAP_ID);
                this.mapType = mapId == null ? getString(R.string.mapbox_style_2d) : MAPBOX_STYLE_PREFIX + mapId;
                break;
            case FOLLOW_ROUTE:
            case FULL_CLIMB:
                mapId = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_FOLLOW_MAP_ID);
                this.mapType = mapId == null ? getString(R.string.mapbox_style_2d) : MAPBOX_STYLE_PREFIX + mapId;
                break;
            case PURSUIT:
            case CLIMB_3D:
                mapId = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_3D_MAP_ID);
                this.mapType = mapId == null ? getString(R.string.mapbox_style_satellite) : MAPBOX_STYLE_PREFIX + mapId;
                break;
            default:
                mapId = Preferences.getInstance().getStringPreference(Preferences.PREFERENCES_MAPBOX_2D_MAP_ID);
                this.mapType = mapId == null ? getString(R.string.mapbox_style_2d) : MAPBOX_STYLE_PREFIX + mapId;
        }
    }

    @Override
    public void updateMap() {
    }

    @Override
    public void setReady(boolean ready) {
        mapReady = ready;
    }

    @Override
    public void show(boolean visible) {
        if (mapView == null) {
            return;
        }

        if (visible) {
            mapView.setVisibility(View.VISIBLE);
        } else {
            mapView.setVisibility(View.GONE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        marker = new HashMap<>();
        return inflater.inflate(R.layout.fragment_map_box, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = getView().findViewById(R.id.map3dView);
        mapView.getMapboxMap().clearData(new AsyncOperationResultCallback() {
            @Override
            public void run(@NonNull Expected<String, None> result) {
                // Do nothing
            }
        });
        map = mapView.getMapboxMap();
        map.loadStyleUri(getString(R.string.mapbox_style_2d), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                if (plotType == IMapFragment.PlotType.ROUTE || plotType == IMapFragment.PlotType.FOLLOW_ROUTE) {
                    track = ClimbController.getInstance().getRoute();
                } else {
                    track = ClimbController.getInstance().getClimb();
                }
                plotTrack();
                mapReady = true;
            }
        });

        map.addOnCameraChangeListener(new OnCameraChangeListener() {
            @Override
            public void onCameraChanged(@NonNull CameraChangedEventData cameraChangedEventData) {
            }
        });

        camera = CameraAnimationsUtils.getCamera(mapView);
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
        this.zoom = 17;
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
        if (track == null) {
            return;
        }

        map.loadStyle(showTrack());
    }

    private StyleContract.StyleExtension showTrack() {
        styleBuilder = new StyleExtensionImpl.Builder(this.mapType);
        loadedLayers.clear();
        double trackOpacity = 0.7;

        if (this.plotType == PlotType.FULL_CLIMB || this.plotType == PlotType.CLIMB_3D) {
            trackOpacity = 0.0;
        }

        List<Point> trackCoordinates = getTrackAndZoom();

        LineString lineString = LineString.fromLngLats(trackCoordinates);
        FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(lineString)});
        GeoJsonSource trackJsonSource = new GeoJsonSource.Builder(TRACK_PREFIX + SOURCE).featureCollection(featureCollection).build();

        LineLayer linelayer = new LineLayer(TRACK_PREFIX + LAYER, TRACK_PREFIX + SOURCE);
        linelayer.lineWidth(5).lineColor("#FF0000").lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(trackOpacity);

        Bitmap icon = PositionMarker.getInstance().getIcon(PositionMarker.Size.TINY, Color.YELLOW);
        ImageExtensionImpl img = new ImageExtensionImpl.Builder(RIDER_PREFIX + IMAGE).bitmap(icon).build();

        FeatureCollection symbolCollection = FeatureCollection.fromFeatures(new Feature[] {Feature.fromGeometry(trackCoordinates.get(0))});
        GeoJsonSource symbolJsonSource = new GeoJsonSource.Builder(RIDER_PREFIX + SOURCE).featureCollection(symbolCollection).build();
        SymbolLayer symbol = new SymbolLayer(RIDER_PREFIX + LAYER, RIDER_PREFIX + SOURCE);
        symbol.iconImage(RIDER_PREFIX + IMAGE).iconAnchor(IconAnchor.BOTTOM).iconAllowOverlap(true);

        addLoadedLayer(TRACK_PREFIX, trackJsonSource, false, false);
        addLoadedLayer(RIDER_PREFIX, symbolJsonSource, false, false);

        styleBuilder.addSource(trackJsonSource);
        styleBuilder.addLayer(linelayer);
        if (this.plotType == PlotType.ROUTE || this.plotType == PlotType.NORMAL) {
            styleBuilder.addSource(symbolJsonSource);
            styleBuilder.addImage(img);
            styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(symbol, TRACK_PREFIX + LAYER, null));
        }

        if (this.plotType == PlotType.FULL_CLIMB) {
            plotElevationLines(track.getPoints(), 0, track.getPoints().size()-1, 60, true);
        } else if (this.plotType == PlotType.CLIMB_3D) {
            plotElevationLines(track.getPoints(), 0, track.getPoints().size()-1, 6, true);
        }

        return styleBuilder.build();
    }

    private List<Point> getTrackAndZoom() {
        List<Point> trackCoordinates = new ArrayList<>();
        for (RoutePoint pt : track.getPoints()) {
            trackCoordinates.add(Point.fromLngLat(pt.getLon(), pt.getLat()));
        }

        EdgeInsets padding = new EdgeInsets(100,100,100,100);

        map.setCamera(map.cameraForCoordinates(trackCoordinates, padding, 0.0, 0.0));
        return trackCoordinates;
    }

    @Override
    public void plotLocalSection(int minIdx, int maxIdx) {
        Log.d(TAG, "Plot local section: " + minIdx + " to " + maxIdx);
        if (!mapReady || track == null) {
            return;
        }

        if (minIdx < 0) {
            minIdx = 0;
        }

        if (maxIdx >= track.getPoints().size()) {
            maxIdx = track.getPoints().size()-1;
        }

        Log.d(TAG, "Adjusted to: " + minIdx + " to " + maxIdx);
        plotElevationLines(track.getPoints(), minIdx, maxIdx, 21, false);
    }

    @Override
    public boolean plotClimbTrackFromRoutePoints(String name, List<RoutePoint> points) {
        if (!isLayerAdded(getClimbTrackLayerPrefix(name))) {
            List<Point> trackCoordinates = new ArrayList<>();
            for (RoutePoint pt : points) {
                trackCoordinates.add(Point.fromLngLat(pt.getLon(), pt.getLat()));
            }
            LineString lineString = LineString.fromLngLats(trackCoordinates);

            FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(lineString)});
            GeoJsonSource geoJsonSource = new GeoJsonSource.Builder(getClimbTrackLayerPrefix(name) + SOURCE).featureCollection(featureCollection).build();

            LineLayer linelayer = new LineLayer(getClimbTrackLayerPrefix(name) + LAYER, getClimbTrackLayerPrefix(name) + SOURCE);
            linelayer.lineWidth(5).lineColor("#555555").lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(1.0);

            addLoadedLayer(getClimbTrackLayerPrefix(name), geoJsonSource, false, true);
            styleBuilder.addSource(geoJsonSource);
            styleBuilder.addLayer(linelayer);
            createClimbLabelIcon(name, new LatLng(points.get(0).getLat(), points.get(0).getLon()), true);
            return true;
        } else {
            // Make existing loaded layer visible
            map.getStyle().setStyleLayerProperty(getClimbTrackLayerPrefix(name) + LAYER, "visibility", new Value(Visibility.VISIBLE.getValue()));
        }
        return false;
    }

    public void reloadMap() {
        map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                for (Map.Entry<String, LoadedLayer> layer : loadedLayers.entrySet()) {
                    if (layer.getValue().isPending()) {
                        layer.getValue().setLoaded(true);
                        layer.getValue().setPending(false);
                    }
                }
            }
        });
    }

    private void plotElevationLines(List<RoutePoint> pts, int minIdx, int maxIdx, int width, boolean smoothed) {
        boolean first = true;
        boolean buildStyle = false;
        int count = 1;
        List<Feature> features = new ArrayList<>();
        String name = LOCAL_TRACK_PREFIX;

        for (int i=maxIdx; i>minIdx; i--) {
            List<Point> line = new ArrayList<>();
            line.add(Point.fromLngLat(pts.get(i - 1).getLon(), pts.get(i - 1).getLat()));
            line.add(Point.fromLngLat(pts.get(i).getLon(), pts.get(i).getLat()));
            LineString lineString = LineString.fromLngLats(line);

            double elevDiff = pts.get(i).getSmoothedElevation() - pts.get(i - 1).getSmoothedElevation();
            if (!smoothed) {
                elevDiff = pts.get(i).getElevation() - pts.get(i - 1).getElevation();
            }
            double distBetween = pts.get(i).getDistFromStart() - pts.get(i - 1).getDistFromStart();
            double gradient = elevDiff * 100 / distBetween;

            Feature feature = Feature.fromGeometry(lineString);
            feature.addStringProperty("color", Palette.getColourRGB(gradient));
            features.add(feature);
        }

        Feature[] featureArray = new Feature[features.size()];
        featureArray = features.toArray(featureArray);
        FeatureCollection featureCollection = FeatureCollection.fromFeatures(featureArray);

        if (!isLayerAdded(name)) {
            buildStyle = true;
            GeoJsonSource trackJsonSource = new GeoJsonSource.Builder(name + SOURCE).featureCollection(featureCollection).build();

            /**
            Drawable vectorDrawable = ContextCompat.getDrawable(this.getContext(), R.drawable.angle_up_solid);
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            vectorDrawable.setTint(Palette.getColour(0));
            vectorDrawable.draw(canvas);

            List<ImageStretches> stretch = new ArrayList<>();
            stretch.add(new ImageStretches(0, 0));
            ImageExtensionImpl img = new ImageExtensionImpl.Builder("ARROW-" + IMAGE).bitmap(bitmap).stretchX(stretch).stretchY(stretch).build();
            **/

            LineLayer linelayer = new LineLayer(name + LAYER, name + SOURCE);
            LineLayer lineBorder = new LineLayer(name + LAYER + "-edge", name + SOURCE);
            linelayer.lineWidth(width).lineColor(Expression.get("color")).lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(1.0);
            lineBorder.lineWidth(width+4).lineColor("#000000").lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(1.0);

            styleBuilder.addSource(trackJsonSource);
            styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(linelayer, null, TRACK_PREFIX + LAYER));
            styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(lineBorder, null, name + LAYER));
            addLoadedLayer(name, trackJsonSource, false, false);
        } else {
            LoadedLayer lineLayer = loadedLayers.get(name + LAYER);
            if (lineLayer.isLoaded()) {
                // As both the line and the edge use the same source, this will update both
                GeoJsonSource lineSource = lineLayer.getSource();
                lineSource.featureCollection(featureCollection);
            } else {
                checkTimeout(lineLayer);
            }
        }

        if (buildStyle) {
            styleLoadedTimeout = (new Date()).getTime() + 5000;
            map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    loadedLayers.get(LOCAL_TRACK_PREFIX + LAYER).setLoaded(true);
                    styleLoadedTimeout = -1;
                }
            });
        }
    }

    private void checkTimeout(LoadedLayer layer) {
        if (styleLoadedTimeout > 0 && (new Date()).getTime() > styleLoadedTimeout) {
            layer.setLoaded(true);
            styleLoadedTimeout = -1;
        }
    }

//    private void plotElevationLines(List<RoutePoint> pts, int minIdx, int maxIdx, int width, boolean smoothed) {
//        boolean first = true;
//        boolean buildStyle = false;
//        int count = 1;
//        List<Feature> features = new ArrayList<>();
//
//        for (int i=minIdx+1; i<=maxIdx; i++) {
//            String name = LOCAL_TRACK_PREFIX + count + "-";
//
//            List<Point> line = new ArrayList<>();
//            line.add(Point.fromLngLat(pts.get(i - 1).getLon(), pts.get(i - 1).getLat()));
//            line.add(Point.fromLngLat(pts.get(i).getLon(), pts.get(i).getLat()));
//            LineString lineString = LineString.fromLngLats(line);
//            FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(lineString)});
//
//            double elevDiff = pts.get(i).getSmoothedElevation() - pts.get(i - 1).getSmoothedElevation();
//            if (!smoothed) {
//                elevDiff = pts.get(i).getElevation() - pts.get(i - 1).getElevation();
//            }
//            double distBetween = pts.get(i).getDistFromStart() - pts.get(i - 1).getDistFromStart();
//            double gradient = elevDiff * 100 / distBetween;
//
//            if (!isLayerAdded(name)) {
//                buildStyle = true;
//                GeoJsonSource trackJsonSource = new GeoJsonSource.Builder(name + SOURCE).featureCollection(featureCollection).build();
//
//                LineLayer linelayer = new LineLayer(name + LAYER, name + SOURCE);
//                LineLayer lineBorder = new LineLayer(name + LAYER + "-edge", name + SOURCE);
//                linelayer.lineWidth(width).lineColor(Palette.getColour(gradient)).lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(1.0);
//                lineBorder.lineWidth(width+4).lineColor("#000000").lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(1.0);
//
//                String behind;
//
//                if (first) {
//                    behind = TRACK_PREFIX + LAYER;
//                    first = false;
//                } else {
//                    behind = LOCAL_TRACK_PREFIX + (count - 1) + "-" + LAYER;
//                }
//                styleBuilder.addSource(trackJsonSource);
//                styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(linelayer, null, behind));
//                styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(lineBorder, null, name + LAYER));
//                addLoadedLayer(name, trackJsonSource, false);
//            } else {
//                LoadedLayer lineLayer = loadedLayers.get(name + LAYER);
//                if (lineLayer.isLoaded()) {
//                    map.getStyle().setStyleLayerProperty(name + LAYER, "line-color", new Value(Palette.getColourRGB(gradient)));
//                    GeoJsonSource lineSource = lineLayer.getSource();
//                    lineSource.featureCollection(featureCollection);
//                }
//            }
//            count++;
//        }
//
//        if (buildStyle) {
//            final int endIndex = count-1;
//            map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
//                @Override
//                public void onStyleLoaded(@NonNull Style style) {
//                    for (int i=1; i<=endIndex; i++) {
//                        loadedLayers.get(LOCAL_TRACK_PREFIX + i + "-" + LAYER).setLoaded(true);
//                    }
//                }
//            });
//        }
//    }

    private String addIcon(IconGenerator iconFactory, CharSequence text, LatLng position) {
        String name = text.toString();
        if (!isLayerAdded(getClimbLabelLayerPrefix(name, false))) {
            ImageExtensionImpl img = new ImageExtensionImpl.Builder(getClimbLabelLayerPrefix(name, false) + IMAGE).bitmap(iconFactory.makeIcon(text)).build();

            FeatureCollection symbolCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(Point.fromLngLat(position.longitude, position.latitude))});
            GeoJsonSource labelJsonSource = new GeoJsonSource.Builder(getClimbLabelLayerPrefix(name, false) + SOURCE).featureCollection(symbolCollection).build();
            SymbolLayer symbol = new SymbolLayer(getClimbLabelLayerPrefix(name, false) + LAYER, getClimbLabelLayerPrefix(name, false) + SOURCE);
            symbol.iconImage(getClimbLabelLayerPrefix(name, false) + IMAGE).iconAnchor(IconAnchor.BOTTOM).iconAllowOverlap(true);

            addLoadedLayer(getClimbLabelLayerPrefix(name, false), labelJsonSource, false, false);
            styleBuilder.addSource(labelJsonSource);
            styleBuilder.addImage(img);
            styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(symbol, getClimbTrackLayerPrefix(name) + LAYER));
            return getClimbLabelLayerPrefix(name, false) + LAYER;
        } else {
            // Make existing layer visible
            LoadedLayer climbIconLayer = loadedLayers.get(getClimbLabelLayerPrefix(name, false) + LAYER);
            if (climbIconLayer.isLoaded()) {
                GeoJsonSource labelJsonSource = climbIconLayer.getSource();
                labelJsonSource.geometry(Point.fromLngLat(position.longitude, position.latitude));
                map.getStyle().setStyleLayerProperty(getClimbLabelLayerPrefix(name, false) + LAYER, "visibility", new Value(Visibility.VISIBLE.getValue()));
            }
        }
        return null;
    }

    private void updateOffRouteView(LatLng point) {
        final Cancelable cancelable = camera.easeTo(
                new CameraOptions.Builder()
                        .center(Point.fromLngLat(point.longitude, point.latitude))
                        .bearing(0.0)
                        .pitch(0.0)
                        .build(),
                new MapAnimationOptions.Builder().duration(900).build());
    }

    @Override
    public void setSingleClimbIcon(String name, LatLng ll) {
        if (!isLayerAdded(getClimbLabelLayerPrefix(name, true))) {
            createClimbLabelIcon(name, ll, true);
            map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    setLayerLoaded(getClimbLabelLayerPrefix(name, true) + LAYER);
                }
            });
        } else {
            LoadedLayer climbIconLayer = loadedLayers.get(getClimbLabelLayerPrefix(name, true) + LAYER);
            if (climbIconLayer.isLoaded()) {
                GeoJsonSource climbMarkerJsonSource = climbIconLayer.getSource();
                climbMarkerJsonSource.geometry(Point.fromLngLat(ll.longitude, ll.latitude));
                clearClimbMarkers();
                map.getStyle().setStyleLayerProperty(getClimbLabelLayerPrefix(name, true) + LAYER, "visibility", new Value(Visibility.VISIBLE.getValue()));
            }
        }
    }

    private void createClimbLabelIcon(String name, LatLng ll, boolean pending) {
        ImageExtensionImpl img = new ImageExtensionImpl.Builder(getClimbLabelLayerPrefix(name, true) + IMAGE).bitmap(createClimbLabelIcon(true).makeIcon(name)).build();

        FeatureCollection symbolCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(Point.fromLngLat(ll.longitude, ll.latitude))});
        final GeoJsonSource climbMarkerJsonSource = new GeoJsonSource.Builder(getClimbLabelLayerPrefix(name, true) + SOURCE).featureCollection(symbolCollection).build();
        SymbolLayer symbol = new SymbolLayer(getClimbLabelLayerPrefix(name, true) + LAYER, getClimbLabelLayerPrefix(name, true) + SOURCE);
        symbol.iconImage(getClimbLabelLayerPrefix(name, true) + IMAGE).iconAnchor(IconAnchor.TOP).iconAllowOverlap(true).visibility(pending ? Visibility.NONE : Visibility.VISIBLE);

        climbMarkers.add(symbol);
        styleBuilder.addSource(climbMarkerJsonSource);
        styleBuilder.addImage(img);
        styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(symbol, getClimbTrackLayerPrefix(name) + LAYER));
        addLoadedLayer(getClimbLabelLayerPrefix(name, true), climbMarkerJsonSource, false, pending);
    }

    private String getClimbLabelLayerPrefix(String name, boolean under) {
        return CLIMB_LABEL_PREFIX + name + (under ? "-U-" : "-O-" );
    }

    private String getClimbTrackLayerPrefix(String name) {
        return CLIMB_TRACK_PREFIX + name + "-";
    }

    private void setLayerLoaded(String name) {
        if (loadedLayers.containsKey(name)) {
            loadedLayers.get(name).setLoaded(true);
        }
    }
    private boolean isLayerAdded(String name) {
        return loadedLayers.containsKey(name + LAYER);
    }

    @Override
    public void showClimbLabels(List<GPXRoute> climbs) {
        final List<String> newLayers = new ArrayList<>();
        for (GPXRoute climb : climbs) {
            List<RoutePoint> pts = climb.getPoints();
            int midIndex = pts.size()/2;
            IconGenerator iconFactory = createClimbLabelIcon(false);
            String layerName = addIcon(iconFactory, climb.getName(),
                    new LatLng(climb.getPoints().get(midIndex).getLat(), climb.getPoints().get(midIndex).getLon()));
            if (layerName != null) {
                newLayers.add(getClimbLabelLayerPrefix(climb.getName(), false) + LAYER);
            }
        }

        if (!newLayers.isEmpty()) {
            map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    for (String newLayer : newLayers) {
                        setLayerLoaded(newLayer);
                    }

                    // Remove "under" markers
                    for (Map.Entry<String, LoadedLayer> layer : loadedLayers.entrySet()) {
                        if (layer.getKey().startsWith(CLIMB_LABEL_PREFIX) && layer.getKey().contains("-U-")) {
                            map.getStyle().setStyleLayerProperty(layer.getKey(), "visibility", new Value(Visibility.NONE.getValue()));
                        }
                    }
                }
            });
        }
    }

    private IconGenerator createClimbLabelIcon(boolean under) {
        IconGenerator iconFactory = new IconGenerator(ApplicationContextProvider.getContext());
        iconFactory.setStyle(IconGenerator.STYLE_WHITE);
        iconFactory.setColor(Color.parseColor("#455A64"));
        iconFactory.setContentPadding(4,0,4,0);
        iconFactory.setTextAppearance(R.style.climbMarkerTextStyle);
        if (under) {
            iconFactory.setRotation(180);
            iconFactory.setContentRotation(180);
        }
        return iconFactory;
    }

    @Override
    public void clearClimbTracks() {
        for (Map.Entry<String, LoadedLayer> loadedLayer : loadedLayers.entrySet()) {
            if (loadedLayer.getKey().startsWith(CLIMB_TRACK_PREFIX)) {
                map.getStyle().setStyleLayerProperty(loadedLayer.getKey(), "visibility", new Value(Visibility.NONE.getValue()));
            }
        }
        clearClimbMarkers();
    }

    @Override
    public void clearClimbMarkers() {
        for (Map.Entry<String, LoadedLayer> loadedLayer : loadedLayers.entrySet()) {
            if (loadedLayer.getKey().startsWith(CLIMB_LABEL_PREFIX) && loadedLayer.getValue().isLoaded()) {
                try {
                    map.getStyle().setStyleLayerProperty(loadedLayer.getKey(), "visibility", new Value(Visibility.NONE.getValue()));
                } catch (Throwable t) {
                    // I know this is rubbish but I can't figure out why there is a NPE
                    // TODO: fix this one day :-)
                    Log.e(TAG, "Error changing visibility: " + t.getMessage());
                }
            }
        }
    }

    @Override
    public void plotClimbTrack(List<LatLng> points) {
        if (!mapReady || points == null) {
            return;
        }

        List<Point> climbCoordinates = new ArrayList<>();
        for (LatLng ll : points) {
            climbCoordinates.add(Point.fromLngLat(ll.longitude, ll.latitude));
        }

        EdgeInsets padding = new EdgeInsets(100,100,100,100);
        map.setCamera(map.cameraForCoordinates(climbCoordinates, padding, 0.0, 0.0));

        LineString lineString = LineString.fromLngLats(climbCoordinates);
        FeatureCollection featureCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(lineString)});

        if (!isLayerAdded(CLIMB_TRACK_PREFIX)) {
            styleBuilder = new StyleExtensionImpl.Builder(this.mapType);
            loadedLayers.clear();
            double trackOpacity = 0.7;

            GeoJsonSource trackJsonSource = new GeoJsonSource.Builder(CLIMB_TRACK_PREFIX + SOURCE).featureCollection(featureCollection).build();

            LineLayer linelayer = new LineLayer(CLIMB_TRACK_PREFIX + LAYER, CLIMB_TRACK_PREFIX + SOURCE);
            linelayer.lineWidth(5).lineColor("#FF0000").lineCap(LineCap.ROUND).lineJoin(LineJoin.MITER).lineOpacity(trackOpacity);

            addLoadedLayer(CLIMB_TRACK_PREFIX, trackJsonSource, false, false);

            styleBuilder.addSource(trackJsonSource);
            styleBuilder.addLayer(linelayer);
            map.loadStyle(styleBuilder.build(), new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    setLayerLoaded(CLIMB_TRACK_PREFIX + LAYER);
                }
            });
        } else {
            LoadedLayer climbTrack = loadedLayers.get(CLIMB_TRACK_PREFIX + LAYER);
            if (climbTrack.isLoaded()) {
                GeoJsonSource source = climbTrack.getSource();
                source.featureCollection(featureCollection);
            }
        }
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

        List<Point> bounds = new ArrayList<>();
        bounds.add(Point.fromLngLat(southwestCorner.longitude, southwestCorner.latitude));
        bounds.add(Point.fromLngLat(northeastCorner.longitude, northeastCorner.latitude));
        EdgeInsets padding = new EdgeInsets(100,100,100,100);

        map.setCamera(map.cameraForCoordinates(bounds, padding, 0.0, 0.0));

        // Plot polyline
        updateOffRouteView(currentPoint);
    }

    @Override
    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB, boolean keepZoomAndPitch, ClimbController.PointType ptType, float bearing, ClimbViewActivity activity) {
        if (zoomToPB && ptType == ClimbController.PointType.ATTEMPT) {
            float distBetween = Math.abs(ClimbController.getInstance().getDistToPB());
            zoom = 19;

            if (distBetween < 20) {
                zoom = 24;
            } else if (distBetween > 150) {
                zoom = 14;
            }

            if (isMirror) {
                bearing = (bearing + 180) % 360;
                zoom = 16;
            }
        }

        camera.easeTo(
                new CameraOptions.Builder()
//                        .center(calcCentre(point, (bearing*Math.PI/180.0)))
                        .center(Point.fromLngLat(point.getLon(), point.getLat()))
                        .bearing(Double.valueOf(bearing))
                        .zoom(Double.valueOf(this.zoom))
                        .pitch(tilt)
                        .build(),
                new MapAnimationOptions.Builder()
                        .duration(900)
                        .animatorListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(@NonNull Animator animator) {}

                            @Override
                            public void onAnimationEnd(@NonNull Animator animator) {
                                if (activity != null) {
                                    activity.acceptMoveEvents();
                                }
                            }

                            @Override
                            public void onAnimationCancel(@NonNull Animator animator) {
                                if (activity != null) {
                                    activity.acceptMoveEvents();
                                }
                            }

                            @Override
                            public void onAnimationRepeat(@NonNull Animator animator) {}
                        })
                        .build());
    }

    @Override
    public void moveCamera(RoutePoint point, boolean isMirror, boolean zoomToPB) {
        if (!mapReady || !trackRider) return; //|| plotType == PlotType.NORMAL

        ClimbController.PointType ptType = (ClimbController.getInstance().isAttemptInProgress() ?
                ClimbController.PointType.ATTEMPT : ClimbController.PointType.ROUTE);

        float bearing = ClimbController.getInstance().getAttempts().get(ptType).getBearing();

        moveCamera(point, isMirror, zoomToPB, false, ptType, bearing, null);
    }

    private Point calcCentre(RoutePoint pos, double bearing) {
        int fullHeight = mapView.getHeight();
        int adjustment = (int)((fullHeight/12) * map.getMetersPerPixelAtLatitude(pos.getLat(), this.zoom));

        double cE = pos.getEasting() + (adjustment * Math.sin(bearing));
        double cN = pos.getNorthing() + (adjustment * Math.cos(bearing));
        RoutePoint newCentre = new RoutePoint();
        newCentre.setEasting(cE);
        newCentre.setNorthing(cN);
        LatLng newLL = GeoConvert.convertGridToLL(Database.getProjection(track.getProjectionId()), newCentre, track.getZone());
        return Point.fromLngLat(newLL.longitude, newLL.latitude);
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
        String riderTrackPrefix = getRiderTrackPrefix(type, size, colour);
        int idx = lls.size()-1;
        if (!isLayerAdded(riderTrackPrefix)) {
            FeatureCollection symbolCollection = FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(Point.fromLngLat(lls.get(idx).longitude, lls.get(idx).latitude))});
            Bitmap icon = PositionMarker.getInstance().getIcon(size, colour);
            ImageExtensionImpl img = new ImageExtensionImpl.Builder(riderTrackPrefix + IMAGE).bitmap(icon).build();
            GeoJsonSource symbolJsonSource = new GeoJsonSource.Builder(riderTrackPrefix + SOURCE).featureCollection(symbolCollection).build();
            SymbolLayer symbol = new SymbolLayer(riderTrackPrefix + LAYER, riderTrackPrefix + SOURCE);
            symbol.iconImage(riderTrackPrefix + IMAGE).iconAnchor(IconAnchor.BOTTOM).iconAllowOverlap(true);

            addLoadedLayer(riderTrackPrefix, symbolJsonSource, false, false);
            styleBuilder.addSource(symbolJsonSource);
            styleBuilder.addImage(img);
            styleBuilder.addLayerAtPosition(styleBuilder.layerAtPosition(symbol, TRACK_PREFIX + LAYER, null));

            map.loadStyle(styleBuilder.build());
            trackRider = true;
            setLayerLoaded(riderTrackPrefix + LAYER);
        } else {
            LoadedLayer riderLayer = loadedLayers.get(riderTrackPrefix + LAYER);
            if (riderLayer.isLoaded()) {
                GeoJsonSource source = riderLayer.getSource();
                source.geometry(Point.fromLngLat(lls.get(idx).longitude, lls.get(idx).latitude));

                if (riderLayer.isBeenRemoved()) {
                    map.getStyle().setStyleLayerProperty(riderTrackPrefix + LAYER, "icon-opacity", new Value(1.0));
                    riderLayer.setBeenRemoved(false);
                }
            }
        }
    }

    private String getRiderTrackPrefix(ClimbController.PointType type, PositionMarker.Size size, int colour) {
        return TRACK_RIDER_PREFIX + type.name() + "-" + size.name() + "-" + colour + "-";
    }

    @Override
    public void removeMarker(ClimbController.PointType type, int colour, PositionMarker.Size size) {
        String riderTrackPrefix = getRiderTrackPrefix(type, size, colour);
        LoadedLayer riderLayer = loadedLayers.get(riderTrackPrefix + LAYER);
        if (riderLayer != null && riderLayer.isLoaded()) {
            map.getStyle().setStyleLayerProperty(riderTrackPrefix + LAYER, "icon-opacity", new Value(0.0));
            riderLayer.setBeenRemoved(true);
//            Expected<String, Value> props = map.getStyle().getStyleLayerProperties(riderTrackPrefix + LAYER);
//            Log.d(TAG, "Props: " + props.getValue());
        }

    }

    @Override
    public void showPosition(LatLng ll) {
        if (ll == null) {
            return;
        }

        LoadedLayer riderLayer = loadedLayers.get(RIDER_PREFIX + LAYER);
        if (riderLayer == null) {
            return;
        }

        GeoJsonSource symbolJsonSource = riderLayer.getSource();
        symbolJsonSource.geometry(Point.fromLngLat(ll.longitude, ll.latitude));
    }

    private static void animateMarker(Marker marker, List<LatLng> positions, LatLngInterpolator latLngInterpolator) {
//        if (positions.isEmpty()) {
//            return;
//        }
//
//        LatLng[] lls = new LatLng[positions.size()];
//        lls = positions.toArray(lls);
//        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
//            @Override
//            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
//                return latLngInterpolator.interpolate(fraction, startValue, endValue);
//            }
//        };
//        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
//        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, lls);
//        animator.setDuration(MARKER_ANIMATION_MS);
//        animator.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mapView != null)
            mapView.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null)
            mapView.onStart();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null)
            mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null)
            mapView.onDestroy();
    }

    private void addLoadedLayer(String prefix, GeoJsonSource source, boolean beenRemoved, boolean pending) {
        LoadedLayer layer = new LoadedLayer(source, beenRemoved, false, pending);
        loadedLayers.put(prefix + LAYER, layer);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    class LoadedLayer {
        private GeoJsonSource source;
        private boolean beenRemoved;
        private boolean loaded;
        private boolean pending;

    }
}
