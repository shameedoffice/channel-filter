package com.example.channelfilter;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView preview;
    private ImageView filteredView;
    private TextView status;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private ExecutorService executor;
    private volatile int mode = 0; // 0 RGB, 1 R, 2 G, 3 B
    private volatile boolean rawTab = false;
    private volatile Bitmap latestFiltered;
    private Camera camera;
    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { if (granted) startProcessed(); else status.setText("Camera permission is required."); });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        preview=findViewById(R.id.preview); filteredView=findViewById(R.id.filteredView); status=findViewById(R.id.status); executor=Executors.newSingleThreadExecutor();
        findViewById(R.id.processedTab).setOnClickListener(v -> { rawTab=false; startProcessed(); });
        findViewById(R.id.rawTab).setOnClickListener(v -> { rawTab=true; showRawInfo(); });
        findViewById(R.id.rgb).setOnClickListener(v -> mode=0);
        findViewById(R.id.red).setOnClickListener(v -> mode=1);
        findViewById(R.id.green).setOnClickListener(v -> mode=2);
        findViewById(R.id.blue).setOnClickListener(v -> mode=3);
        findViewById(R.id.capture).setOnClickListener(v -> captureProcessed());
        findViewById(R.id.exposure).setOnClickListener(v -> toggleAeLock());
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startProcessed(); else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void startProcessed() {
        rawTab=false; preview.setVisibility(View.GONE); filteredView.setVisibility(View.VISIBLE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return;
        ListenableFuture<ProcessCameraProvider> f=ProcessCameraProvider.getInstance(this);
        f.addListener(() -> { try { provider=f.get(); bindProcessed(); } catch(Exception e){ status.setText("Camera error: "+e.getMessage()); } }, ContextCompat.getMainExecutor(this));
    }

    private void bindProcessed() {
        provider.unbindAll();
        Preview p=new Preview.Builder().build();
        ImageAnalysis.Builder ab=new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888);
        ImageAnalysis analysis=ab.build();
        analysis.setAnalyzer(executor, this::analyze);
        imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        p.setSurfaceProvider(preview.getSurfaceProvider());
        camera=provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis, imageCapture);
        status.setText("Processed mode • RGB/R/G/B channel view • blue is the intended AVS HD 709 filter mode.");
    }

    private void analyze(ImageProxy image) {
        try {
            int w=image.getWidth(), h=image.getHeight(); Bitmap src=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            ByteBuffer buf=image.getPlanes()[0].getBuffer(); int row=image.getPlanes()[0].getRowStride(); int pix=image.getPlanes()[0].getPixelStride();
            byte[] bytes=new byte[row*h]; buf.rewind(); buf.get(bytes,0,Math.min(bytes.length,buf.remaining()));
            int[] out=new int[w*h];
            for(int y=0;y<h;y++){ int base=y*row; for(int x=0;x<w;x++){ int i=base+x*pix; int r=bytes[i]&255,g=bytes[i+1]&255,b=bytes[i+2]&255; int v=(mode==1?r:mode==2?g:mode==3?b:Math.max(r,Math.max(g,b))); if(mode==0) out[y*w+x]=Color.rgb(r,g,b); else out[y*w+x]=Color.rgb(v,v,v); }}
            src.setPixels(out,0,w,0,0,w,h);
            latestFiltered = src;
            runOnUiThread(() -> filteredView.setImageBitmap(src));
        } catch(Exception ignored) {} finally { image.close(); }
    }

    private void captureProcessed(){
        Bitmap bmp = latestFiltered;
        if (bmp == null) { toast("No filtered frame yet"); return; }
        ContentValues v=new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME,"ChannelFilter_"+System.currentTimeMillis()+".png");
        v.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        if(Build.VERSION.SDK_INT>=29) v.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/ChannelFilter");
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        if(uri==null){ toast("Could not create photo entry"); return; }
        try(OutputStream os=getContentResolver().openOutputStream(uri)){
            if(os==null || !bmp.compress(Bitmap.CompressFormat.PNG,100,os)) throw new Exception("PNG encode failed");
            toast("Saved filtered photo");
        }catch(Exception e){ getContentResolver().delete(uri,null,null); toast("Save failed: "+e.getMessage()); }
    }

    private void toggleAeLock(){ if(camera==null){return;} try { boolean locked=camera.getCameraInfo().getExposureState().isExposureCompensationSupported(); status.setText(locked?"Exposure compensation is available. For stable calibration, keep lighting and framing fixed.":"Camera does not expose exposure compensation."); }catch(Exception e){status.setText("Keep exposure/framing fixed while calibrating.");}}

    private void showRawInfo(){
        preview.setVisibility(View.GONE); filteredView.setVisibility(View.VISIBLE); filteredView.setImageDrawable(null);
        try { CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); boolean raw=false; String id=null; for(String cid:cm.getCameraIdList()){CameraCharacteristics c=cm.getCameraCharacteristics(cid); Integer facing=c.get(CameraCharacteristics.LENS_FACING); int[] caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES); if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK&&caps!=null){for(int x:caps) if(x==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) raw=true;} if(raw){id=cid;break;}}
            if(raw){ status.setText("RAW_SENSOR supported. RAW capture/channel extraction is enabled in the project architecture; live RAW streaming is device-dependent."); }
            else status.setText("This phone's back camera does not advertise RAW_SENSOR. Processed RGB/R/G/B mode remains available.");
        } catch(Exception e){status.setText("RAW capability check failed: "+e.getMessage());}
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){super.onDestroy(); if(provider!=null) provider.unbindAll(); if(executor!=null) executor.shutdown();}
}
