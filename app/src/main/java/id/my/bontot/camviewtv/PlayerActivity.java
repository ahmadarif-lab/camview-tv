package id.my.bontot.camviewtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.List;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private CameraStore store;
    private Camera camera;
    private List<Camera> cameras;
    private ExoPlayer player;
    private PlayerView video;
    private View overlay;
    private LinearLayout messagePanel;
    private TextView message, state, title;
    private ProgressBar spinner;
    private Button audioButton, qualityButton, reconnectButton;
    private boolean muted=true, failed, resumed;
    private final Runnable hide = ()->{if(player!=null && player.getPlaybackState()==Player.STATE_READY && !failed)overlay.setVisibility(View.INVISIBLE);};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);Ui.immersive(this);
        // Media3 errors can contain the authenticated RTSP URI. Keep credentials out of logcat.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);
        store=new CameraStore(this);cameras=store.load();
        String wanted=getIntent().getStringExtra("camera_ip");for(Camera c:cameras)if(c.ip.equals(wanted)){camera=c;break;}
        if(camera==null){finish();return;}
        muted=getPreferences(MODE_PRIVATE).getBoolean("muted",true);
        if(saved!=null){muted=saved.getBoolean("muted",muted);camera.streamPath=saved.getString("path",camera.streamPath);}
        buildUi();
    }
    @Override protected void onStart(){super.onStart();resumed=true;if(camera!=null)connect();}
    @Override protected void onStop(){resumed=false;main.removeCallbacks(hide);release();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("muted",muted);if(camera!=null)out.putString("path",camera.streamPath);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.immersive(this);}
    private int d(float value){return Ui.dp(this,value);}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void buildUi(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setOnClickListener(v->showControls());
        video=new PlayerView(this);video.setUseController(false);video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);video.setKeepScreenOn(true);video.setFocusable(false);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        messagePanel=new LinearLayout(this);messagePanel.setOrientation(LinearLayout.VERTICAL);messagePanel.setGravity(Gravity.CENTER);messagePanel.setPadding(d(30),d(24),d(30),d(24));messagePanel.setBackground(Ui.rounded(this,0xDE15252D,16));
        spinner=new ProgressBar(this);messagePanel.addView(spinner,new LinearLayout.LayoutParams(d(40),d(40)));
        message=Ui.text(this,"Connecting to camera…",18,Color.WHITE,false);message.setGravity(Gravity.CENTER);message.setMaxWidth(d(630));message.setMinWidth(d(300));message.setPadding(0,d(16),0,0);messagePanel.addView(message,new LinearLayout.LayoutParams(-2,-2));
        FrameLayout.LayoutParams msgLp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);root.addView(messagePanel,msgLp);
        FrameLayout controls=new FrameLayout(this);overlay=controls;root.addView(controls,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=row();top.setPadding(d(32),d(22),d(32),d(20));top.setBackgroundColor(0xA613222A);
        title=Ui.text(this,camera.name,22,Color.WHITE,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        state=Ui.text(this,"CONNECTING  ·  "+camera.ip,11,0xFFAFDDE2,true);top.addView(state);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);controls.addView(top,topLp);
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(d(32),d(18),d(32),d(22));bottom.setBackgroundColor(0xC613222A);
        TextView hint=Ui.text(this,"Use the remote to reveal controls · Back to your cameras",11,0xFFC5D7DD,false);bottom.addView(hint);
        LinearLayout buttons=row();buttons.setPadding(0,d(12),0,0);bottom.addView(buttons);
        Button back=Ui.button(this,"‹ Cameras",false);back.setOnClickListener(v->finish());buttons.addView(back,new LinearLayout.LayoutParams(-2,d(43)));
        qualityButton=control(buttons,qualityLabel(),this::quality);
        audioButton=control(buttons,muted?"Audio: Off":"Audio: On",()->{muted=!muted;getPreferences(MODE_PRIVATE).edit().putBoolean("muted",muted).apply();if(player!=null)player.setVolume(muted?0:1);audioButton.setText(muted?"Audio: Off":"Audio: On");showControls();});
        reconnectButton=control(buttons,"Reconnect",()->{connect();showControls();});
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);controls.addView(bottom,bottomLp);
        setContentView(root);qualityButton.requestFocus();
    }
    private Button control(LinearLayout row,String label,Runnable action){Button b=Ui.button(this,label,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,d(43));lp.leftMargin=d(12);row.addView(b,lp);b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,focus)->{if(focus)showControls();});return b;}
    private String qualityLabel(){return camera.streamPath.equals("/stream1")?"Quality: HD":camera.streamPath.equals("/stream2")?"Quality: SD":"Quality: Custom";}
    private void quality(){
        main.removeCallbacks(hide);
        if (!camera.streamPath.equals("/stream1") && !camera.streamPath.equals("/stream2")) {
            new AlertDialog.Builder(this).setTitle("Custom camera stream").setMessage("Quality is defined by this camera’s RTSP path. Change the path in Camera settings using the manufacturer’s main or sub-stream URL.").setPositiveButton("Close",(dialog,which)->showControls()).setOnCancelListener(dialog->showControls()).show();
            return;
        }
        String[] labels={"HD · /stream1","SD · /stream2"};
        int selected=camera.streamPath.equals("/stream1")?0:camera.streamPath.equals("/stream2")?1:-1;
        new AlertDialog.Builder(this).setTitle("Video quality").setSingleChoiceItems(labels,selected,(dialog,which)->{
            camera.streamPath=which==0?"/stream1":"/stream2";store.save(cameras);qualityButton.setText(qualityLabel());dialog.dismiss();connect();showControls();
        }).setNegativeButton("Cancel",(dialog,which)->showControls()).setOnCancelListener(dialog->showControls()).show();
    }
    private void release(){if(player!=null){video.setPlayer(null);player.release();player=null;}}
    private void connect(){
        if(!resumed)return;release();failed=false;spinner.setVisibility(View.VISIBLE);message.setText("Connecting to camera…");messagePanel.setVisibility(View.VISIBLE);state.setText("CONNECTING  ·  "+camera.ip);
        player=new ExoPlayer.Builder(this).build();player.setVolume(muted?0:1);video.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int playbackState){
                if(playbackState==Player.STATE_READY){failed=false;messagePanel.setVisibility(View.GONE);state.setText("● LIVE  ·  "+(camera.streamPath.equals("/stream2")?"SD":"HD / CUSTOM")+"  ·  "+camera.ip);showControls();}
                else if(playbackState==Player.STATE_BUFFERING && !failed){spinner.setVisibility(View.VISIBLE);message.setText("Buffering camera video…");messagePanel.setVisibility(View.VISIBLE);}
                else if(playbackState==Player.STATE_ENDED){showFailure("The camera stream ended. Select Reconnect.");}
            }
            @Override public void onRenderedFirstFrame(){main.postDelayed(()->{if(player!=null && resumed)SnapshotStore.capture(PlayerActivity.this,video,camera.ip,main);},400);}
            @Override public void onPlayerError(PlaybackException error){
                String causeText="";Throwable cause=error;for(int i=0;cause!=null&&i<8;i++,cause=cause.getCause())causeText+=" "+String.valueOf(cause.getMessage());
                String lower=causeText.toLowerCase(java.util.Locale.ROOT);
                if(lower.contains("401")||lower.contains("unauthorized")||lower.contains("403"))showFailure("The camera rejected these credentials. Check the username and password in Camera settings.");
                else if(lower.contains("404"))showFailure("Stream path not found. Check the RTSP path supplied by your camera manufacturer.");
                else if(error.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED || error.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)showFailure("This TV cannot decode the video. Try SD quality or H.264 in camera settings.");
                else showFailure("Unable to open video. Check the camera, network, RTSP port and credentials, then select Reconnect.");
            }
        });
        try {
            RtspMediaSource source=new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(12000).createMediaSource(MediaItem.fromUri(camera.rtspUri()));
            player.setMediaSource(source);player.prepare();player.setPlayWhenReady(true);
        }catch(Exception error){showFailure("Invalid stream address. Check the IP, port and path in Camera settings.");}
        showControls();
    }
    private void showFailure(String text){failed=true;spinner.setVisibility(View.GONE);message.setText(text);messagePanel.setVisibility(View.VISIBLE);state.setText("DISCONNECTED  ·  "+camera.ip);overlay.setVisibility(View.VISIBLE);main.removeCallbacks(hide);reconnectButton.requestFocus();}
    private void showControls(){if(overlay==null)return;overlay.setVisibility(View.VISIBLE);main.removeCallbacks(hide);if(!failed)main.postDelayed(hide,8000);}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_BACK)return super.onKeyDown(keyCode,event);
        if(keyCode==KeyEvent.KEYCODE_DPAD_CENTER||keyCode==KeyEvent.KEYCODE_ENTER||keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT||keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_DOWN){
            boolean hidden=overlay!=null && overlay.getVisibility()!=View.VISIBLE;showControls();if(hidden){qualityButton.requestFocus();return true;}
        }
        if(keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){muted=!muted;if(player!=null)player.setVolume(muted?0:1);audioButton.setText(muted?"Audio: Off":"Audio: On");showControls();return true;}
        return super.onKeyDown(keyCode,event);
    }
}
