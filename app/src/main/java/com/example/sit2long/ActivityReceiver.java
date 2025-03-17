//package com.example.sit2long;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import com.google.android.gms.location.ActivityRecognitionResult;
//import com.google.android.gms.location.DetectedActivity;
//
//public class ActivityReceiver extends BroadcastReceiver
//{
//    private static final String TAG = "ActivityReceiver";
//
//    @Override
//    public void onReceive(Context context, Intent intent)
//    {
//        if (ActivityRecognitionResult.hasResult(intent))
//        {
//            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
//            DetectedActivity mostProbableActivity = result.getMostProbableActivity();
//            int activityType = mostProbableActivity.getType();
//            handleActivity(context, activityType);
//        }
//    }
//
//    private void handleActivity(Context context, int activityType)
//    {
//        MainActivity activity = (MainActivity)context;
//
//        switch (activityType)
//        {
//            case DetectedActivity.STILL:
//                activity.startCounter();
//                break;
//            case DetectedActivity.WALKING:
//            case DetectedActivity.RUNNING:
//                activity.stopCounter();
//                break;
//        }
//    }
//}
