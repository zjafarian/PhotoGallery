package org.maktab.photogallery.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import org.maktab.photogallery.network.FlickrFetcher;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ThumbnailDownloader<T> extends HandlerThread {

    public static final String TAG = "ThumbnailDownloader";
    private static final int WHAT_THUMBNAIL_DOWNLOAD = 1;
    private LruCache<String, Bitmap> mLruCache;

    private Handler mHandlerRequest;
    private Handler mHandlerResponse;
    private ConcurrentHashMap<T, String> mRequestMap = new ConcurrentHashMap<>();

    private ThumbnailDownloaderListener mListener;

    public ThumbnailDownloaderListener getListener() {
        return mListener;
    }

    public void setListener(ThumbnailDownloaderListener listener) {
        mListener = listener;
    }

    public LruCache<String, Bitmap> getLruCache() {
        return mLruCache;
    }

    public void setLruCache(LruCache<String, Bitmap> lruCache) {
        mLruCache = lruCache;
    }

    public ThumbnailDownloader(Handler uiHandler) {
        super(TAG);

        mHandlerResponse = uiHandler;
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();

        mHandlerRequest = new Handler() {
            //run message
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);

                //download url from net
                try {
                    if (msg.what == WHAT_THUMBNAIL_DOWNLOAD) {
                        if (msg.obj == null)
                            return;

                        T target = (T) msg.obj;
                        String url = mRequestMap.get(target);

                        handleDownloadMessage(target, url);
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        };
    }

    private void handleDownloadMessage(T target, String url) throws IOException {
        if (url == null)
            return;

        FlickrFetcher flickrFetcher = new FlickrFetcher();
        byte[] bitmapBytes = flickrFetcher.getUrlBytes(url);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
        if (getResourceFromMemoryCache(url) == null)
            mLruCache.put(url, bitmap);


        mHandlerResponse.post(new Runnable() {
            @Override
            public void run() {
                if (mRequestMap.get(target) != url)
                    return;

                mListener.onThumbnailDownloaded(target, bitmap);
//                mRequestMap.remove(target);
            }
        });
    }

    public Bitmap getResourceFromMemoryCache(String key) {
        return mLruCache.get(key);
    }


    public void queueThumbnail(T target, String url) {
        mRequestMap.put(target, url);

        //create a message and send it to looper (to put in queue)
        Message message = mHandlerRequest.obtainMessage(WHAT_THUMBNAIL_DOWNLOAD, target);
        message.sendToTarget();
    }

    public void clearQueue() {
        mHandlerRequest.removeMessages(WHAT_THUMBNAIL_DOWNLOAD);
    }

    public interface ThumbnailDownloaderListener<T> {
        void onThumbnailDownloaded(T target, Bitmap bitmap);
    }
}
