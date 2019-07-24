package com.ts.app3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.ts.app3.VerticalSeekBar.VerticalSeekBar;
import com.ts.app3.utils.FnProgressDialog;
import com.ts.app3.utils.ScreenUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * 录像
 */
public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks, View.OnClickListener, VerticalSeekBar.SlideChangeListener, MediaRecorder.OnInfoListener {

    private static final String TAG = "MainActivity";
    private static final int STOP_RECORDING = 1;
    private static final int DELETE_SUCCESS = 2;

    private boolean isOpenForFlash = false;//闪光灯是否开启
    private boolean mIsRecordingVideo;//是否正在录像

    private TextureView mTextureView;
    private Button camera_btn;
    private ImageView mIv_left;
    private ImageView mIv_right;
    private ImageView mIv_flash;
    private VerticalSeekBar mVerticalSeekBar;//垂直的进度条
    private SeekBar sb_scale;

    // ======================= 权限部分 start ========================
    private static final int REQUEST_VIDEO_PERMISSIONS = 0;
    private static final String[] VIDEO_PERMISSIONS = {

            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.RECORD_AUDIO
            , Manifest.permission.READ_PHONE_STATE, Manifest.permission.CAMERA, Manifest.permission.WAKE_LOCK
    };

    // ======================= 权限部分 end ========================

    //----------------------------------- 这部分是出自 google demo start -----------------------------------
    //给大家一个参考链接
    // https://blog.csdn.net/baicaizaiqingdao2009/article/details/68488843
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Handler mHandler, mUIHandler;// mHandler 用于向工作线程发送任务 , mUIHandler 用于主线程处理请求的
    private HandlerThread mCeamera3;//工作线程
    private CameraManager mManager;//获取摄像头管理
    private CameraCharacteristics mCameraCharacteristics;//相机特性类，例如，是否支持自动调焦，是否支持zoom，是否支持闪光灯一系列特征。
    private Rect maxZoomrect;////画面传感器的面积，单位是像素 这里应该是指显示区域
    private int maxRealRadio;//最大的数字缩放
    private Float minimumLens;//最小的焦距值
    private Rect picRect;
    private Size mPreViewSize;//比较出最大的尺寸也就是宽高比 //设置输入的预览尺寸
    private ImageReader mImageReader;//设置最大的图像尺寸 ,这里就是单次拍照 最大数量限制

    private Integer mSensorOrientation;

    private CameraDevice mCameraDevice;//得到这个设备后,即可开始初始化预览

    private CaptureRequest.Builder mPreViewBuidler;//预览请求
    private CameraCaptureSession mCameraSession;//拍照的Session类

    private Surface surface;//获取Surface显示预览数据

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private MediaRecorder mMediaRecorder;//录像使用的类
    private Chronometer mRecordTime;//计时器
    private long mRecordCurrentTime = 0;  //录制时间间隔
    private String mAbsolutePath;//录像保存地址

    //----------------------------------- 这部分是出自 google demo end -----------------------------------


    //------------------------------ 屏幕适配 start ------------------------------

    /**
     * 使得在“setContentView()"之前生效，所以配置在此方法中。
     *
     * @param newBase
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        ScreenUtil.resetDensity(this);
    }

    /**
     * 在某种情况下需要Activity的视图初始完毕Application中DisplayMetrics相关参数才能起效果，例如toast.
     *
     * @param
     */
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ScreenUtil.resetDensity(this.getApplicationContext());

    }

    //------------------------------ 屏幕适配 end ------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActivityConfig();
        setContentView(R.layout.activity_main);

        init();
    }

    /**
     * 01--
     */
    private void init() {
        initView();
    }

    //----------------------------------- 第一部分 view 的基本描述 start -----------------------------------

    /**
     * 02--
     */
    private void initView() {

        mTextureView = (TextureView) findViewById(R.id.tv);
        camera_btn = (Button) findViewById(R.id.camera_btn);

        mIv_left = (ImageView) findViewById(R.id.iv_left);
        mIv_right = (ImageView) findViewById(R.id.iv_right);
        mIv_flash = (ImageView) findViewById(R.id.iv_flash);
        sb_scale = findViewById(R.id.sb_scale);

        mRecordTime = (Chronometer) findViewById(R.id.record_time);

        mVerticalSeekBar = (VerticalSeekBar) findViewById(R.id.verticalSeekBar);
        mVerticalSeekBar.setThumb(R.mipmap.color_seekbar_thum);
        mVerticalSeekBar.setThumbSizePx(50, 50);
        mVerticalSeekBar.setProgress(0);
        mVerticalSeekBar.setMaxProgress(100);

        setListener();
    }

    //设置点击事件
    private void setListener() {

        //设置TextureView监听
        mTextureView.setSurfaceTextureListener(surfaceTextureListener);

        camera_btn.setOnClickListener(this);
        mIv_left.setOnClickListener(this);
        mIv_right.setOnClickListener(this);
        mIv_flash.setOnClickListener(this);
//
        mVerticalSeekBar.setOnSlideChangeListener(this);
        sb_scale.setOnSeekBarChangeListener(myOnSeekBarChangeListener);
    }

    //----------------------------------- 第一部分 view 的基本描述 end -----------------------------------

    //----------------------------------- 第二部分 view 的监听 及camera2 start 的初始化  -----------------------------------
    /**
     * 设置 TextureView.SurfaceTextureListener 监听
     * 这一步主要是适配摄像头和Surface 的尺寸
     */
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

            //工作线程,可以配合 Handler 使用,实现对线程性能的优化 https://blog.csdn.net/isee361820238/article/details/52589731
            mCeamera3 = new HandlerThread("Ceamera3");
            mCeamera3.start();

            mHandler = new Handler(mCeamera3.getLooper());//通过获取HandlerThread的looper对象传递给Handler对象，然后在handleMessage()方法中执行异步任务

            mUIHandler = new Handler(new InnerCallBack());

            mManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            String cameraid = CameraCharacteristics.LENS_FACING_FRONT + "";//获取前置摄像头的属性ID

            // 获取指定摄像头的特性 //摄像头id（通常0代表后置摄像头，1代表前置摄像头）
            try {

                mCameraCharacteristics = mManager.getCameraCharacteristics(cameraid);//拿到 mCameraCharacteristics 对象 描述特定摄像头所支持的各种特性。

                maxZoomrect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);//画面传感器的面积，单位是像素 这里应该是指显示区域

                maxRealRadio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue(); //最大的数字缩放

                //获取最小的焦距值
                minimumLens = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);//镜头信息最小焦距

                picRect = new Rect(maxZoomrect);

                // 获取摄像头支持的配置属性
                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // 获取摄像头支持的最大尺寸
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizeByArea());

                //比较出最大的尺寸也就是宽高比
                mPreViewSize = map.getOutputSizes(SurfaceTexture.class)[0];//设置输入的预览尺寸

                // 获取最佳的预览尺寸
                choosePreSize(i, i1, map, largest);

                // 创建一个ImageReader对象，用于获取摄像头的图像数据
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 10);

                //设置获取图片的监听 拍照后结果会在 onImageAvaiableListener 呈现
                mImageReader.setOnImageAvailableListener(onImageAvaiableListener, mHandler);

                if (EasyPermissions.hasPermissions(MainActivity.this, VIDEO_PERMISSIONS)) {//检查是否获取该权限
                    Log.i(TAG, "requestPhonePermission: 已获得权限");
                    //获取权限，打开照相机

                    //开启摄像头,监听开启状态
                    mManager.openCamera(cameraid, cameraOpenCallBack, mHandler);

                } else {
                    //第二个参数是被拒绝后再次申请该权限的解释
                    //第三个参数是请求码
                    //第四个参数是要申请的权限
                    EasyPermissions.requestPermissions(MainActivity.this, "请允许必要的权限!", REQUEST_VIDEO_PERMISSIONS, VIDEO_PERMISSIONS);
                }


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {

            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private void choosePreSize(int i, int i1, StreamConfigurationMap map, Size largest) {

        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();

        //noinspection ConstantConditions 获取摄像头的方向
        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        boolean swappedDimensions = false;

        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }
        android.graphics.Point displaySize = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);//这在自定义控件中是获取屏幕宽高的方法
        int rotatedPreviewWidth = i;
        int rotatedPreviewHeight = i1;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        //此时 swappedDimensions =true;
        if (swappedDimensions) {

            rotatedPreviewWidth = i1;
            rotatedPreviewHeight = i;
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        //经过对比测量,选出最佳的预览尺寸
        mPreViewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

    }

    //经过对比测量,选出最佳的预览尺寸
    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // 获取摄像头支持的配置属性
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface  // 获取摄像头支持的最大尺寸
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for (Size option : choices) {

            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&option.getHeight() == option.getWidth() * 3 / 4) {

                if (option.getWidth() >= textureViewWidth &&option.getHeight() >= textureViewHeight) {

                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {

            return Collections.min(bigEnough, new CompareSizeByArea());

        } else if (notBigEnough.size() > 0) {

            return Collections.max(notBigEnough, new CompareSizeByArea());

        } else {

            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    /**
     * // 为Size定义一个比较器Comparator
     */
    public class CompareSizeByArea implements java.util.Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {

            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());

        }
    }

    /**
     * 这一步,就是直接接收拍照结果,IO 操作 ,当然要放在 子线程进行,当然我们这里使用 handlerThread 来优化性能
     */
    private ImageReader.OnImageAvailableListener onImageAvaiableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

            Log.e(TAG, "onImageAvailable() called with: imageReader = [" + imageReader + "]");

        }
    };

    /**
     * 这一步是创建预览请求,同时将预览画面 surface 添加到 mPreViewBuidler
     */
    private CameraDevice.StateCallback cameraOpenCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {

            Log.d(TAG, "相机已经打开");
            mCameraDevice = cameraDevice;
            try {

                //创建预览请求
                mPreViewBuidler = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                SurfaceTexture texture = mTextureView.getSurfaceTexture();

                //设置TextureView的缓冲区大小
                texture.setDefaultBufferSize(mPreViewSize.getWidth(), mPreViewSize.getHeight());

                //获取Surface显示预览数据
                surface = new Surface(texture);

                //设置Surface作为预览数据的显示界面
                mPreViewBuidler.addTarget(surface);


                //通知 硬件 创建相机捕获会话
                /**
                 * Arrays.asList(surface, mImageReader.getSurface())  捕获数据的输出 Surface 列表
                 * mSessionStateCallBack                              CameraCaptureSession 的状态回调接口，当它创建好后会回调onConfigured方法(配置)
                 * mHandler                                           确定Callback在哪个线程执行，为null的话就在当前线程执行
                 */
                cameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), mSessionStateCallBack, mHandler);


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "相机连接断开");
            mCameraDevice.close();
            mCameraDevice = null;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.d(TAG, "相机打开失败");
            mCameraDevice.close();
            mCameraDevice = null;

        }
    };


    //相机会话的监听器，通过他得到mCameraSession对象，这个对象可以用来发送预览和拍照请求
    private CameraCaptureSession.StateCallback mSessionStateCallBack = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try {

                //得到mCameraSession对象
                mCameraSession = cameraCaptureSession;

                //开始预览,预览过程在子线程中进行
                cameraCaptureSession.setRepeatingRequest(mPreViewBuidler.build(), myCaptureCallback, mHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

    private CameraCaptureSession.CaptureCallback myCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            //todo 只要在预览,那么这里一直在执行
            Log.e(TAG, "process: 我想看看是不是预览的时候这里一直在走 ... ?");

        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            //Log.d(TAG, "onCaptureCompleted: ");
            mCameraSession = session;
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            mCameraSession = session;
            //Log.d(TAG, "onCaptureFailed: ");
        }

    };

    /**
     * 开始录像
     */
    @SuppressLint("MissingPermission")
    private void startRecordingVideo() throws CameraAccessException {

        if (EasyPermissions.hasPermissions(MainActivity.this, VIDEO_PERMISSIONS)) {//检查是否获取该权限
            Log.i(TAG, "requestPhonePermission: 已获得权限");

        } else {
            //第二个参数是被拒绝后再次申请该权限的解释
            //第三个参数是请求码
            //第四个参数是要申请的权限
            EasyPermissions.requestPermissions(MainActivity.this, "请允许必要的权限!", REQUEST_VIDEO_PERMISSIONS, VIDEO_PERMISSIONS);
        }

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();

        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreViewSize) {

            return;
        }

        if (mRecordCurrentTime != 0) {
            mRecordTime.setBase(SystemClock.elapsedRealtime() - (mRecordCurrentTime - mRecordTime.getBase()));
            Log.i(TAG, "mRecordCurrentTime != 0: " + SystemClock.elapsedRealtime());
            Log.i(TAG, "mRecordTime.getBase(): " + mRecordTime.getBase());
        } else {
            mRecordTime.setBase(SystemClock.elapsedRealtime());
            Log.i(TAG, "mRecordCurrentTime == 0: " + SystemClock.elapsedRealtime());
            Log.i(TAG, "mRecordTime.getBase(): " + mRecordTime.getBase());
        }

        mRecordTime.start();

        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreViewSize.getWidth(), mPreViewSize.getHeight());

            mPreViewBuidler = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);//录像模式
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview    对于相机预览设置表
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreViewBuidler.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreViewBuidler.addTarget(recorderSurface);

            // Start a capture session  启动捕获会话
            // Once the session starts, we can update the UI and start recording    会话开始后，我们可以更新UI并开始录制。
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                    mCameraSession = cameraCaptureSession;

                    if (isOpenForFlash) {

                        mPreViewBuidler.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

                    } else {
                        mPreViewBuidler.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    }

                    try {
                        updateCameraPreviewSession();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            if (isOpenForFlash) {

                                mIv_flash.setImageResource(R.drawable.ic_highlight_yellow_24dp);

                            } else {
                                mIv_flash.setImageResource(R.drawable.ic_highlight_black_24dp);
                            }

                            // UI
                            camera_btn.setText("停止");
                            mIsRecordingVideo = true;
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {


                }
            }, mHandler);

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 关掉是为了重新开启一个新的
     */
    private void closePreviewSession() {
        if (mCameraSession != null) {
            mCameraSession.close();
            mCameraSession = null;
        }
    }

    /**
     * 设置录像参数
     *
     * @throws IOException
     */
    private void setUpMediaRecorder() throws IOException {

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOnErrorListener(onErrorListener);//设置录像错误监听

        File tempImageFilePath = getTempImageFilePath();

        mAbsolutePath = tempImageFilePath.getAbsolutePath();
        mMediaRecorder.setOutputFile(mAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(6000000);//这玩意控制视频质量 7兆
        mMediaRecorder.setAudioEncodingBitRate(44100);
        mMediaRecorder.setVideoFrameRate(60);
        mMediaRecorder.setVideoSize(mPreViewSize.getWidth(), mPreViewSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        //设置最大录像时间 单位：毫秒
        mMediaRecorder.setMaxDuration(10 * 1000);
        mMediaRecorder.setOnInfoListener(this);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    private void stopRecordingVideo() {

        FnProgressDialog.showDialog(this);
        // UI
        isOpenForFlash = false;
        mIv_flash.setImageResource(R.drawable.ic_highlight_black_24dp);

        mRecordTime.stop();
        mRecordTime.setBase(SystemClock.elapsedRealtime());

        mHandler.post(new StopRecordingView());

    }

    private void updateUIForStopRecordingVideo() {

        mIsRecordingVideo = false;
        camera_btn.setText("录像");

        Toast.makeText(MainActivity.this, "录像结束,请检查", Toast.LENGTH_SHORT).show();
        Log.e(TAG, " 录像路径: " + mAbsolutePath);


    }

    /**
     * 停止录制,此时是在子线程中
     */
    private class StopRecordingView implements Runnable {
        @Override
        public void run() {

            if (mIsRecordingVideo) {

                mIsRecordingVideo = false;

                /**
                 *  添加这两句  可以解决stop是闪退问题
                 */
                try {

                    if (isOpenForFlash) {
                        mPreViewBuidler.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                    }

                    //todo 这里看能不能,通过设置某些属性解决这个bug

                    updateCameraPreviewSession();

                    Thread.sleep(500);

                    mCameraSession.stopRepeating();//停止连续预览
                    mCameraSession.abortCaptures();//停止捕获

                    closePreviewSession();//关闭预览 session

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Stop recording
                mMediaRecorder.stop();
                mMediaRecorder.release();
                Message.obtain(mUIHandler, STOP_RECORDING).sendToTarget();

            }


        }
    }

    private File getTempImageFilePath() throws FileNotFoundException {
        String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + System.currentTimeMillis() + "temp.mp4";
        File file = new File(mPath);
        //判断文件是否存在，不存在就创建一个
        if (!file.exists()) {
            try {
                //创建文件
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }


    /**
     * 这里可以实现重新预览
     *
     * @throws CameraAccessException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void updateCameraPreviewSession() throws CameraAccessException {
        //todo 这里出现了bug 当录像完成以后 ,再次点击 控制闪光灯,出现

        try {
            mPreViewBuidler.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);//控制AF触发取消
//        mPreViewBuidler.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);//控制自动闪光声发射模式,就是开启自动闪光灯模式
            mCameraSession.setRepeatingRequest(mPreViewBuidler.build(), myCaptureCallback, mHandler);//这句话就是通过管道向摄像头发出请求
        } catch (Exception e) {
            quickToast("摄像头开启失败,请重试!");
            e.printStackTrace();
        }

    }


    //----------------------------------- 第二部分 view 的监听 及camera2 end 的初始化  -----------------------------------


    //----------------------------------- 第三部分 控制: 消息处理,UI更新 start -----------------------------------

    /**
     * 处理子线程发出的消息
     */
    private class InnerCallBack implements Handler.Callback {
        @Override
        public boolean handleMessage(Message message) {

            FnProgressDialog.closeDialog();

            switch (message.what) {

                //录像成功后,更新UI
                case STOP_RECORDING:

                    updateUIForStopRecordingVideo();

                    break;

                case DELETE_SUCCESS:
                    finish();
                    break;
            }
            return false;
        }
    }

    /**
     * 点击事件处理
     *
     * @param view
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {

            case R.id.camera_btn:

                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                    Log.e(TAG, "停止");

                } else {
                    Log.e(TAG, "开始");
                    try {
                        startRecordingVideo();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                break;

            case R.id.iv_left:

                showOrHintButton(false);

                try {
                    updateCameraPreviewSession();//重新开始预览--里面包含了关闭闪光灯操作
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

                break;

            //确认完成拍照并返回
            case R.id.iv_right:


                finish();

                break;

            case R.id.iv_flash:

                if (isOpenForFlash) {

                    setFlashMode(false);

                } else {
                    setFlashMode(true);
                }


                break;
        }
    }


    /**
     * 是否显示左右两边的按钮
     *
     * @param isShow
     */
    public void showOrHintButton(boolean isShow) {
        if (isShow) {

            mIv_left.setVisibility(View.VISIBLE);
            mIv_right.setVisibility(View.VISIBLE);
            camera_btn.setVisibility(View.INVISIBLE);
            mIv_flash.setVisibility(View.INVISIBLE);

        } else {

            mIv_left.setVisibility(View.INVISIBLE);
            mIv_right.setVisibility(View.INVISIBLE);
            camera_btn.setVisibility(View.VISIBLE);
            mIv_flash.setVisibility(View.VISIBLE);

        }
    }

    private void setFlashMode(boolean mIsOpenForFlash) {

        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreViewSize) {
            quickToast("请重新开启这个界面!");
            return;
        }

        if (mIsOpenForFlash) {

            isOpenForFlash = true;
            mPreViewBuidler.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            mIv_flash.setImageResource(R.drawable.ic_highlight_yellow_24dp);

        } else {

            isOpenForFlash = false;
            mPreViewBuidler.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            mIv_flash.setImageResource(R.drawable.ic_highlight_black_24dp);
        }

        if (mCameraSession != null) {
            try {
                updateCameraPreviewSession();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    }

    //------------------------------------ 控制缩放 start ------------------------------------
    @Override
    public void onStart(VerticalSeekBar slideView, int progress) {

    }

    @Override
    public void onProgress(VerticalSeekBar slideView, int progress) {

        mHandler.post(new ZoomRunnable(progress));

    }

    private void zoomRunnable(int progress) {

        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int radio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue() / 2;
        int realRadio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();
        int centerX = rect.centerX();
        int centerY = rect.centerY();
        int minMidth = (rect.right - ((progress * centerX) / 100 / radio) - 1) - ((progress * centerX / radio) / 100 + 8);
        int minHeight = (rect.bottom - ((progress * centerY) / 100 / radio) - 1) - ((progress * centerY / radio) / 100 + 16);
        if (minMidth < rect.right / realRadio || minHeight < rect.bottom / realRadio) {
            Log.i("sb_zoom", "sb_zoomsb_zoomsb_zoom");
            return;
        }

        Rect newRect = new Rect((progress * centerX / radio) / 100 + 40, (progress * centerY / radio) / 100 + 40, rect.right - ((progress * centerX) / 100 / radio) - 1, rect.bottom - ((progress * centerY) / 100 / radio) - 1);
        Log.i("sb_zoom", "left--->" + ((progress * centerX / radio) / 100 + 8) + ",,,top--->" + ((progress * centerY / radio) / 100 + 16) + ",,,right--->" + (rect.right - ((progress * centerX) / 100 / radio) - 1) + ",,,bottom--->" + (rect.bottom - ((progress * centerY) / 100 / radio) - 1));
        mPreViewBuidler.set(CaptureRequest.SCALER_CROP_REGION, newRect);
    }

    @Override
    public void onStop(VerticalSeekBar slideView, int progress) {

    }

    private class ZoomRunnable implements Runnable {

        private int mProgress;

        public ZoomRunnable(int progress) {
            this.mProgress = progress;
        }

        @Override
        public void run() {
            zoomRunnable(mProgress);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    quickToast("放大：" + mProgress + "%");
                    updatePreview1();
                }
            });
        }
    }


    private SeekBar.OnSeekBarChangeListener myOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int radio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue() / 2;
            int realRadio = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();
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
            mPreViewBuidler.set(CaptureRequest.SCALER_CROP_REGION, newRect);
            quickToast("放大：" + i + "%");

            updatePreview1();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    /**
     * 删除当前视频文件
     */
    private class DeleteFileRunnable implements Runnable {
        @Override
        public void run() {
            File file = new File(mAbsolutePath);
            // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    Log.i(TAG,"删除单个文件" + mAbsolutePath + "成功！");
                } else {
                    Log.i(TAG,"删除单个文件" + mAbsolutePath + "失败！");
                }
            } else {
                Log.i(TAG,"删除单个文件" + mAbsolutePath + "不存在！");
            }

            Message.obtain(mUIHandler, DELETE_SUCCESS).sendToTarget();
        }
    }

    /**
     * 更新预览
     */
    private void updatePreview1() {
        try {
            mPreViewBuidler.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            //3A
            mCameraSession.setRepeatingRequest(mPreViewBuidler.build(), myCaptureCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("updatePreview", "ExceptionExceptionException");
        }
    }


    @Override
    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                Log.i(TAG, "//3");
                if (mIsRecordingVideo) {
                    stopRecordingVideo();
                }
                break;
        }
    }

    //退出提示
    private void showFinishDialog() {

        new AlertDialog.Builder(this)
                .setTitle("非正常退出,是否保存该视频 ? ")
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        finish();
                    }
                })
                .setNegativeButton("不保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        FnProgressDialog.showDialog(MainActivity.this);
                        mHandler.post(new DeleteFileRunnable());
                    }
                })
                .show();

    }

    //----------------------------------- 第三部分 控制: 消息处理,UI更新 end -----------------------------------


    //=======================================================================================================
    //==
    //==
    //== 以下是一些普通配置,分开为了好看
    //== 权限请求使用了: EasyPermissions 直接 copy 即可
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==
    //==========================================================================================================

    //----------------------------------------- 一般配置 start -----------------------------------------
    private void setActivityConfig() {
        //去除title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //去掉Activity上面的状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

    }

    /**
     * 快速Toast
     */
    private Toast toast;

    public void quickToast(String text) {
        if (toast == null) {
            toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        toast.setText(text);
        toast.show();
    }


    @Override
    public void onBackPressed() {

        //如果没有正常结束,同时视频仍在录制,那么先停止录制,提醒用户是否保存该视频
        //如果保存,则无需任何操作,然后退出,如果不保存,那么先删除该视频,然后退出!
        if (mIsRecordingVideo) {
            stopRecordingVideo();
            showFinishDialog();//退出提示
        }else{
            super.onBackPressed();
        }
    }


    @Override
    protected void onDestroy() {

        if (mIsRecordingVideo) {
            stopRecordingVideo();
        }

        if (mUIHandler != null) {
            mUIHandler.removeCallbacksAndMessages(null);
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }

        surface = null;

        super.onDestroy();

    }

    private MediaRecorder.OnErrorListener onErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    //----------------------------------------- 一般配置 end -----------------------------------------

    //-------------------------------------------------- 权限请求 start -------------------------------------------------

    @SuppressLint("MissingPermission")
    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        //权限同意
        Log.i(TAG, "获取成功的权限" + perms);
        if (requestCode == 0) {
            if (perms.size() > 0) {
                if (perms.contains(Manifest.permission.CAMERA) && perms.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    //获取权限

                    //开启摄像头,监听开启状态
                    try {
                        String cameraid = CameraCharacteristics.LENS_FACING_FRONT + "";//获取前置摄像头的属性ID
                        mManager.openCamera(cameraid, cameraOpenCallBack, mHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

        }

    }


    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        //权限拒绝
        for (String perm : perms) {
            Log.i(TAG, "onPermissionsDenied: " + perm);
        }

        //权限拒绝
        if (!EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {

            new AlertDialog.Builder(this)
                    .setMessage("为了您能正常使用功能，请点击确认后,重新允许权限!")
                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //把申请权限的回调交由EasyPermissions处理
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }




    //-------------------------------------------------- 权限请求 end -------------------------------------------------
}
