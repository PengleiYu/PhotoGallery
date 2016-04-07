package com.example.administrator.photogallery;

import android.support.v4.app.Fragment;

/**
 * Created by yupenglei on 16/4/7.
 */
public class PhotoPageActivity extends SingleFragmentActivity {
    @Override
    protected Fragment createFragment() {
        return new PhotoPageFragment();
    }
}
