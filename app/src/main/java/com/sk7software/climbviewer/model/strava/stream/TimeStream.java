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
public class TimeStream implements Serializable, Parcelable {
    private int[] data;
    @JsonProperty("original_size")
    private int size;

    protected TimeStream(Parcel in) {
        data = in.createIntArray();
        size = in.readInt();
    }

    public static final Creator<TimeStream> CREATOR = new Creator<TimeStream>() {
        @Override
        public TimeStream createFromParcel(Parcel in) {
            return new TimeStream(in);
        }

        @Override
        public TimeStream[] newArray(int size) {
            return new TimeStream[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeIntArray(data);
        parcel.writeInt(size);
    }
}
