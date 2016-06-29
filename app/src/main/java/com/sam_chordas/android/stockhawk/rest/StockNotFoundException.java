package com.sam_chordas.android.stockhawk.rest;

/**
 * Created by lyla on 6/28/16.
 */

public class StockNotFoundException extends Exception {
    public StockNotFoundException(String detailMessage) {
        super(detailMessage);
    }

    public StockNotFoundException() {
        super();
    }
}
