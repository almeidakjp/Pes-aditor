package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.View;
import android.content.ContentResolver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NomesNovosActivity extends BaseActivity {
    private String ultimoDiagnosticoBatch = "";


    private static final int PICK_INPUT_FOLDER = 601;
    private static final int PICK_PNG_FILES = 605;
    private static final int PICK_INDIVIDUAL_OUTPUT = 606;
    private static final int PICK_TXT = 602;
    private static final int PICK_OUTPUT_FOLDER = 603;
    private static final int PICK_SINGLE_OUTPUT = 604;

    private static final int ESCALA_SAIDA = 4;

    private Uri inputFolderUri;
    private Uri txtUri;
    private Uri outputFolderUri;

    private EditText edtPorcentagem;
    private EditText edtBorda;
    private TextView txtStatus;
    private TextView txtPngCount, txtTxtCount, txtSelected;
    private LinearLayout fileList;
    private ImageView preview;
    private EditText edtNomeIndividual;
    private final ArrayList<Uri> previewUris = new ArrayList<>();
    private Uri selectedPreviewUri;
    private Bitmap previewResultBitmap;
    private Bitmap selectedOriginalBitmap;
    private Uri individualOutputFolderUri;
    private static final int BATCH_SCALE = ESCALA_SAIDA;
    private int ultimoBatchFontePx = -1;
    private int ultimoBatchBorda = -1;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(14));
        root.setBackgroundColor(0xff101112);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("NOMES NOVOS");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(62), 1));

        Button bPng=smallButton("PNG"), bTxt=smallButton("TXT"), bBatch=smallButton("BATCH");
        top.addView(bPng, new LinearLayout.LayoutParams(dp(68),dp(56)));
        top.addView(bTxt, marginLeft(dp(68),dp(56),dp(6)));
        top.addView(bBatch, marginLeft(dp(76),dp(56),dp(6)));
        root.addView(top);

        LinearLayout summary=new LinearLayout(this);
        summary.setPadding(dp(14),dp(12),dp(14),dp(12));
        summary.setBackground(round(0xff191b1d,16,0xff34373b));
        txtPngCount=summaryCell(summary,"PNGs:","0");
        txtTxtCount=summaryCell(summary,"NOMES:","0");
        txtSelected=summaryCell(summary,"SELECIONADO:","-");
        root.addView(summary,new LinearLayout.LayoutParams(-1,dp(92)));

        LinearLayout heads=new LinearLayout(this);
        heads.setPadding(0,dp(18),0,dp(8));
        TextView h1=header("ARQUIVO PNG"), h2=header("PRÉVIA");
        heads.addView(h1,new LinearLayout.LayoutParams(0,-2,1));
        heads.addView(h2,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(heads);

        LinearLayout body=new LinearLayout(this);
        fileList=new LinearLayout(this); fileList.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll=new ScrollView(this); listScroll.addView(fileList);
        body.addView(listScroll,new LinearLayout.LayoutParams(0,dp(300),1));

        LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(8),0,0,0);
        preview=new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        right.addView(preview,new LinearLayout.LayoutParams(-1,dp(200)));

        edtNomeIndividual=new EditText(this); edtNomeIndividual.setHint("Nome corrigido");
        styleEdit(edtNomeIndividual);
        right.addView(edtNomeIndividual,new LinearLayout.LayoutParams(-1,dp(46)));

        Button atualizar=smallButton("ATUALIZAR");
        right.addView(atualizar,new LinearLayout.LayoutParams(-1,dp(44)));
        Button salvarIndividual=smallButton("SALVAR");
        right.addView(salvarIndividual,new LinearLayout.LayoutParams(-1,dp(44)));
        body.addView(right,new LinearLayout.LayoutParams(0,dp(350),1));
        root.addView(body);

        LinearLayout opts=new LinearLayout(this);
        TextView t1=new TextView(this); t1.setText("Tamanho %"); t1.setTextColor(0xffb7b8bb);
        TextView t2=new TextView(this); t2.setText("Borda px"); t2.setTextColor(0xffb7b8bb);
        LinearLayout c1=new LinearLayout(this);c1.setOrientation(LinearLayout.VERTICAL);c1.addView(t1);
        edtPorcentagem=new EditText(this);edtPorcentagem.setText("100");styleEdit(edtPorcentagem);c1.addView(edtPorcentagem);
        LinearLayout c2=new LinearLayout(this);c2.setOrientation(LinearLayout.VERTICAL);c2.setPadding(dp(8),0,0,0);c2.addView(t2);
        edtBorda=new EditText(this);edtBorda.setText("4");styleEdit(edtBorda);c2.addView(edtBorda);
        opts.addView(c1,new LinearLayout.LayoutParams(0,dp(82),1));opts.addView(c2,new LinearLayout.LayoutParams(0,dp(82),1));
        root.addView(opts);

        txtStatus=new TextView(this);txtStatus.setTextColor(0xffb7b8bb);txtStatus.setTextSize(13);
        root.addView(txtStatus,new LinearLayout.LayoutParams(-1,-2));

        bPng.setOnClickListener(v->selecionarPngs());
        bTxt.setOnClickListener(v->selecionarTxt());
        bBatch.setOnClickListener(v -> iniciarProcessamentoSelecionados());
        atualizar.setOnClickListener(v->atualizarPreviewIndividual());
        salvarIndividual.setOnClickListener(v->salvarIndividual());

        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    private void selecionarPngs() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent, PICK_PNG_FILES);
    }

    private void selecionarPasta() {
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                PICK_INPUT_FOLDER
        );
    }

    private void selecionarTxt() {
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                PICK_TXT
        );
    }

    private void selecionarSaida() {
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(
                intent,
                PICK_OUTPUT_FOLDER
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == PICK_INDIVIDUAL_OUTPUT) {
            Uri folder=data.getData();
            if(folder!=null){
                individualOutputFolderUri=folder;
                try {
                    getContentResolver().takePersistableUriPermission(
                            folder,
                            data.getFlags() &
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                             Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    );
                } catch(Exception ignored) {}
                salvarIndividualAgora();
            }
            return;
        }

        if (requestCode == PICK_PNG_FILES) {
            previewUris.clear();

            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i=0; i<clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u != null) {
                        previewUris.add(u);
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    u,
                                    data.getFlags() &
                                    (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            );
                        } catch(Exception ignored) {}
                    }
                }
            } else if (data.getData() != null) {
                Uri u=data.getData();
                previewUris.add(u);
                try {
                    getContentResolver().takePersistableUriPermission(
                            u,
                            data.getFlags() &
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                             Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    );
                } catch(Exception ignored) {}
            }

            Collections.sort(previewUris, new Comparator<Uri>() {
                @Override public int compare(Uri a, Uri b) {
                    return obterNome(a).compareToIgnoreCase(obterNome(b));
                }
            });
            carregarListaPngSelecionados();
            return;
        }

        Uri uri = data.getData();

        if (uri == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    data.getFlags() &
                    (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
            );
        } catch (Exception ignored) {
        }

        if (requestCode == PICK_INPUT_FOLDER) {

            inputFolderUri = uri;

            txtStatus.setText(
                    "Pasta do BATCH selecionada. Toque em BATCH para continuar."
            );

        } else if (requestCode == PICK_TXT) {

            txtUri = uri;

            txtStatus.setText(
                    "📄 TXT selecionado."
            );
            try { txtTxtCount.setText(String.valueOf(lerNomesTxt().size())); } catch(Exception ignored) {}

        } else if (requestCode == PICK_OUTPUT_FOLDER) {

            outputFolderUri = uri;

            txtStatus.setText(
                    "Pasta de saída selecionada."
            );

            iniciarProcessamentoSelecionados();
        }
    }

    private void iniciarProcessamentoSelecionados() {
        if(previewUris.isEmpty()){
            Toast.makeText(this,"Selecione os PNGs no botão PNG.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(txtUri==null){
            Toast.makeText(this,"Selecione o TXT com os nomes.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(outputFolderUri==null){
            selecionarSaida();
            return;
        }

        float porcentagem;
        int borda;
        try{
            porcentagem=Float.parseFloat(edtPorcentagem.getText().toString().trim().replace(",","."));
            borda=Integer.parseInt(edtBorda.getText().toString().trim());
        }catch(Exception e){
            Toast.makeText(this,"Valor inválido.",Toast.LENGTH_SHORT).show();
            return;
        }
        if(porcentagem<50 || porcentagem>150){
            Toast.makeText(this,"Use tamanho entre 50 e 150%.",Toast.LENGTH_SHORT).show(); return;
        }
        if(borda<0 || borda>10){
            Toast.makeText(this,"Use borda entre 0 e 10 px.",Toast.LENGTH_SHORT).show(); return;
        }

        final float escalaTexto=porcentagem;
        final int espessuraBorda=borda;
        final ArrayList<Uri> imagens=new ArrayList<>(previewUris);
        txtStatus.setText("Preparando BATCH...");

        new Thread(() -> {
            try{
                List<String> nomes=lerNomesTxt();
                if(nomes.size()!=imagens.size()){
                    throw new Exception("Quantidade diferente:\n\nPNG selecionados: "+imagens.size()+
                            "\nNomes no TXT: "+nomes.size()+
                            "\n\nÉ necessário ter a mesma quantidade.");
                }

                Typeface fonte=Typeface.create("sans-serif",Typeface.NORMAL);
                Bitmap primeira=carregarBitmap(imagens.get(0));
                if(primeira==null) throw new Exception("Não foi possível abrir a primeira imagem.");
                int alturaAlfaBatch=obterAlturaAlfa(primeira);
                int larguraBatch=primeira.getWidth();
                int alturaBatch=primeira.getHeight();
                int fontePx=criarTamanhoFonte(primeira,escalaTexto);
                primeira.recycle();
                final String diagnosticoBatch=
                    "BATCH | ref="+larguraBatch+"x"+alturaBatch+
                    " | alfaH="+alturaAlfaBatch+
                    " | pct="+escalaTexto+
                    " | fontePx="+fontePx+
                    " | borda="+espessuraBorda;
                ultimoDiagnosticoBatch=diagnosticoBatch;
                

                for(int i=0;i<imagens.size();i++){
                    Uri imagemUri=imagens.get(i);
                    String nome=nomes.get(i);
                    Bitmap original=carregarBitmap(imagemUri);
                    if(original==null) throw new Exception("Não foi possível abrir "+obterNome(imagemUri));

                    Bitmap resultado=processarImagem(original,nome,fonte,fontePx,espessuraBorda);
                    String base=removerExtensao(obterNome(imagemUri));
                    salvarImagem(base+".png",resultado);
                    original.recycle(); resultado.recycle();

                    final int atual=i+1;
                    final String nomeAtual=nome;
                    handler.post(() -> txtStatus.setText(
                            "Processando BATCH...\nArquivo: "+atual+" / "+imagens.size()+"\nNome: "+nomeAtual
                    ));
                }

                handler.post(() -> txtStatus.setText(
                        "CONCLUÍDO!\nPNGs: "+imagens.size()+"\nNomes: "+nomes.size()+
                        "\nTamanho: "+escalaTexto+"%\nBorda: "+espessuraBorda+" px"
                ));
            }catch(Exception e){
                final String msg=e.getMessage();
                handler.post(() -> txtStatus.setText("ERRO:\n\n"+msg));
            }
        }).start();
    }

    private void iniciarProcessamento() {

        if (inputFolderUri == null) {
            Toast.makeText(
                    this,
                    "Selecione a pasta dos PNGs.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (txtUri == null) {
            Toast.makeText(
                    this,
                    "Selecione o TXT com os nomes.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (outputFolderUri == null) {
            selecionarSaida();
            return;
        }

        float porcentagem;
        int borda;

        try {
            porcentagem = Float.parseFloat(
                    edtPorcentagem.getText()
                            .toString()
                            .trim()
                            .replace(",", ".")
            );

            borda = Integer.parseInt(
                    edtBorda.getText()
                            .toString()
                            .trim()
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Valor inválido.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (porcentagem < 50 || porcentagem > 150) {

            Toast.makeText(
                    this,
                    "Use tamanho entre 50 e 150%.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (borda < 0 || borda > 10) {

            Toast.makeText(
                    this,
                    "Use borda entre 0 e 10 px.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        final float escalaTexto = porcentagem;
        final int espessuraBorda = borda;

        txtStatus.setText(
                "⏳ Preparando..."
        );

        new Thread(() -> {

            try {

                List<String> nomes =
                        lerNomesTxt();

                List<Uri> imagens =
                        listarImagens(
                                inputFolderUri
                        );

                if (nomes.isEmpty()) {
                    throw new Exception(
                            "O TXT não possui nomes."
                    );
                }

                if (imagens.isEmpty()) {
                    throw new Exception(
                            "A pasta não possui imagens."
                    );
                }

                if (nomes.size() != imagens.size()) {

                    throw new Exception(
                            "Quantidade diferente:\n\n" +
                            "PNG encontrados: " +
                            imagens.size() +
                            "\n" +
                            "Nomes no TXT: " +
                            nomes.size() +
                            "\n\n" +
                            "É necessário ter a mesma " +
                            "quantidade."
                    );
                }

                Typeface fonte =
                        Typeface.create(
                                "sans-serif",
                                Typeface.NORMAL
                        );

                Bitmap primeira =
                        carregarBitmap(imagens.get(0));

                if (primeira == null) {
                    throw new Exception(
                            "Não foi possível abrir a primeira imagem."
                    );
                }

                int fontePx =
                        criarTamanhoFonte(
                                primeira,
                                escalaTexto
                        );

                primeira.recycle();

                final int fonteBatchMemoria = fontePx;
                final int bordaBatchMemoria = espessuraBorda;
                handler.post(() -> {
                    ultimoBatchFontePx = fonteBatchMemoria;
                    ultimoBatchBorda = bordaBatchMemoria;
                });

                int processados = 0;

                for (int i = 0;
                     i < imagens.size();
                     i++) {

                    final int indice = i + 1;

                    Uri imagemUri =
                            imagens.get(i);

                    String nome =
                            nomes.get(i);

                    Bitmap original =
                            carregarBitmap(imagemUri);

                    if (original == null) {
                        throw new Exception(
                                "Não foi possível abrir a imagem " +
                                obterNome(imagemUri)
                        );
                    }

                    Bitmap resultado =
                            processarImagem(
                                    original,
                                    nome,
                                    fonte,
                                    fontePx,
                                    espessuraBorda
                            );

                    String nomeArquivo =
                            obterNome(imagemUri);

                    String base =
                            removerExtensao(
                                    nomeArquivo
                            );

                    salvarImagem(
                            base + ".png",
                            resultado
                    );

                    original.recycle();
                    resultado.recycle();

                    processados++;

                    final int atual =
                            processados;

                    handler.post(() ->
                            txtStatus.setText(
                                    "⏳ Processando...\n\n" +
                                    "Arquivo: " +
                                    indice +
                                    " / " +
                                    imagens.size() +
                                    "\n\n" +
                                    "Nome: " +
                                    nome
                            )
                    );
                }

                final int total =
                        processados;

                handler.post(() ->
                        txtStatus.setText(
                                "====================================\n" +
                                "✅ CONCLUÍDO!\n" +
                                "====================================\n\n" +
                                "PNGs encontrados: " +
                                imagens.size() +
                                "\n" +
                                "Nomes no TXT: " +
                                nomes.size() +
                                "\n" +
                                "Processados: " +
                                total +
                                "\n\n" +
                                "Tamanho: " +
                                escalaTexto +
                                "%\n" +
                                "Borda: " +
                                espessuraBorda +
                                " px"
                        )
                );

            } catch (Exception e) {

                String erro =
                        e.getMessage() == null
                                ? e.toString()
                                : e.getMessage();

                handler.post(() ->
                        txtStatus.setText(
                                "❌ ERRO:\n\n" +
                                erro
                        )
                );
            }

        }).start();
    }

    private List<String> lerNomesTxt()
            throws Exception {

        List<String> nomes =
                new ArrayList<>();

        InputStream in =
                getContentResolver()
                        .openInputStream(txtUri);

        if (in == null) {
            throw new Exception(
                    "Não foi possível abrir o TXT."
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                in,
                                StandardCharsets.UTF_8
                        )
                );

        String linha;

        while ((linha = reader.readLine()) != null) {

            if (linha.startsWith("\uFEFF")) {
                linha =
                        linha.substring(1);
            }

            linha = linha.trim();

            if (!linha.isEmpty()) {
                nomes.add(linha);
            }
        }

        reader.close();

        return nomes;
    }

    private List<Uri> listarImagens(
            Uri treeUri
    ) {

        List<Uri> resultado =
                new ArrayList<>();

        listarImagensRecursivo(
                treeUri,
                resultado
        );

        Collections.sort(
                resultado,
                new Comparator<Uri>() {
                    @Override
                    public int compare(
                            Uri a,
                            Uri b
                    ) {
                        return obterNome(a)
                                .compareToIgnoreCase(
                                        obterNome(b)
                                );
                    }
                }
        );

        return resultado;
    }

    private void listarImagensRecursivo(
            Uri treeUri,
            List<Uri> resultado
    ) {

        String documentId =
                DocumentsContract
                        .getTreeDocumentId(
                                treeUri
                        );

        Uri childrenUri =
                DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                                treeUri,
                                documentId
                        );

        android.database.Cursor cursor =
                getContentResolver().query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                        },
                        null,
                        null,
                        null
                );

        if (cursor == null) {
            return;
        }

        try {

            while (cursor.moveToNext()) {

                String id =
                        cursor.getString(0);

                String nome =
                        cursor.getString(1);

                String mime =
                        cursor.getString(2);

                Uri childUri =
                        DocumentsContract
                                .buildDocumentUriUsingTree(
                                        treeUri,
                                        id
                                );

                if (DocumentsContract
                        .Document
                        .MIME_TYPE_DIR
                        .equals(mime)) {

                    listarImagensRecursivo(
                            childUri,
                            resultado
                    );

                } else if (
                        nome != null &&
                        (
                            nome.toLowerCase()
                                    .endsWith(".png") ||
                            nome.toLowerCase()
                                    .endsWith(".jpg") ||
                            nome.toLowerCase()
                                    .endsWith(".jpeg") ||
                            nome.toLowerCase()
                                    .endsWith(".webp")
                        )
                ) {

                    resultado.add(
                            childUri
                    );
                }
            }

        } finally {
            cursor.close();
        }
    }

    private Bitmap carregarBitmap(
            Uri uri
    ) throws Exception {

        InputStream in =
                getContentResolver()
                        .openInputStream(uri);

        if (in == null) {
            return null;
        }

        Bitmap bitmap =
                BitmapFactory.decodeStream(in);

        in.close();

        return bitmap;
    }

    private int criarTamanhoFonte(
            Bitmap imagem,
            float porcentagem
    ) {

        int largura =
                imagem.getWidth();

        int altura =
                imagem.getHeight();

        int[] pixels =
                new int[
                        largura * altura
                ];

        imagem.getPixels(
                pixels,
                0,
                largura,
                0,
                0,
                largura,
                altura
        );

        int topo = altura;
        int baixo = 0;

        boolean encontrou = false;

        for (int y = 0;
             y < altura;
             y++) {

            for (int x = 0;
                 x < largura;
                 x++) {

                int alpha =
                        Color.alpha(
                                pixels[
                                    y * largura + x
                                ]
                        );

                if (alpha > 0) {

                    encontrou = true;

                    if (y < topo) {
                        topo = y;
                    }

                    if (y + 1 > baixo) {
                        baixo = y + 1;
                    }
                }
            }
        }

        if (!encontrou) {
            return 16;
        }

        int alturaNome =
                Math.max(
                        1,
                        baixo - topo
                );

        return Math.max(
                1,
                Math.round(
                        alturaNome *
                        ESCALA_SAIDA *
                        0.78f *
                        porcentagem /
                        100f
                )
        );
    }

    private Bitmap processarImagem(
            Bitmap original,
            String novoNome,
            Typeface typeface,
            int fontePx,
            int strokePx
    ) {

        int largura =
                original.getWidth() *
                ESCALA_SAIDA;

        int altura =
                original.getHeight() *
                ESCALA_SAIDA;

        int ow =
                original.getWidth();

        int oh =
                original.getHeight();

        int[] pixels =
                new int[ow * oh];

        original.getPixels(
                pixels,
                0,
                ow,
                0,
                0,
                ow,
                oh
        );

        int esquerda = ow;
        int topo = oh;
        int direita = 0;
        int baixo = 0;

        boolean encontrou = false;

        for (int y = 0;
             y < oh;
             y++) {

            for (int x = 0;
                 x < ow;
                 x++) {

                if (Color.alpha(
                        pixels[y * ow + x]
                ) > 0) {

                    encontrou = true;

                    if (x < esquerda) {
                        esquerda = x;
                    }

                    if (y < topo) {
                        topo = y;
                    }

                    if (x + 1 > direita) {
                        direita = x + 1;
                    }

                    if (y + 1 > baixo) {
                        baixo = y + 1;
                    }
                }
            }
        }

        if (!encontrou) {

            Bitmap vazio =
                    Bitmap.createBitmap(
                            largura,
                            altura,
                            Bitmap.Config.ARGB_8888
                    );

            vazio.eraseColor(
                    Color.TRANSPARENT
            );

            return vazio;
        }

        float areaX =
                esquerda *
                ESCALA_SAIDA;

        float areaY =
                topo *
                ESCALA_SAIDA;

        float areaW =
                Math.max(
                        1,
                        (direita - esquerda) *
                        ESCALA_SAIDA
                );

        float areaH =
                Math.max(
                        1,
                        (baixo - topo) *
                        ESCALA_SAIDA
                );

        Bitmap saida =
                Bitmap.createBitmap(
                        largura,
                        altura,
                        Bitmap.Config.ARGB_8888
                );

        saida.eraseColor(
                Color.TRANSPARENT
        );

        Canvas canvas =
                new Canvas(saida);

        Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG |
                        Paint.SUBPIXEL_TEXT_FLAG
                );

        paint.setTypeface(
                typeface
        );

        paint.setTextSize(
                fontePx
        );

        paint.setColor(
                Color.WHITE
        );

        paint.setStyle(
                Paint.Style.FILL
        );

        paint.setStrokeWidth(
                strokePx
        );

        Paint.FontMetrics fm =
                paint.getFontMetrics();

        float textoLargura =
                paint.measureText(
                        novoNome
                );

        float textoCentroX =
                areaX +
                areaW / 2f;

        float textoCentroY =
                areaY +
                areaH / 2f;

        float baseline =
                textoCentroY -
                (fm.ascent + fm.descent)
                / 2f;

        float x =
                textoCentroX -
                textoLargura / 2f;

        if (strokePx > 0) {

            Paint borda =
                    new Paint(
                            paint
                    );

            borda.setStyle(
                    Paint.Style.STROKE
            );

            borda.setStrokeWidth(
                    strokePx * 2f
            );

            borda.setColor(
                    Color.BLACK
            );

            canvas.drawText(
                    novoNome,
                    x,
                    baseline,
                    borda
            );
        }

        canvas.drawText(
                novoNome,
                x,
                baseline,
                paint
        );

        return saida;
    }

    private void salvarImagem(
            String nomeArquivo,
            Bitmap bitmap
    ) throws Exception {

        String documentId =
                DocumentsContract
                        .getTreeDocumentId(
                                outputFolderUri
                        );

        Uri childrenUri =
                DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                                outputFolderUri,
                                documentId
                        );

        Uri existente = null;

        android.database.Cursor cursor =
                getContentResolver().query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        },
                        null,
                        null,
                        null
                );

        if (cursor != null) {

            try {

                while (cursor.moveToNext()) {

                    String id =
                            cursor.getString(0);

                    String nome =
                            cursor.getString(1);

                    if (nomeArquivo.equals(
                            nome
                    )) {

                        existente =
                                DocumentsContract
                                        .buildDocumentUriUsingTree(
                                                outputFolderUri,
                                                id
                                        );

                        break;
                    }
                }

            } finally {
                cursor.close();
            }
        }

        Uri destino;

        if (existente != null) {
            destino = existente;
        } else {
            Uri pastaDocumento =
                    DocumentsContract.buildDocumentUriUsingTree(
                            outputFolderUri,
                            DocumentsContract.getTreeDocumentId(
                                    outputFolderUri
                            )
                    );

            destino =
                    DocumentsContract.createDocument(
                            getContentResolver(),
                            pastaDocumento,
                            "image/png",
                            nomeArquivo
                    );
        }

        OutputStream out =
                getContentResolver()
                        .openOutputStream(
                                destino,
                                "wt"
                        );

        if (out == null) {
            throw new Exception(
                    "Não foi possível criar " +
                    nomeArquivo
            );
        }

        bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                out
        );

        out.flush();
        out.close();
    }

    private String obterNome(
            Uri uri
    ) {

        android.database.Cursor cursor =
                getContentResolver().query(
                        uri,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        },
                        null,
                        null,
                        null
                );

        if (cursor != null) {

            try {

                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }

            } finally {
                cursor.close();
            }
        }

        return "arquivo.png";
    }

    private String removerExtensao(
            String nome
    ) {

        int ponto =
                nome.lastIndexOf('.');

        if (ponto > 0) {
            return nome.substring(
                    0,
                    ponto
            );
        }

        return nome;
    }

    private void carregarListaPngSelecionados() {
        txtPngCount.setText(String.valueOf(previewUris.size()));
        fileList.removeAllViews();
        for (Uri u : previewUris) {
            Button b=smallButton(obterNome(u));
            b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v->selecionarPreview(u));
            fileList.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
        }
        txtStatus.setText(previewUris.size()+" PNG(s) selecionado(s).");
        if(!previewUris.isEmpty()) selecionarPreview(previewUris.get(0));
    }

    private void carregarListaPreview() {
        try {
            previewUris.clear(); previewUris.addAll(listarImagens(inputFolderUri));
            txtPngCount.setText(String.valueOf(previewUris.size()));
            fileList.removeAllViews();
            for (Uri u: previewUris) {
                Button b=smallButton(obterNome(u));
                b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                b.setOnClickListener(v->selecionarPreview(u));
                fileList.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));
            }
        } catch(Exception e) { txtStatus.setText("Erro: "+e.getMessage()); }
    }

    private void selecionarPreview(Uri u) {
        selectedPreviewUri=u;
        txtSelected.setText(obterNome(u));
        try {
            if(selectedOriginalBitmap!=null && !selectedOriginalBitmap.isRecycled()) {
                selectedOriginalBitmap.recycle();
            }
            Bitmap carregado=carregarBitmap(u);
            if(carregado==null) throw new Exception("Não foi possível abrir o PNG.");
            selectedOriginalBitmap=carregado.copy(Bitmap.Config.ARGB_8888,false);
            preview.setImageBitmap(selectedOriginalBitmap);

            int idx=previewUris.indexOf(u);
            if(txtUri!=null) {
                List<String> nomes=lerNomesTxt();
                if(idx>=0 && idx<nomes.size()) edtNomeIndividual.setText(nomes.get(idx));
            } else {
                edtNomeIndividual.setText("");
            }
        } catch(Exception e) {
            txtStatus.setText("Erro: "+e.getMessage());
        }
    }

    private void salvarIndividual() {
        if(previewResultBitmap==null || previewResultBitmap.isRecycled() || selectedPreviewUri==null){
            Toast.makeText(this,"Toque em ATUALIZAR primeiro.",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent,PICK_INDIVIDUAL_OUTPUT);
    }

    private void salvarIndividualAgora() {
        try{
            if(individualOutputFolderUri==null || previewResultBitmap==null)
                throw new Exception("Pasta de saída não selecionada.");

            String nomeArquivo=obterNome(selectedPreviewUri);
            String treeId=android.provider.DocumentsContract.getTreeDocumentId(individualOutputFolderUri);
            Uri parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    individualOutputFolderUri, treeId
            );

            Uri novo=android.provider.DocumentsContract.createDocument(
                    getContentResolver(),
                    parent,
                    "image/png",
                    nomeArquivo
            );
            if(novo==null) throw new Exception("Não foi possível criar o PNG.");

            java.io.OutputStream out=getContentResolver().openOutputStream(novo,"w");
            if(out==null) throw new Exception("Não foi possível abrir o PNG para salvar.");
            boolean ok=previewResultBitmap.compress(Bitmap.CompressFormat.PNG,100,out);
            out.flush(); out.close();
            if(!ok) throw new Exception("Falha ao salvar PNG.");

            txtStatus.setText("Salvo: "+nomeArquivo);

        }catch(Exception e){
            txtStatus.setText("Erro ao salvar: "+e.getMessage());
        }
    }

    private int obterAlturaAlfa(Bitmap imagem) {
        int w=imagem.getWidth(), h=imagem.getHeight();
        int[] px=new int[w*h];
        imagem.getPixels(px,0,w,0,0,w,h);
        int topo=h, baixo=0;
        boolean achou=false;
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                if(Color.alpha(px[y*w+x])>0){
                    achou=true;
                    if(y<topo) topo=y;
                    if(y+1>baixo) baixo=y+1;
                }
            }
        }
        return achou ? Math.max(1,baixo-topo) : 0;
    }

    private void atualizarPreviewIndividual() {
        if(selectedPreviewUri==null){
            Toast.makeText(this,"Selecione um PNG.",Toast.LENGTH_SHORT).show();
            return;
        }
        String nome=edtNomeIndividual.getText().toString().trim();
        if(nome.isEmpty()){
            Toast.makeText(this,"Digite o novo nome.",Toast.LENGTH_SHORT).show();
            return;
        }
        try{
            float pct=Float.parseFloat(
                    edtPorcentagem.getText().toString().trim().replace(",",".")
            );
            int borda=Integer.parseInt(edtBorda.getText().toString().trim());

            if(pct<50 || pct>150) throw new Exception("Use tamanho entre 50 e 150%.");
            if(borda<0 || borda>10) throw new Exception("Use borda entre 0 e 10 px.");

            // IGUAL AO BATCH:
            // o BATCH calcula a fonte UMA VEZ usando o primeiro PNG da lista
            // e depois usa essa mesma fonte para todos os PNGs.
            if(previewUris.isEmpty()) throw new Exception("Selecione os PNGs.");

            Typeface fonte=Typeface.create("sans-serif",Typeface.NORMAL);

            Bitmap primeira=carregarBitmap(previewUris.get(0));
            if(primeira==null) throw new Exception("Não foi possível abrir a primeira imagem.");
            int alturaAlfaRef=obterAlturaAlfa(primeira);
            int larguraRef=primeira.getWidth();
            int alturaRef=primeira.getHeight();

            // Diagnóstico confirmou o padrão real do BATCH:
            // ref 128x16, altura alfa 10 -> fontePx 19 em 60%.
            // O Individual deve usar a MESMA referência de tamanho do BATCH,
            // independentemente da altura alfa do nome selecionado.
            final int ALTURA_ALFA_REFERENCIA_BATCH = 10;
            int fontePx=Math.max(1,Math.round(
                    ALTURA_ALFA_REFERENCIA_BATCH * ESCALA_SAIDA * 0.78f * pct / 100f
            ));
            primeira.recycle();

            // Processa o PNG selecionado com EXATAMENTE a fonte calculada
            // da mesma maneira que o BATCH.
            Bitmap origem=carregarBitmap(selectedPreviewUri);
            if(origem==null) throw new Exception("Não foi possível abrir o PNG.");

            if(previewResultBitmap!=null && !previewResultBitmap.isRecycled())
                previewResultBitmap.recycle();

            previewResultBitmap=processarImagem(origem,nome,fonte,fontePx,borda);
            origem.recycle();

            preview.setImageBitmap(previewResultBitmap);
            txtStatus.setText("Prévia atualizada. Toque em SALVAR.");
        }catch(Exception e){
            txtStatus.setText("Erro: "+e.getMessage());
        }
    }

    private Bitmap processarImagemIndividual(
            Bitmap batchResult, String novoNome, Typeface typeface,
            float porcentagem, int strokePx) {

        int bw=batchResult.getWidth();
        int bh=batchResult.getHeight();

        // NOMES NOVOS/BATCH outputs at ESCALA_SAIDA (4x).
        // Build a transparent logical source at the exact pre-BATCH dimensions.
        int ow=Math.max(1, Math.round(bw/(float)BATCH_SCALE));
        int oh=Math.max(1, Math.round(bh/(float)BATCH_SCALE));

        // Find the occupied area in the BATCH result.
        int[] px=new int[bw*bh];
        batchResult.getPixels(px,0,bw,0,0,bw,bh);
        int l=bw,t=bh,r=0,bt=0; boolean found=false;
        for(int y=0;y<bh;y++){
            for(int x=0;x<bw;x++){
                if(Color.alpha(px[y*bw+x])>0){
                    found=true;
                    if(x<l)l=x; if(x+1>r)r=x+1;
                    if(y<t)t=y; if(y+1>bt)bt=y+1;
                }
            }
        }

        Bitmap logical=Bitmap.createBitmap(ow,oh,Bitmap.Config.ARGB_8888);
        logical.eraseColor(Color.TRANSPARENT);

        if(found){
            Canvas lc=new Canvas(logical);
            Paint marker=new Paint();
            marker.setColor(Color.WHITE);
            marker.setStyle(Paint.Style.FILL);

            float left=l/(float)BATCH_SCALE;
            float top=t/(float)BATCH_SCALE;
            float right=r/(float)BATCH_SCALE;
            float bottom=bt/(float)BATCH_SCALE;
            lc.drawRect(left,top,right,bottom,marker);
        }

        // Use the SAME font-size function and SAME renderer as BATCH.
        int fontePx=criarTamanhoFonte(logical,porcentagem);
        Bitmap result=processarImagem(
                logical,
                novoNome,
                typeface,
                fontePx,
                strokePx
        );
        logical.recycle();

        // processarImagem returns exactly logical dimensions * ESCALA_SAIDA,
        // i.e. the same dimensions as the selected BATCH result.
        if(result.getWidth()!=bw || result.getHeight()!=bh){
            Bitmap exact=Bitmap.createScaledBitmap(result,bw,bh,true);
            if(exact!=result) result.recycle();
            result=exact;
        }
        return result;
    }

    private Button smallButton(String text) {
        Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setBackground(round(0xff242629,14,0xff3a3d42));return b;
    }
    private TextView header(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(13);t.setTextColor(0xffa8aaad);t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView summaryCell(LinearLayout parent,String label,String value){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        TextView l=header(label);TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(16);
        c.addView(l);c.addView(v);parent.addView(c,new LinearLayout.LayoutParams(0,-1,1));return v;
    }
    private LinearLayout.LayoutParams marginLeft(int w,int h,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.leftMargin=left;return p;}
    private void styleButton(Button b) {
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(round(0xff242629, 14, 0xff3a3d42));
        b.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        p.topMargin = dp(8);
        p.bottomMargin = dp(8);
        b.setLayoutParams(p);
    }

    private void styleEdit(EditText e) {
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(0xff191b1d, 12, 0xff3a3d42));
        e.setMinHeight(dp(54));
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

}
