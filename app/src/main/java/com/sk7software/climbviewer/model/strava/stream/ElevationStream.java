package com.sk7software.climbviewer.model.strava.stream;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class ElevationStream implements Serializable, Parcelable {
    private float[] data;
    @JsonProperty("original_size")
    private int size;

    protected ElevationStream(Parcel in) {
        data = in.createFloatArray();
        size = in.readInt();
    }

    public static final Creator<ElevationStream> CREATOR = new Creator<ElevationStream>() {
        @Override
        public ElevationStream createFromParcel(Parcel in) {
            return new ElevationStream(in);
        }

        @Override
        public ElevationStream[] newArray(int size) {
            return new ElevationStream[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeFloatArray(data);
        parcel.writeInt(size);
    }
}
