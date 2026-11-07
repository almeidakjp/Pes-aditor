package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

public class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setNavigationBarColor(
                android.graphics.Color.BLACK
        );

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        getWindow().setStatusBarColor(
                android.graphics.Color.BLACK
        );

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
        }

        corrigirEdgeToEdge();
    }

    private void corrigirEdgeToEdge() {

        View content = findViewById(android.R.id.content);

        if (content == null) {
            return;
        }

        final int paddingLeft = content.getPaddingLeft();
        final int paddingTop = content.getPaddingTop();
        final int paddingRight = content.getPaddingRight();
        final int paddingBottom = content.getPaddingBottom();

        content.setOnApplyWindowInsetsListener((v, insets) -> {

            android.graphics.Insets barras =
                    insets.getInsets(
                            WindowInsets.Type.systemBars()
                    );

            v.setPadding(
                    paddingLeft + barras.left,
                    paddingTop + barras.top,
                    paddingRight + barras.right,
                    paddingBottom + barras.bottom
            );

            return insets;
        });

        content.requestApplyInsets();
    }
}
