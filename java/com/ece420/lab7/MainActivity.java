package com.ece420.lab7;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class MainActivity<Vec2i> extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";

    // UI Variables
    private Button controlButton;
    private SeekBar widthSeekbar;
    private SeekBar heightSeekbar;
    private TextView widthTextview;
    private TextView heightTextview;

    public static int appFlag = 0;
    // UI Variables
    private Button circButton;
    private Button rectButton;
    private Button lineButton;

    // Declare OpenCV based camera view base
    private CameraBridgeViewBase mOpenCvCameraView;
    // Camera size
    private int myWidth;
    private int myHeight;

    // Mat to store RGBA and Grayscale camera preview frame
    private Mat mRgba;
    private Mat mGray;

    //    // KCF Tracker variables
//    private Tracker myTracker;
    private Rect2d myROI = new Rect2d(0,0,0,0);
    private int myROIWidth = 70;
    private int myROIHeight = 70;
    private Scalar myROIColor = new Scalar(0,0,0);

    // define GHT variables
    private byte edge_colour = (byte) 255;		//search-/edge-colour (0= black); edit for other colours
    private int xref = 0, yref = 0;		// middlepoint for RTable-Calculation
    private ArrayList<Integer> rtable = new ArrayList<Integer>();		// RTable part I (vectors)
    private ArrayList<Integer> rot_rtable = new ArrayList<Integer>();
    private ArrayList<Integer> sca_rtable = new ArrayList<Integer>();
    private Thread[] threads;
    private byte [] pixels;		// temp pic-array
    private int [] delta;		// array for one-dimensional rtable pixel-offsets for a searhc-picture
    private ArrayList<Integer> hits = new ArrayList<Integer>();		// dynamic array for hits
    private int wref = 0, href = 0;		// width and height of reference-pic
    private int wsch = 0, hsch = 0;		// width and height of search-pics

    // Input variables
    private Mat accum; //accumulator matrix
    private Mat param;
    // contour points:
    private List<MatOfPoint> pts = new ArrayList<MatOfPoint>();
//    // R-table of template object:
//    private List<MatOfPoint2f> rtable = new ArrayList<MatOfPoint2f>();
    // number of intervals for angles of R-table:
    private int intervals =  180;
    // width of template contour
    private int wtemplate;
    // minimum and maximum width of scaled contour
    private int wmin = 0;
    private int wmax = 0;
    // minimum and maximum rotation allowed for template
    private int phimin = -45;
    private int phimax = 45;
    // dimension in pixels of squares in image
    private int rangeXY;
    // interval to increase scale
    private int rangeS;
    private int detect = -1;
    private Point center = new Point();

    // Calculate gradient angle for edge point
    private Mat angle(Mat image){
        Mat dx = new Mat();
        Mat dy = new Mat();
        Mat absdx = new Mat();
        Mat absdy = new Mat();
        Mat gradient = new Mat();
        Imgproc.Sobel(image,dx, CvType.CV_32F, 1, 0);
        Imgproc.Sobel(image,dy, CvType.CV_32F, 0, 1);
        Core.convertScaleAbs(dx,absdx);
        Core.convertScaleAbs(dy,absdy);
        Core.addWeighted( absdx, 0.5, absdy, 0.5, 0, gradient );
        return gradient;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Request User Permission on Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        // OpenCV Loader and Avoid using OpenCV Manager
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

//        // Setup color seek bar
//        colorSeekbar = (SeekBar) findViewById(R.id.colorSeekBar);
//        colorSeekbar.setProgress(50);
//        setColor(50);
//        colorSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
//        {
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
//            {
//                setColor(progress);
//            }
//            public void onStartTrackingTouch(SeekBar seekBar) {}
//            public void onStopTrackingTouch(SeekBar seekBar) {}
//        });

//        // Setup width seek bar
//        widthTextview = (TextView) findViewById(R.id.widthTextView);
//        widthSeekbar = (SeekBar) findViewById(R.id.widthSeekBar);
//        widthSeekbar.setProgress(myROIWidth - 20);
//        widthSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
//        {
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
//            {
//                // Only allow modification when not tracking
//                if(detect == -1) {
//                    myROIWidth = progress + 20;
//                }
//            }
//            public void onStartTrackingTouch(SeekBar seekBar) {}
//            public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
//
//        // Setup width seek bar
//        heightTextview = (TextView) findViewById(R.id.heightTextView);
//        heightSeekbar = (SeekBar) findViewById(R.id.heightSeekBar);
//        heightSeekbar.setProgress(myROIHeight - 20);
//        heightSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
//        {
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
//            {
//                // Only allow modification when not tracking
//                if(detect == -1) {
//                    myROIHeight = progress + 20;
//                }
//            }
//            public void onStartTrackingTouch(SeekBar seekBar) {}
//            public void onStopTrackingTouch(SeekBar seekBar) {}
//        });

        // Setup control button
        controlButton = (Button) findViewById(R.id.ControlButton);
        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 3;
                if (detect == -1) {
                    // Modify UI
                    controlButton.setText("Key");
//                    widthTextview.setVisibility(View.INVISIBLE);
//                    widthSeekbar.setVisibility(View.INVISIBLE);
//                    heightTextview.setVisibility(View.INVISIBLE);
//                    heightSeekbar.setVisibility(View.INVISIBLE);
                    // Modify tracking flag
                    detect = 0;
                }
                else if(detect == 1){
                    // Modify UI
                    controlButton.setText("Key");
//                    widthTextview.setVisibility(View.VISIBLE);
//                    widthSeekbar.setVisibility(View.VISIBLE);
//                    heightTextview.setVisibility(View.VISIBLE);
//                    heightSeekbar.setVisibility(View.VISIBLE);
//                    // Tear down myTracker
//                    myTracker.clear();
                    // Modify tracking flag
                    detect = -1;
                }
            }
        });
        // Setup Button for Circle Detection
        circButton = (Button) findViewById(R.id.circButton);
        circButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 1;
//                startActivity(new Intent(MainActivity.this));
            }
        });

        // Setup Button for Rectangle Detection
        rectButton = (Button) findViewById(R.id.rectButton);
        rectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 2;
//                startActivity(new Intent(MainActivity.this));
            }
        });

//        // Setup Button for Polygon Detection
//        polyButton = (Button) findViewById(R.id.polyButton);
//        polyButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                appFlag = 3;
////                startActivity(new Intent(MainActivity.this));
//            }
//        });

        // Setup Button for Line Detection
        lineButton = (Button) findViewById(R.id.lineButton);
        lineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appFlag = 4;
//                startActivity(new Intent(MainActivity.this));
            }
        });

        // Setup OpenCV Camera View
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_camera_preview);
        // Use main camera with 0 or front camera with 1
        mOpenCvCameraView.setCameraIndex(0);
        // Force camera resolution, ignored since OpenCV automatically select best ones
        // mOpenCvCameraView.setMaxFrameSize(1280, 720);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    // Helper Function to map single integer to color scalar
    // https://www.particleincell.com/2014/colormap/
    public void setColor(int value) {
        double a = (1 - (double) value / 100) / 0.2;
        int X = (int) Math.floor(a);
        int Y = (int) Math.floor(255 * (a - X));
        double newColor[] = {0, 0, 0};
        switch (X) {
            case 0:
                // r=255;g=Y;b=0;
                newColor[0] = 255;
                newColor[1] = Y;
                break;
            case 1:
                // r=255-Y;g=255;b=0
                newColor[0] = 255 - Y;
                newColor[1] = 255;
                break;
            case 2:
                // r=0;g=255;b=Y
                newColor[1] = 255;
                newColor[2] = Y;
                break;
            case 3:
                // r=0;g=255-Y;b=255
                newColor[1] = 255 - Y;
                newColor[2] = 255;
                break;
            case 4:
                // r=Y;g=0;b=255
                newColor[0] = Y;
                newColor[2] = 255;
                break;
            case 5:
                // r=255;g=0;b=255
                newColor[0] = 255;
                newColor[2] = 255;
                break;
        }
//        myROIColor.set(newColor);
        return;
    }

    // OpenCV Camera Functionality Code
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        myWidth = width;
        myHeight = height;
//        myROI = new Rect2d(myWidth / 2 - myROIWidth / 2,
//                            myHeight / 2 - myROIHeight / 2,
//                            myROIWidth,
//                            myROIHeight);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // Grab camera frame in rgba and grayscale format
        mRgba = inputFrame.rgba();
        // Grab camera frame in gray format
        mGray = inputFrame.gray();
        // Gaussian blur to enhance processing
        Imgproc.GaussianBlur(mGray, mGray, new Size(5, 5), 0, 0);

        // Detect Lines
        if (appFlag == 4) {
            // Apply Canny edge detection
            Imgproc.Canny(mGray, mGray, 50, 150);

            Mat lines = new Mat();
            //Apply Hough Transform
            Imgproc.HoughLinesP(mGray, lines, 1, Math.PI / 180, 30, 100, 10);
//            Mat houghLines = new Mat();
//            houghLines.create(mGray.rows(), mGray.cols(), CvType.CV_8UC1);
            //Drawing lines on the image
            for (int i = 0; i < lines.rows(); i++) {
                double[] points = lines.get(i, 0);
                double x1, y1, x2, y2;
                x1 = points[0];
                y1 = points[1];
                x2 = points[2];
                y2 = points[3];

                Point point1 = new Point(x1, y1);
                Point point2 = new Point(x2, y2);

                //Drawing lines on the colored frame
                Imgproc.line(mRgba, point1, point2, new Scalar(255, 255, 255), 2);
            }
            lines.release();
        }

        // Circle Detection
        if (appFlag == 1) {
            Mat circles = new Mat();
            // Apply circle transform
            Imgproc.HoughCircles(mGray, circles, Imgproc.CV_HOUGH_GRADIENT, 2, 1000,
                    100, 100, 50, 1000);
            // Log.i(TAG, String.valueOf("size: " + circles.cols()) + ", " + String.valueOf(circles.rows()));
            // Draw circles
            if (circles.cols() > 0) {
                for (int i = 0; i < Math.min(circles.cols(), 5); i++) {
                    double circleVec[] = circles.get(0, i);

                    if (circleVec == null) {
                        break;
                    }

                    Point center = new Point((int) circleVec[0], (int) circleVec[1]);
                    int radius = (int) circleVec[2];

                    Imgproc.circle(mRgba, center, 3, new Scalar(255, 255, 255), 5);
                    Imgproc.circle(mRgba, center, radius, new Scalar(255, 255, 255), 2);
                }
            }

            circles.release();
            // Returned frame will be displayed on the screen
        }
        // Rectangle Detection
        if (appFlag == 2) {
            // Apply Canny edge detection
            Imgproc.Canny(mGray, mGray, 50, 150);
            // Apply thresholding
            Imgproc.adaptiveThreshold(mGray, mGray, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY, 7, 5);
            // Create a list of contours
            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            // Find contours
            Imgproc.findContours(mGray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            // Loop over all contour and Draw rectangles
            Mat rectangles = new Mat();
            for (int i = 0; i < contours.size(); i++) {
                if (Imgproc.contourArea(contours.get(i)) > 1000) {
                    Rect rect = Imgproc.boundingRect(contours.get(i));
                    if (rect.height > 50) {
                        Imgproc.rectangle(mRgba, new Point(rect.x, rect.y), new Point(rect.x + rect.width,
                                rect.y + rect.height), new Scalar(255, 255, 255));
                    }
                }
            }
            rectangles.release();
            contours.clear();
        }
        // GHT to detect arbitrary shapes
        if (appFlag == 3){
//            int width = (int)myROI.width;
//           int height = (int)myROI.height;
//            Mat image_roi = mGray.submat( (int)myROI.x, (int)(myROI.x+width), (int)myROI.y, (int)(myROI.y+height));
//            byte pixels[] = new byte[(int) image_roi.total()];
//            image_roi.get(0, 0, pixels);
//            Mat grad = angle(image_roi);
//            byte gradient[] = new byte[(int) grad.total()];
//            //build R table
//            for (int i=0; i<height;i++){
//                for (int j=0; i<width;j++){
//                    if(pixels[i*width+j] != 0){
//                        int r = (int)(Math.sqrt(Math.pow((i-width/2),2)+Math.pow(j-height/2,2)));
//                        if(r!=0) {
//                            int idx = (int)(gradient[i*height+j]*180/Math.PI);
////                                rtable.add((int)Math.abs((int) gradient[i*(int)myROI.width+j] * 180 / Math.PI), (int)r);
//                            rtable.add(idx,r);
//                        }
//                    }
//                }
//            }
//            image_roi.release();
//            grad.release();
//            wmax = width*100;
//            wmin = (int)width/100;
////                int[][][][] accum = new int[rows][cols][r_size][phi_size];
//            int rows = mGray.height();
//            int cols = mGray.width();
//            int r_size = wmax - wmin;
//            int phi_size = phimax - phimin;
//            int r0 = -phimin;
//            //scaling
//            for (int r = wmin;r<wmax;r++) {
//                rot_rtable.add(r_size);
//                for (int i = 0; i < r_size; i++) {
//                    sca_rtable.add(Math.round(rtable.get(i * 2) * r));
//                    sca_rtable.add(Math.round(rtable.get(i * 2 + 1) * r));
//                }
//            }
//            //rotation
//            int i = 0;
//            int x = 0;
//            int y = 0;
//            for (; i<=phi_size; i++) {
//                for (int j = 0; j < r_size; j++) {
//                    x = -rot_rtable.get(j * 2);
//                    y = -rot_rtable.get(j * 2 + 1);
//
//                    double r = Math.sqrt(x * x + y * y);
//                    double phi = Math.atan2(y, x);
//                    if (phi > 0)
//                        phi = phi + phimin;
//                    else
//                        phi = 2 * Math.PI + phi + i;
//                    rtable.add(-Math.round((float) (r * Math.cos(phi))));
//                    rtable.add(-Math.round((float) (r * Math.sin(phi))));
//
//                    if (j % r_size == 0) {
//                        // store parameters
//                        sca_rtable.add(sca_rtable.get((int) (j / r_size) * 2));    // scale
//                        sca_rtable.add(i + phimin);
//                    }
//                }
//            }
//            int[][] accum = new int[rows][cols];
////                for (int r = wmin; r < wmax; r++) {
////                    for (int p = phimin; p < phimax; p++) {
//                    for (i = 0; i < rows; i++) {
//                        for (int j = 0; j < cols; j++) {
//                            if (pixels[i * cols + j] != 0) {
//                                int phi = gradient[i * height + j];
////                                    for (int i = 0; i < rtable.size(); i++) {
//                                    double dist = rtable.get((int)phi);
//                                    int x_center = (int) (i + dist*Math.cos(phi));
//                                    int y_center = (int) (j + dist*Math.sin(phi));
//                                    if ((x_center < rows) && (y_center < cols) && (x_center > -1) && (y_center > -1)) {
//                                        accum[x_center][y_center]++;
//                                       }
//                                }
//
//                }
//            }
//            int maxval = 0;
////                for (int r = wmin; r < wmax; r++) {
////                    for (int p = phimin; p < phimax; p++) {
//                    for (i = 0; i < rows; i++) {
//                        for (int j = 0; j < cols; j++) {
//                            if (accum[i][j]>maxval){
//                                maxval = accum[i][j];
//                                x = i;
//                                y = j;
//                            }
//                        }
//                    }
//            // Apply Canny edge detection
//            Imgproc.Canny(mGray, mGray, 50, 150);
//            // Apply thresholding
//            Imgproc.adaptiveThreshold(mGray, mGray, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C,
//                    Imgproc.THRESH_BINARY, 7, 5);
//            for (i = 0; i < rows; i++) {
//                for (int j = 0; i < cols; j++) {
//                    if (pixels[i * cols + j] != 0) {
//                        int r = (int) (Math.sqrt(Math.pow((i - width / 2), 2) + Math.pow(j - height / 2, 2)));
//                        if (r != 0) {
//                            int idx = (int) (gradient[i * height + j] * 180 / Math.PI);
//                            rtable.add(idx, (int)r);
//                        }
//                    }
//                }
//            }
            // Create a list of contours
            List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            // Find contours
            Imgproc.findContours(mGray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            double max_area = 2000;
            double limit = myWidth*myHeight*0.8;
            double area = 0;
            int idx = 0;
            int x_center = 0, y_center = 0;
            for (int i = 0; i < contours.size(); i++) {
                area = Imgproc.contourArea(contours.get(i));
                if(area > max_area && area < limit) {
                    idx = i;
                    max_area = area;
                    Moments M = Imgproc.moments(contours.get(i));
                    x_center = (int)(M.get_m10() / M.get_m00());
                    y_center = (int)(M.get_m01() / M.get_m00());
                }
            }
            Imgproc.drawContours(mRgba,contours, idx, new Scalar(255, 255, 255), 2);
            Imgproc.circle(mRgba, new Point(x_center,y_center), 3, new Scalar(255, 255, 255), 5);
        }
        return mRgba;
    }
}