package com.sk7software.climbviewer.model.strava;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk7software.climbviewer.model.strava.stream.DistanceStream;
import com.sk7software.climbviewer.model.strava.stream.ElevationStream;
import com.sk7software.climbviewer.model.strava.stream.LatLngStream;
import com.sk7software.climbviewer.model.strava.stream.TimeStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivityStream implements Serializable, Parcelable {
    private LatLngStream latlng;
    private DistanceStream distance;
    private ElevationStream altitude;
    private TimeStream time;

    private static final String LATLNG = "latlng";
    private static final String DISTANCE = "distance";
    private static final String ALTITUDE = "altitude";
    private static final String TYPE = "type";
    private static final String DATA = "data";
    private static final String SIZE = "original_size";
    private static final String TAG = StravaActivityStream.class.getSimpleName();
    public static StravaActivityStream createFromJson(JSONArray json) {
        StravaActivityStream stream = new StravaActivityStream();
        ObjectMapper mapper = new ObjectMapper();
        try {
            for (int i = 0; i < json.length(); i++) {
                JSONObject jobj = json.getJSONObject(i);
                switch (jobj.get(TYPE).toString()) {
                    case LATLNG:
                        LatLngStream latlng = mapper.readValue(jobj.toString(), LatLngStream.class);
                        latlng.setSize(latlng.getData().size());
                        stream.setLatlng(latlng);
                        break;
                    case DISTANCE:
                        DistanceStream dist = mapper.readValue(jobj.toString(), DistanceStream.class);
                        dist.setSize(dist.getData().size());
                        stream.setDistance(dist);
                        break;
                    case ALTITUDE:
                        ElevationStream elev = mapper.readValue(jobj.toString(), ElevationStream.class);
                        elev.setSize(elev.getData().length);
                        stream.setAltitude(elev);
                        break;
                    default:
                        //Do nothing
                }
            }
        } catch (JSONException | JsonProcessingException e) {
            Log.e(TAG, "JSON error creating stream: " + e.getMessage());
        }
        return stream;
    }
    protected StravaActivityStream(Parcel in) {
        latlng = in.readParcelable(LatLngStream.class.getClassLoader());
        distance = in.readParcelable(DistanceStream.class.getClassLoader());
        altitude = in.readParcelable(ElevationStream.class.getClassLoader());
        time = in.readParcelable(TimeStream.class.getClassLoader());
    }

    public static final Creator<StravaActivityStream> CREATOR = new Creator<StravaActivityStream>() {
        @Override
        public StravaActivityStream createFromParcel(Parcel in) {
            return new StravaActivityStream(in);
        }

        @Override
        public StravaActivityStream[] newArray(int size) {
            return new StravaActivityStream[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeParcelable(latlng, i);
        parcel.writeParcelable(distance, i);
        parcel.writeParcelable(altitude, i);
        parcel.writeParcelable(time, i);
    }
}
