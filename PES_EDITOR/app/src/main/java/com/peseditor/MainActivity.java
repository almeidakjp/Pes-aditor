package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Button btnFace = findViewById(R.id.btnFace);
        Button btnHair = findViewById(R.id.btnHair);
        Button btnCpk = findViewById(R.id.btnCpk);
        Button btnZlib = findViewById(R.id.btnZlib);
        Button btnNomes = findViewById(R.id.btnNomes);
        Button btnRenomear = findViewById(R.id.btnRenomear);

        btnFace.setOnClickListener(v -> {
            Intent intent = new Intent(this, FaceImportActivity.class);
            startActivity(intent);
        });

        btnHair.setOnClickListener(v -> {
            Intent intent = new Intent(this, HairImportActivity.class);
            startActivity(intent);
        });

        btnCpk.setOnClickListener(v -> {
            Intent intent = new Intent(this, CpkActivity.class);
            startActivity(intent);
        });

        btnZlib.setOnClickListener(v -> startActivity(new Intent(this, ZlibActivity.class)));

        btnNomes.setOnClickListener(v -> {
            Intent intent = new Intent(this, NomesActivity.class);
            startActivity(intent);
        });

        btnRenomear.setText("BATCH RENOME");
        btnRenomear.setOnClickListener(v -> {
            Intent intent = new Intent(
                    this,
                    BatchRenomeActivity.class
            );
            startActivity(intent);
        });
    }
}
