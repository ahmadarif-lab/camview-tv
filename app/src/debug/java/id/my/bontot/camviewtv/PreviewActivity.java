package id.my.bontot.camviewtv;

import android.content.Intent;
import android.graphics.Bitmap;
import java.util.List;

/** Debug-only, read-only screenshot fixture. Never reads the camera vault or scans a network. */
public final class PreviewActivity extends MainActivity {
    @Override protected boolean isPreview(){return true;}
    @Override protected List<Camera> loadCameras(){return PreviewData.cameras();}
    @Override protected Bitmap loadSnapshot(Camera camera){return PreviewData.frame(camera);}
    @Override protected LanDetails readLanDetails(){
        LanDetails lan=new LanDetails();lan.type="Ethernet connection";lan.address="192.0.2.10 /24";lan.subnet="192.0.2.0 /24";lan.iface="eth0";return lan;
    }
    @Override protected void openMultiView(int layout){startActivity(new Intent(this,PreviewMultiActivity.class).putExtra("layout",layout<0?2:layout));}
}
