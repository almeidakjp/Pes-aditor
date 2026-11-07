package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NomesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(24), dp(18), dp(18));
        layout.setBackgroundColor(0xff101112);

        TextView titulo = new TextView(this);
        titulo.setText("PLAYER NAMES");
        titulo.setTextSize(26);
        titulo.setTextColor(Color.WHITE);
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        titulo.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        layout.addView(titulo, lp(-1, dp(58), 0, dp(22)));

        TextView subtitulo = new TextView(this);
        subtitulo.setText("FERRAMENTAS");
        subtitulo.setTextSize(13);
        subtitulo.setTextColor(0xffa8aaad);
        subtitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(subtitulo, lp(-1, -2, 0, dp(10)));

        Button btnNomes = menuButton("NOMES", "Redimensionar texturas de nomes");
        layout.addView(btnNomes, lp(-1, dp(78), 0, dp(10)));

        Button btnNomesNovo = menuButton("NOMES NOVOS", "Criar novos nomes nas texturas");
        layout.addView(btnNomesNovo, lp(-1, dp(78), 0, dp(10)));

        Button btnRenomear = menuButton("RENOMEAR", "Renomear arquivos de nomes");
        layout.addView(btnRenomear, lp(-1, dp(78), 0, 0));

        btnNomes.setOnClickListener(v ->
                startActivity(new Intent(this, NomesProcessActivity.class)));
        btnNomesNovo.setOnClickListener(v ->
                startActivity(new Intent(this, NomesNovosActivity.class)));
        btnRenomear.setOnClickListener(v ->
                startActivity(new Intent(this, RenomearActivity.class)));

        setContentView(layout);
    }

    private Button menuButton(String title, String description) {
        Button b = new Button(this);
        b.setText(title + "\n" + description);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(20), 0, dp(16), 0);
        b.setBackground(round(0xff191b1d, 16, 0xff34373b));
        return b;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
