package id.my.bontot.camviewtv;

import android.graphics.Bitmap;
import java.util.List;

/** Debug-only multi-view with fictional illustrations and no player connections. */
public final class PreviewMultiActivity extends MultiViewActivity {
    @Override protected boolean isPreview(){return true;}
    @Override protected List<Camera> loadCameras(){return PreviewData.cameras();}
    @Override protected Bitmap previewFrame(Camera camera){return PreviewData.frame(camera);}
}
