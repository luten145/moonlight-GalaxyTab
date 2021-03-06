package com.limelight;

import static android.content.ContentValues.TAG;
import static com.limelight.nvstream.input.KeyboardPacket.MODIFIER_WIN;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;

import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.driver.UsbDriverService;
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
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.icu.text.Transliterator;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
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
import com.samsung.android.sdk.penremote.SpenEventListener;
import com.samsung.android.sdk.penremote.SpenRemote;
import com.samsung.android.sdk.penremote.SpenUnit;
import com.samsung.android.sdk.penremote.SpenUnitManager;


public class Game extends Activity implements SurfaceHolder.Callback,
        OnGenericMotionListener, OnTouchListener, NvConnectionListener,
        OnSystemUiVisibilityChangeListener, GameGestures, StreamView.InputCallbacks,
        PerfOverlayListener {
    //______________________________________________________________________________________________

    // Model Profile

    // Tab S8 Ultra


    // Swtich

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------

    // System Settings
    // Reference
    private static final String refershRateMode = "refresh_rate_mode";
    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Moonlight Settings
    // Reference
    private static PreferenceConfiguration prefConfig;
    private static SharedPreferences tombstonePrefs;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Connection
    // Reference
    private static NvConnection conn;
    private ShortcutHelper shortcutHelper;
    public X509Certificate setServerCert;
    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_PC_UUID = "UUID";
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_APP_HDR = "HDR";
    public static final String EXTRA_SERVER_CERT = "ServerCert";
    // Switch
    private boolean connecting = false;
    private boolean connected = false;
    private boolean attemptedConnection = false;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // UI
    // Data
    public static int viewHeight;
    public static int viewWidth;
    public float moveTimer = 0;
    // Reference
    public static Context mContext;
    public static Activity mActivity;
    private SpinnerDialog spinner;
    private StreamView streamView;
    private ImageView reconnectionWaitingImage;
    private ImageView notchBackground;
    private TextView notificationOverlayView;
    private TextView notchRight;
    private TextView notchLeft;
    // Switch
    private boolean surfaceCreated = false;
    private boolean isHidingOverlays;
    private int requestedNotificationOverlayVisibility = View.GONE;
    private boolean autoEnterPip = false;
    private boolean displayedFailureDialog = false;
    public boolean overlaySwitch = false;
    public boolean SurfaceViewState = false;
    public boolean pixelCopyState = false;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Decode
    // Reference
    private MediaCodecDecoderRenderer decoderRenderer;
    // Switch
    private boolean reportedCrash;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Input
    // USB Devices
    private boolean connectedToUsbDriverService = false;
    //----------------------------------------------------------------------------------------------
    // UI Input
    public Button action1;
    public Button action2;
    //----------------------------------------------------------------------------------------------
    // KeyBoard
    public static int KeyCombination = 0;
    //----------------------------------------------------------------------------------------------
    // Mouse
    public int lastButtonDown;

    // 0 : Move Mode
    // 1 : PositionMode
    private static short mouseSendMode = 0;

    private short mousePositionX = 0;
    private short mousePositionY = 0;

    private ImageView pointer;

    private boolean hidePointer;
    private Timer hidePointerTimer;
    //----------------------------------------------------------------------------------------------
    // AirAction
    // Settings
    private static short airAction_DisableMotionListeningWaitTime = 5000;
    private static short airAction_MoveSpeed = 500;
    // Reference
    private static final SpenRemote mSpenRemote = SpenRemote.getInstance();
    private static SpenUnitManager mSpenUnitManager;
    // Time
    private Timer airAction_MoveWait;
    private Timer airAction_ButtonUpInputWaitingTime;
    private Timer airAction_DisableMotionListening;
    // Switch
    private boolean airAction_Move = false;
    private static short airAction_Mode = 0;
    private static boolean airAction_IsButtonListening = false;
    private static boolean airAction_IsMotionListening = false;
    private boolean airAction_ButtonUpInput = true;
    // Other
    public static float spenButtonDownTime;
    //----------------------------------------------------------------------------------------------
    // TrackPad
    // Settings
    private static short trackPad_autoMoveInterval = 10;
    private static short trackPad_TapEventThreshold = 200;
    // Data
    private static int trackPad_SENSE;
    private static int trackPad_SCROLL;
    private float trackPad_Single_StartRawX;
    private float trackPad_Single_StartRawY;
    private float trackPad_Single_EndRawX;
    private float trackPad_Single_EndRawY;
    private float trackPad_Double_Start_PointToPointDistance;
    private float trackPad_Triple_StartRawX;
    private float trackPad_Triple_StartRawY;
    // Time
    private Timer trackPad_Single_TapClick;
    private long trackPad_Single_autoMoveEventTime;
    // Switch
    public boolean trackPad_Move = true;
    private boolean trackPad_Single_EventQueue = false;
    private boolean trackPad_Single_DownStatus = false;
    private short trackPad_Double_Mode = 0;
    private short trackPad_Double_Mode_0 = 0;
    private boolean trackPad_Double_One_Time_Event = false;
    private short trackPad_Triple_Mode = 0;
    private boolean trackPad_Global_One_Time_Event = false;
    // Other
    public boolean stopAutoMove = false;
    public float PadScroll;
    public float PadScrollY;
    public float zoom;
    private Timer tapDownTimer;
    private boolean padMoveStatus = false;
    private boolean padTimer = false;
    public static float touchUpTime;
    //----------------------------------------------------------------------------------------------
    // Legacy
    // Controller
    private ControllerHandler controllerHandler;
    private VirtualController virtualController;
    private boolean grabbedInput = true;
    private boolean grabComboDown = false;
    private float lastAbsTouchUpX, lastAbsTouchUpY;
    private float lastAbsTouchDownX, lastAbsTouchDownY;
    private long lastAbsTouchUpTime = 0;
    private long lastAbsTouchDownTime = 0;
    private int lastButtonState = 0;
    // Touch
    // Reference
    private static final int REFERENCE_HORIZ_RES = 1280;
    private static final int REFERENCE_VERT_RES = 720;
    private final TouchContext[] touchContextMap = new TouchContext[2];
    private static final int THREE_FINGER_TAP_THRESHOLD = 300;
    private long threeFingerDownTime = 0;

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // StatusInfo
    // DecoderStatusInfo
    public int gResolutionWidth;
    public int gResolutionHeight;
    public short gTotalFps;
    public short gReceivedFps;
    public short gRenderedFps;
    public int gPing;
    public int gVariance;
    public short gDecodeTime;
    public float gPacketLossPercentage;
    // NetWorkStatusInfo
    private long TotalTx;
    private long TotalRx;
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Active
    public int setBitrate;
    public float suggestBitrate;

    public Timer autoReconnectTimer;
    public Timer autoReconnectPoorTimer;
    //_______________________________________________________________________________________________

    public static int swi = 0;
    public static final boolean releaseVirsion = true ;
    public View notch;
    private int modifierFlags = 0;

    //3.?????????
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

    //4.???????????? : onCreate
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_game);

        //__________________________________________________________________________________________
        // System Settings

        // ????????????
        // Wifi ?????? (????????? ?????????)
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock");
        highPerfWifiLock.setReferenceCounted(false);
        highPerfWifiLock.acquire();

        // Wifi ?????? (Q?????? ?????? ?????? ???????????? ?????? ?????????)
        // wifi?????? ????????? ???????????????
        // ????????? ?????? ??? wifi??? ?????? ?????? ????????? ???????????? ????????????.
        // wifi??? ?????? ?????? ???????????? ???????????????.
        lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock");
        lowLatencyWifiLock.setReferenceCounted(false);
        lowLatencyWifiLock.acquire();

        // 120Hz??? ??????
        if(!releaseVirsion){
            Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 1);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // ??????
        // ???????????? ???????????? ?????? ?????? ?????? ????????? ???????????????. (?????? ?????????????????? ????????? ?????? ??????)
        //getWindow().setSustainedPerformanceMode(true);
        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        // ???????????? ??????
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        //VPN ?????? ??????
        boolean vpnActive = NetHelper.isActiveNetworkVpn(this);
        if (vpnActive) {
            LimeLog.info("Detected active network is a VPN");
        }

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Moonlight Settings
        UiHelper.setLocale(this);
        // Moonlight Settings
        prefConfig = PreferenceConfiguration.readPreferences(this);
        System.out.println(prefConfig.trackpadSensibility);
        trackPad_SENSE = prefConfig.trackpadSensibility;
        trackPad_SCROLL = prefConfig.trackpadScrollSensibility;
        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Connection
        // ????????? ?????? ??????
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        //???????????????????????? ??????????????? (??? : ????????? ?????? ????????? ?????? ????????? ????????? ??????) ??????
        if (connMgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }
        // ????????? ??????
        // ????????? ?????? ???????????? ??????
        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        String appName = Game.this.getIntent().getStringExtra(EXTRA_APP_NAME);
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        String uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        String pcName = Game.this.getIntent().getStringExtra(EXTRA_PC_NAME);
        boolean appSupportsHdr = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        //????????? ?????? ??????
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

        //??? ???????????? ?????? ????????? ??????
        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            //return ????????? ???????????? ??? ??? ????????? ?????? ???????????? ???????????? ?????? ?????????
            // + break??? for?????? ??????????????? ??? ????????? ?????? ????????? ?????????
            return;
        }

        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = uuid;
        shortcutHelper = new ShortcutHelper(this);
        // ????????? ??????????????? ??????
        shortcutHelper.reportComputerShortcutUsed(computer);
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, new NvApp(appName, appId, appSupportsHdr));
        }

        // Initialize the MediaCodec helper before creating the decoder
        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        MediaCodecHelper.initialize(this, glPrefs.glRenderer);

        // HDR ?????? ??????
        // HDR ????????? ????????? ????????? ??????
        boolean willStreamHdr = false;
        if (prefConfig.enableHdr) {
            // Start our HDR checklist
            Display display = getWindowManager().getDefaultDisplay();
            Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();
            // ?????????????????? HDR10??? ??????????????? ??????
            if (hdrCaps != null) {
                for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                    if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                        willStreamHdr = true;
                        break;
                    }
                }
            }
            if (!willStreamHdr) {
                Toast.makeText(this, "??? ?????????????????? Hdr10??? ???????????? ????????????.",
                        Toast.LENGTH_LONG).show();
            }
        }

        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // UI
        //?????? ??? ??????
        if (!ServerHelper.restart) {
            spinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                    getResources().getString(R.string.conn_establishing_msg), true);
        } else {
            reconnectionWaitingImage = findViewById(R.id.imim);
            reconnectionWaitingImage.setImageBitmap(AppView.reBitrate);
            reconnectionWaitingImage.setVisibility(View.VISIBLE);
            Toast.makeText(this, "??????????????? ??????????????? ?????????????????? ??????????????????.",
                    Toast.LENGTH_LONG).show();
        }
        notificationOverlayView = findViewById(R.id.notificationOverlay);
        notchRight = findViewById(R.id.notchRight);
        notchLeft = findViewById(R.id.notchLeft);
        streamView = findViewById(R.id.surfaceView);
        notchBackground = findViewById(R.id.notchBackground);
        notch = findViewById(R.id.notch);


        if(prefConfig.smartNotchFill){

        }

        if (prefConfig.stretchVideo || shouldIgnoreInsetsForResolution(prefConfig.width,
                prefConfig.height)) {
            // Allow the activity to layout under notches if the fill-screen option
            // was turned on by the user or it's a full-screen native resolution
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Decode
        decoderRenderer = new MediaCodecDecoderRenderer(this, prefConfig, new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        // The MediaCodec instance is going down due to a crash
                        // let's tell the user something when they open the app again
                        // We must use commit because the app will crash when we return from this function
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt(
                                "CrashCount", 0) + 1).commit();
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
            Toast.makeText(this, "Decoder does not support HEVC Main10HDR10",
                    Toast.LENGTH_LONG).show();
        }
        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FORCE_H265_ON && !decoderRenderer.
                isHevcSupported()) {
            Toast.makeText(this, "No HEVC decoder found.\nFalling back to H.264.",
                    Toast.LENGTH_LONG).show();
        }
        // ??????
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
        LimeLog.info("Display refresh rate: " + displayRefreshRate);

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
            } else if (prefConfig.fps >= roundedRefreshRate) {
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
        // Decoder Config Complete
        autoBitrate();
        //----------------------------------------------------------------------------------------------
        //----------------------------------------------------------------------------------------------
        // Input
        // UI Input
        action1 = findViewById(R.id.action1);
        action1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Reconnect();
            }
        });
        action2 = findViewById(R.id.action2);
        action2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!overlaySwitch) {
                    overlaySwitch = true;
                    notchLeft.setVisibility(View.GONE);
                    notchRight.setVisibility(View.GONE);
                } else {
                    overlaySwitch = false;
                    notchLeft.setVisibility(View.VISIBLE);
                    notchRight.setVisibility(View.VISIBLE);
                }
            }
        });
        //------------------------------------------------------------------------------------------
        // Mouse
        // ????????? ??????
        streamView.setFocusable(true);
        streamView.setDefaultFocusHighlightEnabled(false);
        streamView.requestPointerCapture();
        pointerCaptureOn();
        streamView.setOnGenericMotionListener(this);

        pointer = findViewById(R.id.pointer);

        //------------------------------------------------------------------------------------------
        // AirAction
        checkSdkInfo();
        //------------------------------------------------------------------------------------------
        // Legacy
        streamView.setOnTouchListener(this);
        streamView.setInputCallbacks(this);
        // ?????? ????????? ??????
        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(controllerHandler, null);

        // ?????? ?????? ??? ??????
        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
            } else {
                touchContextMap[i] = new RelativeTouchContext(conn, i,
                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                        streamView);
            }
        }
        if (prefConfig.onscreenController) {
            // ?????? ?????? ???????????? ??????
            virtualController = new VirtualController(controllerHandler,
                    (FrameLayout) streamView.getParent(),
                    this);
            virtualController.refreshLayout();
            virtualController.show();
        }
        if (prefConfig.usbDriver) {
            // USB ???????????? ??????
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // StatusInfo
        //?????? ???????????? ??????
        AutoCallSec();
        //?????? Tx Rx ??? ??????
        TotalTx = TrafficStats.getTotalTxBytes();
        TotalRx = TrafficStats.getTotalRxBytes();
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        // Active
        // ?????? ?????? ???????????? ??????

        if(prefConfig.smartNotchFill){
            if(Build.MODEL.equals("SM-X906N")||Build.MODEL.equals("SM-X900")){
                notchBackground();
            }
        }

        //__________________________________________________________________________________________
        // Environment Setup Complete
        // Connection Start!
        streamView.getHolder().addCallback(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContext = this;
        mActivity = this;
        System.out.println("onResume");
        accessibilityKeyOn();
        connectAirActionFrameWork();

        if(!releaseVirsion){
            if(swi==2){
                ServerHelper.restart = false;
            }
            if(swi==1){
                System.out.println("Reconnect");
                swi=0;
                ServerHelper.restart = true;
                ReconnectOK();
            }
        }

    }

    protected void onPause() {
        super.onPause();
        MyAccessibilityService.accessibilityKeyListening=false;
        System.out.println("onPause");
        GlobalExit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MyAccessibilityService.accessibilityKeyListening=false;

        System.out.println("onStop");
        GlobalExit();
        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();
        if(!releaseVirsion){
            swi=1;

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
                    message = getResources().getString(R.string.conn_client_latency) + " " + averageEndToEndLat + " ms";
                    if (averageDecoderLat > 0) {
                        message += " (" + getResources().getString(R.string.conn_client_latency_hw) + " " + averageDecoderLat + " ms)";
                    }
                } else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency) + " " + averageDecoderLat + " ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    if (videoFormat == MoonBridge.VIDEO_FORMAT_H265_MAIN10) {
                        message += " [HEVC HDR]";
                    } else if (videoFormat == MoonBridge.VIDEO_FORMAT_H265) {
                        message += " [HEVC]";
                    } else if (videoFormat == MoonBridge.VIDEO_FORMAT_H264) {
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

    //????????? ????????? ??? ???????????? ??????
    @Override
    protected void onDestroy() {
        super.onDestroy();
        GlobalExit();
        SpinnerDialog.closeDialogs(this);
        Dialog.closeDialogs();

        surfaceCreated = false;

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
                    message = getResources().getString(R.string.conn_client_latency) + " " + averageEndToEndLat + " ms";
                    if (averageDecoderLat > 0) {
                        message += " (" + getResources().getString(R.string.conn_client_latency_hw) + " " + averageDecoderLat + " ms)";
                    }
                } else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency) + " " + averageDecoderLat + " ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    if (videoFormat == MoonBridge.VIDEO_FORMAT_H265_MAIN10) {
                        message += " [HEVC HDR]";
                    } else if (videoFormat == MoonBridge.VIDEO_FORMAT_H265) {
                        message += " [HEVC]";
                    } else if (videoFormat == MoonBridge.VIDEO_FORMAT_H264) {
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
        if(!releaseVirsion){
            Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 1);

        }

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
    }

    public void GlobalExit(){
        resetAirActionService();
        pointerCaptureOff();
        disconnectAirActionFrameWork();

        if(!releaseVirsion){
            Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 1);
        }
        mContext = null;
        mActivity = null;
        pointer =null;
    }

    //______________________________________________________________________________________________
    // System Settings
    //????????? ui???????????? ??????
    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }
    //????????? UI ?????? ?????? ??????
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
            } else {
                Game.this.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    };

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Moonlight Settings

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Connection
    // ?????? ??? ?????? ?????? ?????????
    public void autoBitrate() {
        if (AppView.setBitrate == 0) {
            AppView.setBitrate = prefConfig.bitrate;
        }
        setBitrate = AppView.setBitrate;
        suggestBitrate = (setBitrate / 1000);
        setBitrate = (int) (setBitrate * 1.2);

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
                .setClientRefreshRateX100((int) (displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setAudioEncryption(true)
                .build();

        conn = new NvConnection(host, uniqueId, config, PlatformBinding.getCryptoProvider(this), setServerCert);
        controllerHandler = new ControllerHandler(this, conn, this, prefConfig);
    }

    //??????????????? ??????
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

                if(ServerHelper.restart){
                    ServerHelper.restart = false;
                    reconnectionWaitingImage.setVisibility(View.GONE);
                }
                // ?????? ?????? ??????
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                hideSystemUi(100);
            }
        });
    }
    //?????? ?????? ??? ????????? ???????????? ?????? ??????
    private void stopConnection() {
        if (connecting || connected) {
            setPipAutoEnter(false);
            connecting = connected = false;
            controllerHandler.stop();
            // ????????? ????????? ??? ???????????? I/O??? ???????????? ????????? ????????? ?????? ????????? ????????? ????????? ??? ?????? ms??? ?????? ??? ????????????.
            // UI??? ???????????? ???????????? ?????? ????????? ??????????????? ??????????????? ?????????.
            // moonlight-common ??????????????? ??? ???????????? ???????????? ?????? ????????? ?????? ???????????? ????????? ???????????? ?????? ???????????????.
            new Thread() {
                public void run() {
                    conn.stop();
                }
            }.start();
        }
    }
    //?????? ?????? ??? ???????????? ??????
    @Override
    public void stageFailed(final String stage, final int portFlags, final int errorCode) {
        // ????????? ????????? ?????? ????????? ????????? ?????? ?????? ???????????? ???????????????.
        // ????????? ???????????? I/O??? ??????????????? ?????? ??????????????? ???????????? ????????????.
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
                    // ????????? ???????????? ???????????? ????????? ????????? ????????? ?????? ???????????? ?????? ?????? ????????? ???????????????.
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        Toast.makeText(Game.this, getResources().getText(R.string.video_decoder_init_failed), Toast.LENGTH_LONG).show();
                    }
                    String dialogText = getResources().getString(R.string.conn_error_msg) + " " + stage + " (error " + errorCode + ")";
                    if (portFlags != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }
                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.nettest_text_blocked);
                    }
                    Dialog.displayDialog(Game.this, getResources().getString(R.string.conn_error_title), dialogText, true);
                }
            }
        });
    }

    //?????? ?????? ??? ???????????? ??????
    @Override
    public void connectionTerminated(final int errorCode) {
        // ????????? ????????? ?????? ????????? ????????? ?????? ?????? ???????????? ???????????????.
        // ????????? ???????????? I/O??? ??????????????? ?????? ??????????????? ???????????? ????????????.
        final int portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode);
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // ?????? ?????? ?????? ?????? ??????
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                // ??????????????? ??????

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe("Connection terminated: " + errorCode);
                    stopConnection();

                    // ????????? ?????? ????????? ?????? ?????? ?????? ????????? ???????????????.
                    // ????????? ????????? ?????? ????????? ??????????????????.
                    if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                        String message;

                        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                            // If we got a blocked result, that supersedes any other error message
                            message = getResources().getString(R.string.nettest_text_blocked);
                        } else {
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
                    } else {
                        finish();
                    }
                }
            }
        });
    }
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

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // UI

    //?????? ?????? ??? ?????? ??????
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
        } else {
            isHidingOverlays = false;
            // Restore overlays to previous state when leaving PiP
            if (virtualController != null) {
                virtualController.show();
            }
            notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);
        }
    }
    // PIP
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

    private void setPipAutoEnter(boolean autoEnter) {
        if (!prefConfig.enablePip) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter));
        } else {
            autoEnterPip = autoEnter;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
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

    //??????????????? ?????? ??????
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
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            // Enable performance optimizations for foreground
            getWindow().setSustainedPerformanceMode(true);
            decoderRenderer.notifyVideoForeground();
        }

        // Correct the system UI visibility flags
        hideSystemUi(50);
    }

    //????????? ????????? ?????????
    @SuppressLint("SetTextI18n")
    public void overlayManager() {
        //???????????? ???????????? ?????? ??????
        if (moveTimer == 100) {
            notchLeft.setX(notchLeft.getX() + 1);
            notchRight.setX(notchRight.getX() + 1);
            moveTimer += 1;
        } else if (moveTimer == 200){
            notchLeft.setX(notchLeft.getX() - 1);
            notchRight.setX(notchRight.getX() - 1);
            moveTimer = 0;
        }
        else {
            moveTimer += 1;
        }
        if(releaseVirsion&& prefConfig.enablePerfOverlay){
            notchLeft.setText(getTimeDate() + " | T : " + gTotalFps + "FPS | N : " +
                    gReceivedFps + "FPS | R : " + gRenderedFps + "FPS | P : " +
                    gPing + "ms | P(V) : " + gVariance + " | L : " + gPacketLossPercentage +
                    "% | set : " + (AppView.setBitrate / 1000) +
                    "Mbps | Suggest : " + (short) suggestBitrate + "Mbps | D : " +
                    (TrafficStats.getTotalRxBytes() - TotalRx) / 125000 + "Mbps | U : " +
                    (TrafficStats.getTotalTxBytes() - TotalTx) / 125 + "Kbps");

            notchRight.setText("AirAction Framework : "+ mSpenRemote.isConnected() + " | Button Service : " + airAction_IsButtonListening +
                    " | Motion Service : " + airAction_IsMotionListening +" | D(c) : " + gDecodeTime + "ms | " +
                    gResolutionWidth + " x " + gResolutionHeight + " | " + getBatteryCharge() + " | " +
                    getBatteryPct() + " | Real Set : " + setBitrate + " Kbps");

        }
    }

    //???????????? ??????
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        SurfaceViewState = true;

        viewHeight = streamView.getHeight();
        viewWidth = streamView.getWidth();
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

    //????????? ui?????? ?????? ??????
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

    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Decode
    //????????? ?????? ??????
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

    //?????? ??????
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

            LimeLog.info("Examining display mode: " + candidate.getPhysicalWidth() + "x" +
                    candidate.getPhysicalHeight() + "x" + candidate.getRefreshRate());

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
            } else if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
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
        LimeLog.info("Selected display mode: " + bestMode.getPhysicalWidth() + "x" +
                bestMode.getPhysicalHeight() + "x" + bestMode.getRefreshRate());
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
        } else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio((double) prefConfig.width / (double) prefConfig.height);
        }
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate;
        } else {
            // Use the actual refresh rate of the display, since the preferred refresh rate or mode
            // may not actually be applied (ex: Pixel 4 with Smooth Display disabled).
            return getWindowManager().getDefaultDisplay().getRefreshRate();
        }
    }
    //????????? ?????? ??????
    private boolean isRefreshRateGoodMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                Math.round(refreshRate) % prefConfig.fps <= 3;
    }

    //______________________________________________________________________________________________
    // Input Integrated Management System
    //----------------------------------------------------------------------------------------------
    // Input Service pool
    //----------------------------------------------------------------------------------------------
    // AirAction
    // Data
    private void checkSdkInfo() {
        Log.d(TAG, "VersionCode=" + mSpenRemote.getVersionCode());
        Log.d(TAG, "versionName=" + mSpenRemote.getVersionName());
        Log.d(TAG, "Support Button = " + mSpenRemote
                .isFeatureEnabled(SpenRemote.FEATURE_TYPE_BUTTON));
        Log.d(TAG, "Support Air motion = " + mSpenRemote
                .isFeatureEnabled(SpenRemote.FEATURE_TYPE_AIR_MOTION));
    }
    // Swtich
    private void connectAirActionFrameWork() {
        if (mSpenRemote.isConnected()) {
            return;
        }
        mSpenRemote.setConnectionStateChangeListener(new SpenRemote.ConnectionStateChangeListener() {
            @Override
            public void onChange(int state) {
                }
        });
        mSpenRemote.connect(this, mConnectionResultCallback);
        airAction_IsButtonListening = false;
        airAction_IsMotionListening = false;
    }
    private SpenRemote.ConnectionResultCallback mConnectionResultCallback = new SpenRemote.
            ConnectionResultCallback() {
        @Override
        public void onSuccess(SpenUnitManager spenUnitManager) {
            mSpenUnitManager = spenUnitManager;
            Log.d(TAG, "AirAction Connect Result : Connected");
        }
        @Override
        public void onFailure(int i){
            Log.d(TAG, "AirAction Connect Result : Fail" + i);
            // i
            // SpenRemote.Error.CONNECTION_FAILED S??? ?????????????????? ?????? ??????????????? ??????
            // SpenRemote.Error.UNKNOWN
            // SpenRemote.Error.UNSUPPORTED_DEVICE
        }
    };
    private void disconnectAirActionFrameWork() {
        mSpenRemote.disconnect(mContext);
    }
    public void AirActionButtonServiceOn() {
        if (!mSpenRemote.isConnected()) {
            Log.d(TAG, "Spen FrameWork Not Connected");
            return;
        }
        if (!airAction_IsButtonListening) {
            Log.d(TAG, "register Button Listening");
            SpenUnit buttonUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_BUTTON);
            mSpenUnitManager.registerSpenEventListener(mButtonEventListener, buttonUnit);
            airAction_IsButtonListening = true;
        }
        else {
            Log.d(TAG, "Already Button Listening");
        }
    }
    public void AirActionButtonServiceOff() {
        if (!mSpenRemote.isConnected()) {
            return;
        }
        if (airAction_IsButtonListening) {
            SpenUnit buttonUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_BUTTON);
            mSpenUnitManager.unregisterSpenEventListener(buttonUnit);
            airAction_IsButtonListening = false;
        }
    }
    public void AirActionMotionServiceOn() {
        if (!mSpenRemote.isConnected()) {
            return;
        }
        if (!airAction_IsMotionListening) {
            SpenUnit airMotionUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_AIR_MOTION);
            mSpenUnitManager.registerSpenEventListener(mAirMotionEventListener, airMotionUnit);
            airAction_IsMotionListening = true;
        }
    }
    public void AirActionMotionServiceOff() {
        if (!mSpenRemote.isConnected()) {
            return;
        }
        if (airAction_IsMotionListening) {
            SpenUnit airMotionUnit = mSpenUnitManager.getUnit(SpenUnit.TYPE_AIR_MOTION);
            mSpenUnitManager.unregisterSpenEventListener(airMotionUnit);
            airAction_IsMotionListening = false;
        }
    }
    private void resetAirActionService(){
        airAction_Mode = 0;
        AirActionButtonServiceOff();
        AirActionMotionServiceOff();
    }
    //----------------------------------------------------------------------------------------------
    // PointerCapture
    // Switch
    public void pointerCaptureOn(){
        streamView.requestPointerCapture();
        streamView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
            @Override
            public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                return pointerCaptureInputManager(view, motionEvent);
            }
        });
    }
    public void pointerCaptureOff(){
        streamView.releasePointerCapture();
    }
    //----------------------------------------------------------------------------------------------
    // Accessibility Key
    // Switch
    public void accessibilityKeyOn(){
        Log.d("KeyBoard","KeyBoardOn");
        MyAccessibilityService.accessibilityKeyListening = true;
    }
    public void accessibilityKeyOff(){
        Log.d("KeyBoard","KeyBoardOff");
        MyAccessibilityService.accessibilityKeyListening = false;
    }
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Input Signal Manager pool
    public boolean accessibilityKeyManager(KeyEvent event) {
        if (keyEventWhitelist(event)){
            return false;
        }
        else {
            return keyboardHandler(event);
        }
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        super.onKeyDown(keyCode, event);
        if (keyEventWhitelist(event)){
            return false;
        }
        else {
            return handleKeyDown(event) || super.onKeyDown(keyCode, event);
        }
    }
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyEventWhitelist(event)){
            return false;
        }
        else {
            return handleKeyUp(event) || super.onKeyUp(keyCode, event);
        }
    }
    public static boolean keyEventWhitelist(KeyEvent event){
        switch (event.getKeyCode()){
            case KeyEvent.KEYCODE_BRIGHTNESS_UP: case KeyEvent.KEYCODE_BRIGHTNESS_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                //???????????? ??????
            case 102: case 103: case 110: case 106: case 109: case 108: case 107: case 96: case 97:
                case 99: case 100:
               return true;
            default:
                return false;
        }
    }
    //----------------------------------------------------------------------------------------------
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(null, event) || super.onGenericMotionEvent(event);
    }
    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        switch (event.getToolType(0)) {
            case MotionEvent.TOOL_TYPE_MOUSE:
            case MotionEvent.TOOL_TYPE_FINGER:
                streamView.requestPointerCapture();
                break;
            case MotionEvent.TOOL_TYPE_STYLUS:
                stylusHandler(view, event);
                break;
            default:
        }
        return handleMotionEvent(view,event);
    }
    //----------------------------------------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        Display display = getWindowManager().getDefaultDisplay();

        Point size = new Point();

        display.getSize(size);

        short eventY = 0 ;

        float y = 0;
        if(Build.MODEL.equals("SM-X906N")||Build.MODEL.equals("SM-X900")){
            y = 1848;
        }
        else {
            y = size.y;
        }

        if((event.getY()-(y-(streamView.getHeight()))<0)){
            eventY = (short) 0;
        }
        else {
            eventY = (short) (event.getY()-(y-(streamView.getHeight())));
        }


        mousePositionSender((short) event.getX() ,eventY);
        return handleMotionEvent(null, event) || super.onTouchEvent(event);
    }
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        //S??? ????????? ??????????????? ???????????? ????????????.
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            stylusHandler(view, event);
            return false;
        }
        return handleMotionEvent(view, event);
    }
    //----------------------------------------------------------------------------------------------
    private SpenEventListener mButtonEventListener = event -> {
        ButtonEvent button = new ButtonEvent(event);
        airActionButtonHandler(button);
    };
    private SpenEventListener mAirMotionEventListener = event -> {
        AirMotionEvent airMotion = new AirMotionEvent(event);
        airActionMoveHandler(airMotion);
    };
    //----------------------------------------------------------------------------------------------
    public boolean pointerCaptureInputManager(View view, MotionEvent event) {
        switch (event.getToolType(0)) {
            case MotionEvent.TOOL_TYPE_MOUSE:
                mouseHandler(view, event);
                break;
            case MotionEvent.TOOL_TYPE_FINGER:
                trackpadHandler(view, event);
                break;
            case MotionEvent.TOOL_TYPE_STYLUS:
                //????????? ?????? ???????????? ???????????? ????????? ????????? ????????? ????????? ???????????????.
                pointerCaptureOff();
                break;
            default:
        }
        return true;
    }
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Input Sender Pool
    public static void keyboardDownSender(int keycode, byte Combination) {
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN,
                Combination);
    }
    public static void keyboardUpSender(int keycode, byte Combination) {
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, Combination);
    }
    public void mousePositionSender(short x, short y) {
        conn.sendMousePosition(x, y,(short) viewWidth, (short) viewHeight);
    }
    public static void mouseMoveSender(short DeltaX, short DeltaY) {
        conn.sendMouseMove(DeltaX, DeltaY);
    }
    public static void mouseButtonDownSender(byte mouseButton) {
        conn.sendMouseButtonDown(mouseButton);
    }
    public static void mouseButtonUpSender(byte mouseButton) {
        conn.sendMouseButtonUp(mouseButton);
    }
    public static void mouseScrollSender(byte mouseScroll) {
        conn.sendMouseScroll(mouseScroll);
    }
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Input Signal Handler Pool
    public boolean keyboardHandler(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                sendSettings();
            }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_SYSRQ && event.getAction() == KeyEvent.ACTION_DOWN) {
            sendScreenShot();
            return true;
        }
        //App1
        if(event.getKeyCode() == 1090 && event.getAction() == KeyEvent.ACTION_DOWN){
            sendExplorer();
            return true;
        }
        //App2
        if(event.getKeyCode() == 1091 && event.getAction() == KeyEvent.ACTION_DOWN){
            return true;
        }
        //App3
        if(event.getKeyCode() == 1092 && event.getAction() == KeyEvent.ACTION_DOWN){
            return true;
        }
        //viewApp
        if(event.getKeyCode() == 1002 && event.getAction() == KeyEvent.ACTION_DOWN){
            if(mouseSendMode == 0){
                mouseSendMode = 1;
                Toast.makeText(mContext,"Position Mode", Toast.LENGTH_SHORT);
            }
            else if(mouseSendMode == 1) {
                mouseSendMode = 0;
                Toast.makeText(mContext,"Move Mode", Toast.LENGTH_SHORT);
            }
            return true;
        }
        //Finder
        if (event.getKeyCode() == 1064 && event.getAction() == KeyEvent.ACTION_DOWN) {
            sendSearch();
            return true;
        }
        // Dex
        if (event.getKeyCode() == 1084 && event.getAction() == KeyEvent.ACTION_DOWN) {
            return true;
        }
        // ????????? View Keyboard ??? ??????????????? ??????????????? ??????
        if(event.getKeyCode() == 1006 && event.getAction() == KeyEvent.ACTION_DOWN){
            airActionModeToggle();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            keyboardDownSender(event.getKeyCode(), keyBoardCombinationConverter(event));
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            keyboardUpSender(event.getKeyCode(), keyBoardCombinationConverter(event));
        }
        return true;
    }

    public void mouseMoveDataConverter(short DeltaX , short DeltaY ){

        if(mouseSendMode ==0){
            mouseMoveSender(DeltaX, DeltaY);
        }
        else {

            pointer.setVisibility(View.VISIBLE);
            if(hidePointerTimer != null){
                hidePointerTimer.cancel();
                hidePointerTimer = null;
            }

            hidePointerTimer = new Timer(true);
            Handler handler = new Handler();
            hidePointerTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            hidePointer = false;
                            pointer.setVisibility(View.GONE);
                        }
                    });
                }
            }, 3000);


            mousePositionX += DeltaX;
            mousePositionY += DeltaY;

            if(mousePositionX < 0 ){
                mousePositionX = 0;
            }
            else if(mousePositionX > viewWidth){
                mousePositionX = (short) viewWidth;
            }
            if(mousePositionY < 0 ){
                mousePositionY = 0;
            }
            else if(mousePositionY > viewHeight){
                mousePositionY = (short) viewHeight;
            }
            pointer.setX(mousePositionX);
            pointer.setY(mousePositionY+28);
            mousePositionSender(mousePositionX,mousePositionY);

        }
    }

    public void mouseHandler(View view, MotionEvent event) {
        
        float x = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
        for (int i = 0; i < event.getHistorySize(); i++) {
            x += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i);
        }

        float y = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
        for (int i = 0; i < event.getHistorySize(); i++) {
            y += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i);
        }

        mouseMoveDataConverter((short) x, (short) y);


        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                mouseButtonDownSender(mouseButtonDownManager(view, event));
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                mouseButtonUpSender(mouseButtonUpManager(view, event));
                break;
            case MotionEvent.ACTION_SCROLL:
                mouseScrollSender((byte) event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                break;
        }
    }
    public void trackpadHandler(View view, MotionEvent event){
        trackpadConverter(view,event);
    }
    public void stylusHandler(View view, MotionEvent event) {
        mousePositionSender((short) (event.getX()),(short) (event.getY()));
    }
    public void airActionButtonHandler(ButtonEvent button) {
        airActionConverter(button);
    }
    public void airActionMoveHandler(AirMotionEvent airMotion) {
        if (airAction_Move) {
            mouseMoveSender((short) (airMotion.getDeltaX() * airAction_MoveSpeed),
                    (short)(airMotion.getDeltaY() * -airAction_MoveSpeed));
        }
    }
    //----------------------------------------------------------------------------------------------
    //----------------------------------------------------------------------------------------------
    // Input Data Converter
    public byte keyBoardCombinationConverter(KeyEvent event){
        if (event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_SHIFT_RIGHT){
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (KeyCombination){
                    case 0: case 2: case 4: case 6: case 8: case 10: case 14:
                        KeyCombination = KeyCombination+1;
                        break;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (KeyCombination){
                    case 1: case 3: case 5: case 7: case 9: case 11: case 15:
                        KeyCombination = KeyCombination-1;
                        break;
                }
            }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_CTRL_RIGHT ||
                event.getKeyCode() == KeyEvent.KEYCODE_CTRL_LEFT){
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (KeyCombination){
                    case 0: case 1: case 4: case 5: case 8: case 9: case 13:
                        KeyCombination = KeyCombination+2;
                        break;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (KeyCombination){
                    case 2: case 3: case 6: case 7: case 10: case 11: case 14: case 15:
                        KeyCombination = KeyCombination-2;
                        break;
                }
            }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_ALT_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_ALT_RIGHT){
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (KeyCombination){
                    case 0: case 1: case 2: case 3: case 8: case 9: case 10: case 11:
                        KeyCombination = KeyCombination+4;
                        break;
                }
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (KeyCombination){
                    case 4: case 5: case 6: case 7: case 11: case 12: case 13: case 15:
                        KeyCombination = KeyCombination-4;
                        break;
                }
            }
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_META_LEFT ||
                event.getKeyCode() == KeyEvent.KEYCODE_META_RIGHT){
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if(KeyCombination >= 0 && KeyCombination <= 7){
                    KeyCombination = KeyCombination+ 8;
                }
            }
            else if (event.getAction() == KeyEvent.ACTION_UP) {
                if(KeyCombination >= 8 && KeyCombination <= 15 ){
                    KeyCombination = KeyCombination - 8;
                }
            }
        }
        //Quit
        if(KeyCombination == 7&&
                event.getKeyCode() == KeyEvent.KEYCODE_Q &&
                event.getAction() == KeyEvent.ACTION_DOWN){
            mActivity.finish();


        }
        System.out.println(KeyCombination +""+event.getKeyCode()+""+ event.getAction());
        return (byte) KeyCombination;
    }

    public static void sendShortcutKey(int keycode, byte keyState, byte modifier) {
        short translated = KeyboardTranslator.translate(keycode);
        conn.sendKeyboardInput(translated, keyState, modifier);
    }
    public void sendWinTap() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_TAB, KeyboardPacket.KEY_UP, (byte) 0x0);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    public void sendScreenShot() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x09);
        sendShortcutKey(KeyEvent.KEYCODE_S, KeyboardPacket.KEY_DOWN, (byte) 0x09);
        sendShortcutKey(KeyEvent.KEYCODE_S, KeyboardPacket.KEY_UP, (byte) 0x09);
        sendShortcutKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyboardPacket.KEY_UP, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    public void sendSearch() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_S, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_S, KeyboardPacket.KEY_UP, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    public void sendExplorer() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_E, KeyboardPacket.KEY_UP, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    public void sendSettings() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_I, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_I, KeyboardPacket.KEY_UP, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    public void sendDesktop() {
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_D, KeyboardPacket.KEY_DOWN, (byte) 0x08);
        sendShortcutKey(KeyEvent.KEYCODE_D, KeyboardPacket.KEY_UP, (byte) 0x0);
        sendShortcutKey(KeyEvent.KEYCODE_META_LEFT, KeyboardPacket.KEY_UP, (byte) 0x0);
    }
    //----------------------------------------------------------------------------------------------
    public byte mouseButtonDownManager(View view, MotionEvent event) {
        byte buttonIndex = 0x00;
        if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS) {
            switch (event.getButtonState() - lastButtonDown) {
                case 1: buttonIndex = 0x01;
                    break;
                case 2: buttonIndex = 0x03;
                    break;
                case 4: buttonIndex = 0x02;
                    break;
                case 8: buttonIndex = 0x04;
                    break;
                case 16: buttonIndex = 0x05;
                    break;
            }
            lastButtonDown = event.getButtonState();
        }
        return buttonIndex;
    }
    public byte mouseButtonUpManager(View view, MotionEvent event) {
        byte buttonIndex = 0x00;
        if (event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) {
            switch (lastButtonDown - event.getButtonState()) {
                case 1: buttonIndex = 0x01;
                    break;
                case 2: buttonIndex = 0x03;
                    break;
                case 4: buttonIndex = 0x02;
                    break;
                case 8: buttonIndex = 0x04;
                    break;
                case 16: buttonIndex = 0x05;
                    break;
            }
            lastButtonDown = event.getButtonState();
        }
        return buttonIndex;
    }
    //----------------------------------------------------------------------------------------------
    public float trackPad_LastMoveEventTime;

    public void trackpadConverter(View view, MotionEvent event) {

        if (trackPad_Move) {
            if((SystemClock.uptimeMillis() - trackPad_LastMoveEventTime) < 100 ){

                float x = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                for (int i = 0; i < event.getHistorySize(); i++) {
                    x += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i);
                }

                float y = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
                for (int i = 0; i < event.getHistorySize(); i++) {
                    y += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i);
                }


                mouseMoveDataConverter((short) (y*(trackPad_SENSE/1000)),
                        (short) (-x*(trackPad_SENSE/1000)));


            }

            trackPad_LastMoveEventTime = SystemClock.uptimeMillis();

        }
        if (event.getPointerCount() == 1) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_BUTTON_PRESS:
                    mouseButtonDownSender(mouseButtonDownManager(view, event));
                    break;
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    mouseButtonUpSender(mouseButtonUpManager(view, event));
                    break;
            }
            // ???????????? ?????????
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // ???????????? ?????? ?????????
                // ?????? ??????????????? ?????? ???????????? ???????????? ????????????.
                if (trackPad_Single_EventQueue) {
                    //????????? ????????? ????????? ???????????????.
                    if ((SystemClock.uptimeMillis() - touchUpTime) > trackPad_TapEventThreshold) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                    //?????????, ?????? ??? ??? ???????????? ???????????????.
                    if ((SystemClock.uptimeMillis() - touchUpTime) < trackPad_TapEventThreshold) {
                        // ?????? ??? ??? ????????? ?????????.
                        // ?????? ????????? ???????????? ???????????????.
                        if (trackPad_Single_TapClick != null) {
                            trackPad_Single_TapClick.cancel();
                            trackPad_Single_TapClick = null;
                        }
                    }
                }
                trackPad_Single_DownStatus = true;
                padMoveStatus = false;
                // ???????????? ????????? ?????? ????????? ???????????????.
                // ???????????????????????? ?????? ????????? ???????????????.
                trackPad_Single_StartRawX = event.getRawX();
                trackPad_Single_StartRawY = event.getRawY();
            }
            //???????????? ????????? ???
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!trackPad_Single_EventQueue) {
                    //?????? ?????? ??? 100??????
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) < 70) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchUpTime = SystemClock.uptimeMillis();
                        trackPad_Single_EventQueue = true;
                    }
                    //?????? ?????? ??? 100??????
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) > 70) {
                        //????????? ???
                    }
                    //?????? ?????? ??? 100??????  200ms ????????? ?????? ?????? ????????? ??????
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) < 70) {
                        trackPad_Single_TapClick = new Timer(true);
                        trackPad_Single_TapClick.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                                trackPad_Single_EventQueue = false;
                            }
                        }, trackPad_TapEventThreshold);
                    }
                } else if (trackPad_Single_EventQueue) {
                    //200ms ?????? : ??????
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) < 200) {
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                    if ((SystemClock.uptimeMillis() - event.getDownTime()) > 200) {
                        mouseButtonUpSender(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                }
                trackPad_Global_One_Time_Event = false;
                trackPadReset(view, event);
            }
            // ?????????
            // ???????????? ?????? ??????
            float dragRightLength = (event.getRawY() - trackPad_Single_StartRawY);
            float dragLeftLength = -(event.getRawY() - trackPad_Single_StartRawY);
            float dragUpLength = (event.getRawX() - trackPad_Single_StartRawX);
            float dragDownLength = -(event.getRawX() - trackPad_Single_StartRawX);
            // ????????? ???????????? ??????
            short autoMoveDirection = 0;
            if (dragRightLength > dragLeftLength && dragRightLength > dragUpLength &&
                    dragRightLength > dragDownLength) {
                autoMoveDirection = 1;
            }
            if (dragLeftLength > dragRightLength && dragLeftLength > dragUpLength &&
                    dragLeftLength > dragDownLength) {
                autoMoveDirection = 2;
            }
            if (dragUpLength > dragLeftLength && dragUpLength > dragRightLength &&
                    dragUpLength > dragDownLength) {
                autoMoveDirection = 3;
            }
            if (dragDownLength > dragLeftLength && dragDownLength > dragUpLength &&
                    dragDownLength > dragRightLength) {
                autoMoveDirection = 4;
            }
            //????????? ????????????
            //?????? ???????????? ????????? ?????? ????????? ????????? ??????
            if (!padMoveStatus) {
                //?????? ???????????? ??????
                padTimer = false;
            }
            //??????
            //???????????? ???????????? ???????????????.
            //????????? 1??? ????????????.
            //?????? ????????? 1????????? ????????????.
            if (((event.getRawX() - trackPad_Single_EndRawX) > 10 || (event.getRawX() - trackPad_Single_EndRawX) < -10) ||
                    ((event.getRawY() - trackPad_Single_EndRawY) > 10 || (event.getRawY() - trackPad_Single_EndRawY) < -10)) {
                padTimer = false;
                //??????????????? ???????????? ???????????? ???????????????.
                if (tapDownTimer != null) {
                    tapDownTimer.cancel();
                    tapDownTimer = null;
                }
            } else {
                //?????????????????? ?????????.
                if (!padTimer) {
                    padTimer = true;
                    //???????????? ???????????? ?????????
                    //(TABS7 PLUS) event.getRawX()<900
                    //(TABS7 PLUS) event.getRawY()<1500
                    if (event.getRawX() < 590 || event.getRawX() > 1500 ||
                            event.getRawY() > 1700 || event.getRawY() < 100) {
                        stopAutoMove = false;
                        autoMovePointer(autoMoveDirection);
                    } else {
                        padTimer = false;
                        padMoveStatus = false;
                        if(tapDownTimer != null){
                            tapDownTimer.cancel();
                            tapDownTimer = null;
                        }
                    }
                }
            }
            trackPad_Single_EndRawX = event.getRawX();
            trackPad_Single_EndRawY = event.getRawY();
        }
        //????????? ?????? 2???
        if (event.getPointerCount() == 2) {
            if (event.getButtonState() == 1) {
                trackPad_Double_Mode = 1;
                trackPad_Move = true;
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
            }
            //?????? ???
            //????????? ????????? ??????
            if (event.getAction() == 262 || event.getAction() == 6) {
                //100ms ????????? ???????????? ????????????
                if ((SystemClock.uptimeMillis() - event.getDownTime()) < 100) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    //????????? ??? ???
                    trackPad_Double_One_Time_Event = false;
                    trackPad_Move = true;
                }
                trackPad_Global_One_Time_Event = false;
                trackPadReset(view, event);
            }
            //????????????
            if (event.getAction() == 261) {
                //????????? ?????? ??????
                trackPad_Move = false;

                //?????? ?????????
                if (!trackPad_Double_One_Time_Event) {
                    //????????? ??? ???
                    trackPad_Double_One_Time_Event = true;
                    //?????? ??? ?????? ?????? ??????
                    trackPad_Double_Start_PointToPointDistance = (float) Math.sqrt(Math.pow(event.getX(0) -
                            event.getX(1), 2) + Math.pow(event.getY(0) - event.getY(1), 2));
                    //?????? ?????? ??????
                    PadScroll = event.getRawX();
                    PadScrollY = event.getRawY();
                    zoom = (float) Math.sqrt(Math.pow(event.getX(0) -
                            event.getX(1), 2) + Math.pow(event.getY(0) -
                            event.getY(1), 2));
                }
            }
            //?????? ?????? ??????
            if (trackPad_Double_Mode == 0 ) {
                //??? ????????? ????????? ??????
                if (trackPad_Double_One_Time_Event) {
                    if (trackPad_Double_Mode_0 == 0) {
                        //?????? ???????????????
                        //?????? ??????- ???????????? 1??? ??????
                        conn.sendMouseHighResScroll((short) ((PadScroll - event.getRawX()) * trackPad_SCROLL/1000));
                        PadScroll = event.getRawX();
                        if ((event.getRawY() - PadScrollY) > 150 || (event.getRawY() - PadScrollY) < -150) {
                            trackPad_Double_Mode_0 = 1;
                            PadScrollY = event.getRawY();
                        }

                        if ((trackPad_Double_Start_PointToPointDistance - (float) Math.sqrt(Math.pow(event.getX(0) -
                                event.getX(1), 2) + Math.pow(event.getY(0) -
                                event.getY(1), 2))) > 200 ||
                                (trackPad_Double_Start_PointToPointDistance - (float) Math.sqrt(Math.pow(event.getX(0) -
                                        event.getX(1), 2) + Math.pow(event.getY(0)
                                        - event.getY(1), 2))) < -200) {
                            trackPad_Double_Mode_0 = 2;
                        }
                    }
                    if(trackPad_Double_Mode_0 == 1){
                        //?????? ?????????
                        // ????????? ????????? ?????? ?????????????????? ??????????????? ??????????????? ???????????????.
                        if (((event.getRawY() - trackPad_Single_EndRawY) < 25 && (event.getRawY() - trackPad_Single_EndRawY) > -25)) {
                            conn.sendKeyboardInput((short) 0xA0, KeyboardPacket.KEY_DOWN, (byte) 0);
                            conn.sendMouseHighResScroll((short) ((event.getRawY() - PadScrollY) * trackPad_SCROLL/1000));
                            PadScrollY = event.getRawY();
                        } else if (!trackPad_Global_One_Time_Event){
                            //????????????
                            if (event.getRawY() < 150) {
                                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                                trackPad_Global_One_Time_Event = true;
                            }
                            if (event.getRawY() > 1450) {
                                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                                trackPad_Global_One_Time_Event = true;
                            }
                        }
                        trackPad_Single_EndRawX = event.getRawX();
                        trackPad_Single_EndRawY = event.getRawY();
                    }

                    if(trackPad_Double_Mode_0 == 2){
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
        if (event.getPointerCount() == 3) {
            //??? ????????? ?????? ??????
            if (event.getAction() == 517) {
                trackPad_Triple_StartRawX = event.getRawX();
                trackPad_Triple_StartRawY = event.getRawY();
            }
            //??? ????????? ?????? ???
            if (event.getAction() == 518) {
                trackPad_Global_One_Time_Event = false;
                trackPadReset(view, event);
            }
            //??? ????????? ?????????
            if (event.getAction() == 2) {
                if (trackPad_Triple_Mode == 0) {
                    //???????????? ?????? ??????
                    if (Math.abs(event.getRawX() - trackPad_Triple_StartRawX) > 100) {
                        //??????
                        trackPad_Triple_Mode = 1;
                    }
                    if (Math.abs(event.getRawY() - trackPad_Triple_StartRawY) > 100) {
                        //??????
                        trackPad_Triple_Mode = 2;
                    }
                }
                //??? ????????? ?????? ?????????
                if (trackPad_Triple_Mode == 1) {
                    if (!trackPad_Global_One_Time_Event) {
                        //??? ????????? ?????? ?????????
                        if (event.getRawX() - trackPad_Triple_StartRawX > 100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_DOWN, (byte) 0x08);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_DOWN, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_UP, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_UP, (byte) 0);
                            trackPad_Global_One_Time_Event = true;
                        }
                        //??? ????????? ????????? ?????????
                        if (event.getRawX() - trackPad_Triple_StartRawX < -100) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_DOWN, (byte) 0x08);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_D),
                                    KeyboardPacket.KEY_DOWN, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_D),
                                    KeyboardPacket.KEY_UP, MODIFIER_WIN);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_META_LEFT),
                                    KeyboardPacket.KEY_UP, (byte) 0);
                            trackPad_Global_One_Time_Event = true;
                        }
                    }
                }
                //??? ????????? ?????? ?????????
                //???????????? ??????
                if (trackPad_Triple_Mode == 2) {
                    if (Math.abs((event.getRawY() - trackPad_Triple_StartRawY)) > 200) {
                        if (!trackPad_Global_One_Time_Event) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_ALT_LEFT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            trackPad_Triple_StartRawY = event.getRawY();
                            trackPad_Global_One_Time_Event = true;
                        }
                        if (event.getRawY() - trackPad_Triple_StartRawY < -200) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_LEFT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_LEFT),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            trackPad_Triple_StartRawY = event.getRawY();
                        }
                        if (event.getRawY() - trackPad_Triple_StartRawY > 200) {
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_RIGHT),
                                    KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_ALT);
                            conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_DPAD_RIGHT),
                                    KeyboardPacket.KEY_UP, KeyboardPacket.MODIFIER_ALT);
                            trackPad_Triple_StartRawY = event.getRawY();
                        }
                    }
                }
            }
        }
    }
    public void autoMovePointer(short autoMoveDirection) {
        if ((trackPad_Single_DownStatus && padTimer)&& !trackPad_Global_One_Time_Event ) {
            trackPad_Global_One_Time_Event = true;
            tapDownTimer = new Timer(true);
            tapDownTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    padMoveStatus = true;
                    switch (autoMoveDirection) {
                        case 1:
                            conn.sendMouseMove((short) (1), (short) 0);
                            break;
                        case 2:
                            conn.sendMouseMove((short) (-1), (short) 0);
                            break;
                        case 3:
                            conn.sendMouseMove((short) (0), (short) -1);
                            break;
                        case 4:
                            conn.sendMouseMove((short) (0), (short) 1);
                            break;
                    }
                }
            }, 1000,trackPad_autoMoveInterval);
        }
    }
    private void trackPadReset(View view, MotionEvent event) {
        mouseButtonUpSender(MouseButtonPacket.BUTTON_MIDDLE);
        conn.sendKeyboardInput((short) 0xA0, KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput((short) 0xA2, KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_ALT_LEFT), KeyboardPacket.KEY_UP, (byte) 0);
        conn.sendKeyboardInput(KeyboardTranslator.translate(KeyEvent.KEYCODE_TAB), KeyboardPacket.KEY_UP, (byte) 0);
        trackPad_Single_EndRawX = event.getRawX();
        trackPad_Single_EndRawY = event.getRawY();
        trackPad_Single_DownStatus = padMoveStatus = false;
        trackPad_Double_Mode = 0;
        trackPad_Double_Mode_0 = 0;
        trackPad_Move= true;
        trackPad_Double_One_Time_Event = false;
        trackPad_Global_One_Time_Event = false;
        trackPad_Double_Start_PointToPointDistance = trackPad_Triple_StartRawX = trackPad_Triple_StartRawY = trackPad_Triple_Mode = 0;
        if (tapDownTimer != null) {
            tapDownTimer.cancel();
            tapDownTimer = null;
        }
    }
    //----------------------------------------------------------------------------------------------
    public void airActionModeToggle(){
        switch (airAction_Mode){
            case 0:
                airAction_Mode = 1;
                Toast.makeText(mContext, "???????????? ????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show();
                AirActionButtonServiceOn();
                break;
            case 1:
                airAction_Mode = 2;
                Toast.makeText(mContext, "???????????? ????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show();
                break;
            case 4:
                airAction_Mode = 3;
                Toast.makeText(mContext, "???????????? ????????? ????????? ???????????????.", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(mContext, "??????????????? ???????????????.", Toast.LENGTH_SHORT).show();
                resetAirActionService();
                break;
        }
    }
    public void airActionConverter(ButtonEvent button) {
        //------------------------------------------------------------------------------------------
        // ????????? ??????
        if(airAction_Mode == 1){
            if (button.getAction() == ButtonEvent.ACTION_DOWN){
                if (airAction_ButtonUpInputWaitingTime != null) {
                    airAction_ButtonUpInputWaitingTime.cancel();
                    airAction_ButtonUpInputWaitingTime = null;
                }
                if(airAction_ButtonUpInput){
                    airAction_ButtonUpInput = false;
                    // ????????? ?????? ????????? ???????????????.
                    airAction_Move = true;
                    AirActionMotionServiceOn();
                    //????????? ???
                    if (airAction_DisableMotionListening != null) {
                        airAction_DisableMotionListening.cancel();
                        airAction_DisableMotionListening = null;
                    }
                    //??????????????? ????????? ?????????
                    keyboardDownSender(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.MODIFIER_CTRL);
                    keyboardDownSender(KeyEvent.KEYCODE_L, KeyboardPacket.MODIFIER_CTRL);
                    keyboardUpSender(KeyEvent.KEYCODE_L, KeyboardPacket.MODIFIER_CTRL);
                    keyboardUpSender(KeyEvent.KEYCODE_CTRL_LEFT, (byte) 0);
                    // ????????? ?????? ??????
                    mousePositionSender( (short) (viewWidth/2), (short) (viewHeight/2));
                }
            }
            else {
                // ??????????????? ????????? ???????????? ?????? ???????????? ?????? ?????? ???????????? ???????????????.
                airAction_ButtonUpInputWaitingTime = new Timer(true);
                airAction_ButtonUpInputWaitingTime.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        airAction_ButtonUpInput = true;
                        airAction_Move = false;
                        // ???????????? ?????? ?????? ????????? ????????? S??? ????????? ???????????????.
                        // 5??? ???????????? ?????? ???????????? ???????????? ?????? ???????????? ?????? S??? ???????????? ???????????????.
                        airAction_DisableMotionListening = new Timer(true);
                        airAction_DisableMotionListening.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                AirActionMotionServiceOff();
                            }
                        }, airAction_DisableMotionListeningWaitTime);
                        mousePositionSender((short) (viewWidth-1), (short) (viewHeight-1));
                        keyboardDownSender(KeyEvent.KEYCODE_CTRL_LEFT, KeyboardPacket.MODIFIER_CTRL);
                        keyboardDownSender(KeyEvent.KEYCODE_L, KeyboardPacket.MODIFIER_CTRL);
                        keyboardUpSender(KeyEvent.KEYCODE_L, KeyboardPacket.MODIFIER_CTRL);
                        keyboardUpSender(KeyEvent.KEYCODE_CTRL_LEFT, (byte) 0);
                    }
                }, 150);
            }
        }
        //------------------------------------------------------------------------------------------
        if (airAction_Mode == 2) {
            if (button.getAction() == ButtonEvent.ACTION_DOWN){
                //???????????? ???
                airAction_MoveWait = new Timer(true);
                airAction_MoveWait.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        airAction_Move = true;
                    }
                }, 200);
                //?????? ???
                AirActionMotionServiceOn();
                //????????? ???
                if (airAction_DisableMotionListening != null) {
                    airAction_DisableMotionListening.cancel();
                    airAction_DisableMotionListening = null;
                }
                spenButtonDownTime = SystemClock.uptimeMillis();

                if (trackPad_Single_EventQueue) {
                    //200ms ?????? : ????????? ??? ????????? ?????????
                    if ((SystemClock.uptimeMillis() - touchUpTime) > 300) {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                    //200ms ??????
                    if ((SystemClock.uptimeMillis() - touchUpTime) < 300) {
                        //???????????? ?????? ??????
                        spenButtonDownTime = SystemClock.uptimeMillis();

                        //?????? ????????? ??????
                        if (trackPad_Single_TapClick != null) {
                            trackPad_Single_TapClick.cancel();
                            trackPad_Single_TapClick = null;
                        }
                    }
                }
                trackPad_Single_DownStatus = true;
            }
            else {
                //???????????? ???
                if (airAction_MoveWait != null) {
                    airAction_MoveWait.cancel();
                    airAction_MoveWait = null;
                }
                airAction_Move = false;
                // ???????????? ?????? ?????? ????????? ????????? S??? ????????? ???????????????.
                // 5??? ???????????? ?????? ???????????? ???????????? ?????? ???????????? ?????? S??? ???????????? ???????????????.
                airAction_DisableMotionListening = new Timer(true);
                airAction_DisableMotionListening.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        AirActionMotionServiceOff();
                    }
                }, airAction_DisableMotionListeningWaitTime);
                if (!trackPad_Single_EventQueue) {
                    //?????? ?????? ??? 100??????
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) < 200) {
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        touchUpTime = SystemClock.uptimeMillis();
                        trackPad_Single_EventQueue = true;
                    }
                    //?????? ?????? ??? 100??????
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) > 200) {
                        //????????? ???
                    }
                    //?????? ?????? ??? 100??????  200ms ????????? ?????? ?????? ????????? ??????
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) < 200) {
                        trackPad_Single_TapClick = new Timer(true);
                        trackPad_Single_TapClick.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                                trackPad_Single_EventQueue = false;
                            }
                        }, 200);
                    }
                } else {
                    //200ms ?????? : ??????
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) < 200) {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        mouseButtonDownSender(MouseButtonPacket.BUTTON_LEFT);
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                    //200ms ??????
                    if ((SystemClock.uptimeMillis() - spenButtonDownTime) > 200) {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        trackPad_Single_EventQueue = false;
                    }
                }
                trackPad_Single_DownStatus = false;
            }
        }
        //------------------------------------------------------------------------------------------
        //???????????????
        if (airAction_Mode == 3) {
            if (button.getAction() == ButtonEvent.ACTION_DOWN) {
                AirActionMotionServiceOn();
                airAction_Move = false;
            }
            else {
                //???????????????
                if (airAction_Mode == 3) {
                    if (airAction_DisableMotionListening != null) {
                        airAction_DisableMotionListening.cancel();
                        airAction_DisableMotionListening = null;
                    }
                    airAction_DisableMotionListening = new Timer(true);
                    airAction_DisableMotionListening.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            AirActionMotionServiceOff();
                        }
                    }, airAction_DisableMotionListeningWaitTime);
                }
            }
        }
    }
    //----------------------------------------------------------------------------------------------
    // Legacy
    private boolean handleMotionEvent(View view, MotionEvent event) {
        //???????????? ??????
        if (!grabbedInput) {
            return false;
        }
        // ???????????? ?????? ??????
        int eventSource = event.getSource();
        if ((eventSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true;
            }
        }
        //????????? ??????,????????? ??????,????????? ?????? ??????
        else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) {
            // ???????????? ??? ????????? ?????? ?????????
            if (eventSource == InputDevice.SOURCE_MOUSE ||
                    (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 || // SOURCE_TOUCHPAD
                    eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (event.getPointerCount() >= 1 &&
                            (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER))) {
                int changedButtons = event.getButtonState() ^ lastButtonState;
                // Mouse secondary or stylus primary is right click (stylus down is left click)
                if ((changedButtons & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                    if ((event.getButtonState() & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    } else {
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
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
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
            else {
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
                int eventX = (int) event.getX(actionIndex);
                int eventY = (int) event.getY(actionIndex);
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
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_DOWN:
                        for (TouchContext touchContext : touchContextMap) {
                            touchContext.setPointerCount(event.getPointerCount());
                        }
                        context.touchDownEvent(eventX, eventY, true);

                        if (event.getButtonState() == 2) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_UP:
                        if (tapDownTimer != null) {
                            tapDownTimer.cancel();
                            tapDownTimer = null;
                        }
                        if ((eventX - trackPad_Triple_StartRawX) < -300 && event.getPointerCount() == 2) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                        }
                        if ((eventX - trackPad_Triple_StartRawX) > 300 && event.getPointerCount() == 2) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                        }

                        trackPad_Single_DownStatus = false;
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
                            context.touchDownEvent((int) event.getX(1), (int) event.getY(1), false);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // ACTION_MOVE is special because it always has actionIndex == 0
                        // We'll call the move handlers for all indexes manually
                        // First process the historical events
                        for (int i = 0; i < event.getHistorySize(); i++) {
                            for (TouchContext aTouchContextMap : touchContextMap) {
                                if (aTouchContextMap.getActionIndex() < event.getPointerCount()) {
                                    aTouchContextMap.touchMoveEvent(
                                            (int) event.getHistoricalX(aTouchContextMap.getActionIndex(), i),
                                            (int) event.getHistoricalY(aTouchContextMap.getActionIndex(), i));
                                }
                            }
                        }
                        // Now process the current values
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount()) {
                                aTouchContextMap.touchMoveEvent(
                                        (int) event.getX(aTouchContextMap.getActionIndex()),
                                        (int) event.getY(aTouchContextMap.getActionIndex()));
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
    // Legacy ????????? ????????? ?????? ??????
    @Override
    public void showKeyboard() {
        LimeLog.info("Showing keyboard overlay");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }
    private TouchContext getTouchContext(int actionIndex) {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        } else {
            return null;
        }
    }
    @Override
    public boolean handleKeyDown(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }
        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click. This event WILL repeat if
        // the right mouse button is held down, so we ignore those.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
            }
            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }
        boolean handled = false;
        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonDown(event);
        }
        if (!handled) {
            // Try the keyboard handler
            short translated = KeyboardTranslator.translate(event.getKeyCode());
            if (translated == 0) {
                return false;
            }
            // Let this method take duplicate key down events
            if (handleSpecialKeys(event.getKeyCode(), true)) {
                return true;
            }
            // Eat repeat down events
            if (event.getRepeatCount() > 0) {
                return true;
            }
            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }
            byte modifiers = getModifierState(event);
            if (KeyboardTranslator.needsShift(event.getKeyCode())) {
                modifiers |= KeyboardPacket.MODIFIER_SHIFT;
            }
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, modifiers);
        }
        return true;
    }
    @Override
    public boolean handleKeyUp(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }
        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            }
            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }
        boolean handled = false;
        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonUp(event);
        }
        if (!handled) {
            // Try the keyboard handler
            short translated = KeyboardTranslator.translate(event.getKeyCode());
            if (translated == 0) {
                return false;
            }

            if (handleSpecialKeys(event.getKeyCode(), false)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }

            byte modifiers = getModifierState(event);
            if (KeyboardTranslator.needsShift(event.getKeyCode())) {
                modifiers |= KeyboardPacket.MODIFIER_SHIFT;
            }
            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, modifiers);
        }
        return true;
    }
    // Returns true if the key stroke was consumed
    private boolean handleSpecialKeys(int androidKeyCode, boolean down) {
        int modifierMask = 0;

        if (androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT;
        }

        if (down) {
            this.modifierFlags |= modifierMask;
        }
        else {
            this.modifierFlags &= ~modifierMask;
        }

        // Check if Ctrl+Shift+Z is pressed
        if (androidKeyCode == KeyEvent.KEYCODE_Z &&
                (modifierFlags & (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT)) ==
                        (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_SHIFT))
        {
            if (down) {
                // Now that we've pressed the magic combo
                // we'll wait for one of the keys to come up
                grabComboDown = true;
            }
            else {
                // Toggle the grab if Z comes up
                Handler h = getWindow().getDecorView().getHandler();
                if (h != null) {
                    h.postDelayed(toggleGrab, 250);
                }
                grabComboDown = false;
            }

            return true;
        }
        // Toggle the grab if control or shift comes up
        else if (grabComboDown) {
            Handler h = getWindow().getDecorView().getHandler();
            if (h != null) {
                h.postDelayed(toggleGrab, 250);
            }

            grabComboDown = false;
            return true;
        }

        // Not a special combo
        return false;
    }

    private byte getModifierState(KeyEvent event) {
        // Start with the global modifier state to ensure we cover the case
        // detailed in https://github.com/moonlight-stream/moonlight-android/issues/840
        byte modifier = getModifierState();
        if (event.isShiftPressed()) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (event.isCtrlPressed()) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (event.isAltPressed()) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }
        return modifier;
    }

    private byte getModifierState() {
        return (byte) modifierFlags;
    }

    private final Runnable toggleGrab = new Runnable() {
        @Override
        public void run() {

            grabbedInput = !grabbedInput;
        }
    };
    //______________________________________________________________________________________________
    // StatusInfo
    public void AutoCallSec() {
        Timer timer = new Timer(true);
        Handler handler = new Handler();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        //autoRefreshRate();
                        getSuggestBitrate();
                        overlayManager();
                        networkStatus();
                    }
                });
            }
        }, 0, 1000);
    }
    //----------------------------------------------------------------------------------------------
    // SystemStatusInfo
    public boolean getBatteryCharge() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);
        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        // How are we charging?
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
        return isCharging;
    }
    public float getBatteryPct() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        float batteryPct = level * 100 / (float) scale;

        return batteryPct;
    }
    public String getTimeDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        return sdf.format(new Date(System.currentTimeMillis()));
    }
    public short getHz() {
        int getHz = Settings.Secure.getInt(this.getContentResolver(), refershRateMode, 50);
        short hz;
        switch (getHz) {
            case 0:
                hz = 60;
                break;
            case 1:
                hz = 120;
                break;
            default:
                hz = 000;
        }
        return hz;
    }
    //----------------------------------------------------------------------------------------------
    // ConnectionStatusInfo
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
                    } else {
                        notificationOverlayView.setText(getResources().getString(R.string.poor_connection_msg));
                    }
                    long currentRx = TrafficStats.getTotalRxBytes();
                    if (suggestBitrate > ((currentRx - TotalRx) / 125000)) {
                        suggestBitrate = (currentRx - TotalRx) / 125000;
                    }
                    if (autoReconnectPoorTimer != null) {
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
                } else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                    if (autoReconnectPoorTimer != null) {
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
    //----------------------------------------------------------------------------------------------
    // NetWorkStatusInfo
    private void networkStatus(){
        TotalTx = TrafficStats.getTotalTxBytes();
        TotalRx = TrafficStats.getTotalRxBytes();
    }
    //----------------------------------------------------------------------------------------------
    // DecoderStatusInfo
    @Override
    public void onPerfUpdate(int resolutionWidth, int resolutionHeight, short totalFps,
                             short receivedFps, short renderedFps, int ping, int variance,
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

                if (AppView.reBitrate != null) {
                    AppView.reBitrate.recycle();
                    AppView.reBitrate = null;
                }
            }
        });
    }
    //______________________________________________________________________________________________
    // Active
    // notch
    public void notchBackground() {
        Timer timer = new Timer(true);
        Handler handler = new Handler();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        if (SurfaceViewState) {
                            if (!pixelCopyState) {
                                pixelCopyState = true;
                                notchViewCapture();
                            }
                        }
                    }
                });
            }
        }, 0, 8);
    }
    public void notchViewCapture() {
        View surfaceView = streamView;
        int width = 2960;
        Bitmap bmp = Bitmap.createBitmap(2961, 1849,
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(streamView, bmp, i -> {
            int color1 = bmp.getPixel(width/2, 1);
            float notchLeftLine = 0;
            float notchRightLine = 0;


            if(pointer != null){
                int pointerColor = bmp.getPixel((int)pointer.getX(),(int) pointer.getY());
                if((Color.red(pointerColor) < 125 &&Color.green(pointerColor) < 125)&&
                        Color.blue(pointerColor) < 125){
                    pointer.setBackgroundColor(Color.WHITE);
                }
                else {
                    pointer.setBackgroundColor(Color.BLACK);
                }
            }


            for(int b = width/2; b >=0 ; b--){
                if(bmp.getPixel(b, 1) == -16777216){
                    notchLeftLine = b;
                    break;
                }
            }
            for(int c = width/2; c <= width; c++){
                if(bmp.getPixel(c, 1) == -16777216){
                   notchRightLine = c;
                   break;
                }
            }
            if((notchLeftLine!=0 && notchRightLine!=0) &&
                    (bmp.getPixel(5, 1)== -16777216 && bmp.getPixel((width-5), 1)== -16777216) &&
                    (((notchLeftLine - (width-notchRightLine))<10)||((notchLeftLine - (width-notchRightLine))>-10))){
                LinearLayout.LayoutParams param = new LinearLayout.LayoutParams((int) (notchRightLine-notchLeftLine), 28);
                notchBackground.setLayoutParams(param);
                notchBackground.setX(3);
                notchLeft.setVisibility(View.GONE);
                notchRight.setVisibility(View.GONE);
            }
            else {
                LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(width, 28);
                notchBackground.setLayoutParams(param);
                notchBackground.setX(0);
                notchLeft.setVisibility(View.VISIBLE);
                notchRight.setVisibility(View.VISIBLE);
            }

            if ((Color.red(color1) > 125 && Color.green(color1) > 125) && Color.blue(color1) > 125) {
                notchLeft.setTextColor(Color.BLACK);
                notchRight.setTextColor(Color.BLACK);
            } else {
                notchLeft.setTextColor(Color.WHITE);
                notchRight.setTextColor(Color.WHITE);
            }
            notchBackground.setBackgroundColor(color1);
            pixelCopyState = false;
            bmp.recycle();
        }, new Handler(Looper.getMainLooper()));
    }
    //----------------------------------------------------------------------------------------------
    // RefreshRate
    public void autoRefreshRate() {
        if (SurfaceViewState) {
            if (gRenderedFps < 80) {
                Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 0);
            }
            if (gRenderedFps > 85) {
                Settings.Secure.putInt(this.getContentResolver(), refershRateMode, 1);
            }
        }
    }
    //----------------------------------------------------------------------------------------------
    // Bitrate
    public void getSuggestBitrate() {
        if ((gPing > 200 || (gRenderedFps > 3 && gRenderedFps < 50)) &&
                suggestBitrate > ((TrafficStats.getTotalRxBytes() - TotalRx) / 125000)) {
            if (((TrafficStats.getTotalRxBytes() - TotalRx) / 125000) != 0) {
                suggestBitrate = (TrafficStats.getTotalRxBytes() - TotalRx) / 125000;
            } else {
                suggestBitrate = 1;
            }
            if(!releaseVirsion){
                autoReconnect();
            }
        }
        if (gRenderedFps >= 60 && gPing < 300) {
            if (autoReconnectTimer != null) {
                autoReconnectTimer.cancel();
                autoReconnectTimer = null;
            }
        }
    }
    public void autoReconnect() {
        autoReconnectTimer = new Timer(true);
        autoReconnectTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (suggestBitrate == AppView.setBitrate / 1000) {
                    suggestBitrate = setBitrate / 2;
                }
                Reconnect();
            }
        }, 2000);
    }
    public void Reconnect() {
        if(!releaseVirsion){
            if (SurfaceViewState) {
                captureBitratePicture();
            }
        }
    }
    public void captureBitratePicture() {
        View surfaceView = streamView;
        Bitmap bmpb = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(),
                Bitmap.Config.ARGB_8888);
        PixelCopy.request(streamView, bmpb, i -> {
            AppView.reBitrate = bmpb;
            ReconnectOK();
        }, new Handler(Looper.getMainLooper()));
    }
    public void ReconnectOK() {
        Activity ac = Game.this;
        AppView.restertConnection(ac);
        AppView.setBitrate = (int) (suggestBitrate * 1000);
    }
    //----------------------------------------------------------------------------------------------
    //?????????????????? ?????? ??????
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
    //----------------------------------------------------------------------------------------------
    //Output
    //?????? ????????????
    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        LimeLog.info(String.format((Locale) null, "Rumble on gamepad %d: %04x %04x",
                controllerNumber, lowFreqMotor, highFreqMotor));
        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor);
    }
    //______________________________________________________________________________________________




    //______________________________________________________________________________________________
}



