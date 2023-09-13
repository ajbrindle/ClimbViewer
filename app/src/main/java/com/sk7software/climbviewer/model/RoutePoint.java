package com.sk7software.climbviewer.model;

import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.text.NumberFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Root(strict=false)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePoint {
    @Attribute
    private double lat;

    @Attribute
    private double lon;

    @Element(name="ele")
    private double elevation;

    @Element(required = false)
    private String time;

    @Attribute(required = false)
    private int no;

    @Attribute(required = false)
    private double easting;

    @Attribute(required = false)
    private double northing;

    @Attribute(required = false)
    private float bearing;

    @Attribute(required = false)
    private float distFromStart;

    @Attribute(required = false)
    private float accuracy;

    @Attribute(required = false)
    private float elevFromStart;

    @Attribute(required = false)
    private double smoothedElevation;

    public void setENFromLL(Projection proj, int zone) {
        RoutePoint tmpPt = GeoConvert.convertLLToGrid(proj, this, zone);
        this.setEasting(tmpPt.getEasting());
        this.setNorthing(tmpPt.getNorthing());
    }

    @Override
    public String toString() {
        NumberFormat df = NumberFormat.getInstance();
        df.setMaximumFractionDigits(4);
        df.setGroupingUsed(false);
        StringBuilder sb = new StringBuilder("RoutePoint[");
        sb.append("Lat: " + df.format(lat) + "; ");
        sb.append("Lon: " + df.format(lon) + "; ");
        df.setMaximumFractionDigits(1);
        sb.append("Elev: " + df.format(elevation) + "; ");
        sb.append("E: " + df.format(easting) + "; ");
        sb.append("N: " + df.format(northing) + "; ");
        sb.append("Dist: " + df.format(distFromStart) + "]");
        return sb.toString();
    }
}
