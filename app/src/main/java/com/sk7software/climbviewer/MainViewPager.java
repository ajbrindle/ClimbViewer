package com.sk7software.climbviewer;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainViewPager extends FragmentStateAdapter {
    public MainViewPager(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch(position) {
            case 0:
                return new RoutesFragment();
            case 1:
                return new ClimbsFragment();
            default:
                return new RoutesFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
