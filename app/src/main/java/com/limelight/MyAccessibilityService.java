package com.limelight;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class MyAccessibilityService extends AccessibilityService  {

    private static boolean exceptKey;
    private static boolean threeDrag;
    private static boolean aaa;

    public static boolean spenMouseMode=false;
    public static boolean spenAiractionPointerMode = false;
    public static boolean spenGuestureMode =false;

    public static boolean spenMontion = false;
    public static float modifieraaa = 0;

    public static boolean perfOverTogle = true;

    public static short airActionMode = 0;


    //s펜 관련 변수
    public static boolean spenDebugTogle = false;
    @Override
    public void onCreate() {
    }

    @Override
    public void onServiceConnected() {
        Toast.makeText(this.getApplicationContext(),"키보드 추적 서비스 켜짐", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {

        if (event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_RIGHT){

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if(modifieraaa == 0 ||modifieraaa == 2 ||modifieraaa == 4 || modifieraaa == 6 ||
                        modifieraaa == 8 ||modifieraaa == 10 ||modifieraaa == 14){
                    modifieraaa = modifieraaa+1;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if(modifieraaa == 1 ||modifieraaa == 3 ||modifieraaa == 5 || modifieraaa == 7 ||
                        modifieraaa == 9 ||modifieraaa == 11 ||modifieraaa == 15){
                    modifieraaa = modifieraaa -1;
                }
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_CTRL_RIGHT ||
                event.getKeyCode() == KeyEvent.KEYCODE_CTRL_LEFT){

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if(modifieraaa == 0 ||modifieraaa == 1 ||modifieraaa == 4 ||modifieraaa == 5 ||
                        modifieraaa == 8 || modifieraaa == 9 ||modifieraaa == 12 ||modifieraaa == 13){
                    modifieraaa =modifieraaa+ 2;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {

                if(modifieraaa == 2 || modifieraaa == 3 ||modifieraaa == 6 ||modifieraaa == 7 ||
                        modifieraaa == 10 || modifieraaa == 11 ||modifieraaa == 14 ||modifieraaa == 15){
                    modifieraaa = modifieraaa-2;
                }
            }

        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_ALT_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_ALT_RIGHT){
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if(modifieraaa == 0 ||modifieraaa == 1 ||modifieraaa == 2 ||modifieraaa == 3 ||
                            modifieraaa == 8 || modifieraaa == 9 ||modifieraaa == 10 ||modifieraaa == 11){
                        modifieraaa = modifieraaa+ 4;
                    }
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if(modifieraaa == 4 ||modifieraaa == 5 ||modifieraaa == 6 ||modifieraaa == 7 ||
                            modifieraaa == 12 || modifieraaa == 13 ||modifieraaa == 11 ||modifieraaa == 15){
                        modifieraaa = modifieraaa- 4;
                    }
                }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_META_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_META_RIGHT){

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if(modifieraaa >= 0 && modifieraaa <= 7){
                    modifieraaa =modifieraaa+ 8;
                }
            }

            if (event.getAction() == KeyEvent.ACTION_UP) {
                if(modifieraaa >= 8 && modifieraaa <= 15 ){
                    modifieraaa =modifieraaa- 8;
                }
            }

        }

        if(Game.KeyboardServiceState == true) {
            if (event.getKeyCode() == 220 || event.getKeyCode() == 221 ||
                    event.getKeyCode() == 164 || event.getKeyCode() == 25 ||
                    event.getKeyCode() == 24 )
            { return false; }
            else  {

                //fn+f10
                if(event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS){
                    if (event.getAction() == KeyEvent.ACTION_DOWN){
                        if(!spenDebugTogle){
                            spenDebugTogle=true;
                            Game g = new Game();
                            g.debugToolOverlay(event);
                        }
                        else {
                            spenDebugTogle=false;
                            Game g = new Game();
                            g.debugToolOverlay(event);
                        }
                    }
                }
                //fn+f11
                if(event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){
                    if (event.getAction() == KeyEvent.ACTION_DOWN){
                        if (!spenAiractionPointerMode) {
                            Toast.makeText(getApplicationContext(), "에어액션 포인터 모드가 켜졌습니다.", Toast.LENGTH_SHORT).show();
                            airActionMode = 1;
                        } else {
                            Toast.makeText(getApplicationContext(), "에어액션 포인터 모드가 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                            airActionMode = 0;
                        }
                        System.out.println("파인더 키");
                    }
                    return true;
                }
                //fn+f12
                if(event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT){
                    if (event.getAction() == KeyEvent.ACTION_DOWN){
                        if(!spenMouseMode){
                            airActionMode = 2;
                            Toast.makeText(getApplicationContext(), "에어액션 마우스 모드가 켜졌습니다.", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            airActionMode = 0;
                            Toast.makeText(getApplicationContext(), "에어액션 마우스 모드가 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                //f13
                if (event.getKeyCode() == 1064) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (airActionMode !=1) {
                            airActionMode = 1;
                            Toast.makeText(getApplicationContext(), "에어액션 포인터 모드가 켜졌습니다.", Toast.LENGTH_SHORT).show();
                        } else {
                            airActionMode = 0;
                            Toast.makeText(getApplicationContext(), "에어액션 포인터 모드가 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                //f14
                if (event.getKeyCode() == 120 && event.getAction()==KeyEvent.ACTION_DOWN) {
                    if(!perfOverTogle){
                        perfOverTogle=true;
                        Game g = new Game();
                        g.debugToolOverlay(event);
                    }
                    else {
                        perfOverTogle=false;
                        Game g = new Game();
                        g.debugToolOverlay(event);
                    }
                }
                //fn+f14
                if (event.getKeyCode() == 1084&& event.getAction()==KeyEvent.ACTION_DOWN) {
                    if(airActionMode !=2){
                        airActionMode = 2;
                        Toast.makeText(getApplicationContext(), "에어액션 마우스 모드가 켜졌습니다.", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        airActionMode = 0;
                        Toast.makeText(getApplicationContext(), "에어액션 마우스 모드가 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
                //fn+f13
                if (event.getKeyCode() == 176) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if(airActionMode !=3){
                            airActionMode =3;
                            Toast.makeText(getApplicationContext(), "에어액션 제스쳐 모드가 켜졌습니다.", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            airActionMode =0;
                            Toast.makeText(getApplicationContext(), "에어액션 제스쳐 모드가 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                        }
                        if (event.getAction() == KeyEvent.ACTION_UP) {
                        }
                    }
                }
                Game.accessibilityKeyEvent(event);

            }
            return true;
        }
        return false;
    }



    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

    }
    @Override
    public void onInterrupt() {
        Toast.makeText(this.getApplicationContext(),"접근성 서비스가 해제되었습니다. 음 문제가 생겼나요?", Toast.LENGTH_SHORT).show();
    }
    @Override
    public boolean onUnbind(Intent inten) {
        return true;
    }

}
