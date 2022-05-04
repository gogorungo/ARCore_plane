package com.example.ex_07_plane;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView myTextView;

    GLSurfaceView mSurfaceView;
    MainRenderer mRenderer;

    Session mSession;
    Config mConfig;

    boolean mUserRequestedInstall = true, mTouched = false;

    float mCurrentX, mCurrentY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideStatusBarAndTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        myTextView = (TextView) findViewById(R.id.myTextView);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if(displayManager != null){
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {

                }

                @Override
                public void onDisplayRemoved(int i) {

                }

                @Override
                public void onDisplayChanged(int i) {
                    synchronized (this){
                        mRenderer.mViewportChanged = true;
                    }
                }
            }, null);
        }

        mRenderer = new MainRenderer(this, new MainRenderer.RenderCallback() {
            @Override
            public void preRender() {
                if(mRenderer.mViewportChanged){
                    Display display = getWindowManager().getDefaultDisplay();
                    int displayRotation = display.getRotation();
                    mRenderer.updateSession(mSession, displayRotation);
                }

                // 카메라에 알려준다
                mSession.setCameraTextureName(mRenderer.getTextureId());

                // 세션으로부터 정보를 받아올 프레임
                Frame frame = null;

                try {
                    frame = mSession.update();
                } catch (CameraNotAvailableException e) {
                    e.printStackTrace();
                }

                // 프레임 변경시
                if(frame.hasDisplayGeometryChanged()){
                    mRenderer.mCamera.transformDisplayGeometry(frame);
                }

                PointCloud pointCloud = frame.acquirePointCloud();
                mRenderer.mPointCloud.update(pointCloud);
                // 자원해제
                pointCloud.release();

                //터치하였다면
                if(mTouched){

                    LightEstimate estimate = frame.getLightEstimate();
                    // LightEstimate : 빛에 대한 정보를 가지는 클래스

                    // getPixelIntensity() : 빛의 강도 0.0 ~ 1.0 감지
                    // 빛의 세기
                    float lightIntensity = estimate.getPixelIntensity();

                    float [] colorCorrection = new float[4];
                    // 빛의 색깔 가져오기
                    estimate.getColorCorrection(colorCorrection, 0);


                    List<HitResult> results = frame.hitTest(mCurrentX, mCurrentY);
                    for(HitResult result : results){
                        Pose pose = result.getHitPose(); // 증강 공간에서의 좌표
                        float [] modelMatrix = new float[16];
                        pose.toMatrix(modelMatrix, 0); // 좌표를 가지고 matrix 화 함

                        // 증강 공간의 좌표에 객체 있는지 받아온다(Plane 이 걸려 있는지 확인)
                        Trackable trackable = result.getTrackable();


                        // 좌표에 걸린 객체가 Plane 인가
                        if(trackable instanceof Plane &&
                                // Plane 폴리곤(면) 안에 좌표가 있는가?
                                ((Plane)trackable).isPoseInPolygon(pose)
                        ){

                            // 빛의 세기 값을 넘긴다.
                            mRenderer.mObj.setLightIntensity(lightIntensity);
                            // 빛의 색을 magenta 로 강제화 시킴
                            // mRenderer.mObj.setColorCorrection(new float[]{1.0f,0.0f,1.0f,1.0f});
                            mRenderer.mObj.setColorCorrection(colorCorrection);
                            mRenderer.mObj.setModelMatrix(modelMatrix);
                            //큐브의 modelMatrix를 터치한 증강현실 modelMatrix로 설정
//                            mRenderer.mCube.setModelMatrix(modelMatrix);

                        }
                    }
                    mTouched = false;
                }

                //Session으로부터 증강현실 속에서의 평면이나 점 객체를 얻을 수 있다.
                //                                   Plane   Point
                Collection<Plane> planes = mSession.getAllTrackables(Plane.class);


                boolean isPlaneDetected = false;

                //ARCore 상의 Plane 들을 얻는다.
                for(Plane plane: planes){
                    
                    // plane 이 정상이라면
                    if(plane.getTrackingState() == TrackingState.TRACKING &&
                    plane.getSubsumedBy() == null){ // 다른 평면이 존재하는지

                        isPlaneDetected = true;

                        //렌더링에서 plane 정보를 갱신하여 출력
                        mRenderer.mPlane.update(plane);
                    }
                }

                if(isPlaneDetected) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("평면을 찾았다");
                        }
                    });
                }else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("평면이 안보인다");
                        }
                    });
                }

                // 카메라 세팅
                Camera camera = frame.getCamera();
                float [] projMatrix = new float[16];
                camera.getProjectionMatrix(projMatrix,0,0.1f,100f);
                float [] viewMatrix = new float[16];
                camera.getViewMatrix(viewMatrix,0);
                
                mRenderer.setProjectionMatrix(projMatrix);
                mRenderer.updateViewMatrix(viewMatrix);
            }
        });

        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8,8,8,8,16,0);
        mSurfaceView.setRenderer(mRenderer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestCameraPermission();
        try {
            // 세션이 없다면 세션 생성
            if(mSession == null){
                switch (ArCoreApk.getInstance().requestInstall(this, true)){
                    case INSTALLED:
                        mSession = new Session(this);
                        Log.d("메인"," ARCore session 생성");
                        break;
                        //설치가 안되는 기계
                    case INSTALL_REQUESTED:
                        mUserRequestedInstall = false;
                        Log.d("메인"," ARCore 설치가 필요함");
                        break;
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        mConfig = new Config(mSession);

        mSession.configure(mConfig);

        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }

        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        mSession.pause();
    }

    void hideStatusBarAndTitleBar(){
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    void requestCameraPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    this,
                    new String [] {Manifest.permission.CAMERA},
                    0
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN){
            mTouched = true;
            mCurrentX = event.getX();
            mCurrentY = event.getY();

        }
        return true;
    }
}