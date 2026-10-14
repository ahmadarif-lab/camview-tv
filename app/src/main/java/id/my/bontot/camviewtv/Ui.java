package id.my.bontot.camviewtv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

final class Ui {
    static final int TEAL = Color.rgb(8,157,169), INK = Color.rgb(27,46,58), MUTED = Color.rgb(112,130,141), BG = Color.rgb(244,247,249);
    static final int NAVY = Color.rgb(22,43,56), LINE = Color.rgb(229,236,240), PALE = Color.rgb(232,247,247);
    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable rounded(Context c, int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    static GradientDrawable outlined(Context c, int color, int border, float radius, float width) {
        GradientDrawable d = rounded(c, color, radius); d.setStroke(dp(c, width), border); return d;
    }
    static StateListDrawable focusBackground(Context c, int color, float radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, outlined(c, Color.rgb(224,245,246), TEAL, radius, 3));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(c, Color.rgb(220,242,244), radius));
        states.addState(new int[]{}, rounded(c, color, radius)); return states;
    }
    static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }
    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c); b.setText(label); b.setTextSize(14); b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : INK); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setIncludeFontPadding(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, outlined(c, primary ? Color.rgb(3,119,132) : Color.rgb(228,248,249), primary ? Color.rgb(54,224,230) : TEAL, 10, 3));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(c,primary ? Color.rgb(3,119,132) : PALE,10));
        states.addState(new int[]{}, primary ? rounded(c,TEAL,10) : outlined(c,Color.WHITE,LINE,10,1)); b.setBackground(states);
        b.setStateListAnimator(null);
        b.setFocusable(true); return b;
    }
    static Button darkButton(Context c, String label) {
        Button b = button(c,label,false); b.setTextColor(Color.WHITE);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, outlined(c,Color.rgb(34,66,80),Color.rgb(54,224,230),10,3));
        states.addState(new int[]{},rounded(c,NAVY,10)); b.setBackground(states); return b;
    }
    static void icon(Button b,String type,int color) {
        b.setCompoundDrawablesWithIntrinsicBounds(new LineIcon(b.getContext(),type,color),null,null,null);
        b.setCompoundDrawablePadding(dp(b.getContext(),9));
    }
    /** Original 24-unit line icons. Kept in code so the TV never relies on emoji fonts. */
    private static final class LineIcon extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final String type;
        private final int size;
        LineIcon(Context c,String type,int color) {this.type=type;size=dp(c,18);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public void draw(Canvas c){
            c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
            switch(type){
                case "overview": path.rewind();path.moveTo(3,11);path.lineTo(12,3);path.lineTo(21,11);path.moveTo(5,10);path.lineTo(5,21);path.lineTo(10,21);path.lineTo(10,15);path.lineTo(14,15);path.lineTo(14,21);path.lineTo(19,21);path.lineTo(19,10);c.drawPath(path,p);break;
                case "camera": c.drawRoundRect(3,5,21,19,3,3,p);c.drawCircle(12,12,4,p);c.drawCircle(18,8,0.6f,p);break;
                case "grid": c.drawRoundRect(3,3,10,10,1,1,p);c.drawRoundRect(14,3,21,10,1,1,p);c.drawRoundRect(3,14,10,21,1,1,p);c.drawRoundRect(14,14,21,21,1,1,p);break;
                case "side": c.drawRoundRect(2,4,22,20,2,2,p);c.drawLine(12,4,12,20,p);break;
                case "stack": c.drawRoundRect(4,2,20,22,2,2,p);c.drawLine(4,12,20,12,p);break;
                case "network": c.drawCircle(12,5,3,p);c.drawCircle(5,19,3,p);c.drawCircle(19,19,3,p);c.drawLine(10.5f,8,6.5f,16,p);c.drawLine(13.5f,8,17.5f,16,p);c.drawLine(8,19,16,19,p);break;
                case "help": c.drawCircle(12,12,9,p);c.drawArc(8,5,16,13,190,240,false,p);c.drawLine(12,13,12,14,p);c.drawPoint(12,18,p);break;
                case "scan": c.drawArc(4,4,20,20,35,285,false,p);path.rewind();path.moveTo(21,4);path.lineTo(21,10);path.lineTo(15,10);c.drawPath(path,p);break;
                case "plus": c.drawLine(12,5,12,19,p);c.drawLine(5,12,19,12,p);break;
                case "play": path.rewind();path.moveTo(7,4);path.lineTo(20,12);path.lineTo(7,20);path.close();c.drawPath(path,p);break;
                case "settings": c.drawCircle(12,12,4,p);c.drawCircle(12,12,8,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}break;
                case "shield": path.rewind();path.moveTo(12,2);path.lineTo(20,5);path.lineTo(20,12);path.quadTo(20,18,12,22);path.quadTo(4,18,4,12);path.lineTo(4,5);path.close();c.drawPath(path,p);path.rewind();path.moveTo(8,12);path.lineTo(11,15);path.lineTo(16,9);c.drawPath(path,p);break;
                case "star": path.rewind();for(int i=0;i<10;i++){double a=i*Math.PI/5-Math.PI/2;float r=i%2==0?9:4;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();c.drawPath(path,p);break;
                default: c.drawCircle(12,12,8,p);
            }c.restore();
        }
    }
    static void immersive(Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
