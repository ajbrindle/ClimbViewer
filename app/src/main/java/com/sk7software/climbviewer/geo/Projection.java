package com.sk7software.climbviewer.geo;

public class Projection {
    private int id;
    private String name;
    private double falseE;
    private double falseN;
    private double lat0;
    private double lon0;
    private double k0;
    private Ellipsoid e;
    private int projType;

    public static final int SYS_TYPE_TM = 1;
    public static final int SYS_TYPE_UTM = 2;
    public static final int SYS_OSGB36 = 1;
    public static final int SYS_UTM_WGS84 = 2;

    public Projection(int id, String name,
                      double falseE, double falseN, double lat0, double lon0, double k0,
                      Ellipsoid ellipsoid, int projType) {
        this.id = id;
        this.name = name;
        this.falseE = falseE;
        this.falseN = falseN;
        this.lat0 = lat0;
        this.lon0 = lon0;
        this.k0 = k0;
        this.e = ellipsoid;
        this.projType = projType;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getFalseE() {
        return falseE;
    }

    public void setFalseE(double falseE) {
        this.falseE = falseE;
    }

    public double getFalseN() {
        return falseN;
    }

    public void setFalseN(double falseN) {
        this.falseN = falseN;
    }

    public double getLat0() {
        return lat0;
    }

    public void setLat0(double lat0) {
        this.lat0 = lat0;
    }

    public double getLon0() {
        return lon0;
    }

    public void setLon0(double lon0) {
        this.lon0 = lon0;
    }

    public double getK0() {
        return k0;
    }

    public void setK0(double k0) {
        this.k0 = k0;
    }

    public Ellipsoid getE() {
        return e;
    }

    public void setE(Ellipsoid e) {
        this.e = e;
    }

    public int getProjType() {
        return projType;
    }

    public void setProjType(int projType) {
        this.projType = projType;
    }
}
