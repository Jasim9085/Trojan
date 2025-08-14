package com.trojan;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide; // You will need to add the Glide dependency

// This activity is styled as a dialog to show an image from a URL.
public class ImageDisplayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_display);

        ImageView imageView = findViewById(R.id.imageViewDisplay);
        String imageUrl = getIntent().getStringExtra("image_url");

        if (imageUrl != null && !imageUrl.isEmpty()) {
            // Use the Glide library to efficiently load the image from the URL.
            Glide.with(this)
                 .load(imageUrl)
                 .into(imageView);
        }
    }
}
