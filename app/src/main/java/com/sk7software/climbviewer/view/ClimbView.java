package com.sk7software.climbviewer.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.ClimbController;
import com.sk7software.climbviewer.LocationMonitor;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public class ClimbView extends View {

    private GPXRoute climb;
    private RoutePoint currentPoint;
    private PlotPoint currentPlotPoint;
    private RoutePoint pbPoint;
    private PlotPoint pbPlotPoint;
    private List<PlotPoint> points;
    private float groundDist = 0;
    private float pbGroundDist = 0;
    private double scaleFacX = 0;
    private int minAttemptIdx = 0;
    private int minPBIdx = 0;
    private int maxY = 800;
    private int y0 = 300;

    private static final int PADDING = 20;
    private static final int SMOOTH_DIST = 10;
    private static final String TAG = ClimbView.class.getSimpleName();


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

    public void setClimb(GPXRoute climb, int y0) {
        this.climb = climb;
        currentPoint = new RoutePoint();
        currentPoint.setEasting(climb.getPoints().get(0).getEasting());
        currentPoint.setNorthing(climb.getPoints().get(0).getNorthing());
        this.y0 = y0;
    }

    public void setPB(ClimbAttempt pb) {
        if (pb == null) return;
        pbPoint = new RoutePoint();
        pbPoint.setEasting(climb.getPoints().get(0).getEasting());
        pbPoint.setNorthing(climb.getPoints().get(0).getNorthing());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint p = new Paint();
        p.setStrokeWidth(1.0f);
        p.setColor(Color.BLUE);
        p.setStyle(Paint.Style.FILL_AND_STROKE);

        if (points == null) {
            points = getPlotPoints();
        }
        int numPoints = points.size();

        for (int i=0; i<numPoints-1; i++) {
            Path path = new Path();
            path.moveTo(points.get(i).getX(), points.get(i).getY());
            path.lineTo(points.get(i+1).getX(), points.get(i+1).getY());
            path.lineTo(points.get(i+1).getX(), maxY);
            path.lineTo(points.get(i).getX(), maxY);
            path.lineTo(points.get(i).getX(), points.get(i).getY());
            p.setColor(Palette.getColour(points.get(i+1).getGradient()));
            canvas.drawPath(path, p);
        }

        if (y0 == 300 && currentPlotPoint != null) {
            // Draw zoomed view of current position
            int y1 = maxY - 50 - y0 + 100;
            ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
            layoutParams.height = 800;
            this.setLayoutParams(layoutParams);

            // Find point before current location
            int startIdx = findPointBefore(currentPlotPoint);

            // Find point 500m further along
            int endIdx = findNextPoint(currentPlotPoint, startIdx);

            // Push back start if too short
            if ((points.get(endIdx).getX() - points.get(startIdx).getX()) / scaleFacX < 500) {
                startIdx = adjustStart(currentPlotPoint, startIdx, endIdx);
            }

            int yRange = calcYRange(startIdx, endIdx);
            double xFac = 1;

            for (int i=startIdx; i<=endIdx; i++) {
                Path path = new Path();
                path.moveTo((int)(points.get(i).getX()/xFac), points.get(i).getY()+y1);
                path.lineTo((int)(points.get(i + 1).getX()/xFac), points.get(i + 1).getY()+y1);
                path.lineTo((int)(points.get(i + 1).getX()/xFac), 800);
                path.lineTo((int)(points.get(i).getX()/xFac), 800);
                path.lineTo((int)(points.get(i).getX()/xFac), points.get(i).getY()+y1);
                p.setColor(Palette.getColour(points.get(i + 1).getGradient()));
                canvas.drawPath(path, p);
            }
        }

        drawTracker(canvas, pbPlotPoint, Color.GREEN);
        drawTracker(canvas, currentPlotPoint, Color.BLUE);
    }

    private void drawTracker(Canvas canvas, PlotPoint pt, int colour) {
        if (pt == null) return;

        Paint p = new Paint();
        p.setStrokeWidth(20.0f);
        p.setColor(colour);
        p.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(pt.getX(), 100, 50, p);
        p.setStrokeWidth(10.0f);
        canvas.drawLine(pt.getX(), 150.0f, pt.getX(), pt.getY(), p);
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

    private int findNextPoint(PlotPoint pt, int start) {
        int endIdx = 0;
        for (int i=start+1; i<points.size(); i++) {
            if ((points.get(i).getX() - points.get(start).getX())/scaleFacX >= 500) {
                return i;
            }
        }
        return points.size()-1;
    }

    private int adjustStart(PlotPoint pt, int start, int end) {
        for (int i=start; i>=0; i--) {
            double dist = (points.get(end).getX() - points.get(i).getX()) / scaleFacX;
            if (dist >= 500) {
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

    private List<PlotPoint> getPlotPoints() {

        double delta = 0.0;
        float distFromLast = 0;
        boolean first = true;
        double minElevation = Double.MAX_VALUE;
        double maxElevation = Double.MIN_VALUE;
        float lastX = 0;
        double lastElevation = 0;
        int lastIndex = 0;
        List<PlotPoint> points = new ArrayList<>();

        // Determine cumulative delta to all points
        for (RoutePoint pt : climb.getPoints()) {
            PlotPoint p = new PlotPoint();

            if (first) {
                first = false;
                p.setX(0);
                p.setElevation((float)pt.getElevation());
                p.setGradient(0);
                lastIndex = 0;
            } else {
                distFromLast += (float)calcDelta(pt, climb.getPoints().get(lastIndex).getEasting(), climb.getPoints().get(lastIndex).getNorthing());
                lastIndex++;

                if (distFromLast < SMOOTH_DIST) continue;

                p.setX(distFromLast + lastX);
                p.setElevation((float) pt.getElevation());
                p.setGradient((float) calcGradient(p.getX() - lastX, p.getElevation() - lastElevation));
                distFromLast = 0;
            }

            points.add(p);

            if (pt.getElevation() < minElevation) {
                minElevation = pt.getElevation();
            }
            if (pt.getElevation() > maxElevation) {
                maxElevation = pt.getElevation();
            }

            lastX = p.getX();
            lastElevation = p.getElevation();
            delta = p.getX();
        }

        // Scale to fit on screen
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenWidth = size.x - (2 * PADDING); // 20 pixels padding each end
        scaleFacX = (double)screenWidth / delta;
        double avgGradient = 100 * (maxElevation - minElevation) / delta;
        double yRange = 800;

        if (avgGradient < 3) {
            yRange *= 0.2;
        } else if (avgGradient < 5) {
            yRange *= 0.4;
        } else if (avgGradient < 10) {
            yRange *= 0.6;
        } else if (avgGradient < 15) {
            yRange *= 0.8;
        }

        double scaleFacY = yRange / (maxElevation - minElevation);
        maxY = (int)(yRange + y0) + 50;

        Log.d(TAG, "Adjust - Screen width: " + screenWidth + "; xScale: " + scaleFacX +
                "; Y range: " + yRange + "; yScale: " + scaleFacY);
        Log.d(TAG, "Plotting y between " + y0 + " and " + (((maxElevation-minElevation)*scaleFacY)+y0));
        for (PlotPoint p : points) {
            p.setX((float)(p.getX() * scaleFacX) + PADDING);
            p.setY((float)((maxElevation - p.getElevation()) * scaleFacY) + y0);
        }

        ViewGroup.LayoutParams layoutParams = this.getLayoutParams();
        layoutParams.height = maxY;
        this.setLayoutParams(layoutParams);

        return points;
    }

    private double calcDelta(RoutePoint pt, double e, double n) {
        return Math.sqrt(Math.pow(e - pt.getEasting(), 2.0) + Math.pow(n - pt.getNorthing(), 2.0));
    }

    private double calcGradient(double dist, double elevDiff) {
        return 100 * (elevDiff/dist);
    }

    public float plotLocation(RoutePoint loc) {
        groundDist = ClimbController.getInstance().getAttemptDist();
        double xDist = groundDist * scaleFacX + PADDING;
        currentPlotPoint = new PlotPoint();
        currentPoint = loc;
        calcPlotPoints(currentPlotPoint, xDist);
        return groundDist;
    }

    public float pbLocation(RoutePoint loc) {
        if (loc == null) return 0;

        //double delta = calcDelta(pbPoint, loc.getEasting(), loc.getNorthing());
        pbGroundDist = ClimbController.getInstance().getPbDist();
        double xDist = pbGroundDist * scaleFacX + PADDING;
        pbPlotPoint = new PlotPoint();
        pbPoint = loc;
        calcPlotPoints(pbPlotPoint, xDist);
        return pbGroundDist;
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
}
