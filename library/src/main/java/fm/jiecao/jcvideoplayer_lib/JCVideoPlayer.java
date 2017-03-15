package fm.jiecao.jcvideoplayer_lib;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;

import java.lang.reflect.Constructor;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by Nathen on 16/7/30.
 */
public abstract class JCVideoPlayer extends FrameLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener {

    public static final String TAG = "JieCaoVideoPlayer";

    public static boolean ACTION_BAR_EXIST = true;
    public static boolean TOOL_BAR_EXIST = true;
    public static int FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
    public static int NORMAL_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

    public static boolean WIFI_TIP_DIALOG_SHOWED = false;

    public static final int FULLSCREEN_ID = 33797;
    public static final int TINY_ID = 33798;
    public static final int THRESHOLD = 80;
    public static final int FULL_SCREEN_NORMAL_DELAY = 300;
    public static long CLICK_QUIT_FULLSCREEN_TIME = 0;

    public static final int SCREEN_LAYOUT_NORMAL = 0;
    public static final int SCREEN_LAYOUT_LIST = 1;
    public static final int SCREEN_WINDOW_FULLSCREEN = 2;
    public static final int SCREEN_WINDOW_TINY = 3;

    public static final int CURRENT_STATE_NORMAL = 0;
    public static final int CURRENT_STATE_PREPARING = 1;
    public static final int CURRENT_STATE_PLAYING = 2;
    public static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3;
    public static final int CURRENT_STATE_PAUSE = 5;
    public static final int CURRENT_STATE_AUTO_COMPLETE = 6;
    public static final int CURRENT_STATE_ERROR = 7;
    private static boolean isFullscreen;
    public int currentState = -1;
    public int currentScreen = -1;
    public boolean loop = false;

    public String url = "";
    public Object[] objects = null;
    public int seekToInAdvance = -1;

    public ImageView startButton;
    public SeekBar progressBar;
    public ImageView fullscreenButton;
    public TextView currentTimeTextView, totalTimeTextView;
    public ViewGroup textureViewContainer;
    public ViewGroup topContainer, bottomContainer;
    private TextView countdownTimerTextView;

    protected static JCUserAction JC_USER_EVENT;
    protected static Timer UPDATE_PROGRESS_TIMER;

    protected int mScreenWidth;
    protected int mScreenHeight;
    protected AudioManager mAudioManager;
    protected Handler mHandler;
    protected ProgressTimerTask mProgressTimerTask;

    protected boolean mTouchingProgressBar;
    protected float mDownX;
    protected float mDownY;
    protected boolean mChangeVolume;
    protected boolean mChangePosition;
    protected int mDownPosition;
    protected int mGestureDownVolume;
    protected int mSeekTimePosition;
    private OnVideoFinishedCallback onVideoFinishedCallback;
    private OnFullScreenStateChanged onFullScreenStateChanged;
    private boolean isWifiDialogEnabled = true;
    private OnCountdownTimerFinish onCountdownTimerFinish;

    public JCVideoPlayer(Context context) {
        super(context);
        init(context);
    }

    public JCVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setWifiDialogEnabled(boolean wifiDialogEnabled) {
        isWifiDialogEnabled = wifiDialogEnabled;
    }

    public boolean isWifiDialogEnabled() {
        return isWifiDialogEnabled;
    }

    public void setOnCountdownTimerFinish(OnCountdownTimerFinish onCountdownTimerFinish) {
        this.onCountdownTimerFinish = onCountdownTimerFinish;
    }

    public void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        startButton = (ImageView) findViewById(R.id.start);
        fullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        progressBar = (SeekBar) findViewById(R.id.progress);
        currentTimeTextView = (TextView) findViewById(R.id.current);
        totalTimeTextView = (TextView) findViewById(R.id.total);
        bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        textureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
        topContainer = (ViewGroup) findViewById(R.id.layout_top);
        countdownTimerTextView = (TextView) findViewById(R.id.text_countdown_timer);
        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);
        textureViewContainer.setOnTouchListener(this);

        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();

        NORMAL_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
    }

    public void setUp(String url, int screen, Object... objects) {
        if (TextUtils.isEmpty(url) || TextUtils.equals(this.url, url))
            return;

        this.url = url;
        this.objects = objects;
        this.currentScreen = screen;
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.start) {
            Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentState == CURRENT_STATE_NORMAL || currentState == CURRENT_STATE_ERROR) {
                if (isWifiDialogEnabled()) {
                    if (url.startsWith("http") && !JCUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                        showWifiDialog();
                        return;
                    }
                }
                prepareMediaPlayer();
                onEvent(currentState != CURRENT_STATE_ERROR ? JCUserAction.ON_CLICK_START_ICON : JCUserAction.ON_CLICK_START_ERROR);
            } else if (currentState == CURRENT_STATE_PLAYING) {
                onEvent(JCUserAction.ON_CLICK_PAUSE);
                Log.d(TAG, "pauseVideo [" + this.hashCode() + "] ");
                JCMediaManager.instance().simpleExoPlayer.setPlayWhenReady(false);
                setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
            } else if (currentState == CURRENT_STATE_PAUSE) {
                onEvent(JCUserAction.ON_CLICK_RESUME);
                JCMediaManager.instance().simpleExoPlayer.setPlayWhenReady(true);
                setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
            } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
                onEvent(JCUserAction.ON_CLICK_START_AUTO_COMPLETE);
                prepareMediaPlayer();
            }
        } else if (i == R.id.fullscreen) {
            Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");

            isFullscreen = currentScreen != SCREEN_WINDOW_FULLSCREEN;

            if (onFullScreenStateChanged != null)
                onFullScreenStateChanged.stateChanged(isFullscreen);

            if (currentState == CURRENT_STATE_AUTO_COMPLETE) return;
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                //quit fullscreen
                backPress();
            } else {
                Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
                onEvent(JCUserAction.ON_ENTER_FULLSCREEN);
                startWindowFullscreen();
            }

        } else if (i == R.id.surface_container && currentState == CURRENT_STATE_ERROR) {
            Log.i(TAG, "onClick surfaceContainer State=Error [" + this.hashCode() + "] ");
            prepareMediaPlayer();
        }
    }

    public void prepareMediaPlayer() {
        JCVideoPlayerManager.completeAll();
        Log.d(TAG, "prepareMediaPlayer [" + this.hashCode() + "] ");
        initTextureView();
        addTextureView();
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        JCUtils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        JCMediaManager.CURRENT_PLAYING_URL = url;
        JCMediaManager.CURRENT_PLING_LOOP = loop;
        setUiWitStateAndScreen(CURRENT_STATE_PREPARING);
        JCVideoPlayerManager.setFirstFloor(this);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
                    mTouchingProgressBar = true;

                    mDownX = x;
                    mDownY = y;
                    mChangeVolume = false;
                    mChangePosition = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                        if (!mChangePosition && !mChangeVolume) {
                            if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                                cancelProgressTimer();
                                if (absDeltaX >= THRESHOLD) {
                                    // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                                    // 否则会因为mediaplayer的状态非法导致App Crash
                                    if (currentState != CURRENT_STATE_ERROR) {
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    }
                                } else {
                                    mChangeVolume = true;
                                    mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = JCUtils.stringForTime(mSeekTimePosition);
                        String totalTime = JCUtils.stringForTime(totalTimeDuration);

                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    }
                    if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(-deltaY, volumePercent);
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    if (mChangePosition) {
                        onEvent(JCUserAction.ON_TOUCH_SCREEN_SEEK_POSITION);
                        JCMediaManager.instance().simpleExoPlayer.seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress(progress);
                    }
                    if (mChangeVolume) {
                        onEvent(JCUserAction.ON_TOUCH_SCREEN_SEEK_VOLUME);
                    }
                    startProgressTimer();
                    break;
            }
        }
        return false;
    }

    public int widthRatio = 16;
    public int heightRatio = 9;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentScreen == SCREEN_WINDOW_FULLSCREEN || currentScreen == SCREEN_WINDOW_TINY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (widthRatio != 0 && heightRatio != 0) {
            int specWidth = MeasureSpec.getSize(widthMeasureSpec);
            int specHeight = (int) ((specWidth * (float) heightRatio) / widthRatio);
            setMeasuredDimension(specWidth, specHeight);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(specHeight, MeasureSpec.EXACTLY);
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

    }

    public void setOnVideoFinishedCallback(OnVideoFinishedCallback onVideoFinishedCallback) {
        this.onVideoFinishedCallback = onVideoFinishedCallback;
    }

    public void setOnFullScreenStateChanged(OnFullScreenStateChanged onFullScreenStateChanged) {
        this.onFullScreenStateChanged = onFullScreenStateChanged;
    }

    public void initTextureView() {
        removeTextureView();
        JCMediaManager.textureView = new JCResizeTextureView(getContext());
        JCMediaManager.textureView.setSurfaceTextureListener(JCMediaManager.instance());
    }

    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(JCMediaManager.textureView, layoutParams);
    }

    public void removeTextureView() {
        JCMediaManager.savedSurfaceTexture = null;
        if (JCMediaManager.textureView != null && JCMediaManager.textureView.getParent() != null) {
            ((ViewGroup) JCMediaManager.textureView.getParent()).removeView(JCMediaManager.textureView);
        }
    }

    public void setUiWitStateAndScreen(int state) {
        currentState = state;
        switch (currentState) {
            case CURRENT_STATE_NORMAL:
                cancelProgressTimer();
                if (isCurrentJcvd()) {//这个if是无法取代的，否则进入全屏的时候会releaseMediaPlayer
                    JCMediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_PREPARING:
                resetProgressAndTime();
                break;
            case CURRENT_STATE_PLAYING:
            case CURRENT_STATE_PAUSE:
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                startProgressTimer();
                break;
            case CURRENT_STATE_ERROR:
                cancelProgressTimer();
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                progressBar.setProgress(100);
                currentTimeTextView.setText(totalTimeTextView.getText());
                break;
        }
    }

    public void startProgressTimer() {
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    public void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }
    }

    public void onPrepared() {
        Log.i(TAG, "onPrepared " + " [" + this.hashCode() + "] ");

        if (currentState != CURRENT_STATE_PREPARING) return;
        if (seekToInAdvance != -1) {
            JCMediaManager.instance().simpleExoPlayer.seekTo(seekToInAdvance);
            seekToInAdvance = -1;
        } else {
            int position = JCUtils.getSavedProgress(getContext(), url);
            if (position != 0) {
                JCMediaManager.instance().simpleExoPlayer.seekTo(position);
            }
        }
        startProgressTimer();
        setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
    }

    public void clearFullscreenLayout() {
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(FULLSCREEN_ID);
        View oldT = vp.findViewById(TINY_ID);
        if (oldF != null) {
            vp.removeView(oldF);
        }
        if (oldT != null) {
            vp.removeView(oldT);
        }
        showSupportActionBar(getContext());
    }

    public void onAutoCompletion() {
        //加上这句，避免循环播放video的时候，内存不断飙升。
        Runtime.getRuntime().gc();
        Log.i(TAG, "onAutoCompletion " + " [" + this.hashCode() + "] ");
        onEvent(JCUserAction.ON_AUTO_COMPLETE);
        dismissVolumeDialog();
        dismissProgressDialog();
        setUiWitStateAndScreen(CURRENT_STATE_AUTO_COMPLETE);

        if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
            backPress();
        }
        JCUtils.saveProgress(getContext(), url, 0);
    }

    public void onCompletion() {
        Log.i(TAG, "onCompletion " + " [" + this.hashCode() + "] ");
        //save position
        if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE) {
            int position = getCurrentPositionWhenPlaying();
//            int duration = getDuration();
            JCUtils.saveProgress(getContext(), url, position);
        }
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        // 清理缓存变量
        textureViewContainer.removeView(JCMediaManager.textureView);
        JCMediaManager.instance().currentVideoWidth = 0;
        JCMediaManager.instance().currentVideoHeight = 0;

        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        JCUtils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearFullscreenLayout();
        JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(NORMAL_ORIENTATION);
    }

    //退出全屏和小窗的方法
    public void playOnThisJcvd() {
        Log.i(TAG, "playOnThisJcvd " + " [" + this.hashCode() + "] ");
        onEvent(currentScreen == JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN ?
                JCUserAction.ON_QUIT_FULLSCREEN :
                JCUserAction.ON_QUIT_TINYSCREEN);
        //1.清空全屏和小窗的jcvd
        currentState = JCVideoPlayerManager.getSecondFloor().currentState;
        clearFloatScreen();
        //2.在本jcvd上播放
        setUiWitStateAndScreen(currentState);
        addTextureView();
    }

    public void clearFloatScreen() {
        JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(NORMAL_ORIENTATION);
//        JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        showSupportActionBar(getContext());
        JCVideoPlayer secJcvd = JCVideoPlayerManager.getCurrentJcvd();
        secJcvd.textureViewContainer.removeView(JCMediaManager.textureView);
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        vp.removeView(secJcvd);
//        secJcvd.onCompletion();
        JCVideoPlayerManager.setSecondFloor(null);
    }

    public static long lastAutoFullscreenTime = 0;

    //重力感应的时候调用的函数，
    public void autoFullscreen(float x) {
        if (isCurrentJcvd()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen != SCREEN_WINDOW_FULLSCREEN
                && currentScreen != SCREEN_WINDOW_TINY) {
            if (x > 0) {
                JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            }
            startWindowFullscreen();
        }
    }

    public void autoQuitFullscreen() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentJcvd()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen == SCREEN_WINDOW_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            backPress();
        }
    }

    /**
     *以后可能用到这个函数
     */
//    public void onBufferingUpdate(int percent) {
//        if (currentState != CURRENT_STATE_NORMAL && currentState != CURRENT_STATE_PREPARING) {
//            Log.v(TAG, "onBufferingUpdate " + percent + " [" + this.hashCode() + "] ");
//            setTextAndProgress(percent);
//        }
//    }

    /**
     * 以后可能用到这个函数
     */
    public void onSeekComplete() {
    }

    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && what != -38) {
            setUiWitStateAndScreen(CURRENT_STATE_ERROR);
            if (isCurrentJcvd()) {
                JCMediaManager.instance().releaseMediaPlayer();
            }
        }
    }

    /**
     * 以后可能用到这个函数
     */
//    public void onInfo(int what, int extra) {
//        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
//        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
//            JCMediaManager.instance().backUpBufferState = currentState;
//            setUiWitStateAndScreen(CURRENT_STATE_PLAYING_BUFFERING_START);
//            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
//        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
//            if (JCMediaManager.instance().backUpBufferState != -1) {
//                setUiWitStateAndScreen(JCMediaManager.instance().backUpBufferState);
//                JCMediaManager.instance().backUpBufferState = -1;
//            }
//            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
//        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
//            JCMediaManager.instance().videoRotation = extra;
//            JCMediaManager.textureView.setRotation(extra);
//            cacheImageView.setRotation(JCMediaManager.instance().videoRotation);
//            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED");
//
//
//        }
//    }
    public void onVideoSizeChanged() {
        Log.i(TAG, "onVideoSizeChanged " + " [" + this.hashCode() + "] ");
        JCMediaManager.textureView.setVideoSize(JCMediaManager.instance().getVideoSize());
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar.getTag() == null)
            return;


        if (((Integer) seekBar.getTag()) == 1)
            JCMediaManager.instance().simpleExoPlayer.seekTo((JCMediaManager.instance().simpleExoPlayer.getDuration() * progress) / 1000L);
    }


    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStartTrackingTouch [" + this.hashCode() + "] ");
        seekBar.setTag(1);
        cancelProgressTimer();
        ViewParent vpdown = getParent();
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true);
            vpdown = vpdown.getParent();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStopTrackingTouch [" + this.hashCode() + "] ");
        seekBar.setTag(0);
        onEvent(JCUserAction.ON_SEEK_POSITION);
        startProgressTimer();
        ViewParent vpup = getParent();
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false);
            vpup = vpup.getParent();
        }
        if (currentState != CURRENT_STATE_PLAYING &&
                currentState != CURRENT_STATE_PAUSE) return;
        int time = seekBar.getProgress() * getDuration() / 100;
        JCMediaManager.instance().simpleExoPlayer.seekTo(time);
        Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
    }

    public static boolean backPress() {
        isFullscreen = false;

        Log.i(TAG, "backPress");
        if ((System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) < FULL_SCREEN_NORMAL_DELAY)
            return false;
        if (JCVideoPlayerManager.getSecondFloor() != null) {
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            JCVideoPlayerManager.getFirstFloor().playOnThisJcvd();
            return true;
        } else if (JCVideoPlayerManager.getFirstFloor() != null &&
                (JCVideoPlayerManager.getFirstFloor().currentScreen == SCREEN_WINDOW_FULLSCREEN ||
                        JCVideoPlayerManager.getFirstFloor().currentScreen == SCREEN_WINDOW_TINY)) {//以前我总想把这两个判断写到一起，这分明是两个独立是逻辑
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            //直接退出全屏和小窗
            JCVideoPlayerManager.getCurrentJcvd().currentState = CURRENT_STATE_NORMAL;
            JCVideoPlayerManager.getFirstFloor().clearFloatScreen();
            JCMediaManager.instance().releaseMediaPlayer();
            JCVideoPlayerManager.setFirstFloor(null);
            return true;
        }
        return false;
    }

    public void startWindowFullscreen() {
        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");
        hideSupportActionBar(getContext());
        JCUtils.getAppCompActivity(getContext()).setRequestedOrientation(FULLSCREEN_ORIENTATION);

        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
//        ((ViewGroup)JCMediaManager.textureView.getParent()).removeView(JCMediaManager.textureView);
        textureViewContainer.removeView(JCMediaManager.textureView);
        try {
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(getContext());
            jcVideoPlayer.setId(FULLSCREEN_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.addTextureView();
            JCVideoPlayerManager.setSecondFloor(jcVideoPlayer);
//            final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void startWindowTiny() {
        Log.i(TAG, "startWindowTiny " + " [" + this.hashCode() + "] ");
        onEvent(JCUserAction.ON_ENTER_TINYSCREEN);
        if (currentState == CURRENT_STATE_NORMAL || currentState == CURRENT_STATE_ERROR) return;
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(getContext()))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(TINY_ID);
        if (old != null) {
            vp.removeView(old);
        }
        textureViewContainer.removeView(JCMediaManager.textureView);

        try {
            Constructor<JCVideoPlayer> constructor = (Constructor<JCVideoPlayer>) JCVideoPlayer.this.getClass().getConstructor(Context.class);
            JCVideoPlayer jcVideoPlayer = constructor.newInstance(getContext());
            jcVideoPlayer.setId(TINY_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(400, 400);
            lp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_TINY, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.addTextureView();
            JCVideoPlayerManager.setSecondFloor(jcVideoPlayer);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE || currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
//                Log.v(TAG, "onProgressUpdate " + position + "/" + duration + " [" + this.hashCode() + "] ");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            setTextAndProgress();
                        } catch (Exception ex) {
                        }
                    }
                });
            }
        }
    }

    public int getCurrentPositionWhenPlaying() {
        int position = 0;
        if (currentState == CURRENT_STATE_PLAYING ||
                currentState == CURRENT_STATE_PAUSE ||
                currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
            try {
                position = (int) JCMediaManager.instance().simpleExoPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    public int getDuration() {
        int duration = 0;
        try {
            duration = (int) JCMediaManager.instance().simpleExoPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        } catch (NullPointerException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    public void setTextAndProgress() {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        long secProgress = JCMediaManager.instance().simpleExoPlayer.getBufferedPosition();
        setProgressAndTime(progress, progressBarValue(secProgress), position, duration);
    }

    public void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) progressBar.setProgress(progress);
        }
        if (secProgress > 95) secProgress = 100;
        if (secProgress != 0) progressBar.setSecondaryProgress(secProgress);
        if (currentTime != 0) currentTimeTextView.setText(JCUtils.stringForTime(currentTime));
        totalTimeTextView.setText(JCUtils.stringForTime(totalTime));

        if (onVideoFinishedCallback == null)
            return;
        
        if(totalTime <= TimeUnit.SECONDS.toMillis(1))
            return; // Media isn't prepared yet
       
        if (totalTime - currentTime <= TimeUnit.SECONDS.toMillis(2)) {
            onVideoFinishedCallback.done();
            if (onCountdownTimerFinish != null) {
                startCountdownTimer(onCountdownTimerFinish);
                setOnCountdownTimerFinish(null);
            }
        }
    }

    public void resetProgressAndTime() {
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(JCUtils.stringForTime(0));
        totalTimeTextView.setText(JCUtils.stringForTime(0));
    }

    private int progressBarValue(long position) {
        long duration = JCMediaManager.instance().simpleExoPlayer == null ?
                C.TIME_UNSET : JCMediaManager.instance().simpleExoPlayer.getDuration();
        return duration == C.TIME_UNSET || duration == 0 ? 0
                : (int) ((position * 100) / duration);
    }

    public static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    Log.d(TAG, "AUDIOFOCUS_LOSS [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (JCMediaManager.instance().simpleExoPlayer != null &&
                            JCMediaManager.instance().simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_READY) {
                        JCMediaManager.instance().simpleExoPlayer.setPlayWhenReady(false);
                    }
                    Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };

    public void release() {
        if (url.equals(JCMediaManager.CURRENT_PLAYING_URL) &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
            //如果正在全屏播放就不能手动调用release
            if (JCVideoPlayerManager.getSecondFloor() != null &&
                    JCVideoPlayerManager.getSecondFloor().currentScreen != SCREEN_WINDOW_FULLSCREEN) {
                Log.d(TAG, "release [" + this.hashCode() + "]");
                releaseAllVideos();
            } else if (JCVideoPlayerManager.getSecondFloor() == null) {
                releaseAllVideos();
            }
        }
    }

    //isCurrentJcvd and isCurrenPlayUrl should be two logic methods,isCurrentJcvd is for different jcvd with same
    //url when fullscreen or tiny screen. isCurrenPlayUrl is to find where is myself when back from tiny screen.
    //Sometimes they are overlap.
    public boolean isCurrentJcvd() {//虽然看这个函数很不爽，但是干不掉
        return JCVideoPlayerManager.getCurrentJcvd() != null
                && JCVideoPlayerManager.getCurrentJcvd() == this;
    }

//    public boolean isCurrenPlayingUrl() {
//        return url.equals(JCMediaManager.CURRENT_PLAYING_URL);
//    }

    public static void releaseAllVideos() {
        if ((System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
            Log.d(TAG, "releaseAllVideos");
            JCVideoPlayerManager.completeAll();
            JCMediaManager.instance().releaseMediaPlayer();
        }
    }

    public static void setJcUserAction(JCUserAction jcUserEvent) {
        JC_USER_EVENT = jcUserEvent;
    }

    public void onEvent(int type) {
        if (JC_USER_EVENT != null && isCurrentJcvd()) {
            JC_USER_EVENT.onEvent(type, url, currentScreen, objects);
        }
    }

    public static void startFullscreen(Context context, Class _class, String url, Object... objects) {
        hideSupportActionBar(context);
        JCUtils.getAppCompActivity(context).setRequestedOrientation(FULLSCREEN_ORIENTATION);
        ViewGroup vp = (ViewGroup) (JCUtils.scanForActivity(context))//.getWindow().getDecorView();
                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(JCVideoPlayer.FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        try {
            Constructor<JCVideoPlayer> constructor = _class.getConstructor(Context.class);
            final JCVideoPlayer jcVideoPlayer = constructor.newInstance(context);
            jcVideoPlayer.setId(JCVideoPlayer.FULLSCREEN_ID);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(jcVideoPlayer, lp);
//            final Animation ra = AnimationUtils.loadAnimation(context, R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);
            jcVideoPlayer.setUp(url, JCVideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            jcVideoPlayer.startButton.performClick();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    public void startCountdownTimer(final OnCountdownTimerFinish onCountdownTimerFinish) {
        countdownTimerTextView.setVisibility(VISIBLE);
        startButton.setVisibility(GONE);
        new CountDownTimer(6000, 1000) {

            public void onTick(long millisUntilFinished) {
                countdownTimerTextView.setText(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished)));
            }

            public void onFinish() {
                countdownTimerTextView.setVisibility(GONE);
                startButton.setVisibility(VISIBLE);
                onCountdownTimerFinish.finished();
            }
        }.start();
    }

    public static void hideSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.hide();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public static void showSupportActionBar(Context context) {
        /*if (ACTION_BAR_EXIST) {
            ActionBar ab = JCUtils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.show();
            }
        }
        if (TOOL_BAR_EXIST) {
            JCUtils.getAppCompActivity(context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }*/
    }

    public static class JCAutoFullscreenListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {//可以得到传感器实时测量出来的变化值
            final float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            float z = event.values[SensorManager.DATA_Z];
            //过滤掉用力过猛会有一个反向的大数值
            if (((x > -15 && x < -10) || (x < 15 && x > 10)) && Math.abs(y) < 1.5) {
                if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000) {
                    if (JCVideoPlayerManager.getCurrentJcvd() != null) {
                        JCVideoPlayerManager.getCurrentJcvd().autoFullscreen(x);
                    }
                    lastAutoFullscreenTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public static void clearSavedProgress(Context context, String url) {
        JCUtils.clearSavedProgress(context, url);
    }

    public void showWifiDialog() {
    }

    public void showProgressDialog(float deltaX,
                                   String seekTime, int seekTimePosition,
                                   String totalTime, int totalTimeDuration) {
    }

    public void dismissProgressDialog() {

    }

    public void showVolumeDialog(float deltaY, int volumePercent) {

    }

    public void dismissVolumeDialog() {

    }


    public abstract int getLayoutId();


    public interface OnVideoFinishedCallback {
        void done();
    }

    public interface OnFullScreenStateChanged {
        void stateChanged(boolean isFullscreen);
    }

    public interface OnCountdownTimerFinish {
        void finished();
    }
}
