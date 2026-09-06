package com.android.launcher3.util;

import static android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.OpenParams;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An extension of {@link SQLiteOpenHelper} with utility methods for a single table cache DB.
 * Any exception during write operations are ignored, and any version change causes a DB reset.
 */
public class SQLiteCacheHelper {
    private static final String TAG = "SQLiteCacheHelper";

    private static final boolean IN_MEMORY_CACHE = false;

    private final String mTableName;
    private final MySQLiteOpenHelper mOpenHelper;
    private final Supplier<String> mCreationCommand;

    private boolean mIgnoreWrites;

    public SQLiteCacheHelper(Context context, String name, int version,
            String tableName, Supplier<String> creationCommand) {
        if (IN_MEMORY_CACHE) {
            name = null;
        }
        mTableName = tableName;
        mCreationCommand = creationCommand;
        mOpenHelper = new MySQLiteOpenHelper(context, name, version);

        mIgnoreWrites = false;
    }

    /**
     * @see SQLiteDatabase#delete(String, String, String[])
     */
    public void delete(String whereClause, String[] whereArgs) {
        if (mIgnoreWrites) {
            return;
        }
        try {
            mOpenHelper.getWritableDatabase().delete(mTableName, whereClause, whereArgs);
        } catch (SQLiteFullException e) {
            onDiskFull(e);
        } catch (SQLiteException e) {
            Log.d(TAG, "Ignoring sqlite exception", e);
        }
    }

    /**
     * @see SQLiteDatabase#insertWithOnConflict(String, String, ContentValues, int)
     */
    public void insertOrReplace(ContentValues values) {
        if (mIgnoreWrites) {
            return;
        }
        try {
            mOpenHelper.getWritableDatabase().insertWithOnConflict(
                    mTableName, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteFullException e) {
            onDiskFull(e);
        } catch (SQLiteException e) {
            Log.d(TAG, "Ignoring sqlite exception", e);
        }
    }

    private void onDiskFull(SQLiteFullException e) {
        Log.e(TAG, "Disk full, all write operations will be ignored", e);
        mIgnoreWrites = true;
    }

    /**
     * @see SQLiteDatabase#query(String, String[], String, String[], String, String, String)
     */
    public Cursor query(String[] columns, String selection, String[] selectionArgs) {
        return mOpenHelper.getReadableDatabase().query(
                mTableName, columns, selection, selectionArgs, null, null, null);
    }

    /** Helper method to read a single entry from cache */
    public <T> T querySingleEntry(String[] columns, String selection, String[] selectionArgs,
            T defaultValue, Function<Cursor, T> callback) {

        try (Cursor c = query(columns, selection, selectionArgs)) {
            if (c.moveToNext()) {
                return callback.apply(c);
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading cache", e);
        }
        return defaultValue;
    }

    public void clear() {
        mOpenHelper.clearDB(mOpenHelper.getWritableDatabase());
    }

    public void close() {
        mOpenHelper.close();
    }

    protected void onCreateTable(SQLiteDatabase db) {
        db.execSQL(mCreationCommand.get());
    }

    /**
     * A private inner class to prevent direct DB access.
     */
    private class MySQLiteOpenHelper extends SQLiteOpenHelper {

        public MySQLiteOpenHelper(Context context, String name, int version) {
            super(context, name, version, createNoLocaleParams());
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            onCreateTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        private void clearDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + mTableName);
            onCreate(db);
        }
    }

    /**
     * Returns {@link OpenParams} which can be used to create databases without support for
     * localized collators.
     */
    public static OpenParams createNoLocaleParams() {
        return new OpenParams.Builder().addOpenFlags(NO_LOCALIZED_COLLATORS).build();
    }
}
