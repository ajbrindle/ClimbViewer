package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.PlotType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClimbView extends View {

    private GPXRoute profile;
    private List<PlotPoint> points;
    private double yRange;
    private Map<PlotType, Double> scaleFacX = new HashMap<>();
    private int height;
    private boolean heightSet;
    private boolean showZoom;
    private boolean initialised;
    private boolean leaveGap;
    private Set<ClimbController.PointType> plots;
    private boolean startUpdating;
    private int showGradientAt;
    private int transparency;

    private static final int PADDING = 20;
    private static final int SMOOTH_DIST = 10;
    private static final String TAG = ClimbView.class.getSimpleName();
    private static final int ZOOM_DIST = 500;
    private static final float ZOOM_MULTIPLIER = 2.5f;
    private static final int TOP_MARGIN = 110;

    public ClimbView(Context context) {
        super(context);
    }

    public ClimbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ClimbView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClimbView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setClimb(GPXRoute profile, boolean showZoom) {
        this.profile = profile;
        this.showZoom = showZoom;
        this.heightSet = false;
        this.initialised = false;
        this.plots = new HashSet<>();
        this.showGradientAt = -1;

        this.startUpdating = false;
    }

    public void addPlot(ClimbController.PointType p) {
        plots.add(p);
    }

    public void setHeight(int height, boolean leaveGap) {
        this.height = height;
        this.heightSet = true;
        this.leaveGap = leaveGap;
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = height;
        this.setLayoutParams(layoutParams);
    }

    public void setShowGradientAt(int x) {
        this.showGradientAt = x;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!heightSet) return;

        // Plot points onto bitmap first, then copy that to the display canvas
        Bitmap bitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);

        // Don't use full height if there is to be a gap at the bottom
        int yHeight = (leaveGap ? height/2 : height);

        Point size = getScreenSize();
        int screenWidth = size.x - (2 * PADDING); // Add some padding for display

        if (points == null) {
            points = getClimbPoints(0, Integer.MAX_VALUE, true);
            yRange = convertToPlotPoints(points, screenWidth, yHeight, 1, PlotType.FULL);

            // Height might have changed
            yHeight = (leaveGap ? height/2 : height);
        }

        int y0 = 0;
        double extra = (leaveGap ? (yHeight - yRange)/2 : 0);
        int maxY = (int)(yRange + TOP_MARGIN + extra);
        plotPoints(points, y0, maxY, bitmapCanvas, PADDING);

        if (showZoom && startUpdating) {
            showZoomProfile(bitmapCanvas, y0, size);
        }

        Paint p = new Paint();

        if (!leaveGap) {
            p.setAlpha(transparency);
        }
        canvas.drawBitmap(bitmap, 0, 0, p);
        plotGradientLine(points, y0, maxY, canvas, PADDING);
        plotPositions(canvas, y0);
        initialised = true;
    }

    private void plotPositions(Canvas canvas, int y0) {
        for (ClimbController.PointType p : plots) {
            Log.d(TAG, "Plot postion: " + p.name());
            PlotPoint pt = calcPlotXY(points, PlotType.FULL, PADDING, ClimbController.getInstance().getAttempts().get(p).getDist());
            Log.d(TAG, "Plot rider " + p.name() + " at " + pt.getX() + "," + pt.getY());
            drawTracker(canvas, pt, p.getColor(), PADDING, y0);
        }
    }

    private void showZoomProfile(Canvas canvas, int y0, Point size) {
        List<PlotPoint> zoomPoints = new ArrayList<>();

        float attemptDist = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist();
        PlotPoint attPoint = calcPlotXY(points, PlotType.FULL, PADDING, attemptDist);

        // Find point before current location
        int startIdx = findPointBefore(attPoint);
        Log.d(TAG, "Zoom point start: " + startIdx);
        zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);

        // Push back start if too short
        if ((zoomPoints.get(zoomPoints.size() - 1).getX() - zoomPoints.get(0).getX()) < ZOOM_DIST) {
            startIdx = adjustStart(attPoint, startIdx, zoomPoints.get(zoomPoints.size() - 1).getX(), ZOOM_DIST);
            zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);
        }

        Log.d(TAG, "ZOOM START X: " + zoomPoints.get(0).getX() + " [" + startIdx + "]");

        if (!zoomPoints.isEmpty()) {
            y0 = height / 2;
            int zoomWidth = size.x / 2;

            // Draw box round zoom area
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(5);

            float startX = zoomPoints.get(0).getX();
            float distOnZoom = attemptDist - startX;
            double zoomYRange = convertToPlotPoints(zoomPoints, zoomWidth, (3 * height / 8) - 100, ZOOM_MULTIPLIER, PlotType.ZOOM);
            int maxZoomY = (int) (zoomYRange + TOP_MARGIN + ((((3 * height) / 8) - 100 - yRange) / 2));
            int zoomPadding = (int) (size.x - zoomPoints.get(zoomPoints.size() - 1).getX()) / 2;
            canvas.drawRoundRect(zoomPadding - 10, y0 - 20, zoomPadding + zoomWidth + 10, maxZoomY + y0 + 10, 20, 20, p);
            canvas.drawLine(attPoint.getX() + PADDING, attPoint.getY(), zoomPadding, y0 - 20, p);
            canvas.drawLine(attPoint.getX() + PADDING, attPoint.getY(), zoomPadding + zoomWidth, y0 - 20, p);
            plotPoints(zoomPoints, y0, maxZoomY, canvas, zoomPadding);

            PlotPoint zoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distOnZoom);
            drawTracker(canvas, zoomPoint, ClimbController.PointType.ATTEMPT.getColor(), zoomPadding, y0);

            AttemptData pbData = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB);
            if (pbData != null) {
                float pbDist = pbData.getDist();
                float distPBOnZoom = pbDist - startX;
                Log.d(TAG, "ZOOM: " + startX + ", " + zoomWidth + "; PB dist: " + pbDist + " [" + distPBOnZoom + "]");
                if (distPBOnZoom > 0) {
                    PlotPoint pbZoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distPBOnZoom);

                    if (pbZoomPoint.getX() > 0 && pbZoomPoint.getX() < zoomWidth) {
                        drawTracker(canvas, pbZoomPoint, ClimbController.PointType.PB.getColor(), zoomPadding, y0);
                    }
                }
            }
        }
    }

    private void plotPoints(List<PlotPoint> pts, int y0, int maxY, Canvas canvas, int padding) {
        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);

        for (int i=0; i<pts.size()-1; i++) {
            Path path = new Path();
            path.moveTo(pts.get(i).getX()+padding, pts.get(i).getY()+y0);
            path.lineTo(pts.get(i+1).getX()+padding, pts.get(i+1).getY()+y0);
            path.lineTo(pts.get(i+1).getX()+padding, maxY+y0);
            path.lineTo(pts.get(i).getX()+padding, maxY+y0);
            path.lineTo(pts.get(i).getX()+padding, pts.get(i).getY()+y0);
            p.setColor(Palette.getColour(pts.get(i+1).getGradient()));
            canvas.drawPath(path, p);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(3);
            canvas.drawLine(pts.get(i).getX()+padding, pts.get(i).getY()+y0,
                    pts.get(i+1).getX()+padding, pts.get(i+1).getY()+y0, p);
        }
    }

    private void plotGradientLine(List<PlotPoint> pts, int y0, int maxY, Canvas canvas, int padding) {
        int index = getNearestIndex(pts, showGradientAt, padding);
        if (index < 0) {
            return;
        }

        Paint p = new Paint();
        p.setStrokeWidth(2.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setColor(Color.GRAY);
        p.setTextSize(48.0f);

        float gradient = pts.get(index).getGradient();
        double distance = pts.get(index).getX() / scaleFacX.get(PlotType.FULL);
        float elevation = pts.get(index).getElevation();
        DecimalFormat df1 = new DecimalFormat();
        df1.setMaximumFractionDigits(1);
        DecimalFormat df0 = new DecimalFormat();
        df0.setMaximumFractionDigits(0);
        String gradientText = df1.format(gradient) + "%/" + df0.format(elevation) + "m";
        String distanceText = df1.format(distance/1000) + "km";
        // Work out text size and draw on canvas
        Rect textBounds = new Rect();
        p.getTextBounds(gradientText, 0, gradientText.length(), textBounds);
        canvas.drawLine(showGradientAt, y0 + textBounds.height() + 5, showGradientAt, maxY, p);
        p.setColor(Color.BLACK);
        canvas.drawText(gradientText, calcTextPos(showGradientAt, textBounds), y0 + textBounds.height(), p);
        p.getTextBounds(distanceText, 0, distanceText.length(), textBounds);
        canvas.drawText(distanceText, calcTextPos(showGradientAt, textBounds), maxY + (leaveGap ? textBounds.height() + 5 : - 2), p);
    }

    private int calcTextPos(int x, Rect textBounds) {
        int pos = x - (textBounds.width()/2);
        if (pos < 0) {
            pos = 0;
        } else {
            int screenWidth = getScreenSize().x;
            if (pos + (textBounds.width()) > screenWidth) {
                pos = screenWidth - textBounds.width();
            }
        }
        return pos;
    }

    private int getNearestIndex(List<PlotPoint> pts, int x, int padding) {
        // Finds the plot point that is nearest to x from the start
        if (x < padding || x > pts.get(pts.size()-1).getX() + padding) {
            return -1;
        }
        for (int i=0; i<pts.size()-1; i++) {
            if (pts.get(i).getX() + padding <= x &&
                    pts.get(i+1).getX() + padding > x) {
                return i+1;
            }
        }

        return pts.size()-1;
    }

    private Point getScreenSize() {
        // Find screen width
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    private void drawTracker(Canvas canvas, PlotPoint pt, int colour, int padding, int y0) {
        if (pt == null) return;

        Bitmap bmp = getBitmap((VectorDrawable) AppCompatResources.getDrawable(ApplicationContextProvider.getContext(), R.drawable.ic_map_marker_solid), Color.BLACK);
        Bitmap bike = getBitmap((VectorDrawable) AppCompatResources.getDrawable(ApplicationContextProvider.getContext(), R.drawable.ic_biking_solid), colour);

        int x = (int)pt.getX() + padding;
        int y = (int)pt.getY() + y0;

        Paint p = new Paint();
        if (leaveGap) {
            canvas.drawBitmap(bmp, null, new Rect(x - 40, y - 120, x + 40, y), p);
            canvas.drawBitmap(bike, null, new Rect(x - 30, y - 100, x + 30, y - 60), p);
        } else {
            // Smaller marker where we have reduced space
            canvas.drawBitmap(bmp, null, new Rect(x - 20, y - 60, x + 20, y), p);
            canvas.drawBitmap(bike, null, new Rect(x - 15, y - 50, x + 15, y - 30), p);
        }
    }

    private int findPointBefore(PlotPoint pt) {
        // Start is 0 if not past second point
        if (points.get(0).getX() > pt.getX() || points.get(1).getX() > pt.getX()) {
            return 0;
        }

        int idx = 0;
        for (PlotPoint p : points) {
            if (p.getX() > pt.getX()) {
                if (idx >= 2) {
                    return idx - 2;
                } else {
                    return 0;
                }
            }
            idx++;
        }
        return 0;
    }

    private int adjustStart(PlotPoint pt, int start, float endX, int targetDist) {
        for (int i=start; i>=0; i--) {
            double dist = (endX - points.get(i).getX()) / scaleFacX.get(PlotType.FULL);
            if (dist >= targetDist) {
                return i;
            }
        }
        return 0;
    }

    private int calcYRange(int start, int end) {
        float mnY = Integer.MAX_VALUE;
        float mxY = Integer.MIN_VALUE;

        for (int i=start; i<=end; i++) {
            if (points.get(i).getY() < mnY) mnY = points.get(i).getY();
            if (points.get(i).getY() > mxY) mxY = points.get(i).getY();
        }

        return (int)(mxY - mnY);
    }

    private List<PlotPoint> getClimbPoints(int startIdx, int targetDist, boolean smooth) {
        float distFromLast = 0;
        float totalDist = 0;
        boolean first = true;
        float lastX = 0;
        double lastElevation = 0;
        int lastIndex = 0;
        List<PlotPoint> points = new ArrayList<>();

        // Determine cumulative delta to all points
        for (int i = startIdx; i< profile.getPoints().size(); i++) {
            RoutePoint pt = profile.getPoints().get(i);
            PlotPoint p = new PlotPoint();
            p.setLocation(new LatLng(pt.getLat(), pt.getLon()));

            if (first) {
                first = false;
                p.setX(profile.getPoints().get(i).getDistFromStart());
                p.setElevation((float)pt.getElevation());
                p.setGradient(0);
                lastIndex = startIdx;
            } else {
                distFromLast += (float)calcDelta(pt, profile.getPoints().get(lastIndex).getEasting(), profile.getPoints().get(lastIndex).getNorthing());
                lastIndex++;

                if (smooth && distFromLast < SMOOTH_DIST) continue;

                p.setX(distFromLast + lastX);
                p.setElevation((float) pt.getElevation());
                p.setGradient((float) calcGradient(p.getX() - lastX, p.getElevation() - lastElevation));
                distFromLast = 0;
            }

            points.add(p);
            lastX = p.getX();
            lastElevation = p.getElevation();
            totalDist = p.getX();

            if (totalDist - points.get(0).getX() >= targetDist) {
                break;
            }
        }

        return points;
    }

    private double convertToPlotPoints(List<PlotPoint> points, int xSize, int ySize, float multiplier, PlotType type) {
        double xDist = points.get(points.size()-1).getX() - points.get(0).getX();
        double minElevation = Double.MAX_VALUE;
        double maxElevation = Double.MIN_VALUE;

        // Determine cumulative delta to all points
        for (PlotPoint pt : points) {
            if (pt.getElevation() < minElevation) {
                minElevation = pt.getElevation();
            }
            if (pt.getElevation() > maxElevation) {
                maxElevation = pt.getElevation();
            }
        }

        double scaleX = (double)xSize / xDist;

        // Work out y range
        Log.d(TAG, "Y RANGE: " + (maxElevation - minElevation));
        double yRange = adjustYRange(ySize - TOP_MARGIN, maxElevation - minElevation) * multiplier;

        if (!leaveGap) {
            // Amend height if no gap is being left around the profile
            ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
            layoutParams.height = this.height = ySize = (int) yRange + TOP_MARGIN;
            this.setLayoutParams(layoutParams);
        }

        double scaleFacY = yRange / (maxElevation - minElevation);
        int topY = (int)((ySize - yRange) / 2);

        Log.d(TAG, "Adjust - Screen width: " + xSize + "; xScale: " + scaleX +
                "; Y range: " + yRange + "; yScale: " + scaleFacY);
        Log.d(TAG, "Plotting y between " + 0 + " and " + ((maxElevation-minElevation)*scaleFacY));
        float xStart = points.get(0).getX();

        for (PlotPoint p : points) {
            p.setX((float)((p.getX() - xStart) * scaleX));
            p.setY((float)((maxElevation - p.getElevation()) * scaleFacY) + topY);
        }

        scaleFacX.put(type, scaleX);
        return yRange;
    }

    private double adjustYRange(double yRange, double elevationRange) {
        if (elevationRange < 150) {
            return yRange * 0.2;
        } else if (elevationRange < 300) {
            return yRange * 0.4;
        } else if (elevationRange < 500) {
            return yRange * 0.6;
        } else if (elevationRange < 800) {
            return yRange * 0.8;
        }
        return yRange;
    }

    private double calcDelta(RoutePoint pt, double e, double n) {
        return Math.sqrt(Math.pow(e - pt.getEasting(), 2.0) + Math.pow(n - pt.getNorthing(), 2.0));
    }

    private double calcGradient(double dist, double elevDiff) {
        return 100 * (elevDiff/dist);
    }

    public void startUpdating() {
        startUpdating = true;
    }


    private PlotPoint calcPlotXY(List<PlotPoint> plotPoints, PlotType type, float x0, float xDist) {
        int xIndex = Integer.MIN_VALUE;
        PlotPoint plotXY = new PlotPoint();

        xDist *= scaleFacX.get(type);

        if (plotPoints.get(0).getX() > xDist) {
            // Point is not on the plot
            plotXY.setX(-1);
            return plotXY;
        }

        // Find which points the xDist is between
        for (int i=0; i<plotPoints.size(); i++) {
            if (plotPoints.get(i).getX() > xDist) {
                xIndex = i;
                break;
            }
        }

        if (xIndex < plotPoints.size() && xIndex > 0) {
            // Found a location on the plot
            double y1 = plotPoints.get(xIndex-1).getY();
            double y2 = plotPoints.get(xIndex).getY();
            double xDiff = plotPoints.get(xIndex).getX() - plotPoints.get(xIndex-1).getX();
            double yPlot = y1 + ((y2 - y1) * ((xDist - plotPoints.get(xIndex-1).getX())/xDiff));
            plotXY.setX(xDist);
            plotXY.setY((float)yPlot);
        } else if (xIndex == 0) {
            plotXY.setX(points.get(0).getX());
            plotXY.setY(points.get(0).getY());
        } else {
            plotXY.setX(points.get(points.size() - 1).getX());
            plotXY.setY(points.get(points.size() - 1).getY());
        }

        return plotXY;
    }

    private static Bitmap getBitmap(VectorDrawable vectorDrawable, int color) {
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.setTint(color);
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    private BitmapDescriptor bitmapDescriptorFromVector(Context context, int vectorResId, int colour) {
        float scaleFac = 1;
        int right = (int)(77 * scaleFac);
        int bottom = (int)(102 * scaleFac);
        Drawable background = ContextCompat.getDrawable(context, R.drawable.ic_map_marker_solid);
        background.setBounds(0, 0, right, bottom);
        background.setTint(Color.GREEN);
        Drawable vectorDrawable = ContextCompat.getDrawable(context, vectorResId);
        vectorDrawable.setBounds((int)(10 * scaleFac), (int)(8 * scaleFac), (int)(60 * scaleFac), (int)(50 * scaleFac));
        vectorDrawable.setTint(colour);
        Bitmap bitmap = Bitmap.createBitmap(right, bottom, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        background.draw(canvas);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public boolean isInitialised() {
        return initialised;
    }

    public LatLng getLatLongAtX(int x) {
        int index = getNearestIndex(points, x, PADDING);

        if (index < 0) {
            return null;
        }
        return points.get(index).getLocation();
    }

    public void setTransparency(int transparency) {
        this.transparency = transparency;
    }
}
