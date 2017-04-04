package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.AxisValueFormatter;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.HistoricalQuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;


public class StockHistoryChartActivity  extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>  {

    LineChart mHistoricalDataLineChart;
    //TextView mTitleTextView;
    String mSymbol;


    public static final String STOCK_SYMBOL_HISTORY_EXTRA = "historical_data_symbol_extra";
    public static final int CHART_DATA_LOADER_ID = 8;

    public static final String[] PROJECTION = {
            HistoricalQuoteColumns.COLUMN_CLOSE_PRICE,
            HistoricalQuoteColumns.COLUMN_DATE
    };

    public static final int CLOSE_PRICE_PROJECTION_INDEX = 0;
    public static final int DATE_PROJECTION_INDEX = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_history_chart);

        mHistoricalDataLineChart = (LineChart) findViewById(R.id.historical_data_line_chart);
        //mTitleTextView = (TextView) findViewById(R.id.chart_title_text_view);

        mSymbol = null;
        Intent incomingIntent = getIntent();
        if (incomingIntent != null && incomingIntent.hasExtra(STOCK_SYMBOL_HISTORY_EXTRA)) {
            mSymbol = getIntent().getStringExtra(STOCK_SYMBOL_HISTORY_EXTRA);
        } else {
            finish();
            return;
        }




       // mTitleTextView.setText(title);

        // Initialize the loader
        getSupportLoaderManager().initLoader(CHART_DATA_LOADER_ID,null,this);

    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {


        String sortOrder = HistoricalQuoteColumns.COLUMN_DATE + " ASC";

        return new CursorLoader(
                this,   // Parent activity context
                QuoteProvider.Historical_Data.historicalDataPathWith(mSymbol),        // Table to query
                PROJECTION,     // Projection to return
                null,            // No selection clause
                null,            // No selection arguments
                sortOrder             // Sort by date from past to present
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        List<Entry> entries = new ArrayList<Entry>();

        final String dates[] = new String[data.getCount()];

        int time = 0;
        data.moveToPosition(-1);
        while (data.moveToNext()) {
            entries.add(new Entry(time, data.getLong(CLOSE_PRICE_PROJECTION_INDEX)));
            dates[data.getPosition()] = data.getString(DATE_PROJECTION_INDEX).substring(5);
            time++;
        }
        LineDataSet dataSet = new LineDataSet(entries, "Stock Price");

        dataSet.setColor(Color.rgb(255, 255, 255));
        LineData lineData = new LineData(dataSet);

//        // the labels that should be drawn on the XAxis
//        final String[] quarters = new String[] { "Q1", "Q2", "Q3", "Q4" };

        AxisValueFormatter formatterXAxis = new AxisValueFormatter() {

            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return dates[(int) value];
            }

            // we don't draw numbers, so no decimal digits needed
            @Override
            public int getDecimalDigits() {  return 0; }
        };

        AxisValueFormatter formatterYAxis = new AxisValueFormatter() {

            @Override
            public String getFormattedValue(float money, AxisBase axis) {


                NumberFormat formatter = NumberFormat.getCurrencyInstance();
                String moneyString = formatter.format(money);

                return  moneyString ;
            }

            // we don't draw numbers, so no decimal digits needed
            @Override
            public int getDecimalDigits() {  return 0; }
        };


        XAxis xAxis = mHistoricalDataLineChart.getXAxis();
        xAxis.setGranularity(1f); // minimum axis-step (interval) is 1
        xAxis.setValueFormatter(formatterXAxis);

        YAxis yAxisLeft = mHistoricalDataLineChart.getAxisLeft();
        yAxisLeft.setValueFormatter(formatterYAxis);

        mHistoricalDataLineChart.getAxisRight().setEnabled(false);

        Legend legend = mHistoricalDataLineChart.getLegend();
        legend.setEnabled(false);

        mHistoricalDataLineChart.setData(lineData);
        lineData.setDrawValues(false);
        
        String title = String.format(getResources().getString(R.string.historical_chart_title), mSymbol);
        mHistoricalDataLineChart.setDescription(title);


        mHistoricalDataLineChart.invalidate(); // refresh
    }


    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //TODO clear the chart
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);

    }
}
