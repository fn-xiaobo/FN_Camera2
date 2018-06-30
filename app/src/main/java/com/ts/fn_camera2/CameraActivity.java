package com.ts.fn_camera2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ts.fn_camera2.view.FocusFrameView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * 拍照
 */
public class CameraActivity extends AppCompatActivity implements View.OnClickListener {


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private Handler handler1, handler2, mainHandler, mBackgroundHandler;

    ///为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static String TAG = "CameraActivity";
    private LinearLayout llTitle;
    private TextView tvLight;
    private TextView tv_focus;
    private TextView tv_iso;
    private TextView tv_scale;
    private TextView tv_ae;
    private TextView tv_time;
    private TextView tv_awb;
    private TextView tv_effect;
    private TextView tv_messagetoast;
    private TextView tv_scene;

    private SeekBar sb_focus;
    private SeekBar sb_iso;
    private SeekBar sb_scale;
    private SeekBar sb_ae;
    private SeekBar sb_time;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Button btnTakephoto;
    private Button btn_startrecord;
    private ImageView ivPhotes;


    private FocusFrameView focusFrameView;//自定义的矩形框

    private CameraManager cameraManager;//摄像头管理类，用于检测、打开系统摄像头，通过getCameraCharacteristics(cameraId)可以获取摄像头特征。
    private CameraDevice mCameraDevice;
    private ImageReader imageReaderJPEG;
    /**
     * camera数据属性
     */
    private CameraCharacteristics characteristics;//相机特性类，例如，是否支持自动调焦，是否支持zoom，是否支持闪光灯一系列特征。

    /**
     * 最小的焦距值
     */
    private Float minimumLens;

    /**
     * 传感器的信息ISO
     */
    private Range<Integer> integerRange;

    /**
     * 传感器的最大值
     */
    private Integer maxISO;
    /**
     * 传感器的最小值
     */
    private Integer minISO;

    private StreamConfigurationMap map;

    /**
     * 图片的地址
     */
    private ArrayList<String> picturesPath;

    /**
     * 摄像头
     */
    private String mCameraID;

    /**
     * 预览的build
     */
    private CaptureRequest.Builder previewRequestBuilder;

    /**
     * 拍照的build
     */
    private CaptureRequest.Builder takePictureRequestBuilder;

    /**
     * 用于创建预览、拍照的Session类。通过它的setRepeatingRequest()方法控制预览界面 , 通过它的capture()方法控制拍照动作或者录像动作。
     */
    private CameraCaptureSession mCameraCaptureSession;

    /**
     * 消失对焦框
     */
    private static final int REFRESHFOCUSVIEW = 0;
    private static final int LIGHTOPEN = 1;
    private static final int LIGHTCLOSE = 2;
    private static final int LIGHTAUTO = 3;
    private static final int CAPTURES = 4;
    private static final int TOASTGONE = 5;
    private static int LIGHTOPENORCLOSE = CaptureRequest.FLASH_MODE_OFF;

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_CAPTURE = 1;
    private static final int STATE_REPRE = 2;
    private int mState = STATE_PREVIEW;

    /**
     * 是否显示seekbar
     */
    private boolean isFoucs = false;
    private boolean isISO = false;
    private boolean isSacle = false;
    private boolean isAe = false;
    private boolean isTime = false;
    private int pictureNum = 1;
    boolean isSetBuilder = false;


    private Handler messageHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);


            if (msg.what == REFRESHFOCUSVIEW) {
                focusFrameView.setVisibility(View.GONE);
                //messageHandler.removeMessages(REFRESHFOCUSVIEW);
            } else if (msg.what == LIGHTCLOSE) {
                tvLight.setText("闪光灯关闭");
            } else if (msg.what == LIGHTOPEN) {
                tvLight.setText("闪光灯打开");
            } else if (msg.what == LIGHTAUTO) {
                tvLight.setText("闪光灯自动");
            } else if (msg.what == CAPTURES) {
                mState = STATE_CAPTURE;
                tv_messagetoast.setVisibility(View.VISIBLE);
            } else if (msg.what == TOASTGONE) {
                tv_messagetoast.setVisibility(View.GONE);
            }


        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        init();
    }

    private void init() {


        //在一个activity上面添加额外的content
        //自定义的矩形框
        focusFrameView = new FocusFrameView(CameraActivity.this, 540, 960, 200, 200, Color.BLUE);
        addContentView(focusFrameView, new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT));
        focusFrameView.setVisibility(View.GONE);

        //初始化的是实用中间值
        initSeekBarValue();

        initView();

    }


    //---------------------------- seekbar的值 start -------------------------
    //初始化的话是实用中间值
    private float valueAF;
    private int valueAE;
    private long valueAETime;
    private int valueISO;

    /**
     * 初始化seekbar上的一些参数
     */
    private void initSeekBarValue() {
        valueAF = 5.0f;
        valueAETime = (214735991 - 13231) / 2;
        valueISO = (10000 - 100) / 2;
        valueAE = 0;
    }
    //---------------------------- seekbar的值 end -------------------------

    //---------------------------- 初始话控件 start -------------------------
    private void initView() {
        llTitle = (LinearLayout) findViewById(R.id.ll_title);
        tvLight = (TextView) findViewById(R.id.tv_light);
        btnTakephoto = (Button) findViewById(R.id.btn_takephoto);
        ivPhotes = (ImageView) findViewById(R.id.iv_photes);
        sb_focus = findViewById(R.id.sb_focus);
        sb_iso = findViewById(R.id.sb_iso);
        sb_scale = findViewById(R.id.sb_scale);
        sb_ae = findViewById(R.id.sb_ae);
        sb_time = findViewById(R.id.sb_time);
        tv_focus = findViewById(R.id.tv_focus);
        tv_iso = findViewById(R.id.tv_iso);
        tv_scale = findViewById(R.id.tv_scale);
        tv_ae = findViewById(R.id.tv_ae);
        tv_time = findViewById(R.id.tv_time);
        tv_awb = findViewById(R.id.tv_awb);
        tv_effect = findViewById(R.id.tv_effect);
        tv_messagetoast = findViewById(R.id.tv_messagetoast);
        tv_scene = findViewById(R.id.tv_scene);

        btn_startrecord = findViewById(R.id.btn_startrecord);

        picturesPath = new ArrayList<>();
        //surfaceview的初始化
        surfaceView = (SurfaceView) findViewById(R.id.sv_precamera);
        surfaceView.setOnTouchListener(new MyOnTouchListener());
        surfaceHolder = surfaceView.getHolder();
        //保持常亮
        surfaceView.setKeepScreenOn(true);
        //surfaceview的回调
        surfaceHolder.addCallback(new SurfaceHolderCallback());


        setListeners();
    }

    private void setListeners() {
        //设置监听
        btnTakephoto.setOnClickListener(this);
        btn_startrecord.setOnClickListener(this);
        btnTakephoto.setOnLongClickListener(new MyOnLongClickListener());
        btnTakephoto.setOnTouchListener(OnTouchListener);
        ivPhotes.setOnClickListener(this);
        tvLight.setOnClickListener(this);
        tv_focus.setOnClickListener(this);
        tv_iso.setOnClickListener(this);
        tv_scale.setOnClickListener(this);
        tv_ae.setOnClickListener(this);
        tv_time.setOnClickListener(this);
        tv_awb.setOnClickListener(this);
        tv_effect.setOnClickListener(this);
        tv_scene.setOnClickListener(this);

        MyOnSeekBarChangeListener myOnSeekBarChangeListener = new MyOnSeekBarChangeListener();
        sb_scale.setOnSeekBarChangeListener(myOnSeekBarChangeListener);
        sb_focus.setVisibility(View.INVISIBLE);
        sb_iso.setVisibility(View.INVISIBLE);
        sb_scale.setVisibility(View.INVISIBLE);
        sb_ae.setVisibility(View.INVISIBLE);
        sb_time.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(View view) {
        sb_focus.setVisibility(View.INVISIBLE);
        sb_iso.setVisibility(View.INVISIBLE);
        sb_scale.setVisibility(View.INVISIBLE);
        sb_ae.setVisibility(View.INVISIBLE);
        sb_time.setVisibility(View.INVISIBLE);

        switch (view.getId()) {
            case R.id.btn_takephoto:
                //拍照
                takePicture();
                isSetBuilder = false;
                break;
            case R.id.iv_photes:
                //进入照片预览
//                showPictures();
                break;
            case R.id.tv_light:
                //闪光灯开启或关闭
                //开启或关闭闪光灯
                Toast.makeText(CameraActivity.this, "闪光灯", Toast.LENGTH_SHORT).show();

                if (LIGHTOPENORCLOSE == CaptureRequest.FLASH_MODE_OFF) {

                    LIGHTOPENORCLOSE = CaptureRequest.FLASH_MODE_TORCH;
                    messageHandler.sendEmptyMessage(LIGHTOPEN);

                } else if (LIGHTOPENORCLOSE == CaptureRequest.FLASH_MODE_TORCH) {

                    LIGHTOPENORCLOSE = CaptureRequest.FLASH_MODE_SINGLE;
                    messageHandler.sendEmptyMessage(LIGHTAUTO);

                } else if (LIGHTOPENORCLOSE == CaptureRequest.FLASH_MODE_SINGLE) {

                    LIGHTOPENORCLOSE = CaptureRequest.FLASH_MODE_OFF;
                    messageHandler.sendEmptyMessage(LIGHTCLOSE);
                }
                break;

            case R.id.tv_focus:
                if (isFoucs) {
                    sb_focus.setVisibility(View.INVISIBLE);

                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);


                    isFoucs = false;
                } else {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                    sb_focus.setVisibility(View.VISIBLE);
                    isFoucs = true;
                }
                break;
            case R.id.tv_iso:
                if (isISO) {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    sb_iso.setVisibility(View.INVISIBLE);
                    isISO = false;
                } else {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    sb_iso.setVisibility(View.VISIBLE);
                    isISO = true;
                }
                break;
            case R.id.tv_scale:
                if (isSacle) {
                    sb_scale.setVisibility(View.INVISIBLE);
                    isSacle = false;
                } else {
                    sb_scale.setVisibility(View.VISIBLE);
                    isSacle = true;
                }
                break;
            case R.id.tv_ae:
                if (isAe) {

                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    sb_ae.setVisibility(View.INVISIBLE);
                    isAe = false;
                } else {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    sb_ae.setVisibility(View.VISIBLE);
                    isAe = true;
                }
                break;

            case R.id.tv_time:
                if (isTime) {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    sb_time.setVisibility(View.INVISIBLE);
                    isTime = false;
                } else {
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    sb_time.setVisibility(View.VISIBLE);
                    isTime = true;
                }
                break;

        }
        updatePreview1();
    }

    //---------------------------- 初始话控件 end -------------------------

    private View.OnTouchListener OnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    messageHandler.sendEmptyMessageDelayed(CAPTURES, 300);
                    break;
                case MotionEvent.ACTION_MOVE:

                    break;
                case MotionEvent.ACTION_UP:
                    mState = STATE_PREVIEW;

                    messageHandler.sendEmptyMessage(TOASTGONE);
                    pictureNum = 1;
                    messageHandler.removeMessages(CAPTURES);
                    break;
            }
            return false;
        }
    };

    /**
     * surfaceview 的点击监听，点击对焦
     */
    class MyOnTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            // 先取相对于view上面的坐标
            //double x = motionEvent.getX(), y = motionEvent.getY(), tmp;
            //Log.d(TAG, "onTouch: x" + x + " y : " + y);

            //重绘对焦框
            refreshFocusView((int) motionEvent.getX(), (int) motionEvent.getY());
            final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            //TODO: here I just flip x,y, but this needs to correspond with the sensor orientation (via SENSOR_ORIENTATION)
            //得到触摸点对应的图像点
            final int y = (int) ((motionEvent.getX() / (float) view.getWidth()) * (float) sensorArraySize.height());
            final int x = (int) ((motionEvent.getY() / (float) view.getHeight()) * (float) sensorArraySize.width());
            final int halfTouchWidth = 300; //(int)motionEvent.getTouchMajor(); //TODO: this doesn't represent actual touch size in pixel. Values range in [3, 10]...
            final int halfTouchHeight = 300; //(int)motionEvent.getTouchMinor();
            Log.d(TAG, "onTouch: x" + x + " y : " + y);
            final MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth, 0),
                    Math.max(y - halfTouchHeight, 0),
                    halfTouchWidth * 2,
                    halfTouchHeight * 2,
                    MeteringRectangle.METERING_WEIGHT_MAX - 1);
            //closePreviewSession();
            /**
             * 更新预览界面
             */
            rePreview(focusAreaTouch);
            //takepreCameraDisplay();
            return false;
        }
    }

    /**
     * 重绘focusview
     */
    private void refreshFocusView(int x, int y) {
        focusFrameView.setmCenterX(x);
        focusFrameView.setmCenterY(y);
        focusFrameView.invalidate();
        //focusFrameView.refreshDrawableState();
        focusFrameView.setVisibility(View.VISIBLE);
        messageHandler.removeMessages(REFRESHFOCUSVIEW);
        messageHandler.sendEmptyMessageDelayed(REFRESHFOCUSVIEW, 2000);
    }

    private void rePreview(final MeteringRectangle focusAreaTouch) {

        // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

        updatePreview1();
    }


    /**
     * 01--
     * SurfaceHolder的回调
     * 当surfaceview准备好的时候初始化相机
     */
    class SurfaceHolderCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            Log.d(TAG, "surfaceCreated: ");

            requestPermissions();
            //初始化相机
            //initCamera();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }
    }

    private int num = 0;

    /**
     * 初始化相机
     */
    @SuppressLint("MissingPermission")
    private void initCamera() {

        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        handler1 = new Handler(handlerThread.getLooper());

        HandlerThread handlerThread1 = new HandlerThread("Captures");
        handlerThread1.start();
        handler2 = new Handler(handlerThread1.getLooper());

        mainHandler = new Handler(getMainLooper());//主线程中的UI

        HandlerThread mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;//后摄像头

        //获取摄像头管理
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //打开摄像头
            Log.d("MyCamera", "打开摄像头成功");
            cameraManager.openCamera(mCameraID, stateCallback, mainHandler);
            characteristics = cameraManager.getCameraCharacteristics(mCameraID);//通过getCameraCharacteristics(cameraId)可以获取摄像头特征。
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.d("MyCamera", "打开摄像头失败");
        }

        //获取最小的焦距值
        minimumLens = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);//镜头信息最小焦距

        //传感器的信息
        integerRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);//传感器信息敏感范围 传感器的信息ISO

        Integer integer1 = characteristics.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY);//传感器最大模拟灵敏度

        Log.d(TAG, "initCamera: SENSOR_MAX_ANALOG_SENSITIVITY " + integer1);
        if (integerRange != null) {
            //获取最大最小值
            maxISO = integerRange.getUpper();
            minISO = integerRange.getLower();
            Log.d(TAG, "initCamera: minimumLens :" + minimumLens + " maxISO: " + maxISO + " minISO: " + minISO + " range: " + integerRange.toString());
        }


        Log.d(TAG, "initCamera: " + minimumLens + " ca ");


        Integer integer = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);//获取摄像头支持某些特性的程度。
        Toast.makeText(CameraActivity.this, "" + integer.toString(), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "initCamera: " + integer.toString());

        map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);//标量流配置图
        int[] outputFormats = map.getOutputFormats();//获取所有的输出格式
        for (int i = 0; i < outputFormats.length; i++) {
            Log.d(TAG, "outputFormats: " + outputFormats[i]);
        }

        //比较出最大的尺寸也就是宽高比
        Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

        //设置最大的图像尺寸 ,这里就是单次拍照 最大数量限制
        imageReaderJPEG = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 3);

        //这个回调,会监听拍照指令,回调中可以做保存图片操作
        imageReaderJPEG.setOnImageAvailableListener(new MyJPEGOnImageAvailableListener(), mainHandler);

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }



    class MyJPEGOnImageAvailableListener implements ImageReader.OnImageAvailableListener {

        @Override
        public void onImageAvailable(ImageReader imageReader) {
            //Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            num++;
            if (num > 100) {
                Toast.makeText(CameraActivity.this, "照片够多了", Toast.LENGTH_SHORT).show();
                return;
            }
            //Log.d(TAG, "onImageAvailable: saveimage " + num);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            String filepath = "/sdcard/Camera2/" + timeStamp + "_" + num + ".jpg";
            Log.d(TAG, "onImageAvailable: saveimage in " + filepath + num);

            //将任务放入子线程中进行
            mBackgroundHandler.post(new JPEGImageSaver(imageReader.acquireNextImage(), filepath));

        }
    }


    /**
     * 保存bitmap
     *
     * @param bitNamePath
     * @param mBitmap
     */
    public void saveBitmap(String bitNamePath, Bitmap mBitmap) {
        File f = new File(bitNamePath);
        try {
            f.createNewFile();
        } catch (IOException e) {
            Log.d(TAG, "saveBitmap: 保存图片是出错");
        }
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存图片的线程
     */
    private class JPEGImageSaver implements Runnable {
        /**
         * The JPEG image
         */
        private final Image image;
        private final String filepath;

        /**
         * The file we save the image into.
         */
        JPEGImageSaver(Image image, String filepath) {
            this.image = image;
            this.filepath = filepath;
        }

        @Override
        public void run() {// 定义矩阵对象
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);//由缓冲区存入字节数组
            image.close();
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ivPhotes.setImageBitmap(bitmap);
                }
            });

            picturesPath.add(filepath);
            File mFile = new File(filepath);
            if (!mFile.getParentFile().exists()) {
                //父目录不存在 创建父目录
                Log.d(TAG, "creating parent directory...");
                if (!mFile.getParentFile().mkdirs()) {
                    Log.e(TAG, "created parent directory failed.");
                    //return FLAG_FAILED;
                }
            }
            try {
                mFile.createNewFile();
            } catch (IOException e) {
                Log.d(TAG, "saveBitmap: 保存图片是出错");
            }
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


    /**
     * 摄像头创建监听
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {//打开摄像头

            //Toast.makeText(CameraActivity.this, "打开摄像头成功", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "onOpened: 打开摄像头成功");
            //当打开摄像头成功后开始预览
            mCameraDevice = camera;
            takepreCameraDisplay();


        }

        @Override
        public void onDisconnected(CameraDevice camera) {//关闭摄像头
            //Toast.makeText(CameraActivity.this, "关闭摄像头成功", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onError(CameraDevice camera, int error) {//发生错误
            //Toast.makeText(CameraActivity.this, "摄像头开启失败", Toast.LENGTH_SHORT).show();
        }
    };


    /**
     * 开始预览
     */
    private void takepreCameraDisplay() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder.addTarget(surfaceHolder.getSurface());
            //初始化参数
            initPreviewBuilder();
            //3A--->auto
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);//控制模式自动
            //3A
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);//控制AF模式连续图像
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);//控制AE模式
            previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);//自动控制AWB模式
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice.createCaptureSession(Arrays.asList(surfaceHolder.getSurface(), imageReaderJPEG.getSurface()), new CameraCaptureSession.StateCallback() // ③
            {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) {
                        //Toast.makeText(MainActivity.this, "mCameraDevice is null", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "onConfigured: ");
                        return;
                    }
                    // 当摄像头已经准备好时，开始显示预览
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        // 显示预览
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, myCaptureCallback, handler1);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    //Toast.makeText(CameraActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "onConfigureFailed: 配置失败");
                }
            }, handler1);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 初始化预览的builder，这样做是为了一来就调iso的时候ae不为最低
     */
    private void initPreviewBuilder() {
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);//控制方式;
        previewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, valueAF);//透镜焦距
        previewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, valueAETime);//传感器曝光时间
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, valueAE);//反声发射曝光补偿
        previewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, valueISO);//传感器灵敏度
    }


    private CameraCaptureSession.CaptureCallback myCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            //Log.d(TAG, "onCaptureStarted: ");
        }

        private void process(CaptureResult result) {

            Log.e(TAG, "process: 我想看看是不是预览的时候这里一直在走 ... ?");

            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_CAPTURE:
                    if (pictureNum > 30) {
                        mState = STATE_PREVIEW;
                        pictureNum = 1;
                        isSetBuilder = false;
                        messageHandler.sendEmptyMessage(TOASTGONE);
                        return;
                    }
                    //Log.d(TAG, "process:pictureNum " + pictureNum);
                    try {
                        if (!isSetBuilder) {
                            initTakePictureBuilder();
                            Log.d(TAG, "process: builder init");
                            isSetBuilder = true;
                        }
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_messagetoast.setText(pictureNum + "");
                            }
                        });
                        pictureNum++;
                        mCameraCaptureSession.capture(takePictureRequestBuilder.build(), null, handler2);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }


                    break;
                case STATE_REPRE:
                    updatePreview1();
                    mState = STATE_PREVIEW;
                    break;
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,@NonNull CaptureResult partialResult) {
            //Log.d(TAG, "onCaptureProgressed: ");
            //process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,@NonNull TotalCaptureResult result) {
            //Log.d(TAG, "onCaptureCompleted: ");
            mCameraCaptureSession = session;
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            mCameraCaptureSession = session;
            //Log.d(TAG, "onCaptureFailed: ");
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
            //Log.d(TAG, "onCaptureSequenceAborted: ");
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
            //Log.d(TAG, "onCaptureBufferLost: ");
        }
    };

    private void initTakePictureBuilder() throws CameraAccessException {

        //创建一个适用于零快门延迟的请求。在不影响预览帧率的情况下最大化图像质量。
        takePictureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

        // 将imageReader的surface作为CaptureRequest.Builder的目标
        takePictureRequestBuilder.addTarget(surfaceHolder.getSurface());
        // 自动对焦
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 自动曝光
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        // 获取手机方向
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        // 根据设备方向计算设置照片的方向
        takePictureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

    }

    /**
     * 更新预览
     */
    private void updatePreview1() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            //3A
            mCameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), myCaptureCallback, handler1);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("updatePreview", "ExceptionExceptionException");
        }
    }

    /**
     * 长按连拍
     */
    class MyOnLongClickListener implements View.OnLongClickListener {

        @Override
        public boolean onLongClick(View view) {
            //设置最大张数限制
            //连拍
            takePictures();
            return true;
        }
    }

    /**
     * 拍照
     */
    private void takePicture() {
        try {


            takePictureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            takePictureRequestBuilder.addTarget(imageReaderJPEG.getSurface());
            takePictureRequestBuilder.addTarget(surfaceHolder.getSurface());
            //takePictureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            //设置连续帧
            Range<Integer> fps[] = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            takePictureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps[fps.length - 1]);//设置每秒30帧
            //得到方向
            takePictureRequestBuilder.set(CaptureRequest.FLASH_MODE, LIGHTOPENORCLOSE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            takePictureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            previewBuilder2CaptureBuilder();
            Log.d(TAG, "takePicture: " + rotation);
            //拍照
            CaptureRequest mCaptureRequest = takePictureRequestBuilder.build();
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.capture(mCaptureRequest, myCaptureCallback, handler1);
            mState = STATE_REPRE;
            // mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, handler1);
            //mCameraCaptureSession.setRepeatingBurst(list,null,handler1);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 连拍
     */
    private void takePictures() {

        try {

            takePictureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);//拍照

            // 将imageReader的surface作为CaptureRequest.Builder的目标
            takePictureRequestBuilder.addTarget(surfaceHolder.getSurface());
            // 自动对焦
            takePictureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            takePictureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            takePictureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            //拍照
            CaptureRequest mCaptureRequest = takePictureRequestBuilder.build();

            List<CaptureRequest> list = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                list.add(mCaptureRequest);
            }

            mCameraCaptureSession.captureBurst(list, null, handler1);

            //mCameraCaptureSession.capture(mCaptureRequest, null, handler1);
            // mCameraCaptureSession.setRepeatingRequest(mCaptureRequest, null, handler1);
            //mCameraCaptureSession.setRepeatingBurst(list,null,handler1);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * 将previewBuilder中修改的参数设置到captureBuilder中
     */
    private void previewBuilder2CaptureBuilder() {

        //HDR等等
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_MODE));
        //AWB
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE));
        //AE
//        if (mPreviewBuilder.get(CaptureRequest.CONTROL_AE_MODE) == CameraMetadata.CONTROL_AE_MODE_OFF) {
        //曝光时间
        takePictureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME));
//        } else if (mPreviewBuilder.get(CaptureRequest.CONTROL_AE_MODE) == CameraMetadata.CONTROL_AE_MODE_ON) {
        //曝光增益
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, previewRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION));
//        }
        //AF
//        if (mPreviewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == CameraMetadata.CONTROL_AF_MODE_OFF) {
        //手动聚焦的值
        takePictureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, previewRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE));
//        }
        //effects
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE));
        //ISO
        takePictureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, previewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
        //AF REGIONS
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, previewRequestBuilder.get(CaptureRequest.CONTROL_AF_REGIONS));
//        mCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        //AE REGIONS
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, previewRequestBuilder.get(CaptureRequest.CONTROL_AE_REGIONS));
//        mCaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        //SCENSE
        takePictureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_SCENE_MODE));
        //zoom
        takePictureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, previewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION));
    }

    /**
     * seekbar手动seekbar的监听
     */
    class MyOnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            Log.e(TAG, "onProgressChanged: int i --> : "+ i);
            tv_messagetoast.setVisibility(View.VISIBLE);

            if (previewRequestBuilder == null) {
                return;
            }

            switch (seekBar.getId()) {

                case R.id.sb_scale:

                    Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    int radio = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue() / 2;
                    int realRadio = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();
                    int centerX = rect.centerX();
                    int centerY = rect.centerY();
                    int minMidth = (rect.right - ((i * centerX) / 100 / radio) - 1) - ((i * centerX / radio) / 100 + 8);
                    int minHeight = (rect.bottom - ((i * centerY) / 100 / radio) - 1) - ((i * centerY / radio) / 100 + 16);
                    if (minMidth < rect.right / realRadio || minHeight < rect.bottom / realRadio) {
                        Log.i("sb_zoom", "sb_zoomsb_zoomsb_zoom");
                        return;
                    }

                    Rect newRect = new Rect((i * centerX / radio) / 100 + 40, (i * centerY / radio) / 100 + 40, rect.right - ((i * centerX) / 100 / radio) - 1, rect.bottom - ((i * centerY) / 100 / radio) - 1);
                    Log.i("sb_zoom", "left--->" + ((i * centerX / radio) / 100 + 8) + ",,,top--->" + ((i * centerY / radio) / 100 + 16) + ",,,right--->" + (rect.right - ((i * centerX) / 100 / radio) - 1) + ",,,bottom--->" + (rect.bottom - ((i * centerY) / 100 / radio) - 1));
                    previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
                    tv_messagetoast.setText("放大：" + i + "%");
                    break;

            }

            updatePreview1();
        }


        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            Log.d(TAG, "onStartTrackingTouch: seek按下");

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

            messageHandler.sendEmptyMessageDelayed(TOASTGONE,100);

        }
    }

    //--------------------------------------------- 权限申请 start -------------------------------------

    /**
     * 当有多个权限需要申请的时候
     * 这里以打电话和SD卡读写权限为例
     */
    private void requestPermissions() {

        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.CAMERA);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.RECORD_AUDIO);
        }

        if (!permissionList.isEmpty()) {  //申请的集合不为空时，表示有需要申请的权限
            ActivityCompat.requestPermissions(this, permissionList.toArray(new String[permissionList.size()]), 1);
        } else { //所有的权限都已经授权过了

            initCamera();
        }
    }

    /**
     * 权限申请返回结果
     *
     * @param requestCode  请求码
     * @param permissions  权限数组
     * @param grantResults 申请结果数组，里面都是int类型的数
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) { //安全写法，如果小于0，肯定会出错了
                    for (int i = 0; i < grantResults.length; i++) {

                        int grantResult = grantResults[i];
                        if (grantResult == PackageManager.PERMISSION_DENIED) { //这个是权限拒绝
                            String s = permissions[i];
                            Toast.makeText(this, s + "权限被拒绝了", Toast.LENGTH_SHORT).show();
                        } else { //授权成功了
                            //do Something
                            initCamera();
                        }
                    }
                }
                break;
            default:
                break;
        }
    }

    //--------------------------------------------- 权限申请 end -------------------------------------
}
