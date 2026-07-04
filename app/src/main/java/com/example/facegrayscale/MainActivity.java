package com.example.facegrayscale;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView statusText;
    private ProcessCameraProvider cameraProvider;
    private FaceDetector detector;
    private ExecutorService executor;
    private volatile Float lastSeenRatio = null;

    private final ActivityResultLauncher<String> requestPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startPreview();
                else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        statusText = findViewById(R.id.statusText);
        Button enrollButton = findViewById(R.id.enrollButton);
        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);

        executor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startPreview();
        } else {
            requestPermission.launch(Manifest.permission.CAMERA);
        }

        enrollButton.setOnClickListener(v -> {
            if (lastSeenRatio == null) {
                Toast.makeText(this, "Look straight at the camera first", Toast.LENGTH_SHORT).show();
                return;
            }
            FaceWatchService.ownerEyeRatio = lastSeenRatio;
            statusText.setText("Face enrolled! You can now tap Start Watching.");
        });

        startButton.setOnClickListener(v -> {
            if (FaceWatchService.ownerEyeRatio == null) {
                Toast.makeText(this, "Enroll your face first", Toast.LENGTH_SHORT).show();
                return;
            }
            startService(new Intent(this, FaceWatchService.class));
            Toast.makeText(this, "Watching started", Toast.LENGTH_SHORT).show();
        });

        stopButton.setOnClickListener(v -> {
            stopService(new Intent(this, FaceWatchService.class));
            GrayscaleController.disableGrayscale(this);
            Toast.makeText(this, "Watching stopped", Toast.LENGTH_SHORT).show();
        });
    }

    private void startPreview() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(480, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(executor, this::analyzeForEnrollment);

                CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeForEnrollment(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(this::updateLastSeenRatio)
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void updateLastSeenRatio(List<Face> faces) {
        if (faces.isEmpty()) return;
        Face face = faces.get(0);
        if (face.getLandmark(FaceLandmark.LEFT_EYE) == null
                || face.getLandmark(FaceLandmark.RIGHT_EYE) == null) return;

        float leftX = face.getLandmark(FaceLandmark.LEFT_EYE).getPosition().x;
        float rightX = face.getLandmark(FaceLandmark.RIGHT_EYE).getPosition().x;
        float eyeDistance = Math.abs(leftX - rightX);
        float faceWidth = face.getBoundingBox().width();
        if (faceWidth == 0) return;

        lastSeenRatio = eyeDistance / faceWidth;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) cameraProvider.unbindAll();
        executor.shutdown();
    }
}
