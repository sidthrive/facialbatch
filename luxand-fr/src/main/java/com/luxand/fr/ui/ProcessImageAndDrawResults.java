package com.luxand.fr.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import com.luxand.FSDK;
import com.luxand.fr.util.FaceRectangle;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.content.ContentValues.TAG;

/**
 * Created by wildan on 10/24/17.
 */

// Draw graphics on top of the video
public class ProcessImageAndDrawResults extends View {
    public FSDK.HTracker mTracker;

    final int MAX_FACES = 5;
    final FaceRectangle[] mFacePositions = new FaceRectangle[MAX_FACES];
    final long[] mIDs = new long[MAX_FACES];
    final Lock faceLock = new ReentrantLock();
    int mTouchedIndex;
    long mTouchedID;
    public int mStopping;
    public int mStopped;

    Context mContext;
    Paint mPaintGreen, mPaintBlue, mPaintBlueTransparent;
    public byte[] mYUVData;
    public byte[] mRGBData;
    public int mImageWidth;
    public int mImageHeight;
    boolean first_frame_saved;
    public boolean rotated;
    private FSDK.FSDK_FaceTemplate faceToMatch;

    public ProcessImageAndDrawResults(Context context) {
        super(context);

        mTouchedIndex = -1;
        mStopping = 0;
        mStopped = 0;
        rotated = false;
        mContext = context;
        mPaintGreen = new Paint();
        mPaintGreen.setStyle(Paint.Style.FILL);
        mPaintGreen.setColor(Color.GREEN);
        mPaintGreen.setTextSize(25);
        mPaintGreen.setTextAlign(Paint.Align.CENTER);
        mPaintBlue = new Paint();
        mPaintBlue.setStyle(Paint.Style.FILL);
        mPaintBlue.setColor(Color.BLUE);
        mPaintBlue.setTextSize(25);
        mPaintBlue.setTextAlign(Paint.Align.CENTER);

        mPaintBlueTransparent = new Paint();
        mPaintBlueTransparent.setStyle(Paint.Style.STROKE);
        mPaintBlueTransparent.setStrokeWidth(2);
        mPaintBlueTransparent.setColor(Color.BLUE);
        mPaintBlueTransparent.setTextSize(25);

        //mBitmap = null;
        mYUVData = null;
        mRGBData = null;

        first_frame_saved = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mStopping == 1) {
            mStopped = 1;
            super.onDraw(canvas);
            return;
        }

        if (mYUVData == null || mTouchedIndex != -1) {
            super.onDraw(canvas);
            return; //nothing to process or name is being entered now
        }

        int canvasWidth = canvas.getWidth();
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);

        // Load image to FaceSDK
        FSDK.HImage Image = new FSDK.HImage();
        FSDK.FSDK_IMAGEMODE imagemode = new FSDK.FSDK_IMAGEMODE();
        imagemode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;
        FSDK.LoadImageFromBuffer(Image, mRGBData, mImageWidth, mImageHeight, mImageWidth*3, imagemode);
        FSDK.MirrorImage(Image, false);
        FSDK.HImage RotatedImage = new FSDK.HImage();
        FSDK.CreateEmptyImage(RotatedImage);

        //it is necessary to work with local variables (onDraw called not the time when mImageWidth,... being reassigned, so swapping mImageWidth and mImageHeight may be not safe)
        int ImageWidth = mImageWidth;
        //int ImageHeight = mImageHeight;
        if (rotated) {
            ImageWidth = mImageHeight;
            //ImageHeight = mImageWidth;
            FSDK.RotateImage90(Image, -1, RotatedImage);
        } else {
            FSDK.CopyImage(Image, RotatedImage);
        }
        FSDK.FreeImage(Image);

        // Save first frame to gallery to debug (e.g. rotation angle)
		/*
		if (!first_frame_saved) {
			first_frame_saved = true;
			String galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
			FSDK.SaveImageToFile(RotatedImage, galleryPath + "/first_frame.jpg"); //frame is rotated!
		}
		*/

        long IDs[] = new long[MAX_FACES];
        long face_count[] = new long[1];

        FSDK.FeedFrame(mTracker, 0, RotatedImage, face_count, IDs);
        FSDK.FreeImage(RotatedImage);

        faceLock.lock();

        for (int i=0; i < MAX_FACES; ++i) {
            mFacePositions[i] = new FaceRectangle();
            mFacePositions[i].x1 = 0;
            mFacePositions[i].y1 = 0;
            mFacePositions[i].x2 = 0;
            mFacePositions[i].y2 = 0;
            mIDs[i] = IDs[i];
        }

        float ratio = (canvasWidth * 1.0f) / ImageWidth;
        for (int i = 0; i < (int)face_count[0]; ++i) {
            FSDK.FSDK_Features Eyes = new FSDK.FSDK_Features();
            FSDK.GetTrackerEyes(mTracker, 0, mIDs[i], Eyes);

            GetFaceFrame(Eyes, mFacePositions[i]);
            mFacePositions[i].x1 *= ratio;
            mFacePositions[i].y1 *= ratio;
            mFacePositions[i].x2 *= ratio;
            mFacePositions[i].y2 *= ratio;
        }

        faceLock.unlock();

        // Mark and name faces
        for (int i=0; i < face_count[0]; ++i) {
            canvas.drawRect(mFacePositions[i].x1, mFacePositions[i].y1, mFacePositions[i].x2, mFacePositions[i].y2, mPaintBlueTransparent);

            boolean named = false;
            if (IDs[i] != -1) {
                String names[] = new String[1];
                FSDK.GetAllNames(mTracker, IDs[i], names, 1024);
                if (names[0] != null && names[0].length() > 0) {
                    canvas.drawText(names[0], (mFacePositions[i].x1+mFacePositions[i].x2)/2, mFacePositions[i].y2+30, mPaintBlue);
                    named = true;
                }
            }
            if (!named) {
                canvas.drawText("Tap to name", (mFacePositions[i].x1+mFacePositions[i].x2)/2, mFacePositions[i].y2+30, mPaintGreen);
            }
        }

        super.onDraw(canvas);
    } // end onDraw method

    @Override
    public boolean onTouchEvent(MotionEvent event) { //NOTE: the method can be implemented in Preview class
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                int x = (int)event.getX();
                int y = (int)event.getY();

                faceLock.lock();
                FaceRectangle rects[] = new FaceRectangle[MAX_FACES];
                long IDs[] = new long[MAX_FACES];
                for (int i=0; i<MAX_FACES; ++i) {
                    Log.e(TAG, "onTouchEvent: i = "+i );
                    rects[i] = new FaceRectangle();
                    rects[i].x1 = mFacePositions[i].x1;
                    rects[i].y1 = mFacePositions[i].y1;
                    rects[i].x2 = mFacePositions[i].x2;
                    rects[i].y2 = mFacePositions[i].y2;
                    IDs[i] = mIDs[i];
                }
                faceLock.unlock();

                for (int i=0; i < MAX_FACES; ++i) {
                    if (rects[i] != null && rects[i].x1 <= x && x <= rects[i].x2 && rects[i].y1 <= y && y <= rects[i].y2 + 30) {
                        mTouchedID = IDs[i];

                        mTouchedIndex = i;

                        // requesting name on tapping the face
                        final EditText input = new EditText(mContext);
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setMessage("Enter person's name" )
                                .setView(input)
                                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialogInterface, int j) {
                                        FSDK.LockID(mTracker, mTouchedID);
                                        Log.e(TAG, "onClick: mTouchedID "+ mTouchedID );
                                        FSDK.SetName(mTracker, mTouchedID, input.getText().toString());
                                        FSDK.UnlockID(mTracker, mTouchedID);
                                        mTouchedIndex = -1;
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialogInterface, int j) {
                                        mTouchedIndex = -1;
                                    }
                                })
                                .setCancelable(false) // cancel with button only
                                .show();

                        break;
                    }
                }
        }
        return true;
    }

    static public void decodeYUV420SP(byte[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                rgb[3*yp] = (byte) ((r >> 10) & 0xff);
                rgb[3*yp+1] = (byte) ((g >> 10) & 0xff);
                rgb[3*yp+2] = (byte) ((b >> 10) & 0xff);
                ++yp;
            }
        }
    }

    int GetFaceFrame(FSDK.FSDK_Features Features, FaceRectangle fr) {
        if (Features == null || fr == null)
            return FSDK.FSDKE_INVALID_ARGUMENT;

        float u1 = Features.features[0].x;
        float v1 = Features.features[0].y;
        float u2 = Features.features[1].x;
        float v2 = Features.features[1].y;
        float xc = (u1 + u2) / 2;
        float yc = (v1 + v2) / 2;
        int w = (int)Math.pow((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1), 0.5);

        fr.x1 = (int)(xc - w * 1.6 * 0.9);
        fr.y1 = (int)(yc - w * 1.1 * 0.9);
        fr.x2 = (int)(xc + w * 1.6 * 0.9);
        fr.y2 = (int)(yc + w * 2.1 * 0.9);
        if (fr.x2 - fr.x1 > fr.y2 - fr.y1) {
            fr.x2 = fr.x1 + fr.y2 - fr.y1;
        } else {
            fr.y2 = fr.y1 + fr.x2 - fr.x1;
        }
        return 0;
    }


    public void setFaceToMatch(String picturePath) {
        Log.d("picturePath", picturePath);
        FSDK.HImage image = new FSDK.HImage();
        int result = FSDK.LoadImageFromFile(image, picturePath);
        Log.d("LoadImageFromFile", String.format("%d", result));
        FSDK.FSDK_FaceTemplate faceTemplate = new FSDK.FSDK_FaceTemplate();
        result = FSDK.GetFaceTemplate(image, faceTemplate);
        Log.d("GetFaceTemplate", String.format("%d", result));
        this.faceToMatch = (result == 0) ? faceTemplate : null;
        FSDK.FreeImage(image);
    }

} // end of ProcessImageAndDrawResults class


