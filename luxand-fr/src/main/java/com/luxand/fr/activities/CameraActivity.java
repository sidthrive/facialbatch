package com.luxand.fr.activities;

import com.luxand.FSDK;
import com.luxand.FSDK.HTracker;
import com.luxand.fr.ui.ProcessImageAndDrawResults;
import com.luxand.fr.util.Config;
import com.luxand.fr.util.Preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import org.sid.fr.R;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

public class CameraActivity extends Activity implements OnClickListener {

	public static final String SERIAL_KEY = "";
	private Preview mPreview;
	private ProcessImageAndDrawResults mDraw;
	private final String database = "Memory50.dat";
	private final String help_text = "Luxand Face Recognition\n\nJust tap any detected face and name it. The app will recognize this face further. For best results, hold the device at arm's length. You may slowly rotate the head for the app to memorize you at multiple views. The app can memorize several persons. If a face is not recognized, tap and name it again.\n\nThe SDK is available for mobile developers: www.luxand.com/facesdk";
	private String serialKey;
	Config config = new Config();

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int res = 0;
		try {
			res = FSDK.ActivateLibrary(config.getSerialKey("serialkey", getApplicationContext()));
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (res != FSDK.FSDKE_OK) {
			showErrorAndClose("FaceSDK activation failed", res);
		} else {
			FSDK.Initialize();

			// Hide the window title (it is done in manifest too)
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			requestWindowFeature(Window.FEATURE_NO_TITLE);

			// Lock orientation
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

			// Camera layer and drawing layer
			mDraw = new ProcessImageAndDrawResults(this);
			mPreview = new Preview(this, mDraw);
			mDraw.mTracker = new HTracker();
			String templatePath = this.getApplicationInfo().dataDir + "/" + database;
			if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw.mTracker, templatePath)) {
				res = FSDK.CreateTracker(mDraw.mTracker);
				if (FSDK.FSDKE_OK != res) {
					showErrorAndClose("Error creating tracker", res);
				}
			}
			int errpos[] = new int[1];
			FSDK.SetTrackerMultipleParameters(mDraw.mTracker, "ContinuousVideoFeed=true;RecognitionPrecision=0;Threshold=0.997;Threshold2=0.9995;ThresholdFeed=0.97;MemoryLimit=1000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=70;FaceDetectionThreshold=5;", errpos);
			if (errpos[0] != 0) {
				showErrorAndClose("Error setting tracker parameters, position", errpos[0]);
			}

			setContentView(mPreview); //creates CameraActivity contents
			addContentView(mDraw, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

			// Menu
			LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View buttons = inflater.inflate(R.layout.bottom_menu, null);
			buttons.findViewById(R.id.helpButton).setOnClickListener(this);
			buttons.findViewById(R.id.clearButton).setOnClickListener(this);
			addContentView(buttons, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		}
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.helpButton) {
			showMessage(help_text);
		} else if (view.getId() == R.id.clearButton) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Are you sure to clear the memory?" )
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialogInterface, int j) {
						pauseProcessingFrames();
						FSDK.ClearTracker(mDraw.mTracker);
						resumeProcessingFrames();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialogInterface, int j) {
					}
				})
				.setCancelable(false) // cancel with button only
				.show();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		pauseProcessingFrames();
		String templatePath = this.getApplicationInfo().dataDir + "/" + database;
		FSDK.SaveTrackerMemoryToFile(mDraw.mTracker, templatePath);		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		resumeProcessingFrames();
	}

	public void showErrorAndClose(String error, int code) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(error + ": " + code)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						android.os.Process.killProcess(android.os.Process.myPid());
					}
				})
				.show();
	}

	public void showMessage(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
					}
				})
				.setCancelable(false) // cancel with button only
				.show();
	}

	private void pauseProcessingFrames() {
		mDraw.mStopping = 1;
		
		// It is essential to limit wait time, because mStopped will not be set to 0, if no frames are feeded to mDraw
		for (int i=0; i<100; ++i) {
			if (mDraw.mStopped != 0) break; 
			try { Thread.sleep(10); }
			catch (Exception ex) {}
		}
	}
	
	private void resumeProcessingFrames() {
		mDraw.mStopped = 0;
		mDraw.mStopping = 0;
	}

}







