package id.my.bontot.camviewtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.net.Inet4Address;
import java.text.DateFormat;

public class MainActivity extends Activity {
    private static final String OVERVIEW = "Overview", CAMERAS = "Cameras", NETWORK = "Network", HELP = "Help";
    private static final String[] NAV_LABELS = {OVERVIEW, CAMERAS, "Multi-view", NETWORK, HELP};
    private static final String[] NAV_ICONS = {"overview", "camera", "grid", "network", "help"};
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Camera> cameras = new ArrayList<>();
    private final Runnable drawResults = this::renderBody;
    private final Runnable autoScan = this::startScan;
    private CameraStore store;
    private Discovery discovery;
    private LinearLayout body, sidebar;
    private ScrollView scroll;
    private TextView progress, count, pageTitle;
    private TextView networkScanStatus;
    private Button scanButton, multiButton, addCameraButton;
    private SharedPreferences dashboardPrefs;
    private final Set<String> scanFoundIps = new HashSet<>();
    private String scanProgress = "";
    private String lastScanSummary = "No completed scan yet.";
    private long lastScanAt;
    private int lastScanCount;
    private String section = OVERVIEW;
    private boolean scanning, favoritesOnly, returningFromViewer;
    private int scanGeneration;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.immersive(this);
        store = new CameraStore(this);
        cameras.addAll(loadCameras());
        dashboardPrefs = getSharedPreferences("dashboard_state", MODE_PRIVATE);
        lastScanAt = dashboardPrefs.getLong("last_scan_at", 0);
        lastScanSummary = dashboardPrefs.getString("last_scan_summary", "No completed scan yet.");
        lastScanCount = dashboardPrefs.getInt("last_scan_count", 0);
        if(isPreview()){lastScanAt=1791986400000L;lastScanCount=4;lastScanSummary="Example scan · 4 cameras · 254 addresses";}
        if (state != null) {
            section = state.getString("section", OVERVIEW);
            favoritesOnly = state.getBoolean("favorites");
            if (!section.equals(CAMERAS) && !section.equals(NETWORK) && !section.equals(HELP)) section = OVERVIEW;
        }
        buildUi();
        renderBody();
        if (store.loadError != null) new AlertDialog.Builder(this).setTitle("Camera storage").setMessage(store.loadError).setPositiveButton("Got it", null).show();
        if (cameras.isEmpty() && state == null && store.loadError == null) main.postDelayed(autoScan, 700);
    }
    @Override protected void onResume() {
        super.onResume(); Ui.immersive(this);
        if (returningFromViewer && store != null && body != null) {
            returningFromViewer = false;
            List<Camera> saved = loadCameras();
            if (store.loadError == null) { cameras.clear(); cameras.addAll(saved); renderBody(); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("section", section); out.putBoolean("favorites", favoritesOnly);
    }
    @Override protected void onStop() { main.removeCallbacks(autoScan); if (scanning) cancelScan(); super.onStop(); }
    @Override protected void onDestroy() { if (discovery != null) discovery.cancel(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private int d(float value) { return Ui.dp(this, value); }
    private void pad(View view, int left, int top, int right, int bottom) { view.setPadding(d(left), d(top), d(right), d(bottom)); }

    private void buildUi() {
        LinearLayout root = row(); root.setGravity(Gravity.TOP); root.setBackgroundColor(Ui.BG); pad(root,24,22,24,22);
        sidebar = column(); sidebar.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,18,1)); pad(sidebar,13,24,13,16);
        root.addView(sidebar,new LinearLayout.LayoutParams(d(184),-1));
        TextView brand = Ui.text(this,"CamView TV",23,Ui.TEAL,true); pad(brand,11,0,0,0); sidebar.addView(brand);
        TextView subtitle = Ui.text(this,"YOUR HOME. ON SCREEN.",8.5f,Ui.MUTED,true); subtitle.setLetterSpacing(.12f); pad(subtitle,12,9,0,30); sidebar.addView(subtitle);
        for (int i=0; i<NAV_LABELS.length; i++) {
            String label=NAV_LABELS[i]; Button nav=Ui.button(this,label,false); nav.setTextSize(13); nav.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); nav.setTag("nav:"+label); nav.setContentDescription(label); Ui.icon(nav,NAV_ICONS[i],Ui.MUTED);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(47)); lp.bottomMargin=d(7); sidebar.addView(nav,lp);
            nav.setOnClickListener(v->{ if(label.equals("Multi-view")){openMultiView();return;} showSection(label); });
        }
        sidebar.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        LinearLayout local=column();local.setBackground(Ui.rounded(this,Ui.PALE,12));pad(local,13,14,13,14);
        TextView localTitle=Ui.text(this,"LOCAL BY DESIGN",8.5f,Ui.TEAL,true);localTitle.setLetterSpacing(.1f);local.addView(localTitle);
        TextView localCopy=Ui.text(this,"Your cameras.\nYour home network.",11,Ui.MUTED,false);localCopy.setLineSpacing(d(4),1);pad(localCopy,0,8,0,0);local.addView(localCopy);sidebar.addView(local);
        LinearLayout content=column();pad(content,26,7,0,0);root.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=row();LinearLayout titles=column();pageTitle=Ui.text(this,"Your home, in view.",27,Ui.INK,true);pageTitle.setSingleLine(true);pageTitle.setEllipsize(TextUtils.TruncateAt.END);titles.addView(pageTitle);
        count=Ui.text(this,"Private viewing. Local connection.",11,Ui.MUTED,false);pad(count,0,9,0,0);titles.addView(count);header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        multiButton=Ui.darkButton(this,"Multi-view");multiButton.setTextSize(13);multiButton.setTag("multi-view");Ui.icon(multiButton,"grid",Color.WHITE);multiButton.setOnClickListener(v->openMultiView());
        LinearLayout.LayoutParams multiLp=new LinearLayout.LayoutParams(-2,d(44));multiLp.leftMargin=d(14);header.addView(multiButton,multiLp);
        addCameraButton=Ui.button(this,"Add camera",true);addCameraButton.setTextSize(13);addCameraButton.setTag("header-add");Ui.icon(addCameraButton,"plus",Color.WHITE);addCameraButton.setOnClickListener(v->editCamera(null));
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-2,d(44));addLp.leftMargin=d(14);header.addView(addCameraButton,addLp);
        scanButton=Ui.button(this,"Scan network",false);scanButton.setTextSize(13);scanButton.setTag("scan");Ui.icon(scanButton,"scan",Ui.TEAL);scanButton.setOnClickListener(v->{if(scanning)cancelScan();else startScan();});
        LinearLayout.LayoutParams scanLp=new LinearLayout.LayoutParams(-2,d(44));scanLp.leftMargin=d(10);header.addView(scanButton,scanLp);content.addView(header);
        progress=Ui.text(this,"Discover cameras on the same Wi-Fi or Ethernet network.",11,Ui.MUTED,false);progress.setMaxLines(2);pad(progress,0,17,0,21);content.addView(progress);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setPadding(0,0,d(5),d(8));body=column();scroll.addView(body,new ScrollView.LayoutParams(-1,-2));content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);updateNav();updateHeader();if(section.equals(CAMERAS))addCameraButton.requestFocus();else if(section.equals(NETWORK))scanButton.requestFocus();else if(section.equals(OVERVIEW))multiButton.requestFocus();
    }
    private void updateNav() {
        int index=0;
        for(int i=0;i<sidebar.getChildCount();i++) {
            View view=sidebar.getChildAt(i);if(!(view instanceof Button))continue;Button nav=(Button)view;boolean active=("nav:"+section).equals(nav.getTag());
            nav.setTextColor(active?Ui.TEAL:Ui.MUTED);nav.setBackground(Ui.focusBackground(this,active?Ui.PALE:Color.WHITE,10));Ui.icon(nav,NAV_ICONS[index++],active?Ui.TEAL:Ui.MUTED);
        }
    }
    private void renderBody() {
        if(body==null||isFinishing()||isDestroyed())return;
        View focused=body.findFocus();Object focusTag=focused==null?null:focused.getTag();int savedY=scroll.getScrollY();body.removeAllViews();
        networkScanStatus=null;updateHeader();
        if(section.equals(OVERVIEW)){renderOverview();restoreBodyFocus(focusTag,savedY);return;}
        if(section.equals(NETWORK)){renderNetwork();restoreBodyFocus(focusTag,savedY);return;}
        if(section.equals(HELP)){renderHelp();restoreBodyFocus(focusTag,savedY);return;}
        LinearLayout toolbar=row();TextView title=Ui.text(this,"Camera library",19,Ui.INK,true);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        {
            Button all=filterButton("All cameras",!favoritesOnly,"filter-all");all.setOnClickListener(v->{favoritesOnly=false;renderBody();});toolbar.addView(all,new LinearLayout.LayoutParams(-2,d(36)));
            Button favorites=filterButton("Favorites",favoritesOnly,"filter-favorites");Ui.icon(favorites,"star",favoritesOnly?Ui.TEAL:Ui.MUTED);favorites.setOnClickListener(v->{favoritesOnly=true;renderBody();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,d(36));lp.leftMargin=d(6);toolbar.addView(favorites,lp);
        }
        LinearLayout.LayoutParams toolbarLp=new LinearLayout.LayoutParams(-1,-2);toolbarLp.bottomMargin=d(17);body.addView(toolbar,toolbarLp);
        List<Camera> visible=new ArrayList<>();for(Camera camera:cameras)if(!favoritesOnly||camera.favorite)visible.add(camera);
        visible.sort(Comparator.comparing((Camera c)->!c.favorite).thenComparing(c->c.name,String.CASE_INSENSITIVE_ORDER));if(visible.isEmpty())renderEmpty();
        int columns=getResources().getConfiguration().screenWidthDp>=1200?3:2;
        for(int start=0;start<visible.size();start+=columns) {
            LinearLayout line=row();line.setGravity(Gravity.TOP);
            for(int col=0;col<columns;col++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(col>0)lp.leftMargin=d(16);line.addView(start+col<visible.size()?cameraCard(visible.get(start+col)):new View(this),lp);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=d(16);body.addView(line,lp);
        }
        if(!visible.isEmpty()){TextView note=Ui.text(this,"Previews are from your last viewing session. Open a camera for live video.",10,Ui.MUTED,false);pad(note,1,0,0,8);body.addView(note);}
        restoreBodyFocus(focusTag,savedY);
    }
    private void restoreBodyFocus(Object tag,int y){if(tag!=null){View replacement=body.findViewWithTag(tag);if(replacement!=null)replacement.requestFocus();}scroll.post(()->scroll.scrollTo(0,y));}
    private void updateHeader() {
        if(multiButton==null)return;
        multiButton.setVisibility(section.equals(OVERVIEW)?View.VISIBLE:View.GONE);
        addCameraButton.setVisibility(section.equals(CAMERAS)?View.VISIBLE:View.GONE);
        scanButton.setVisibility(section.equals(NETWORK)?View.VISIBLE:View.GONE);
        scanButton.setText(scanning?"Stop scan":lastScanAt>0?"Scan again":"Scan network");
        pageTitle.setText(section.equals(NETWORK)?"Your local network.":section.equals(HELP)?"Make yourself at home.":section.equals(CAMERAS)?"Your cameras.":"Your home, in view.");
        count.setText(section.equals(NETWORK)?"TV connection & camera service discovery":section.equals(HELP)?"camera credentials · Local streaming · Remote controls":section.equals(CAMERAS)?cameras.size()+" saved cameras · Account & connection settings":"A quick look at your home");
        progress.setText(section.equals(NETWORK)?(scanning?scanProgress:lastScanAt>0?"Last completed scan: "+formatTime(lastScanAt):"Scan your local network to update the device inventory."):section.equals(CAMERAS)?"Watch, organize, and configure each camera.":section.equals(HELP)?"Everything you need to get connected.":"Local monitoring, without a cloud sign-in.");
    }
    private void showSection(String target){section=target;scroll.scrollTo(0,0);updateNav();renderBody();}
    private String formatTime(long timestamp){return timestamp>0?DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(timestamp)):"Never";}
    private void renderOverview() {
        int accounts=0,favorites=0;
        for(Camera camera:cameras){if(camera.hasAccount())accounts++;if(camera.favorite)favorites++;}
        LinearLayout stats=row();
        String[] labels={"SAVED CAMERAS","ACCOUNTS SAVED","FAVORITES","LAST SCAN"};
        String[] values={String.valueOf(cameras.size()),accounts+" / "+cameras.size(),String.valueOf(favorites),lastScanAt>0?DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(lastScanAt)):"Not yet"};
        String[] details={"In your camera library","Local camera credentials","Your pinned cameras",lastScanAt>0?lastScanCount+" observed · "+DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(lastScanAt)):"Discover in Network"};
        for(int i=0;i<labels.length;i++){
            LinearLayout tile=column();tile.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,12,1));pad(tile,16,14,16,13);
            TextView label=Ui.text(this,labels[i],8,Ui.MUTED,true);label.setLetterSpacing(.08f);tile.addView(label);
            TextView value=Ui.text(this,values[i],i==3?19:26,i==1?Ui.TEAL:Ui.INK,true);pad(value,0,8,0,7);tile.addView(value);
            tile.addView(Ui.text(this,details[i],9,Ui.MUTED,false));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(94),1);if(i>0)lp.leftMargin=d(12);stats.addView(tile,lp);
        }
        LinearLayout.LayoutParams statsLp=new LinearLayout.LayoutParams(-1,-2);statsLp.bottomMargin=d(19);body.addView(stats,statsLp);
        LinearLayout monitoringTitle=row();monitoringTitle.addView(Ui.text(this,"Quick monitoring",18,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));monitoringTitle.addView(Ui.text(this,"Choose a layout and go live",10,Ui.MUTED,false));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);titleLp.bottomMargin=d(11);body.addView(monitoringTitle,titleLp);
        LinearLayout shortcuts=row();String[] layouts={"Side by side","Stacked","4-camera grid"};String[] icons={"side","stack","grid"};
        for(int i=0;i<layouts.length;i++){
            final int layout=i;Button shortcut=Ui.button(this,layouts[i],false);shortcut.setTextSize(14);shortcut.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);shortcut.setTag("overview-layout:"+i);Ui.icon(shortcut,icons[i],Ui.TEAL);shortcut.setOnClickListener(v->openMultiView(layout));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(64),1);if(i>0)lp.leftMargin=d(12);shortcuts.addView(shortcut,lp);
        }
        LinearLayout.LayoutParams shortcutsLp=new LinearLayout.LayoutParams(-1,-2);shortcutsLp.bottomMargin=d(20);body.addView(shortcuts,shortcutsLp);
        LinearLayout recentTitle=row();LinearLayout titleText=column();titleText.addView(Ui.text(this,"Camera shortcuts",18,Ui.INK,true));TextView subtitle=Ui.text(this,"Last-view images · Open a camera for live video",9,Ui.MUTED,false);pad(subtitle,0,6,0,0);titleText.addView(subtitle);recentTitle.addView(titleText,new LinearLayout.LayoutParams(0,-2,1));
        Button manage=Ui.button(this,"Manage cameras",false);manage.setTextSize(11);manage.setTag("overview-manage");Ui.icon(manage,"camera",Ui.TEAL);manage.setOnClickListener(v->showSection(CAMERAS));recentTitle.addView(manage,new LinearLayout.LayoutParams(-2,d(36)));
        LinearLayout.LayoutParams recentTitleLp=new LinearLayout.LayoutParams(-1,-2);recentTitleLp.bottomMargin=d(12);body.addView(recentTitle,recentTitleLp);
        if(cameras.isEmpty()){
            LinearLayout empty=row();empty.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,12,1));pad(empty,18,18,18,18);empty.addView(Ui.text(this,"Your cameras will appear here after a network scan.",13,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));Button discover=Ui.button(this,"Find cameras",true);discover.setTag("overview-discover");discover.setOnClickListener(v->showSection(NETWORK));empty.addView(discover,new LinearLayout.LayoutParams(-2,d(38)));body.addView(empty);
            return;
        }
        List<Camera> recent=new ArrayList<>(cameras);recent.sort(Comparator.comparing((Camera c)->!c.favorite).thenComparing(c->c.name,String.CASE_INSENSITIVE_ORDER));LinearLayout recentRow=row();
        for(int i=0;i<Math.min(2,recent.size());i++){
            Camera camera=recent.get(i);LinearLayout shortcut=row();shortcut.setBackground(Ui.focusBackground(this,Color.WHITE,12));pad(shortcut,12,12,14,12);shortcut.setFocusable(true);shortcut.setClickable(true);shortcut.setTag("overview-camera:"+camera.ip);shortcut.setContentDescription("Watch "+displayName(camera));shortcut.setOnClickListener(v->open(camera));
            FrameLayout thumbnail=new FrameLayout(this);thumbnail.setBackground(Ui.rounded(this,Ui.PALE,8));thumbnail.setClipToOutline(true);Bitmap frame=loadSnapshot(camera);if(frame==null)thumbnail.addView(new CameraArt(this),new FrameLayout.LayoutParams(-1,-1));else{ImageView image=new ImageView(this);image.setImageBitmap(frame);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Last-view image from "+displayName(camera));thumbnail.addView(image,new FrameLayout.LayoutParams(-1,-1));}
            shortcut.addView(thumbnail,new LinearLayout.LayoutParams(d(104),d(60)));LinearLayout names=column();pad(names,13,0,8,0);TextView name=Ui.text(this,displayName(camera),16,Ui.INK,true);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);names.addView(name);TextView address=Ui.text(this,camera.ip,10,Ui.MUTED,false);pad(address,0,7,0,6);names.addView(address);names.addView(Ui.text(this,camera.hasAccount()?"Watch camera":"Set up account",9,Ui.TEAL,true));shortcut.addView(names,new LinearLayout.LayoutParams(0,-2,1));shortcut.addView(Ui.text(this,"›",25,Ui.TEAL,false));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(84),1);if(i>0)lp.leftMargin=d(14);recentRow.addView(shortcut,lp);
        }
        if(recent.size()==1)recentRow.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));body.addView(recentRow);
    }
    private Button filterButton(String label,boolean selected,String tag) {
        Button button=Ui.button(this,label,false);button.setTextSize(11);button.setTag(tag);button.setTextColor(selected?Ui.TEAL:Ui.MUTED);button.setBackground(Ui.focusBackground(this,selected?Ui.PALE:Ui.BG,9));return button;
    }
    private View cameraCard(Camera camera) {
        LinearLayout card=column();card.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,14,1));card.setClipToOutline(true);
        FrameLayout preview=new FrameLayout(this);Bitmap snapshot=loadSnapshot(camera);
        if(snapshot!=null){ImageView image=new ImageView(this);image.setImageBitmap(snapshot);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("Last viewed image from "+displayName(camera)+". Not a live preview.");preview.addView(image,new FrameLayout.LayoutParams(-1,-1));}
        else preview.addView(new CameraArt(this),new FrameLayout.LayoutParams(-1,-1));
        TextView badge=Ui.text(this,snapshot==null?"NO PREVIEW YET":"LAST VIEWED",8.5f,snapshot==null?Ui.MUTED:Color.WHITE,true);badge.setLetterSpacing(.08f);pad(badge,9,6,9,6);badge.setBackground(Ui.rounded(this,snapshot==null?0xEFFFFFFF:0xCE172C38,6));FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);badgeLp.setMargins(d(12),d(12),0,0);preview.addView(badge,badgeLp);
        if(camera.favorite){TextView favorite=Ui.text(this,"★",18,Color.WHITE,false);favorite.setGravity(Gravity.CENTER);favorite.setBackground(Ui.rounded(this,0xBB172C38,7));FrameLayout.LayoutParams favoriteLp=new FrameLayout.LayoutParams(d(31),d(31),Gravity.TOP|Gravity.END);favoriteLp.setMargins(0,d(10),d(10),0);preview.addView(favorite,favoriteLp);}
        card.addView(preview,new LinearLayout.LayoutParams(-1,d(162)));
        LinearLayout detail=column();pad(detail,17,15,17,16);card.addView(detail);
        TextView name=Ui.text(this,displayName(camera),19,Ui.INK,true);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);detail.addView(name);
        String model=camera.model.isEmpty()?"Network camera":camera.model;TextView address=Ui.text(this,model+"  ·  "+camera.ip,11,Ui.MUTED,false);address.setSingleLine(true);address.setEllipsize(TextUtils.TruncateAt.END);pad(address,0,7,0,0);detail.addView(address);
        LinearLayout status=row();TextView account=Ui.text(this,camera.hasAccount()?"●  Account saved":"○  Setup required",10,camera.hasAccount()?Ui.TEAL:Ui.MUTED,true);status.addView(account,new LinearLayout.LayoutParams(0,-2,1));status.addView(Ui.text(this,services(camera),9,Ui.MUTED,false));pad(status,0,11,0,13);detail.addView(status);
        LinearLayout buttons=row();Button watch=Ui.button(this,camera.hasAccount()?"Watch":"Set up camera",true);watch.setTextSize(13);watch.setTag("watch:"+camera.ip);Ui.icon(watch,camera.hasAccount()?"play":"plus",Color.WHITE);watch.setOnClickListener(v->open(camera));buttons.addView(watch,new LinearLayout.LayoutParams(0,d(42),1));
        Button options=Ui.button(this,"Settings",false);options.setTextSize(12);options.setTag("options:"+camera.ip);options.setContentDescription("Settings for "+displayName(camera));Ui.icon(options,"settings",Ui.MUTED);options.setOnClickListener(v->cameraOptions(camera));LinearLayout.LayoutParams optionsLp=new LinearLayout.LayoutParams(-2,d(42));optionsLp.leftMargin=d(9);buttons.addView(options,optionsLp);detail.addView(buttons);return card;
    }
    private String displayName(Camera camera){return !camera.userNamed&&camera.name.startsWith("Kamera ")?"Camera "+camera.name.substring(7):camera.name;}
    private String services(Camera camera){return camera.rtsp&&camera.onvif?"RTSP / ONVIF":camera.rtsp?"RTSP":camera.onvif?"ONVIF":"MANUAL";}
    private void renderEmpty() {
        LinearLayout panel=column();panel.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,14,1));pad(panel,27,28,27,28);panel.addView(Ui.text(this,favoritesOnly?"Your favorites will appear here.":"Let's find your cameras.",22,Ui.INK,true));
        TextView hint=Ui.text(this,favoritesOnly?"Choose Settings on a camera, then add it to your favorites.":"Connect your TV and cameras to the same home network, then scan. You can also add a camera using its local IP address.",14,Ui.MUTED,false);hint.setLineSpacing(d(5),1);pad(hint,0,14,0,21);panel.addView(hint);
        Button help=Ui.button(this,"Camera setup guide",false);Ui.icon(help,"help",Ui.TEAL);help.setOnClickListener(v->showSection(HELP));panel.addView(help,new LinearLayout.LayoutParams(-2,d(43)));body.addView(panel);
    }
    private void renderNetwork() {
        LanDetails lan=readLanDetails();LinearLayout top=row();top.setGravity(Gravity.TOP);
        LinearLayout connection=column();connection.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,12,1));pad(connection,20,18,20,18);
        TextView eyebrow=Ui.text(this,"THIS TV",8.5f,Ui.TEAL,true);eyebrow.setLetterSpacing(.08f);connection.addView(eyebrow);
        TextView title=Ui.text(this,lan.type,19,Ui.INK,true);pad(title,0,8,0,16);connection.addView(title);
        LinearLayout addresses=row();addresses.setGravity(Gravity.TOP);networkDetail(addresses,"TV ADDRESS",lan.address);networkDetail(addresses,"SUBNET",lan.subnet);networkDetail(addresses,"INTERFACE",lan.iface);connection.addView(addresses);
        top.addView(connection,new LinearLayout.LayoutParams(0,d(143),1.9f));
        LinearLayout scan=column();scan.setBackground(Ui.rounded(this,Ui.NAVY,12));pad(scan,20,18,20,18);
        TextView scanTitle=Ui.text(this,scanning?"SCAN IN PROGRESS":"LAST COMPLETED SCAN",8.5f,0xFF9DC7D0,true);scanTitle.setLetterSpacing(.08f);scan.addView(scanTitle);
        TextView scanTime=Ui.text(this,scanning?"Discovering cameras…":lastScanAt>0?formatTime(lastScanAt):"No scan yet",16,Color.WHITE,true);pad(scanTime,0,10,0,9);scan.addView(scanTime);
        networkScanStatus=Ui.text(this,scanning?scanFoundIps.size()+" cameras observed so far":lastScanAt>0?lastScanCount+" cameras observed\n"+lastScanSummary:"Choose Scan network to discover local camera services.",10,0xFFC5DAE0,false);networkScanStatus.setMaxLines(3);networkScanStatus.setLineSpacing(d(3),1);scan.addView(networkScanStatus);
        LinearLayout.LayoutParams scanLp=new LinearLayout.LayoutParams(0,d(143),1);scanLp.leftMargin=d(16);top.addView(scan,scanLp);LinearLayout.LayoutParams topLp=new LinearLayout.LayoutParams(-1,-2);topLp.bottomMargin=d(20);body.addView(top,topLp);
        LinearLayout inventoryHeader=row();inventoryHeader.addView(Ui.text(this,"Device inventory",18,Ui.INK,true),new LinearLayout.LayoutParams(0,-2,1));int observed=0;for(Camera camera:cameras)if(camera.lastSeen>0)observed++;inventoryHeader.addView(Ui.text(this,observed+" discovered  ·  "+(cameras.size()-observed)+" manual",10,Ui.MUTED,false));body.addView(inventoryHeader);
        TextView description=Ui.text(this,"Cached service observations, not a live online status. Select a row for details.",10,Ui.MUTED,false);pad(description,0,8,0,13);body.addView(description);
        LinearLayout table=column();table.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,12,1));pad(table,10,5,10,5);
        LinearLayout headings=row();pad(headings,10,10,10,11);String[] headingsText={"CAMERA / SOURCE","IPv4 ADDRESS","RTSP","ONVIF","LAST OBSERVED"};float[] weights={2.05f,1.25f,.65f,.65f,1.6f};
        for(int i=0;i<headingsText.length;i++){TextView heading=Ui.text(this,headingsText[i],8,Ui.MUTED,true);heading.setLetterSpacing(.04f);headings.addView(heading,new LinearLayout.LayoutParams(0,-2,weights[i]));}table.addView(headings);
        List<Camera> inventory=new ArrayList<>(cameras);inventory.sort(Comparator.comparing(c->c.ip));
        for(Camera camera:inventory){
            View divider=new View(this);divider.setBackgroundColor(Ui.LINE);table.addView(divider,new LinearLayout.LayoutParams(-1,d(1)));
            LinearLayout device=row();pad(device,10,12,10,12);device.setBackground(Ui.focusBackground(this,Color.WHITE,8));device.setFocusable(true);device.setClickable(true);device.setTag("network-device:"+camera.ip);device.setContentDescription("Network details for "+displayName(camera));device.setOnClickListener(v->networkDeviceDetails(camera));
            LinearLayout name=column();TextView deviceName=Ui.text(this,displayName(camera),14,Ui.INK,true);deviceName.setSingleLine(true);deviceName.setEllipsize(TextUtils.TruncateAt.END);name.addView(deviceName);TextView source=Ui.text(this,camera.lastSeen>0?(camera.model.isEmpty()?"Discovered camera":camera.model):"Manually saved",9,camera.lastSeen>0?Ui.TEAL:Ui.MUTED,false);source.setSingleLine(true);source.setEllipsize(TextUtils.TruncateAt.END);pad(source,0,6,9,0);name.addView(source);device.addView(name,new LinearLayout.LayoutParams(0,-2,weights[0]));
            String rtspPort=camera.rtsp?String.valueOf(camera.rtspPort):camera.hasAccount()?camera.rtspPort+" *":"—";
            String onvifPort=camera.onvif?(camera.onvifPort>0?String.valueOf(camera.onvifPort):"Seen"):"—";
            TextView address=Ui.text(this,camera.ip,11,Ui.INK,false);device.addView(address,new LinearLayout.LayoutParams(0,-2,weights[1]));device.addView(Ui.text(this,rtspPort,11,Ui.INK,false),new LinearLayout.LayoutParams(0,-2,weights[2]));device.addView(Ui.text(this,onvifPort,11,Ui.INK,false),new LinearLayout.LayoutParams(0,-2,weights[3]));
            TextView last=Ui.text(this,camera.lastSeen>0?formatTime(camera.lastSeen):"Manual entry",10,Ui.MUTED,false);last.setMaxLines(2);device.addView(last,new LinearLayout.LayoutParams(0,-2,weights[4]));table.addView(device,new LinearLayout.LayoutParams(-1,d(66)));
        }
        if(inventory.isEmpty()){TextView empty=Ui.text(this,"No camera services saved. Run a scan to populate this inventory.",13,Ui.MUTED,false);pad(empty,12,20,12,20);table.addView(empty);}
        body.addView(table);TextView note=Ui.text(this,"* Configured port, not observed by discovery. Scans cover this TV's local subnet; isolated guest networks or VLANs may not be reachable.",9,Ui.MUTED,false);note.setLineSpacing(d(3),1);pad(note,0,12,0,8);body.addView(note);
    }
    private void networkDetail(LinearLayout parent,String label,String value){LinearLayout item=column();TextView caption=Ui.text(this,label,8,Ui.MUTED,true);item.addView(caption);TextView text=Ui.text(this,value,12,Ui.INK,true);text.setMaxLines(2);pad(text,0,7,7,0);item.addView(text);parent.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
    private void networkDeviceDetails(Camera camera){new AlertDialog.Builder(this).setTitle(displayName(camera)).setMessage("IPv4 address: "+camera.ip+"\nModel: "+(camera.model.isEmpty()?"Not identified":camera.model)+"\nSource: "+(camera.lastSeen>0?"Network discovery":"Manual entry")+"\nRTSP port: "+camera.rtspPort+(camera.rtsp?" (observed)":" (configured)")+"\nONVIF port: "+(camera.onvifPort>0?camera.onvifPort:"Not observed")+"\nLast observed: "+(camera.lastSeen>0?formatTime(camera.lastSeen):"No discovery observation")+"\n\nThese are saved observations and do not confirm current connectivity.").setPositiveButton("Close",null).show();}
    protected static final class LanDetails {String type="No local connection",address="Unavailable",subnet="Unavailable",iface="Unavailable";}
    protected LanDetails readLanDetails() {
        LanDetails result=new LanDetails();
        try{
            ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(manager==null)return result;
            List<Network> links=new ArrayList<>();Network active=manager.getActiveNetwork();if(active!=null)links.add(active);for(Network network:manager.getAllNetworks())if(!links.contains(network))links.add(network);
            for(Network network:links){NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);LinkProperties properties=manager.getLinkProperties(network);if(capabilities==null||properties==null)continue;
                boolean ethernet=capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),wifi=capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);if(!ethernet&&!wifi)continue;
                LanDetails candidate=new LanDetails();candidate.type=ethernet?"Ethernet connection":"Wi-Fi connection";candidate.iface=properties.getInterfaceName()==null?"Unknown":properties.getInterfaceName();candidate.address="No IPv4 address";candidate.subnet="IPv4 unavailable";result=candidate;
                for(LinkAddress address:properties.getLinkAddresses())if(address.getAddress() instanceof Inet4Address){candidate.address=address.getAddress().getHostAddress()+" /"+address.getPrefixLength();candidate.subnet=subnetOf(address);return candidate;}
            }
        }catch(Exception ignored){}
        return result;
    }
    private String subnetOf(LinkAddress address){byte[] bytes=address.getAddress().getAddress();int prefix=address.getPrefixLength();long number=0;for(byte part:bytes)number=(number<<8)|(part&255);long mask=prefix==0?0:(0xffffffffL<<(32-prefix))&0xffffffffL;long subnet=number&mask;return((subnet>>>24)&255)+"."+((subnet>>>16)&255)+"."+((subnet>>>8)&255)+"."+(subnet&255)+" /"+prefix;}
    private void renderHelp() {
        LinearLayout intro=column();intro.setBackground(Ui.rounded(this,Ui.NAVY,14));pad(intro,23,23,23,23);intro.addView(Ui.text(this,"Your cameras. One clear view.",23,Color.WHITE,true));
        TextView introCopy=Ui.text(this,"Connect cameras that provide RTSP on your home network. Use the local camera username, password and stream path.",13,0xFFB9CED7,false);introCopy.setLineSpacing(d(4),1);pad(introCopy,0,12,0,0);intro.addView(introCopy);LinearLayout.LayoutParams introLp=new LinearLayout.LayoutParams(-1,-2);introLp.bottomMargin=d(18);body.addView(intro,introLp);
        String[] titles={"Enable RTSP on your camera","Find the stream details","Connect from your TV","Choose your view"};
        String[] descriptions={"Enable RTSP in the camera app or web settings. For Tapo: Settings → Advanced Settings → Camera Account.","Find the local camera username, password, RTSP port and stream path in the manufacturer’s documentation.","Keep the TV and cameras on the same network. Scan, choose Set up camera, and enter the local account.","Watch one camera or open Multi-view. Tapo uses /stream1 (HD) and /stream2 (SD). Other cameras may use different paths."};
        for(int start=0;start<4;start+=2){LinearLayout line=row();line.setGravity(Gravity.TOP);for(int col=0;col<2;col++){int index=start+col;LinearLayout step=column();step.setBackground(Ui.outlined(this,Color.WHITE,Ui.LINE,12,1));pad(step,20,19,20,20);TextView number=Ui.text(this,"0"+(index+1),11,Ui.TEAL,true);number.setLetterSpacing(.1f);step.addView(number);TextView title=Ui.text(this,titles[index],16,Ui.INK,true);pad(title,0,10,0,10);step.addView(title);TextView copy=Ui.text(this,descriptions[index],12,Ui.MUTED,false);copy.setLineSpacing(d(4),1);step.addView(copy);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,d(179),1);if(col>0)lp.leftMargin=d(15);line.addView(step,lp);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=d(15);body.addView(line,lp);}
        TextView privacy=Ui.text(this,"In Multi-view, hold OK on a tile to change its camera. camera credentials are encrypted on this TV. Card previews are saved from your last viewing session, not live video. Some battery-powered cameras do not support RTSP. RTSP support and stream paths depend on the camera model.",11,Ui.MUTED,false);privacy.setLineSpacing(d(4),1);pad(privacy,0,4,0,19);body.addView(privacy);
        Button add=Ui.button(this,"Add a camera manually",true);Ui.icon(add,"plus",Color.WHITE);add.setOnClickListener(v->editCamera(null));body.addView(add,new LinearLayout.LayoutParams(-2,d(44)));
    }
    protected boolean isPreview() { return false; }
    protected List<Camera> loadCameras() { return store.load(); }
    protected Bitmap loadSnapshot(Camera camera) { return SnapshotStore.load(this,camera.ip); }
    private void startScan() {
        if(isPreview())return;
        if(scanning)return;scanning=true;scanFoundIps.clear();scanProgress="Preparing your local network scan…";scanButton.setText("Stop scan");final int generation=++scanGeneration;
        discovery=new Discovery(getApplicationContext(),new Discovery.Listener(){
            @Override public void onCamera(Camera found){main.post(()->{if(!scanning||generation!=scanGeneration||isDestroyed())return;scanFoundIps.add(found.ip);Camera existing=find(found.ip);if(existing!=null)existing.mergeDiscovery(found);else{found.lastSeen=System.currentTimeMillis();cameras.add(found);}persist();main.removeCallbacks(drawResults);main.postDelayed(drawResults,350);});}
            @Override public void onProgress(String status){main.post(()->{if(scanning&&generation==scanGeneration){scanProgress=status;if(section.equals(NETWORK)){progress.setText(status);if(networkScanStatus!=null)networkScanStatus.setText(scanFoundIps.size()+" cameras observed so far");}}});}
            @Override public void onComplete(String summary){main.post(()->{if(!scanning||generation!=scanGeneration)return;scanning=false;lastScanAt=System.currentTimeMillis();lastScanSummary=summary;lastScanCount=scanFoundIps.size();dashboardPrefs.edit().putLong("last_scan_at",lastScanAt).putString("last_scan_summary",lastScanSummary).putInt("last_scan_count",lastScanCount).apply();persist();renderBody();});}
        });discovery.start();renderBody();
    }
    private void cancelScan(){scanGeneration++;if(discovery!=null)discovery.cancel();scanning=false;scanButton.setText("Scan again");progress.setText("Scan stopped. Discovered cameras have been saved.");persist();renderBody();}
    private Camera find(String ip){for(Camera camera:cameras)if(camera.ip.equals(ip))return camera;return null;}
    private void storageError(){Toast.makeText(this,"Could not save cameras securely. Check the TV's available storage.",Toast.LENGTH_LONG).show();}
    private boolean persist(){if(isPreview())return false;if(store.save(cameras))return true;storageError();return false;}
    private void open(Camera camera){if(isPreview())return;if(!camera.hasAccount()){editCamera(camera);return;}if(scanning)cancelScan();returningFromViewer=true;startActivity(new Intent(this,PlayerActivity.class).putExtra("camera_ip",camera.ip));}
    private void openMultiView(){openMultiView(-1);}
    protected void openMultiView(int layout){if(scanning)cancelScan();returningFromViewer=true;Intent intent=new Intent(this,MultiViewActivity.class);if(layout>=0)intent.putExtra("layout",layout);startActivity(intent);}
    private void cameraOptions(Camera camera) {
        if(isPreview())return;
        String[] options={"Watch camera","Edit account & connection",camera.favorite?"Remove from favorites":"Add to favorites","Remove from this TV"};
        new AlertDialog.Builder(this).setTitle(displayName(camera)).setItems(options,(dialog,index)->{
            if(index==0)open(camera);else if(index==1)editCamera(camera);else if(index==2){camera.favorite=!camera.favorite;if(!persist())camera.favorite=!camera.favorite;renderBody();}
            else new AlertDialog.Builder(this).setTitle("Remove "+displayName(camera)+"?").setMessage("The address, saved credentials and saved preview will be removed from this TV.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->{List<Camera> remaining=new ArrayList<>(cameras);remaining.remove(camera);if(!store.save(remaining)){storageError();return;}cameras.clear();cameras.addAll(remaining);SnapshotStore.delete(this,camera.ip);renderBody();}).show();
        }).show();
    }
    private EditText field(LinearLayout form,String label,String value,int input) {
        TextView caption=Ui.text(this,label,12,Ui.MUTED,true);pad(caption,0,14,0,5);form.addView(caption);EditText edit=new EditText(this);edit.setTextSize(16);edit.setSingleLine(true);edit.setInputType(input);edit.setText(value);edit.setSelectAllOnFocus(true);
        if(android.os.Build.VERSION.SDK_INT>=26)edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);edit.setSaveEnabled(false);form.addView(edit,new LinearLayout.LayoutParams(-1,d(44)));return edit;
    }
    private void editCamera(Camera existing) {
        if(isPreview())return;
        LinearLayout form=column();pad(form,24,4,24,18);TextView intro=Ui.text(this,"Enter the local RTSP credentials and stream path from your camera’s app or web settings.",13,Ui.MUTED,false);intro.setLineSpacing(d(3),1);form.addView(intro);
        int plain=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        EditText name=field(form,"Camera name",existing==null?"":displayName(existing),plain);EditText ip=field(form,"Camera IPv4 address",existing==null?"":existing.ip,plain);EditText user=field(form,"Camera username",existing==null?"":existing.username,plain);EditText pass=field(form,"Camera password",existing==null?"":existing.password,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox show=new CheckBox(this);show.setText("Show password");show.setTextSize(12);show.setTextColor(Ui.MUTED);form.addView(show);show.setOnCheckedChangeListener((v,checked)->{int cursor=pass.getSelectionStart();pass.setInputType(InputType.TYPE_CLASS_TEXT|(checked?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));if(cursor>=0)pass.setSelection(Math.min(cursor,pass.length()));});
        EditText port=field(form,"RTSP port",existing==null?"554":String.valueOf(existing.rtspPort),InputType.TYPE_CLASS_NUMBER);EditText path=field(form,"RTSP stream path (for example /stream1)",existing==null?"/stream1":existing.streamPath,plain);
        ScrollView scroller=new ScrollView(this);scroller.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"Add camera":"Camera settings").setView(scroller).setNegativeButton("Cancel",null).setNeutralButton("Help",(d,w)->showSection(HELP)).setPositiveButton("Save camera",null).create();
        dialog.setOnShowListener(v->{
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.getWindow().setLayout(Math.min(d(610),getResources().getDisplayMetrics().widthPixels-d(80)),Math.min(d(570),getResources().getDisplayMetrics().heightPixels-d(50)));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                String address=ip.getText().toString().trim();if(!validIpv4(address)){ip.setError("Enter an IPv4 address, such as 192.168.1.20");ip.requestFocus();return;}Camera duplicate=find(address);if(duplicate!=null&&duplicate!=existing){ip.setError("This camera address is already saved");ip.requestFocus();return;}
                int rtspPort;try{rtspPort=Integer.parseInt(port.getText().toString().trim());if(rtspPort<1||rtspPort>65535)throw new Exception();}catch(Exception error){port.setError("Use a port between 1 and 65535");port.requestFocus();return;}
                String stream=path.getText().toString().trim();if(!stream.startsWith("/")||stream.contains(" ")||stream.contains("\n")||stream.contains("\r")||stream.contains("#")){path.setError("Start the path with / and remove spaces");path.requestFocus();return;}
                if(user.getText().length()==0){user.setError("Enter the camera username");user.requestFocus();return;}if(pass.getText().length()==0){pass.setError("Enter the camera password");pass.requestFocus();return;}
                Camera draft;try{draft=existing==null?new Camera(address):Camera.fromJson(existing.toJson());}catch(Exception error){storageError();return;}
                draft.ip=address;draft.name=name.getText().toString().trim();if(draft.name.isEmpty())draft.name="Camera "+address;draft.userNamed=true;draft.username=user.getText().toString();draft.password=pass.getText().toString();draft.rtspPort=rtspPort;draft.streamPath=stream;
                List<Camera> updated=new ArrayList<>(cameras);if(existing==null)updated.add(draft);else updated.set(cameras.indexOf(existing),draft);if(!store.save(updated)){storageError();return;}if(existing!=null&&!existing.ip.equals(address))SnapshotStore.delete(this,existing.ip);cameras.clear();cameras.addAll(updated);dialog.dismiss();renderBody();Toast.makeText(this,"Camera saved. Choose Watch to check the connection.",Toast.LENGTH_LONG).show();
            });
        });dialog.show();
    }
    private static boolean validIpv4(String address){String[] parts=address.split("\\.",-1);if(parts.length!=4)return false;for(String part:parts)try{if(!part.matches("[0-9]{1,3}")||Integer.parseInt(part)>255)return false;}catch(Exception error){return false;}return !address.equals("0.0.0.0")&&!address.equals("255.255.255.255");}
}
