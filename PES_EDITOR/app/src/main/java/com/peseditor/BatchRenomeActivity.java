package com.peseditor;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.View;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BatchRenomeActivity extends BaseActivity {

    private static final int REQUEST_ARQUIVOS = 801;
    private static final int REQUEST_LISTA = 802;
    private static final int REQUEST_RESULTADO = 803;
    private static final int REQUEST_ARQUIVOS_INDIVIDUAIS = 804;
    private static final int REQUEST_SALVAR_LISTA = 805;
    private String listaGeradaPendente = null;

    private Uri pastaArquivosUri;
    private Uri listaUri;
    private Uri pastaResultadoUri;
    private final List<Uri> arquivosSelecionados = new ArrayList<>();
    private final java.util.Map<String, String> nomesCache = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean modoPersonalizado = false;
    private String nomePersonalizado = "ID", extensaoPersonalizada = ".bin";
    private int numeroInicial = 0, digitos = 5, modoExtensao = 0;
    private TextView txtStatus;
    private TextView txtPreview;

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private android.graphics.drawable.GradientDrawable fundo(int cor, int raioDp, int strokeCor) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raioDp));
        if (strokeCor != 0) g.setStroke(dp(1), strokeCor);
        return g;
    }

    private TextView texto(String valor, float sp, int cor, boolean bold) {
        TextView t = new TextView(this);
        t.setText(valor);
        t.setTextSize(sp);
        t.setTextColor(cor);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }

    private Button botao(String valor) {
        Button b = new Button(this);
        b.setText(valor);
        b.setTextSize(15);
        b.setTextColor(0xfff1f3f4);
        b.setAllCaps(false);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(fundo(0xff242629, 14, 0xff3a3d40));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        lp.setMargins(0,0,0,dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private Button botaoTopo(String valor) {
        Button b = new Button(this);
        b.setText(valor);
        b.setTextSize(12);
        b.setTextColor(0xfff1f3f4);
        b.setAllCaps(false);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(dp(4),0,dp(4),0);
        b.setBackground(fundo(0xff242629, 14, 0xff3a3d40));
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        lp.setMargins(dp(3),0,dp(3),0);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff101112);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(28));
        scroll.addView(root);

        TextView titulo = texto("BATCH RENAME", 25, 0xffffffff, true);
        LinearLayout cabecalho = new LinearLayout(this);
        cabecalho.setOrientation(LinearLayout.HORIZONTAL);
        cabecalho.setGravity(android.view.Gravity.CENTER_VERTICAL);
        cabecalho.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView engrenagem = texto("⚙", 23, 0xffeeeeee, false);
        engrenagem.setGravity(android.view.Gravity.CENTER);
        engrenagem.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(38), dp(40));
        gearLp.setMargins(0, 0, 0, 0);
        engrenagem.setLayoutParams(gearLp);
        engrenagem.setOnClickListener(v -> abrirConfiguracoes());
        cabecalho.addView(engrenagem);
        root.addView(cabecalho);

        TextView subtitulo = texto("Renomeação em massa com prévia.", 14, 0xff9aa0a6, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0,dp(5),0,dp(18));
        subtitulo.setLayoutParams(subLp);
        root.addView(subtitulo);

        LinearLayout botoes = new LinearLayout(this);
        botoes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams botoesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        botoesLp.setMargins(-dp(3),0,-dp(3),dp(18));
        botoes.setLayoutParams(botoesLp);

        Button btnArquivos = botaoTopo("ARQUIVOS");
        Button btnLista = botaoTopo("TXT");
        Button btnGerarLista = botaoTopo("GERAR LISTA");
        Button btnProcessar = botaoTopo("BATCH");
        botoes.addView(btnArquivos);
        botoes.addView(btnLista);
        botoes.addView(btnGerarLista);
        botoes.addView(btnProcessar);
        root.addView(botoes);

        TextView prevTitulo = texto("PRÉVIA", 12, 0xff8f9499, true);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ptLp.setMargins(dp(3),0,0,dp(9));
        prevTitulo.setLayoutParams(ptLp);
        root.addView(prevTitulo);

        txtPreview = texto(
                "Selecione ARQUIVOS e TXT para visualizar as alterações.",
                14, 0xffc7c9cc, false);
        txtPreview.setPadding(dp(16),dp(15),dp(12),dp(15));
        txtPreview.setBackground(fundo(0xff191b1d, 16, 0xff34373a));
        txtPreview.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        txtPreview.setVerticalScrollBarEnabled(false);
        txtPreview.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        final LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams previewRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        previewRowLp.setMargins(0,0,0,dp(18));
        previewRow.setLayoutParams(previewRowLp);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        txtPreview.setLayoutParams(textLp);
        previewRow.addView(txtPreview);

        final PreviewScrollBar previewBar = new PreviewScrollBar(this, txtPreview);
        previewBar.setLayoutParams(new LinearLayout.LayoutParams(
                dp(18), LinearLayout.LayoutParams.MATCH_PARENT));
        previewRow.addView(previewBar);

        txtPreview.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                previewBar.invalidate());

        root.addView(previewRow);

        TextView statusTitulo = texto("STATUS", 12, 0xff8f9499, true);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.setMargins(dp(3),0,0,dp(9));
        statusTitulo.setLayoutParams(stLp);
        root.addView(statusTitulo);

        txtStatus = texto("Nenhuma seleção feita.", 14, 0xffc7c9cc, false);
        txtStatus.setPadding(dp(16),dp(15),dp(16),dp(15));
        txtStatus.setBackground(fundo(0xff191b1d, 16, 0xff34373a));
        root.addView(txtStatus);

        setContentView(scroll);

        btnArquivos.setOnClickListener(v -> selecionarArquivosIndividuais());
        btnLista.setOnClickListener(v -> selecionarLista());
        btnGerarLista.setOnClickListener(v -> gerarListaArquivos());
        btnProcessar.setOnClickListener(v -> {
            if (pastaArquivosUri == null && arquivosSelecionados.isEmpty()) {
                Toast.makeText(this,"Selecione os arquivos ou uma pasta.",Toast.LENGTH_SHORT).show();
                return;
            }
            if (!modoPersonalizado && listaUri == null) {
                Toast.makeText(this,"Selecione o nomes.txt.",Toast.LENGTH_SHORT).show();
                return;
            }
            selecionarPasta(REQUEST_RESULTADO);
        });
    }

    private void atualizarPrevia() {
        if (txtPreview == null) return;

        if (pastaArquivosUri == null && arquivosSelecionados.isEmpty()) {
            txtPreview.setText("Selecione ARQUIVOS para visualizar a lista.");
            return;
        }

        new Thread(() -> {
            try {
                List<Uri> arquivos = obterArquivosSelecionados();
                StringBuilder sb = new StringBuilder();

                // Apenas ARQUIVOS selecionados: mostra um arquivo por linha.
                if (modoPersonalizado) {
                    for (int i=0; i<arquivos.size(); i++) {
                        if (sb.length()>0) sb.append("\n");
                        sb.append(obterNome(arquivos.get(i))).append("  →  ").append(nomeSequencial(i, obterNome(arquivos.get(i))));
                    }
                } else if (listaUri == null) {
                    for (Uri arquivo : arquivos) {
                        String nome = obterNome(arquivo);
                        if (nome == null) nome = "";
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(nome);
                    }
                } else {
                    // ARQUIVOS + TXT: arquivo atual -> novo nome, tudo na mesma linha.
                    List<String[]> regras = carregarLista();

                    for (int i=0; i<regras.size(); i++) {
                        String antigo = regras.get(i)[0];
                        String novo = regras.get(i)[1];
                        Uri origem = null;

                        if (!antigo.isEmpty()) {
                            origem = encontrarArquivo(arquivos, antigo);
                        } else if (i < arquivos.size()) {
                            origem = arquivos.get(i);
                        }

                        String origemNome;
                        if (origem != null) {
                            origemNome = obterNome(origem);
                        } else if (!antigo.isEmpty()) {
                            origemNome = antigo + " [não encontrado]";
                        } else {
                            origemNome = "posição " + (i+1) + " [não encontrado]";
                        }

                        if (sb.length() > 0) sb.append("\n");
                        sb.append(origemNome == null ? "" : origemNome);
                        sb.append("  →  ");
                        sb.append(novo);
                    }
                }

                final String resultado = sb.length() == 0
                        ? "Nenhum arquivo encontrado."
                        : sb.toString();
                runOnUiThread(() -> {
                    txtPreview.setText(resultado);
                    if (!arquivosSelecionados.isEmpty()) {
                        txtStatus.setText(arquivosSelecionados.size() + " arquivo(s) selecionado(s).");
                    }
                });
            } catch (Exception e) {
                final String erro = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> txtPreview.setText("Não foi possível gerar a prévia:\n" + erro));
            }
        }).start();
    }

    private String nomeSequencial(int i, String original) {
        String numero = String.format(java.util.Locale.US, "%0" + digitos + "d", numeroInicial + i);
        String ext = "";
        if (modoExtensao == 0) {
            int ponto = original == null ? -1 : original.lastIndexOf('.');
            if (ponto > 0) ext = original.substring(ponto);
        } else if (modoExtensao == 2) {
            String e = extensaoPersonalizada.trim();
            ext = e.isEmpty() ? "" : (e.startsWith(".") ? e : "." + e);
        }
        return nomePersonalizado + numero + ext;
    }

    private void abrirConfiguracoes() {
        final int white = 0xfff3f4f6, muted = 0xffa6a9b0, surface = 0xff101113;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(18), dp(20), dp(8));
        TextView title = texto("Renomeação personalizada", 19, white, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1,-2);
        titleLp.bottomMargin = dp(10);
        layout.addView(title,titleLp);
        android.widget.CheckBox ativo = new android.widget.CheckBox(this);
        ativo.setText("Ativar renomeação personalizada");
        ativo.setTextColor(white); ativo.setTextSize(14);
        ativo.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff8dbdf4));
        ativo.setChecked(modoPersonalizado);
        layout.addView(ativo);
        TextView nameLabel = texto("Nome / prefixo", 12, muted, false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1,-2);
        labelLp.topMargin = dp(10);
        layout.addView(nameLabel,labelLp);
        android.widget.EditText nome = new android.widget.EditText(this);
        nome.setSingleLine(true); nome.setText(nomePersonalizado); nome.setTextColor(white);
        nome.setTextSize(16); layout.addView(nome);
        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(-1,-2);
        numLp.topMargin=dp(10);
        layout.addView(numbers,numLp);
        LinearLayout startCol = new LinearLayout(this); startCol.setOrientation(1);
        LinearLayout digitsCol = new LinearLayout(this); digitsCol.setOrientation(1);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0,-2,1f);
        startParams.rightMargin=dp(12);
        numbers.addView(startCol,startParams);
        numbers.addView(digitsCol,new LinearLayout.LayoutParams(0,-2,1f));
        startCol.addView(texto("Número inicial",12,muted,false));
        digitsCol.addView(texto("Dígitos",12,muted,false));
        android.widget.EditText inicio = new android.widget.EditText(this);
        inicio.setSingleLine(true); inicio.setInputType(2); inicio.setText(String.valueOf(numeroInicial));
        inicio.setTextColor(white); inicio.setTextSize(16); startCol.addView(inicio);
        android.widget.EditText zeros = new android.widget.EditText(this);
        zeros.setSingleLine(true); zeros.setInputType(2); zeros.setText(String.valueOf(digitos));
        zeros.setTextColor(white); zeros.setTextSize(16); digitsCol.addView(zeros);
        TextView extLabel = texto("Extensão",12,muted,false);
        LinearLayout.LayoutParams extLabelLp = new LinearLayout.LayoutParams(-1,-2);
        extLabelLp.topMargin=dp(12);
        layout.addView(extLabel,extLabelLp);
        android.widget.RadioGroup opcoes = new android.widget.RadioGroup(this);
        String[] labels = {"Original", "Sem extensão", "Personalizada"};
        for(int i=0;i<3;i++) {
            android.widget.RadioButton radio = new android.widget.RadioButton(this);
            radio.setId(2000+i); radio.setText(labels[i]); radio.setTextColor(white);
            radio.setTextSize(14); radio.setPadding(0,0,0,0);
            radio.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff8dbdf4));
            opcoes.addView(radio,new android.widget.RadioGroup.LayoutParams(-1,dp(38)));
        }
        opcoes.check(2000+modoExtensao); layout.addView(opcoes);
        android.widget.EditText ext = new android.widget.EditText(this);
        ext.setSingleLine(true); ext.setHint("Ex.: .bin, .png, .at3");
        ext.setHintTextColor(muted); ext.setTextColor(white); ext.setTextSize(15);
        ext.setText(extensaoPersonalizada);
        layout.addView(ext);
        ext.setVisibility(modoExtensao==2 ? View.VISIBLE : View.GONE);
        opcoes.setOnCheckedChangeListener((g,id)->ext.setVisibility(id==2002 ? View.VISIBLE : View.GONE));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1,dp(58));
        actionsLp.topMargin=dp(10);
        layout.addView(actions,actionsLp);
        Button cancel = new Button(this), apply = new Button(this);
        for(Button button : new Button[]{cancel,apply}) {
            button.setAllCaps(false); button.setTextSize(13); button.setTextColor(white);
            button.setStateListAnimator(null);
            button.setBackground(fundo(0xff2c2e34,12,0xff44464c));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,dp(42),1f);
            bp.setMargins(dp(4),0,dp(4),0); actions.addView(button,bp);
        }
        cancel.setText("CANCELAR"); apply.setText("APLICAR");
        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.setFillViewport(false); sc.addView(layout);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(sc).create();
        dialog.setOnShowListener(d -> {
            android.view.Window window = dialog.getWindow();
            if (window != null) {
                window.setWindowAnimations(0);
                window.setDimAmount(0.45f);
            }
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        apply.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(inicio.getText().toString().trim());
                int z = Integer.parseInt(zeros.getText().toString().trim());
                if(n<0 || z<1 || z>12) throw new IllegalArgumentException();
                numeroInicial=n; digitos=z;
                nomePersonalizado=nome.getText().toString();
                extensaoPersonalizada=ext.getText().toString();
                modoExtensao=opcoes.getCheckedRadioButtonId()-2000;
                modoPersonalizado=ativo.isChecked();
                atualizarPrevia(); dialog.dismiss();
            } catch(Exception ex) {
                Toast.makeText(this,"Número ou dígitos inválidos.",Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            window.setBackgroundDrawable(fundo(surface,20,0xff35373c));
            window.setLayout((int)(getResources().getDisplayMetrics().widthPixels*0.90f), -2);
        }
    }

    private void selecionarArquivosIndividuais() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(intent, REQUEST_ARQUIVOS_INDIVIDUAIS);
    }

    private List<Uri> obterArquivosSelecionados() throws Exception {
        if (!arquivosSelecionados.isEmpty()) {
            List<Uri> arquivos = new ArrayList<>(arquivosSelecionados);
            arquivos.sort((a, b) -> {
                String nomeA = obterNome(a);
                String nomeB = obterNome(b);
                if (nomeA == null) nomeA = "";
                if (nomeB == null) nomeB = "";
                String stemA = obterStem(nomeA);
                String stemB = obterStem(nomeB);
                int cmp = stemA.compareToIgnoreCase(stemB);
                if (cmp != 0) return cmp;
                return nomeA.compareToIgnoreCase(nomeB);
            });
            return arquivos;
        }
        if (pastaArquivosUri != null) return listarArquivos(pastaArquivosUri);
        return new ArrayList<>();
    }

    private void selecionarPasta(int requestCode) {

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE
        );

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, requestCode);
    }

    private void selecionarLista() {

        Intent intent = new Intent(
                Intent.ACTION_OPEN_DOCUMENT
        );

        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, REQUEST_LISTA);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        if (requestCode == REQUEST_SALVAR_LISTA) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && listaGeradaPendente != null) {
                Uri destino = data.getData();
                try {
                    OutputStream out = getContentResolver().openOutputStream(destino);
                    if (out == null) throw new Exception("Não foi possível abrir o TXT.");
                    out.write(listaGeradaPendente.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    out.close();
                    listaGeradaPendente = null;
                    atualizarStatus("✅ LISTA GERADA!\n\n#Lista_Arquivos.txt");
                } catch (Exception e) {
                    String erro = e.getMessage() == null ? e.toString() : e.getMessage();
                    atualizarStatus("❌ ERRO AO SALVAR LISTA:\n\n" + erro);
                }
            }
            return;
        }

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        try {
            if (requestCode == REQUEST_ARQUIVOS_INDIVIDUAIS) {
                arquivosSelecionados.clear();
                nomesCache.clear();
                pastaArquivosUri = null;

                android.content.ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri itemUri = clip.getItemAt(i).getUri();
                        if (itemUri != null) {
                            try {
                                getContentResolver().takePersistableUriPermission(
                                        itemUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {}
                            arquivosSelecionados.add(itemUri);
                        }
                    }
                } else if (data.getData() != null) {
                    Uri itemUri = data.getData();
                    try {
                        getContentResolver().takePersistableUriPermission(
                                itemUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    arquivosSelecionados.add(itemUri);
                }

                txtStatus.setText(arquivosSelecionados.size() + " arquivo(s) selecionado(s) — carregando prévia...");
                atualizarPrevia();
                return;
            }

            if (data.getData() == null) return;
            Uri uri = data.getData();

            if (requestCode == REQUEST_LISTA) {

                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                listaUri = uri;

                txtStatus.setText(
                        "NOMES.TXT selecionado."
                );
                atualizarPrevia();

            } else {

                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

                if (requestCode == REQUEST_ARQUIVOS) {

                    pastaArquivosUri = uri;
                    arquivosSelecionados.clear();

                    txtStatus.setText(
                            "Pasta dos arquivos selecionada."
                    );
                    atualizarPrevia();

                } else if (requestCode == REQUEST_RESULTADO) {

                    pastaResultadoUri = uri;

                    txtStatus.setText(
                            "📁 Pasta de resultado selecionada.\\n\\n" +
                            "⏳ Processando..."
                    );

                    new Thread(() -> processar()).start();
                }
            }

        } catch (Exception e) {

            txtStatus.setText(
                    "ERRO AO SELECIONAR:\n\n" +
                    e.getMessage()
            );
        }
    }

    private void iniciarProcessamento() {

        if (pastaArquivosUri == null) {
            Toast.makeText(
                    this,
                    "Selecione a pasta dos arquivos.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (listaUri == null) {
            Toast.makeText(
                    this,
                    "Selecione o nomes.txt.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (pastaResultadoUri == null) {
            Toast.makeText(
                    this,
                    "Selecione a pasta de resultado.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        new Thread(() -> processar()).start();
    }

    private void processar() {

        try {

            List<String[]> regras = modoPersonalizado ? new ArrayList<>() : carregarLista();
            if (modoPersonalizado) {
                List<Uri> fontes = obterArquivosSelecionados();
                for (int i=0; i<fontes.size(); i++) regras.add(new String[]{"", nomeSequencial(i, obterNome(fontes.get(i)))});
            }

            if (regras.isEmpty()) {

                atualizarStatus(
                        "❌ Nenhuma regra válida encontrada."
                );

                return;
            }

            List<Uri> arquivos =
                    obterArquivosSelecionados();

            if (arquivos.isEmpty()) {

                atualizarStatus(
                        "❌ Nenhum arquivo encontrado."
                );

                return;
            }

            int sucesso = 0;
            int naoEncontrados = 0;
            int erros = 0;

            final int total = regras.size();
            final long inicio = android.os.SystemClock.elapsedRealtime();
            atualizarStatus("Processando 0 / " + total + " arquivos...");
            StringBuilder relatorio = new StringBuilder();
            /*
             * Cada regra pode ser:
             *
             * 1) antigo = novo
             *
             * ou:
             *
             * novo
             *
             * No segundo caso, o arquivo é escolhido
             * pela posição dentro da pasta.
             */

            for (int i = 0; i < regras.size(); i++) {
                if (i % 10 == 0) {
                    atualizarStatus("Processando " + i + " / " + total + " arquivos...");
                }


                String antigo = regras.get(i)[0];
                String novo = regras.get(i)[1];

                Uri origem = null;

                /*
                 * FORMATO:
                 *
                 * arquivo_original = novo_nome
                 */
                if (!antigo.isEmpty()) {

                    origem =
                            encontrarArquivo(
                                    arquivos,
                                    antigo
                            );

                /*
                 * FORMATO POR ORDEM:
                 *
                 * novo_nome
                 *
                 * Nesse caso o primeiro campo fica vazio
                 * e o segundo contém o novo nome.
                 */
                } else if (i < arquivos.size()) {

                    origem = arquivos.get(i);
                }

                if (origem == null) {

                    naoEncontrados++;

                    relatorio.append(
                            "[NÃO ENCONTRADO] "
                    );

                    if (!antigo.isEmpty()) {
                        relatorio.append(antigo);
                    } else {
                        relatorio.append(
                                "posição " + (i + 1)
                        );
                    }

                    relatorio.append("\n");

                    continue;
                }

                String nomeDestino = novo;

                Uri destino = null;

                try {

                    destino =
                            criarArquivo(
                                    nomeDestino
                            );

                    if (destino == null) {

                        throw new Exception(
                                "Não foi possível criar o destino."
                        );
                    }

                    copiarArquivo(
                            origem,
                            destino
                    );

                    sucesso++;

                    relatorio.append(
                            "[OK] "
                    );

                    relatorio.append(
                            obterNome(origem)
                    );

                    relatorio.append(
                            " -> "
                    );

                    relatorio.append(
                            nomeDestino
                    );

                    relatorio.append("\n");

                } catch (Exception e) {

                    erros++;

                    if (destino != null) {

                        try {

                            DocumentsContract.deleteDocument(
                                    getContentResolver(),
                                    destino
                            );

                        } catch (Exception ignored) {
                        }
                    }

                    relatorio.append(
                            "[ERRO] "
                    );

                    relatorio.append(
                            obterNome(origem)
                    );

                    relatorio.append(
                            " -> "
                    );

                    relatorio.append(
                            nomeDestino
                    );

                    relatorio.append(
                            " : "
                    );

                    relatorio.append(
                            e.getMessage()
                    );

                    relatorio.append("\n");
                }
            }

            relatorio.append("\n");
            relatorio.append(
                    "========================================\n"
            );

            relatorio.append(
                    "Concluído: "
            );

            relatorio.append(
                    sucesso
            );

            relatorio.append(
                    " arquivo(s)\n"
            );

            relatorio.append(
                    "Não encontrados: "
            );

            relatorio.append(
                    naoEncontrados
            );

            relatorio.append("\n");

            relatorio.append(
                    "Erros: "
            );

            relatorio.append(
                    erros
            );

            relatorio.append("\n");

            relatorio.append(
                    "Originais: preservados\n"
            );

            relatorio.append(
                    "========================================"
            );

            long segundos = (android.os.SystemClock.elapsedRealtime() - inicio) / 1000;
            atualizarStatus("BATCH CONCLUÍDO\n\n" +
                    "Processados: " + total + "\n" +
                    "Concluídos: " + sucesso + "\n" +
                    "Não encontrados: " + naoEncontrados + "\n" +
                    "Erros: " + erros + "\n" +
                    "Tempo: " + segundos + "s\n" +
                    "Originais preservados.");


        } catch (Exception e) {

            final String erro =
                    e.getMessage() == null
                            ? e.toString()
                            : e.getMessage();

            atualizarStatus(
                    "❌ ERRO:\n\n" + erro
            );
        }
    }

    private List<String[]> carregarLista()
            throws Exception {

        List<String[]> lista =
                new ArrayList<>();

        InputStream in =
                getContentResolver()
                        .openInputStream(listaUri);

        if (in == null) {
            throw new Exception(
                    "Não foi possível abrir o nomes.txt."
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

            linha = linha.trim();

            if (linha.isEmpty() ||
                    linha.startsWith("#")) {
                continue;
            }

            /*
             * FORMATO 1:
             *
             * Messi.bin = ID03097
             *
             * Primeiro campo = arquivo original
             * Segundo campo = novo nome
             */

            int separador =
                    linha.indexOf("=");

            if (separador >= 0) {

                String antigo =
                        linha.substring(
                                0,
                                separador
                        ).trim();

                String novo =
                        linha.substring(
                                separador + 1
                        ).trim();

                if (!antigo.isEmpty() &&
                        !novo.isEmpty()) {

                    lista.add(
                            new String[]{
                                    antigo,
                                    novo
                            }
                    );
                }

                continue;
            }

            /*
             * FORMATO 2:
             *
             * ID03097
             * ID03098
             * ID03099
             *
             * O arquivo correspondente será
             * escolhido pela posição na pasta.
             */

            lista.add(
                    new String[]{
                            "",
                            linha
                    }
            );
        }

        reader.close();
        in.close();

        return lista;
    }

    private List<Uri> listarArquivos(Uri pasta)
            throws Exception {

        List<Uri> arquivos =
                new ArrayList<>();

        String documentId =
                DocumentsContract.getTreeDocumentId(
                        pasta
                );

        Uri childrenUri =
                DocumentsContract
                        .buildChildDocumentsUriUsingTree(
                                pasta,
                                documentId
                        );

        android.database.Cursor cursor =
                getContentResolver().query(
                        childrenUri,
                        new String[]{
                                DocumentsContract.Document
                                        .COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document
                                        .COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document
                                        .COLUMN_MIME_TYPE
                        },
                        null,
                        null,
                        null
                );

        if (cursor == null) {
            return arquivos;
        }

        try {

            while (cursor.moveToNext()) {

                String id =
                        cursor.getString(0);

                String nome =
                        cursor.getString(1);

                int tipo =
                        cursor.getColumnIndex(
                                DocumentsContract.Document
                                        .COLUMN_MIME_TYPE
                        );

                if (nome == null) {
                    continue;
                }

                if (tipo >= 0 &&
                        "vnd.android.document/directory"
                                .equals(cursor.getString(tipo))) {
                    continue;
                }

                Uri arquivo =
                        DocumentsContract
                                .buildDocumentUriUsingTree(
                                        pasta,
                                        id
                                );

                arquivos.add(arquivo);
            }

        } finally {
            cursor.close();
        }

        // Ordem alfabética fixa dentro do app.
        // Compara primeiro o nome sem extensão, para que:
        // Danilo.bin -> Danilo Santos.bin -> Messi.bin
        // independentemente da ordem mostrada pelo seletor de arquivos.
        arquivos.sort((a, b) -> {
            String nomeA = obterNome(a);
            String nomeB = obterNome(b);

            if (nomeA == null) nomeA = "";
            if (nomeB == null) nomeB = "";

            String stemA = obterStem(nomeA);
            String stemB = obterStem(nomeB);

            int cmp = stemA.compareToIgnoreCase(stemB);
            if (cmp != 0) return cmp;

            return nomeA.compareToIgnoreCase(nomeB);
        });

        return arquivos;
    }

    private Uri encontrarArquivo(
            List<Uri> arquivos,
            String nomeAntigo
    ) throws Exception {

        Uri exato = null;

        for (Uri uri : arquivos) {

            String nome =
                    obterNome(uri);

            if (nome == null) {
                continue;
            }

            if (nome.equals(nomeAntigo)) {
                return uri;
            }
        }

        String stemProcurado =
                obterStem(nomeAntigo);

        for (Uri uri : arquivos) {

            String nome =
                    obterNome(uri);

            if (nome == null) {
                continue;
            }

            if (obterStem(nome)
                    .equals(nomeAntigo)) {

                if (exato != null) {
                    return null;
                }

                exato = uri;
            }
        }

        if (exato != null) {
            return exato;
        }

        if (!stemProcurado.equals(nomeAntigo)) {
            return null;
        }

        return null;
    }

    private String obterStem(String nome) {

        int ponto =
                nome.lastIndexOf(".");

        if (ponto > 0) {
            return nome.substring(
                    0,
                    ponto
            );
        }

        return nome;
    }

    private String obterNome(Uri uri) {

        if (uri == null) return null;

        String chave = uri.toString();
        String cache = nomesCache.get(chave);
        if (cache != null) return cache;

        android.database.Cursor cursor =
                getContentResolver().query(
                        uri,
                        new String[]{
                                DocumentsContract.Document
                                        .COLUMN_DISPLAY_NAME
                        },
                        null,
                        null,
                        null
                );

        if (cursor == null) {
            return null;
        }

        try {

            if (cursor.moveToFirst()) {
                String nome = cursor.getString(0);
                if (nome != null) nomesCache.put(chave, nome);
                return nome;
            }

        } finally {
            cursor.close();
        }

        return null;
    }

    private Uri criarArquivo(
            String nome
    ) throws Exception {

        String documentId =
                DocumentsContract.getTreeDocumentId(
                        pastaResultadoUri
                );

        Uri pastaDocumentUri =
                DocumentsContract
                        .buildDocumentUriUsingTree(
                                pastaResultadoUri,
                                documentId
                        );

        String mimeType =
                nome.toLowerCase(
                        Locale.ROOT
                ).endsWith(".png")
                        ? "image/png"
                        : "application/octet-stream";

        return DocumentsContract.createDocument(
                getContentResolver(),
                pastaDocumentUri,
                mimeType,
                nome
        );
    }

    private void copiarArquivo(
            Uri origem,
            Uri destino
    ) throws Exception {

        InputStream entrada =
                getContentResolver()
                        .openInputStream(origem);

        OutputStream saida =
                getContentResolver()
                        .openOutputStream(destino);

        if (entrada == null ||
                saida == null) {

            if (entrada != null) {
                entrada.close();
            }

            if (saida != null) {
                saida.close();
            }

            throw new Exception(
                    "Não foi possível copiar o arquivo."
            );
        }

        try {
            byte[] buffer = new byte[131072];
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                saida.write(buffer, 0, lidos);
            }
            saida.flush();
        } finally {
            try { entrada.close(); } finally { saida.close(); }
        }
    }

    private void gerarListaArquivos() {
        if (pastaArquivosUri == null && arquivosSelecionados.isEmpty()) {
            Toast.makeText(this, "Selecione os arquivos primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        atualizarStatus("Gerando lista...");
        new Thread(() -> {
            try {
                List<Uri> arquivos = obterArquivosSelecionados();
                if (arquivos.isEmpty()) {
                    atualizarStatus("❌ Nenhum arquivo encontrado.");
                    return;
                }
                StringBuilder texto = new StringBuilder();
                for (Uri arquivo : arquivos) {
                    String nome = obterNome(arquivo);
                    if (nome != null && !nome.trim().isEmpty())
                        texto.append(nome.trim()).append("\n");
                }
                listaGeradaPendente = texto.toString();
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, "#Lista_Arquivos.txt");
                    startActivityForResult(intent, REQUEST_SALVAR_LISTA);
                });
            } catch (Exception e) {
                String erro = e.getMessage() == null ? e.toString() : e.getMessage();
                atualizarStatus("❌ ERRO AO GERAR LISTA:\n\n" + erro);
            }
        }).start();
    }

    private Uri criarArquivoNaPastaArquivos(
            String nome
    ) throws Exception {

        String documentId =
                DocumentsContract.getTreeDocumentId(
                        pastaArquivosUri
                );

        Uri pastaDocumentUri =
                DocumentsContract
                        .buildDocumentUriUsingTree(
                                pastaArquivosUri,
                                documentId
                        );

        return DocumentsContract.createDocument(
                getContentResolver(),
                pastaDocumentUri,
                "text/plain",
                nome
        );
    }

    private void atualizarStatus(
            String texto
    ) {

        runOnUiThread(() ->
                txtStatus.setText(texto)
        );
    }
    private class PreviewScrollBar extends android.view.View {
        private final TextView target;
        private final android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);

        PreviewScrollBar(android.content.Context context, TextView target) {
            super(context);
            this.target = target;
            setClickable(true);
        }

        private int maxScroll() {
            if (target.getLayout() == null) return 0;
            int content = target.getLayout().getHeight()
                    + target.getPaddingTop() + target.getPaddingBottom();
            return Math.max(0, content - target.getHeight());
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float trackW = dp(4);
            float cx = w / 2f;

            paint.setColor(0xff34373a);
            canvas.drawRoundRect(cx-trackW/2f, dp(8), cx+trackW/2f, h-dp(8),
                    trackW/2f, trackW/2f, paint);

            int max = maxScroll();
            float usable = Math.max(1f, h - dp(16));
            float thumbH;
            float top;
            if (max <= 0 || target.getLayout() == null) {
                thumbH = usable;
                top = dp(8);
            } else {
                float content = target.getLayout().getHeight()
                        + target.getPaddingTop() + target.getPaddingBottom();
                thumbH = Math.max(dp(42), usable * target.getHeight() / content);
                float range = Math.max(1f, usable - thumbH);
                top = dp(8) + range * target.getScrollY() / max;
            }

            paint.setColor(0xffaeb4ba);
            canvas.drawRoundRect(cx-dp(3), top, cx+dp(3), top+thumbH,
                    dp(3), dp(3), paint);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN
                    || event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                int max = maxScroll();
                if (max > 0) {
                    float h = Math.max(1f, getHeight() - dp(16));
                    float ratio = (event.getY() - dp(8)) / h;
                    ratio = Math.max(0f, Math.min(1f, ratio));
                    target.scrollTo(0, Math.round(max * ratio));
                    invalidate();
                }
                return true;
            }
            return event.getAction() == android.view.MotionEvent.ACTION_UP
                    || super.onTouchEvent(event);
        }
    }

}
