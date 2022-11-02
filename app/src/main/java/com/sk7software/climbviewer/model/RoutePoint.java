package com.sk7software.climbviewer.model;

import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import lombok.Getter;
import lombok.Setter;

@Root(strict=false)
@Getter
@Setter
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
    private float distFromStart;

    @Attribute(required = false)
    private float accuracy;

    @Attribute(required = false)
    private float elevFromStart;

    public void setENFromLL(Projection proj, int zone) {
        RoutePoint tmpPt = new RoutePoint();
        tmpPt = GeoConvert.convertLLToGrid(proj, this, zone);
        this.setEasting(tmpPt.getEasting());
        this.setNorthing(tmpPt.getNorthing());
    }
}
