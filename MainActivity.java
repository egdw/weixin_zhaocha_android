package hdy.im.auto_zhaocha;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.yhao.floatwindow.FloatWindow;
import com.yhao.floatwindow.MoveType;
import com.yhao.floatwindow.Screen;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PROJECTION = 10;
    private Button mShowFloat;
    private OutputStream os = null;
    private boolean isShow; //悬浮窗口是否显示
    private boolean isOpen;
    private boolean isComplete = true;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        staticLoadCVLibraries();
        try {
            requestCapturePermission();
            mShowFloat = (Button) findViewById(R.id.showFloat);
            mShowFloat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isOpen) {
                        mShowFloat.setText("打开辅助");
                        FloatWindow.destroy("logo");
                    } else {
                        mShowFloat.setText("关闭辅助");
                        showFloat();
                    }
                    isOpen = !isOpen;
                }
            });
        } catch (Exception e) {
            myHandler.sendEmptyMessage(0x2);
        }
    }

    /**
     * 获取到真实的物理屏幕
     */
    private void getScreen() {
        Display display = getWindowManager().getDefaultDisplay();
        Method mGetRawH = null;
        Point point = new Point();
        display.getRealSize(point);
        int mScreenH = point.y;
        int mScreenW = point.x;
        mScreenWidth = mScreenW;
        mScreenHeight = mScreenH;
        mScreenDensity = mScreenHeight / mScreenWidth;
    }


    /**
     * 定位两张图片的位置
     *
     * @param bitmap
     * @return 0 代表第一张图
     * 1 代表第二征途
     * 2 代表x坐标
     * 3 代表y坐标
     * 4 代表宽和高
     */
    private Object[] findRect(Bitmap bitmap) {
        //搜索开始和结束的坐标(Y坐标)
        int search_y = 0;
        int search_x = 0;

        int bitmapHeight = bitmap.getHeight();
        int bitmapWidth = bitmap.getWidth();
        //设置开始搜索的为图片的三分之一处
        search_y = bitmapHeight / 3;
        //设置搜索的X坐标为图片的正中心
        search_x = bitmapWidth / 2;

        //进行从上到下定位图片位置
        //判断中间白线所在的位置

        int center_white_height = 0;
        int center_white_width = bitmapWidth;
        int[] center_point = new int[2];
        //pic的宽度和高度.宽度和高度相等
        int pic_whidth = 0;

        //用于临时存放数据
        int last_point_temp = 0;
        //记录第一层白点出现的位置.此循环用于定位中间白线的Y轴和高度
        int first_white_point = 0;
        for (int i = search_y; i < bitmapHeight; i++) {
            int bitmapPixel = bitmap.getPixel(search_x, i);
            if (Color.red(bitmapPixel) == 218 && Color.green(bitmapPixel) == 238 && Color.blue(bitmapPixel) == 236) {
                //判断是不是白点
                if (last_point_temp == 0) {
                    last_point_temp = i;
                    first_white_point = i;
                } else {
                    if (i - last_point_temp != 1) {
                        //说明是不连续的,出现中断
                        //获取到白色的高度
                        int whiteHeight = last_point_temp - first_white_point;
                        if (whiteHeight > center_white_height && whiteHeight > 5) {
                            center_white_height = whiteHeight;
                            center_point[1] = last_point_temp;
                        }
                        //初始化,等待下一次搜索
                        last_point_temp = 0;
                        first_white_point = 0;
                    } else {
                        last_point_temp = i;
                    }
                }
            }
        }


        int biggest_white_x = 0;
        int biggest_white_width = 0;
        for (int i = bitmapWidth - 1; i > 0; i--) {
            int bitmapPixel = bitmap.getPixel(i, search_y);
            //从右往左搜索
            if (Color.red(bitmapPixel) == 218 && Color.green(bitmapPixel) == 238 && Color.blue(bitmapPixel) == 236) {
                //判断是不是白点
                if (last_point_temp == 0) {
                    last_point_temp = i;
                    first_white_point = i;
                } else {
                    if (last_point_temp - i != 1) {
                        //说明是不连续的,出现中断
                        //获取到最小白色边框的坐标和宽度
                        int whiteWidth = first_white_point - last_point_temp;
                        if (whiteWidth < center_white_width && whiteWidth > 5) {
                            center_white_width = whiteWidth;
                            center_point[0] = last_point_temp;
                        }
                        if (whiteWidth > biggest_white_width) {
                            biggest_white_width = whiteWidth;
                            biggest_white_x = last_point_temp;
                        }
                        //初始化,等待下一次搜索
                        last_point_temp = 0;
                        first_white_point = 0;
                    } else {
                        last_point_temp = i;
                    }
                }
            }
        }
        //得到图片的宽高
        pic_whidth = biggest_white_x - center_point[0] - center_white_width;
        center_point[0] += center_white_width;
        //循环完成获取到了图片的左上角的坐标宽度和高度
        Bitmap getBitmap = Bitmap.createBitmap(bitmap, center_point[0], center_point[1] + 5, pic_whidth, pic_whidth);
        Bitmap getBitmap2 = Bitmap.createBitmap(bitmap, center_point[0], center_point[1] - center_white_height - pic_whidth + center_white_width, pic_whidth, pic_whidth);
        //获取到两张图片
        return new Object[]{getBitmap, getBitmap2, center_point[0], center_point[1] - center_white_height - pic_whidth + center_white_width, pic_whidth};
    }


    private void showFloat() {
//        final View view = View.inflate(MainActivity.this, R.layout.float_view, null);
        final ImageView imageView = new ImageView(MainActivity.this);
        imageView.setImageResource(R.mipmap.logo);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isShow) {
                    if (isComplete) {
                        Toast.makeText(MainActivity.this, "开始查找!", Toast.LENGTH_SHORT).show();
                        getScreen();
                        startScreenShot();
                        createImageReader();
                        onResume();
                    } else {
                        Toast.makeText(MainActivity.this, "正在查找中...请稍后..", Toast.LENGTH_SHORT).show();
                    }
                }
                isShow = !isShow;

            }
        });
        showLogoFloat(imageView);
    }


    //显示logo悬浮图标
    private void showLogoFloat(View view) {
        FloatWindow
                .with(getApplicationContext())
                .setView(view)
                .setTag("logo")
                .setY(Screen.height, 0.1f)
                .setDesktopShow(true)
                .setMoveType(MoveType.inactive)
                .build();
        onResume();
    }

    /**
     * 查找图片的不同
     *
     * @param bitmap
     * @return 0 代表第一张图
     * 1 代表第二张图
     * 2 代表x坐标
     * 3 代表y坐标
     * 4 代表宽和高
     */
    public void find(final Bitmap bitmap, final Object[] rect) {
        myHandler.sendEmptyMessage(0x3);
        final Bitmap bitmap_src = (Bitmap) rect[0];
        final Bitmap src = bitmap_src.copy(Bitmap.Config.ARGB_8888, true);
        final Bitmap bitmap_src2 = (Bitmap) rect[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Mat pic1 = new Mat();
                    Mat pic2 = new Mat();
                    Utils.bitmapToMat(bitmap_src, pic1);
                    Utils.bitmapToMat(bitmap_src2, pic2);
                    Imgproc.cvtColor(pic1, pic1, Imgproc.COLOR_BGR2RGB);
                    Imgproc.cvtColor(pic2, pic2, Imgproc.COLOR_BGR2RGB);
                    Mat t = new Mat();
                    Core.absdiff(pic1, pic2, t);
                    Imgproc.blur(t, t, new Size(5, 5));
                    Imgproc.cvtColor(t, t, Imgproc.COLOR_BGR2GRAY);
                    for (int i = 0; i < 4; i++) {
                        //膨胀
                        Imgproc.dilate(t, t, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5)));
                        Imgproc.erode(t, t, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5)));
                    }
                    Imgproc.GaussianBlur(t, t, new Size(5, 5), 1.5);

                    Utils.matToBitmap(t, src);

//                    Imgproc.blur(t, t, new Size(5, 5));
//                    Imgproc.cvtColor(t, t, Imgproc.COLOR_BGR2GRAY);
//                    FileService.savePhoto(src,"3.png");
                    Message message = new Message();
                    message.what = 0x1;
                    message.obj = new Object[]{src, rect[2], rect[3], rect[4]};
                    myHandler.sendMessage(message);
                } catch (Exception e) {
                    e.printStackTrace();
                    myHandler.sendEmptyMessage(0x2);
                }
            }
        });
        thread.start();
    }


    @SuppressLint("HandlerLeak")
    private Handler myHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case 0x1:
                    isComplete = true;
                    Object[] m = (Object[]) msg.obj;
                    //悬浮窗触摸事件
                    ImageView imageView = new ImageView(MainActivity.this);
                    imageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            FloatWindow.destroy("show");
                            Handler handler1 = new Handler();
                            handler1.postDelayed(new Runnable() {
                                public void run() {
                                    getScreen();
                                    startScreenShot();
                                    createImageReader();
                                }
                            }, 500);

                        }
                    });
                    imageView.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View view) {
                            FloatWindow.destroy("show");
                            return true;
                        }
                    });
                    imageView.setImageBitmap(drawTextToBitmap(MainActivity.this, (Bitmap) m[0], "单击这里重新识别图片,长按关闭."));
                    int x = (int) m[1];
                    int y = (int) m[2];
                    int height = (int) m[3];
                    showFloat(imageView, x, y, height);
                    Toast.makeText(MainActivity.this, "搜索完成!", Toast.LENGTH_SHORT).show();
                    break;
                case 0x2:
                    isComplete = true;
                    Toast.makeText(MainActivity.this, "程序出现异常!", Toast.LENGTH_SHORT).show();
                    break;
                case 0x3:
                    isComplete = false;
                    break;
                case 0x4:
                    isComplete = true;
                    Toast.makeText(MainActivity.this, "未检测到图片框,请重试!", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }

        }
    };

    //显示计算结果图悬浮图标
    private void showFloat(View view, int x, int y, int width) {
        FloatWindow
                .with(getApplicationContext())
                .setView(view)
                .setTag("show")
                .setX(x)
                .setY(y)
                .setWidth(width)
                .setHeight(width)
                .setDesktopShow(true)
                .setMoveType(MoveType.inactive)
                .build();
        onResume();
    }

    /**
     * 下面是获取屏幕外的截图的代码
     */

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private static Intent mResultData = null;


    private ImageReader mImageReader;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private GestureDetector mGestureDetector;

    private int mScreenWidth = 1920;
    private int mScreenHeight = 1080;
    private int mScreenDensity = mScreenWidth / mScreenHeight;

    public void requestCapturePermission() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            //5.0 之后才允许使用屏幕截图

            return;
        }

        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_MEDIA_PROJECTION:

                if (resultCode == RESULT_OK && data != null) {
                    mResultData = data;
                } else {
                    Toast.makeText(MainActivity.this, "必须需要截取您的屏幕显示的所有内容!", Toast.LENGTH_SHORT).show();
                    requestCapturePermission();
                }
                break;
        }

    }


    private void startScreenShot() {

//    mFloatView.setVisibility(View.GONE);

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
            public void run() {
                //start virtual
                startVirtual();
            }
        }, 5);

        handler1.postDelayed(new Runnable() {
            public void run() {
                //capture the screen
                startCapture();

            }
        }, 30);
    }


    private void createImageReader() {
        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);
        }

    }

    public void startVirtual() {
        if (mMediaProjection != null) {
            virtualDisplay();
        } else {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void setUpMediaProjection() {
        if (mResultData == null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(intent);
        } else {
            mMediaProjection = getMediaProjectionManager().getMediaProjection(Activity.RESULT_OK, mResultData);
        }
    }

    private MediaProjectionManager getMediaProjectionManager() {
        return (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private void startCapture() {
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            startScreenShot();
        } else {
            SaveTask mSaveTask = new SaveTask();
            AsyncTaskCompat.executeParallel(mSaveTask, image);
        }
    }


    public class SaveTask extends AsyncTask<Image, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Image... params) {
            if (params == null || params.length < 1 || params[0] == null) {

                return null;
            }
            Image image = params[0];
            int width = image.getWidth();
            int height = image.getHeight();
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            //每个像素的间距
            int pixelStride = planes[0].getPixelStride();
            //总的间距
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            image.close();
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //预览图片
            try {
                Object[] rect = findRect(bitmap);
                find(bitmap, rect);
            } catch (Exception e) {
                myHandler.sendEmptyMessage(0x4);
            }
        }
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

    //OpenCV库静态加载并初始化
    private void staticLoadCVLibraries() {
        boolean load = OpenCVLoader.initDebug();
        if (load) {
            Log.i("CV", "Open CV Libraries loaded...");
        }
    }

    /**
     * 获取当前屏幕的尺寸大小
     *
     * @param context
     * @return
     */
    public static DisplayMetrics getMetrics(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    public static Bitmap drawTextToBitmap(Context gContext, Bitmap b, String gText) {
        Resources resources = gContext.getResources();
        float scale = resources.getDisplayMetrics().density;
        Bitmap bitmap = b.copy(Bitmap.Config.ARGB_8888, true);

        android.graphics.Bitmap.Config bitmapConfig = bitmap.getConfig();
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
        }
        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true);

        Canvas canvas = new Canvas(bitmap);
        // new antialised Paint
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // text color - #3D3D3D
        paint.setColor(Color.rgb(61, 61, 61));
        // text size in pixels
        paint.setTextSize((int) 25);
        // text shadow
        paint.setShadowLayer(1f, 0f, 1f, Color.WHITE);

        // draw text to the Canvas center
        android.graphics.Rect bounds = new android.graphics.Rect();
//        paint.getTextBounds(gText, 0, gText.length(), bounds);
        paint.getTextBounds(gText, 0, gText.length(), bounds);
        //int x = (bitmap.getWidth() - bounds.width()) / 2;
        //int y = (bitmap.getHeight() + bounds.height()) / 2;
        //draw  text  to the bottom
        int x = (bitmap.getWidth() - bounds.width()) / 10 * 9;
        int y = (bitmap.getHeight() + bounds.height()) / 10 * 9;
        canvas.drawText(gText, x, y, paint);

        return bitmap;
    }

}
