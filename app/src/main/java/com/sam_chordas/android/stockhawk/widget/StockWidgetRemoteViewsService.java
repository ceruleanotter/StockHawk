package com.sam_chordas.android.stockhawk.widget;

/**
 * Created by lylafujiwara on 2017-04-06.
 */

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteDatabase;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.ui.StockHistoryChartActivity;

/**
 * RemoteViewsService controlling the mData being shown in the scrollable weather detail widget
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class StockWidgetRemoteViewsService extends RemoteViewsService {
    public final String LOG_TAG = StockWidgetRemoteViewsService.class.getSimpleName();
    private static final String[] STOCK_COLUMNS = {
            QuoteDatabase.QUOTES + "." + QuoteColumns._ID,
            QuoteColumns.SYMBOL,
            QuoteColumns.BIDPRICE,
            QuoteColumns.CHANGE,
            QuoteColumns.PERCENT_CHANGE,
            QuoteColumns.ISCURRENT,
            QuoteColumns.ISUP
    };
    // these indices must match the projection
    static final int INDEX_STOCK_ID = 0;
    static final int INDEX_STOCK_SYMBOL = 1;
    static final int INDEX_STOCK_BIDPRICE = 2;
    static final int INDEX_STOCK_CHANGE = 3;
    static final int INDEX_STOCK_PERCENT_CHANGE = 4;
    static final int INDEX_STOCK_ISCURRENT = 5;
    static final int INDEX_STOCK_ISUP= 6;


    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor mData = null;

            @Override
            public void onCreate() {
                mData = null;
                // Nothing to do
            }

            @Override
            public void onDataSetChanged() {
                Log.e(LOG_TAG, "On dataset changed called");
                //TODO this is probably bad
                if (mData != null) {
                    mData.close();
                }

                // This method is called by the app hosting the widget (e.g., the launcher)
                // However, our ContentProvider is not exported so it doesn't have access to the
                // mData. Therefore we need to clear (and finally restore) the calling identity so
                // that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();

                mData = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                        STOCK_COLUMNS,
                        QuoteColumns.ISCURRENT + " = ?",
                        new String[]{"1"},
                        null);

                Log.e(LOG_TAG, "querying the content provider");

                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (mData != null) {
                    mData.close();
                    mData = null;
                }
            }

            @Override
            public int getCount() {
                return mData == null ? 0 : mData.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                Log.e(LOG_TAG, "called for view at position " + position);

                if (position == AdapterView.INVALID_POSITION ||
                        mData == null || !mData.moveToPosition(position)) {
                    Log.e(LOG_TAG, "POSITION IS NULL!!!!!");
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_list_item_quote);

                // TODO adapter stuff

                views.setTextViewText(R.id.stock_symbol, mData.getString(INDEX_STOCK_SYMBOL));
                views.setTextViewText(R.id.bid_price, mData.getString(INDEX_STOCK_BIDPRICE));
                Log.e(LOG_TAG, "Stock symbol is " + mData.getString(INDEX_STOCK_SYMBOL));
                int sdk = Build.VERSION.SDK_INT;
                final String backgroundColorResource = "setBackgroundResource";




                if (mData.getInt(INDEX_STOCK_ISUP) == 1) {
                    if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
                        // should be set background drawable
                        views.setInt(R.id.change, backgroundColorResource, R.drawable.percent_change_pill_green);
                    } else {
                        // should be set background
                        views.setInt(R.id.change, backgroundColorResource, R.drawable.percent_change_pill_green);
                    }
                } else {
                    if (sdk < Build.VERSION_CODES.JELLY_BEAN) {
                        views.setInt(R.id.change, backgroundColorResource, R.drawable.percent_change_pill_red);
                    } else {
                        views.setInt(R.id.change, backgroundColorResource, R.drawable.percent_change_pill_red);
                    }
                }
                if (Utils.showPercent) {
                    views.setTextViewText(R.id.change, mData.getString(INDEX_STOCK_PERCENT_CHANGE));
                } else {
                    views.setTextViewText(R.id.change, mData.getString(INDEX_STOCK_CHANGE));
                }


                // TODO click stuff
                final Intent fillInIntent = new Intent();
                String symbol = mData.getString(INDEX_STOCK_SYMBOL);
                fillInIntent.putExtra(StockHistoryChartActivity.STOCK_SYMBOL_HISTORY_EXTRA, symbol);

                //TODO
                views.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);
                return views;
            }

            //TODO accessibility
            @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            private void setRemoteContentDescription(RemoteViews views, String description) {

            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_list_item_quote);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (mData.moveToPosition(position))
                    // TODO
                    return mData.getLong(INDEX_STOCK_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
