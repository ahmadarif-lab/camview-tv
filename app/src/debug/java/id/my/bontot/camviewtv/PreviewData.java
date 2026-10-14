package id.my.bontot.camviewtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.List;

/** Fictional documentation addresses (RFC 5737) and original scene illustrations. */
final class PreviewData {
    static List<Camera> cameras(){
        List<Camera> result=new ArrayList<>();String[] names={"Front door","Garden","Driveway","Patio"};
        for(int i=0;i<4;i++){
            Camera c=new Camera("192.0.2."+(21+i));c.name=names[i];c.userNamed=true;c.username="demo";c.password="demo";
            c.rtsp=true;c.onvif=true;c.onvifPort=2020;c.favorite=i<2;c.lastSeen=1791986400000L;result.add(c);
        }
        return result;
    }
    static Bitmap frame(Camera camera){
        int variant=Integer.parseInt(camera.ip.substring(camera.ip.lastIndexOf('.')+1))-21;
        Bitmap b=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
        p.setColor(Color.rgb(211,232,236));c.drawRect(0,0,960,540,p);
        p.setColor(Color.rgb(184,210,194));c.drawRect(0,275,960,540,p);
        p.setColor(Color.rgb(160,190,172));c.drawRect(0,395,960,540,p);
        if(variant==1){
            p.setColor(Color.rgb(225,222,201));c.drawRect(0,315,960,350,p);
            for(int x=25;x<960;x+=54)c.drawRect(x,245,x+16,350,p);
            p.setColor(Color.rgb(220,214,192));Path path=new Path();path.moveTo(440,540);path.lineTo(530,350);path.lineTo(610,350);path.lineTo(740,540);path.close();c.drawPath(path,p);
        }else{
            p.setColor(Color.rgb(232,229,218));c.drawRect(170,160,760,400,p);
            p.setColor(Color.rgb(109,136,144));Path roof=new Path();roof.moveTo(130,175);roof.lineTo(465,53);roof.lineTo(800,175);roof.close();c.drawPath(roof,p);
            p.setColor(Color.rgb(121,157,167));c.drawRect(230,220,350,310,p);c.drawRect(570,220,690,310,p);
            p.setColor(Color.rgb(255,255,246));c.drawRect(285,220,294,310,p);c.drawRect(625,220,634,310,p);
            p.setColor(Color.rgb(139,158,150));c.drawRect(415,215,520,400,p);
            p.setColor(Color.rgb(220,214,192));Path path=new Path();path.moveTo(320,540);path.lineTo(420,400);path.lineTo(520,400);path.lineTo(670,540);path.close();c.drawPath(path,p);
        }
        for(int x:new int[]{85,850}){
            p.setColor(Color.rgb(137,152,128));c.drawRect(x-9,250,x+9,425,p);
            p.setColor(Color.rgb(111+variant*7,163,137));c.drawCircle(x,230,78,p);c.drawCircle(x-35,267,58,p);c.drawCircle(x+36,263,58,p);
        }
        p.setColor(0xAA17333B);c.drawRoundRect(24,478,290,518,8,8,p);p.setColor(Color.WHITE);p.setTextSize(22);c.drawText("ILLUSTRATED PREVIEW",40,506,p);
        return b;
    }
}
