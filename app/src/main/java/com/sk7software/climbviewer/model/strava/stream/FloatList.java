package com.sk7software.climbviewer.model.strava.stream;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class FloatList extends ArrayList<Float> implements Parcelable {

    private static final long serialVersionUID = 1L;

    public FloatList(){
        super();
    }

    protected FloatList(Parcel in) {
        in.readList(this, Float.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeList(this);
    }

    public static final Parcelable.Creator<FloatList> CREATOR =
            new Parcelable.Creator<FloatList>() {
                public FloatList createFromParcel(Parcel in) {
                    return new FloatList(in);
                }

                public FloatList[] newArray(int size) {
                    return new FloatList[size];
                }
            };

}
