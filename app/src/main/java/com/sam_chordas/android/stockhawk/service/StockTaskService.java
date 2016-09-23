package com.sam_chordas.android.stockhawk.service;

import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.StockNotFoundException;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService {
    private String LOG_TAG = StockTaskService.class.getSimpleName();

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private StringBuilder mStoredSymbols = new StringBuilder();
    private boolean isUpdate;


    //Creating fake enums using the support annotations intdef
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATUS_LOADING, STATUS_OK, STATUS_UNSUPPORTED_ENCODING_ERROR, STATUS_SERVER_ERROR, STATUS_CANNOT_CONNECT})
    public @interface StockLoadingStatus {
    }

    public static final int STATUS_LOADING = 0;
    public static final int STATUS_OK = 1;
    public static final int
            STATUS_UNSUPPORTED_ENCODING_ERROR = 2;
    public static final int STATUS_SERVER_ERROR = 3;
    public static final int
            STATUS_CANNOT_CONNECT = 4;

    public static final String STATUS_PREFERENCE_FILE = "lastConnectionInfo";
    public static final String STATUS_KEY = "status";

    public static final String LAST_UPDATE_DAY_KEY = "lastUpdate";

    private SharedPreferences mLastConnectionInfo;


    public StockTaskService() {
    }

    public StockTaskService(Context c) {
        mContext = c;
    }


    String fetchData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }


    @Override
    public int onRunTask(TaskParams params) {
        //update status

        Cursor initQueryCursor;
        if (mContext == null) mContext = getApplicationContext();
        if (mLastConnectionInfo == null)
            mLastConnectionInfo = mContext.getSharedPreferences(STATUS_PREFERENCE_FILE, 0);

        changeStatus(STATUS_LOADING);

        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
                    + "in (", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            changeStatus(STATUS_UNSUPPORTED_ENCODING_ERROR);
            e.printStackTrace();
        }
        if (params.getTag().equals("init") || params.getTag().equals("periodic")) {
            isUpdate = true;
            initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[]{"Distinct " + QuoteColumns.SYMBOL}, null,
                    null, null);
            if (initQueryCursor.getCount() == 0 || initQueryCursor == null) {
                // Init task. Populates DB with quotes for the symbols seen below
                try {
                    urlStringBuilder.append(
                            URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    changeStatus(STATUS_UNSUPPORTED_ENCODING_ERROR);
                    e.printStackTrace();
                }
            } else if (initQueryCursor != null) {
                DatabaseUtils.dumpCursor(initQueryCursor);
                initQueryCursor.moveToFirst();
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    mStoredSymbols.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                try {
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    changeStatus(STATUS_UNSUPPORTED_ENCODING_ERROR);
                    e.printStackTrace();
                }
            }
            //Download historical data fo all stocks
            downloadHistoricalForAllStocks();
        } else if (params.getTag().equals("add")) {
            isUpdate = false;
            // get symbol from params.getExtra and build query
            String stockInput = params.getExtras().getString("symbol");
            try {
                urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                changeStatus(STATUS_UNSUPPORTED_ENCODING_ERROR);
                e.printStackTrace();
            }
            //Download historical data but just for the new stock
            downloadHistoricalForStock(stockInput);
        }
        // finalize the URL for the API query.
        urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                + "org%2Falltableswithkeys&callback=");

        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        if (urlStringBuilder != null) {
            urlString = urlStringBuilder.toString();
            try {
                getResponse = fetchData(urlString);
                result = GcmNetworkManager.RESULT_SUCCESS;
                try {
                    ContentValues contentValues = new ContentValues();
                    // update ISCURRENT to 0 (false) so new data is current
                    if (isUpdate) {
                        contentValues.put(QuoteColumns.ISCURRENT, 0);
                        mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                                null, null);
                    }

                    mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                            Utils.quoteJsonToContentVals(getResponse));
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                    result = GcmNetworkManager.RESULT_FAILURE;
                    changeStatus(STATUS_SERVER_ERROR);
                } catch (final StockNotFoundException e) {
                    result = GcmNetworkManager.RESULT_FAILURE;
                    Handler errorHandler = new Handler(Looper.getMainLooper()); //Some reason this looper thing helped

                    errorHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                changeStatus(STATUS_CANNOT_CONNECT);
            }
        }

        if (result == GcmNetworkManager.RESULT_SUCCESS) {
            SharedPreferences.Editor editor = mLastConnectionInfo.edit();
            editor.putInt(STATUS_KEY, STATUS_OK);
            editor.apply();
        }


        return result;
    }

    private void changeStatus(@StockLoadingStatus int status) {
        SharedPreferences.Editor editor = mLastConnectionInfo.edit();
        editor.putInt(STATUS_KEY, status);
        editor.commit();
        Log.e("COMMITTED", "with status " + status);
    }

    private void downloadHistoricalForAllStocks() {
        downloadHistoricalForStock(null);
    }

    private void downloadHistoricalForStock(String symbol) {
        Log.e(LOG_TAG, "Historical Data is being downloaded");
        //Get the stock symbols with dates
        Cursor stockSymbols = getInitialStockSymbols();

        //Set up a calendar and get today's date
        Calendar cal = new GregorianCalendar();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(cal.getTimeZone());
        String today = dateFormat.format(cal.getTime());
        Log.e(LOG_TAG, "Downloading data for the first time");
        cal.add(Calendar.MONTH, -3);
        String threeMonthsAgo = dateFormat.format(cal.getTime());
        String lastUpdateDate = mLastConnectionInfo.getString(LAST_UPDATE_DAY_KEY, null);

        String endDate = null, startDate = null;



        //Make the request url
        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol "
                    + "in (", "UTF-8"));

            //Case where we're downloading for a single symbol
            if (symbol != null) {
                //set the start dates if we're downloading one new stock
                startDate = threeMonthsAgo;
                endDate = lastUpdateDate; // there's a possibility this is null, not sure what to
                // do in this case...

                //In this case we are downloading a single stock
                urlStringBuilder.append(URLEncoder.encode("\"" + symbol + "\")", "UTF-8"));


            } else {
                // Since it's not a single stock, start by checking whether today is a new day
                // set the start dates if we're downloading everything
                startDate = (lastUpdateDate == null) ? threeMonthsAgo : lastUpdateDate;
                endDate = today;

                if (startDate == endDate) {
                    Log.e(LOG_TAG, "No new data to download");
                    return;
                }

                // Check whether we actually have stored stock symbols
                if (mStoredSymbols.length() > 0) {
                    // In this case we're downloading whatever has been stored in the symbols
                    Log.e(LOG_TAG, "the lengths of stored symbols is greater than 0");
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                } else {
                    // in this case we're downloading the default stock data
                    Log.e(LOG_TAG, "the length of store symbols is zero?");
                    urlStringBuilder.append(URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
                }
            }

            urlStringBuilder.append(URLEncoder.encode(" and startDate = \"" + startDate +
                    "\" and endDate = \"" + endDate + "\"","UTF-8"));

            urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                    + "org%2Falltableswithkeys&callback=");

            Log.e(LOG_TAG, "query is " + urlStringBuilder.toString());
        } catch (UnsupportedEncodingException e) {
            changeStatus(STATUS_UNSUPPORTED_ENCODING_ERROR);
            e.printStackTrace();
        }


        //Do the network request
        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        if (urlStringBuilder != null) {
            urlString = urlStringBuilder.toString();
            try {
                getResponse = fetchData(urlString);
                result = GcmNetworkManager.RESULT_SUCCESS;
                try {
                    ContentValues contentValues = new ContentValues();
                    // update ISCURRENT to 0 (false) so new data is current
                    mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                            Utils.historicalJsonToContentVals(getResponse));
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                    result = GcmNetworkManager.RESULT_FAILURE;
                    changeStatus(STATUS_SERVER_ERROR);
                } catch (SQLiteConstraintException e) {
                    Log.e(LOG_TAG, "the constraint was failed, but it's OK", e);
                }
            } catch (IOException e) {
                e.printStackTrace();
                changeStatus(STATUS_CANNOT_CONNECT);
            }
        }

        if (result == GcmNetworkManager.RESULT_SUCCESS) {
            SharedPreferences.Editor editor = mLastConnectionInfo.edit();
            editor.putInt(STATUS_KEY, STATUS_OK);
            editor.putString(LAST_UPDATE_DAY_KEY, endDate);
            editor.apply();
            Log.e(LOG_TAG, "saved the new today which is " + endDate );
        }

        Log.e(LOG_TAG, "Done downloading historical data, the result is " + result);
    }

    private Cursor getInitialStockSymbols() {
        return mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                new String[]{"Distinct " + QuoteColumns.SYMBOL}, null,
                null, null);
    }
}
