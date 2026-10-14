package id.my.bontot.camviewtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** An original illustration, used until the user opens the camera's live stream. */
final class CameraArt extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient background;
    CameraArt(Context context) { super(context); setContentDescription("Camera illustration. No saved preview yet."); }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        background = new LinearGradient(0,0,w,h,Color.rgb(228,243,244),Color.rgb(243,248,250),Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setShader(background);
        c.drawRect(0,0,w,h,paint); paint.setShader(null);
        paint.setColor(Color.argb(26,124,190,196));
        c.drawCircle(w*.87f,h*.1f,h*.92f,paint); c.drawCircle(w*.09f,h*.99f,h*.57f,paint);
        paint.setColor(Color.argb(18,8,157,169));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(getContext(),1));
        for(int i=1;i<7;i++)c.drawLine(w*i/7,0,w*i/7,h,paint);
        for(int i=1;i<5;i++)c.drawLine(0,h*i/5,w,h*i/5,paint);
        paint.setStyle(Paint.Style.FILL);
        float unit = Math.min(w*.19f,h*.48f), cx = w*.5f, cy = h*.49f;
        paint.setColor(Color.argb(25,35,92,106));c.drawOval(cx-unit*.59f,cy+unit*.79f,cx+unit*.59f,cy+unit*.95f,paint);
        paint.setColor(Color.WHITE); c.drawRoundRect(cx-unit*.75f,cy-unit*.64f,cx+unit*.75f,cy+unit*.4f,unit*.22f,unit*.22f,paint);
        paint.setColor(Color.rgb(42,77,91)); c.drawCircle(cx,cy-unit*.1f,unit*.33f,paint);
        paint.setColor(Ui.TEAL); c.drawCircle(cx,cy-unit*.1f,unit*.2f,paint);
        paint.setColor(Color.argb(200,255,255,255)); c.drawCircle(cx-unit*.055f,cy-unit*.16f,unit*.065f,paint);
        paint.setColor(Color.WHITE); c.drawRoundRect(cx-unit*.09f,cy+unit*.35f,cx+unit*.09f,cy+unit*.7f,unit*.04f,unit*.04f,paint);
        c.drawRoundRect(cx-unit*.44f,cy+unit*.66f,cx+unit*.44f,cy+unit*.8f,unit*.08f,unit*.08f,paint);
        paint.setColor(Color.rgb(112,157,166)); paint.setTextSize(Ui.dp(getContext(),9));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Open this camera to save a preview",w*.5f,h-Ui.dp(getContext(),15),paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
