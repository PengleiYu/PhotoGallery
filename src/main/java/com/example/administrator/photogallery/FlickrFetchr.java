package com.example.administrator.photogallery;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by Administrator on 2016/3/18.
 */
public class FlickrFetchr {
    public static final String TAG = "FlickrFetchr";

    public static final String PREF_SEARCH_QUERY="searchQuery";
    public static final String PREF_LAST_RESULT_ID="lastResultId";

    private static final String ENDPOINT = "https://api.flickr.com/services/rest/";
    private static final String API_KEY = "97f00313183a0664e2fabdcd674d42fd";

    private static final String METHOD_GET_RECENT = "flickr.photos.getRecent";
    private static final String METHOD_SEARCH = "flickr.photos.search";

    private static final String PARAM_EXTRAS = "extras";
    private static final String PARAM_PAGE = "page";
    private static final String PARAM_TEXT = "text";

    private static final String EXTRA_SMALL_URL = "url_s";

    private static final String XML_PHOTO = "photo";
    private static final String XML_PHOTOS = "photos";

    public ArrayList<GalleryItem> fetchItems(){
        return fetchItems(1);
    }
    //按页生成url，再下载
    public ArrayList<GalleryItem> fetchItems(int page) {
        Uri.Builder builder = Uri.parse(ENDPOINT).buildUpon()
                .appendQueryParameter("method", METHOD_GET_RECENT)
                .appendQueryParameter("api_key", API_KEY)
                .appendQueryParameter(PARAM_EXTRAS, EXTRA_SMALL_URL);
        //请求分页
        if (page > 0) {
            builder.appendQueryParameter(PARAM_PAGE, String.valueOf(page));
        }
        String url = builder.build().toString();

        return downloadGalleryItems(url);
    }

    //按搜索生成url，再下载
    public ArrayList<GalleryItem> search(String query) {
        String url = Uri.parse(ENDPOINT).buildUpon()
                .appendQueryParameter("method", METHOD_SEARCH)
                .appendQueryParameter("api_key", API_KEY)
                .appendQueryParameter(PARAM_EXTRAS, EXTRA_SMALL_URL)
                .appendQueryParameter(PARAM_TEXT, query)
                .build().toString();
        return downloadGalleryItems(url);
    }

    //下载专用方法
    @NonNull
    private ArrayList<GalleryItem> downloadGalleryItems(String url) {
        ArrayList<GalleryItem> items = new ArrayList<>();
        try {
            String xmlString = getUrl(url);

            Log.i(TAG,xmlString);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xmlString));

            parseItems(items, parser);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
        return items;
    }

    //xml解析，将xml字符串解析为items
    void parseItems(ArrayList<GalleryItem> items, XmlPullParser parser) throws IOException, XmlPullParserException {
        int eventType = parser.next();
        int page = 0, pages = 0;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (XML_PHOTOS.equals(name)) {
                    page = Integer.parseInt(parser.getAttributeValue(null, "page"));
                    pages = Integer.parseInt(parser.getAttributeValue(null, "pages"));
                } else if (XML_PHOTO.equals(name)) {
                    String id = parser.getAttributeValue(null, "id");
                    String caption = parser.getAttributeValue(null, "title");
                    String smallUrl = parser.getAttributeValue(null, EXTRA_SMALL_URL);
                    String owner=parser.getAttributeValue(null,"owner");
                    GalleryItem item = new GalleryItem();
                    item.setId(id);
                    item.setCaption(caption);
                    item.setUrl(smallUrl);
                    item.setOwner(owner);
                    item.setPage(page);
                    item.setPages(pages);
                    items.add(item);
                }
            }
            eventType = parser.next();
        }
    }

    //获取url返回的字符串
    public String getUrl(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    //获取url返回的字节数组
    byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            InputStream inputStream = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                return null;

            int bytesRead;
            byte[] buffer = new byte[1024];
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            return outputStream.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
