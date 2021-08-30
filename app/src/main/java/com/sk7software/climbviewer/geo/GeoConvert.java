package com.sk7software.climbviewer.geo;

import com.sk7software.climbviewer.model.RoutePoint;

public class GeoConvert {

    // OSTN02 array of shifts to apply to calculated Easting
    // Sampled at 50km intervals from full matrix
    // Index 0, 0 refers to SW corner of area
    private static final double[][] eShiftArr =
            {
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	92.84,	93.685,	94.083,	94.4,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	93.822,	94.513,	94.963,	95.858,	97.072,	97.612,	98.562,	0,	0,	0},
                    {0,	0,	0,	0,	0,	93.986,	94.682,	95.444,	95.988,	96.766,	97.842,	98.856,	100.014,	0,	0},
                    {0,	0,	0,	0,	93.455,	94.024,	94.647,	95.658,	96.15,	96.919,	97.992,	99.133,	100.335,	0,	0},
                    {0,	0,	0,	0,	92.9,	93.806,	94.623,	95.471,	96.381,	97.277,	98.378,	99.18,	100.418,	101.823,	0},
                    {0,	0,	0,	0,	0,	93.445,	94.596,	95.618,	96.698,	97.823,	99.137,	100.167,	101.276,	102.66,	0},
                    {0,	0,	0,	0,	0,	93.453,	94.557,	95.779,	97.085,	98.556,	99.791,	101.173,	102.087,	0,	0},
                    {0,	0,	0,	0,	0,	93.536,	0,	96.02,	97.256,	98.978,	100.508,	101.99,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	95.918,	97.867,	99.478,	101.097,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	93.255,	94.907,	96.548,	98.189,	99.997,	101.805,	0,	0,	0,	0},
                    {0,	0,	0,	0,	90.588,	93.047,	95.06,	96.867,	98.538,	100.345,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	90.708,	93.001,	94.984,	97.017,	98.844,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	88.634,	90.841,	93.159,	95.174,	97.219,	99.034,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	89.345,	91.446,	93.459,	95.431,	97.355,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	88.093,	90.081,	92.012,	93.909,	95.775,	97.654,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	90.791,	92.712,	94.396,	96.077,	97.69,	99.552,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	91.53,	93.221,	94.786,	96.226,	97.798,	99.371,	0,	0,	0,	0,	0,	0},
                    {0,	0,	90.745,	92.297,	93.951,	95.23,	96.783,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	93.227,	0,	95.85,	97.142,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	98.555,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	98.948,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	101.322,	102.095,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	102.762,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0}
            };

    // OSTN02 array of shifts to apply to calculated Northing
    // Index 0, 0 is SW corner
    private static final double[][] nShiftArr =
            {
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-80.041,	-79.517,	-79.517,	-79.55,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	-78.834,	-78.743,	-78.892,	-79.112,	-79.169,	-79.401,	-80.178,	0,	0,	0},
                    {0,	0,	0,	0,	0,	-78.228,	-78.196,	-78.047,	-78.18,	-78.657,	-78.706,	-79.122,	-79.623,	0,	0},
                    {0,	0,	0,	0,	-77.896,	-77.531,	-77.448,	-77.474,	-77.793,	-77.998,	-78.262,	-78.365,	-78.895,	0,	0},
                    {0,	0,	0,	0,	-77.094,	-76.799,	-76.611,	-76.84,	-77.28,	-77.501,	-77.657,	-77.858,	-78.428,	-78.939,	0},
                    {0,	0,	0,	0,	0,	-75.787,	-75.783,	-75.969,	-76.263,	-76.734,	-76.887,	-77.143,	-77.795,	-78.396,	0},
                    {0,	0,	0,	0,	0,	-74.421,	-74.434,	-74.686,	-75.012,	-75.319,	-75.994,	-76.544,	-76.963,	0,	0},
                    {0,	0,	0,	0,	0,	-73.128,	0,	-73.235,	-73.564,	-74.025,	-74.468,	-75.071,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	-71.509,	-71.824,	-72.373,	-72.864,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	-69.513,	-69.497,	-69.622,	-69.956,	-70.435,	-70.886,	0,	0,	0,	0},
                    {0,	0,	0,	0,	-68.074,	-68.046,	-67.724,	-68.006,	-68.282,	-68.71,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	-65.576,	-65.887,	-66.016,	-66.028,	-66.274,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-62.798,	-63.339,	-63.562,	-63.982,	-64.11,	-64.215,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-60.404,	-61.221,	-61.658,	-61.995,	-62.222,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	-57.956,	-58.579,	-59.202,	-59.629,	-60.143,	-60.382,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-56.617,	-57.461,	-57.963,	-58.443,	-58.661,	-58.588,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-54.996,	-55.718,	-56.305,	-56.83,	-56.935,	-57.241,	0,	0,	0,	0,	0,	0},
                    {0,	0,	-52.499,	-53.483,	-54.194,	-54.884,	-55.564,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	-51.887,	0,	-53.693,	-54.101,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	-53.477,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	-52.449,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	-51.236,	-51.726,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	-51.08,	0,	0,	0,	0,	0},
                    {0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0,	0}
            };

    public static RoutePoint convertLLToGrid(Projection proj, RoutePoint loc, int zone)
    {
        RoutePoint gridLoc = null;

        if (proj == null) return null;

        // Call appropriate conversion function
        switch (proj.getProjType())
        {
            case Projection.SYS_TYPE_TM:
                gridLoc = TMConvertLLToGrid(loc, proj);

                // Special case for OSGB transformation (apply shift)
                if (proj.getProjType() == Projection.SYS_OSGB36) convertOSGB(gridLoc, true);

                break;

            case Projection.SYS_TYPE_UTM:
//				// Get UTM zone parameters
//				SetUTMProjection(&proj, zone);
//
//				TMConvertLLToGrid(latDeg, lonDeg, ellip.a, ellip.b,
//							   proj.lat0, proj.lon0, proj.EF, proj.NF, proj.k0,
//						       east, north);
//				break;
//			default:
//				return retVal;
                break;
        }

        return gridLoc;
    }

    private static RoutePoint TMConvertLLToGrid(RoutePoint loc, Projection proj) {
        double 		latRad, lonRad;

        double		v;
        double		f;
        double		e;
        double		ee;
        double		r;

        double		M;
        double		M0;

        double		e2, e4, e6;
        double		sinLatRad;
        double		sin2LatRad;
        double		cosLatRad;

        RoutePoint gridLoc = new RoutePoint();

        latRad = loc.getLat() * Math.PI/180.0;
        lonRad = loc.getLon() * Math.PI/180.0;

        // Set up parameters
        f = (proj.getE().getRadiusA() - proj.getE().getRadiusB())/proj.getE().getRadiusA();
        e = Math.sqrt((2.0*f) - (f*f));

        e2 = e*e;
        e4 = e2*e2;
        e6 = e4*e2;

        sinLatRad = Math.sin(latRad);
        sin2LatRad = sinLatRad * sinLatRad;
        cosLatRad = Math.cos(latRad);

        ee = e/Math.sqrt(1 - e2);
        r = (proj.getE().getRadiusA() * (1 - e2))/Math.pow(1 - (e2*sin2LatRad),1.5);
        v = proj.getE().getRadiusA()/Math.pow(1 - (e2*sin2LatRad),0.5);

        // Calculate M and M0
        {
            double M1,M2,M3,M4;
            M1 = (1.0 - ((e2)/4) - ((3 * e4)/64) - ((5 * e6)/256)) * proj.getLat0();
            M2 = (((3 * e2)/8) + ((3 * e4)/32) + ((45 * e6)/1024)) * Math.sin(2.0*proj.getLat0());
            M3 = (((15 * e4)/256) + ((45 * e6)/1024)) * Math.sin(4.0*proj.getLat0());
            M4 = ((35 * e6)/3072) * Math.sin(6.0*proj.getLat0());
            M0 = proj.getE().getRadiusA() * (M1 - M2 + M3 - M4);

            M1 = (1.0 - (e2/4) - ((3 * e4)/64) - ((5 * e6)/256)) * latRad;
            M2 = (((3 * e2)/8) + ((3 * e4)/32) + ((45 * e6)/1024)) * Math.sin(2.0*latRad);
            M3 = (((15 * e4)/256) + ((45 * e6)/1024)) * Math.sin(4.0*latRad);
            M4 = ((35 * e6)/3072) * Math.sin(6.0*latRad);
            M = proj.getE().getRadiusA() * (M1 - M2 + M3 - M4);
        }

        // Calculate easting and northing
        {
            double	A;
            double	T;
            double	C;

            double	E1, E2;
            double	N1, N2, N3;

            T = Math.pow(Math.tan(latRad),2);
            C = (ee*ee) * Math.pow(cosLatRad,2);
            A = (lonRad - proj.getLon0()) * cosLatRad;

            E1 = (1.0 - T + C) * (A*A*A/6);
            E2 = (5 - (18*T) + (T*T) + (72*C) - (58*ee*ee)) * (Math.pow(A,5)/120);
            gridLoc.setEasting(proj.getFalseE() + ((proj.getK0() * v) * (A + E1 + E2)));

            N1 = (A*A)/2;
            N2 = (5 - T + (9*C) + 4*(C*C)) * Math.pow(A,4)/24;
            N3 = (61 - (58*T) + (T*T) + (600*C) - (330*(ee*ee))) * Math.pow(A,6)/720;
            gridLoc.setNorthing(proj.getFalseN() + (proj.getK0() * (M - M0 + (v * Math.tan(latRad) * (N1 + N2 + N3)))));
        }

        return gridLoc;
    }


    private static RoutePoint convertOSGB(RoutePoint gridLoc, boolean reverseTrans)
    {
        // Four corners of the grid cell
        double e0, e1, e2, e3;
        double n0, n1, n2, n3;

        double x0, y0;
        double cellWid, cellHt;
        double dx, dy;
        double t, u;
        double eShift, nShift;

        RoutePoint OSGB = new RoutePoint();
        OSGB.setEasting(gridLoc.getEasting());
        OSGB.setNorthing(gridLoc.getNorthing());
        RoutePoint last = new RoutePoint();
        last.setEasting(gridLoc.getEasting());
        last.setNorthing(gridLoc.getNorthing());

        // Sampling interval of matrix of shifts (m)
        final double resolution = 50000.0;

        boolean finished = false;

        while (!finished)
        {
            // Truncate E and N to whole kms
            RoutePoint rndLoc = new RoutePoint();
            rndLoc.setEasting(gridLoc.getEasting()/1000.0);
            rndLoc.setNorthing(gridLoc.getNorthing()/1000.0);

            // Find the offset cell index that represents the NW corner of the
            // square containing the location
            int cellE = (int)(rndLoc.getEasting()/(resolution/1000.0));
            int cellN = (int)(rndLoc.getNorthing()/(resolution/1000.0));
            //System.out.println(cellE + "," + cellN);

            // Check limits
            if ((cellE > 13) || (cellN > 24))
            {
                // Out of range, so return without applying shift
                return gridLoc;
            }

            // Get values from this cell and surrounding cells
            cellWid = resolution;
            cellHt = resolution;

            // Find the offsets at the four corners
            e0 = eShiftArr[cellN][cellE];
            e1 = eShiftArr[cellN][cellE+1];
            e2 = eShiftArr[cellN+1][cellE+1];
            e3 = eShiftArr[cellN+1][cellE];
            //System.out.println("e: " +e0+","+e1+","+e2+","+e3);

            n0 = nShiftArr[cellN][cellE];
            n1 = nShiftArr[cellN][cellE+1];
            n2 = nShiftArr[cellN+1][cellE+1];
            n3 = nShiftArr[cellN+1][cellE];

            // If any of the corners are zero, set to max of the other 4
            // This reduces the impact of interpolating between a cell with a value
            // and an adjacent one with a value of 0.  This is a feature of the sampling
            // and would not occur if the full 1km resolution matrix was used.
            double maxE = Math.max(Math.max(e0, e1), Math.max(e2, e3));
            if (e0 == 0.0) e0 = maxE;
            if (e1 == 0.0) e1 = maxE;
            if (e2 == 0.0) e2 = maxE;
            if (e3 == 0.0) e3 = maxE;
//			System.out.println("e: " +e0+","+e1+","+e2+","+e3);
            // Northing shifts are all negative, so look for lowest
            double maxN = Math.min(Math.min(n0, n1), Math.min(n2, n3));
            if (n0 == 0.0) n0 = maxN;
            if (n1 == 0.0) n1 = maxN;
            if (n2 == 0.0) n2 = maxN;
            if (n3 == 0.0) n3 = maxN;

            x0 = cellE * resolution;
            y0 = cellN * resolution;

            // Interpolate to get the actual shift
            dx = gridLoc.getEasting() - x0;
            dy = gridLoc.getNorthing() - y0;
            t = dx/cellWid;
            u = dy/cellHt;

            eShift = ((1-t)*(1-u)*e0) +
                    (t*(1-u)*e1) +
                    (t*u*e2) +
                    ((1-t)*u*e3);

            nShift = ((1-t)*(1-u)*n0) +
                    (t*(1-u)*n1) +
                    (t*u*n2) +
                    ((1-t)*u*n3);

            if (reverseTrans)
            {
                // Apply this shift to the easting and northing
                gridLoc.setEasting(gridLoc.getEasting() + eShift);
                gridLoc.setNorthing(gridLoc.getNorthing() + nShift);
                finished = true;
            }
            else
            {
                // Take this shift away from the easting and northing, then
                // iterate until the difference is negligible
                gridLoc.setEasting(OSGB.getEasting() - eShift);
                gridLoc.setNorthing(OSGB.getNorthing() - nShift);

                // Determine whether this is now close enough
                if (Math.abs(last.getEasting() - gridLoc.getEasting()) < 0.1 &&
                        Math.abs(last.getNorthing() - gridLoc.getNorthing()) < 0.1)
                {
                    finished = true;
                }
                else
                {
                    last.setEasting(gridLoc.getEasting());
                    last.setNorthing(gridLoc.getNorthing());
                }
            }
        }

        return gridLoc;
    }

/*	
	static void TMConvertGridToLL(double east, double north, double ellipa, double ellipb,
			      double projlat0, double projlon0, double projEF, double projNF, double projk0,
			      double *latDeg, double *lonDeg)
	{
		double		v;
		double		v1;
		double		f;
		double		e;
		double		ee;
		double		e1;
		double		r;
		double		r1;
	
		double		lat1;
		double		lat1a,lat1b,lat1c,lat1d;
		double		D;
		double		T1;
		double		C1;
		double		M0;
		double		M1;
		double		Ma,Mb,Mc,Md;
		double		u1;
	
		double		latRad;
		double		latRad1, latRad2;
		double		lonRad;
		double		lonRad1, lonRad2;
	
		double		e2, e4, e6;
		double		e1_2, e1_3, e1_4;
		double		sinLatRad; 
		double		sin2LatRad; 
		double		sinLat1; 
		double		sin2Lat1; 
	
		// Set up parameters
		f = (ellipa - ellipb)/ellipa;
		e = sqrt((2.0*f) - (f*f));
	
		e2 = e*e;
		e4 = pow(e,4);
		e6 = pow(e,6);
		sinLatRad = sin(latRad);
		sin2LatRad = sinLatRad * sinLatRad;
	
		ee = e/sqrt(1 - e2);
		e1 = (1 - sqrt(1 - e2))/(1 + sqrt(1 - e2));
		r = (ellipa * (1 - e2))/pow(1 - (e2*sin2LatRad),1.5);
		v = ellipa/pow(1 - (e2*sin2LatRad),0.5);
	
		e1_2 = e1*e1;
		e1_3 = pow(e1, 3);
		e1_4 = pow(e1, 4);
	
		Ma = (1.0 - ((e2)/4) - ((3 * e4)/64) - ((5 * e6)/256)) * projlat0;
		Mb = (((3 * e2)/8) + ((3 * e4)/32) + ((45 * e6)/1024)) * sin(2.0*projlat0);
		Mc = (((15 * e4)/256) + ((45 * e6)/1024)) * sin(4.0*projlat0);
		Md = ((35 * e6)/3072) * sin(6.0*projlat0);
		M0 = ellipa * (Ma - Mb + Mc - Md);
	
		M1 = M0 + (north - projNF)/projk0;
		u1 = M1/(ellipa * (1 - (e2/4) - (3*e4/64) - (5*e6/256)));
	
		lat1a = ((3*e1/2) - (27*e1_3/32)) * sin(2*u1);
		lat1b = ((21*e1_2/16) - (55*e1_4/32)) * sin(4*u1);
		lat1c = (151*e1_3/96) * sin(6*u1);
		lat1d = (1097*e1_4/512) * sin(8*u1);
		lat1 = u1 + lat1a + lat1b + lat1c + lat1d;
	
		sinLat1 = sin(lat1);
		sin2Lat1 = sinLat1 * sinLat1;
	
		r1 = (ellipa * (1 - e2))/pow(1 - (e2*sin2Lat1),1.5);
		v1 = ellipa/pow(1 - (e2*sin2Lat1),0.5);
	
		T1 = pow(tan(lat1),2);
		C1 = (ee*ee) * pow(cos(lat1),2);
		D = (east - projEF) / (v1 * projk0);
	
		// Calculate latitude and longitude
		latRad1 = (5 + (3*T1) + (10*C1) - (4*C1*C1) - (9*ee*ee)) * (pow(D,4)/24);
		latRad2 = (61 + (90*T1) + (298*C1) + (45*T1*T1) - (252*ee*ee) - (3*C1*C1)) * (pow(D,6)/720);
		latRad = lat1 - (v1 * tan(lat1)/r1) * ((D*D/2) - latRad1 + latRad2);
	
		lonRad1 = (1 + (2*T1) + C1) * (pow(D,3)/6);
		lonRad2 = (5 - (2*C1) + (28*T1) - (3*C1*C1) + (8*ee*ee) + (24*T1*T1)) * (pow(D,5)/120);
		lonRad = projlon0 + (D - lonRad1 + lonRad2) / cos(lat1);
	
		*latDeg = latRad * 180/pi;
		*lonDeg = lonRad * 180/pi;
	}
	
	
	int CalcUTMZone(double lat, double lon)
	{
		int UTMzone;
		UTMzone = (int)(((lon + 180.0)/6)+1) *
				 (lat < 0 ? -1 : 1);
	
		return UTMzone;
	}
*/
}
