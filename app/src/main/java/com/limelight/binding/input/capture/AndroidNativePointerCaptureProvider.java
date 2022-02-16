package com.limelight.binding.input.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;

import com.limelight.Game;


// We extend AndroidPointerIconCaptureProvider because we want to also get the
// pointer icon hiding behavior over our stream view just in case pointer capture
// is unavailable on this system (ex: DeX, ChromeOS)
@TargetApi(Build.VERSION_CODES.O)
public class AndroidNativePointerCaptureProvider extends AndroidPointerIconCaptureProvider {
    private View targetView;
    private Context context;

    public AndroidNativePointerCaptureProvider(Activity activity, View targetView) {
        super(activity, targetView);
        this.targetView = targetView;
        this.context = activity;
    }

    public static boolean isCaptureProviderSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    @Override
    public void enableCapture() {
        super.enableCapture();
        targetView.requestPointerCapture();
    }

    @Override
    public void disableCapture() {
        super.disableCapture();
        targetView.releasePointerCapture();
    }

    public int switchX ;
    public int switchRELATIVEX ;
    public int switchY ;
    public int switchRELATIVEY ;
    public float reversalRELATIVEY;

    @Override
    public boolean eventHasRelativeMouseAxes(MotionEvent event) {
        // SOURCE_MOUSE_RELATIVE is how SOURCE_MOUSE appears when our view has pointer capture.
        // SOURCE_TOUCHPAD will have relative axes populated iff our view has pointer capture.
        // See https://developer.android.com/reference/android/view/View#requestPointerCapture()
        int eventSource = event.getSource();
        return eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                (eventSource == InputDevice.SOURCE_TOUCHPAD && targetView.hasPointerCapture());
    }

    @Override
    public float getRelativeAxisX(MotionEvent event) {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                targetView.releasePointerCapture();
                targetView.setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW));
            }
        }

        if (event.getSource() == 1048584){
            switchX = MotionEvent.AXIS_Y ;
            switchRELATIVEX = MotionEvent.AXIS_RELATIVE_Y;
        }
        else {
            switchX = MotionEvent.AXIS_X ;
            switchRELATIVEX = MotionEvent.AXIS_RELATIVE_X;
        }
        int axis = (event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE) ?
                switchX : switchRELATIVEX ;
        float x = event.getAxisValue(axis);
        for (int i = 0; i < event.getHistorySize(); i++) {
            x += event.getHistoricalAxisValue(axis, i);
        }

        return x;
    }

    @Override
    public float getRelativeAxisY(MotionEvent event) {
        if (event.getToolType(0) == 2 ){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                targetView.releasePointerCapture();
            }
        }

        if (event.getSource() == 1048584){
            switchY = MotionEvent.AXIS_X ;
            switchRELATIVEY = MotionEvent.AXIS_RELATIVE_X;
            reversalRELATIVEY = -event.getAxisValue(switchRELATIVEY);
        }
        else {
            switchY = MotionEvent.AXIS_Y ;
            switchRELATIVEY = MotionEvent.AXIS_RELATIVE_Y;
            reversalRELATIVEY = event.getAxisValue(switchRELATIVEY);
        }
        int axis = (event.getSource() == InputDevice.SOURCE_MOUSE_RELATIVE) ? switchY : switchRELATIVEY;
        float y = reversalRELATIVEY;
        for (int i = 0; i < event.getHistorySize(); i++) {
            y += event.getHistoricalAxisValue(axis, i);
        }
        return y;
    }
}
