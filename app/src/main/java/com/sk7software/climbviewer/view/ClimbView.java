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

    // Profile to plot
    private GPXRoute profile;

    // Points on plot
    // Initially this contains geographic points but the x and y get converted from E and N to screen locations
    private List<PlotPoint> points;
    private boolean zoomView;

    // Screen pixels between min and max elevation of the plot
    private double yRange;

    // Y location of the top of the profile
    private int profileMaxY;

    // Scale factor from metres to pixels in x direction
    private double scaleFacX;

    // View height and boolean indicating whether it has been calculated
    private int height;
    private boolean heightSet;
    private boolean initialised;
    private boolean startUpdating;

    // Plots that are represented on this view (e.g. ATTEMPT, PB)
    private Set<ClimbController.PointType> plots;

    // X position where the gradient value needs to be displayed
    private int showGradientAt;

    private int transparency = 0xFF;

    // Stores the profile image when it has been calculated the first time
    private Bitmap profileBitmap = null;

    // Additional fields for zoom
    private int x0 = 0;
    private int x1 = 0;
    private boolean findingClimb = false;
    private double fixedYRange = 0;
    private float zoomFac = 1;
    private int trackStartIndex;
    private int trackEndIndex;
    private int x0Fine;
    private int x1Fine;

    private static final String TAG = ClimbView.class.getSimpleName();
    private static final int PADDING = 20;
    private static final int SMOOTH_DIST = 10;
    private static final int ZOOM_DIST = 500;
    private static final float ZOOM_MULTIPLIER = 2.5f;
    private static final int TOP_MARGIN = 160;
    private static final float ZOOM_WIDTH = 0.75f;

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

    public void setClimb(GPXRoute profile) {
        this.profile = profile;
        this.heightSet = false;
        this.initialised = false;
        this.plots = new HashSet<>();
        this.showGradientAt = -1;
        this.startUpdating = false;
    }

    public void addPlot(ClimbController.PointType p) {
        plots.add(p);
    }

    public void setHeight(int height, boolean zoomView) {
        if (height > 0) {
            this.height = height;
            ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
            layoutParams.height = height;
            this.setLayoutParams(layoutParams);
        } else {
            this.height = 150;
        }
        this.heightSet = true;
        this.zoomView = zoomView;
    }

    public void setShowGradientAt(int x) {
        this.showGradientAt = x;
        this.startUpdating = true;
    }

    public void setX0(int x0) {
        this.x0 = x0;
        this.x1 = x0 + 5;

        if (!this.zoomView) {
            this.findingClimb = true;
        }
    }

    public void setX1(int x1) {
        if (x1 < this.x0 + 5) {
            this.x1 = x0 + 5;
        } else {
            this.x1 = x1;
        }
    }

    public void setX0Fine(int x0) {
        this.x0Fine = x0;
        this.x1Fine = x0 + 5;
        this.findingClimb = true;
    }

    public void setX1Fine(int x1) {
        if (x1 < this.x0Fine + 5) {
            this.x1Fine = x0Fine + 5;
        } else {
            this.x1Fine = x1;
        }
    }

    public void setZoomFac(float zoomFac) {
        this.zoomFac = zoomFac;
    }

    public void setFixedYRange(double yRange) {
        this.fixedYRange = yRange;
    }

    public double getFixedYRange() {
        return fixedYRange;
    }

    public int getTrackStartIndex() {
        return trackStartIndex;
    }

    public int getTrackEndIndex() {
        return trackEndIndex;
    }

    public void clearPoints() {
        this.points = null;
        this.profileBitmap = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!heightSet) return;

        int viewHeight = height;
        Paint p = new Paint();
        p.setAlpha(transparency);

        Point size = getScreenSize();

        if (profileBitmap == null) {
            // Plot points onto bitmap first, then copy that to the display canvas
            profileBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(profileBitmap);

            int screenWidth = size.x - (2 * PADDING); // Add some padding for display

            if (zoomView) {
                // Adjust width to fill smaller fraction of the screen
                screenWidth = (int)((float)screenWidth * ZOOM_WIDTH);
            }

            if (points == null) {
                // Fetch the climb points and smooth if we are not zooming
                points = getClimbPoints(0, Integer.MAX_VALUE, !zoomView);
                yRange = convertToPlotPoints(points, screenWidth, viewHeight);

                // Height might have changed
                viewHeight = height;
            }

            plotPoints(points, bitmapCanvas, PADDING);
        }

        canvas.drawBitmap(profileBitmap, 0, 0, p);
        plotGradientLine(points, canvas, PADDING);
        plotPositions(canvas);

        if (findingClimb) {
            if (!zoomView) {
                markClimb(canvas, x0, x1, 0, PADDING);
            } else {
                int padding = (int)((size.x - (PADDING*2)) * ZOOM_WIDTH / 2);
                markClimb(canvas, x0Fine, x1Fine, 0, padding);
            }
        }

        initialised = true;
    }

    /**
     * Adds rider and PB markers to profile plot
     * @param canvas
     * @param y0
     */
    private void plotPositions(Canvas canvas) {
        for (ClimbController.PointType p : plots) {
            PlotPoint pt = calcPlotXY(points, PlotType.FULL, PADDING, ClimbController.getInstance().getAttempts().get(p).getDist());
            drawTracker(canvas, pt, p.getColor(), PADDING);
        }
    }

    private void markClimb(Canvas canvas, int xStart, int xEnd, int y0, int padding) {
        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setColor(0x44555555);
        canvas.drawRect(xStart, y0, xEnd, height, p);
    }

    private void showZoomProfile(Canvas canvas, int y0, Point size) {
        List<PlotPoint> zoomPoints = new ArrayList<>();
        int zoomPadding = 3;

        float attemptDist = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist();
        PlotPoint attPoint = calcPlotXY(points, PlotType.FULL, zoomPadding, attemptDist);

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
            y0 = 0;
            int zoomWidth = this.getMeasuredWidth(); //size.x / 2;

            // Draw box round zoom area
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(5);

            float startX = zoomPoints.get(0).getX();
            float distOnZoom = attemptDist - startX;
//            double zoomYRange = convertToPlotPoints(zoomPoints, zoomWidth, (3 * height / 8) - 100, ZOOM_MULTIPLIER, PlotType.ZOOM);
            double zoomYRange = convertToPlotPoints(zoomPoints, zoomWidth, height - 30);
            zoomYRange += TOP_MARGIN - 30;
            int maxZoomY = (int) (zoomYRange + TOP_MARGIN + ((height - 30 - yRange) / 2));
//            int zoomPadding = (int) (size.x - zoomPoints.get(zoomPoints.size() - 1).getX()) / 2;
//            canvas.drawRoundRect(zoomPadding - 10, y0 - 20, zoomPadding + zoomWidth + 10, maxZoomY + y0 + 10, 20, 20, p);
//            canvas.drawLine(attPoint.getX() + PADDING, attPoint.getY(), zoomPadding, y0 - 20, p);
//            canvas.drawLine(attPoint.getX() + PADDING, attPoint.getY(), zoomPadding + zoomWidth, y0 - 20, p);
            plotPoints(zoomPoints, canvas, zoomPadding);

            PlotPoint zoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distOnZoom);
            drawTracker(canvas, zoomPoint, ClimbController.PointType.ATTEMPT.getColor(), zoomPadding);

            AttemptData pbData = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB);
            if (pbData != null) {
                float pbDist = pbData.getDist();
                float distPBOnZoom = pbDist - startX;
                Log.d(TAG, "ZOOM: " + startX + ", " + zoomWidth + "; PB dist: " + pbDist + " [" + distPBOnZoom + "]");
                if (distPBOnZoom > 0) {
                    PlotPoint pbZoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distPBOnZoom);

                    if (pbZoomPoint.getX() > 0 && pbZoomPoint.getX() < zoomWidth) {
                        drawTracker(canvas, pbZoomPoint, ClimbController.PointType.PB.getColor(), zoomPadding);
                    }
                }
            }
        }
    }

    /**
     * Plots each elevation point in the relevant colour on the profile
     * @param pts
     * @param y0
     * @param maxY
     * @param canvas
     * @param padding
     */
    private void plotPoints(List<PlotPoint> pts, Canvas canvas, int padding) {
        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);

        for (int i=0; i<pts.size()-1; i++) {
            if (pts.get(i).getX() < 0 || pts.get(i+1).getX() < 0) continue;

            Path path = new Path();
            path.moveTo(pts.get(i).getX()+padding, pts.get(i).getY());
            path.lineTo(pts.get(i+1).getX()+padding, pts.get(i+1).getY());
            path.lineTo(pts.get(i+1).getX()+padding, height);
            path.lineTo(pts.get(i).getX()+padding, height);
            path.lineTo(pts.get(i).getX()+padding, pts.get(i).getY());
            p.setColor(Palette.getColour(pts.get(i+1).getGradient()));
            canvas.drawPath(path, p);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(3);
            canvas.drawLine(pts.get(i).getX()+padding, pts.get(i).getY(),
                    pts.get(i+1).getX()+padding, pts.get(i+1).getY(), p);
        }
    }

    /**
     * Gives gradient, elevation and distance at the point on the plot
     * @param pts
     * @param y0
     * @param maxY
     * @param canvas
     * @param padding
     */
    private void plotGradientLine(List<PlotPoint> pts, Canvas canvas, int padding) {
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
        double distance = pts.get(index).getX() / scaleFacX;
        float elevation = pts.get(index).getElevation();

        if (findingClimb) {
            // Amend to climb distance, elevation gain and average gradient
            int x0Index = getNearestIndex(pts, x0, padding);
            distance -= pts.get(x0Index).getX() / scaleFacX;
            elevation -= pts.get(x0Index).getElevation();
            gradient = (float)(elevation * 100 / distance);
        }

        DecimalFormat df1 = new DecimalFormat();
        df1.setMaximumFractionDigits(1);
        DecimalFormat df0 = new DecimalFormat();
        df0.setMaximumFractionDigits(0);
        String gradientText = df1.format(gradient) + "%/" + df0.format(elevation) + "m";
        String distanceText = df1.format(distance/1000) + "km";
        // Work out text size and draw on canvas
        Rect textBounds = new Rect();
        p.getTextBounds(gradientText, 0, gradientText.length(), textBounds);
        canvas.drawLine(showGradientAt, textBounds.height() + 5, showGradientAt, height, p);
        p.setColor(Color.BLACK);
        canvas.drawText(gradientText, calcTextPos(showGradientAt, textBounds), textBounds.height(), p);
        p.getTextBounds(distanceText, 0, distanceText.length(), textBounds);
        canvas.drawText(distanceText, calcTextPos(showGradientAt, textBounds), height-5, p);
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

    /**
     * Finds the nearest index to the point x on the plot
     * @param pts
     * @param x
     * @param padding
     * @return
     */
    private int getNearestIndex(List<PlotPoint> pts, int x, int padding) {
        // Finds the plot point that is nearest to x from the start
        if (x < padding || x > pts.get(pts.size()-1).getX() + padding) {
            return -1;
        }
        for (int i=0; i<pts.size()-1; i++) {
            if (pts.get(i).getX() + padding <= x && pts.get(i+1).getX() + padding > x) {
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

    private void drawTracker(Canvas canvas, PlotPoint pt, int colour, int padding) {
        if (pt == null) return;

        Bitmap bmp = getBitmap((VectorDrawable) AppCompatResources.getDrawable(ApplicationContextProvider.getContext(), R.drawable.ic_map_marker_solid), Color.BLACK);
        Bitmap bike = getBitmap((VectorDrawable) AppCompatResources.getDrawable(ApplicationContextProvider.getContext(), R.drawable.ic_biking_solid), colour);

        int x = (int)pt.getX() + padding;
        int y = (int)pt.getY();

        Paint p = new Paint();
//        if (leaveGap) {
//            canvas.drawBitmap(bmp, null, new Rect(x - 40, y - 120, x + 40, y), p);
//            canvas.drawBitmap(bike, null, new Rect(x - 30, y - 100, x + 30, y - 60), p);
//        } else {
            // Smaller marker where we have reduced space
            canvas.drawBitmap(bmp, null, new Rect(x - 30, y - 80, x + 30, y), p);
            canvas.drawBitmap(bike, null, new Rect(x - 20, y - 66, x + 20, y - 38), p);
//        }
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
            double dist = (endX - points.get(i).getX()) / scaleFacX;
            if (dist >= targetDist) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Populates the profile points from the climb stored in the database
     * @param startIdx - start at this point of the profile, rather than 0
     * @param targetDist - only load up to this distance from start
     * @param smooth - applies smoothing to the retrieved points
     * @return
     */
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

    private double convertToPlotPoints(List<PlotPoint> points, int xSize, int ySize) {
        double minElevation = Double.MAX_VALUE;
        double maxElevation = Double.MIN_VALUE;

        // Determine min and max elevation in the profile
        for (PlotPoint pt : points) {
            if (pt.getElevation() < minElevation) {
                minElevation = pt.getElevation();
            }
            if (pt.getElevation() > maxElevation) {
                maxElevation = pt.getElevation();
            }
        }

        // Get distance in metres that the plot covers and calculate the scale to fit this across screen
        double xDist = (points.get(points.size()-1).getX() - points.get(0).getX());
        double scaleX = (double)xSize / xDist;

        // Work out y range based on height of the view and overall elevation profile
        // The full height is not always used so that gentle profiles are not stretched to look extreme
        double yRange = adjustYRange(ySize - TOP_MARGIN, maxElevation - minElevation);

        // Set fixed y range so it can be used by any zoom views based on it
        if (!zoomView) {
            fixedYRange = yRange;
        } else {
            yRange = fixedYRange;
        }

        // Amend height if no gap is being left around the profile
        // TODO: check if this is still needed
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = this.height = ySize = (int) yRange + TOP_MARGIN;
        this.setLayoutParams(layoutParams);

        // Calculate the scale factor for elevation points and where the top point should be plotted
        // The top point allows for a bit of extra plot at the bottom so that the profile is not
        // right at the bottom of the screen (unless minimum elevation actually is 0)
        // Elevations will be plotted from topY down to height (0,0 is top left of view)
        double scaleY = yRange / (maxElevation - minElevation);
        int topY = minElevation <= 0 ? TOP_MARGIN : TOP_MARGIN / 2;

        Log.d(TAG, "Adjust - Screen width: " + xSize + "; xScale: " + scaleX +
                "; Y range: " + yRange + "; yScale: " + scaleY);
        Log.d(TAG, "Plotting y between " + 0 + " and " + ((maxElevation-minElevation)*scaleY));
        float xStart = points.get(0).getX();

        for (PlotPoint p : points) {
            p.setX((float)((p.getX() - xStart) * scaleX));
            p.setY((float)((maxElevation - p.getElevation()) * scaleY) + topY);
        }

        if (zoomView) {
            // Adjust for zoom
            yRange = zoomAdjust(points, xSize, ySize, maxElevation, topY, scaleY);
        } else {
            scaleFacX = scaleX;
        }

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

    private double zoomAdjust(List<PlotPoint> points, int xSize, int ySize, double maxElevation, int topY, double scaleY) {
        // Work out what x0 relates to on the compressed zoom width
        Point screenSize = getScreenSize();
        float adjustFac = (float)xSize/((float)screenSize.x - (PADDING*2));
        int adjustedX0 = (int)((float)(x0 - PADDING) * adjustFac);
        int adjustedX1 = (int)((float)(x1 - PADDING) * adjustFac);

        trackStartIndex = getNearestIndex(points, adjustedX0, 0);
        trackEndIndex = getNearestIndex(points, adjustedX1, 0);

        Log.d(TAG, "x0: " + adjustedX0 + ", x1: " + x1 + " [" + xSize + "]");
        Log.d(TAG, "Start: " + trackStartIndex + ", End: " + trackEndIndex + " [" + points.size() + "]");
        Log.d(TAG, "Points x: " + points.get(0).getX() + ", " + points.get(points.size()-1).getX());
        if (trackStartIndex == -1 || trackEndIndex == -1) {
            return 1;
        }

        // Get distance in metres that the plot covers and calculate the scale to fit this across screen
        double xDist = (points.get(trackEndIndex).getX() - points.get(trackStartIndex).getX());
        double scaleX = (double)xSize / xDist;

        // Work out y range based on height of the view and overall elevation profile
        // The full height is not always used so that gentle profiles are not stretched to look extreme
        double yRange = fixedYRange;
        Log.d(TAG, "yRange: " + yRange + ", xDist: " + xDist);

        // Amend height if no gap is being left around the profile
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = this.height = ySize = (int) yRange + TOP_MARGIN;
        this.setLayoutParams(layoutParams);

        Log.d(TAG, "Adjust - Screen width: " + xSize + "; xScale: " + scaleX +
                "; Y range: " + yRange + "; yScale: " + scaleY);
        float xStart = points.get(trackStartIndex).getX();

        // Compress into the zoom size
        int xPad = ((screenSize.x - xSize - (PADDING*2)) / 2);

        for (int i=0; i<points.size(); i++) {
            PlotPoint p = points.get(i);
            if (i < trackStartIndex || i > trackEndIndex) {
                p.setX(-1);
                continue;
            }

            p.setX((float)((p.getX() - xStart) * scaleX) + xPad);

            if (p.getX() > xSize + xPad) {
                p.setX(-1);
            }
        }

        scaleFacX = scaleX;
        return yRange;
    }

    /**
     * Interpolates an x, y view location given a horizontal distance (in metres)
     * @param plotPoints
     * @param type
     * @param x0
     * @param xDist
     * @return
     */
    private PlotPoint calcPlotXY(List<PlotPoint> plotPoints, PlotType type, float x0, float xDist) {
        int xIndex = Integer.MIN_VALUE;
        PlotPoint plotXY = new PlotPoint();

        // Convert distance to pixel value
        xDist *= scaleFacX;

        // Check if it is past the maximum of the plot
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

    // Creates a bitmap from a vector, applying the specified colour tint
    // TODO: Merge with the below method
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

    /**
     * Works out the lat/long at an x position on the plot so that it can be shown on a map
     * @param x
     * @return
     */
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

    public void fixClimbPoint(int x) {
        if (x < 0) return;

        int index = getNearestIndex(points, x, PADDING);
        double distance = points.get(index).getX() / scaleFacX;
        Log.d(TAG, "Distance from start: " + distance);
        int startIdx = 0;
        for (RoutePoint p : profile.getPoints()) {
            if (p.getDistFromStart() > distance) {
                break;
            }
            startIdx++;
        }

        if (startIdx > 0) {
            startIdx--;
        }

    }

    private void showClimbFindProfile(Canvas canvas, int x, int y0, Point size, boolean isStart) {
        if (x < 0) return;
        Log.d(TAG, "Zoom around x: " + x);
        int index = getNearestIndex(points, x, PADDING);
        double distance = points.get(index).getX() / scaleFacX;
        Log.d(TAG, "Distance from start: " + distance);
        int startIdx = 0;
        for (RoutePoint p : profile.getPoints()) {
            if (p.getDistFromStart() > distance) {
                break;
            }
            startIdx++;
        }

        if (startIdx > 0) {
            startIdx--;
        }

        List<PlotPoint> zoomPoints = new ArrayList<>();

        if (startIdx < 0) startIdx = 0;
        Log.d(TAG, "Zoom point start: " + startIdx + " [" + points.size() + "]");
        zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);

        // Push back start if too short
//        if ((zoomPoints.get(zoomPoints.size() - 1).getX() - zoomPoints.get(0).getX()) < ZOOM_DIST) {
//            startIdx = adjustStart(climbPoint, startIdx, zoomPoints.get(zoomPoints.size() - 1).getX(), ZOOM_DIST);
//            zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);
//        }

        Log.d(TAG, "ZOOM START/END X: " + zoomPoints.get(0).getX() + "," + zoomPoints.get(zoomPoints.size() - 1).getX());

        if (!zoomPoints.isEmpty()) {
            y0 = height / 2;
            int zoomWidth = size.x / 2;

            // Draw box round zoom area
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(5);

            float startX = zoomPoints.get(0).getX();
            float distOnZoom = (float)distance - startX;
            double zoomYRange = convertToPlotPoints(zoomPoints, zoomWidth, (3 * height / 8) - 100);
            int maxZoomY = (int) (zoomYRange + TOP_MARGIN + ((((3 * height) / 8) - 100 - yRange) / 2));
            int zoomPadding = (int) (size.x - zoomPoints.get(zoomPoints.size() - 1).getX()) / 2;
            canvas.drawRoundRect(zoomPadding - 10, y0 - 20, zoomPadding + zoomWidth + 10, maxZoomY + y0 + 10, 20, 20, p);
            plotPoints(zoomPoints, canvas, zoomPadding);

            PlotPoint zoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distOnZoom);
        }
    }
}
