package com.example.administrator.photogallery;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SearchView;

import java.util.ArrayList;

/**
 * Created by Administrator on 2016/3/18.
 */
public class PhotoGalleryFragment extends VisibleFragment {
    private static final String TAG = "PhotoGalleryFragment";
    GridView mGridView;
    ArrayList<GalleryItem> mItems = new ArrayList<>();
    ThumbnailDownloader<ImageView> mThumbnailThread;
    LruCache<String, Bitmap> mLruCache;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);


        updateItems();

//        Intent intent=new Intent(getActivity(),PollService.class);
//        getActivity().startService(intent);
//        PollService.setServiceAlarm(getActivity(),true);

        int size = (int) Runtime.getRuntime().maxMemory();
        mLruCache = new LruCache<String, Bitmap>(size / 8) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        mThumbnailThread = new ThumbnailDownloader<>(new Handler());
        mThumbnailThread.setListener(new ThumbnailDownloader.Listener<ImageView>() {
            @Override
            public void onThumbnailDownloaded(ImageView imageView, Bitmap thumbnail, String url) {
                //防图片错位
                if (isVisible() && url.equals(imageView.getTag())) {
                    imageView.setImageBitmap(thumbnail);
                }
                mLruCache.put(url, thumbnail);
            }
        });
        mThumbnailThread.start();
        mThumbnailThread.getLooper();
        Log.e(TAG, "background thread started");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mGridView = (GridView) view.findViewById(R.id.gridView);
        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            private int mFirstVisibleItem, mVisibleItemCount, mTotalItemCount;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                        && mFirstVisibleItem + mVisibleItemCount == mTotalItemCount) {
                    if (!mItems.isEmpty()) {
                        GalleryItem item = mItems.get(mTotalItemCount - 1);
                        Log.e(TAG, "page=" + item.getPage() + ",pages=" + item.getPages());
                        int page = item.getPage();
                        int pages = item.getPages();
                        if (page < pages) {
                            new FetchItemsTask(page + 1).execute();
                        }
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mFirstVisibleItem = firstVisibleItem;
                mVisibleItemCount = visibleItemCount;
                mTotalItemCount = totalItemCount;
//                Log.e(TAG, "first=" + firstVisibleItem + ",visible=" + visibleItemCount + ",total=" + totalItemCount);

            }
        });
        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GalleryItem item=mItems.get(position);
                Uri photoPageUri=Uri.parse(item.getPhotoPageUrl());
                Intent intent=new Intent(Intent.ACTION_VIEW,photoPageUri);
                startActivity(intent);
            }
        });
//        setupAdapter();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);
        //初始化searchView,SearchView不会触发onOptionsItemSelected
        MenuItem item = menu.findItem(R.id.menu_item_search);
        SearchView searchView = (SearchView) item.getActionView();
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        ComponentName componentName = getActivity().getComponentName();
        SearchableInfo searchableInfo = searchManager.getSearchableInfo(componentName);
        searchView.setSearchableInfo(searchableInfo);
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit().putString(FlickrFetchr.PREF_SEARCH_QUERY, null)
                        .apply();
                updateItems();
                return false;
            }
        });
    }

    //3.0之后菜单不会自动更新，需要调用刷新方法
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem item = menu.findItem(R.id.menu_item_toggle_polling);
        if (PollService.isServiceAlarmOn(getActivity())) {
            item.setTitle(R.string.stop_polling);
        } else {
            item.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_search:
                //适用于低版本
                getActivity().onSearchRequested();
                return true;
            case R.id.menu_item_clear:
                //已隐藏，代码已设置在SearchView中
                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit().putString(FlickrFetchr.PREF_SEARCH_QUERY, null)
                        .apply();
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailThread.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailThread.quit();
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit().putString(FlickrFetchr.PREF_SEARCH_QUERY, null).apply();
        Log.e(TAG, "background thread destroy");
    }

    //应该没用了
    void setupAdapter() {
        if (getActivity() == null || mGridView == null)
            return;
        if (mItems != null) {
            mGridView.setAdapter(new GalleryItemAdapter(mItems));
        } else mGridView.setAdapter(null);
    }

    void notifyOrSetupAdapter() {
        if (getActivity() == null || mGridView == null)
            return;

        if (mGridView.getAdapter() != null) {
            ((ArrayAdapter) mGridView.getAdapter()).notifyDataSetChanged();
        } else if (mItems != null) {
            mGridView.setAdapter(new GalleryItemAdapter(mItems));
        } else mGridView.setAdapter(null);
    }

    public void updateItems() {
        new FetchItemsTask().execute();
    }

    //用于下载图片信息列表，所以取消图片下载线程
    private class FetchItemsTask extends AsyncTask<Void, Void, ArrayList<GalleryItem>> {
        private int mPage;

        //若不指定页数，使用非正数
        public FetchItemsTask(int page) {
            mPage = page;
        }

        public FetchItemsTask() {
            mPage = -1;
        }

        @Override
        protected ArrayList<GalleryItem> doInBackground(Void... params) {
            if (mThumbnailThread != null)
                mThumbnailThread.clearQueue();
            Activity activity = getActivity();
            if (activity == null)
                return new ArrayList<>();
            String query = PreferenceManager.getDefaultSharedPreferences(activity).getString(FlickrFetchr.PREF_SEARCH_QUERY, null);
            Log.e(TAG, "query = " + query);
            if (query != null)
                return new FlickrFetchr().search(query);
            if (mPage != -1)
                return new FlickrFetchr().fetchItems(mPage);
            else return new FlickrFetchr().fetchItems();
        }

        @Override
        protected void onPostExecute(ArrayList<GalleryItem> items) {
            // TODO: 2016/3/25 问题：由查询模式进入普通模式，将不会清空
            //如果是查询，就先清空所有条目
//            if (getActivity() != null && PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(FlickrFetchr.PREF_SEARCH_QUERY, null) != null) {
//                mItems.clear();
//            }
//            mItems.addAll(items);
//            notifyOrSetupAdapter();

            mItems = items;//// TODO: 2016/3/25 分页模式目前无法兼容搜索，所以将分页模式改为一次只展示一页
            setupAdapter();
        }
    }

    private class GalleryItemAdapter extends ArrayAdapter<GalleryItem> {
        public GalleryItemAdapter(ArrayList<GalleryItem> items) {
            super(getActivity(), 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = getActivity().getLayoutInflater().inflate(R.layout.gallery_item, parent, false);
            ImageView imageView = (ImageView) convertView.findViewById(R.id.gallery_item_imageView);
            GalleryItem item = getItem(position);
            imageView.setTag(item.getUrl());
            if (item.getUrl() != null && mLruCache.get(item.getUrl()) != null) {
                imageView.setImageBitmap(mLruCache.get(item.getUrl()));
            } else {
                imageView.setImageResource(R.mipmap.ic_launcher);
                mThumbnailThread.queueThumbnail(imageView, item.getUrl());
            }
            if (item.getUrl() == null) {
                Log.e(TAG, "=========================================================================================================================");
                Log.e(TAG, item.toString());
            }
            return convertView;
        }
    }
}
