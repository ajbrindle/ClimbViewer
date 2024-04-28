package com.sk7software.climbviewer.model.strava.stream;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatLngStream implements Serializable, Parcelable {
    private List<FloatList> data = new ArrayList<>();
    @JsonProperty("original_size")
    private int size;

    protected LatLngStream(Parcel in) {
        data = in.createTypedArrayList(FloatList.CREATOR);
        size = in.readInt();
    }

    public static final Creator<LatLngStream> CREATOR = new Creator<LatLngStream>() {
        @Override
        public LatLngStream createFromParcel(Parcel in) {
            return new LatLngStream(in);
        }

        @Override
        public LatLngStream[] newArray(int size) {
            return new LatLngStream[size];
        }
    };

    public void addLatLng(float lat, float lon) {
        FloatList ll = new FloatList();
        ll.add(lat);
        ll.add(lon);
        data.add(ll);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeTypedList(data);
        parcel.writeInt(size);
    }
}
