package id.my.bontot.camviewtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Independent, lifecycle-bound players. Unassigned slots never open camera connections. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MultiViewActivity extends Activity {
    private static final int BG=Ui.BG, PANEL=Color.WHITE, TEAL=Ui.TEAL,
            TEXT=Ui.INK, MUTED=Ui.MUTED, BORDER=Ui.LINE;
    private static final String[] LAYOUT_NAMES={"Side by side", "Stacked", "2 × 2 grid", "3 × 2 grid", "3 × 3 grid"};
    private static final int[] CAPACITY={2,2,4,6,9}, COLUMNS={2,1,2,3,3};
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Camera> available=new ArrayList<>(), selected=new ArrayList<>();
    private final List<Tile> tiles=new ArrayList<>();
    private SharedPreferences prefs;
    private CameraStore store;
    private LinearLayout grid;
    private TextView subtitle;
    private Button layoutButton,camerasButton,qualityButton,audioButton,fitButton;
    private int layout=0, activeTile=0, generation=0;
    private boolean sd=true, audio=false, fill=false, started=false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.immersive(this);
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF);
        store=new CameraStore(this);
        prefs=getSharedPreferences("multi_view", MODE_PRIVATE);
        layout=Math.max(0,Math.min(4,prefs.getInt("layout",0)));
        sd=prefs.getBoolean("sd",true);
        fill=prefs.getBoolean("fill",false);
        loadSelection();
        int requested=getIntent().getIntExtra("layout",-1);
        if(requested>=0&&requested<LAYOUT_NAMES.length){layout=requested;saveSelection();}
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart(); started=true;
        loadSelection(); rebuild();
    }

    @Override protected void onStop() {
        started=false; releasePlayers(); super.onStop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null); super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus); if(focus)Ui.immersive(this);
    }

    private int d(float n){return Ui.dp(this,n);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private GradientDrawable shape(int color,int radius,int strokeColor,int stroke){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(d(radius));if(stroke>0)b.setStroke(d(stroke),strokeColor);return b;}
    private StateListDrawable buttonBackground(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(Ui.PALE,10,TEAL,2));s.addState(new int[]{android.R.attr.state_pressed},shape(Ui.PALE,10,TEAL,2));s.addState(new int[]{},shape(PANEL,10,BORDER,1));return s;}
    private Button button(String label,Runnable click){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setTextColor(TEXT);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(d(16),0,d(16),0);b.setBackground(buttonBackground());b.setFocusable(true);b.setStateListAnimator(null);b.setOnClickListener(v->click.run());return b;}

    private void loadSelection() {
        available.clear();
        for(Camera c:loadCameras())if(c.hasAccount())available.add(c);
        selected.clear();
        String encoded=isPreview()?null:prefs.getString("selection",null);
        if(encoded==null){for(int i=0;i<Math.min(9,available.size());i++)selected.add(available.get(i));}
        else {
            try {
                JSONArray ids=new JSONArray(encoded);
                for(int i=0;i<Math.min(9,ids.length());i++){
                    String id=ids.getString(i);
                    for(Camera c:available)if(SnapshotStore.key(c.ip).equals(id)){selected.add(c);break;}
                }
            }catch(Exception ignored){if(!available.isEmpty())selected.add(available.get(0));}
        }
        if(selected.isEmpty()&&!available.isEmpty())selected.add(available.get(0));
        if(encoded==null && selected.size()>CAPACITY[layout])fitLayout();
    }

    protected boolean isPreview(){return false;}
    protected List<Camera> loadCameras(){return store.load();}
    protected Bitmap previewFrame(Camera camera){return null;}

    private void saveSelection() {
        if(isPreview())return;
        JSONArray ids=new JSONArray();
        for(Camera c:selected)ids.put(SnapshotStore.key(c.ip));
        prefs.edit().putString("selection",ids.toString()).putInt("layout",layout).putBoolean("sd",sd).putBoolean("fill",fill).apply();
    }

    private void fitLayout(){if(selected.size()>6)layout=4;else if(selected.size()>4)layout=3;else if(selected.size()>2)layout=2;}

    private void buildUi() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(d(24),d(18),d(24),d(16));
        LinearLayout header=row();
        Button back=button("‹ My home",this::finish);header.addView(back,new LinearLayout.LayoutParams(-2,d(40)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(d(18),0,0,0);labels.addView(text("Multi-view",24,TEXT,true));
        subtitle=text("Your cameras, together",11,MUTED,false);labels.addView(subtitle);header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=text("●  LOCAL NETWORK",10,TEAL,true);header.addView(local);
        LinearLayout.LayoutParams headerLp=new LinearLayout.LayoutParams(-1,d(62));headerLp.bottomMargin=d(12);root.addView(header,headerLp);
        grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);root.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=row();
        layoutButton=button("Layout",this::chooseLayout);addControl(controls,layoutButton,false);
        camerasButton=button("Cameras",this::chooseCameras);addControl(controls,camerasButton,true);
        qualityButton=button("Quality",()->{sd=!sd;saveSelection();rebuild();});addControl(controls,qualityButton,true);
        audioButton=button("Audio",()->{audio=!audio;updateAudio();updateLabels();});addControl(controls,audioButton,true);
        fitButton=button("Fit video",()->{fill=!fill;for(Tile t:tiles)if(t.video!=null)t.video.setResizeMode(fill?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);saveSelection();updateLabels();});addControl(controls,fitButton,true);
        Button reconnect=button("Reconnect",this::rebuild);addControl(controls,reconnect,true);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,d(44));controlsLp.topMargin=d(14);root.addView(controls,controlsLp);
        TextView hint=text("OK for full screen  ·  Hold OK to rearrange  ·  Audio follows the highlighted camera",10,MUTED,false);hint.setPadding(d(2),d(10),0,0);root.addView(hint);
        setContentView(root);layoutButton.requestFocus();
    }

    private void addControl(LinearLayout row,Button b,boolean margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(margin)p.leftMargin=d(10);row.addView(b,p);}

    private void updateLabels() {
        layoutButton.setText("Layout · "+LAYOUT_NAMES[layout]);
        camerasButton.setText("Cameras · "+Math.min(selected.size(),CAPACITY[layout]));
        qualityButton.setText("Quality · "+(sd?"SD":"HD"));
        audioButton.setText(audio?"Audio · Selected":"Audio · Off");
        fitButton.setText(fill?"Fill tiles":"Fit video");
        int shown=Math.min(selected.size(),CAPACITY[layout]);
        subtitle.setText(shown+" of "+selected.size()+" selected cameras  ·  "+(sd?"SD for smooth viewing":"HD quality"));
    }

    private void releasePlayers() {
        generation++;
        for(Tile tile:tiles)tile.release();
    }

    private void rebuild() {
        if(grid==null)return;
        releasePlayers();tiles.clear();grid.removeAllViews();
        int columns=COLUMNS[layout],count=CAPACITY[layout],rows=count/columns;
        activeTile=Math.min(activeTile,Math.max(0,Math.min(selected.size(),count)-1));
        for(int r=0;r<rows;r++){
            LinearLayout line=row();
            for(int c=0;c<columns;c++){
                int index=r*columns+c;
                Camera camera=index<selected.size()?selected.get(index):null;
                Tile tile=new Tile(index,camera);tiles.add(tile);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(c>0)p.leftMargin=d(10);line.addView(tile.root,p);
            }
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);if(r>0)p.topMargin=d(10);grid.addView(line,p);
        }
        updateLabels();
        if(started){int token=generation;for(Tile tile:tiles)if(tile.camera!=null)main.postDelayed(()->{if(started&&token==generation)tile.start();},tile.index*350L);}
    }

    private void chooseLayout() {
        String[] options={"2 cameras · Side by side", "2 cameras · Stacked", "4 cameras · 2 × 2", "6 cameras · 3 × 2", "9 cameras · 3 × 3"};
        new AlertDialog.Builder(this).setTitle("Choose your layout").setSingleChoiceItems(options,layout,(dialog,index)->{layout=index;saveSelection();dialog.dismiss();rebuild();}).setNegativeButton("Cancel",null).show();
    }

    private void chooseCameras() {
        if(available.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Set up your cameras").setMessage("Set up camera credentials in My home, then choose the cameras to display together.").setPositiveButton("My home",(dialog,which)->finish()).setNegativeButton("Cancel",null).show();return;
        }
        String[] names=new String[available.size()];boolean[] checked=new boolean[names.length];
        for(int i=0;i<names.length;i++){Camera c=available.get(i);names[i]=c.name+"  ·  "+c.ip;for(Camera s:selected)if(s.ip.equals(c.ip))checked[i]=true;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose cameras · up to 9")
                .setMultiChoiceItems(names,checked,(d,index,on)->checked[index]=on)
                .setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int count=0;for(boolean c:checked)if(c)count++;
            if(count<1||count>9){Toast.makeText(this,"Choose between 1 and 9 cameras",Toast.LENGTH_SHORT).show();return;}
            selected.clear();for(int i=0;i<checked.length;i++)if(checked[i])selected.add(available.get(i));
            if(selected.size()>CAPACITY[layout])fitLayout();saveSelection();dialog.dismiss();rebuild();
        }));dialog.show();
    }

    private void updateAudio(){for(Tile t:tiles)if(t.player!=null)t.player.setVolume(audio&&t.index==activeTile?1:0);}

    private void chooseSlot(int slot) {
        if(slot>=selected.size()) { chooseCameras(); return; }
        String[] names=new String[available.size()];
        for(int i=0;i<names.length;i++)names[i]=available.get(i).name;
        new AlertDialog.Builder(this).setTitle("Camera in slot "+(slot+1)).setItems(names,(dialog,index)->{
            Camera picked=available.get(index),previous=selected.get(slot);
            int existing=-1;
            for(int i=0;i<selected.size();i++)if(selected.get(i).ip.equals(picked.ip)){existing=i;break;}
            selected.set(slot,picked);
            if(existing>=0&&existing!=slot)selected.set(existing,previous);
            saveSelection();rebuild();
        }).setNegativeButton("Cancel",null).show();
    }

    private String streamUri(Camera camera) {
        String path=camera.streamPath;
        if(path.equals("/stream1")||path.equals("/stream2"))path=sd?"/stream2":"/stream1";
        else if(path.equals("/stream6")||path.equals("/stream7"))path=sd?"/stream7":"/stream6";
        Camera stream=new Camera(camera.ip);stream.username=camera.username;stream.password=camera.password;stream.rtspPort=camera.rtspPort;stream.streamPath=path;
        return stream.rtspUri();
    }

    private final class Tile {
        final int index;
        final Camera camera;
        final FrameLayout root;
        PlayerView video;
        ExoPlayer player;
        TextView status,message;
        ProgressBar spinner;
        LinearLayout loading;
        boolean failed;

        Tile(int index,Camera camera) {
            this.index=index;this.camera=camera;
            root=new FrameLayout(MultiViewActivity.this);root.setBackground(shape(PANEL,12,0,0));root.setClipToOutline(true);root.setFocusable(true);root.setClickable(true);root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            StateListDrawable outline=new StateListDrawable();outline.addState(new int[]{android.R.attr.state_focused},shape(Color.TRANSPARENT,12,TEAL,3));outline.addState(new int[]{},shape(Color.TRANSPARENT,12,BORDER,1));root.setForeground(outline);
            if(camera==null){
                LinearLayout empty=new LinearLayout(MultiViewActivity.this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);
                TextView plus=text("+",34,TEAL,false);plus.setGravity(Gravity.CENTER);empty.addView(plus);
                TextView title=text("Add a camera",14,MUTED,true);title.setGravity(Gravity.CENTER);empty.addView(title);
                TextView detail=text("Slot "+(index+1),10,MUTED,false);detail.setGravity(Gravity.CENTER);detail.setPadding(0,d(6),0,0);empty.addView(detail);
                root.addView(empty,new FrameLayout.LayoutParams(-1,-1));root.setOnClickListener(v->chooseCameras());return;
            }
            video=new PlayerView(MultiViewActivity.this);video.setUseController(false);video.setKeepScreenOn(true);video.setFocusable(false);video.setResizeMode(fill?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:AspectRatioFrameLayout.RESIZE_MODE_FIT);video.setBackgroundColor(PANEL);video.setShutterBackgroundColor(PANEL);FrameLayout.LayoutParams videoLp=new FrameLayout.LayoutParams(-1,-1);videoLp.topMargin=d(34);root.addView(video,videoLp);
            loading=new LinearLayout(MultiViewActivity.this);loading.setOrientation(LinearLayout.VERTICAL);loading.setGravity(Gravity.CENTER);loading.setPadding(d(14),d(10),d(14),d(10));loading.setBackground(shape(0xF2FFFFFF,10,BORDER,1));
            spinner=new ProgressBar(MultiViewActivity.this);loading.addView(spinner,new LinearLayout.LayoutParams(d(26),d(26)));
            message=text("Connecting…",12,TEXT,false);message.setGravity(Gravity.CENTER);message.setMaxWidth(d(300));message.setPadding(0,d(10),0,0);loading.addView(message,new LinearLayout.LayoutParams(-2,-2));
            root.addView(loading,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
            LinearLayout bar=row();bar.setPadding(d(12),d(9),d(12),d(9));bar.setBackgroundColor(PANEL);
            TextView title=text(camera.name,12,TEXT,true);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            status=text("CONNECTING",8,MUTED,true);status.setPadding(d(8),0,0,0);bar.addView(status);
            root.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
            root.setOnFocusChangeListener((v,focus)->{if(focus){activeTile=index;updateAudio();}});
            root.setOnClickListener(v->{if(failed)start();else startActivity(new Intent(MultiViewActivity.this,PlayerActivity.class).putExtra("camera_ip",camera.ip));});
            root.setOnLongClickListener(v->{chooseSlot(index);return true;});
        }

        void release(){if(player!=null){video.setPlayer(null);player.release();player=null;}}

        void start() {
            if(!started||camera==null)return;
            if(isPreview()){
                loading.setVisibility(View.GONE);video.setVisibility(View.GONE);
                ImageView image=new ImageView(MultiViewActivity.this);image.setImageBitmap(previewFrame(camera));image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);lp.topMargin=d(34);root.addView(image,0,lp);
                status.setText("DEMO");status.setTextColor(TEAL);root.setOnClickListener(null);return;
            }
            release();failed=false;spinner.setVisibility(View.VISIBLE);message.setText("Connecting…");loading.setVisibility(View.VISIBLE);status.setText("CONNECTING");status.setTextColor(MUTED);
            DefaultLoadControl buffers=new DefaultLoadControl.Builder().setBufferDurationsMs(1500,5000,700,1000).setTargetBufferBytes(2*1024*1024).build();
            player=new ExoPlayer.Builder(MultiViewActivity.this).setRenderersFactory(new DefaultRenderersFactory(MultiViewActivity.this).setEnableDecoderFallback(true)).setLoadControl(buffers).build();
            ExoPlayer current=player;
            video.setPlayer(current);updateAudio();
            current.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(current!=player)return;
                    if(state==Player.STATE_READY){failed=false;loading.setVisibility(View.GONE);status.setText("● LIVE");status.setTextColor(TEAL);}
                    else if(state==Player.STATE_BUFFERING&&!failed){message.setText("Buffering…");spinner.setVisibility(View.VISIBLE);loading.setVisibility(View.VISIBLE);}
                    else if(state==Player.STATE_ENDED)failure("Stream ended\nSelect to reconnect");
                }
                @Override public void onRenderedFirstFrame(){main.postDelayed(()->{if(player==current&&started)SnapshotStore.capture(MultiViewActivity.this,video,camera.ip,main);},400);}
                @Override public void onPlayerError(PlaybackException error){
                    if(current!=player)return;
                    if(error.errorCode==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||error.errorCode==PlaybackException.ERROR_CODE_DECODING_FAILED)failure("Decoder unavailable\nTry SD or fewer cameras");
                    else failure("Unable to connect\nSelect to retry");
                }
            });
            try{current.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(12000).createMediaSource(MediaItem.fromUri(streamUri(camera))));current.prepare();current.setPlayWhenReady(true);}
            catch(Exception ignored){failure("Check stream settings\nSelect to retry");}
        }

        void failure(String text){failed=true;spinner.setVisibility(View.GONE);message.setText(text);loading.setVisibility(View.VISIBLE);status.setText("UNAVAILABLE");status.setTextColor(0xFF9C5525);release();}
    }
}
