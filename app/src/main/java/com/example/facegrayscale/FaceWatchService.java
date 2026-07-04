package com.example.facegrayscale;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the front camera continuously and, using ML Kit's on-device face
 * detector, decides whether the CURRENT phone holder looks like the enrolled
 * owner. This is a lightweight geometric match (eye distance / face ratio),
 * not full biometric recognition - good enough to distinguish "it's probably
 * me" from "someone else / no one" for a wellbeing feature, but it is NOT
 * secure enough for authentication.
 *
 * Behavior:
 *  - Owner's face recognized alone in frame  -> screen goes grayscale
 *  - Anyone else's face detected, or no face -> screen returns to color
 */
public class FaceWatchService extends LifecycleService {

    private static final String CHANNEL_ID = "face_watch_channel";
    private static final String TAG = "FaceWatchService";

    private ExecutorService cameraExecutor;
    private FaceDetector detector;
    private ProcessCameraProvider cameraProvider;

    // Enrolled owner "signature": ratio of eye-distance to face-box width.
    // Set via MainActivity.enrollOwnerFace() during a one-time setup step.
    static volatile Float ownerEyeRatio = null;
    private boolean currentlyGray = false;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());

        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        detector = FaceDetection.getClient(options);

        startCamera();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(480, 640))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, analysis);

            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, cameraExecutor);
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    handleDetectedFaces(faces);
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Detection failed", e);
                    imageProxy.close();
                });
    }

    private void handleDetectedFaces(List<Face> faces) {
        boolean shouldBeGray;

        if (faces.size() == 1 && ownerEyeRatio != null && matchesOwner(faces.get(0))) {
            // Exactly one face, and it matches the enrolled owner ratio
            shouldBeGray = true;
        } else {
            // No face, more than one face, or a non-matching face
            shouldBeGray = false;
        }

        if (shouldBeGray != currentlyGray) {
            currentlyGray = shouldBeGray;
            if (shouldBeGray) {
                GrayscaleController.enableGrayscale(this);
            } else {
                GrayscaleController.disableGrayscale(this);
            }
        }
    }

    /** Very simple geometric check: compares eye-distance / face-width ratio. */
    private boolean matchesOwner(Face face) {
        if (face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE) == null
                || face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE) == null) {
            return false;
        }
        float leftX = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)
                .getPosition().x;
        float rightX = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)
                .getPosition().x;
        float eyeDistance = Math.abs(leftX - rightX);
        float faceWidth = face.getBoundingBox().width();
        if (faceWidth == 0) return false;

        float ratio = eyeDistance / faceWidth;
        return Math.abs(ratio - ownerEyeRatio) < 0.06f; // tolerance
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Face Watch", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Face Grayscale active")
                .setContentText("Watching for your face to toggle grayscale")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) cameraProvider.unbindAll();
        cameraExecutor.shutdown();
        GrayscaleController.disableGrayscale(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null;
    }
}
