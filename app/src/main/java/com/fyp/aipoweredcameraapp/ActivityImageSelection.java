package com.fyp.aipoweredcameraapp;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.fyp.aipoweredcameraapp.data.ServicesDatabase;
import com.fyp.aipoweredcameraapp.data.SharedPref;
import com.fyp.aipoweredcameraapp.utils.CallbackDialog;
import com.fyp.aipoweredcameraapp.utils.DialogUtils;
import com.fyp.aipoweredcameraapp.utils.NetworkCheck;
import com.fyp.aipoweredcameraapp.utils.PermissionUtil;
import com.fyp.aipoweredcameraapp.utils.Tools;
import com.google.android.material.snackbar.Snackbar;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.android.Utils;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

//import com.sun.imageio.plugins.jpeg.JPEG;
//import java.util.*;


public class ActivityImageSelection extends AppCompatActivity {

    private View rootView;
    private ActionBar actionBar;
    private Toolbar toolbar;
    private Button previousBtn;
    private Button nextBtn;
    private ImageView img;
    private int module_selected;
    private String image_source;
    private Uri imgPath;
    private SharedPref sharedPref;

    public static final int GET_FROM_GALLERY = 1;

    /*static {
        try {
            System.loadLibrary("native-lib");

        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load.\n" + e);
            //System.exit(1);
        }
    }
    //static { System.loadLibrary("native-lib"); }
    native void synEFFromJNI(Long frame, Long res);
*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = getLayoutInflater().inflate(R.layout.activity_image_selection, null, false);
        setContentView(rootView);
        //setContentView(R.layout.activity_image_selection);

        sharedPref = new SharedPref(this);
        module_selected = sharedPref.getPref("module_selected");
        image_source = getIntent().getStringExtra("image_source");
        img = (ImageView) findViewById(R.id.loadImageView);
        previousBtn = (Button) findViewById(R.id.previous);
        nextBtn = (Button) findViewById(R.id.next);

        initToolbar();
        if (image_source.equals("camera"))
            initCameraSource();
        else // if (image_source.equals("gallery"))
            initGallerySource();

        if (module_selected == R.id.enhanced_image)
            initEnhanceImage();
        else if (module_selected == R.id.facial_features)
            initEditFacialFeatures();
        else // if (module_selected == R.id.selfie_manipulation)
            initSelfieManipulation();

        System.loadLibrary("native-lib");
    }

    private void initEnhanceImage() {
        nextBtn.setText(R.string.PROCESS);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processImage();
            }
        });
    }

    private void initEditFacialFeatures() {
        nextBtn.setText(R.string.UPLOAD);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });
    }

    private void initSelfieManipulation() {
        nextBtn.setText(R.string.UPLOAD);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });
    }

    private void initToolbar() {
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setTitle(R.string.image_preview);
    }

    private void initCameraSource() {
        String filePath = getIntent().getStringExtra("filePath");
        if (filePath.isEmpty())
            Snackbar.make(rootView,"Image file is empty or not valid", Snackbar.LENGTH_INDEFINITE).setAction("RETRY", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    previousBtn.performClick();
                }
            }).show();
        else {
            imgPath = Uri.parse(filePath);
            img.setImageURI(imgPath);
            //img.setImageURI(Uri.parse(filePath));
            Glide.with(img.getContext())
                    .load(filePath)
                    .into(img);
        }
        previousBtn.setText(R.string.RETAKE);
        previousBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //delete previous temp file
                File imgFile = new File(filePath);
                imgFile.delete();

                Intent i = new Intent(ActivityImageSelection.this, ActivityCamera.class);
                startActivity(i);

                //kill current activity
                finish();
            }
        });
    }

    private void initGallerySource() {
        requestImageFromGallery();
        previousBtn.setText(R.string.RESELECT);
        previousBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestImageFromGallery();
            }
        });
    }

    private void requestImageFromGallery() {
        startActivityForResult(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI), GET_FROM_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //Detects request codes
        if(requestCode==GET_FROM_GALLERY && resultCode == Activity.RESULT_OK) {
            imgPath = data.getData();
            String mimeType = getContentResolver().getType(imgPath);
            if (mimeType != null && mimeType.startsWith("image")) {
                img.setImageURI(imgPath);
            } else {
                Snackbar.make(rootView,"Select an image from gallery", Snackbar.LENGTH_INDEFINITE).setAction("RETRY", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestImageFromGallery();
                    }
                }).show();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void getFileSystemPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            PermissionUtil.showSystemDialogPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        else
            PermissionUtil.goToPermissionSettingScreen(this);
    }

    private void processImage() {
        //az modules

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());

        if (!PermissionUtil.isGranted(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(rootView,"File write permission must be enabled", Snackbar.LENGTH_INDEFINITE).setAction("GRANT PERMISSION", new View.OnClickListener() {
                @Override
                @RequiresApi(api = Build.VERSION_CODES.M)
                public void onClick(View v) {
                    getFileSystemPermission();
                }
            }).show();
            return;
        }

        //String root = Environment.getExternalStorageDirectory().getPath();
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CamAI");
        if(!folder.exists()){
            folder.mkdirs();
        }

        String sampleFileName = folder.getPath() + "/sample_picture_" + currentDateandTime + ".jpg";
        String camAiFileName = folder.getPath() + "/CamAI_sample_picture_" + currentDateandTime + ".jpg";

        /*val bb = image.planes[0].buffer;
        val buf = ByteArray(bb.remaining());
        bb.get(buf);*/

        ProgressDialog progressDialog = new ProgressDialog(ActivityImageSelection.this);
        progressDialog.setTitle("Image Enhancement"); // Setting Title
        progressDialog.setMessage("Processing..."); // Setting Message
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        progressDialog.show(); // Display Progress Dialog
        progressDialog.setCancelable(false);

        Runnable taskIE = () -> {
            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            progressDialog.dismiss();
        };

        Executor ext = Executors.newSingleThreadExecutor();
        ext.execute(taskIE);

        Mat prev = new Mat();
        Bitmap imgBmp = Tools.getBitmap(new File(imgPath.getPath()));
        Utils.bitmapToMat(imgBmp, prev);
        //Store the picture in mat object

        //Mat prev = Imgcodecs.imdecode(mat, Imgcodecs.IMREAD_UNCHANGED);
        Bitmap bitmap = ((BitmapDrawable)img.getDrawable()).getBitmap();
        Mat prev = new Mat();
        Utils.bitmapToMat(bitmap, prev);
        Mat res = new Mat(prev.cols(), prev.rows(), CvType.CV_8UC3);
        synEFFromJNI(prev.getNativeObjAddr(), res.getNativeObjAddr());

        //Pass mat to native C++ function

        //AiCam image
        Bitmap img = Bitmap.createBitmap(res.cols(), res.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(res, img);
        ByteArrayOutputStream stream2 = new ByteArrayOutputStream();
        img.compress(Bitmap.CompressFormat.JPEG, 100, stream2);
        byte[] data2 = stream2.toByteArray();

        //Sample image
        ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream1);
        byte[] data1 = stream1.toByteArray();

        /*
        String[] arr = fileName.split("/");
        arr[6] = "AICam" + arr[6];
        String mPictureFileName2 = TextUtils.join("/", arr);
        */

        try {
            FileOutputStream fos = new FileOutputStream(sampleFileName);
            fos.write(data1);
            fos.close();

            FileOutputStream fos2 = new FileOutputStream(camAiFileName);
            fos2.write(data2);
            fos2.close();

            String msg = "Photo capture succeeded at" + folder.getAbsolutePath();
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("CameraFragment", "Exception in photoCallback", e);
        }

        previousBtn.performClick();
    }

    public void dialogNoInternet() {
        Dialog dialog = new DialogUtils(this).buildDialogWarning(R.string.title_no_internet, R.string.msg_no_internet, R.string.TRY_AGAIN, R.string.CLOSE, R.drawable.img_no_internet, new CallbackDialog() {
            @Override
            public void onPositiveClick(Dialog dialog) {
                dialog.dismiss();
                retryUploadImage();
            }
            @Override
            public void onNegativeClick(Dialog dialog) {
                dialog.dismiss();
                onBackPressed();
            }
        });
        dialog.show();
    }

    private void uploadImage() {
        if (!NetworkCheck.isConnect(this)) {
            dialogNoInternet();
        } else {
            int pos = 0;
            ArrayList<String> images_list = new ArrayList<>();
            images_list.add(imgPath.getPath());

            Intent i = new Intent(ActivityImageSelection.this, ActivityFullScreenImage.class);
            i.putExtra(ActivityFullScreenImage.EXTRA_POS, pos);
            i.putStringArrayListExtra(ActivityFullScreenImage.EXTRA_IMGS, images_list);
            startActivity(i);
            //Intent intent = new Intent(ActivityImageSelection.this, ServicesDatabase.class);
            //intent.putExtra("Function", "upload_image");
            //intent.putExtra("image", bitmap);
            //startService(intent);
        }
    }

    private void retryUploadImage() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                uploadImage();
            }
        }, 2000);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int item_id = item.getItemId();
        if (item_id == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /*if(!isChangingConfigurations()) {
            deleteTempFiles(getCacheDir());
        }*/
    }

    @Override
    public void onStop() {
        super.onStop();
        if(!isChangingConfigurations()) {
            deleteTempFiles(getCacheDir());
        }
    }

    private boolean deleteTempFiles(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        deleteTempFiles(f);
                    } else {
                        f.delete();
                    }
                }
            }
        }
        return file.delete();
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (image_source.equals("camera") && imgPath != null) {
            File imgFile = new File(imgPath.getPath());
            imgFile.delete();
        }
        Intent i = new Intent(ActivityImageSelection.this, ActivityMain.class);
        startActivity(i);

        finish();
    }

    public native void synEFFromJNI(long frame, long res);

}
