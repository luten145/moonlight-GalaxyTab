package com.limelight;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class MyAccessibilityService extends AccessibilityService  {

    // 접근성 서비스 입니다.
    // 시스템에 영향을 줄 수 있으므로 최소한의 코드만 실행해야 합니다.

    public static boolean accessibilityKeyListening = false;
    @Override
    public void onServiceConnected() {
        Toast.makeText(this.getApplicationContext(),"키보드 추적 서비스 켜짐", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onInterrupt() {
        Toast.makeText(this.getApplicationContext(),"접근성 서비스가 해제되었습니다. 음 문제가 생겼나요?", Toast.LENGTH_SHORT).show();
    }
    @Override
    public boolean onUnbind(Intent inten) {
        return true;
    }


    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (accessibilityKeyListening) {
            Game g = new Game();
            return g.accessibilityKeyManager(event);
        } else {
            System.out.println("KeyOff");
            return false;
        }
    }
}
