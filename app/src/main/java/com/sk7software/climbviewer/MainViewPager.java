package com.sk7software.climbviewer;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class MainViewPager extends FragmentStateAdapter {
    private RoutesFragment routesFragment;
    private ClimbsFragment climbsFragment;
    public MainViewPager(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch(position) {
            case 0:
                routesFragment = new RoutesFragment();
                return routesFragment;
            case 1:
                climbsFragment = new ClimbsFragment();
                return climbsFragment;
            default:
                return null;
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    public Fragment getFragmentAtPosition(int position) {
        switch(position) {
            case 0:
                return routesFragment;
            case 1:
                return climbsFragment;
            default:
                return null;
        }
    }
}
