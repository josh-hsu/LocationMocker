package com.mumu.locationmocker.service;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

public class JoystickView extends View {
    private Paint paintBackground;
    private Paint paintHandle;
    private float centerX, centerY; // Joystick center
    private float handleX, handleY; // Handle position
    private float baseRadius, handleRadius; // Radius of base and handle

    private JoystickListener joystickListener;
    private long lastReportTimestampMs;
    private long reportIntervalMs = 500;

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paintBackground = new Paint();
        paintBackground.setColor(Color.WHITE);
        paintBackground.setStyle(Paint.Style.FILL);
        paintBackground.setAlpha(150);

        paintHandle = new Paint();
        paintHandle.setColor(Color.WHITE);
        paintHandle.setStyle(Paint.Style.FILL);
        paintHandle.setAlpha(200);

        lastReportTimestampMs = System.currentTimeMillis();
    }

    public void setJoystickListener(JoystickListener listener) {
        joystickListener = listener;
    }

    public void setReportInterval(long intervalMs) {
        reportIntervalMs = intervalMs;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2.0f;
        centerY = h / 2.0f;
        baseRadius = Math.min(w, h) / 3.0f;
        handleRadius = baseRadius / 3.0f;
        handleX = centerX;
        handleY = centerY;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Draw the joystick base
        canvas.drawCircle(centerX, centerY, baseRadius, paintBackground);
        // Draw the joystick handle
        canvas.drawCircle(handleX, handleY, handleRadius, paintHandle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - centerX;
                float dy = event.getY() - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance < baseRadius) {
                    handleX = event.getX();
                    handleY = event.getY();
                } else {
                    float ratio = baseRadius / distance;
                    handleX = centerX + dx * ratio;
                    handleY = centerY + dy * ratio;
                }

                // Notify listener with normalized values
                if (joystickListener != null) {
                    float xPercent = (handleX - centerX) / baseRadius;
                    float yPercent = (handleY - centerY) / baseRadius;
                    reportPosition(xPercent, yPercent);
                }

                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                handleX = centerX;
                handleY = centerY;

                // Notify listener with centered values (0,0)
                if (joystickListener != null) {
                    reportPosition(0, 0);
                }

                invalidate();
                return true;

            default:
                return false;
        }
    }

    public float getNormalizedX() {
        return (handleX - centerX) / baseRadius;
    }

    public float getNormalizedY() {
        return (handleY - centerY) / baseRadius;
    }

    public float getAngle() {
        return (float) Math.toDegrees(Math.atan2(handleY - centerY, handleX - centerX));
    }

    private void reportPosition(float x, float y) {
        long currentTimestampMs = System.currentTimeMillis();
        if (currentTimestampMs - lastReportTimestampMs > reportIntervalMs) {
            joystickListener.onJoystickMoved(x, y);
            lastReportTimestampMs = currentTimestampMs;
        }
    }

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent);
    }
}
