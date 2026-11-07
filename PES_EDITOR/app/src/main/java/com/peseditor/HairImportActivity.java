package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.view.View;
import android.database.Cursor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.Inflater;
import java.util.zip.Deflater;

public class HairImportActivity extends BaseActivity {

    private static final int PICK_BIN = 200;
    private static final int PICK_PNG = 201;
    private static final int PICK_OUTPUT = 202;
    private static final int EXPORT_PNG = 203;
    private static final int IMPORT_PNG = 204;

    private static final byte[] HAIR_HEADER = new byte[]{
            (byte)0x94,(byte)0x72,(byte)0x85,(byte)0x29,0x01,0x00,0x00,0x00
    };
    private static final int PALETTE_REL_OFFSET = 0x40;
    private static final int TEXTURE_REL_OFFSET = 0x440;
    private static final int PALETTE_SIZE = 0x400;
    private static final int TEXTURE_SIZE = 0x4000;
    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;

    private TextView txtBin;
    private TextView txtPng;
    private TextView txtStatus;

    private final ArrayList<Uri> binUris = new ArrayList<>();
    private final ArrayList<Uri> pngUris = new ArrayList<>();

    private Uri outputTreeUri = null;
    private LinearLayout binListLayout;
    private ImageView hairPreview;
    private Button btnExportar;
    private Button btnImportar;
    private Uri selectedBinUri = null;
    private Bitmap selectedHairBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(24),dp(18),dp(14));
        root.setBackgroundColor(0xff101112);

        LinearLayout topBar=new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=uiText("HAIR IMPORT",22,Color.WHITE,true);
        title.setSingleLine(true);
        topBar.addView(title,new LinearLayout.LayoutParams(0,dp(52),1f));

        Button btnBin=uiSmallButton("ABRIR");
        LinearLayout.LayoutParams abrirLp=new LinearLayout.LayoutParams(dp(60),dp(44));
        abrirLp.leftMargin=dp(10);
        topBar.addView(btnBin,abrirLp);
        Button btnPng=uiSmallButton("PNG");
        LinearLayout.LayoutParams pngLp=new LinearLayout.LayoutParams(dp(60),dp(44));
        pngLp.leftMargin=dp(6); topBar.addView(btnPng,pngLp);
        Button btnProcessar=uiSmallButton("BATCH");
        LinearLayout.LayoutParams prLp=new LinearLayout.LayoutParams(dp(60),dp(44));
        prLp.leftMargin=dp(6); topBar.addView(btnProcessar,prLp);
        root.addView(topBar,uiLp(-1,52,0,10));

        LinearLayout summary=new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setPadding(dp(12),dp(10),dp(12),dp(10));
        summary.setBackground(uiRound(0xff191b1d,16,0xff2c2f33));

        LinearLayout a=summaryCell("BINs:");
        txtBin=(TextView)a.getChildAt(1);
        summary.addView(a,new LinearLayout.LayoutParams(0,dp(60),1f));
        LinearLayout b=summaryCell("PNGs:");
        txtPng=(TextView)b.getChildAt(1);
        summary.addView(b,new LinearLayout.LayoutParams(0,dp(60),1f));
        LinearLayout c=summaryCell("SELECIONADO:");
        TextView selectedLabel=(TextView)c.getChildAt(1);
        selectedLabel.setText("-");
        selectedLabel.setTag("selectedLabel");
        summary.addView(c,new LinearLayout.LayoutParams(0,dp(60),1f));
        root.addView(summary,uiLp(-1,-2,0,10));

        LinearLayout cols=new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(dp(12),0,dp(12),0);
        TextView colName=uiText("ARQUIVO BIN",11,0xff9b9da2,true);
        colName.setGravity(Gravity.CENTER_VERTICAL);
        cols.addView(colName,new LinearLayout.LayoutParams(0,dp(38),1f));
        TextView colFace=uiText("PRÉVIA",11,0xff9b9da2,true);
        colFace.setGravity(Gravity.CENTER_VERTICAL);
        cols.addView(colFace,new LinearLayout.LayoutParams(0,dp(38),1f));
        root.addView(cols);

        LinearLayout browser=new LinearLayout(this);
        browser.setOrientation(LinearLayout.HORIZONTAL);

        ScrollView listScroll=new ScrollView(this);
        binListLayout=new LinearLayout(this);
        binListLayout.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(binListLayout);
        browser.addView(listScroll,new LinearLayout.LayoutParams(0,-1,1f));

        LinearLayout previewPanel=new LinearLayout(this);
        previewPanel.setOrientation(LinearLayout.VERTICAL);
        previewPanel.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        previewPanel.setPadding(dp(10),dp(8),0,0);

        hairPreview=new ImageView(this);
        hairPreview.setAdjustViewBounds(true);
        hairPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewPanel.addView(hairPreview,new LinearLayout.LayoutParams(-1,dp(190)));

        LinearLayout hairActions=new LinearLayout(this);
        hairActions.setOrientation(LinearLayout.HORIZONTAL);

        btnExportar=uiButton("EXPORTAR");
        btnExportar.setTextSize(11);
        btnExportar.setSingleLine(true);
        btnExportar.setEnabled(false);
        hairActions.addView(btnExportar,new LinearLayout.LayoutParams(0,dp(48),1f));

        btnImportar=uiButton("IMPORTAR");
        btnImportar.setTextSize(11);
        btnImportar.setSingleLine(true);
        btnImportar.setEnabled(false);
        LinearLayout.LayoutParams importLp=new LinearLayout.LayoutParams(0,dp(48),1f);
        importLp.leftMargin=dp(6);
        hairActions.addView(btnImportar,importLp);

        previewPanel.addView(hairActions,uiLp(-1,48,8,0));

        browser.addView(previewPanel,new LinearLayout.LayoutParams(0,-1,1f));
        root.addView(browser,new LinearLayout.LayoutParams(-1,0,1f));

        txtStatus=uiText("",13,0xffa8a9ad,false);
        txtStatus.setPadding(dp(4),dp(8),dp(4),dp(2));
        root.addView(txtStatus);

        btnBin.setOnClickListener(v->selecionarBins());
        btnPng.setOnClickListener(v->selecionarPngs());
        btnProcessar.setOnClickListener(v->iniciarProcessamento());
        btnExportar.setOnClickListener(v->exportarHairSelecionado());
        btnImportar.setOnClickListener(v->importarHairSelecionado());

        setContentView(root);
    }

    private void selecionarBins() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_BIN);
    }

    private void selecionarPngs() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_PNG);
    }

    private void iniciarProcessamento() {

        if (binUris.isEmpty()) {
            txtStatus.setText("❌ Nenhum BIN selecionado.");
            return;
        }

        if (pngUris.isEmpty()) {
            txtStatus.setText("❌ Nenhum PNG selecionado.");
            return;
        }

        escolherPastaSaida();
    }

    private void escolherPastaSaida() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );

        startActivityForResult(intent, PICK_OUTPUT);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == PICK_BIN) {

            binUris.clear();

            if (data.getClipData() != null) {

                int count = data.getClipData().getItemCount();

                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    binUris.add(uri);
                }

            } else if (data.getData() != null) {

                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}
                binUris.add(uri);
            }

            atualizarLista();
            atualizarListaBinsVisual();
        }

        else if (requestCode == PICK_PNG) {

            pngUris.clear();

            if (data.getClipData() != null) {

                int count = data.getClipData().getItemCount();

                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    pngUris.add(uri);
                }

            } else if (data.getData() != null) {

                pngUris.add(data.getData());
            }

            atualizarLista();
        }

        else if (requestCode == EXPORT_PNG) {
            Uri out = data.getData();
            if (out != null && selectedHairBitmap != null) {
                try {
                    OutputStream os = getContentResolver().openOutputStream(out);
                    if (os == null) throw new IOException("Não foi possível criar PNG");
                    selectedHairBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                    os.close();
                    mostrarStatusTemporario("✅ Hair exportado.");
                } catch (Exception e) { txtStatus.setText("❌ " + e.getMessage()); }
            }
        }
        else if (requestCode == IMPORT_PNG) {
            Uri png = data.getData();
            if (png != null && selectedBinUri != null) {
                try {
                    byte[] novoBin = criarHairBinComNovaTextura(selectedBinUri, png);

                    OutputStream os = getContentResolver().openOutputStream(selectedBinUri, "wt");
                    if (os == null) throw new IOException("Não foi possível atualizar o BIN");
                    os.write(novoBin);
                    os.close();

                    // Reabre o próprio BIN já alterado e atualiza a prévia.
                    selectedHairBitmap = lerHairDoBin(selectedBinUri);
                    hairPreview.setImageBitmap(selectedHairBitmap);

                    mostrarStatusTemporario("✅ Hair importado no BIN.");
                } catch (Exception e) {
                    txtStatus.setText("❌ " + e.getMessage());
                }
            }
        }

        else if (requestCode == PICK_OUTPUT) {

            outputTreeUri = data.getData();

            if (outputTreeUri == null) {
                txtStatus.setText("❌ Pasta de saída inválida.");
                return;
            }

            try {
                int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                         Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getContentResolver().takePersistableUriPermission(
                        outputTreeUri,
                        flags
                );
            } catch (Exception ignored) {
            }

            processarTodos();
        }
    }

    private void atualizarLista() {
        txtBin.setText(String.valueOf(binUris.size()));
        txtPng.setText(String.valueOf(pngUris.size()));
    }

    private String getFileName(Uri uri) {

        String result = null;

        Cursor cursor = null;

        try {

            cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(
                        cursor.getColumnIndexOrThrow(
                                OpenableColumns.DISPLAY_NAME
                        )
                );
            }

        } catch (Exception ignored) {

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }

        if (result == null) {
            result = uri.getLastPathSegment();
        }

        if (result == null) {
            result = "arquivo";
        }

        return result;
    }

    private void atualizarListaBinsVisual() {
        binListLayout.removeAllViews();
        for (Uri uri : binUris) {
            TextView item=uiText(getFileName(uri),15,Color.WHITE,true);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14),0,dp(12),0);
            item.setBackground(uiRound(0xff191b1d,16,0xff303338));
            item.setOnClickListener(v -> selecionarHairParaPreview(uri));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(62));
            lp.setMargins(0,0,dp(4),dp(2));
            binListLayout.addView(item,lp);
        }
    }

    private void selecionarHairParaPreview(Uri uri) {
        selectedBinUri = uri;
        txtStatus.setText("⏳ Carregando hair...");
        new Thread(() -> {
            try {
                Bitmap bmp = lerHairDoBin(uri);
                runOnUiThread(() -> {
                    if (selectedHairBitmap != null && selectedHairBitmap != bmp) selectedHairBitmap.recycle();
                    selectedHairBitmap = bmp;
                    hairPreview.setImageBitmap(bmp);
                    btnExportar.setEnabled(true);
                    btnImportar.setEnabled(true);
                    mostrarStatusTemporario("✅ " + getFileName(uri));
                });
            } catch (Exception e) {
                runOnUiThread(() -> txtStatus.setText("❌ " + e.getMessage()));
            }
        }).start();
    }
    private Bitmap lerHairDoBin(Uri uri) throws Exception {
        byte[] original = readAll(uri);
        if (original.length < 0x20) throw new Exception("BIN inválido");
        int zsize = readIntLE(original, 4);
        int size = readIntLE(original, 8);
        if (zsize <= 0 || 0x20 + zsize > original.length) throw new Exception("Cabeçalho zlib inválido");
        byte[] compressed = new byte[zsize];
        System.arraycopy(original, 0x20, compressed, 0, zsize);
        byte[] dec = inflate(compressed, size);
        int h = findHairHeader(dec);
        if (h < 0) throw new Exception("Cabeçalho da textura Hair não encontrado no BIN.");
        int po = h + PALETTE_REL_OFFSET, to = h + TEXTURE_REL_OFFSET;
        if (po + PALETTE_SIZE > dec.length || to + TEXTURE_SIZE > dec.length)
            throw new Exception("Estrutura hair diferente ou BIN incompatível.");

        int[] palette = new int[256];
        for (int i=0;i<256;i++) {
            int r=dec[po+i*4]&255, g=dec[po+i*4+1]&255, b=dec[po+i*4+2]&255, a=dec[po+i*4+3]&255;
            palette[i]=(a<<24)|(r<<16)|(g<<8)|b;
        }
        byte[] sw = new byte[TEXTURE_SIZE];
        System.arraycopy(dec,to,sw,0,TEXTURE_SIZE);
        byte[] indices = new byte[TEXTURE_SIZE];
        int rowblocks=WIDTH/16, rowblocksadd=(rowblocks-1)<<7, blockaddress=0;
        for(int y=0;y<HEIGHT;y++){
            for(int i=0;i<rowblocks;i++){
                int dst=y*WIDTH+i*16, src=blockaddress+i*128;
                System.arraycopy(sw,src,indices,dst,16);
            }
            blockaddress+=16;
            if((y&7)==7) blockaddress+=rowblocksadd;
        }
        int[] pixels=new int[WIDTH*HEIGHT];
        for(int i=0;i<pixels.length;i++) pixels[i]=palette[indices[i]&255];
        return Bitmap.createBitmap(pixels,WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    }


    private void exportarHairSelecionado() {
        if (selectedBinUri == null || selectedHairBitmap == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_TITLE, baseName(getFileName(selectedBinUri)) + ".png");
        startActivityForResult(i, EXPORT_PNG);
    }

    private void importarHairSelecionado() {
        if (selectedBinUri == null) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, IMPORT_PNG);
    }
    private byte[] criarHairBinComNovaTextura(Uri bin, Uri png) throws Exception {
        byte[] original=readAll(bin);
        byte[][] hair=pngToHair(readAll(png));
        if(original.length<0x20) throw new Exception("BIN inválido");
        int zsize=readIntLE(original,4), size=readIntLE(original,8);
        if(zsize<=0 || 0x20+zsize>original.length) throw new Exception("Cabeçalho zlib inválido");
        byte[] compressed=new byte[zsize];
        System.arraycopy(original,0x20,compressed,0,zsize);
        byte[] dec=inflate(compressed,size);
        int h=findHairHeader(dec);
        if(h<0) throw new Exception("Cabeçalho da textura Hair não encontrado no BIN.");
        int po=h+PALETTE_REL_OFFSET, to=h+TEXTURE_REL_OFFSET;
        if(po+PALETTE_SIZE>dec.length || to+TEXTURE_SIZE>dec.length)
            throw new Exception("Estrutura hair diferente ou BIN incompatível.");
        System.arraycopy(hair[0],0,dec,po,PALETTE_SIZE);
        System.arraycopy(hair[1],0,dec,to,TEXTURE_SIZE);
        byte[] nc=deflate(dec);
        byte[] header=new byte[0x20]; System.arraycopy(original,0,header,0,0x20);
        writeIntLE(header,4,nc.length); writeIntLE(header,8,dec.length);
        byte[] out=new byte[0x20+nc.length];
        System.arraycopy(header,0,out,0,0x20); System.arraycopy(nc,0,out,0x20,nc.length);
        return out;
    }

    private void mostrarStatusTemporario(String msg) {
        txtStatus.setText(msg);
        txtStatus.removeCallbacks(clearStatusRunnable);
        txtStatus.postDelayed(clearStatusRunnable, 3000);
    }

    private final Runnable clearStatusRunnable = () -> txtStatus.setText("");

    private void processarTodos() {
        txtStatus.setText("⏳ Processando...");

        new Thread(() -> {

            int pares = 0;
            int semPng = 0;
            int erros = 0;

            StringBuilder resultado = new StringBuilder();

            HashMap<String, Uri> pngMap = new HashMap<>();

            for (Uri png : pngUris) {
                String nome = getFileName(png);
                String base = baseName(nome);

                pngMap.put(
                        base.toLowerCase(Locale.US),
                        png
                );
            }

            for (Uri bin : binUris) {

                String binName = getFileName(bin);
                String base = baseName(binName);

                Uri png = pngMap.get(
                        base.toLowerCase(Locale.US)
                );

                if (png == null) {
                    semPng++;

                    resultado.append(
                            "❌ " + binName +
                            " — PNG correspondente não encontrado\n"
                    );

                    continue;
                }

                try {

                    byte[] original = readAll(bin);
                    byte[] pngData = readAll(png);

                    byte[][] hair = pngToHair(pngData);

                    if (original.length < 0x20) {
                        throw new Exception(
                                "BIN muito pequeno ou formato diferente."
                        );
                    }

                    int zsize = readIntLE(original, 4);
                    int size = readIntLE(original, 8);

                    if (zsize <= 0 ||
                            0x20 + zsize > original.length) {

                        throw new Exception(
                                "Cabeçalho zlib inválido."
                        );
                    }

                    byte[] compressed =
                            new byte[zsize];

                    System.arraycopy(
                            original,
                            0x20,
                            compressed,
                            0,
                            zsize
                    );

                    byte[] data =
                            inflate(
                                    compressed,
                                    size
                            );

                    if (data.length != size) {
                        throw new Exception(
                                "Tamanho interno inesperado."
                        );
                    }

                    int headerOffset =
                            findHairHeader(data);

                    if (headerOffset < 0) {
                        throw new Exception(
                                "Cabeçalho da textura Hair não encontrado no BIN."
                        );
                    }

                    int paletteOffset =
                            headerOffset +
                            PALETTE_REL_OFFSET;

                    int textureOffset =
                            headerOffset +
                            TEXTURE_REL_OFFSET;

                    if (textureOffset +
                            TEXTURE_SIZE > data.length) {

                        throw new Exception(
                                "Estrutura hair diferente ou BIN incompatível."
                        );
                    }

                    System.arraycopy(
                            hair[0],
                            0,
                            data,
                            paletteOffset,
                            PALETTE_SIZE
                    );

                    System.arraycopy(
                            hair[1],
                            0,
                            data,
                            textureOffset,
                            TEXTURE_SIZE
                    );

                    byte[] newCompressed =
                            deflate(data);

                    byte[] header =
                            new byte[0x20];

                    System.arraycopy(
                            original,
                            0,
                            header,
                            0,
                            0x20
                    );

                    writeIntLE(
                            header,
                            4,
                            newCompressed.length
                    );

                    writeIntLE(
                            header,
                            8,
                            data.length
                    );

                    byte[] output =
                            new byte[
                                    0x20 +
                                    newCompressed.length
                            ];

                    System.arraycopy(
                            header,
                            0,
                            output,
                            0,
                            0x20
                    );

                    System.arraycopy(
                            newCompressed,
                            0,
                            output,
                            0x20,
                            newCompressed.length
                    );

                    salvarArquivo(
                            outputTreeUri,
                            binName,
                            output
                    );

                    pares++;

                    resultado.append(
                            "✓ " + base + "\n"
                    );

                } catch (Exception e) {

                    erros++;

                    resultado.append(
                            "❌ " + binName +
                            " — " +
                            e.getMessage() +
                            "\n"
                    );
                }
            }

            final int fPares = pares;
            final int fSemPng = semPng;
            final int fErros = erros;
            final String fResultado =
                    resultado.toString();

            runOnUiThread(() ->
                    txtStatus.setText(
                            "Pares processados: " +
                            fPares +
                            "\n\n" +
                            "BINs sem PNG: " +
                            fSemPng +
                            "\n\n" +
                            "Erros: " +
                            fErros +
                            "\n\n" +
                            fResultado
                    )
            );

        }).start();
    }

    private byte[] readAll(Uri uri)
            throws IOException {

        InputStream in =
                getContentResolver()
                        .openInputStream(uri);

        if (in == null) {
            throw new IOException(
                    "Não foi possível abrir o arquivo"
            );
        }

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];

        int n;

        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }

        in.close();

        return out.toByteArray();
    }

    private void salvarArquivo(
            Uri treeUri,
            String nome,
            byte[] dados)
            throws Exception {

        Uri arquivo = DocumentsContract
                .buildDocumentUriUsingTree(
                        treeUri,
                        DocumentsContract
                                .getTreeDocumentId(treeUri)
                );

        Uri novo = DocumentsContract.createDocument(
                getContentResolver(),
                arquivo,
                "application/octet-stream",
                nome
        );

        if (novo == null) {
            throw new IOException(
                    "Não foi possível criar " + nome
            );
        }

        OutputStream out =
                getContentResolver()
                        .openOutputStream(novo);

        if (out == null) {
            throw new IOException(
                    "Não foi possível gravar " + nome
            );
        }

        out.write(dados);
        out.flush();
        out.close();
    }

    private static int findHairHeader(
            byte[] data) {

        outer:

        for (int i = 0;
             i <= data.length - HAIR_HEADER.length;
             i++) {

            for (int j = 0;
                 j < HAIR_HEADER.length;
                 j++) {

                if (data[i + j] != HAIR_HEADER[j]) {
                    continue outer;
                }
            }

            return i;
        }

        return -1;
    }


    private static byte[][] pngToHair(
            byte[] pngData)
            throws Exception {

        Bitmap bitmap =
                BitmapFactory.decodeByteArray(
                        pngData,
                        0,
                        pngData.length
                );

        if (bitmap == null) {
            throw new Exception("PNG inválido.");
        }

        int bw = bitmap.getWidth();
        int bh = bitmap.getHeight();

        if (bw != WIDTH || bh != HEIGHT) {
            Bitmap resized = Bitmap.createScaledBitmap(
                    bitmap,
                    WIDTH,
                    HEIGHT,
                    true
            );
            if (resized == null) {
                bitmap.recycle();
                throw new Exception("Não foi possível redimensionar o PNG para 128x128.");
            }
            if (resized != bitmap) {
                bitmap.recycle();
            }
            bitmap = resized;
        }

        int[] pixels =
                new int[WIDTH * HEIGHT];

        bitmap.getPixels(
                pixels,
                0,
                WIDTH,
                0,
                0,
                WIDTH,
                HEIGHT
        );

        bitmap.recycle();

        /*
         * O Hair usa CLUT8:
         * máximo de 256 cores.
         *
         * Android decodifica o PNG como ARGB.
         * Portanto precisamos quantizar a imagem quando
         * ela possui mais de 256 cores.
         *
         * Primeiro contamos as cores exatas.
         */
        HashMap<Integer, Integer> frequencias =
                new HashMap<>();

        for (int color : pixels) {
            Integer n = frequencias.get(color);

            if (n == null) {
                frequencias.put(color, 1);
            } else {
                frequencias.put(color, n + 1);
            }
        }

        ArrayList<Integer> cores =
                new ArrayList<>(frequencias.keySet());

        /*
         * Se já existem <= 256 cores, mantém exatamente
         * as cores originais.
         */
        if (cores.size() > 256) {

            /*
             * Ordena pelas cores mais frequentes.
             * Isso evita o erro "mais de 256 cores" e
             * preserva as cores predominantes do Hair.
             */
            cores.sort((a, b) ->
                    Integer.compare(
                            frequencias.get(b),
                            frequencias.get(a)
                    )
            );

            cores = new ArrayList<>(
                    cores.subList(0, 256)
            );
        }

        int[] palette =
                new int[256];

        for (int i = 0;
             i < cores.size();
             i++) {

            palette[i] = cores.get(i);
        }

        /*
         * Converte cada pixel para o índice da cor
         * mais próxima da paleta.
         */
        byte[] indices =
                new byte[TEXTURE_SIZE];

        for (int i = 0;
             i < pixels.length;
             i++) {

            int color = pixels[i];

            int best = 0;
            long bestDistance = Long.MAX_VALUE;

            int alpha = (color >> 24) & 255;
            int red   = (color >> 16) & 255;
            int green = (color >> 8) & 255;
            int blue  = color & 255;

            for (int p = 0;
                 p < cores.size();
                 p++) {

                int pc = palette[p];

                int pa = (pc >> 24) & 255;
                int pr = (pc >> 16) & 255;
                int pg = (pc >> 8) & 255;
                int pb = pc & 255;

                int da = alpha - pa;
                int dr = red - pr;
                int dg = green - pg;
                int db = blue - pb;

                long distance =
                        (long) da * da +
                        (long) dr * dr +
                        (long) dg * dg +
                        (long) db * db;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = p;
                }
            }

            indices[i] = (byte) best;
        }

        /*
         * Paleta Hair = RGBA8888.
         */
        byte[] rgba =
                new byte[PALETTE_SIZE];

        for (int i = 0;
             i < 256;
             i++) {

            int color =
                    palette[i];

            rgba[i * 4] =
                    (byte)((color >> 16) & 255);

            rgba[i * 4 + 1] =
                    (byte)((color >> 8) & 255);

            rgba[i * 4 + 2] =
                    (byte)(color & 255);

            rgba[i * 4 + 3] =
                    (byte)((color >> 24) & 255);
        }

        /*
         * Swizzle PSP CLUT8 128x128.
         */
        byte[] sw =
                new byte[TEXTURE_SIZE];

        int rowblocks =
                WIDTH / 16;

        int rowblocksadd =
                (rowblocks - 1) << 7;

        int blockaddress = 0;

        for (int y = 0;
             y < HEIGHT;
             y++) {

            for (int i = 0;
                 i < rowblocks;
                 i++) {

                int src =
                        y * WIDTH +
                        i * 16;

                int dst =
                        blockaddress +
                        i * 128;

                System.arraycopy(
                        indices,
                        src,
                        sw,
                        dst,
                        16
                );
            }

            blockaddress += 16;

            if ((y & 7) == 7) {
                blockaddress += rowblocksadd;
            }
        }

        return new byte[][]{
                rgba,
                sw
        };
    }


    private static int readIntLE(
            byte[] data,
            int offset) {

        return
                (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeIntLE(
            byte[] data,
            int offset,
            int value) {

        data[offset] =
                (byte)(value & 0xFF);

        data[offset + 1] =
                (byte)((value >> 8) & 0xFF);

        data[offset + 2] =
                (byte)((value >> 16) & 0xFF);

        data[offset + 3] =
                (byte)((value >> 24) & 0xFF);
    }

    private static byte[] inflate(
            byte[] input,
            int expected)
            throws Exception {

        Inflater inflater =
                new Inflater();

        inflater.setInput(input);

        ByteArrayOutputStream out =
                new ByteArrayOutputStream(
                        Math.max(expected, 1024)
                );

        byte[] buffer =
                new byte[8192];

        try {

            while (!inflater.finished()) {

                int count =
                        inflater.inflate(buffer);

                if (count == 0) {

                    if (inflater.needsDictionary()) {
                        throw new Exception(
                                "zlib precisa de dicionário"
                        );
                    }

                    if (inflater.needsInput()) {
                        break;
                    }
                }

                out.write(buffer, 0, count);
            }

            return out.toByteArray();

        } finally {

            inflater.end();
        }
    }

    private static byte[] deflate(
            byte[] input) {

        Deflater deflater =
                new Deflater(
                        Deflater.BEST_COMPRESSION
                );

        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        byte[] buffer =
                new byte[8192];

        while (!deflater.finished()) {

            int count =
                    deflater.deflate(buffer);

            out.write(
                    buffer,
                    0,
                    count
            );
        }

        deflater.end();

        return out.toByteArray();
    }


    private static byte[] compressDxt1(
            Bitmap bitmap) {

        int blocksX = WIDTH / 4;
        int blocksY = HEIGHT / 4;

        byte[] result =
                new byte[
                        blocksX *
                        blocksY *
                        8
                ];

        int pos = 0;

        for (int by = 0;
             by < blocksY;
             by++) {

            for (int bx = 0;
                 bx < blocksX;
                 bx++) {

                int minR = 255;
                int minG = 255;
                int minB = 255;

                int maxR = 0;
                int maxG = 0;
                int maxB = 0;

                int[] pixels =
                        new int[16];

                int p = 0;

                for (int y = 0;
                     y < 4;
                     y++) {

                    for (int x = 0;
                         x < 4;
                         x++) {

                        int color =
                                bitmap.getPixel(
                                        bx * 4 + x,
                                        by * 4 + y
                                );

                        pixels[p++] = color;

                        int r =
                                (color >> 16) & 255;

                        int g =
                                (color >> 8) & 255;

                        int b =
                                color & 255;

                        minR = Math.min(
                                minR,
                                r
                        );

                        minG = Math.min(
                                minG,
                                g
                        );

                        minB = Math.min(
                                minB,
                                b
                        );

                        maxR = Math.max(
                                maxR,
                                r
                        );

                        maxG = Math.max(
                                maxG,
                                g
                        );

                        maxB = Math.max(
                                maxB,
                                b
                        );
                    }
                }

                int c0 =
                        rgb565(
                                maxR,
                                maxG,
                                maxB
                        );

                int c1 =
                        rgb565(
                                minR,
                                minG,
                                minB
                        );

                if (c0 < c1) {

                    int temp = c0;
                    c0 = c1;
                    c1 = temp;
                }

                int[] palette =
                        new int[4];

                palette[0] =
                        rgb565ToRgb(c0);

                palette[1] =
                        rgb565ToRgb(c1);

                palette[2] =
                        interpolate(
                                palette[0],
                                palette[1],
                                2,
                                1,
                                3
                        );

                palette[3] =
                        interpolate(
                                palette[0],
                                palette[1],
                                1,
                                2,
                                3
                        );

                int indices = 0;

                for (int i = 15;
                     i >= 0;
                     i--) {

                    int color =
                            pixels[i];

                    int best = 0;
                    int bestDistance =
                            Integer.MAX_VALUE;

                    for (int k = 0;
                         k < 4;
                         k++) {

                        int distance =
                                colorDistance(
                                        color,
                                        palette[k]
                                );

                        if (distance <
                                bestDistance) {

                            bestDistance =
                                    distance;

                            best = k;
                        }
                    }

                    indices =
                            (indices << 2) |
                            best;
                }

                result[pos++] =
                        (byte)(c0 & 255);

                result[pos++] =
                        (byte)((c0 >> 8) & 255);

                result[pos++] =
                        (byte)(c1 & 255);

                result[pos++] =
                        (byte)((c1 >> 8) & 255);

                result[pos++] =
                        (byte)(indices & 255);

                result[pos++] =
                        (byte)((indices >> 8) & 255);

                result[pos++] =
                        (byte)((indices >> 16) & 255);

                result[pos++] =
                        (byte)((indices >> 24) & 255);
            }
        }

        return result;
    }

    private static int rgb565(
            int r,
            int g,
            int b) {

        return
                ((r >> 3) << 11) |
                ((g >> 2) << 5) |
                (b >> 3);
    }


    private static String baseName(
            String name) {

        int dot =
                name.lastIndexOf('.');

        if (dot > 0) {
            return name.substring(
                    0,
                    dot
            );
        }

        return name;
    }
    private LinearLayout summaryCell(String label){
        LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); x.setGravity(Gravity.CENTER_VERTICAL);
        x.addView(uiText(label,11,0xff9b9da2,true));
        TextView v=uiText("-",14,Color.WHITE,true); v.setSingleLine(true); x.addView(v); return x;
    }
    private TextView uiText(String text,int sp,int color,boolean bold){
        TextView v=new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private Button uiButton(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setAllCaps(false);
        b.setBackground(uiRound(0xff242629,18,0xff393b40)); return b;
    }
    private Button uiSmallButton(String text){ Button b=uiButton(text); b.setTextSize(11); b.setPadding(dp(4),0,dp(4),0); return b; }
    private GradientDrawable uiRound(int fill,int radius,int stroke){
        GradientDrawable g=new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(1),stroke); return g;
    }
    private LinearLayout.LayoutParams uiLp(int w,int h,int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w==-1?-1:dp(w),h==-2?-2:dp(h));
        p.setMargins(0,dp(top),0,dp(bottom)); return p;
    }
    private int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }

    private static int rgb565ToRgb(int c){
        int r=(c>>11)&31, g=(c>>5)&63, b=c&31;
        r=(r<<3)|(r>>2); g=(g<<2)|(g>>4); b=(b<<3)|(b>>2);
        return (r<<16)|(g<<8)|b;
    }
    private static int interpolate(int a,int b,int wa,int wb,int d){
        int r=(((a>>16)&255)*wa+((b>>16)&255)*wb)/d;
        int g=(((a>>8)&255)*wa+((b>>8)&255)*wb)/d;
        int bl=((a&255)*wa+(b&255)*wb)/d;
        return (r<<16)|(g<<8)|bl;
    }
    private static int colorDistance(int a,int b){
        int dr=((a>>16)&255)-((b>>16)&255);
        int dg=((a>>8)&255)-((b>>8)&255);
        int db=(a&255)-(b&255);
        return dr*dr+dg*dg+db*db;
    }

}
