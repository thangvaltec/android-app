/*
 * PsDataManager.java
 *
 *	All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2021
 */

package com.fujitsu.frontech.palmsecure_sample.data;

import java.io.IOException;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_BIR;
import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_BIR_ARRAY_POPULATION;
import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_IDENTIFY_POPULATION;
import com.fujitsu.frontech.palmsecure.util.PalmSecureConstant;
import com.fujitsu.frontech.palmsecure.util.PalmSecureException;
import com.fujitsu.frontech.palmsecure.util.PalmSecureHelper;
import com.fujitsu.frontech.palmsecure_sample.exception.PsAplException;
import com.fujitsu.frontech.palmsecure_gui_sample.BuildConfig;
import com.fujitsu.frontech.palmsecure_gui_sample.R;

public class PsDataManager {

	private static final String TAG = "PsDataManager";
	private static final String VEIN_DB_NAME = "PsVeinData.sqlite3";
	private static final int VERSION = 1;
	private final String mSensorType;
	private final String mDataType;
	private final PsDbHelper mDbHelper;

	public PsDataManager(Context cx, long sensorType, long dataType) {
		if (BuildConfig.DEBUG) {
			Log.i(TAG, "new PsDataManager");
		}

		mDbHelper = new PsDbHelper(cx, VEIN_DB_NAME);
		mSensorType = Long.toString(sensorType);
		mDataType = Long.toString(dataType);
	}

	private static class PsDbHelper extends SQLiteOpenHelper {

		public PsDbHelper(Context context, String name) {
			super(context, name, null, VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(
					"create table veindata_table(" +
							" sensortype long," +
							" datatype long," +
							" id text," +
							" veindata blob," +
							" primary key(id, sensortype, datatype) );"
			);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		}
	}

	public boolean convertBioAPI_DataToDB(JAVA_BioAPI_BIR Data, String Name) throws PsAplException, PalmSecureException {

		byte[] veinData = null;
		SQLiteDatabase db = null;
		boolean return_value = false;
		long db_result = 0;

		if (Data == null || Name == null)
		{
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		}

		//Create a byte array of vein data
		///////////////////////////////////////////////////////////////////////////
		try {
			veinData = PalmSecureHelper.convertBIRToByte(Data);
		} catch (IOException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertBIRToByte", e);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} catch (PalmSecureException pse) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertBIRToByte", pse);
			}
			throw pse;
		}
		///////////////////////////////////////////////////////////////////////////

		try {
			db = mDbHelper.getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put("id", Name);
			values.put("sensortype", Long.valueOf(mSensorType));
			values.put("datatype", Long.valueOf(mDataType));
			values.put("veindata", veinData);

			db_result = db.insert("veindata_table", null, values);

			if (db_result > 0) {
				return_value = true;
			}
		} catch (SQLiteException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "convertBioAPI_DataToDB", e);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} finally {
			if (db != null) {
				db.close();
			}
		}

		return return_value;
	}

	public JAVA_BioAPI_BIR convertDBToBioAPI_Data(String Name) throws PsAplException, PalmSecureException {

		SQLiteDatabase db = null;
		Cursor c = null;
		String sql = "select id, veindata from veindata_table where id = ? and sensortype = ? and datatype = ?;";
		byte[] veindata = null;

		if (Name == null)
		{
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		}

		try {
			db = mDbHelper.getReadableDatabase();
			c = db.rawQuery(sql, new String[] { Name, mSensorType, mDataType });

			if (c != null && c.moveToFirst()) {
				veindata = c.getBlob(c.getColumnIndex("veindata"));
			}
		} catch (SQLiteException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "convertDBToBioAPI_Data", e);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} finally {
			if (c != null) {
				c.close();
			}
			if (db != null) {
				db.close();
			}
		}

		JAVA_BioAPI_BIR bir = null;

		try {
			bir = PalmSecureHelper.convertByteToBIR(veindata);
		} catch (IOException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertByteToBIR", e);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} catch (PalmSecureException pse) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertByteToBIR", pse);
			}
			throw pse;
		}

		return bir;
	}

	public JAVA_BioAPI_IDENTIFY_POPULATION convertDBToBioAPI_Data_All(ArrayList<String> Name)
			throws PsAplException, PalmSecureException {

		JAVA_BioAPI_IDENTIFY_POPULATION population = new JAVA_BioAPI_IDENTIFY_POPULATION();

		SQLiteDatabase db = null;
		Cursor c = null;
		String sql = "select id, veindata from veindata_table where sensortype = ? and datatype = ?;";

		if (Name == null)
		{
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		}

		try {
			db = mDbHelper.getReadableDatabase();
			c = db.rawQuery(sql, new String[] { mSensorType, mDataType });
		} catch (SQLiteException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "convertDBToBioAPI_Data_All", e);
			}
			if (c != null) {
				c.close();
			}
			if (db != null) {
				db.close();
			}

			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		}

		int memberNum = c.getCount();

		population.Type = PalmSecureConstant.JAVA_BioAPI_ARRAY_TYPE;
		population.BIRArray = new JAVA_BioAPI_BIR_ARRAY_POPULATION();
		population.BIRArray.NumberOfMembers = memberNum;

		JAVA_BioAPI_BIR[] members = new JAVA_BioAPI_BIR[memberNum];

		try {
			if (c != null && c.moveToFirst()) {
				int membersIndex = 0;
				int columnId = c.getColumnIndex("id");
				int columnVeindata = c.getColumnIndex("veindata");
				Name.clear();
				do {
					Name.add(c.getString(columnId));
					members[membersIndex] = PalmSecureHelper.convertByteToBIR(c.getBlob(columnVeindata));
					membersIndex++;
				} while (c.moveToNext());
			}
		} catch (IOException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertByteToBIR", e);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} catch (PalmSecureException pse) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "PalmSecureHelper.convertByteToBIR", pse);
			}
			throw pse;
		} finally {
			if (c != null) {
				c.close();
			}
			if (db != null) {
				db.close();
			}
		}

		population.BIRArray.Members = members;

		return population;
	}

	public void deleteDBToBioAPI_Data(String Name) throws PsAplException {

		SQLiteDatabase db = null;
		long db_result = 0;

		if (Name == null)
		{
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		}

		try {
			db = mDbHelper.getWritableDatabase();
			db_result = db.delete(
					"veindata_table",
					"id = ? and sensortype = ? and datatype = ?",
					new String[] { Name, mSensorType, mDataType });

			if (db_result <= 0) {
				PsAplException pae = new PsAplException(R.string.AplErrorFileDelete);
				throw pae;
			}
		} catch (SQLiteException e) {
			if (BuildConfig.DEBUG) {
				Log.e(TAG, "deleteBioAPI_DataToDB Name = " + Name);
			}
			PsAplException pae = new PsAplException(R.string.AplErrorSystemError);
			throw pae;
		} finally {
			if (db != null) {
				db.close();
			}
		}

		return;
	}
}
