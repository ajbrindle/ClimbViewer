package com.sk7software.climbviewer.geo;

public class Ellipsoid {
    private int id;
    private String name;
    private double radiusA;
    private double radiusB;

    public Ellipsoid(int id, String name, double radiusA, double radiusB) {
        this.id = id;
        this.name = name;
        this.radiusA = radiusA;
        this.radiusB = radiusB;
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
    public double getRadiusA() {
        return radiusA;
    }
    public void setRadiusA(double radiusA) {
        this.radiusA = radiusA;
    }
    public double getRadiusB() {
        return radiusB;
    }
    public void setRadiusB(double radiusB) {
        this.radiusB = radiusB;
    }
}
