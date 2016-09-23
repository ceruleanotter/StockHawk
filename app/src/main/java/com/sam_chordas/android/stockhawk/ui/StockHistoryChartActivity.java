package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.sam_chordas.android.stockhawk.R;

public class StockHistoryChartActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    LineChart mHistoricalDataLineChart;
    TextView mTitleTextView;
    public static final String STOCK_SYMBOL_HISTORY_EXTRA = "historical_data_symbol_extra";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_history_chart);

        mHistoricalDataLineChart = (LineChart) findViewById(R.id.historical_data_line_chart);
        mTitleTextView = (TextView) findViewById(R.id.chart_title_text_view);

        String symbol = null;
        Intent incomingIntent = getIntent();
        if (incomingIntent != null && incomingIntent.hasExtra(STOCK_SYMBOL_HISTORY_EXTRA)) {
            symbol = getIntent().getStringExtra(STOCK_SYMBOL_HISTORY_EXTRA);
        } else {
            finish();
            return;
        }

        Resources res = getResources();
        String title = String.format(res.getString(R.string.historical_chart_title), symbol);

        mTitleTextView.setText(title);

        //TODO init the loader

    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //TODO cursor loader
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        //TODO make the chart
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //TODO clear the chart
    }
}
