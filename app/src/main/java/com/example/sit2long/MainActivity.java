package com.example.sit2long;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String CHANNEL_ID = "activity_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SITTING_THRESHOLD_MS = 30 * 60 * 1000; // 30 minutes
    private static final String TRANSITIONS_RECEIVER_ACTION = "com.example.sit2long.TRANSITIONS_RECEIVER_ACTION";
    private static final int REQUEST_ACTIVITY_RECOGNITION_PERMISSION = 45;

    // private TextView activityTextView;
    private TextView timerTextView;
    private Button startButton;
    private PendingIntent pendingIntent;
    private ActivityRecognitionClient activityRecognitionClient;
    private ActivityTransitionReceiver receiver;
    private long sittingStartTime = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isTracking = false;
    private String currentActivity = "Unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerTextView = findViewById(R.id.timerTextView);
        startButton = findViewById(R.id.startButton);

        createNotificationChannel();

        activityRecognitionClient = ActivityRecognition.getClient(this);

        receiver = new ActivityTransitionReceiver();
        IntentFilter filter = new IntentFilter(TRANSITIONS_RECEIVER_ACTION);
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        Intent intent = new Intent(TRANSITIONS_RECEIVER_ACTION);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTimer();
                handler.postDelayed(this, 1000);
            }
        };

        startButton.setOnClickListener(v -> {
            if (isTracking) {
                stopTracking();
            } else {
                startTracking();
            }
        });
    }

    private void startTracking() {
        if (checkPermissions()) {
            List<ActivityTransition> transitions = new ArrayList<>();

            // Monitor transitions for sitting (in vehicle is considered sitting)
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.STILL)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.STILL)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build());
            // Add transitions for walking, running, etc.
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.WALKING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.RUNNING)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());

            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions();

            activityRecognitionClient
                    .requestActivityTransitionUpdates(request, pendingIntent)
                    .addOnSuccessListener(aVoid -> {
                        isTracking = true;
                        startButton.setText("Stop Tracking");
                        Toast.makeText(MainActivity.this, "Activity tracking started", Toast.LENGTH_SHORT).show();
                        sittingStartTime = 0;
                        handler.post(timerRunnable);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Failed to start activity tracking", Toast.LENGTH_SHORT).show();
                    });
        } else {
            requestPermissions();
        }
    }

    private void stopTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
            return;

        activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener(aVoid -> {
                    isTracking = false;
                    startButton.setText("Start Tracking");
                    handler.removeCallbacks(timerRunnable);
                    //activityTextView.setText("Activity: Not tracking");
                    timerTextView.setText("Time Sitting: 00 : 00 : 00");
                    Toast.makeText(MainActivity.this, "Activity tracking stopped", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Failed to stop activity tracking", Toast.LENGTH_SHORT).show();
                });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    REQUEST_ACTIVITY_RECOGNITION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ACTIVITY_RECOGNITION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTracking();
            } else {
                Toast.makeText(this, "Permission denied. Cannot track activities.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateTimer() {
        if (sittingStartTime > 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - sittingStartTime;

            // Format time as HH:MM:SS
            int hours = (int) (elapsedTime / (1000 * 60 * 60));
            int minutes = (int) (elapsedTime / (1000 * 60)) % 60;
            int seconds = (int) (elapsedTime / 1000) % 60;
            String timeString = String.format("%02d : %02d : %02d", hours, minutes, seconds);
            timerTextView.setText("Time Sitting: " + timeString);

            // Check if sitting threshold has been reached
            if (elapsedTime >= SITTING_THRESHOLD_MS) {
                sendNotification();
                // Reset timer after notification
                sittingStartTime = currentTime;
            }
        } else {
            timerTextView.setText("Time Sitting: 00 : 00 : 00");
        }
    }

    private void sendNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Activity Monitor")
                .setContentText("You've been sitting for too long. Time to move!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Activity Monitor Channel";
            String description = "Channel for Activity Monitor notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister receiver
        if (receiver != null) {
            unregisterReceiver(receiver);
        }

        // Remove callbacks
        if (handler != null) {
            handler.removeCallbacks(timerRunnable);
        }

        // Stop tracking if active
        if (isTracking) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED)
                return;

            activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent);
        }
    }

    public class ActivityTransitionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
                for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                    handleActivityTransition(event);
                }
            }
        }

        private void handleActivityTransition(ActivityTransitionEvent event) {
            int activityType = event.getActivityType();
            int transitionType = event.getTransitionType();

            // Convert activity type to string
            String activityString = getActivityString(activityType);
            currentActivity = activityString;

//            // Update UI with current activity
//            runOnUiThread(() -> {
//                activityTextView.setText("Activity: " + activityString);
//            });

            if ((activityType == DetectedActivity.STILL || activityType == DetectedActivity.IN_VEHICLE)
                    && transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            {
                sittingStartTime = System.currentTimeMillis();
            }
            else if (transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            {

                sittingStartTime = 0;
            }
        }

        private String getActivityString(int activityType) {
            switch (activityType) {
                case DetectedActivity.STILL:
                    return "Sitting";
                case DetectedActivity.WALKING:
                    return "Walking";
                case DetectedActivity.RUNNING:
                    return "Running";
                case DetectedActivity.IN_VEHICLE:
                    return "In Vehicle";
                case DetectedActivity.ON_BICYCLE:
                    return "Cycling";
                case DetectedActivity.ON_FOOT:
                    return "On Foot";
                case DetectedActivity.TILTING:
                    return "Tilting";
                default:
                    return "Unknown";
            }
        }
    }
}