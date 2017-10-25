package com.luxand.fr.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.luxand.FSDK;
import com.luxand.fr.ui.ProcessImageAndDrawResults;
import com.luxand.fr.util.Dialog;
import com.luxand.fr.util.FaceRectangle;

import org.sid.fr.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lib.folderpicker.FolderPicker;

import static com.luxand.fr.activities.CameraActivity.SERIAL_KEY;


public class MainActivity extends Activity {

    private static final int FOLDER_PICKER_CODE = 78;
    private static final int FILE_PICKER_CODE = 786;
    private static final String TAG = MainActivity.class.getSimpleName();
    EditText dirLoc;
    Button btCamera, btLoadFile;
    TextView tvLoc;
    TextView tvFace, tv_percentage;
    ImageView imgDisp, imgFace;
    RadioButton rbDir, rbFile, rbEigen, rbFisher, rbLbph;
    RadioGroup rbgType, rbgAlg;

    private String afilename;
    private String folderLocation;
    private int chooseType;

    private Context mContext;
    private Bitmap mutableBitmap, mutableBitmapFace;
    private String filename;
    private String mPredictResult;
    ProgressBar pbar;

    private ProcessImageBackground mTask;
    private static String m_chosenDir = "";
    LogReport logReport = new LogReport();
    private Dialog mDialog;

    private final String database = "Memory50.dat";
    private ProcessImageAndDrawResults mDraw;

    protected boolean processing;
    private int MAX_FACES = 2;
    private FSDK.HTracker mTracker;


    protected void onCreate(Bundle savedInstanceBundle) {
        processing = true; //prevent user from pushing the button while initializing

        super.onCreate(savedInstanceBundle);
        setContentView(R.layout.activity_main);

        mTask = new ProcessImageBackground();

//        dirLoc = (EditText) findViewById(R.id.editText);
        btCamera = (Button) findViewById(R.id.btCamera);
        btLoadFile = (Button) findViewById(R.id.btLoadFile);

        rbDir = (RadioButton) findViewById(R.id.rbDir);
        rbFile = (RadioButton) findViewById(R.id.rbFile);
        rbEigen = (RadioButton) findViewById(R.id.rb_eigen);
        rbFisher = (RadioButton) findViewById(R.id.rb_fisher);
        rbLbph = (RadioButton) findViewById(R.id.rb_lbph);

        rbgType = (RadioGroup) findViewById(R.id.rbgType);
        rbgAlg = (RadioGroup) findViewById(R.id.rbgAlg);

        tvLoc = (TextView) findViewById(R.id.tvLoc);

        pbar = (ProgressBar) findViewById(R.id.progbar_h);

        imgDisp = (ImageView) findViewById(R.id.imgDisp);
        imgFace = (ImageView) findViewById(R.id.imgDispFace);
        tvFace = (TextView) findViewById(R.id.tvFace);
        tv_percentage = (TextView) findViewById(R.id.tv_percentage);
//        imgDisp = (ImageView) findViewById(R.id.draw_view);
//        dirLoc.setInputType(InputType.TYPE_NULL);
//        dirLoc.setKeyListener(null);
        tvLoc.setText(R.string.no_file_path);

        int res = FSDK.ActivateLibrary(SERIAL_KEY);

        mDialog = new Dialog();

        if (res != FSDK.FSDKE_OK) {
            mDialog.showErrorAndClose(this, "FaceSDK activation failed", res);
        } else {

            initListener();

            FSDK.Initialize();

            String templatePath = this.getApplicationInfo().dataDir + "/" + database;

            mDraw = new ProcessImageAndDrawResults(this);
            mTracker = new FSDK.HTracker();

            if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw.mTracker, templatePath)) {

                res = FSDK.CreateTracker(mTracker);
                if (FSDK.FSDKE_OK != res) {
                    mDialog.showErrorAndClose(this, "Error creating tracker", res);
                }
            }

            int errpos[] = new int[1];
            FSDK.SetTrackerMultipleParameters(mDraw.mTracker, "ContinuousVideoFeed=true;RecognitionPrecision=0;Threshold=0.997;Threshold2=0.9995;ThresholdFeed=0.97;MemoryLimit=1000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=70;FaceDetectionThreshold=5;", errpos);
            if (errpos[0] != 0) {
                mDialog.showErrorAndClose(this, "Error setting tracker parameters, position", errpos[0]);
            }

        }


    }

    protected void onPause() {
        Log.d(TAG, "onPause() called.");

        super.onPause();

        if (isFinishing()) {
            //
            // Exit by user pressing KEY_BACK.
            //
//            Settings.getInstance().sync();
//            LocalNameList.getInstance().store();
            //FaceDetector.getInstance().destroy();
//            WhooLog.close();
        } else {
            // Exit for other reasons.
        }
    }

    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume: " );
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        FSDK.SaveTrackerMemoryToFile(mTracker, templatePath);

    }

    protected void onDestroy() {
        super.onDestroy();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        m_chosenDir = folderLocation;


        if (requestCode == FOLDER_PICKER_CODE && resultCode == Activity.RESULT_OK) {

            Log.e(TAG, "onActivityResult: " + intent);
            folderLocation = intent.getExtras().getString("data");
            Log.i("folderLocation", folderLocation);
            tvLoc.setText(folderLocation);

        } else if (requestCode == FILE_PICKER_CODE && resultCode == Activity.RESULT_OK) {
            Log.e(TAG, "onActivityResult: " + intent);
            folderLocation = intent.getExtras().getString("data");
            Log.i("folderLocation", folderLocation);
            tvLoc.setText(folderLocation);

        }
    }

    public void openCamera(View view) {

        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);

    }

    public void loadFile(File imgFile) {

        String fileLoc = imgFile.toString();

        filename = imgFile.getName();
        Log.e(TAG, "loadFile: Detected filename " + filename);
        int pos = filename.lastIndexOf(".");
        if (pos > 0) {
            filename = filename.substring(0, pos);
        }
//        File imgFile = new File(folderLocation);

        if (imgFile.exists()) {


            // Init detector Proses Image
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inMutable = true;
            Bitmap b = BitmapFactory.decodeFile(fileLoc, options);

            Canvas c = new Canvas(b);

            procLuxand();


//            Toast.makeText(getApplicationContext(), "Load File Success : "+ fr.getResult(), Toast.LENGTH_SHORT).show();

        } else {
            Log.e(TAG, "loadFile: " + "Image Not found");
        }
    }

    private void procLuxand() {

    }

    private void procOpencv() {

    }

    private void initListener() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        rbgType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
//                Log.e(TAG, "onCheckedChanged: "+ checkedId);
                if (checkedId == R.id.rbDir) {
                    Log.e(TAG, "onCheckedChanged: " + "Directory");
                    chooseType = 1;
                } else if (checkedId == R.id.rbFile) {
                    Log.e(TAG, "onCheckedChanged: " + "File");
                    chooseType = 0;
                }
            }
        });

        btLoadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (folderLocation == null) {

                    // Directory
                    m_chosenDir = String.valueOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/matching"));

                } else {
                    m_chosenDir = folderLocation;
                }

                processDir(m_chosenDir);

            }
        });
    }

    private void processDir(String m_chosenDir) {
        if (new File(m_chosenDir).exists() && !MainActivity.m_chosenDir.isEmpty()) {

            // Execution main process
//                    task.execute();
            if (mTask == null) {
                mTask = new ProcessImageBackground();
            }
            mTask.execute();
//                    mTask = (ProcessImageBackground) new ProcessImageBackground().execute();
//                    btn_start.setClickable(false);
            Toast.makeText(MainActivity.this, "Mode : Batch Mode " + m_chosenDir, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "File folder not found " + m_chosenDir, Toast.LENGTH_SHORT).show();

        }
    }

    private DialogInterface.OnClickListener eksekusi() {
        Log.e(TAG, "eksekusi: ");
        mTask.execute();
        return null;
    }

    public void setLocation(View view) {
        int picker_code = 100;

        Log.e(TAG, "setLocation: " + chooseType);
        Intent intent = new Intent(this, FolderPicker.class);
//        startActivityForResult(intent, FOLDER_PICKER_CODE);

        if (chooseType == 1) {
            startActivityForResult(intent, FOLDER_PICKER_CODE);
        } else if (chooseType == 0) {
            intent.putExtra("title", "Select file to upload");
            intent.putExtra("location", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
            intent.putExtra("pickFiles", true);
            startActivityForResult(intent, FILE_PICKER_CODE);
//
        }

    }

//    public void SaveImage(Mat image) {
//        Boolean bool = null;
//        String newfilename = "faceDetection.png";
//
//        File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
//        path.mkdirs();
//
//        File file = new File(path, "image.png");
//        afilename = file.toString();
//
//        bool = Highgui.imwrite(afilename, image);
//
//        System.out.println(String.format("Writing %s", newfilename));
//
//        if (bool)
//            Log.e(TAG, "loadFile: " + "SUCCESS writing image to external storage");
//        else
//            Log.e(TAG, "Fail writing image to external storage");
//    }

    private class ProcessImageBackground extends AsyncTask<String, Integer, List<RowItem>> {

        public FSDK.HTracker mTracker;

        protected FSDK.FSDK_Features features;
        protected FSDK.TFacePosition faceCoords;
        protected String picturePath;
        protected FSDK.HImage picture;
        protected int result;
        private long mTouchedID;

        int myProgress;
        private Activity context;
        List<RowItem> rowItems;
        int noOfPaths;
        private File[] list;

        @Override
        protected List<RowItem> doInBackground(String... urls) {
            noOfPaths = urls.length;
            rowItems = new ArrayList<RowItem>();

            String strDir = MainActivity.m_chosenDir;
            String mCascadeFileName = "haarcascade_frontalface_alt.xml";
            File cascadeDir = getApplicationContext().getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, mCascadeFileName);
            mTracker = new FSDK.HTracker();

//            Check if Input is Directory or Not

            if (new File(strDir).isDirectory()) {

                if (strDir.isEmpty()) {
                    Log.e(TAG, "doInBackground: " + "Directory Empty");

                } else {

                    File dir = new File(strDir);

                    list = dir.listFiles();

                    // Initialize Log Report File
                    logReport.initLogReport();

                    // Test scalefactor, neigbour
                    for (File f : list) {

                        // Convert to Gray
//                        BitmapFactory.Options options = new BitmapFactory.Options();
//                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//                        Bitmap bitmap = BitmapFactory.decodeFile(f.getPath(), options);
//                        int faceSize = 128;

                        faceCoords = new FSDK.TFacePosition();
                        picture = new FSDK.HImage();
                        result = FSDK.LoadImageFromFile(picture, f.getAbsolutePath());

                        // Load image to FaceSDK
                        FSDK.HImage Image = new FSDK.HImage();
                        FSDK.FSDK_IMAGEMODE imagemode = new FSDK.FSDK_IMAGEMODE();
                        imagemode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;
//                        FSDK.LoadImageFromBuffer(Image, mRGBData, mImageWidth, mImageHeight, mImageWidth*3, imagemode);
                        FSDK.MirrorImage(Image, false);
                        FSDK.HImage RotatedImage = new FSDK.HImage();
                        FSDK.CreateEmptyImage(RotatedImage);

                        final long[] mIDs = new long[MAX_FACES];
                        final FaceRectangle[] mFacePositions = new FaceRectangle[MAX_FACES];

                        long IDs[] = new long[MAX_FACES];
                        FaceRectangle rects[] = new FaceRectangle[MAX_FACES];

                        long face_count[] = new long[1];

                        FSDK.FeedFrame(mTracker, 0, RotatedImage, face_count, IDs);
                        FSDK.FreeImage(RotatedImage);

                        for (int i=0; i < MAX_FACES; ++i) {
                            mFacePositions[i] = new FaceRectangle();
                            mFacePositions[i].x1 = 0;
                            mFacePositions[i].y1 = 0;
                            mFacePositions[i].x2 = 0;
                            mFacePositions[i].y2 = 0;
                            mIDs[i] = IDs[i];
                        }

                        for (int i=0; i < MAX_FACES; ++i) {
                            if (rects[i] != null) {
                                mTouchedID = IDs[i];
                            }
                        }

                        for (int i = 0; i < (int)face_count[0]; ++i) {
                            FSDK.FSDK_Features Eyes = new FSDK.FSDK_Features();
                            FSDK.GetTrackerEyes(mTracker, 0, mIDs[i], Eyes);

//                            GetFaceFrame(Eyes, mFacePositions[i]);
//                            mFacePositions[i].x1 *= ratio;
//                            mFacePositions[i].y1 *= ratio;
//                            mFacePositions[i].x2 *= ratio;
//                            mFacePositions[i].y2 *= ratio;
                        }


                        if (result == FSDK.FSDKE_OK){
                            result = FSDK.DetectFace(picture, faceCoords);
                            features = new FSDK.FSDK_Features();
                            if (result == FSDK.FSDKE_OK){
                                result = FSDK.DetectFacialFeaturesInRegion(picture, faceCoords, features);

                                //
                                FSDK.LockID(mTracker, mTouchedID);
                                FSDK.SetName(mTracker, mTouchedID, f.getName());
                                FSDK.UnlockID(mTracker, mTouchedID);

                            }
                        }

                        processing = false;


                        myProgress++;

//                        publishProgress(myProgress * 100 / (7*4*(100-1)));
                        publishProgress(myProgress * 100 / list.length);

////                    rowItems.add(new RowItem(map));
//
//                        try {
//                            Thread.sleep(1000);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//

                    }

                    m_chosenDir = "";


                }
            } else {
                double a = 0;

            }

            return rowItems;
        }

        @Override
        protected void onPreExecute() {
//            Toast.makeText(MainActivity.this, "onPreExecute", Toast.LENGTH_LONG).show();
            myProgress = 0;
        }

        @Override
        protected void onPostExecute(List<RowItem> aVoid) {
//            Toast.makeText(MainActivity.this, "onPostExecute", Toast.LENGTH_LONG).show();
            btLoadFile.setClickable(true);
            mTask = null;

            String templatePath = MainActivity.this.getApplicationInfo().dataDir + "/" + database;
            FSDK.SaveTrackerMemoryToFile(mTracker, templatePath);

        }

//        @Override
//        protected Void doInBackground(Void... params) {
//            while (myProgress<100){
//                myProgress++;
//                publishProgress(myProgress);
//                SystemClock.sleep(100);
//            }
//            return null;
//        }


        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            pbar.setProgress(values[0]);
            tv_percentage.setText(String.format("%s %%", values[0].toString()));
            Log.e(TAG, "onProgressUpdate: values " + values[0]);
        }

    }

    class LogReport {

        Date date = new Date() ;
        SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyy-hhmmss") ;
        String path = Environment.getExternalStorageDirectory().toString();
        File dirLog = new File(path + "/OCVReportFR");
        public File fileLog = new File(dirLog, "OCVFaceData"+ dateFormat.format(date) +".csv");
        BufferedWriter bufw;

        //    public void saveLog(String name, int[] intBitmap, byte[] bytes) {
//    }
        public void saveLog(String name, String result_name, double confidence, String numFace) {

            // Char comma 44
            // Char LineFeed 10
            // Char ; 59

            try {
                bufw = new BufferedWriter(new FileWriter(fileLog, true));
                bufw.write(name);
                bufw.write(44);
                bufw.write(result_name);
                bufw.write(44);
                bufw.write(String.valueOf(confidence));
                bufw.write(44);
                bufw.write(numFace);

//            for (int i: facePoints) {
//                bufw.write(44);
//                bufw.write(String.valueOf(i));
//            }

                bufw.write(10);
                bufw.close();
//            bufw.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }


        }

        public void initLogReport() {

            String[] header_fr = new String[]{};
            String[] header = new String[]{
                    "filename",
                    "result_name",
            };

            try {
                if (!dirLog.exists()) dirLog.mkdir();
                if (!fileLog.exists()) fileLog.createNewFile();

                bufw = new BufferedWriter(new FileWriter(fileLog, true));
                bufw.write("FILE_NAME");
//            for (String s: header_fr) {
                for (String s: header) {
                    bufw.write(44);
                    bufw.write(s);
                }
                bufw.write(10);
                bufw.close();
//            bufw.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }    }
    }

    class RowItem {

        private Bitmap bitmapImage;

        public RowItem(Bitmap bitmapImage){
            this.bitmapImage = bitmapImage;
        }

        public Bitmap getBitmapImage() {
            return bitmapImage;
        }

        public void setBitmapImage(Bitmap bitmapImage){
            this.bitmapImage = bitmapImage;
        }

    }

}