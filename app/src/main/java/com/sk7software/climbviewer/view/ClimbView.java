package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
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
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.PlotType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClimbView extends View {

    private GPXRoute climb;
    private RoutePoint currentPoint;
    private PlotPoint currentPlotPoint;
    private RoutePoint pbPoint;
    private PlotPoint pbPlotPoint;
    private List<PlotPoint> points;
    private double yRange;
    private Map<PlotType, Double> scaleFacX = new HashMap<>();
    private int height;
    private boolean heightSet;
    private boolean showZoom;
    private boolean initialised;

    private static final int PADDING = 20;
    private static final int SMOOTH_DIST = 10;
    private static final String TAG = ClimbView.class.getSimpleName();
    private static final int ZOOM_DIST = 500;
    private static final float ZOOM_MULTIPLIER = 2.5f;

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

    public void setClimb(GPXRoute climb, boolean showZoom) {
        this.climb = climb;
        this.showZoom = showZoom;
        this.heightSet = false;
        this.initialised = false;
        currentPoint = new RoutePoint();
        currentPoint.setEasting(climb.getPoints().get(0).getEasting());
        currentPoint.setNorthing(climb.getPoints().get(0).getNorthing());
    }

    public void setPB(ClimbAttempt pb) {
        if (pb == null) return;
        pbPoint = new RoutePoint();
        pbPoint.setEasting(climb.getPoints().get(0).getEasting());
        pbPoint.setNorthing(climb.getPoints().get(0).getNorthing());
    }

    public void setHeight(int height) {
        this.height = height;
        this.heightSet = true;
        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = height;
        this.setLayoutParams(layoutParams);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!heightSet) return;

        Log.d(TAG, "onDraw");
        int y0 = 0;

//        Paint p = new Paint();
//        p.setStrokeWidth(1.0f);
//        p.setColor(Color.YELLOW);
//        p.setStyle(Paint.Style.FILL_AND_STROKE);
//        canvas.drawRect(5, 5,1050, height/2, p);
//        p.setColor(Color.RED);
//        canvas.drawRect(5, height/2,1050, height, p);
//
        Point size = getScreenSize();
        int screenWidth = size.x - (2 * PADDING); // Add some padding for display

        if (points == null) {
            points = getClimbPoints(0, Integer.MAX_VALUE, true);
            Log.d(TAG, "ZOOM_LOG: climb x: " + points.get(0).getX() + "," + points.get(1).getX());
            yRange = convertToPlotPoints(points, screenWidth, height/2, 1, PlotType.FULL);
        }

        int maxY = (int)(yRange + 50 + (((height/2) - yRange)/2));
        plotPoints(points, y0, maxY, canvas, PADDING);

        List<PlotPoint> zoomPoints = new ArrayList<>();
        PlotPoint pbPoint = calcPlotXY(points, PlotType.FULL, PADDING, ClimbController.getInstance().getPbDist());
        PlotPoint attPoint = calcPlotXY(points, PlotType.FULL, PADDING, ClimbController.getInstance().getAttemptDist());

        // Find points to show shorter distance in zoom window
        if (showZoom && currentPlotPoint != null) {
            // Find point before current location
            int startIdx = findPointBefore(attPoint);
//            Log.d(TAG, "ZOOM_LOG: Zoom start: " + startIdx);
            zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);

            // Push back start if too short
            if ((zoomPoints.get(zoomPoints.size()-1).getX() - zoomPoints.get(0).getX()) < ZOOM_DIST) {
                startIdx = adjustStart(attPoint, startIdx, zoomPoints.get(zoomPoints.size()-1).getX(), ZOOM_DIST);
//                Log.d(TAG, "ZOOM_LOG: adjusted: " + startIdx);
                zoomPoints = getClimbPoints(startIdx, ZOOM_DIST, false);
            }
//            Log.d(TAG, "ZOOM_LOG: Zoom points: " + zoomPoints.size() + "(" + startIdx + ")" + " " + zoomPoints.get(0).getX() + "," + zoomPoints.get(1).getX());
        }

        if (!zoomPoints.isEmpty()) {
            y0 = height/2;
            int zoomWidth = size.x / 2;

            // Draw box round zoom area
            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setColor(Color.BLACK);
            p.setStrokeWidth(5);

            float distOnZoom = ClimbController.getInstance().getAttemptDist() - zoomPoints.get(0).getX();
//            Log.d(TAG, "ZOOM_LOG: Dist on zoom: " + ClimbController.getInstance().getAttemptDist() + "/" + distOnZoom + "/" + zoomPoints.get(0).getX());
            double zoomYRange = convertToPlotPoints(zoomPoints, zoomWidth, (3*height/8)-100, ZOOM_MULTIPLIER, PlotType.ZOOM);
            int maxZoomY = (int)(zoomYRange + 50 + ((((3*height)/8) - 100 - yRange)/2));
            int zoomPadding = (int)(size.x - zoomPoints.get(zoomPoints.size()-1).getX())/2;
            canvas.drawRoundRect(zoomPadding-10, y0-20, zoomPadding+zoomWidth+10, maxZoomY+y0+10, 20, 20, p);
            canvas.drawLine(attPoint.getX()+PADDING, attPoint.getY(), zoomPadding, y0-20, p);
            canvas.drawLine(attPoint.getX()+PADDING, attPoint.getY(), zoomPadding+zoomWidth, y0-20, p);
            plotPoints(zoomPoints, y0, maxZoomY, canvas, zoomPadding);

            PlotPoint zoomPoint = calcPlotXY(zoomPoints, PlotType.ZOOM, zoomPadding, distOnZoom);
            drawTracker(canvas, zoomPoint, Color.CYAN, zoomPadding, y0);
        }

        drawTracker(canvas, pbPoint, Color.GREEN, PADDING, 0);
        drawTracker(canvas, attPoint, Color.CYAN, PADDING, 0);
        initialised = true;
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
        canvas.drawBitmap(bmp, null, new Rect(x-40, y-120, x+40, y), p);
        canvas.drawBitmap(bike, null, new Rect(x-30, y-100, x+30, y-60), p);
    }

    private int findPointBefore(PlotPoint pt) {
        if (points.get(0).getX() > pt.getX()) {
            return 0;
        }

        int idx = 0;
        for (PlotPoint p : points) {
            if (p.getX() > pt.getX()) {
                return idx-1;
            }
            idx++;
        }
        return 0;
    }

//    private int findNextPoint(PlotPoint pt, int start) {
//        int endIdx = 0;
//        for (int i=start+1; i<points.size(); i++) {
//            if ((points.get(i).getX() - points.get(start).getX())/scaleFacX >= 500) {
//                return i;
//            }
//        }
//        return points.size()-1;
//    }

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
        for (int i=startIdx; i<climb.getPoints().size(); i++) {
            RoutePoint pt = climb.getPoints().get(i);
            PlotPoint p = new PlotPoint();

            if (first) {
                first = false;
                p.setX(climb.getPoints().get(i).getDistFromStart());
                p.setElevation((float)pt.getElevation());
                p.setGradient(0);
                lastIndex = startIdx;
            } else {
                distFromLast += (float)calcDelta(pt, climb.getPoints().get(lastIndex).getEasting(), climb.getPoints().get(lastIndex).getNorthing());
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
        double avgGradient = 100 * (maxElevation - minElevation) / xDist;
        double yRange = adjustYRange(ySize - 50, avgGradient) * multiplier;

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

    private double adjustYRange(double yRange, double avgGradient) {
        if (avgGradient < 3) {
            return yRange * 0.2;
        } else if (avgGradient < 5) {
            return yRange * 0.4;
        } else if (avgGradient < 10) {
            return yRange * 0.6;
        } else if (avgGradient < 15) {
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

    public float plotLocation(RoutePoint loc) {
        float groundDist = ClimbController.getInstance().getAttemptDist();
        double xDist = groundDist * scaleFacX.get(PlotType.FULL) + PADDING;
        currentPlotPoint = new PlotPoint();
        currentPoint = loc;
        calcPlotPoints(currentPlotPoint, xDist);
        return groundDist;
    }

    public float pbLocation(RoutePoint loc) {
        if (loc == null) return 0;

        float pbGroundDist = ClimbController.getInstance().getPbDist();
        double xDist = pbGroundDist * scaleFacX.get(PlotType.FULL) + PADDING;
        pbPlotPoint = new PlotPoint();
        pbPoint = loc;
        calcPlotPoints(pbPlotPoint, xDist);
        return pbGroundDist;
    }

    private float zoomPlotLocation(RoutePoint loc) {
        float zoomGroundDist = ClimbController.getInstance().getAttemptDist();
        double xDist = zoomGroundDist * scaleFacX.get(PlotType.ZOOM) + PADDING;
        PlotPoint currentZoomPoint = new PlotPoint();
        calcPlotPoints(currentZoomPoint, xDist);
        return zoomGroundDist;
    }
    private double getXDist(double dist) {
        List<RoutePoint> pts = ClimbController.getInstance().getClimb().getPoints();

        if (pts == null || pts.isEmpty()) {
            return 0;
        }

        double totalDist = pts.get(pts.size()-1).getDistFromStart();
        Point dispSize = getScreenSize();
        double displayScaleFac = dispSize.x/totalDist;
        return (dist * displayScaleFac) + PADDING;
    }

    private void calcPlotPoints(PlotPoint plotPoint, double xDist) {
        int xIndex = Integer.MAX_VALUE;

        for (int i=0; i<points.size(); i++) {
            if (points.get(i).getX() > xDist) {
                xIndex = i;
                break;
            }
        }

        if (xIndex < points.size() && xIndex > 0) {
            // Found a location on the plot
            double y1 = points.get(xIndex-1).getY();
            double y2 = points.get(xIndex).getY();
            double xDiff = points.get(xIndex).getX() - points.get(xIndex-1).getX();
            double yPlot = y1 + ((y2 - y1) * ((xDist - points.get(xIndex-1).getX())/xDiff));
            plotPoint.setX((float)xDist);
            plotPoint.setY((float)yPlot);
        } else if (xIndex == 0) {
            plotPoint.setX(points.get(0).getX());
            plotPoint.setY(points.get(0).getY());
        } else {
            plotPoint.setX(points.get(points.size() - 1).getX());
            plotPoint.setY(points.get(points.size() - 1).getY());
        }
    }

    private PlotPoint calcPlotXY(List<PlotPoint> plotPoints, PlotType type, float x0, float xDist) {
        int xIndex = Integer.MAX_VALUE;
        PlotPoint plotXY = new PlotPoint();

        xDist *= scaleFacX.get(type);

//        if (type == PlotType.ZOOM) Log.d(TAG, "ZOOM_LOG: x dist: " + xDist);

        // Find which points the xDist is between
        for (int i=0; i<plotPoints.size(); i++) {
            if (plotPoints.get(i).getX() > xDist) {
                xIndex = i;
                break;
            }
        }

        if (xIndex < plotPoints.size() && xIndex > 0) {
            if (type == PlotType.ZOOM) {
//                Log.d(TAG, "ZOOM_LOG: x index: " + xIndex + "/" + plotPoints.get(xIndex-1).getX() + "/" + plotPoints.get(xIndex).getX());
            }
            // Found a location on the plot
            double y1 = plotPoints.get(xIndex-1).getY();
            double y2 = plotPoints.get(xIndex).getY();
            double xDiff = plotPoints.get(xIndex).getX() - plotPoints.get(xIndex-1).getX();
            double yPlot = y1 + ((y2 - y1) * ((xDist - plotPoints.get(xIndex-1).getX())/xDiff));
            plotXY.setX(xDist);
//                Log.d(TAG, "ZOOM_LOG: plot x: " + plotXY.getX());
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
}
