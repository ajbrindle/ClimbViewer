package com.sk7software.climbviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.geo.Ellipsoid;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.RoutePoint;

import org.junit.Test;

public class GeoConvertTest {
    final double[] latIn = {57.602556,
            57.333739,
            55.866635,
            55.952845,
            54.847157,
            54.960855,
            54.541593,
            54.277045,
            53.404407,
            53.294559,
            53.34132,
            52.816372,
            51.883609,
            51.823384,
            52.562491,
            53.16686,
            52.606714,
            51.524464,
            51.533007,
            53.191551,
            51.454346,
            51.191735,
            50.127971,
            50.252822,
            50.924157,
            51.349825,
            51.502243};
    final double[] lonIn = {-5.52829,
            -2.210419,
            -4.261984,
            -2.479453,
            -4.641013,
            -1.378073,
            -3.285574,
            -0.40853,
            -2.164969,
            -4.639639,
            -3.347373,
            -4.534586,
            -5.27479,
            -3.037699,
            -1.792124,
            0.317251,
            1.720754,
            0.517751,
            -1.141184,
            -0.957163,
            -2.580393,
            -4.088267,
            -5.667551,
            -3.720225,
            -1.448801,
            1.377431,
            -0.130442};
    final double[] eOut = {189289.333,
            387431.08,
            258546.516,
            370159.237,
            230528.752,
            439922.316,
            316926.701,
            503723.956,
            389129.577,
            224171.03,
            310389.104,
            229289.826,
            174722.913,
            328577.395,
            414188.232,
            555001.422,
            652020.874,
            574757.54,
            459666.197,
            469773.207,
            359768.776,
            254172.619,
            137979.918,
            277461.736,
            438837.236,
            635278.179,
            529858.31};
    final double[] nOut = {862436.384,
            827044.395,
            666032.021,
            673417.096,
            553456.712,
            563089.311,
            517018.451,
            487988.488,
            389742.758,
            380759.773,
            383557.965,
            327324.118,
            225629.633,
            214371.301,
            296091.692,
            365808.826,
            307491.351,
            183620.828,
            181915.871,
            366556.69,
            172978.394,
            145685.692,
            31765.621,
            40621.977,
            114001.12,
            166608.537,
            179801.741};

    @Test
    public final void testConvertLLToGrid() {
        final double error = 2;
        final Ellipsoid e = new Ellipsoid(10, "GRS 1980", 6378137.0, 6356752.31414);
        //final Ellipsoid e = new Ellipsoid(4, "Airy 1830", 6377563.396, 6356256.91);
        final Projection proj = new Projection(1, "UK National Grid", 400000.0, -100000.0, (49.0*Math.PI/180.0), (-2.0*Math.PI/180.0), 0.9996013, e, Projection.SYS_TYPE_TM);

        RoutePoint loc = new RoutePoint();
        RoutePoint p;

        for (int i=0; i<latIn.length; i++) {
            loc.setLat(latIn[i]);
            loc.setLon(lonIn[i]);
            p = GeoConvert.convertLLToGrid(proj, loc, 0);
            assertTrue(nearEnough(p.getEasting(), eOut[i], error) &&
                    nearEnough(p.getNorthing(), nOut[i], error));
        }
    }

    @Test
    public final void testConvertGridToLL() {
        final double error = 2;
        final Ellipsoid e = new Ellipsoid(10, "GRS 1980", 6378137.0, 6356752.31414);
        //final Ellipsoid e = new Ellipsoid(4, "Airy 1830", 6377563.396, 6356256.91);
        final Projection proj = new Projection(1, "UK National Grid", 400000.0, -100000.0, (49.0*Math.PI/180.0), (-2.0*Math.PI/180.0), 0.9996013, e, Projection.SYS_TYPE_TM);

        RoutePoint loc = new RoutePoint();
        LatLng ll;

        for (int i=0; i<latIn.length; i++) {
            loc.setEasting(eOut[i]);
            loc.setNorthing(nOut[i]);
            ll = GeoConvert.convertGridToLL(proj, loc, 0);
            assertTrue(nearEnough(ll.latitude, latIn[i], error) &&
                    nearEnough(ll.longitude, lonIn[i], error));
        }
    }

    @Test
    public void testUTMZone() {
        assertEquals(39, GeoConvert.calcUTMZone(30.0,50.0));
        assertEquals(-39, GeoConvert.calcUTMZone(-30.0,50.0));
    }

    @Test
    public void testUTMConvert() {
        final Ellipsoid e = new Ellipsoid(13, "WGS 1984", 6378137.0, 6356752.314245);
        final Projection proj = new Projection(1, "UTM (WGS 1984)", 500000.0, 0.0, 0.0, 0.0, 0.9996, e, Projection.SYS_TYPE_UTM);

        RoutePoint loc = new RoutePoint();
        RoutePoint p = new RoutePoint();

        loc.setLat(30);
        loc.setLon(50);
        p = GeoConvert.convertLLToGrid(proj, loc, 39);
        assertEquals(403549.85, p.getEasting(), 0.01);
        assertEquals(3319206.22, p.getNorthing(), 0.01);

        loc.setLat(-30);
        loc.setLon(50);
        p = GeoConvert.convertLLToGrid(proj, loc, -39);
        assertEquals(403549.847, p.getEasting(), 0.01);
        assertEquals(6680793.777, p.getNorthing(), 0.01);
    }

    private boolean nearEnough(double result, double value, double error) {
        //System.out.print(Math.abs(result-value) + " ");
        return Math.abs(result-value) < error;
    }

}
