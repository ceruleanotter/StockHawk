package com.sam_chordas.android.stockhawk.data;

import net.simonvt.schematic.annotation.AutoIncrement;
import net.simonvt.schematic.annotation.DataType;
import net.simonvt.schematic.annotation.NotNull;
import net.simonvt.schematic.annotation.PrimaryKey;

/**
 * Created by lyla on 9/15/16.
 */

public class HistoricalQuoteColumns {
    @DataType(DataType.Type.INTEGER) @PrimaryKey
    @AutoIncrement
    public static final String COLUMN_ID = "_id";

    @DataType(DataType.Type.TEXT) @NotNull
    public static final String COLUMN_SYMBOL = "symbol";

    @DataType(DataType.Type.INTEGER) @NotNull
    public static final String COLUMN_DATE = "date";

    @DataType(DataType.Type.REAL) @NotNull
    public static final String COLUMN_CLOSE_PRICE = "close_price";
}
