package com.limelight;

import static android.content.ContentValues.TAG;
import static com.limelight.nvstream.input.KeyboardPacket.MODIFIER_WIN;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.capture.InputCaptureManager;
import com.limelight.binding.input.capture.InputCaptureProvider;
import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.driver.UsbDriverService;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.binding.input.touch.TouchContext;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.GameGestures;
import com.limelight.ui.StreamView;
import com.limelight.utils.Dialog;
import com.limelight.utils.NetHelper;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Layout;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowInsetsAnimationControlListener;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ThemedSpinnerAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.samsung.android.sdk.penremote.AirMotionEvent;
import com.samsung.android.sdk.penremote.ButtonEvent;
import com.samsung.android.sdk.penremote.SpenEvent;
import com.samsung.android.sdk.penremote.SpenEventListener;
import com.samsung.android.sdk.penremote.SpenRemote;
import com.samsung.android.sdk.penremote.SpenUnit;
import com.samsung.android.sdk.penremote.SpenUnitManager;


//1.클래스 시작
public class Game extends Activity implements SurfaceHolder.Callback,
        OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
        OnSystemUiVisibilityChangeListener, GameGestures, StreamView.InputCallbacks,
        PerfOverlayListener
{
    //2.private_static 변수
    private static NvConnection conn;

    private static final int REFERENCE_HORIZ_RES = 1280;
    private static final int REFERENCE_VERT_RES = 720;

    // Only 2 touches are supported
    private static final int THREE_FINGER_TAP_THRESHOLD = 300;

    //3.public_static 변수
    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_PC_UUID = "UUID";
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_APP_HDR = "HDR";
    public static final String EXTRA_SERVER_CERT = "ServerCert";

    public static Timer spenMotionOffTimer;
    public static Timer spenDelay;

    private static SpenRemote mSpenRemote;
    private static SpenUnitManager mSpenUnitManager;


    public static boolean BUTTONSTATEDOWN;
    public static boolean BUTTONSTATEUP;
    public static boolean KeyboardServiceState = false;
    public static boolean spenMotionsend = false;

    public static float motionDeltaX;
    public static float motionDeltaY;

    //private 변수
    private long threeFingerDownTime = 0;
    private final TouchContext[] touchContextMap = new TouchContext[2];
    private ControllerHandler controllerHandler;
    private VirtualController virtualController;
    private PreferenceConfiguration prefConfig;
    private SharedPreferences tombstonePrefs;
    private SpinnerDialog spinner;
    private InputCaptureProvider inputCaptureProvider;
    private ShortcutHelper shortcutHelper;
    private MediaCodecDecoderRenderer decoderRenderer;
    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;
    private StreamView streamView;

    private TextView notificationOverlayView;

    private Timer tapDownTimer;

    //4.private_bool변스
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    private boolean connected = false;
    private boolean autoEnterPip = false;
    private boolean surfaceCreated = false;
    private boolean attemptedConnection = false;
    private boolean padMoveStatus = false;
    private boolean padTimer = false;
    private boolean grabbedInput = true;
    private boolean grabComboDown = false;
    private boolean reportedCrash;
    private boolean isHidingOverlays;
    private boolean connectedToUsbDriverService = false;

    //5.private_float 변수
    private float lastAbsTouchUpX, lastAbsTouchUpY;
    private float lastAbsTouchDownX, lastAbsTouchDownY;
    private float dragRight;
    private float dragLeft;
    private float dragUp;
    private float dragDown;
    private float lastPadDragStartY ;
    private float lastPadDragStartX ;

    private long lastAbsTouchUpTime = 0;
    private long lastAbsTouchDownTime = 0;

    private int lastButtonState = 0;
    private int modifierFlags = 0;
    private int requestedNotificationOverlayVisibility = View.GONE;
    private int moveDirection = 0;

    //트랙패드 관련 변수
    public boolean trackPadTouchDownStatus = false;
    private static boolean mouseDownStatus = false;
    private float lastPadTouchDownX;
    private float lastPadTouchDownY;
    private float lastPadTouchUpY ;
    private float lastPadTouchUpX ;
    private float movePointer ;
    private float lastPadDragX;
    private float lastPadDragY;
    private float trackPadThreeFinger=0;
    private boolean altTabSwitch=false;
    private boolean trackPadfirstMove = false;
    private boolean trackPadScrollButton =false;

    public static float touchUpTime;
    public static Timer mouseClick;
    public float dotToDotLength;
    public float PadScroll;
    public float PadScrollY;
    public float zoom;
    public static float spenButtonDownTime;

    public boolean stop;
    public static boolean touchQueue =false;
    public boolean landMode = false;

    public boolean scrollQueue = false;
    public boolean scrollMode = true;
    public boolean pinchMode = false;

    //s펜 관련 변수
    private static boolean mIsMotionListening = false;
    private static TextView spenDebug;
    private static Timer spenDebugUpdateTimer;

    public static boolean spenConnectionStatus;
    public static boolean spenButtonStatus;
    public static int spenState;

    //오버레이 관련 변수
    public TextView norchRight;
    public TextView norchLeft;
    public X509Certificate setServerCert;

    public static long TotalTx;
    public static long TotalRx;

    public int lastButtonDown;

    //액티브 기술 관련 변수
    private static final String refershRateMode = "refresh_rate_mode";
    public int setBitrate;

    public float suggestBitrate ;

    public Button action1;
    public Button action2;

    //디코더 퍼포먼스 정보
    public static int gResolutionWidth;
    public static int gResolutionHeight;
    public static short gTotalFps;
    public static short gReceivedFps;
    public static short gRenderedFps;
    public static int gPing;
    public static int gVariance;
    public static short gDecodeTime;
    public static float gPacketLossPercentage;

    public  ContentResolver contentResolver ;
    public Context gameContext;

    public Timer autoReconnectTimer;
    public Timer autoReconnectPoorTimer;

    public Timer autoBackgroundTimer;

    public float moveTimer = 0 ;

    public boolean overlaySwitch = false;

    public View imagetest;
    public ImageView imagetest2;

    public boolean SurfaceViewState = false;
    public boolean pixelCopyState = false;

    public static boolean ONEClick;

    //에어액션 관련 변수
    public static short airActionMode = 0;


    //트랙패드 관련 변수
    public static boolean trackPadMove = true;

    //USB 연결상태 확인 함수
    private ServiceConnection usbDriverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            UsbDriverService.UsbDriverBinder binder = (UsbDriverService.UsbDriverBinder) iBinder;
            binder.setListener(controllerHandler);
            connectedToUsbDriverService = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connectedToUsbDriverService = false;
        }
    };

    //onCreate 함수
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //제목 표시줄 제거
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_game);

        Settings.Secure.putInt(this.getContentResolver(),refershRateMode,1);
        gameContext= this;
        //7-1.접근성 키 입력 제어 권한 활성화
        KeyboardServiceState = true ;

        //언어 설정
        UiHelper.setLocale(this);

        //7-2.S펜 에어액션 초기화
        mSpenRemote = SpenRemote.getInstance();
        mSpenRemote.setConnectionStateChangeListener(new SpenRemote.ConnectionStateChangeListener() {
            @Override
            public void onChange(int i) {

            }
        });
        checkSdkInfo();
        //s펜 연결
        connectToSpenRemote();


        // 전체화면 모드 전환
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // immersive mode 선언  전체화면 모드
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        //해석되지 않은 코드
        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);
        // Change volume button behavior
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Inflate the content

        //대기 화면 시작
        if(!ServerHelper.restart) {
            spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                    getResources().getString(R.string.conn_establishing_msg), true);
        }
        else {
            Toast.makeText(this, "네트워크가 불안정하여 비트레이트를 조정했습니다.",
                    Toast.LENGTH_LONG).show();
        }

        //환경설정 값 가져오기
        prefConfig = PreferenceConfiguration.readPreferences(this);
        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);
        if (prefConfig.stretchVideo || shouldIgnoreInsetsForResolution(prefConfig.width, prefConfig.height)) {
            // Allow the activity to layout under notches if the fill-screen option
            // was turned on by the user or it's a full-screen native resolution
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }

        notificationOverlayView = findViewById(R.id.notificationOverlay);
        norchRight = findViewById(R.id.norchRight);
        norchLeft = findViewById(R.id.norchLeft);
        spenDebug = findViewById(R.id.spenDebug);
        streamView = findViewById(R.id.surfaceView);
        action1 = findViewById(R.id.action1);
        action2 = findViewById(R.id.action2);
        imagetest = findViewById(R.id.imagetest);
        imagetest2 = findViewById(R.id.imim);

        Bitmap image = AppView.reBitrate;

        imagetest2.setImageBitmap(image);
        imagetest2.setVisibility(View.VISIBLE);

        if(Build.MODEL.equals("SM-T975N")){
            imagetest.setVisibility(View.GONE);
        }

        streamView.setOnGenericMotionListener(this);
        streamView.setOnTouchListener(this);
        streamView.setInputCallbacks(this);
        action1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Reconnect();
            }
        });

        action2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!overlaySwitch) {
                    overlaySwitch = true;
                    norchLeft.setVisibility(View.GONE);
                    norchRight.setVisibility(View.GONE);
                } else {
                    overlaySwitch = false;
                    norchLeft.setVisibility(View.VISIBLE);
                    norchRight.setVisibility(View.VISIBLE);
                }
            }
        });

        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this, this);

        //포인터 캡쳐 관련 함수
        streamView.setFocusable(true);
        streamView.setDefaultFocusHighlightEnabled(false);
        streamView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
            @Override
            public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                return pointerCaptureInputManager(view, motionEvent);
            }
        });

        //네트워크 관련 코드
        // 데이터 연결 경고
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connMgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }
        // Wifi 설정 (고성능 모드로)
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock");
        highPerfWifiLock.setReferenceCounted(false);
        highPerfWifiLock.acquire();

        TotalTx = TrafficStats.getTotalTxBytes();
        TotalRx =  TrafficStats.getTotalRxBytes();

        // Wifi 설정 (Q버전 이상 로우 레이턴시 모드 활성화)
        // wifi검색 빈도가 줄어듭니다
        // 신호가 가장 센 wifi로 자동 전환 기능이 작동하지 않습니다.
        // wifi를 통한 위치 정확도가 떨어집니다.
        lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock");
        lowLatencyWifiLock.setReferenceCounted(false);
        lowLatencyWifiLock.acquire();

        // 컴퓨터 정보 가져오기 코드
        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        String appName = Game.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        String uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        String pcName = Game.this.getIntent().getStringExtra(EXTRA_PC_NAME);
        boolean appSupportsHdr = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        //인증키 관련 코드
        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        setServerCert = serverCert;

        //앱 아이디를 알수 없을때 탈출
        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            //return 함수를 빠져나감 즉 이 이하에 있는 코드줄은 작동하지 않고 중단됨
            // + break는 for문만 빠져나가고 그 아래에 있는 함수는 작동함
            return;
        }

        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = uuid;
        shortcutHelper = new ShortcutHelper(this);
        // 게임이 실행중인지 확인
        shortcutHelper.reportComputerShortcutUsed(computer);
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, new NvApp(appName, appId, appSupportsHdr));
        }

        //알 수 없는 코드
        // Initialize the MediaCodec helper before creating the decoder
        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        MediaCodecHelper.initialize(this, glPrefs.glRenderer);

        // HDR 관련 코드
        // HDR 설정을 활성화 했는지 확인
        boolean willStreamHdr = false;
        if (prefConfig.enableHdr) {
            // Start our HDR checklist
            Display display = getWindowManager().getDefaultDisplay();
            Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();
            // 디스플레이가 HDR10과 호환되는지 확인
            if (hdrCaps != null) {
                for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                    if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                        willStreamHdr = true;
                        break;
                    }
                }
            }
            if (!willStreamHdr) {
                Toast.makeText(this, "이 디스플레이는 Hdr10을 지원하지 않습니다.", Toast.LENGTH_LONG).show();
            }
        }

        // 사용자가 성능 통계 오버레이를 활성화했는지 확인
        // 사용자가 성능 통계 활성화 코드

        //디코더 렌더러 정의
        decoderRenderer = new MediaCodecDecoderRenderer(
                this,
                prefConfig,
                new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        // The MediaCodec instance is going down due to a crash
                        // let's tell the user something when they open the app again

                        // We must use commit because the app will crash when we return from this function
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1).commit();
                        reportedCrash = true;
                    }
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered(),
                willStreamHdr,
                glPrefs.glRenderer,
                this);

        // Don't stream HDR if the decoder can't support it
        if (willStreamHdr && !decoderRenderer.isHevcMain10Hdr10Supported()) {
            willStreamHdr = false;
            Toast.makeText(this, "Decoder does not support HEVC Main10HDR10", Toast.LENGTH_LONG).show();
        }

        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FORCE_H265_ON && !decoderRenderer.isHevcSupported()) {
            Toast.makeText(this, "No HEVC decoder found.\nFalling back to H.264.", Toast.LENGTH_LONG).show();
        }

        int gamepadMask = ControllerHandler.getAttachedControllerMask(this);
        if (!prefConfig.multiController) {
            // Always set gamepad 1 present for when multi-controller is
            // disabled for games that don't properly support detection
            // of gamepads removed and replugged at runtime.
            gamepadMask = 1;
        }
        if (prefConfig.onscreenController) {
            // If we're using OSC, always set at least gamepad 1.
            gamepadMask |= 1;
        }

        // Set to the optimal mode for streaming
        float displayRefreshRate = prepareDisplayForRendering();
        LimeLog.info("Display refresh rate: "+displayRefreshRate);

        // HACK: Despite many efforts to ensure low latency consistent frame
        // delivery, the best non-lossy mechanism is to buffer 1 extra frame
        // in the output pipeline. Android does some buffering on its end
        // in SurfaceFlinger and it's difficult (impossible?) to inspect
        // the precise state of the buffer queue to the screen after we
        // release a frame for rendering.
        //
        // Since buffering a frame adds latency and we are primarily a
        // latency-optimized client, rather than one designed for picture-perfect
        // accuracy, we will synthetically induce a negative pressure on the display
        // output pipeline by driving the decoder input pipeline under the speed
        // that the display can refresh. This ensures a constant negative pressure
        // to keep latency down but does induce a periodic frame loss. However, this
        // periodic frame loss is *way* less than what we'd already get in Marshmallow's
        // display pipeline where frames are dropped outside of our control if they land
        // on the same V-sync.
        //
        // Hopefully, we can get rid of this once someone comes up with a better way
        // to track the state of the pipeline and time frames.
        int roundedRefreshRate = Math.round(displayRefreshRate);
        int chosenFrameRate = prefConfig.fps;
        if (!prefConfig.disableFrameDrop || prefConfig.unlockFps) {
            if (Build.DEVICE.equals("coral") || Build.DEVICE.equals("flame")) {
                // HACK: Pixel 4 (XL) ignores the preferred display mode and lowers refresh rate,
                // causing frame pacing issues. See https://issuetracker.google.com/issues/143401475
                // To work around this, use frame drop mode if we want to stream at >= 60 FPS.
                if (prefConfig.fps >= 60) {
                    LimeLog.info("Using Pixel 4 rendering hack");
                    decoderRenderer.enableLegacyFrameDropRendering();
                }
            }
            else if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.unlockFps) {
                    // Use frame drops when rendering above the screen frame rate
                    decoderRenderer.enableLegacyFrameDropRendering();
                    LimeLog.info("Using drop mode for FPS > Hz");
                } else if (roundedRefreshRate <= 49) {
                    // Let's avoid clearly bogus refresh rates and fall back to legacy rendering
                    decoderRenderer.enableLegacyFrameDropRendering();
                    LimeLog.info("Bogus refresh rate: " + roundedRefreshRate);
                }
                // HACK: Avoid crashing on some MTK devices
                else if (decoderRenderer.isBlacklistedForFrameRate(roundedRefreshRate - 1)) {
                    // Use the old rendering strategy on these broken devices
                    decoderRenderer.enableLegacyFrameDropRendering();
                } else {
                    chosenFrameRate = roundedRefreshRate - 1;
                    LimeLog.info("Adjusting FPS target for screen to " + chosenFrameRate);
                }
            }
        }

        boolean vpnActive = NetHelper.isActiveNetworkVpn(this);
        if (vpnActive) {
            LimeLog.info("Detected active network is a VPN");
        }

        autoBitrate();

        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(controllerHandler, null);

        // 터치 관련 값 설정
        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
            }
            else {
                touchContextMap[i] = new RelativeTouchContext(conn, i,
                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                        streamView);
            }
        }

        // 일관성을 보장하기 위해 N+에서 지속 성능 모드를 사용합니다. (클럭 낮춘상태에서 안정적 실행 가능)
        getWindow().setSustainedPerformanceMode(true);

        if (prefConfig.onscreenController) {
            // 가상 화면 컨트롤러 생성
            virtualController = new VirtualController(controllerHandler,
                    (FrameLayout)streamView.getParent(),
                    this);
            virtualController.refreshLayout();
            virtualController.show();
        }

        if (prefConfig.usbDriver) {
            // USB 드라이버 시작
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }

        if (!decoderRenderer.isAvcSupported()) {
            if (spinner != null) {
                spinner.dismiss();
                spinner = null;
            }

            // If we can't find an AVC decoder, we can't proceed
            Dialog.displayDialog(this, getResources().getString(R.string.conn_error_title),
                    "This device or ROM doesn't support hardware accelerated H.264 playback.",
                    true);
            return;
        }



        activeManager();

        Game.this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // 표면이 생성되면 연결이 시작됩니다.
        ServerHelper.restart = false;
        overlayBackground();
        streamView.getHolder().addCallback(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    //활동이 다시 시작됬을때 호출함수
    @Override
    protected void onResume() {
        super.onResume();
    }

    //화면 변화 시 호출 함수
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (virtualController != null) {
            // Refresh layout of OSC for possible new screen size
            virtualController.refreshLayout();
        }
        // Hide on-screen overlays in PiP mode
        if (isInPictureInPictureMode()) {
            isHidingOverlays = true;
            if (virtualController != null) {
                virtualController.hide();
            }
            notificationOverlayView.setVisibility(View.GONE);
        }
        else {
            isHidingOverlays = false;
            // Restore overlays to previous state when leaving PiP
            if (virtualController != null) {
                virtualController.show();
            }
            notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);
        }
    }

    protected void onPause(){
        super.onPause();
    }

    //활동에서 나갈때 때 호출되는 함수
    @Override
    protected void onStop() {
        super.onStop();
        Settings.Secure.putInt(this.getContentResolver(),refershRateMode,1);

        AppView.reBitrate =null;

        ServerHelper.restart = false;

        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        if (virtualController != null) {
            virtualController.hide();
        }

        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();

            if (prefConfig.enableLatencyToast) {
                int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
                int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
                String message = null;
                if (averageEndToEndLat > 0) {
                    message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
                    if (averageDecoderLat > 0) {
                        message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
                    }
                }
                else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    if (videoFormat == MoonBridge.VIDEO_FORMAT_H265_MAIN10) {
                        message += " [HEVC HDR]";
                    }
                    else if (videoFormat == MoonBridge.VIDEO_FORMAT_H265) {
                        message += " [HEVC]";
                    }
                    else if (videoFormat == MoonBridge.VIDEO_FORMAT_H264) {
                        message += " [H.264]";
                    }
                }

                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }
        finish();
    }

    //활동이 소멸될 때 호출되는 함수
    @Override
    protected void onDestroy() {
        super.onDestroy();

        Settings.Secure.putInt(this.getContentResolver(),refershRateMode,1);


        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        surfaceCreated = false;

        if(autoBackgroundTimer != null){
            autoBackgroundTimer.cancel();
            autoBackgroundTimer = null;
        }

        if (virtualController != null) {
            virtualController.hide();
        }

        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();

            if (prefConfig.enableLatencyToast) {
                int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
                int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
                String message = null;
                if (averageEndToEndLat > 0) {
                    message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
                    if (averageDecoderLat > 0) {
                        message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
                    }
                }
                else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    if (videoFormat == MoonBridge.VIDEO_FORMAT_H265_MAIN10) {
                        message += " [HEVC HDR]";
                    }
                    else if (videoFormat == MoonBridge.VIDEO_FORMAT_H265) {
                        message += " [HEVC]";
                    }
                    else if (videoFormat == MoonBridge.VIDEO_FORMAT_H264) {
                        message += " [H.264]";
                    }
                }

                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }
        Settings.Secure.putInt(this.getContentResolver(),refershRateMode,1);

        KeyboardServiceState = false;

        if (controllerHandler != null) {
            InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            inputManager.unregisterInputDeviceListener(controllerHandler);
        }

        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }

        if (connectedToUsbDriverService) {
            // Unbind from the discovery service
            unbindService(usbDriverServiceConnection);
        }
        // Destroy the capture provider
        inputCaptureProvider.destroy();
    }

    //PIP 관련 함수
    private PictureInPictureParams getPictureInPictureParams(boolean autoEnter) {
        PictureInPictureParams.Builder builder =
                new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(prefConfig.width, prefConfig.height))
                        .setSourceRectHint(new Rect(
                                streamView.getLeft(), streamView.getTop(),
                                streamView.getRight(), streamView.getBottom()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter);
            builder.setSeamlessResizeEnabled(true);
        }
        return builder.build();
    }

    //PIP자동 전환 함수
    private void setPipAutoEnter(boolean autoEnter) {
        if (!prefConfig.enablePip) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter));
        }
        else {
            autoEnterPip = autoEnter;
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // PiP is only supported on Oreo and later, and we don't need to manually enter PiP on
        // Android S and later.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (autoEnterPip) {
                try {
                    // This has thrown all sorts of weird exceptions on Samsung devices
                    // running Oreo. Just eat them and close gracefully on leave, rather
                    // than crashing.
                    enterPictureInPictureMode(getPictureInPictureParams(false));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // We can't guarantee the state of modifiers keys which may have
        // lifted while focus was not on us. Clear the modifier state.
        this.modifierFlags = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Capture is lost when focus is lost, so it must be requested again
            // when focus is regained.
            if (inputCaptureProvider.isCapturingEnabled() && hasFocus) {
                // Recapture the pointer if focus was regained. On Android Q,
                // we have to delay a bit before requesting capture because otherwise
                // we'll hit the "requestPointerCapture called for a window that has no focus"
                // error and it will not actually capture the cursor.
                Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        streamView.releasePointerCapture();
                    }
                }, 500);
            }
        }
    }



    //주사율 관련 설정
    private boolean isRefreshRateGoodMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                Math.round(refreshRate) % prefConfig.fps <= 3;
    }

    //해상도 관련 설정
    private boolean shouldIgnoreInsetsForResolution(int width, int height) {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false;
        }

        Display display = getWindowManager().getDefaultDisplay();
        for (Display.Mode candidate : display.getSupportedModes()) {
            // Ignore insets if this is an exact match for the display resolution
            if ((width == candidate.getPhysicalWidth() && height == candidate.getPhysicalHeight()) ||
                    (height == candidate.getPhysicalWidth() && width == candidate.getPhysicalHeight())) {
                return true;
            }
        }
        return false;
    }

    //화면 관련
    private float prepareDisplayForRendering() {
        Display display = getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams windowLayoutParams = getWindow().getAttributes();
        float displayRefreshRate;

        // On M, we can explicitly set the optimal display mode
        Display.Mode bestMode = display.getMode();
        boolean isNativeResolutionStream = PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height);
        boolean refreshRateIsGood = isRefreshRateGoodMatch(bestMode.getRefreshRate());
        for (Display.Mode candidate : display.getSupportedModes()) {
            boolean refreshRateReduced = candidate.getRefreshRate() < bestMode.getRefreshRate();
            boolean resolutionReduced = candidate.getPhysicalWidth() < bestMode.getPhysicalWidth() ||
                    candidate.getPhysicalHeight() < bestMode.getPhysicalHeight();
            boolean resolutionFitsStream = candidate.getPhysicalWidth() >= prefConfig.width &&
                    candidate.getPhysicalHeight() >= prefConfig.height;

            LimeLog.info("Examining display mode: "+candidate.getPhysicalWidth()+"x"+
                    candidate.getPhysicalHeight()+"x"+candidate.getRefreshRate());

            if (candidate.getPhysicalWidth() > 4096 && prefConfig.width <= 4096) {
                // Avoid resolutions options above 4K to be safe
                continue;
            }

            // On non-4K streams, we force the resolution to never change unless it's above
            // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
            // or it's a native resolution stream.
            if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                        display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                    continue;
                }
            }

            // Make sure the resolution doesn't regress unless if it's over 60 FPS
            // where we may need to reduce resolution to achieve the desired refresh rate.
            if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                continue;
            }

            if (refreshRateIsGood) {
                // We have a good matching refresh rate, so we're looking for equal or greater
                // that is also a good matching refresh rate for our stream frame rate.
                if (refreshRateReduced || !isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                    continue;
                }
            }
            else if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                // We didn't have a good match and this match isn't good either, so just don't
                // reduce the refresh rate.
                if (refreshRateReduced) {
                    continue;
                }
            } else {
                // We didn't have a good match and this match is good. Prefer this refresh rate
                // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                // match the frame rate even if the active display mode is 90 Hz.
            }

            bestMode = candidate;
            refreshRateIsGood = isRefreshRateGoodMatch(candidate.getRefreshRate());
        }
        LimeLog.info("Selected display mode: "+bestMode.getPhysicalWidth()+"x"+
                bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());
        windowLayoutParams.preferredDisplayModeId = bestMode.getModeId();
        displayRefreshRate = bestMode.getRefreshRate();

        // Enable HDMI ALLM (game mode) on Android R
        windowLayoutParams.preferMinimalPostProcessing = true;

        // Apply the display mode change
        getWindow().setAttributes(windowLayoutParams);

        // From 4.4 to 5.1 we can't ask for a 4K display mode, so we'll
        // need to hint the OS to provide one.
        boolean aspectRatioMatch = false;

        if (prefConfig.stretchVideo || aspectRatioMatch) {
            // Set the surface to the size of the video
            streamView.getHolder().setFixedSize(prefConfig.width, prefConfig.height);
        }
        else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio((double)prefConfig.width / (double)prefConfig.height);
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate;
        }
        else {
            // Use the actual refresh rate of the display, since the preferred refresh rate or mode
            // may not actually be applied (ex: Pixel 4 with Smooth Display disabled).
            return getWindowManager().getDefaultDisplay().getRefreshRate();
        }
    }



    //시스템 UI 숨김 처리 함수
    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
        @Override
        public void run() {
            // TODO: Do we want to use WindowInsetsController here on R+ instead of
            // SYSTEM_UI_FLAG_IMMERSIVE_STICKY? They seem to do the same thing as of S...

            // In multi-window mode on N+, we need to drop our layout flags or we'll
            // be drawing underneath the system UI.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode()) {
                Game.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            // Use immersive mode on 4.4+ or standard low profile on previous builds
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Game.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            else {
                Game.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    };

    //시스템 ui숨김처리 함수
    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }

    //멀티윈도우 관련 함수
    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        // In multi-window, we don't want to use the full-screen layout
        // flag. It will cause us to collide with the system UI.
        // This function will also be called for PiP so we can cover
        // that case here too.
        if (isInMultiWindowMode) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Disable performance optimizations for foreground
            getWindow().setSustainedPerformanceMode(false);
            decoderRenderer.notifyVideoBackground();
        }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Enable performance optimizations for foreground
            getWindow().setSustainedPerformanceMode(true);
            decoderRenderer.notifyVideoForeground();
        }

        // Correct the system UI visibility flags
        hideSystemUi(50);
    }

    //시스템 ui를 숨길 때 같이 호출되는 함수 인풋 캡쳐 관련함수
    private final Runnable toggleGrab = new Runnable() {
        @Override
        public void run() {
            if (grabbedInput) {
                inputCaptureProvider.disableCapture();
            }
            else {
                inputCaptureProvider.enableCapture();
            }

            grabbedInput = !grabbedInput;
        }
    };

    //------------------------------------------------------------------
    //버튼 다운 입력
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        super.onKeyDown(keyCode, event);
        if (event.getKeyCode() == 24 ||event.getKeyCode() == 25 ){
            return false;
        }else {
            return true;
        }
    }
    //버튼 업 입력
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        super.onKeyUp(keyCode, event);
        if (event.getKeyCode() == 24 ||event.getKeyCode() == 25 ){
            return false;
        }else {
            return true;
        }
    }

    //접근성 버튼 이벤트 수신기

    public static boolean accessibilityKeyEvent(KeyEvent event){

        if (event.getKeyCode() == 220 || event.getKeyCode() == 221 ||
                event.getKeyCode() == 164 || event.getKeyCode() == 25 ||
                event.getKeyCode() == 24 )
        { return false; }
        else {
            keyboardManager(event);
            return true;
        }
    }

    //------------------------------------------------------------------
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(null, event) || super.onGenericMotionEvent(event);
    }
    //모션 입력
    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        //s펜 호버
        return onGenericMotionManager(view, event);
    }
    //------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleMotionEvent(null, event) || super.onTouchEvent(event);
    }
    //터치 입력
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        //s펜 입력,화면터치
        TouchView(event);
        onTouchManager(view, event);
        return handleMotionEvent(view, event);
    }
    //------------------------------------------------------------------

    //에어액션 버튼 이벤트 리스너
    private static SpenEventListener mButtonEventListener = new SpenEventListener() {
        @Override
        public void onEvent(SpenEvent event) {
            ButtonEvent button = new ButtonEvent(event);
            airActionButtonManager(button);
        }
    };

    //에어액션 모션 이벤트 리스너
    private static SpenEventListener mAirMotionEventListener = new SpenEventListener() {
        @Override
        public void onEvent(SpenEvent event) {
            AirMotionEvent airMotion = new AirMotionEvent(event);
            airActionMotionManger(airMotion);
        }
    };

    //------------------------------------------------------------------

    public boolean pointerCaptureInputManager (View view, MotionEvent event){
        switch (event.getToolType(0)){
            case MotionEvent.TOOL_TYPE_MOUSE:
                mouseManager(view, event);
                break;
            case MotionEvent.TOOL_TYPE_FINGER:
                trackPadManager(view, event);
                break;
            case MotionEvent.TOOL_TYPE_STYLUS:
                streamView.releasePointerCapture();
                break;
            default:
        }
        return true;
    }

    public boolean onGenericMotionManager(View view, MotionEvent event){
        switch (event.getToolType(0)){
            case MotionEvent.TOOL_TYPE_MOUSE:
            case MotionEvent.TOOL_TYPE_FINGER:
                streamView.requestPointerCapture();
                break;
            case MotionEvent.TOOL_TYPE_STYLUS:
                stylusManager(view, event);
                break;
            default:
        }
        return true;
    }

    public boolean onTouchManager(View view, MotionEvent event){
        //s펜 터치입력
        switch (event.getToolType(0)){
            case MotionEvent.TOOL_TYPE_STYLUS:
                stylusManager(view, event);
                break;
            default:
        }
        return true;
    }

    public static void airActionButtonManager(ButtonEvent button){
        AirMotionEvent airMotion = null;
        airActionManager(button , airMotion);
    }

    public static void airActionMotionManger(AirMotionEvent airMotion){
        motionDeltaX = airMotion.getDeltaX();
        motionDeltaY = airMotion.getDeltaY();
        motionData();
        ButtonEvent button = null;
        //airActionManager(button , airMotion);
    }

    //------------------------------------------------------------------

    public static void keyboardDownSender(int keycode,byte Combination){
        //키보드 다운 패킷 전송기
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN,
                Combination);
    }

    public static void keyboardUpSender(int keycode,byte Combination){
        //키보드 업 패킷 전송기
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP,Combination );
    }
    //------------------------------------------------------------------
    public static void mousePositionSender (short x, short y, View view){
        conn.sendMousePosition(x,y, (short)view.getWidth(), (short)view.getHeight());
    }
    public static void mouseMoveSender (short DeltaX, short DeltaY){
        conn.sendMouseMove(DeltaX, DeltaY);
    }
    public static void mouseButtonDownSender(byte mouseButton){
        conn.sendMouseButtonDown(mouseButton);
    }
    public static void mouseButtonUpSender (byte mouseButton){
        conn.sendMouseButtonUp(mouseButton);
    }
    public static void mouseScrollSender(byte mouseScroll){
        conn.sendMouseScroll(mouseScroll);
    }

    //------------------------------------------------------------------

    public static void keyboardManager(KeyEvent event){
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            keyboardDownSender(event.getKeyCode(),(byte) MyAccessibilityService.modifieraaa);
        }
        else if(event.getAction() == KeyEvent.ACTION_UP) {
            keyboardUpSender(event.getKeyCode(),(byte) MyAccessibilityService.modifieraaa);
        }
    }

    public void mouseManager(View view, MotionEvent event){
        //움직임 입력
        mouseMoveSender((short) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X),
                (short)event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y));
        //버튼,스크롤 입력
        switch (event.getAction()){
            case  MotionEvent.ACTION_BUTTON_PRESS:
                mouseButtonDownSender(mouseButtonDownManager(view, event));
                break;
            case  MotionEvent.ACTION_BUTTON_RELEASE:
                mouseButtonUpSender(mouseButtonUpManager(view, event));
                break;
            case  MotionEvent.ACTION_SCROLL:
                mouseScrollSender((byte) event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                break;
        }
    }

    //특수한 경우 : 매니저 시스템을 따르지 않음
    public void trackPadManager(View view, MotionEvent event){
        if(trackPadMove){
            mouseMoveSender((short)event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y),
                    (short) -event.getAxisValue(MotionEvent.AXIS_RELATIVE_X));
        }
        trackpadConverter(view,event);
    }

    public static void stylusManager(View view, MotionEvent event){
        mousePositionSender((short) event.getX(),(short) event.getY(),view);
    }

    public static void airActionManager(ButtonEvent button, AirMotionEvent airMotion){

        if (button.getAction() == ButtonEvent.ACTION_DOWN) {
            spenButtonStatus = true;
            if(MyAccessibilityService.airActionMode == 1){
                spenMotionsend = true;
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT),
                        KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L),
                        KeyboardPacket.KEY_DOWN, (byte)0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L),
                        KeyboardPacket.KEY_UP, (byte)0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT),
                        KeyboardPacket.KEY_UP, (byte)0);
                spenMotionConnectOn();
                conn.sendMousePosition((short) 1400, (short) 800, (short) 2960, (short) 1820);
            }
            //마우스 모드
            if(MyAccessibilityService.airActionMode == 2){
                //패킷전송 켬
                if( spenDelay!= null){
                    spenDelay.cancel();
                    spenDelay = null;
                }
                spenDelay = new Timer(true);
                spenDelay.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionsend = true;
                    }
                }, 200);

                //모션 켬
                spenMotionConnectOn();
                //타이머 끔
                if(spenMotionOffTimer!=null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer=null;
                }
                spenButtonDownTime = SystemClock.uptimeMillis();

                if(touchQueue) {
                    //200ms 초과 : 드래그 후 마우스 떼는것
                    if ((SystemClock.uptimeMillis() - touchUpTime) > 300) {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue=false;
                    }
                    //200ms 미만
                    if ((SystemClock.uptimeMillis() - touchUpTime) < 300) {
                        //터치다운 시간 기록
                        spenButtonDownTime = SystemClock.uptimeMillis();

                        //클릭 타이머 취소
                        if(mouseClick!=null){
                            mouseClick.cancel();
                            mouseClick=null;
                        }
                    }
                }
                mouseDownStatus = true;
            }

            //제스쳐모드
            if(MyAccessibilityService.airActionMode == 3){
                spenMotionConnectOn();
                spenMotionsend = false;
            }
        }

        //버튼 업
        if (button.getAction() == ButtonEvent.ACTION_UP) {
            spenButtonStatus = false;
            if(MyAccessibilityService.airActionMode == 1) {
                spenMotionsend = false;
                spenMotionConnectOff();
                conn.sendMousePosition((short) 2960, (short) 1820, (short) 2960, (short) 1820);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT), KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L), KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L), KeyboardPacket.KEY_UP, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT), KeyboardPacket.KEY_UP, (byte) 0);
            }
            if(MyAccessibilityService.airActionMode == 2){
                //패킷전송 끔

                if(spenDelay!=null) {
                    spenDelay.cancel();
                    spenDelay=null;
                }
                spenMotionsend = false;

                if( spenMotionOffTimer!= null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer = null;
                }
                spenMotionOffTimer = new Timer(true);
                spenMotionOffTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionConnectOff();
                    }
                }, 5000);

                if(!touchQueue) {
                    //터치 다운 후 100미만
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) < 200) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchUpTime = SystemClock.uptimeMillis();
                        touchQueue = true;
                    }
                    //터치 다운 후 100초과
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) > 200) {
                        //드래그 중
                    }
                    //터치 다운 후 100미만  200ms 후에도 터치 다운 이벤트 없음
                    if((SystemClock.uptimeMillis() - spenButtonDownTime) < 200){

                        if( mouseClick!= null){
                            mouseClick.cancel();
                            mouseClick = null;
                        }

                        mouseClick = new Timer(true);
                        mouseClick.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                                touchQueue = false;
                            }
                        }, 200);
                    }
                }
                else {
                    //200ms 미만 : 클릭
                    if((SystemClock.uptimeMillis() -spenButtonDownTime)<200){
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue = false;
                    }
                    //200ms 초과
                    if((SystemClock.uptimeMillis() -spenButtonDownTime) >200){
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue = false;
                    }
                }
                mouseDownStatus = false;
            }
            //제스쳐모드
            if(MyAccessibilityService.airActionMode == 3){
                if( spenMotionOffTimer!= null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer = null;
                }
                spenMotionOffTimer = new Timer(true);
                spenMotionOffTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionConnectOff();
                    }
                }, 5000);
            }
            BUTTONSTATEUP = true;
            BUTTONSTATEDOWN = false;
            ONEClick = true;
        }
        switch (MyAccessibilityService.airActionMode){
            case 0:
                break;
            case 1:
                break;
            case 2:
                break;
            case 3:
                break;
        }

    }

    //------------------------------------------------
    public byte mouseButtonDownManager(View view, MotionEvent event){
        byte buttonIndex = 0x00;
        if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS) {
            switch (event.getButtonState() - lastButtonDown) {
                case 1:
                    buttonIndex = 0x01;
                    break;
                case 2:
                    buttonIndex = 0x03;
                    break;
                case 4:
                    buttonIndex = 0x02;
                    break;
                case 8:
                    buttonIndex = 0x04;
                    break;
                case 16:
                    buttonIndex = 0x05;
                    break;
            }
            lastButtonDown = event.getButtonState();
        }
        return buttonIndex;
    }

    public byte mouseButtonUpManager(View view, MotionEvent event){
        byte buttonIndex = 0x00;
        if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) {
            switch (lastButtonDown - event.getButtonState()) {
                case 1:
                    buttonIndex = 0x01;
                    break;
                case 2:
                    buttonIndex = 0x03;
                    break;
                case 4:
                    buttonIndex = 0x02;
                    break;
                case 8:
                    buttonIndex = 0x04;
                    break;
                case 16:
                    buttonIndex = 0x05;
                    break;
            }
            lastButtonDown = event.getButtonState();
        }
        return buttonIndex;
    }

    public void trackpadConverter(View view, MotionEvent event){
        if(event.getPointerCount()==1){
            if(event.getAction()== MotionEvent.ACTION_DOWN){
                if(touchQueue) {
                    //200ms 초과 : 드래그 후 마우스 떼는것
                    if ((SystemClock.uptimeMillis() - touchUpTime) > 200) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue=false;
                    }
                    //200ms 미만
                    if ((SystemClock.uptimeMillis() - touchUpTime) < 200) {
                        //터치다운 시간 기록됨
                        //클릭 타이머 취소
                        if(mouseClick!=null){
                            mouseClick.cancel();
                            mouseClick=null;
                        }
                    }
                }
                trackPadTouchDownStatus=true;
                lastPadDragStartX = event.getRawX();
                lastPadDragStartY = event.getRawY();
                mouseDownStatus = true;
                padMoveStatus = false;
            }

            if(event.getAction()== MotionEvent.ACTION_UP){
                if(!touchQueue) {
                    //터치 다운 후 100미만
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) < 70) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchUpTime = SystemClock.uptimeMillis();
                        touchQueue = true;
                    }
                    //터치 다운 후 100초과
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) > 70) {
                        //드래그 중
                    }
                    //터치 다운 후 100미만  200ms 후에도 터치 다운 이벤트 없음
                    if((SystemClock.uptimeMillis() - event.getDownTime()) < 70){
                        if(mouseClick != null){
                            mouseClick.cancel();
                            mouseClick = null;
                        }
                        mouseClick = new Timer(true);
                        mouseClick.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                                touchQueue = false;
                            }
                        }, 200);
                    }
                }
                else if(touchQueue){
                    //200ms 미만 : 클릭
                    if((SystemClock.uptimeMillis() -event.getDownTime()) <200){
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue = false;
                    }
                    if((SystemClock.uptimeMillis() -event.getDownTime()) >200){
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);

                        touchQueue = false;
                    }
                }
                trackPadReset(view,event);
            }
            //드래그
            //방향성 결정
            dragRight = (event.getRawY()-lastPadDragStartY );
            dragLeft = -(event.getRawY()-lastPadDragStartY);
            dragUp = (event.getRawX()-lastPadDragStartX);
            dragDown = -(event.getRawX()-lastPadDragStartX);

            if(dragRight > dragLeft && dragRight > dragUp  &&
                    dragRight > dragDown){
                moveDirection=1;
            }
            if(dragLeft > dragRight && dragLeft > dragUp  &&
                    dragLeft > dragDown){
                moveDirection=2;
            }
            if(dragUp > dragLeft && dragUp > dragRight  &&
                    dragUp > dragDown){
                moveDirection=3;
            }
            if(dragDown > dragLeft && dragDown > dragUp  &&
                    dragDown > dragRight){
                moveDirection=4;
            }

            //움직임 상태확인
            //자동 움직임이 황성화 되면 움직임 신호를 멈춤
            if (!padMoveStatus) {
                //패드 타이머를 끈다
                padTimer = false;
            }

            if(((event.getRawX()-lastPadDragX) > 5 || (event.getRawX()-lastPadDragX) < -5)||
                    ((event.getRawY()-lastPadDragY) > 5 || (event.getRawY()-lastPadDragY) < -5)) {
                padTimer = false;
                if (tapDownTimer != null) {
                    tapDownTimer.cancel();
                    tapDownTimer = null;
                }
            }
            else {
                //패드타이머를 켭니다.
                if(!padTimer){
                    padTimer = true;
                    //가장자리 갔을때문 활성화
                    if(event.getRawX()<900||event.getRawX()>1500 ||
                            event.getRawY()>1500|| event.getRawY()<100) {
                        dragPadPointerCapture(view, event);
                    }
                }
            }
            lastPadDragX = event.getRawX();
            lastPadDragY = event.getRawY();
        }
        //손가락 갯수 2개
        if(event.getPointerCount() == 2){
            if(event.getButtonState()==1){
                trackPadScrollButton = true;
                trackPadMove = true;
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
            }
            //터치 업
            if(event.getAction() == 262 || event.getAction() == 6)
            {
                //100ms 이내
                if((SystemClock.uptimeMillis() -event.getDownTime()) <100){
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    //스크롤 큐 끔
                    scrollQueue = false;
                    trackPadMove = true;
                }
                trackPadReset(view,event);
            }
            //터치다운
            if(event.getAction() == 261){
                //포인터 이동 막기
                trackPadMove = false;
                if(!scrollQueue){
                    //스크롤 큐 켬
                    scrollQueue = true;
                    //점과 점 사이 거리 기록
                    dotToDotLength = (float) Math.sqrt(Math.pow(event.getX(0)-
                            event.getX(1), 2) + Math.pow(event.getY(0) - event.getY(1), 2));
                    //처음 위치 기록
                    PadScroll = event.getRawX();
                    PadScrollY = event.getRawY();
                    zoom = (float) Math.sqrt(Math.pow(event.getX(0) -
                            event.getX(1), 2) + Math.pow(event.getY(0) -
                            event.getY(1), 2));
                }
                trackPadTouchDownStatus=true;
            }
            if(!trackPadScrollButton) {
                //두 손가락 드래그 상태
                if (scrollQueue) {
                    if (scrollMode) {
                        //상하 스크롤모드
                        //현재 위치- 처음위치 1번 이동
                        if (!landMode) {
                            conn.sendMouseHighResScroll((short) ((PadScroll - event.getRawX()) * 5));
                            PadScroll = event.getRawX();
                            if ((event.getRawY() - PadScrollY) > 150 || (event.getRawY() - PadScrollY) < -150) {
                                landMode = true;
                                PadScrollY = event.getRawY();
                            }
                        }
                        //좌우 스크롤
                        if (landMode) {

                            //드래그속도
                            if (((event.getRawY() - lastPadDragY) < 25 && (event.getRawY() - lastPadDragY) > -25)) {
                                conn.sendKeyboardInput((short) 0xA0, KeyboardPacket.KEY_DOWN, (byte) 0);
                                conn.sendMouseHighResScroll((short) ((event.getRawY() - PadScrollY) * 5));
                                PadScrollY = event.getRawY();
                            } else {
                                if (trackPadTouchDownStatus) {
                                    //가장자리
                                    if (event.getRawY() < 150) {
                                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                                        trackPadTouchDownStatus = false;
                                    }
                                    if (event.getRawY() > 1450) {
                                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                                        trackPadTouchDownStatus = false;
                                    }
                                }
                            }
                        }
                        lastPadDragX = event.getRawX();
                        lastPadDragY = event.getRawY();
                    }
                    if (!pinchMode) {
                        if ((dotToDotLength - (float) Math.sqrt(Math.pow(event.getX(0) -
                                event.getX(1), 2) + Math.pow(event.getY(0) -
                                event.getY(1), 2))) > 200 ||
                                (dotToDotLength - (float) Math.sqrt(Math.pow(event.getX(0) -
                                        event.getX(1), 2) + Math.pow(event.getY(0)
                                        - event.getY(1), 2))) < -200) {
                            pinchMode = true;
                            scrollMode = false;
                        }
                    } else if (pinchMode) {
                        conn.sendKeyboardInput((short) 0xA2, KeyboardPacket.KEY_DOWN, (byte) 0);
                        conn.sendMouseHighResScroll((short) (((float) Math.sqrt(Math.pow(event.getX(0) -
                                event.getX(1), 2) + Math.pow(event.getY(0) -
                                event.getY(1), 2))) - zoom));

                        zoom = (float) Math.sqrt(Math.pow(event.getX(0) -
                                event.getX(1), 2) + Math.pow(event.getY(0) -
                                event.getY(1), 2));
                    }
                }
            }
        }
        if(event.getPointerCount()==3){
            //세 손가락 터치 다운
            if(event.getAction()==517 ){
                trackPadTouchDownStatus=true;
                altTabSwitch = true;
                lastPadTouchDownX=event.getRawX();
                lastPadTouchDownY=event.getRawY();
            }
            //세 손가락 터치 업
            if(event.getAction() == 518){
                trackPadReset(view,event);
            }
            //세 손가락 드래그
            if(event.getAction() == 2){
                if(trackPadThreeFinger==0){
                    //트랙패드 모드 결정
                    if(Math.abs(event.getRawX()-lastPadTouchDownX) > 100){
                        trackPadThreeFinger=1;
                    }
                    if(Math.abs(event.getRawY()-lastPadTouchDownY) > 100){
                        trackPadThreeFinger=2;
                    }
                }
                //세 손가락 단일 이벤트
                if(trackPadThreeFinger==1) {
                    if (trackPadTouchDownStatus) {
                        //세 손가락 위로 드래그
                        if (event.getRawX() - lastPadTouchDownX > 100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_DOWN, (byte) 0x08);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_DOWN, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_UP, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_UP, (byte) 0);
                            trackPadTouchDownStatus = false;
                        }
                        //세 손가락 아래로 드래그
                        if (event.getRawX() - lastPadTouchDownX < -100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_DOWN, (byte) 0x08);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_D),
                                    KeyboardPacket.KEY_DOWN, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_D),
                                    KeyboardPacket.KEY_UP, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_UP, (byte) 0);
                            trackPadTouchDownStatus = false;
                        }
                    }
                }
                //세 손가락 다중 이벤트
                //트랙패드 좌우
                if(trackPadThreeFinger==2) {
                    if (Math.abs((event.getRawY() - lastPadTouchDownY)) > 100) {
                        if (altTabSwitch) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_ALT_LEFT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            altTabSwitch = false;
                        }
                        if (event.getRawY() - lastPadTouchDownY < -100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_LEFT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_LEFT),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            lastPadTouchDownX = event.getRawX();
                            lastPadTouchDownY = event.getRawY();
                        }
                        if (event.getRawY() - lastPadTouchDownY > 100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_RIGHT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_RIGHT),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            lastPadTouchDownX = event.getRawX();
                            lastPadTouchDownY = event.getRawY();
                        }
                    }
                }
            }
        }
    }

    public void dragPadPointerCapture(View view , MotionEvent event){
        if(mouseDownStatus== true && padTimer== true){

            tapDownTimer = new Timer(true);
            tapDownTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    for (int i=0 ; true ; i++){
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace(); }

                        padMoveStatus = true;

                        switch (moveDirection){
                            case 1:
                                conn.sendMouseMove((short)(1),(short) 0);
                                break;
                            case 2:
                                conn.sendMouseMove((short)(-1),(short) 0);
                                break;
                            case 3:
                                conn.sendMouseMove((short)(0),(short) -1);
                                break;
                            case 4:
                                conn.sendMouseMove((short)(0),(short) 1);
                                break;
                        }
                        if(!mouseDownStatus){
                            if (tapDownTimer != null) {
                                tapDownTimer.cancel();
                                tapDownTimer = null;
                            }break;
                        }
                    }
                }
            }, 1000);
        }
    }

    private void trackPadReset(View view, MotionEvent event){
        mouseButtonUpSender(MouseButtonPacket.BUTTON_MIDDLE);
        conn.sendKeyboardInput((short) 0xA0, KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput((short) 0xA2, KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_ALT_LEFT), KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB), KeyboardPacket.KEY_UP, (byte) 0);
        lastPadDragX = event.getRawX();
        lastPadDragY = event.getRawY();
        lastPadTouchUpY = event.getY();
        lastPadTouchUpX = event.getX();
        trackPadTouchDownStatus = altTabSwitch = mouseDownStatus = padMoveStatus = false;
        trackPadScrollButton = scrollQueue = pinchMode = landMode = false;
        scrollMode = trackPadMove = true;
        dotToDotLength = lastPadTouchDownX = lastPadTouchDownY = trackPadThreeFinger = 0;
    }



    //------------------------------------------------------------------

    //레거시 터치 입력 시스템
    private boolean handleMotionEvent(View view, MotionEvent event) {
        //인풋캡쳐 토글
        if (!grabbedInput) {
            return false;
        }

        // 컨트롤러 여부 확인
        int eventSource = event.getSource();
        if ((eventSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true;
            }
        }
        //포인터 장치,포지션 장치,마우스 장치 여부
        else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE)
        {
            // 마우스와 비 손가락 터치 장치용
            if (eventSource == InputDevice.SOURCE_MOUSE ||
                    (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 || // SOURCE_TOUCHPAD
                    eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (event.getPointerCount() >= 1 &&
                            (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)))
            {
                int changedButtons = event.getButtonState() ^ lastButtonState;

                if(!trackPadScrollButton) {
                    if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
                        if ((event.getButtonState() & MotionEvent.BUTTON_PRIMARY) != 0) {
                            mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        } else {
                            mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        }
                    }
                }

                // Mouse secondary or stylus primary is right click (stylus down is left click)
                if ((changedButtons & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                    if ((event.getButtonState() & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    }
                }

                // Handle stylus presses
                if (event.getPointerCount() == 1 && event.getActionIndex() == 0) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchDownTime = SystemClock.uptimeMillis();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Stylus is left click
                            mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchDownTime = SystemClock.uptimeMillis();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Eraser is right click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                    else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchUpTime = SystemClock.uptimeMillis();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);

                            // Stylus is left click
                            mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchUpTime = SystemClock.uptimeMillis();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);
                            // Eraser is right click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                }
                lastButtonState = event.getButtonState();
            }
            // This case is for fingers
            else
            {
                if (virtualController != null &&
                        (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons ||
                                virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)) {
                    // Ignore presses when the virtual controller is being configured
                    return true;
                }

                if (view == null && !prefConfig.touchscreenTrackpad) {
                    // Absolute touch events should be dropped outside our view.
                    return true;
                }

                int actionIndex = event.getActionIndex();
                int eventX = (int)event.getX(actionIndex);
                int eventY = (int)event.getY(actionIndex);

                // Special handling for 3 finger gesture
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN &&
                        event.getPointerCount() == 3) {
                    // Three fingers down
                    threeFingerDownTime = SystemClock.uptimeMillis();

                    // Cancel the first and second touches to avoid
                    // erroneous events
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                    }
                    return true;
                }

                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

                switch (event.getActionMasked())
                {
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_DOWN:

                        movePointer = 0;
                        for (TouchContext touchContext : touchContextMap) {
                            touchContext.setPointerCount(event.getPointerCount());
                        }
                        context.touchDownEvent(eventX, eventY, true);

                        if (event.getButtonState() == 2 ){
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                        }

                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_UP:

                        if (tapDownTimer != null) {
                            tapDownTimer.cancel();
                            tapDownTimer = null;
                        }

                        movePointer = 0;

                        if ( (eventX - lastPadTouchDownX) < -300 && event.getPointerCount() == 2){
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);

                        }
                        if ((eventX - lastPadTouchDownX) > 300 && event.getPointerCount() == 2){
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);

                        }

                        lastPadTouchUpY = event.getY();
                        lastPadTouchUpX = event.getX();
                        mouseDownStatus = false;
                        padMoveStatus = false;

                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                        if (event.getPointerCount() == 1) {
                            // All fingers up
                            if (SystemClock.uptimeMillis() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                                // This is a 3 finger tap to bring up the keyboard
                                showKeyboard();
                                return true;
                            }
                        }
                        context.touchUpEvent(eventX, eventY);
                        for (TouchContext touchContext : touchContextMap) {
                            touchContext.setPointerCount(event.getPointerCount() - 1);
                        }
                        if (actionIndex == 0 && event.getPointerCount() > 1 && !context.isCancelled()) {
                            // The original secondary touch now becomes primary
                            context.touchDownEvent((int)event.getX(1), (int)event.getY(1), false);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // ACTION_MOVE is special because it always has actionIndex == 0
                        // We'll call the move handlers for all indexes manually
                        // First process the historical events
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            for (TouchContext aTouchContextMap : touchContextMap) {
                                if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                                {
                                    aTouchContextMap.touchMoveEvent(
                                            (int)event.getHistoricalX(aTouchContextMap.getActionIndex(), i),
                                            (int)event.getHistoricalY(aTouchContextMap.getActionIndex(), i));
                                }
                            }
                        }
                        // Now process the current values
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        (int)event.getX(aTouchContextMap.getActionIndex()),
                                        (int)event.getY(aTouchContextMap.getActionIndex()));
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        for (TouchContext aTouchContext : touchContextMap) {
                            aTouchContext.cancelTouch();
                            aTouchContext.setPointerCount(0);
                        }
                        break;
                    default:
                        return false;
                }
            }
            // Handled a known source
            return true;
        }
        // Unknown class
        return false;
    }

    //(레거시) 소프트 키보드 출력 함수
    @Override
    public void showKeyboard() {
        LimeLog.info("Showing keyboard overlay");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }
    //------------------------------------------------------------------

    public void activeManager(){
        //매 초간 업데이트
        Timer timer = new Timer(true);
        Handler handler = new Handler();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable(){
                    public void run(){
                        autoUpdateSec();
                    }
                });
            }
        }, 0, 1000);
    }

    //액티브 기술 - 자동 업데이트
    public void autoUpdateSec()  {
        autoRefreshRate();
        getSuggestBitrate();
        overlayManager();
        TotalTx = TrafficStats.getTotalTxBytes();
        TotalRx = TrafficStats.getTotalRxBytes();
    }

    //액티브 기술 : 가변 주사율
    public void autoRefreshRate(){
        if(SurfaceViewState) {
            if (gRenderedFps < 80) {
                Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 0);
            }
            if(gRenderedFps > 85 ){
                Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 1);
            }
        }
    }

    // 액티브 기술 : 적절한 비트레이트 값을 구합니다.
    public void getSuggestBitrate(){
        if((gPing > 200 || (gRenderedFps > 3 &&gRenderedFps < 50))&&
                suggestBitrate > ((TrafficStats.getTotalRxBytes()-TotalRx)/125000) ){
            if(((TrafficStats.getTotalRxBytes()-TotalRx)/125000) != 0){
                suggestBitrate = (TrafficStats.getTotalRxBytes()-TotalRx)/125000;
            }
            else {
                suggestBitrate = 1;
            }
            autoReconnect();
        }
        if(gRenderedFps >= 60 && gPing < 300){
            if(autoReconnectTimer != null) {
                autoReconnectTimer.cancel();
                autoReconnectTimer = null;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    public void overlayManager(){
        //업데이트 될때마다 위치 변경
        if(moveTimer == 1){
            moveTimer = 0;
            norchLeft.setX(norchLeft.getX() + 1);
            norchRight.setX(norchRight.getX() + 1);
        }
        else {
            moveTimer = moveTimer+1;
            norchLeft.setX(norchLeft.getX() - 1);
            norchRight.setX(norchRight.getX() - 1);
        }
        norchLeft.setText(getTimeDate() + " | T : " + gTotalFps + "FPS | N : " +
                gReceivedFps + "FPS | R : " + gRenderedFps + "FPS | " + getHz() + "Hz | P : " +
                gPing + "ms | P(V) : " + gVariance + " | L : " + gPacketLossPercentage +
                "% | set : " + (AppView.setBitrate / 1000) +
                "Mbps | Suggest : " + (short) suggestBitrate + "Mbps | D : " +
                (TrafficStats.getTotalRxBytes() - TotalRx) / 125000 + "Mbps | U : " +
                (TrafficStats.getTotalTxBytes() - TotalTx) / 125 + "Kbps");

        norchRight.setText("D(c) : " + gDecodeTime + "ms | " + gResolutionWidth +
                " x " + gResolutionHeight + " | " + getBatteryCharge() + " | " + getBatteryPct() + " | " +
                setBitrate + " Mbps");
    }

    public boolean getBatteryCharge(){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = gameContext.registerReceiver(null, ifilter);

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // How are we charging?
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        return isCharging ;
    }

    public float getBatteryPct (){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = gameContext.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float)scale;

        return  batteryPct ;
    }

    public String getTimeDate(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        return sdf.format(new Date(System.currentTimeMillis())) ;
    }

    public short getHz(){
        int getHz = Settings.Secure.getInt(this.getContentResolver(),refershRateMode,50);
        short hz ;
        switch (getHz){
            case 0:
                hz=60;
                break;
            case 1:
                hz=120;
                break;
            default:
                hz=000 ;
        }
        return hz;
    }

    //노치바 색상 업데이트
    public void overlayBackground(){
        Timer timer = new Timer(true);
        Handler handler = new Handler();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable(){
                    public void run(){
                        if(SurfaceViewState){
                            if(!pixelCopyState){
                                pixelCopyState =true;
                                capturePicture();
                            }
                        }
                    }
                });
            }
        }, 0, 8);
    }
    //노치바용 저해상도 화면 캡쳐
    public void capturePicture() {
        View surfaceView = streamView;
        Bitmap bmp = Bitmap.createBitmap(31, surfaceView.getHeight()/8,
                Bitmap.Config.RGB_565);
        PixelCopy.request(streamView , bmp, i  -> {
            int color1 = bmp.getPixel(15, 1);

            if((Color.red(color1) > 125 && Color.green(color1) > 125 )&& Color.blue(color1) > 125) {
                norchLeft.setTextColor(Color.BLACK);
                norchRight.setTextColor(Color.BLACK);
            }
            else {
                norchLeft.setTextColor(Color.WHITE);
                norchRight.setTextColor(Color.WHITE);
            }

            imagetest.setBackgroundColor(color1);
            pixelCopyState = false;
            bmp.recycle();
        }, new Handler(Looper.getMainLooper()));
    }

    //연결상태 진단
    @Override
    public void connectionStatusUpdate(final int connectionStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (prefConfig.disableWarnings) {
                    return;
                }
                if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {

                    if (prefConfig.bitrate > 5000) {
                        notificationOverlayView.setText(getResources().getString(R.string.slow_connection_msg));
                    }
                    else {
                        notificationOverlayView.setText(getResources().getString(R.string.poor_connection_msg));
                    }
                    long currentRx = TrafficStats.getTotalRxBytes();
                    if( suggestBitrate > ((currentRx-TotalRx)/125000) ){
                        suggestBitrate = (currentRx-TotalRx)/125000;
                    }
                    if(autoReconnectPoorTimer!= null){
                        autoReconnectPoorTimer.cancel();
                        autoReconnectPoorTimer = null;
                    }
                    autoReconnectPoorTimer = new Timer(true);
                    autoReconnectPoorTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Reconnect();
                        }
                    }, 3000);
                    requestedNotificationOverlayVisibility = View.VISIBLE;
                }
                else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                    if(autoReconnectPoorTimer != null) {
                        autoReconnectPoorTimer.cancel();
                        autoReconnectPoorTimer = null;
                    }
                    requestedNotificationOverlayVisibility = View.GONE;
                }
                if (!isHidingOverlays) {
                    notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);
                }
            }
        });
    }

    //비트레이트 변경
    public void autoReconnect(){
        autoReconnectTimer = new Timer(true);
        autoReconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(suggestBitrate == AppView.setBitrate/1000){
                    suggestBitrate = setBitrate/2;
                }
                Reconnect();
            }
        }, 2000);
    }

    //비트레이트 자동 조정 - 다시 연결하기 위해 현재 화면을 캡쳐합니다.
    public void Reconnect(){
        if(SurfaceViewState){
            captureBitratePicture();
        }
    }

    //비트레이트 변경시 고해상도 정지화면 캡쳐
    public void captureBitratePicture() {
        View surfaceView = streamView;
        Bitmap bmpb = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(streamView , bmpb, i  -> {
            AppView.reBitrate = bmpb;
            ReconnectOK();
        }, new Handler(Looper.getMainLooper()));
    }

    //비트레이트 자동 조정 - 화면 캡쳐가 끝나면 연결을 재시작합니다.
    public void ReconnectOK(){
        Activity ac = Game.this;
        AppView.restertConnection(ac);
        AppView.setBitrate = (int) (suggestBitrate*1000);
    }

    public void autoBitrate(){
        if(AppView.setBitrate == 0){
            AppView.setBitrate = prefConfig.bitrate;
        }

        setBitrate = AppView.setBitrate;
        suggestBitrate = (setBitrate/1000);
        setBitrate = (int)(setBitrate*1.2) ;

        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        String appName = Game.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        String uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        String pcName = Game.this.getIntent().getStringExtra(EXTRA_PC_NAME);
        boolean appSupportsHdr = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        boolean vpnActive = NetHelper.isActiveNetworkVpn(this);

        boolean willStreamHdr = false;
        int gamepadMask = ControllerHandler.getAttachedControllerMask(this);

        float displayRefreshRate = prepareDisplayForRendering();
        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(prefConfig.fps)
                .setApp(new NvApp(appName != null ? appName : "app", appId, appSupportsHdr))
                .setBitrate(setBitrate)
                .setEnableSops(prefConfig.enableSops)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(vpnActive ? 1024 : 1392) // Lower MTU on VPN
                .setRemoteConfiguration(vpnActive ? // Use remote optimizations on VPN
                        StreamConfiguration.STREAM_CFG_REMOTE :
                        StreamConfiguration.STREAM_CFG_AUTO)
                .setHevcBitratePercentageMultiplier(100)
                .setHevcSupported(decoderRenderer.isHevcSupported())
                .setEnableHdr(willStreamHdr)
                .setAttachedGamepadMask(gamepadMask)
                .setClientRefreshRateX100((int)(displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setAudioEncryption(true)
                .build();

        conn = new NvConnection(host, uniqueId, config, PlatformBinding.getCryptoProvider(this), setServerCert);
        controllerHandler = new ControllerHandler(this, conn, this, prefConfig);
    }

    //액티브 기술
    public void debugToolOverlay(KeyEvent event) {
        //fn+f10
        if(event.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            if (MyAccessibilityService.spenDebugTogle) {
                spenDebug.setVisibility(View.VISIBLE);
            } else {
                spenDebug.setVisibility(View.GONE);
                if(spenDebugUpdateTimer!=null){
                    spenDebugUpdateTimer.cancel();
                    spenDebugUpdateTimer=null;
                }
            }
        }
        //f14
        if(event.getKeyCode()==120) {
        }
    }

    //퍼포먼스 오버레이 관련 함수
    @Override
    public void onPerfUpdate(int resolutionWidth, int resolutionHeight, short totalFps,
                             short receivedFps, short renderedFps, int ping,int variance,
                             short decodeTime, float packetLossPercentage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gResolutionWidth = resolutionWidth;
                gResolutionHeight = resolutionHeight;
                gTotalFps = totalFps;
                gReceivedFps = receivedFps;
                gRenderedFps = renderedFps;
                gPing = ping;
                gVariance = variance;
                gDecodeTime = decodeTime;
                gPacketLossPercentage = packetLossPercentage;

                imagetest2.setVisibility(View.GONE);
                if(AppView.reBitrate !=null){
                    AppView.reBitrate.recycle();
                    AppView.reBitrate =null;
                }
            }
        });
    }

    //------------------------------------------------------------------

    @Override
    public void stageStarting(final String stage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.setMessage(getResources().getString(R.string.conn_starting) + " " + stage);
                }
            }
        });
    }

    @Override
    public void stageComplete(String stage) {
    }

    //연결시작시 함수
    @Override
    public void connectionStarted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }

                setPipAutoEnter(true);
                connected = true;
                connecting = false;

                // 잠시 후 마우스 커서를 숨깁니다.
                // 스피너를 닫히기 전에 이 작업을 수행하면 스피너가 표시될 때 숨기기가 취소된 것 같습니다.
                // Android Q에서는 스피너에서 캡처하기에는 너무 이릅니다.
                // 캡처하기 전에 스피너를 닫을 수 있도록 1초 지연됩니다.
                Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        inputCaptureProvider.enableCapture();
                    }
                }, 1500);

                // 화면 항상 켜기
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                hideSystemUi(100);
            }
        });
    }

    //연결 해제 시 연결을 정리하기 위한 함수
    private void stopConnection() {
        if (connecting || connected) {
            setPipAutoEnter(false);
            connecting = connected = false;
            controllerHandler.stop();
            // 연결을 중지할 때 네트워크 I/O를 수행하여 서버에 우리가 가고 정리할 것임을 알리는 데 수백 ms가 걸릴 수 있습니다.
            // UI를 원활하게 유지하기 위해 별도의 스레드에서 실행하도록 합니다.
            // moonlight-common 내부에서는 이 스레드를 중지하기 전과 도중에 다른 스레드가 연결을 시작하는 것을 방지합니다.
            new Thread() {
                public void run() {
                    conn.stop();
                }
            }.start();
        }
    }

    //연결 오류 시 발생하는 함수
    @Override
    public void stageFailed(final String stage, final int portFlags, final int errorCode) {
        // 차단된 포트로 인해 장애가 발생한 경우 연결 테스트를 수행합니다.
        // 이것은 네트워크 I/O를 수행하므로 메인 스레드에서 수행하지 마십시오.
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (spinner != null) {
                    spinner.dismiss();
                    spinner = null;
                }
                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe(stage + " failed: " + errorCode);
                    // 비디오 초기화에 실패하고 표면이 여전히 유효한 경우 사용자에 대한 추가 정보를 표시합니다.
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        Toast.makeText(Game.this, getResources().getText(R.string.video_decoder_init_failed), Toast.LENGTH_LONG).show();
                    }
                    String dialogText = getResources().getString(R.string.conn_error_msg) + " " + stage +" (error "+errorCode+")";
                    if (portFlags != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }
                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
                        dialogText += "\n\n" + getResources().getString(R.string.nettest_text_blocked);
                    }
                    Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_error_title), dialogText, true);
                }
            }
        });
    }

    //연결 끊김 시 호출되는 함수
    @Override
    public void connectionTerminated(final int errorCode) {
        // 차단된 포트로 인해 장애가 발생한 경우 연결 테스트를 수행합니다.
        // 이것은 네트워크 I/O를 수행하므로 메인 스레드에서 수행하지 마십시오.
        final int portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode);
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER,443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 이제 화면 항상 켜짐 해제
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                // 커서숨기기 해제
                inputCaptureProvider.disableCapture();

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe("Connection terminated: " + errorCode);
                    stopConnection();

                    // 예기치 않은 종료인 경우 오류 대화 상자를 표시합니다.
                    // 그렇지 않으면 즉시 활동을 완료하십시오.
                    if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                        String message;

                        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                            // If we got a blocked result, that supersedes any other error message
                            message = getResources().getString(R.string.nettest_text_blocked);
                        }
                        else {
                            switch (errorCode) {
                                case MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC:
                                    message = getResources().getString(R.string.no_video_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_NO_VIDEO_FRAME:
                                    message = getResources().getString(R.string.no_frame_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION:
                                case MoonBridge.ML_ERROR_PROTECTED_CONTENT:
                                    message = getResources().getString(R.string.early_termination_error);
                                    break;

                                default:
                                    message = getResources().getString(R.string.conn_terminated_msg);
                                    break;
                            }
                        }

                        if (portFlags != 0) {
                            message += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                    MoonBridge.stringifyPortFlags(portFlags, "\n");
                        }

                        Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_terminated_title),
                                message, true);
                    }
                    else {
                        finish();
                    }
                }
            }
        });
    }

    //토스트메시지 발생 함수
    @Override
    public void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Game.this, message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void displayTransientMessage(final String message) {
        if (!prefConfig.disableWarnings) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Game.this, message,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    //------------------------------------------------------------------

    //서페이스 상태
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        SurfaceViewState = true;
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface changed before creation!");
        }
        if (!attemptedConnection) {
            attemptedConnection = true;

            decoderRenderer.setRenderTarget(holder);
            conn.start(PlatformBinding.getAudioRenderer(), decoderRenderer,
                    Game.this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceCreated = true;
        // Tell the OS about our frame rate to allow it to adapt the display refresh rate appropriately
        holder.getSurface().setFrameRate(prefConfig.fps,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        SurfaceViewState = false;
        if(autoBackgroundTimer != null){
            autoBackgroundTimer.cancel();
            autoBackgroundTimer =null;
        }
        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }
        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone
            decoderRenderer.prepareForStop();

            if (connected) {
                stopConnection();
            }
        }
    }

    //진동 발생함수
    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        LimeLog.info(String.format((Locale)null, "Rumble on gamepad %d: %04x %04x",
                controllerNumber, lowFreqMotor, highFreqMotor));
        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor);
    }

    //안쓰는 코드

    //터치 관련 함수
    private TouchContext getTouchContext(int actionIndex) {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        }
        else {
            return null;
        }
    }
    //모디파이 상태 관련 함수
    private byte getModifierState() {
        return (byte) modifierFlags;
    }
    // Obtain MotionEvent object
    public void TouchView(MotionEvent event) {
    }
    @Override
    public boolean handleKeyDown(KeyEvent event) {
        return true;
    }
    @Override
    public boolean handleKeyUp(KeyEvent event) {
        return true;
    }
    //마우스 움직임 함수
    @Override
    public void mouseMove(int deltaX, int deltaY) {
    }
    @Override
    public void mouseButtonEvent(int buttonId, boolean down) {
    }
    //마우스 스크롤 함수
    @Override
    public void mouseScroll(byte amount) {
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    //키보드 이벤트 관련 함수
    @Override
    public void keyboardEvent(boolean buttonDown, short keyCode) {
        short keyMap = KeyboardTranslator.translate(keyCode);
        if (keyMap != 0) {
            if (buttonDown) {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_DOWN, getModifierState());
            }
            else {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_UP, getModifierState());
            }
        }
    }

    //안쓰는 코드 끝




    public static void sendShortcutKey(int keycode, byte keyState, byte modifier ){
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated,
                keyState, modifier);
    }

    public static void sendWinTap(){
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT , KeyboardPacket.KEY_DOWN,(byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_TAB , KeyboardPacket.KEY_DOWN,(byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_TAB , KeyboardPacket.KEY_UP,(byte) 0x0);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT , KeyboardPacket.KEY_UP,(byte) 0x0);
    }

    public static void sendDesktop(){
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT , KeyboardPacket.KEY_DOWN,(byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_D , KeyboardPacket.KEY_DOWN,(byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_D , KeyboardPacket.KEY_UP,(byte) 0x0);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT , KeyboardPacket.KEY_UP,(byte) 0x0);
    }


    //시스템 ui모드 관련 함수
    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        // Don't do anything if we're not connected
        if (!connected) {
            return;
        }
        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(2000);
        }
        // This flag is only set on 4.4+
        else if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(2000);
        }
    }

    //S펜 에어액션
    //S펜 Sdk 상태 확인
    private static void checkSdkInfo() {
        Log.d(TAG, "VersionCode=" + mSpenRemote.getVersionCode());
        Log.d(TAG, "versionName=" + mSpenRemote.getVersionName());
        Log.d(TAG, "Support Button = " + mSpenRemote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON));
        Log.d(TAG, "Support Air motion = " + mSpenRemote.isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION));
    }

    //S펜 프레임워크 연결
    private void connectToSpenRemote() {
        if (mSpenRemote.isConnected()) {
            return;
        }
        mSpenRemote.setConnectionStateChangeListener(new SpenRemote.ConnectionStateChangeListener() {
            @Override
            public void onChange(int state) {
                if (state == SpenRemote.State.DISCONNECTED
                        || state == SpenRemote.State.DISCONNECTED_BY_UNKNOWN_REASON) {
                    spenConnectionStatus = false;
                }
            }
        });
        mSpenRemote.connect(this, mConnectionResultCallback);
        mIsMotionListening = false;
    }

    //S펜 프레임워크 연결 해제
    private  void disconnectSpenRemote() {

        if (mSpenRemote != null) {
            mSpenRemote.disconnect(Game.this);
            spenConnectionStatus = false;
        }


    }

    //S펜 연결결과 콜백
    private static SpenRemote.ConnectionResultCallback mConnectionResultCallback = new SpenRemote.
            ConnectionResultCallback() {
        @Override
        public void onSuccess(SpenUnitManager spenUnitManager) {
            mSpenUnitManager = spenUnitManager;
            SpenUnit buttonUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_BUTTON);
            mSpenUnitManager.registerSpenEventListener(mButtonEventListener, buttonUnit);
        }
        @Override
        public void onFailure(int i) {
        }
    };

    //S펜 모션 켜기
    public static void spenMotionConnectOn() {

        if (!mSpenRemote.isConnected()) {
            return;
        }
        if (!mIsMotionListening) {
            SpenUnit airMotionUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_AIR_MOTION);
            mSpenUnitManager.registerSpenEventListener(mAirMotionEventListener, airMotionUnit);
            mIsMotionListening = true;
        }
        else {

        }
    }

    //S펜 모션 끄기
    public static void spenMotionConnectOff() {
        if (!mSpenRemote.isConnected()) {
            return;
        }
        if (mIsMotionListening) {
            SpenUnit airMotionUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_AIR_MOTION);
            mSpenUnitManager.unregisterSpenEventListener(airMotionUnit);
            mIsMotionListening = false;
        }
    }

    public static void airActionButtonManagerDead(ButtonEvent button){
        //버튼다운
        if (button.getAction() == ButtonEvent.ACTION_DOWN) {
            spenButtonStatus = true;
            if(MyAccessibilityService.spenAiractionPointerMode){
                spenMotionsend = true;
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT),
                        KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L),
                        KeyboardPacket.KEY_DOWN, (byte)0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L),
                        KeyboardPacket.KEY_UP, (byte)0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT),
                        KeyboardPacket.KEY_UP, (byte)0);
                spenMotionConnectOn();
                conn.sendMousePosition((short) 1480, (short) 910, (short) 2960, (short) 1820);
            }
            //마우스 모드
            if(MyAccessibilityService.spenMouseMode){
                //패킷전송 켬
                if( spenDelay!= null){
                    spenDelay.cancel();
                    spenDelay = null;
                }
                spenDelay = new Timer(true);
                spenDelay.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionsend = true;
                    }
                }, 200);

                //모션 켬
                spenMotionConnectOn();
                //타이머 끔
                if(spenMotionOffTimer!=null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer=null;
                }
                spenButtonDownTime = SystemClock.uptimeMillis();

                if(touchQueue) {
                    //200ms 초과 : 드래그 후 마우스 떼는것
                    if ((SystemClock.uptimeMillis() - touchUpTime) > 300) {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue=false;
                    }
                    //200ms 미만
                    if ((SystemClock.uptimeMillis() - touchUpTime) < 300) {
                        //터치다운 시간 기록
                        spenButtonDownTime = SystemClock.uptimeMillis();

                        //클릭 타이머 취소
                        if(mouseClick!=null){
                            mouseClick.cancel();
                            mouseClick=null;
                        }
                    }
                }
                mouseDownStatus = true;
            }

            //제스쳐모드
            if(MyAccessibilityService.spenGuestureMode){
                spenMotionConnectOn();
                spenMotionsend = false;
            }
        }

        //버튼 업
        if (button.getAction() == ButtonEvent.ACTION_UP) {
            spenButtonStatus = false;
            if(MyAccessibilityService.spenAiractionPointerMode) {
                spenMotionsend = false;
                spenMotionConnectOff();
                conn.sendMousePosition((short) 2959, (short) 1819, (short) 2960, (short) 1820);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT), KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L), KeyboardPacket.KEY_DOWN, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_L), KeyboardPacket.KEY_UP, (byte) 0x02);
                conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_CTRL_LEFT), KeyboardPacket.KEY_UP, (byte) 0);
            }
            if(MyAccessibilityService.spenMouseMode){
                //패킷전송 끔

                if(spenDelay!=null) {
                    spenDelay.cancel();
                    spenDelay=null;
                }
                spenMotionsend = false;

                if( spenMotionOffTimer!= null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer = null;
                }
                spenMotionOffTimer = new Timer(true);
                spenMotionOffTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionConnectOff();
                    }
                }, 5000);

                if(!touchQueue) {
                    //터치 다운 후 100미만
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) < 200) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchUpTime = SystemClock.uptimeMillis();
                        touchQueue = true;
                    }
                    //터치 다운 후 100초과
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) > 200) {
                        //드래그 중
                    }
                    //터치 다운 후 100미만  200ms 후에도 터치 다운 이벤트 없음
                    if((SystemClock.uptimeMillis() - spenButtonDownTime) < 200){

                        if( mouseClick!= null){
                            mouseClick.cancel();
                            mouseClick = null;
                        }

                        mouseClick = new Timer(true);
                        mouseClick.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                                touchQueue = false;
                            }
                        }, 200);
                    }
                }
                else {
                    //200ms 미만 : 클릭
                    if((SystemClock.uptimeMillis() -spenButtonDownTime)<200){
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue = false;
                    }
                    //200ms 초과
                    if((SystemClock.uptimeMillis() -spenButtonDownTime) >200){
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        touchQueue = false;
                    }
                }
                mouseDownStatus = false;
            }
            //제스쳐모드
            if(MyAccessibilityService.spenGuestureMode){
                if( spenMotionOffTimer!= null){
                    spenMotionOffTimer.cancel();
                    spenMotionOffTimer = null;
                }
                spenMotionOffTimer = new Timer(true);
                spenMotionOffTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        spenMotionConnectOff();
                    }
                }, 5000);
            }
            BUTTONSTATEUP = true;
            BUTTONSTATEDOWN = false;
            ONEClick = true;
        }
    }

    public static void airActionPointerMode(){

    }
    public static void airActionMouseMode(){

    }
    public static void airActionGestureMode(){

    }

    public static void motionData() {
        sendSpenAirMousePacket ();
    }
    public static void sendSpenAirMousePacket (){
        if(spenMotionsend) {
            conn.sendMouseMove((short) (motionDeltaX * 500), (short) (motionDeltaY * -500));
        }
    }
}


