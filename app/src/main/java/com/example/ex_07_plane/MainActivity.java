package com.example.ex_07_plane;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    GLSurfaceView mSurfaceView;
    MainRenderer mRenderer;

    Session mSession;
    Config mConfig;

    boolean mUserRequestedInstall = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideStatusBarAndTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
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

        mRenderer = new MainRenderer(new MainRenderer.RenderCallback() {
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

                //Session으로부터 증강현실 속에서의 평면이나 점 객체를 얻을 수 있다.
                //                                   Plane   Point
                Collection<Plane> planes = mSession.getAllTrackables(Plane.class);
                //ARCore 상의 Plane 들을 얻는다.
                for(Plane plane: planes){
                    
                    // plane 이 정상이라면
                    if(plane.getTrackingState() == TrackingState.TRACKING &&
                    plane.getSubsumedBy() == null){ // 다른 평면이 존재하는지
                        //렌더링에서 plane 정보를 갱신하여 출력
                        mRenderer.mPlane.update(plane);
                    }
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
}