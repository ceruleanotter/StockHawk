package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.util.Log;

import com.sam_chordas.android.stockhawk.data.HistoricalQuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

    final static String  JSON_SYMBOL = "Symbol";
    final static String JSON_DATE = "Date";
    final static String JSON_CLOSE = "Close";

    private static String LOG_TAG = Utils.class.getSimpleName();

    public static boolean showPercent = true;

    public static ArrayList quoteJsonToContentVals(String JSON) throws StockNotFoundException {
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject = null;
        JSONArray resultsArray = null;
        try {
            jsonObject = new JSONObject(JSON);
            if (jsonObject != null && jsonObject.length() != 0) {
                jsonObject = jsonObject.getJSONObject("query");
                int count = Integer.parseInt(jsonObject.getString("count"));
                if (count == 1) {
                    jsonObject = jsonObject.getJSONObject("results")
                            .getJSONObject("quote");

                    //Log tag, probably delete:
                    Log.e("Stock Hawk", "The string is \n" + jsonObject.toString());
                    //Error checking here for null
                    if (jsonObject.getString("Bid").equals("null")) {
                        //respond to error
                        throw new StockNotFoundException("Stock " + jsonObject.getString("symbol") + " was not found");
                    } else {
                        batchOperations.add(buildBatchOperationForQuote(jsonObject));
                    }
                } else {
                    resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

                    if (resultsArray != null && resultsArray.length() != 0) {
                        for (int i = 0; i < resultsArray.length(); i++) {
                            jsonObject = resultsArray.getJSONObject(i);
                            batchOperations.add(buildBatchOperationForQuote(jsonObject));
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "String to JSON failed: " + e);
        }
        return batchOperations;
    }

    public static String truncateBidPrice(String bidPrice) {
        bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
        return bidPrice;
    }

    public static String truncateChange(String change, boolean isPercentChange) {
        String weight = change.substring(0, 1);
        String ampersand = "";
        if (isPercentChange) {
            ampersand = change.substring(change.length() - 1, change.length());
            change = change.substring(0, change.length() - 1);
        }
        change = change.substring(1, change.length());
        double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
        change = String.format("%.2f", round);
        StringBuffer changeBuffer = new StringBuffer(change);
        changeBuffer.insert(0, weight);
        changeBuffer.append(ampersand);
        change = changeBuffer.toString();
        return change;
    }

    public static ContentProviderOperation buildBatchOperationForQuote(JSONObject jsonObject) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
                QuoteProvider.Quotes.CONTENT_URI);
        try {
            String change = jsonObject.getString("Change");
            builder.withValue(QuoteColumns.SYMBOL, jsonObject.getString("symbol"));
            builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(jsonObject.getString("Bid")));
            builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                    jsonObject.getString("ChangeinPercent"), true));
            builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
            builder.withValue(QuoteColumns.ISCURRENT, 1);
            if (change.charAt(0) == '-') {
                builder.withValue(QuoteColumns.ISUP, 0);
            } else {
                builder.withValue(QuoteColumns.ISUP, 1);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    public static String buildAndAddBatchOperationForHistoricalData(
            JSONObject jsonHistoricalObject,
            ArrayList<ContentProviderOperation> batchOperations
            ) {
        String symbol = "";
        String date = "";
        double close = -1;
        try {
            symbol = jsonHistoricalObject.getString(JSON_SYMBOL);
            date = jsonHistoricalObject.getString(JSON_DATE);
            close = jsonHistoricalObject.getDouble(JSON_CLOSE);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
                QuoteProvider.Historical_Data.historicalDataPathWith(symbol));

        builder.withValue(HistoricalQuoteColumns.COLUMN_SYMBOL, symbol);
        builder.withValue(HistoricalQuoteColumns.COLUMN_DATE, date);
        builder.withValue(HistoricalQuoteColumns.COLUMN_CLOSE_PRICE, close);
        builder.withValue(HistoricalQuoteColumns.COLUMN_INTERNAL_ID, date+symbol);


        batchOperations.add(builder.build());

        return symbol;

    }

    public static ArrayList<ContentProviderOperation> historicalJsonToContentVals(String JSON, String endDate) {
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject = null;
        JSONArray jsonHistoricalData = null;
        HashSet<String> symbolsFound = new HashSet<>();
        try {
            jsonObject = new JSONObject(JSON);

            if (jsonObject != null && jsonObject.length() != 0) {
                jsonHistoricalData = jsonObject.getJSONObject("query").getJSONObject("results").
                        getJSONArray("quote");

                if (jsonHistoricalData != null && jsonHistoricalData.length() != 0) {
                    for (int i = 0; i < jsonHistoricalData.length(); i++) {
                        JSONObject currentHistoricalData = jsonHistoricalData.getJSONObject(i);
                        String symbolUpdated = buildAndAddBatchOperationForHistoricalData(currentHistoricalData, batchOperations);
                        symbolsFound.add(symbolUpdated);
                    }
                }

                for (String symbol : symbolsFound) {
                    ContentProviderOperation.Builder updateDayForSymbol =
                            ContentProviderOperation.newUpdate(QuoteProvider.Quotes.withSymbol(symbol));
                    updateDayForSymbol.withValue(QuoteColumns.LAST_UPDATED, endDate);
                    batchOperations.add(updateDayForSymbol.build());
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return batchOperations;
    }
}
