package com.ts.fn_camera2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.GridView;

import java.util.ArrayList;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import com.ts.fn_camera2.adapter.GalleryAdapter;


public class GalleryActivity extends AppCompatActivity {
    private ArrayList<String> pictures = new ArrayList<>();
    private GridView gv_gallery;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        initView();
    }

    private void initView() {
        gv_gallery = findViewById(R.id.gv_gallery);

        Intent intent = getIntent();
        pictures = intent.getStringArrayListExtra("pictures");
        Toast.makeText(GalleryActivity.this, pictures.size() + " ", Toast.LENGTH_SHORT).show();
        GalleryAdapter galleryAdapter = new GalleryAdapter(pictures, this);
        gv_gallery.setAdapter(galleryAdapter);
        gv_gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                Intent intent = new Intent(Intent.ACTION_VIEW);
                Bitmap bitmap = BitmapFactory.decodeFile(pictures.get(i));
                Uri uri = Uri.parse(MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, null, null));    //将bitmap转换为uri
                intent.setDataAndType(uri, "image/*");    //设置intent数据和图片格式

                startActivity(intent);
            }
        });
    }
}
