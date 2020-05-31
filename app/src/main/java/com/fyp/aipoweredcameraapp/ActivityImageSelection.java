package com.fyp.aipoweredcameraapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fyp.aipoweredcameraapp.data.SharedPref;
import com.fyp.aipoweredcameraapp.utils.CallbackDialog;
import com.fyp.aipoweredcameraapp.utils.DialogUtils;
import com.fyp.aipoweredcameraapp.utils.NetworkCheck;
import com.fyp.aipoweredcameraapp.utils.Tools;
import com.google.android.material.snackbar.Snackbar;

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
import java.util.Date;

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

    native void synEFFromJNI(Long frame, Long res);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = getLayoutInflater().inflate(R.layout.activity_image_selection, null, false);
        setContentView(rootView);
        //setContentView(R.layout.activity_image_selection);

        sharedPref = new SharedPref(this);
        module_selected = sharedPref.getPref("module_selected");
        image_source = getIntent().getStringExtra("image_source");
        img = findViewById(R.id.loadImageView);
        previousBtn = findViewById(R.id.previous);
        nextBtn = findViewById(R.id.next);

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
        toolbar = findViewById(R.id.toolbar);
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

    private void processImage() {
        //az modules

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());

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

        Mat mat = new Mat();
        Bitmap imgBmp = Tools.getBitmap(new File(imgPath.getPath()));
        Utils.bitmapToMat(imgBmp, mat);
        //Store the picture in mat object

        //Mat prev = Imgcodecs.imdecode(mat, Imgcodecs.IMREAD_UNCHANGED);
        Mat prev = mat;
        Mat res = new Mat(prev.cols(), prev.rows(), CvType.CV_8UC3);

        //Pass mat to native C++ function
        synEFFromJNI(prev.dataAddr(), res.dataAddr());

        //AiCam image
        Bitmap img = Bitmap.createBitmap(res.cols(), res.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(res, img);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        img.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] data2 = stream.toByteArray();

        //Sample image
        ByteArrayOutputStream stream1 = new ByteArrayOutputStream();
        imgBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream1);
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

            String msg = "Photo capture succeeded";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("CameraFragment", "Exception in photoCallback", e);
        }

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
            //Intent intent = new Intent(ActivityFacialFeatures.this, ServicesDatabase.class);
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

}
