package th.pongsanuk.gridphoto;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * เซฟรูปลงแกลเลอรีและแชร์ไฟล์จาก WebView
 * WebView ของ Android ไม่รองรับ <a download> กับ blob: และไม่มี navigator.share
 * จึงต้องส่ง base64 มาให้ฝั่ง native เขียนไฟล์เอง
 */
@CapacitorPlugin(
    name = "SaveImage",
    permissions = {
        @Permission(
            alias = "storage",
            strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }
        )
    }
)
public class SaveImagePlugin extends Plugin {

    private static final String ALBUM = "ประกอบรูป";

    /** เซฟรูปเดียวลงแกลเลอรี: { data: base64, name: "xxx.jpg" } */
    @PluginMethod
    public void save(PluginCall call) {
        // Android 10 ขึ้นไปเขียนผ่าน MediaStore ได้เลย ไม่ต้องขอสิทธิ์
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || getPermissionState("storage") == PermissionState.GRANTED) {
            doSave(call);
        } else {
            requestPermissionForAlias("storage", call, "storagePermsCallback");
        }
    }

    @PermissionCallback
    private void storagePermsCallback(PluginCall call) {
        if (getPermissionState("storage") == PermissionState.GRANTED) {
            doSave(call);
        } else {
            call.reject("ไม่ได้รับสิทธิ์เขียนไฟล์");
        }
    }

    private void doSave(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("name", "photo.jpg");
        if (data == null) { call.reject("ไม่มีข้อมูลรูป"); return; }

        try {
            byte[] bytes = Base64.decode(stripPrefix(data), Base64.DEFAULT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + ALBUM);
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);

                Uri uri = getContext().getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) { call.reject("สร้างไฟล์ในแกลเลอรีไม่ได้"); return; }

                try (OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                    os.write(bytes);
                }
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContext().getContentResolver().update(uri, cv, null, null);
            } else {
                File dir = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        ALBUM);
                if (!dir.exists() && !dir.mkdirs()) { call.reject("สร้างโฟลเดอร์ไม่ได้"); return; }
                File out = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                }
                // บอกแกลเลอรีให้เห็นไฟล์ใหม่
                MediaScannerConnection.scanFile(getContext(),
                        new String[]{ out.getAbsolutePath() },
                        new String[]{ "image/jpeg" }, null);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("เซฟรูปไม่สำเร็จ: " + e.getMessage(), e);
        }
    }

    /** แชร์รูปหนึ่งใบหรือหลายใบ: { files: [{ data, name }, ...] } */
    @PluginMethod
    public void share(PluginCall call) {
        JSArray files = call.getArray("files");
        if (files == null || files.length() == 0) { call.reject("ไม่มีไฟล์ให้แชร์"); return; }

        try {
            File dir = new File(getContext().getCacheDir(), "share");
            if (!dir.exists() && !dir.mkdirs()) { call.reject("สร้างโฟลเดอร์แคชไม่ได้"); return; }

            ArrayList<Uri> uris = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                JSObject f = JSObject.fromJSONObject(files.getJSONObject(i));
                byte[] bytes = Base64.decode(stripPrefix(f.getString("data")), Base64.DEFAULT);
                File out = new File(dir, f.getString("name", "photo" + i + ".jpg"));
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                }
                uris.add(FileProvider.getUriForFile(getContext(),
                        getContext().getPackageName() + ".fileprovider", out));
            }

            Intent intent;
            if (uris.size() == 1) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            intent.setType("image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "แชร์รูป");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("แชร์ไม่สำเร็จ: " + e.getMessage(), e);
        }
    }

    /** ตัด "data:image/jpeg;base64," ออกถ้ามีติดมา */
    private static String stripPrefix(String data) {
        int i = data.indexOf(",");
        return (data.startsWith("data:") && i >= 0) ? data.substring(i + 1) : data;
    }
}
