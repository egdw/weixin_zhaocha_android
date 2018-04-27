package hdy.im.auto_zhaocha;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.yhao.floatwindow.FloatWindow;
import com.yhao.floatwindow.MoveType;
import com.yhao.floatwindow.Screen;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Button mShowFloat;
    private OutputStream os = null;
    private boolean isShow; //悬浮窗口是否显示
    private boolean isOpen;
    private boolean isComplete = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);
        staticLoadCVLibraries();
//        exec("");
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
    }


    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to
     * grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);
        }
    }

    //OpenCV库静态加载并初始化
    private void staticLoadCVLibraries() {
        boolean load = OpenCVLoader.initDebug();
        if (load) {
            Log.i("CV", "Open CV Libraries loaded...");
        }
    }

    //执行shell命令
    private void exec(String cmd) {
        try {
            if (os == null) {
                os = Runtime.getRuntime().exec("su").getOutputStream();
            }
            os.write(cmd.getBytes());
            os.flush();
        } catch (IOException e) {
            Toast.makeText(this, "ROOT权限获取失败" + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void showFloat() {
//        final View view = View.inflate(MainActivity.this, R.layout.float_view, null);
        final ImageView imageView = new ImageView(MainActivity.this);
        imageView.setImageResource(R.mipmap.logo);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isShow) {
                    if(isComplete){
                        Toast.makeText(MainActivity.this, "开始查找!", Toast.LENGTH_SHORT).show();
                        find();
                        onResume();
                    }else{
                        //buchuli
                    }
                } else {
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
                .setY(Screen.height, 0.1f)
                .setDesktopShow(true)
                .setMoveType(MoveType.inactive)
                .build();
        onResume();
    }

    public void find() {
        myHandler.sendEmptyMessage(0x3);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File file = new File("/sdcard/autodect.png");
                    file.delete();
                    exec("screencap -p /sdcard/autodect.png" + "\n");
                    while (file.exists() == false || file.length() == 0) {
                        continue;
                    }
                    Mat im = Imgcodecs.imread("/sdcard/autodect.png");
                    Mat pic1 = new Mat(im, new Rect(199, 143, 826, 780));
                    Mat pic2 = new Mat(im, new Rect(199, 1043, 826, 780));
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
                    Mat temp_t = new Mat();
                    t.copyTo(temp_t);
                    List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
                    Imgproc.findContours(temp_t, contours, new Mat(), Imgproc.RETR_EXTERNAL,
                            Imgproc.CHAIN_APPROX_SIMPLE);

                    for (MatOfPoint matOfPoint : contours) {
                        Rect boundingRect = Imgproc.boundingRect(matOfPoint);

                        int w = boundingRect.width;
                        int h = boundingRect.height;
                        int x = boundingRect.x;
                        int y = boundingRect.y;
                        if (w > 5 && h > 5 && w < 300 && h < 300) {
                            int l_x = (x + w / 2) + 199;
                            int l_y = (y + h / 2) + 143;
                            exec("input tap " + l_x + " " + l_y + "\n");
                        }

                    }
                    //完成.
                    myHandler.sendEmptyMessage(0x1);
                } catch (Exception e) {
                    e.printStackTrace();
                    //异常
                    myHandler.sendEmptyMessage(0x2);
                }
            }
        });
        thread.start();
    }


    private Handler myHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 0x1:
                    isComplete = true;
                    Toast.makeText(MainActivity.this, "搜索完成!", Toast.LENGTH_SHORT).show();
                    break;
                case 0x2:
                    isComplete = true;
                    Toast.makeText(MainActivity.this, "程序出现异常!", Toast.LENGTH_SHORT).show();
                    break;
                case 0x3:
                    isComplete = false;
                    break;
                default:
                    break;
            }

        }
    };
}
