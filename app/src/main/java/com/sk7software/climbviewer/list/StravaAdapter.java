package com.sk7software.climbviewer.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.sk7software.climbviewer.R;
import com.sk7software.climbviewer.model.strava.StravaItem;

public class StravaAdapter extends ArrayAdapter<StravaItem> {

    private int layoutResource;

    public StravaAdapter(Context context, int layoutResource, StravaItem[] stravaItems) {
        super(context, layoutResource, stravaItems);
        this.layoutResource = layoutResource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View view = convertView;

        if (view == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(getContext());
            view = layoutInflater.inflate(layoutResource, null);
        }

        StravaItem stravaItem = getItem(position);

        if (stravaItem != null) {
            TextView txtName = (TextView) view.findViewById(R.id.firstLine);
            TextView txtDate = (TextView) view.findViewById(R.id.secondLine);
            ImageView img = (ImageView) view.findViewById(R.id.imgSportType);

            if (txtName != null) {
                txtName.setText(stravaItem.getName());
            }
            if (txtDate != null) {
                if (stravaItem.getDateTime() != null) {
                    txtDate.setText(stravaItem.reformatDateTime() + " - " + stravaItem.formatDistance());
                } else {
                    txtDate.setText(stravaItem.formatDistance() + " / " + stravaItem.formatElevation());
                }
            }
            if (img != null &&
                    (stravaItem.getSportType() != null && stravaItem.getSportType().contains("Ride")) ||
                    (stravaItem.getType() != null && stravaItem.getType().toString().equals("1"))) {
                img.setImageResource(R.drawable.ic_biking_solid);
                img.setColorFilter(R.color.purple_200);
            } else {
                img.setVisibility(View.INVISIBLE);
            }
        }

        return view;
    }
}
