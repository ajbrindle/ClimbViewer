package com.sk7software.climbviewer.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.geo.Ellipsoid;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXFile;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 5;
    public static final String DATABASE_NAME = "com.sk7software.climbviewer.db";
    private static final String TAG = Database.class.getSimpleName();

    private static Database dbInstance;
    private int maxId = 1;
    private boolean isUpgrading = false;
    private SQLiteDatabase currentDb = null;

    private static final List<String> CLIMB_COLUMNS = Arrays.asList("ID", "NAME", "PROJECTION_ID", "ZONE");
    private static final List<String> CLIMB_POINT_COLUMNS = Arrays.asList ("ID", "POINT_NO", "LAT", "LON", "EASTING", "NORTHING", "ELEVATION");
    private static final List<String> CLIMB_ATTEMPT_COLUMNS = Arrays.asList("ID", "ATTEMPT_ID", "TIMESTAMP", "DURATION");
    private static final List<String> CLIMB_ATTEMPT_POINT_COLUMNS = Arrays.asList("ID", "ATTEMPT_ID", "POINT_NO", "TIMESTAMP", "LAT", "LON", "EASTING", "NORTHING");

    public static Database getInstance() {
        if (dbInstance == null) {
            dbInstance = new Database(ApplicationContextProvider.getContext());
        }

        return dbInstance;
    }

    private Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG,"DB constructor");
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "DB onCreate()");
        initialise(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldv, int newv) {
        Log.d(TAG, "DB onUpgrade() " + oldv + " to " + newv);

        if (oldv <= 1 && newv >= 2) {
            isUpgrading = true;
            currentDb = db;
            // Add UTM fields to climb table
            String sqlStr = "ALTER TABLE CLIMB " +
                            "ADD PROJECTION_ID INTEGER;";
            db.execSQL(sqlStr);
            sqlStr = "ALTER TABLE CLIMB " +
                        "ADD ZONE INTEGER;";
            db.execSQL(sqlStr);

            // Migrate all existing grid points
            migrateToUTM(db, Projection.SYS_UTM_WGS84);
            isUpgrading = false;
            currentDb = null;
        }

        if (oldv <= 2 && newv >= 3) {
            isUpgrading = true;
            currentDb = db;
            migrateToUTM(db, Projection.SYS_UTM_WGS84);
            isUpgrading = false;
            currentDb = null;
        }

        if (oldv <= 3 && newv >= 4) {
            isUpgrading = true;
            currentDb = db;
            addRouteTables(db);
            isUpgrading = false;
            currentDb = null;
        }

        if (oldv <=4 && newv >= 5) {
            isUpgrading = true;
            currentDb = db;
            // Add UTM fields to route table
            String sqlStr = "ALTER TABLE ROUTE " +
                    "ADD PROJECTION_ID INTEGER;";
            db.execSQL(sqlStr);
            sqlStr = "ALTER TABLE ROUTE " +
                    "ADD ZONE INTEGER;";
            db.execSQL(sqlStr);

            // Migrate all existing grid points
            migrateToUTM(db, Projection.SYS_UTM_WGS84);
            isUpgrading = false;
            currentDb = null;

        }
    }


    private void initialise(SQLiteDatabase db) {
        Log.d(TAG, "DB initialise()");
        // Determine if table exists and return if so
        try (Cursor cursor = db.query("ELLIPSOID", new String[] {"NAME", "RADIUS_A", "RADIUS_B"}, "_id=?",
                new String[] {"1"}, null, null, null, null)) {
            if (cursor != null) {
                Log.d(TAG, "DB already initialised");
                return;
            }
        } catch(SQLException e) {
            Log.d(TAG, "DB initialisation required");
        }

        String createEllipsoid =
                "CREATE TABLE ELLIPSOID (" +
                        "_ID INTEGER PRIMARY KEY," +
                        "NAME TEXT," +
                        "RADIUS_A REAL," +
                        "RADIUS_B REAL);";
        db.execSQL(createEllipsoid);

        int id = 1;
        String insertStart = "INSERT INTO ELLIPSOID VALUES(";
        String insertEnd = ");";

        List<String> ellip = new ArrayList<String>();
        ellip.add(id++ + ",'Clarke 1866', 6378206.4, 6356583.8");
        ellip.add(id++ + ",'Clarke 1880', 6378249.145, 6356514.86955");    // 2
        ellip.add(id++ + ",'Bessel 1841', 6377397.155, 6356078.96284");    // 3
        ellip.add(id++ + ",'Airy 1830', 6377563.396, 6356256.91");    // 4
        ellip.add(id++ + ",'New International 1967', 6378157.5, 6356772.2");        // 5
        ellip.add(id++ + ",'International 1924', 6378388.0, 6356911.94613");    // 6
        ellip.add(id++ + ",'WGS 1972', 6378135.0, 6356750.519915");    // 7
        ellip.add(id++ + ",'Everest 1830', 6377276.3452, 6356075.4133");    // 8
        ellip.add(id++ + ",'WGS 1966', 6378145.0, 6356759.769356");    // 9
        ellip.add(id++ + ",'GRS 1980', 6378137.0, 6356752.31414");    // 10
        ellip.add(id++ + ",'Everest 1948', 6377304.063, 6356103.039");    // 11
        ellip.add(id++ + ",'Modified Airy', 6377340.189, 6356034.448");    // 12
        ellip.add(id++ + ",'WGS 1984', 6378137.0, 6356752.314245");    // 13
        ellip.add(id++ + ",'Modified Fisher 1960', 6378155.0, 6356773.3205");    // 14
        ellip.add(id++ + ",'Australian Nat 1965', 6378160.0, 6356774.719");    // 15
        ellip.add(id++ + ",'Krassovsky 1940', 6378245.0, 6356863.0188");    // 16
        ellip.add(id++ + ",'Hough 1960', 6378270.0, 6356794.343479");    // 17
        ellip.add(id++ + ",'Fisher 1960', 6378166.0, 6356784.283666");    // 18
        ellip.add(id++ + ",'Fisher 1968', 6378150.0, 6356768.337303");    // 19
        ellip.add(id++ + ",'Normal Sphere', 6370997.0, 6370997.0");        // 20
        ellip.add(id++ + ",'Indonesian 1974', 6378160.0, 6356774.504086");    // 21
        ellip.add(id++ + ",'Everest (Pakistan)', 6377309.613, 6356108.570542");    // 22
        ellip.add(id++ + ",'Bessel 1841 (Japan)', 6377397.155, 6356078.963");    // 23
        ellip.add(id++ + ",'Bessel 1841 (Namibia)', 6377483.865, 6356165.382966");    // 24
        ellip.add(id++ + ",'Everest 1956', 6377301.243, 6356100.228368");    // 25
        ellip.add(id++ + ",'Everest 1969', 6377295.664, 6356094.667915");    // 26
        ellip.add(id++ + ",'Everest', 6377298.556, 6356097.550301");    // 27
        ellip.add(id++ + ",'Helmert 1906', 6378200.0, 6356818.169628");    // 28
        ellip.add(id++ + ",'SGS 85', 6378136.0, 6356751.301569");    // 29
        ellip.add(id++ + ",'WGS 60', 6378165.0, 6356783.286959");    // 30
        ellip.add(id++ + ",'South American 1969',6378160.0, 6356774.719");    // 31
        ellip.add(id++ + ",'ATS77',	6378135.0, 6356750.304922");    // 32

        for (String s : ellip) {
            String insertEllipsoid = insertStart + s + insertEnd;
            Log.d(TAG, insertEllipsoid);
            db.execSQL(insertEllipsoid);
        }

        String createProjection =
                "CREATE TABLE PROJECTION (" +
                        "_ID INTEGER PRIMARY KEY," +
                        "NAME TEXT," +
                        "FALSE_E REAL," +
                        "FALSE_N REAL, " +
                        "LAT0 REAL, " +
                        "LON0 REAL, " +
                        "K0 REAL, " +
                        "ELLIPSOID_ID INTEGER, " +
                        "PROJ_TYPE INTEGER);";
        db.execSQL(createProjection);

        id = 1;
        insertStart = "INSERT INTO PROJECTION VALUES(";
        insertEnd = ");";

        List<String> proj = new ArrayList<String>();
        proj.add(id++ + ", 'UK National Grid', 400000.0, -100000.0, " + (49.0 * Math.PI / 180.0) + ", " + (-2.0 * Math.PI / 180.0) + ", 0.9996013, 10, " + Projection.SYS_TYPE_TM);
        proj.add(id++ + ", 'UTM (WGS 1984)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 13, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Intl 1924)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 6, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Clarke 1866)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 1, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Clarke 1866)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 2, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Bessel 1841)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 3, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Airy 1830)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 4, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (New Intl 1967)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 5, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (WGS 1972)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 7, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest 1830)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 8, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (WGS 1966)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 9, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (GRS 1980)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 10, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest 1948)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 11, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Modified Airy)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 12, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Modified Fisher 1960)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 14, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Australian Nat 1965)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 15, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Krassovsky 1940)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 16, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Hough 1960)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 17, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Fisher 1960)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 18, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Fisher 1968)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 19, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Indonesian 1974)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 21, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest Pakistan)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 22, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Bessel 1841 Japan)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 23, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Bessel 1841 Namibia)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 24, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest 1956)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 25, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest 1969)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 26, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Everest)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 27, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (Helmert 1906)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 28, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (SGS 85)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 29, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (WGS 60)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 30, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (South American 1969)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 31, " + Projection.SYS_TYPE_UTM);
        proj.add(id++ + ", 'UTM (ATS77)', 500000.0, 0.0, 0.0, 0.0, 0.9996, 32, " + Projection.SYS_TYPE_UTM);

        for (String s : proj) {
            String insertProjection = insertStart + s + insertEnd;
            Log.d(TAG, insertProjection);
            db.execSQL(insertProjection);
        }

        String createClimb =
                "CREATE TABLE CLIMB (" +
                        "ID INTEGER PRIMARY KEY," +
                        "NAME TEXT);";
        db.execSQL(createClimb);

        String createClimbPoint =
                "CREATE TABLE CLIMB_POINT (" +
                        "ID INTEGER," +
                        "POINT_NO INTEGER," +
                        "LAT REAL," +
                        "LON REAL," +
                        "EASTING REAL," +
                        "NORTHING REAL," +
                        "ELEVATION REAL," +
                        "PRIMARY KEY (ID, POINT_NO));";
        db.execSQL(createClimbPoint);

        String createClimbAttempt =
                "CREATE TABLE CLIMB_ATTEMPT (" +
                        "ID INTEGER," +
                        "ATTEMPT_ID INTEGER," +
                        "TIMESTAMP INTEGER, " +
                        "DURATION INTEGER," +
                        "PRIMARY KEY (ID, ATTEMPT_ID));";
        db.execSQL(createClimbAttempt);

        String createClimbAttemptPoints =
                "CREATE TABLE CLIMB_ATTEMPT_POINT (" +
                        "ID INTEGER," +
                        "ATTEMPT_ID INTEGER," +
                        "POINT_NO INTEGER," +
                        "TIMESTAMP INTEGER, " +
                        "LAT REAL," +
                        "LON REAL," +
                        "EASTING REAL," +
                        "NORTHING REAL," +
                        "PRIMARY KEY (ID, ATTEMPT_ID, POINT_NO));";
        db.execSQL(createClimbAttemptPoints);
        onUpgrade(db, 1, DATABASE_VERSION);
    }

    public static Projection getProjection(int id) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();

        try (Cursor cursor = db.query("PROJECTION", new String[] {"NAME", "FALSE_E", "FALSE_N", "LAT0", "LON0", "K0", "ELLIPSOID_ID", "PROJ_TYPE"}, "_id=?",
                    new String[] {String.valueOf(id)}, null, null, null, null)) {
            if (cursor != null) {
                cursor.moveToFirst();
                return new Projection(id, cursor.getString(0), cursor.getDouble(1), cursor.getDouble(2),
                        cursor.getDouble(3), cursor.getDouble(4), cursor.getDouble(5),
                        getEllipsoid(cursor.getInt(6)), cursor.getInt(7));
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up projection: " + e.getMessage());
        }

        return null;
    }

    public static Ellipsoid getEllipsoid(int id) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();

        try (Cursor cursor = db.query("ELLIPSOID", new String[] {"NAME", "RADIUS_A", "RADIUS_B"}, "_id=?",
                    new String[] {String.valueOf(id)}, null, null, null, null)) {
            if (cursor != null) {
                cursor.moveToFirst();
                return new Ellipsoid(id, cursor.getString(0), cursor.getDouble(1), cursor.getDouble(2));
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up ellipsoid: " + e.getMessage());
        }

        return null;
    }

    public boolean addClimb(GPXFile gpx) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        String name = gpx.getMetadata().getName();

        Log.d(TAG, "find climb: " + name);
        int id = findClimb(name);
        Log.d(TAG, "id: " + id);

        return add(db, gpx, "climb", name, id);
    }

    private boolean add(SQLiteDatabase db, GPXFile gpx, String type, String name, int id) {
        if (id > 0) {
            int zone = 0;
            if (!gpx.getRoute().getPoints().isEmpty()) {
                zone = GeoConvert.calcUTMZone(gpx.getRoute().getPoints().get(0).getLat(),
                        gpx.getRoute().getPoints().get(0).getLon());
            }
            String table1Name = "climb".equals(type) ? "CLIMB" : "ROUTE";
            String table2Name = "climb".equals(type) ? "CLIMB_POINT" : "ROUTE_POINT";

            String insert = "INSERT INTO " + table1Name + " VALUES(" + id + ",'" +
                    name + "'," +
                    Projection.SYS_UTM_WGS84 + "," +
                    zone + ")";
            db.execSQL(insert);

            int i = 1;

            for (RoutePoint pt : gpx.getRoute().getPoints()) {
                RoutePoint gridPoint = GeoConvert.convertLLToGrid(getProjection(Projection.SYS_UTM_WGS84), pt, zone);
                gridPoint.setElevation(pt.getElevation());
                String insertPoint = "INSERT INTO " + table2Name + " VALUES(" + id + "," +
                        i + "," +
                        pt.getLat() + "," +
                        pt.getLon() + "," +
                        gridPoint.getEasting() + "," +
                        gridPoint.getNorthing() + "," +
                        gridPoint.getElevation() + ")";
                db.execSQL(insertPoint);
                i++;
            }
        } else {
            return false;
        }

        return true;
    }

    public boolean addRoute(GPXFile gpx) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        String name = gpx.getMetadata().getName();

        Log.d(TAG, "find route: " + name);
        int id = findRoute(name);
        Log.d(TAG, "id: " + id);

        return add(db, gpx, "route", name, id);
    }

    public int findClimb(String name) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        return find(db, "climb", name);
    }

    public int findRoute(String name) {
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        return find(db, "route", name);
    }

    private int find(SQLiteDatabase db, String type, String name) {
        String tableName = "climb".equals(type) ? "CLIMB" : "ROUTE";

        try (Cursor cursor = db.query(tableName, new String[] {"ID"}, "name=?",
                new String[] {name}, null, null, null, null)){
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                Log.d(TAG, "Found Id: " + cursor.getInt(0));
                return -1;
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up climb/route: " + e.getMessage());
        }

        try (Cursor cursor = db.rawQuery("SELECT MAX(ID) FROM " + tableName, null)) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                return cursor.getInt(0) + 1;
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up max id: " + e.getMessage());
        }

        // Must be empty table so return 1
        return 1;
    }

    public GPXRoute getClimb(int id) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT a.name, a.projection_id, a.zone, b.point_no, b.easting, b.northing, b.elevation, b.lat, b.lon " +
                "FROM CLIMB a INNER JOIN CLIMB_POINT b " +
                "ON a.id = b.id " +
                "WHERE a.id = ? " +
                "ORDER BY b.point_no";

        try (Cursor cursor = db.rawQuery(query, new String[] {String.valueOf(id)})){
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();
                GPXRoute climb = new GPXRoute();
                climb.setId(id);
                climb.setName(cursor.getString(0));
                climb.setProjectionId(cursor.getInt(1));
                climb.setZone(cursor.getInt(2));

                List<RoutePoint> points = new ArrayList<>();
                while (!cursor.isAfterLast()) {
                    RoutePoint point = new RoutePoint();
                    point.setNo(cursor.getInt(3));
                    point.setEasting(cursor.getDouble(4));
                    point.setNorthing(cursor.getDouble(5));
                    point.setElevation(cursor.getDouble(6));
                    point.setLat(cursor.getDouble(7));
                    point.setLon(cursor.getDouble(8));
                    points.add(point);
                    cursor.moveToNext();
                }
                climb.setPoints(points);
                return climb;
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up climb: " + e.getMessage());
        }
        return null;
    }

    public GPXRoute getRoute(int id) {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT a.name, a.projection_id, a.zone, b.point_no, b.easting, b.northing, b.elevation, b.lat, b.lon " +
                "FROM ROUTE a INNER JOIN ROUTE_POINT b " +
                "ON a.id = b.id " +
                "WHERE a.id = ? " +
                "ORDER BY b.point_no";

        try (Cursor cursor = db.rawQuery(query, new String[] {String.valueOf(id)})){
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();
                GPXRoute climb = new GPXRoute();
                climb.setId(id);
                climb.setName(cursor.getString(0));
                climb.setProjectionId(cursor.getInt(1));
                climb.setZone(cursor.getInt(2));

                List<RoutePoint> points = new ArrayList<>();
                while (!cursor.isAfterLast()) {
                    RoutePoint point = new RoutePoint();
                    point.setNo(cursor.getInt(3));
                    point.setEasting(cursor.getDouble(4));
                    point.setNorthing(cursor.getDouble(5));
                    point.setElevation(cursor.getDouble(6));
                    point.setLat(cursor.getDouble(7));
                    point.setLon(cursor.getDouble(8));
                    points.add(point);
                    cursor.moveToNext();
                }
                climb.setPoints(points);
                return climb;
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up route: " + e.getMessage());
        }
        return null;
    }

    public GPXRoute[] getClimbs() {
        Log.d(TAG, "Fetch all climbs");
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        String query = "SELECT a.id, a.name, a.zone, b.easting, b.northing, c.easting, c.northing " +
                "FROM CLIMB a INNER JOIN CLIMB_POINT b " +
                "ON a.id = b.id " +
                "INNER JOIN CLIMB_POINT c " +
                "ON a.id = c.id " +
                "WHERE b.point_no = 1 " +
                "AND c.point_no = 2 " +
                "ORDER BY a.name, b.point_no, c.point_no";
        List<GPXRoute> climbs = new ArrayList<>();

        try (Cursor cursor = db.rawQuery(query, null)){
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();

                while (!cursor.isAfterLast()) {
                    GPXRoute climb = new GPXRoute();
                    climb.setId(cursor.getInt(0));
                    climb.setName(cursor.getString(1));
                    climb.setZone(cursor.getInt(2));
                    RoutePoint first = new RoutePoint();
                    RoutePoint second = new RoutePoint();
                    first.setEasting(cursor.getDouble(3));
                    first.setNorthing(cursor.getDouble(4));
                    second.setEasting(cursor.getDouble(5));
                    second.setNorthing(cursor.getDouble(6));
                    climb.addPoint(first);
                    climb.addPoint(second);
                    climbs.add(climb);
                    cursor.moveToNext();
                }
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up climb: " + e.getMessage());
        }
        return climbs.toArray(new GPXRoute[0]);
    }

    public GPXRoute[] getRoutes() {
        Log.d(TAG, "Fetch all routes");
        SQLiteDatabase db = Database.getInstance().getReadableDatabase();
        String query = "SELECT a.id, a.name, a.zone, b.easting, b.northing, c.easting, c.northing " +
                "FROM ROUTE a INNER JOIN ROUTE_POINT b " +
                "ON a.id = b.id " +
                "INNER JOIN ROUTE_POINT c " +
                "ON a.id = c.id " +
                "WHERE b.point_no = 1 " +
                "AND c.point_no = 2 " +
                "ORDER BY a.name, b.point_no, c.point_no";
        List<GPXRoute> routes = new ArrayList<>();

        try (Cursor cursor = db.rawQuery(query, null)){
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();

                while (!cursor.isAfterLast()) {
                    GPXRoute route = new GPXRoute();
                    route.setId(cursor.getInt(0));
                    route.setName(cursor.getString(1));
                    route.setZone(cursor.getInt(2));
                    RoutePoint first = new RoutePoint();
                    RoutePoint second = new RoutePoint();
                    first.setEasting(cursor.getDouble(3));
                    first.setNorthing(cursor.getDouble(4));
                    second.setEasting(cursor.getDouble(5));
                    second.setNorthing(cursor.getDouble(6));
                    route.addPoint(first);
                    route.addPoint(second);
                    routes.add(route);
                    cursor.moveToNext();
                }
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up climb: " + e.getMessage());
        }
        return routes.toArray(new GPXRoute[0]);
    }

    public boolean addAttempt(ClimbAttempt attempt, int climbId) {
        SQLiteDatabase db = getReadableDatabase();
        int id = getAttemptId(climbId, attempt.getDatetime().atZone(ZoneId.systemDefault()).toEpochSecond());

        Log.d(TAG, "attempt id: " + id);

        if (id > 0) {
            String insert = "INSERT INTO CLIMB_ATTEMPT VALUES(" + climbId + "," +
                    id + "," +
                    attempt.getDatetime().atZone(ZoneId.systemDefault()).toEpochSecond() + "," +
                    attempt.getDuration() + ");";

            db.execSQL(insert);

            int i = 1;

            for (AttemptPoint pt : attempt.getPoints()) {
                String insertPoint = "INSERT INTO CLIMB_ATTEMPT_POINT VALUES(" + climbId + "," +
                        id + "," +
                        i + "," +
                        pt.getTimestamp().atZone(ZoneId.systemDefault()).toEpochSecond() + "," +
                        pt.getPoint().getLat() + "," +
                        pt.getPoint().getLon() + "," +
                        pt.getPoint().getEasting() + "," +
                        pt.getPoint().getNorthing() +");";
                db.execSQL(insertPoint);
                i++;
            }

            // Remove points for all but the last attempt and the PB
            tidyUp(db, climbId);
        } else {
            return false;
        }

        Log.d(TAG, "Attempt added: " + id);
        return true;
    }

    public void deleteClimb(int climbId) {
        SQLiteDatabase db = getReadableDatabase();

        String del = "DELETE FROM CLIMB_ATTEMPT WHERE id = " + climbId;
        db.execSQL(del);
        del = "DELETE FROM CLIMB WHERE id = " + climbId;
        db.execSQL(del);
    }

    public void deleteRoute(int routeId) {
        SQLiteDatabase db = getReadableDatabase();

        String del = "DELETE FROM ROUTE WHERE id = " + routeId;
        db.execSQL(del);
        del = "DELETE FROM ROUTE_POINT WHERE id = " + routeId;
        db.execSQL(del);
    }

    private void tidyUp(SQLiteDatabase db, int climbId) {
        // Find most recent attempt id
        int lastAttemptId = -1;
        String query = "SELECT attempt_id " +
                "FROM CLIMB_ATTEMPT  " +
                "WHERE id = ? " +
                "ORDER BY timestamp DESC " +
                "LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(climbId)})) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                lastAttemptId = cursor.getInt(0);
                Log.d(TAG, "Last attempt id: " + lastAttemptId);
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up last attempt: " + e.getMessage());
        }

        // Get pbId
        int pbId = -1;
        query = "SELECT attempt_id " +
                "FROM CLIMB_ATTEMPT  " +
                "WHERE id = ? " +
                "ORDER BY duration ASC " +
                "LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(climbId)})) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                pbId = cursor.getInt(0);
                Log.d(TAG, "PB Id: " + pbId);
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up PB: " + e.getMessage());
        }

        // Now delete all attempt points except those for last attempt and PB
        String del = "DELETE FROM CLIMB_ATTEMPT_POINT WHERE attempt_id NOT IN (" + lastAttemptId + "," + pbId + ")";
        db.execSQL(del);
    }

    public ClimbAttempt getClimbPB(int climbId) {
        SQLiteDatabase db = getReadableDatabase();
        ClimbAttempt pb = new ClimbAttempt();
        int pbId;

        String query = "SELECT attempt_id, duration, timestamp " +
                "FROM CLIMB_ATTEMPT  " +
                "WHERE id = ? " +
                "ORDER BY DURATION ASC";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(climbId)})) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();
                pbId = cursor.getInt(0);
                Log.d(TAG, "PB: ClimbId " + climbId + "; Attempt: " + pbId);

                LocalDateTime datetime =
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(cursor.getInt(2)), ZoneId.systemDefault());
                pb.setDatetime(datetime);
                pb.setDuration(cursor.getInt(1));
            } else {
                return null;
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up attempts: " + e.getMessage());
            return null;
        }

        if (pbId > 0) {
            // Fetch the full attempt
            String attemptQuery = "SELECT point_no, timestamp, easting, northing, lat, lon " +
                    "FROM CLIMB_ATTEMPT_POINT " +
                    "WHERE ATTEMPT_ID = ? " +
                    "AND ID = ? " +
                    "ORDER BY POINT_NO ASC";
            try (Cursor cursor = db.rawQuery(attemptQuery, new String[]{String.valueOf(pbId), String.valueOf(climbId)})) {
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        RoutePoint pt = new RoutePoint();
                        pt.setEasting(cursor.getFloat(2));
                        pt.setNorthing(cursor.getFloat(3));
                        pt.setLat(cursor.getFloat(4));
                        pt.setLon(cursor.getFloat(5));
                        LocalDateTime datetime =
                                LocalDateTime.ofInstant(Instant.ofEpochSecond(cursor.getInt(1)), ZoneId.systemDefault());
                        pb.addPoint(pt, datetime);
                        cursor.moveToNext();
                    }
                }
            } catch (SQLException e) {
                Log.d(TAG, "Error looking up attempt: " + e.getMessage());
            }
        }
        Log.d(TAG, "Got PB - duration " + pb.getDuration() + " seconds");
        return pb;
    }


    public AttemptStats getLastAttempt(int climbId) {
        SQLiteDatabase db = getReadableDatabase();
        AttemptStats attempt = new AttemptStats();

        String query = "SELECT attempt_id, duration " +
                "FROM CLIMB_ATTEMPT  " +
                "WHERE id = ? " +
                "ORDER BY attempt_id DESC";

        // Set stats for this attempt
        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(climbId)})) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                boolean first = true;
                int pbDuration = Integer.MAX_VALUE;
                int pos = 1;
                int total = 0;

                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    total++;
                    int duration = cursor.getInt(1);
                    if (first) {
                        attempt.setId(cursor.getInt(0));
                        attempt.setDuration(duration);
                        first = false;
                    } else if (duration < attempt.getDuration()) {
                        pos++;
                    }

                    if (duration < pbDuration) {
                        attempt.setPb(duration);
                        pbDuration = duration;
                    }
                    cursor.moveToNext();
                }
                attempt.setPos(pos);
                attempt.setTotal(total);
                return attempt;
            } else {
                return null;
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up attempts: " + e.getMessage());
            return null;
        }
    }

    private int getAttemptId(int climbId, long timestamp) {
        SQLiteDatabase db = getReadableDatabase();
        String check = "SELECT ATTEMPT_ID " +
                "FROM CLIMB_ATTEMPT " +
                "WHERE id = ? " +
                "AND TIMESTAMP = ?";

        try (Cursor cursor = db.rawQuery(check, new String[]{String.valueOf(climbId), String.valueOf(timestamp)})) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Attempt exists");
                return -1;
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up id: " + e.getMessage());
        }

        String query = "SELECT MAX(ATTEMPT_ID) " +
                "FROM CLIMB_ATTEMPT " +
                "WHERE id = ? " +
                "GROUP BY id";

        try (Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(climbId)})) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                Log.d(TAG, "Max Id: " + cursor.getInt(0));
                return cursor.getInt(0) + 1;
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up max id: " + e.getMessage());
        }

        // No max found so return 1st attempt
        return 1;
    }

    public boolean attemptExists(LocalDateTime trackTime) {
        long timestamp = trackTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        SQLiteDatabase db = getReadableDatabase();
        String check = "SELECT ATTEMPT_ID " +
                "FROM CLIMB_ATTEMPT " +
                "WHERE TIMESTAMP = ?";

        try (Cursor cursor = db.rawQuery(check, new String[]{String.valueOf(timestamp)})) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Attempt at same time exists");
                return true;
            }
        } catch (SQLException e) {
            Log.d(TAG, "Error looking up id: " + e.getMessage());
        }
        return false;
    }

    private void migrateToUTM(SQLiteDatabase db, int toId) {
        Log.d(TAG, "Fetch all climbs");
        DecimalFormat formatter = new DecimalFormat("#.###");
        String query = "SELECT id, name FROM CLIMB;";
        Projection proj = null;
        int zone = 0;

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();

                while (!cursor.isAfterLast()) {
                    int id = cursor.getInt(0);
                    Log.d(TAG, "Migrating: " + cursor.getString(1) + " [" + id + "]");

                    // Get all lat/long points for climb
                    GPXRoute route = getClimb(id);

                    // Figure out UTM zone from first point
                    if (route != null && !route.getPoints().isEmpty()) {
                        RoutePoint ll = route.getPoints().get(0);
                        zone = GeoConvert.calcUTMZone(ll.getLat(), ll.getLon());
                        proj = getProjection(toId);

                        // Update climb table
                        Log.d(TAG, route.getName() + " - Proj: " + toId + "; Zone: " + zone);
                        query = "UPDATE CLIMB " +
                                "SET projection_id = " + toId + "," +
                                "zone = " + zone +
                                " WHERE id = " + id;
                        db.execSQL(query);

                        for (RoutePoint pt : route.getPoints()) {
                            RoutePoint newPt = GeoConvert.convertLLToGrid(proj, pt, zone);
                            int ptId = pt.getNo();

                            // Update grid points
                            Log.d(TAG, "Point: " + ptId + " - E: " + newPt.getEasting() + ", N: " + newPt.getNorthing());
                            query = "UPDATE CLIMB_POINT " +
                                    "SET easting = " + formatter.format(newPt.getEasting()) + "," +
                                    "northing = " + formatter.format(newPt.getNorthing()) +
                                    " WHERE id = " + id +
                                    " AND point_no = " + ptId + ";";
                            db.execSQL(query);
                        }
                    }

                    // Get all lat/long points for climb attempts
                    Map<Integer, ClimbAttempt> climbAttempts = getAllAttemptPoints(id);

                    // Loop through all attempts and points
                    if (climbAttempts != null && !climbAttempts.isEmpty()) {
                        for (Map.Entry<Integer, ClimbAttempt> attempt : climbAttempts.entrySet()) {
                            int attemptId = attempt.getKey();
                            int pointNo = 1;

                            for (AttemptPoint pt : attempt.getValue().getPoints()) {
                                RoutePoint newPt = GeoConvert.convertLLToGrid(proj, pt.getPoint(), zone);

                                // Update grid points
                                Log.d(TAG, "Attempt: " + attemptId + ", Point: " + pointNo + " - E: " + newPt.getEasting() + ", N: " + newPt.getNorthing());
                                query = "UPDATE CLIMB_ATTEMPT_POINT " +
                                        "SET easting = " + formatter.format(newPt.getEasting()) + "," +
                                        "northing = " + formatter.format(newPt.getNorthing()) +
                                        " WHERE id = " + id +
                                        " AND attempt_id = " + attemptId +
                                        " AND point_no = " + pointNo + ";";
                                db.execSQL(query);
                                pointNo++;
                            }
                        }
                    }
                    cursor.moveToNext();
                }
            }
        }
    }

    private Map<Integer, ClimbAttempt> getAllAttemptPoints(int climbId) {
        SQLiteDatabase db = getReadableDatabase();
        Map<Integer, ClimbAttempt> attempts = new HashMap<>();

        String query = "SELECT attempt_id, point_no, lat, lon " +
                "FROM CLIMB_ATTEMPT_POINT " +
                "WHERE id = ? " +
                "ORDER BY attempt_id, point_no";

        try (Cursor cursor = db.rawQuery(query, new String[] {String.valueOf(climbId)})){
            if (cursor != null && cursor.getCount() > 0) {
                Log.d(TAG, "Found " + cursor.getCount());
                cursor.moveToFirst();
                int attemptId = -1;
                ClimbAttempt attempt = new ClimbAttempt();

                while (!cursor.isAfterLast()) {
                    int id = cursor.getInt(0);
                    if (id != attemptId) {
                        // This is a new attempt
                        attempt = new ClimbAttempt();
                        attempts.put(id, attempt);
                        attemptId = id;
                    }
                    RoutePoint pt = new RoutePoint();
                    pt.setLat(cursor.getDouble(2));
                    pt.setLon(cursor.getDouble(3));
                    attempt.addPoint(pt, null);
                    cursor.moveToNext();
                }
            }
        } catch(SQLException e) {
            Log.d(TAG, "Error looking up climb attempts: " + e.getMessage());
        }
        return attempts;
    }

    private void addRouteTables(SQLiteDatabase db) throws SQLException {
        String createRoute =
                "CREATE TABLE ROUTE (" +
                        "ID INTEGER PRIMARY KEY," +
                        "NAME TEXT);";
        db.execSQL(createRoute);

        String createRoutePoint =
                "CREATE TABLE ROUTE_POINT (" +
                        "ID INTEGER," +
                        "POINT_NO INTEGER," +
                        "LAT REAL," +
                        "LON REAL," +
                        "EASTING REAL," +
                        "NORTHING REAL," +
                        "ELEVATION REAL," +
                        "PRIMARY KEY (ID, POINT_NO));";
        db.execSQL(createRoutePoint);
    }

    @Override
    public SQLiteDatabase getReadableDatabase() {
        if(isUpgrading && currentDb != null){
            return currentDb;
        }
        return super.getReadableDatabase();
    }

    public String backup() {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder backupStr = new StringBuilder();

        backupStr.append(backupTable(db, "CLIMB", CLIMB_COLUMNS))
                 .append(backupTable(db, "CLIMB_POINT", CLIMB_POINT_COLUMNS))
                 .append(backupTable(db, "CLIMB_ATTEMPT", CLIMB_ATTEMPT_COLUMNS))
                 .append(backupTable(db, "CLIMB_ATTEMPT_POINT", CLIMB_ATTEMPT_POINT_COLUMNS));
        return backupStr.toString();
    }

    private String backupTable(SQLiteDatabase db, String table, List<String> columns) {
        try {
            StringBuilder backup = new StringBuilder();

            // Delimit records with ~ and fields with |
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            boolean first = true;
            for (String column : columns) {
                if (!first) {
                    sql.append(",");
                }
                sql.append(column);
                first = false;
            }

            sql.append(" FROM ");
            sql.append(table);
            try (Cursor cursor = db.rawQuery(sql.toString(), null)) {
                if (cursor != null && cursor.getCount() > 0) {
                    Log.d(TAG, "Found " + cursor.getCount());
                    cursor.moveToFirst();

                    while (!cursor.isAfterLast()) {
                        backup.append(TableIdentifier.getAbbrev(table)).append("|");
                        for (int i=0; i<columns.size(); i++) {
                            backup.append(cursor.getString(i)).append(i<columns.size()-1 ? "|" : "~");
                        }
                        cursor.moveToNext();
                    }
                }
            }
            return backup.toString();
        } catch (SQLException e) {
            Log.e(TAG, "BACKUP ERROR: " + e.getMessage());
        }
        return null;
    }

    public void restore(String[] rows) {
        SQLiteDatabase db = getReadableDatabase();

        // Clear existing tables
        String del = "DELETE FROM CLIMB;";
        db.execSQL(del);
        del = "DELETE FROM CLIMB_POINT;";
        db.execSQL(del);
        del = "DELETE FROM CLIMB_ATTEMPT;";
        db.execSQL(del);
        del = "DELETE FROM CLIMB_ATTEMPT_POINT;";
        db.execSQL(del);

        Map<String, List<String>> tableColumns = new HashMap<>();
        tableColumns.put("CLIMB", CLIMB_COLUMNS);
        tableColumns.put("CLIMB_POINT", CLIMB_POINT_COLUMNS);
        tableColumns.put("CLIMB_ATTEMPT", CLIMB_ATTEMPT_COLUMNS);
        tableColumns.put("CLIMB_ATTEMPT_POINT", CLIMB_ATTEMPT_POINT_COLUMNS);

        for (String row : rows) {
            String[] values = row.split("\\|");
            ContentValues cv = new ContentValues();
            String tableName = TableIdentifier.getEnumFromAbbrev(values[0]).name();
            List<String> cols = tableColumns.get(tableName);
            int i = 1;

            for (String col : cols) {
                cv.put(col, values[i++]);
            }

            db.insertOrThrow(tableName, null, cv);
        }
    }
 }
