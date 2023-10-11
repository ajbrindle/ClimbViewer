package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.VectorDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.gms.common.util.Strings;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.DrawableUpdateInterface;
import com.sk7software.climbviewer.LocationMonitor;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.DirectionChecker;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

public class ClimbView extends View {

    // Profile to plot
    private GPXRoute profile;
    private int climbRating;

    // Pixel positions of left and right of the view
    private int xLeft;
    private int xRight;

    // Index position of left of the profile and the distance the profile represents
    private int startIndex;
    private double trackDist;

    // x screen positions where profile has been marked in full view and the plot indexes for them
    private int x0;
    private int xN;
    private int x0Index;
    private int xNIndex;

    // Points on plot
    // Initially this contains geographic points but the x and y get converted from E and N to screen locations
    private List<PlotPoint> points;
    private boolean zoomView;
    private double maxSmoothedGradient;

    // Scale factor from metres to pixels in x direction
    private double scaleFacX;

    // View height and boolean indicating whether it has been calculated
    private int height;
    private boolean heightSet;
    private boolean initialised;
    private String showClimbsList;
    private List<ClimbCoords> climbCoords;

    // Plots that are represented on this view (e.g. ATTEMPT, PB)
    private Set<ClimbController.PointType> plots;

    // X position where the gradient value needs to be displayed
    private int showGradientAt;
    private boolean showInline;

    // Stores the profile image when it has been calculated the first time
    private Bitmap profileBitmap = null;
    private int transparency = 0xFF;

    // Additional fields for zoom
    private boolean findingClimb = false;
    private double fixedYRange = 0;

    // Parent activity
    DrawableUpdateInterface parent;

    private static final String TAG = ClimbView.class.getSimpleName();
    private static final int DEFAULT_SMOOTH_DIST = 50;
    private static final int TOP_MARGIN = 160;

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

    public void setClimb(GPXRoute profile, int padding) {
        // Set profile
        this.profile = profile;
        this.climbCoords = null;

        // Set view left and right
        Point screen = ScreenController.getScreenSize();
        xLeft = padding;
        xRight = screen.x - padding;

        // Assume whole profile is being plotted
        startIndex = 0;
        trackDist = profile.getPoints().get(profile.getPoints().size()-1).getDistFromStart();

        // Initialise
        this.heightSet = false;
        this.initialised = false;
        this.plots = new HashSet<>();
        this.showGradientAt = -1;
    }

    public void setParent(DrawableUpdateInterface activity) {
        this.parent = activity;
    }

    public void addPlot(ClimbController.PointType p) {
        plots.add(p);
    }

    public void setHeight(int height, boolean zoomView) {
        // Store height
        this.height = height;
        this.heightSet = true;
        this.zoomView = zoomView;
    }

    public void setShowGradientAt(int x) {
        // Sets the x screen location of where the gradient information line should be drawn
        showGradientAt = x;

        if (showGradientAt < 0) {
            // Switched off gradient line
            return;
        }

        // Limit to left and right extremes
        if (showGradientAt < xLeft) {
            showGradientAt = xLeft;
        } else if (showGradientAt > xRight) {
            showGradientAt = xRight;
        }
    }

    public void setX0(int x0) {
        // x0 is the left edge of the section that will be marked
        if (x0 < xLeft) {
            x0 = xLeft;
        }

        this.x0 = x0;
        this.findingClimb = true;
        this.x0Index = getNearestIndex(points, this.x0);
        setXN(x0 + 5);
    }

    public void nudgeX0(boolean right) {
        if (right) {
            if (x0Index < xNIndex - 1) {
                x0Index++;
            }
        } else {
            if (x0Index > 0) {
                x0Index--;
            }
        }
        x0 = (int) points.get(x0Index).getX();
    }

    public void setXN(int xN) {
        // xN is the right edge of the section that will be marked
        if (xN < this.x0 + 5) {
            this.xN = x0 + 5;
        } else {
            this.xN = xN;
        }
        showGradientAt = this.xN;
        this.xNIndex = getNearestIndex(points, this.xN);
    }

    public void nudgeXN(boolean right) {
        if (right) {
            if (xNIndex < points.size()-2) {
                xNIndex++;
            }
        } else {
            if (xNIndex > x0Index + 1) {
                xNIndex--;
            }
        }
        xN = (int) points.get(xNIndex).getX();
        setShowGradientAt(this.xN);
    }

    public void autoX0XN() {
        double minElevation = Double.MAX_VALUE;
        double maxElevation = Double.MIN_VALUE;
        int minIdx = -1;
        int maxIdx = -1;

        // Find max elevation in the section
        for (int i=0; i<points.size(); i++) {
            PlotPoint p = points.get(i);
            if (p.getElevation() > maxElevation) {
                maxElevation = p.getElevation();
                maxIdx = i;
            }
        }

        // Find min elevation before the max
        for (int i=0; i<maxIdx; i++) {
            PlotPoint p = points.get(i);
            if (p.getElevation() <= minElevation) {
                minElevation = p.getElevation();
                minIdx = i;
            }
        }

        Log.d(TAG, "Min Idx: " + minIdx + "; Max idx: " + maxIdx);
        if (minIdx > 0 && maxIdx > 0 && minIdx < maxIdx) {
            findingClimb = true;
            setX0((int)points.get(minIdx).getX());
            setXN((int)points.get(maxIdx).getX());
            Log.d(TAG, "x0: " + this.x0 + "; xN: " + this.xN);
        }
    }

    public double getDistanceAtX(int x) {
        // Determines the distance along the profile that x0 occurs
        int index = getNearestIndex(points, x);
        if (index < 0) return 0;
        return (points.get(index).getX() - xLeft) / scaleFacX;
    }

    public void setStartIndex(double distance) {
        // Sets the start index for the zoom plot based on the distance where it will be shown from
        startIndex = findIndexAtDistance(distance);
    }

    public void setShowClimbsList(String ids, boolean showInline) {
        this.showClimbsList = ids;
        this.climbCoords = null;
        this.showInline = showInline;
    }

    public void setTrackDistance(double distance) {
        this.trackDist = distance - profile.getPoints().get(startIndex).getDistFromStart();
    }

    public double getMaxSmoothedGradient() {
        return maxSmoothedGradient;
    }

    public void clearClimbMarker() {
        x0 = xN = -1;
        findingClimb = false;
        showGradientAt = -1;
    }

    private int findIndexAtDistance(double distance) {
        int index = 0;
        for (RoutePoint p : profile.getPoints()) {
            if (p.getDistFromStart() < distance) {
                index++;
            } else {
                break;
            }
        }

        if (index >= profile.getPoints().size()) {
            index = profile.getPoints().size() - 1;
        }
        return index;
    }

    public List<LatLng> getTrackPoints() {
        if (points == null) return null;

        List<LatLng> pts = new ArrayList<>();
        for (PlotPoint p : points) {
            pts.add(p.getLocation());
        }
        return pts;
    }

    public List<LatLng> getMarkedPoints() {
        // Need points between x0 and xN
        List<LatLng> pts = new ArrayList<>();
        for (int i=this.x0Index; i<=this.xNIndex; i++) {
            pts.add(points.get(i).getLocation());
        }
        return pts;
    }

    public List<PlotPoint> getClimbPoints() {
        List<PlotPoint> climbPoints = new ArrayList<>();
        for (int i = this.x0Index; i <= this.xNIndex; i++) {
            climbPoints.add(points.get(i));
        }
        return climbPoints;
    }

    public void setFixedYRange(double yRange) {
        this.fixedYRange = yRange;
    }

    public double getFixedYRange() {
        return fixedYRange;
    }

    public void clearPoints() {
        this.points = null;
        this.profileBitmap = null;
    }

    public int getClimbRating() {
        return this.climbRating;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!heightSet) return;

        int viewHeight = height;
        Paint p = new Paint();
        p.setAlpha(transparency);

        if (profileBitmap == null) {
            // Plot points onto bitmap first, then copy that to the display canvas
            profileBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(profileBitmap);

            int viewWidth = xRight - xLeft;

            if (points == null) {
                // Fetch the climb points and smooth if we are not zooming
                points = getClimbPoints(startIndex, trackDist, !zoomView);
                convertToPlotPoints(points, viewWidth, viewHeight);

                // Height might have changed
                viewHeight = height;
            }

            plotPoints(points, bitmapCanvas);
        }

        canvas.drawBitmap(profileBitmap, 0, 0, p);
        plotGradientLine(points, canvas);
        plotPositions(canvas);

        if (findingClimb) {
            markClimb(canvas, x0, xN, 0);
        }

        drawClimbs(canvas);

        initialised = true;
        if (parent != null) {
            // TODO: Sort out how we can tell that the screen is ready
            parent.updateAfterDraw(zoomView && scaleFacX < Integer.MAX_VALUE);
        }
    }

    private void drawClimbs(Canvas canvas) {
        if (!Strings.isEmptyOrWhitespace(showClimbsList)) {
            // Show climbs on route
            List<String> ids = Arrays.asList(showClimbsList.split(","));
            if (climbCoords == null) {
                climbCoords = new ArrayList<>();
                for (String id : ids) {
                    // Get climb
                    int climbId = Integer.parseInt(id);
                    GPXRoute rt = Database.getInstance().getClimb(climbId);
                    List<Integer> indexes = findRouteIndexes(rt);

                    if (indexes == null) {
                        continue;
                    }

                    float xStart = -1;
                    float xEnd = -1;

                    for (int i = 0; i < points.size(); i++) {
                        PlotPoint p = points.get(i);
                        if (xStart < 0 && p.getProfileIndex() >= indexes.get(0)) {
                            xStart = p.getX();
                        }
                        if (xEnd < 0 && p.getProfileIndex() >= indexes.get(1)) {
                            xEnd = p.getX();
                        }
                    }

                    Log.d(TAG, "Climb range: " + xStart + "," + xEnd);

                    if (xStart >= 0 && xEnd >= 0) {
                        markClimb(canvas, (int) xStart, (int) xEnd, 0);
                        ClimbCoords coords = new ClimbCoords((int) xStart, (int) xEnd, climbId);
                        climbCoords.add(coords);
                    }
                }
            } else {
                for (ClimbCoords c : climbCoords) {
                    markClimb(canvas, c.getX0(), c.getXN(), 0);
                }
            }
        }
    }

    /**
     * Adds rider and PB markers to profile plot
     * @param canvas
     * @param y0
     */
    private void plotPositions(Canvas canvas) {
        for (ClimbController.PointType p : plots) {
            PlotPoint pt = calcPlotXY(points, ClimbController.getInstance().getAttempts().get(p).getDist());
            drawTracker(canvas, pt, p.getColor());
        }
    }

    private void markClimb(Canvas canvas, int xStart, int xEnd, int y0) {
        if (xStart < 0 || xEnd < 0) {
            return;
        }
        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setColor(0x44555555);
        canvas.drawRect(xStart, y0, xEnd, height, p);
    }

    /**
     * Plots each elevation point in the relevant colour on the profile
     * @param pts
     * @param canvas
     */
    private void plotPoints(List<PlotPoint> pts, Canvas canvas) {
        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);

        for (int i=0; i<pts.size()-1; i++) {
            if (pts.get(i).getX() < 0 || pts.get(i+1).getX() < 0) continue;

            Path path = new Path();
            path.moveTo(pts.get(i).getX(), pts.get(i).getY());
            path.lineTo(pts.get(i+1).getX(), pts.get(i+1).getY());
            path.lineTo(pts.get(i+1).getX(), height);
            path.lineTo(pts.get(i).getX(), height);
            path.lineTo(pts.get(i).getX(), pts.get(i).getY());
            p.setColor(Palette.getColour(pts.get(i).getGradient()));
            canvas.drawPath(path, p);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(3);
            canvas.drawLine(pts.get(i).getX(), pts.get(i).getY(),
                    pts.get(i+1).getX(), pts.get(i+1).getY(), p);
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
    private void plotGradientLine(List<PlotPoint> pts, Canvas canvas) {
        if (showGradientAt < 0) {
            return;
        }

        int index = getNearestIndex(pts, showGradientAt);
        if (index < 0) {
            return;
        }

        Paint p = new Paint();
        p.setStrokeWidth(2.0f);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setColor(Color.GRAY);
        p.setTextSize(48.0f);

        float gradient = pts.get(index).getGradient();
        double distance = (pts.get(index).getX() - xLeft) / scaleFacX;
        float elevation = interpolateElevation(pts, index, showGradientAt, false);
        float elevY = interpolateElevation(pts, index, showGradientAt, true);

        if (findingClimb) {
            // Amend to climb distance, elevation gain and average gradient
            distance -= (pts.get(x0Index).getX() - xLeft) / scaleFacX;
            elevation -= pts.get(x0Index).getElevation();

            if (distance > 0) {
                gradient = (float) (elevation * 100 / distance);
            } else {
                gradient = 0;
            }

            if (elevation > 0) {
                // Difficulty rating based on climbbybike formula (multipled by 100 to avoid messing with fractions)
                // https://www.climbbybike.com/climb_difficulty.asp
                float elevationAtTop = elevation + pts.get(x0Index).getElevation();
                float extra = (elevationAtTop > 1000 ? (elevationAtTop - 1000) / 100 : 0);
                climbRating = (int) ((2 * (elevation * 100.0 / distance) + (elevation * elevation / distance) +
                        (distance / 1000) + extra) * 100);
            } else {
                // Don't rate descents
                climbRating = 0;
            }
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
        canvas.drawCircle(showGradientAt, elevY, 10, p);
        p.setColor(Color.BLACK);
        canvas.drawText(gradientText, calcTextPos(showGradientAt, textBounds), textBounds.height(), p);
        p.getTextBounds(distanceText, 0, distanceText.length(), textBounds);
        canvas.drawText(distanceText, calcTextPos(showGradientAt, textBounds), height-7, p);
        if (climbCoords != null && showInline) {
            for (ClimbCoords c : climbCoords) {
                String climbName = c.nameAtLocation(showGradientAt);
                if (!"".equals(climbName)) {
                    p.getTextBounds(climbName, 0, climbName.length(), textBounds);
                    canvas.drawText(climbName, calcTextPos(showGradientAt, textBounds), (height - textBounds.height())/2, p);
                    break;
                }
            }
        }
    }

    private float interpolateElevation(List<PlotPoint> pts, int index, int x, boolean coord) {
        float elevation1 = pts.get(index).getElevation();
        float elevation2;

        if (coord) {
            // Change to y coord, rather than actual elevation
            elevation1 = pts.get(index).getY();
        }

        if (index < pts.size()-1) {
            elevation2 = pts.get(index+1).getElevation();
        } else {
            return elevation1;
        }

        if (coord) {
            // Change to y coord, rather than actual elevation
            elevation2 = pts.get(index+1).getY();
        }

        float xDiff = pts.get(index+1).getX() - pts.get(index).getX();
        float xAlong = x - pts.get(index).getX();

        return elevation1 + ((elevation2-elevation1) * (xAlong/xDiff));
    }

    private int calcTextPos(int x, Rect textBounds) {
        int pos = x - (textBounds.width()/2);
        if (pos < 0) {
            pos = 0;
        } else {
            int screenWidth = ScreenController.getScreenSize().x;
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
    private int getNearestIndex(List<PlotPoint> pts, int x) {
        if (x < xLeft) {
            return -1;
        }
        for (int i=0; i<pts.size()-1; i++) {
            if (pts.get(i).getX() <= x && pts.get(i+1).getX() > x) {
                return i;
            }
        }

        return pts.size()-1;
    }

    private void drawTracker(Canvas canvas, PlotPoint pt, int colour) {
        if (pt == null) return;
        Bitmap bmp = PositionMarker.getInstance().getIcon(PositionMarker.Size.SMALL, colour);

        int x = (int)pt.getX();
        int y = (int)pt.getY();
        Paint p = new Paint();
        canvas.drawBitmap(bmp, null, new Rect(x - 30, y - 80, x + 30, y), p);
    }

    /**
     * Populates the profile points from the climb stored in the database
     * @param startIdx - start at this point of the profile, rather than 0
     * @param targetDist - only load up to this distance from start
     * @param smooth - applies smoothing to the retrieved points
     * @return
     */
    private List<PlotPoint> getClimbPoints(int startIdx, double targetDist, boolean smooth) {
        float distFromLast = 0;
        float totalDist = 0;
        boolean first = true;
        float lastX = 0;
        double lastGradient = 0;
        int lastIndex = 0;
        List<PlotPoint> points = new ArrayList<>();

        maxSmoothedGradient = 0;

        int smoothDist = profile.getSmoothDist();
        if (smoothDist == 0) {
            smoothDist = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, DEFAULT_SMOOTH_DIST);
        }

        // Determine cumulative delta to all points
        for (int i = startIdx; i < profile.getPoints().size(); i++) {
            RoutePoint pt = profile.getPoints().get(i);
            PlotPoint p = new PlotPoint();
            p.setLocation(new LatLng(pt.getLat(), pt.getLon()));

            if (first) {
                first = false;
                p.setX(profile.getPoints().get(i).getDistFromStart());
                p.setElevation((float)pt.getElevation());
                p.setProfileIndex(i);
                lastIndex = startIdx;
            } else {
                distFromLast += (float)calcDelta(pt, profile.getPoints().get(lastIndex).getEasting(), profile.getPoints().get(lastIndex).getNorthing());
                lastIndex++;

                if (smooth && distFromLast < smoothDist && i != profile.getPoints().size() - 1) continue;

                p.setX(distFromLast + lastX);
                p.setElevation((float) pt.getElevation());
                p.setProfileIndex(i);
                distFromLast = 0;
            }

            points.add(p);
            lastX = p.getX();
            totalDist = p.getX();

            if (totalDist - points.get(0).getX() >= targetDist) {
                break;
            }
        }

        // Points have been set so now set gradients
        for (int i=0; i<points.size(); i++) {
            PlotPoint p = points.get(i);
            double gradient = calcGradient(points, i, lastGradient);
            p.setGradient((float)gradient);
            lastGradient = gradient;

            if (gradient > maxSmoothedGradient) {
                maxSmoothedGradient = gradient;
            }
        }
        return points;
    }

    private void convertToPlotPoints(List<PlotPoint> points, int xSize, int ySize) {
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
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = this.height = ySize = (int) yRange + TOP_MARGIN;
        this.setLayoutParams(layoutParams);

        // Calculate the scale factor for elevation points and where the top point should be plotted
        // The top point allows for a bit of extra plot at the bottom so that the profile is not
        // right at the bottom of the screen (unless minimum elevation actually is 0)
        // Elevations will be plotted from topY down to height (0,0 is top left of view)
        double scaleY = yRange / (maxElevation - minElevation);
        int topY = minElevation <= 0 ? 0 : TOP_MARGIN / 2;

        Log.d(TAG, "Adjust - Screen width: " + xSize + "; xScale: " + scaleX +
                "; Y range: " + yRange + "; yScale: " + scaleY);
        float xStart = points.get(0).getX();

        for (PlotPoint p : points) {
            p.setX((float)((p.getX() - xStart) * scaleX) + xLeft);
            p.setY((float)((maxElevation - p.getElevation()) * scaleY) + topY);
        }

        scaleFacX = scaleX;
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

    private double calcGradient(List<PlotPoint> points, int i, double lastGradient) {
        if (i >= points.size()-1) {
            return lastGradient;
        }
        double elevationDiff = points.get(i+1).getElevation() - points.get(i).getElevation();
        double distDiff = points.get(i+1).getX() - points.get(i).getX();

        return 100 * (elevationDiff/distDiff);
    }

    /**
     * Interpolates an x, y view location given a horizontal distance (in metres)
     * @param plotPoints
     * @param type
     * @param x0
     * @param xDist
     * @return
     */
    private PlotPoint calcPlotXY(List<PlotPoint> plotPoints, float xDist) {
        int xIndex = Integer.MIN_VALUE;
        PlotPoint plotXY = new PlotPoint();

        // Convert distance to pixel value
        xDist *= scaleFacX;
        xDist += xLeft;

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
    private static Bitmap getBitmap(VectorDrawable vectorDrawable, int color) {
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.setTint(color);
        vectorDrawable.draw(canvas);
        return bitmap;
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
        int index;
        if (x < 0) {
            index = 0;
        } else {
            index = getNearestIndex(points, x);
        }

        if (index < 0) {
            return null;
        }
        return points.get(index).getLocation();
    }

    public List<LatLng> getAllLatLngsBetween(int x1, int x2) {
        int increment = (x1 < x2 ? 1 : -1);

        List<LatLng> lls = new ArrayList<>();
        for (int x = x1; x != x2; x += increment) {
            LatLng ll = getLatLongAtX(x);
            if (ll != null) {
                lls.add(ll);
            }
        }

        return lls;
    }
    public double getBearingAtX(int x) {
        int index;
        if (x < 0) {
            index = 0;
        } else {
            index = getNearestIndex(points, x);
        }

        if (index < 0) {
            return Float.MIN_VALUE;
        } else if (index == points.size()-1) {
            index--;
        }

        LatLng p1 = points.get(index).getLocation();
        LatLng p2 = points.get(index+1).getLocation();
        return SphericalUtil.computeHeading(p1, p2);
    }

    public void setTransparency(int transparency) {
        this.transparency = transparency;
    }

    @DebugTrace
    private List<Integer> findRouteIndexes(GPXRoute climb) {
        PointF lastPoint = null;
        int startIdx = -1;
        int endIdx = -1;

        // Search through the route looking for the start of the climb
        for (int i=0; i<profile.getPoints().size(); i++) {
            RoutePoint pt = profile.getPoints().get(i);

            if (lastPoint == null) {
                lastPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
                continue;
            }

            PointF currentPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());

            PointF start = new PointF((float)climb.getPoints().get(0).getEasting(), (float)climb.getPoints().get(0).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(start, lastPoint, currentPoint)) {
                PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
                DirectionChecker checker = new DirectionChecker();
                checker.setStartIndex(i-1);
                checker.calcSegmentDist(start, lastPoint, currentPoint);
                if (checker.check(second, profile.getPoints(), 1)) {
                    Log.d(TAG, "FOUND CLIMB START " + climb.getName());
                    startIdx = i;
                    break;
                }
            }

            lastPoint = currentPoint;
        }

        lastPoint = null;
        if (startIdx < 0) {
            return null;
        }

        // If the start was found, look for the end
        for (int i=startIdx; i<profile.getPoints().size(); i++) {
            RoutePoint pt = profile.getPoints().get(i);

            if (lastPoint == null) {
                lastPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());
                continue;
            }

            PointF currentPoint = new PointF((float)pt.getEasting(), (float)pt.getNorthing());

            int lastIdx = climb.getPoints().size()-1;
            PointF end = new PointF((float)climb.getPoints().get(lastIdx).getEasting(), (float)climb.getPoints().get(lastIdx).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(end, lastPoint, currentPoint)) {
                Log.d(TAG, "FOUND CLIMB END " + climb.getName());
                endIdx = i;
                break;
            }

            lastPoint = currentPoint;
        }

        if (endIdx < 0) {
            return null;
        }

        List<Integer> indexes = new ArrayList<>();
        indexes.add(startIdx);
        indexes.add(endIdx);
        return indexes;
    }

    public List<ClimbCoords> getClimbCoords() {
        return this.climbCoords;
    }

    @Getter
    @Setter
    public class ClimbCoords {
        int x0;
        int xN;
        int id;
        String name;

        ClimbCoords(int x0, int xN, int id) {
            this.x0 = x0;
            this.xN = xN;
            this.id = id;
            GPXRoute climb = Database.getInstance().getClimb(id);
            this.name = climb.getName();
        }
        public String nameAtLocation(int x) {
            if (x >= x0 && x <= xN) {
                return name;
            }
            return "";
        }

        public int idAtLocation(int x) {
            if (x >= x0 && x <= xN) {
                return id;
            }
            return -1;
        }
     }
}
