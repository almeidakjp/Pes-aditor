package com.peseditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.os.Environment;
import android.os.Build;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CpkActivity extends BaseActivity {

    private static final int PICK_CPK = 100;
    private static final int PICK_SAVE_ZIP = 101;
    private static final int PICK_ZIP = 102;
    private static final int PICK_SAVE_CPK = 103;
    private static final int PICK_SAVE_ID = 104;
    private static final int PICK_REPLACE_ID = 105;
    private static final int PICK_BATCH_REPLACE = 106;
    private static final int PICK_SELECTED_FOLDER = 107;
    private static final int PICK_BATCH_ADD = 109;

    private TextView txtStatus;
    private File selectedCpk;
    private String nomeCpkOriginal = "CPK";
    private File salvarArquivoPendente;
    private Button btnListar;
    private Button btnExtrair;
    private Button btnRepack;
    private Button btnReplaceBatch;
    private Button btnAddBatch;
    private Button btnDeleteBatch;
    private final java.util.Set<String> selectedIds = new java.util.HashSet<>();
    private TextView cpkName, cpkSize, count;
    private SharedPreferences cpkPrefs;
    private static final String PREF_CPK = "cpk_quick_access";
    private EditText search;
    private ListView listView;
    private final List<EntryView> entries = new ArrayList<>();
    private final List<EntryView> filtered = new ArrayList<>();
    private EntryAdapter adapter;
    private boolean dragSelecting = false;
    private int dragLastPosition = -1;
    private int dragAnchorPosition = -1;
    private final java.util.Set<String> dragInitialSelection = new java.util.HashSet<>();
    private float dragY = 0;
    private final android.os.Handler dragHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable dragAutoScroll = new Runnable() {
        @Override public void run() {
            if (!dragSelecting || listView == null) return;
            int edge = dp(65);
            int delta = dragY < edge ? -dp(24) : dragY > listView.getHeight()-edge ? dp(24) : 0;
            if (delta != 0) {
                listView.smoothScrollBy(delta, 65);
                selectDragAt(dragY);
            }
            dragHandler.postDelayed(this, 65);
        }
    };
    private EntryView pendingExtractEntry;
    private EntryView pendingReplaceEntry;
    private Uri selectedCpkUri;
    private static class EntryView { String name; long packed, original, offset; EntryView(String n,long p,long o){name=n;packed=p;original=o;} }

    private static final int ALIGN = 512;
    private static final String VERSION = "CPKMG1.36.00, DLL1.36.00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cpkPrefs = getSharedPreferences(PREF_CPK, MODE_PRIVATE);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(14)); root.setBackgroundColor(0xff101112);
        // Barra de pesquisa com menu compacto integrado à direita.
        LinearLayout searchBar=new LinearLayout(this); searchBar.setOrientation(LinearLayout.HORIZONTAL); searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(uiRound(0xff191b1d,16,0xff2c2f33));
        search=new EditText(this); search.setHint("Pesquisar CPK ou arquivo..."); search.setHintTextColor(0xff888b90); search.setTextColor(Color.WHITE); search.setSingleLine(true); search.setBackgroundColor(Color.TRANSPARENT); search.setPadding(dp(14),0,dp(6),0);
        searchBar.addView(search,new LinearLayout.LayoutParams(0,dp(50),1f));
        TextView menuTop=uiText("⋮",27,Color.WHITE,true); menuTop.setGravity(Gravity.CENTER); menuTop.setBackgroundResource(android.R.drawable.list_selector_background);
        searchBar.addView(menuTop,new LinearLayout.LayoutParams(dp(46),dp(50)));
        root.addView(searchBar,uiLp(-1,50,0,8));
        menuTop.setOnClickListener(v->mostrarMenuHistoricoFavoritos());
        // Mantém a função LISTAR do projeto original disponível internamente, mas a listagem ITOC é automática.
        btnListar=new Button(this); btnListar.setVisibility(View.GONE); btnListar.setEnabled(false);
        // Botões de ação mantidos como controladores internos; as ações são exibidas no menu do +.
        btnExtrair=uiSmallButton("EXTRAIR"); btnRepack=uiSmallButton("CRIAR");
        btnAddBatch=uiSmallButton("ADICIONAR"); btnReplaceBatch=uiSmallButton("SUBSTITUIR"); btnDeleteBatch=uiSmallButton("EXCLUIR");
        LinearLayout summary=new LinearLayout(this); summary.setOrientation(LinearLayout.HORIZONTAL); summary.setPadding(dp(12),dp(10),dp(12),dp(10)); summary.setBackground(uiRound(0xff191b1d,16,0xff2c2f33));
        LinearLayout a=summaryCell("ARQUIVO:"); cpkName=(TextView)a.getChildAt(1);
        LinearLayout nameLine=new LinearLayout(this); nameLine.setOrientation(LinearLayout.HORIZONTAL); nameLine.setGravity(Gravity.CENTER_VERTICAL);
        a.removeView(cpkName); nameLine.addView(cpkName,new LinearLayout.LayoutParams(0,-2,1f));
        a.addView(nameLine);
        summary.addView(a,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout b=summaryCell("TAMANHO:"); cpkSize=(TextView)b.getChildAt(1); summary.addView(b,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout c=summaryCell("ARQUIVOS:"); count=(TextView)c.getChildAt(1); summary.addView(c,new LinearLayout.LayoutParams(0,dp(60),1)); root.addView(summary,uiLp(-1,-2,0,10));
        LinearLayout cols=new LinearLayout(this); cols.setOrientation(LinearLayout.HORIZONTAL); cols.setPadding(dp(12),0,dp(12),0); cols.addView(uiCol("ID",1.5f)); cols.addView(uiCol("TAMANHO\nCOMPRIMIDO",1f)); cols.addView(uiCol("TAMANHO\nORIGINAL",1f)); cols.setVisibility(View.GONE); cols.setId(123456); root.addView(cols,uiLp(-1,46,0,4));
        LinearLayout listFrame=new LinearLayout(this); listFrame.setOrientation(LinearLayout.HORIZONTAL);
        listView=new ListView(this); listView.setDivider(null); listView.setDividerHeight(0); listView.setVerticalScrollBarEnabled(false); listView.setPadding(0,0,0,dp(72)); listView.setClipToPadding(false); adapter=new EntryAdapter(); listView.setAdapter(adapter);
        listFrame.addView(listView,new LinearLayout.LayoutParams(0,-1,1f));
        FastScrollBar fastBar=new FastScrollBar(); LinearLayout.LayoutParams fastLp=new LinearLayout.LayoutParams(dp(18),-1); fastLp.leftMargin=dp(4); listFrame.addView(fastBar,fastLp);
        FrameLayout workArea=new FrameLayout(this);
        workArea.addView(listFrame,new FrameLayout.LayoutParams(-1,-1));
        TextView fab=uiText("+",32,Color.WHITE,true); fab.setGravity(Gravity.CENTER); fab.setBackground(uiRound(0xff202326,100,0xff45494e)); fab.setElevation(dp(8));
        FrameLayout.LayoutParams fabLp=new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.RIGHT|Gravity.BOTTOM); fabLp.setMargins(0,0,dp(14),dp(14));
        workArea.addView(fab,fabLp); fab.setOnClickListener(v->mostrarMenuAcoesCpk());
        root.addView(workArea,new LinearLayout.LayoutParams(-1,0,1));
        listView.setOnScrollListener(new AbsListView.OnScrollListener(){ public void onScrollStateChanged(AbsListView v,int state){} public void onScroll(AbsListView v,int first,int visible,int total){ fastBar.update(first,visible,total); }});
        txtStatus=uiText("Abra um CPK para visualizar os arquivos.",13,0xffa8a9ad,false); txtStatus.setPadding(dp(4),dp(8),dp(4),dp(2)); root.addView(txtStatus); setContentView(root);
        btnExtrair.setOnClickListener(v->{if(selectedIds.isEmpty()) selecionarDestinoZip(); else selecionarDestinoSelecionados();});
        btnReplaceBatch.setOnClickListener(v->escolherArquivosEmLote());
        btnAddBatch.setOnClickListener(v->escolherArquivosParaAdicionar());
        btnDeleteBatch.setOnClickListener(v->excluirSelecionados());
        btnRepack.setOnClickListener(v->selecionarZip());
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int b,int c){} public void onTextChanged(CharSequence x,int a,int b,int c){filterEntries(x.toString());} public void afterTextChanged(Editable e){}});
        listView.setOnItemClickListener((parent, view, position, id) -> {EntryView e=filtered.get(position);if(!selectedIds.isEmpty()){if(!selectedIds.add(e.name))selectedIds.remove(e.name);adapter.notifyDataSetChanged();mostrarSelecao();}else showEntryMenu(e);});
        listView.setOnItemLongClickListener((parent, view, position, id)->{
            if(position<0 || position>=filtered.size())return true;
            dragInitialSelection.clear();
            dragInitialSelection.addAll(selectedIds);
            dragAnchorPosition=position;
            selectedIds.add(filtered.get(position).name);
            dragSelecting=true;
            dragLastPosition=position;
            adapter.notifyDataSetChanged();mostrarSelecao();
            dragHandler.removeCallbacks(dragAutoScroll);
            dragHandler.postDelayed(dragAutoScroll,65);
            return true;
        });
        listView.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){dragSelecting=false;dragLastPosition=-1;dragAnchorPosition=-1;dragHandler.removeCallbacks(dragAutoScroll);return false;}
            if(dragSelecting && action==MotionEvent.ACTION_MOVE){
                dragY=event.getY();selectDragAt(dragY);return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                if(dragSelecting){dragSelecting=false;dragLastPosition=-1;dragAnchorPosition=-1;dragHandler.removeCallbacks(dragAutoScroll);return true;}
                dragHandler.removeCallbacks(dragAutoScroll);
            }
            return false;
        });
    }

    private void selectDragAt(float y){
        if(filtered.isEmpty())return;
        float safeY=Math.max(1,Math.min(listView.getHeight()-1,y));
        int position=listView.pointToPosition(dp(10),(int)safeY);
        if(position==ListView.INVALID_POSITION){
            position=y<listView.getHeight()/2f?listView.getFirstVisiblePosition():listView.getLastVisiblePosition();
        }
        if(position<0 || position>=filtered.size())return;
        // Recalcular a faixa desde o início do gesto: ao voltar o dedo,
        // os itens fora da faixa são desmarcados, preservando seleções anteriores.
        int anchor=dragAnchorPosition<0?position:dragAnchorPosition;
        int from=Math.min(anchor,position);
        int to=Math.max(anchor,position);
        java.util.Set<String> desired=new java.util.HashSet<>(dragInitialSelection);
        for(int i=from;i<=to;i++)desired.add(filtered.get(i).name);
        boolean changed=!selectedIds.equals(desired);
        if(changed){selectedIds.clear();selectedIds.addAll(desired);adapter.notifyDataSetChanged();mostrarSelecao();}
        dragLastPosition=position;
    }

    private Button uiSmallButton(String text) {
        Button b=uiButton(text);
        b.setTextSize(11);
        b.setPadding(dp(4),0,dp(4),0);
        return b;
    }

    private class FastScrollBar extends View {
        private final Paint trackPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float thumbTop=0f, thumbHeight=80f;
        private int totalItems=0, visibleItems=0;
        FastScrollBar(){
            super(CpkActivity.this);
            trackPaint.setColor(0xff242628); thumbPaint.setColor(0xffd9d9d9);
            setPadding(dp(6),0,dp(4),0);
        }
        void update(int first,int visible,int total){
            totalItems=total; visibleItems=visible;
            if(total<=0 || visible<=0 || total<=visible){ thumbTop=0; thumbHeight=getHeight(); invalidate(); return; }
            float h=getHeight();
            thumbHeight=Math.max(dp(44), h*((float)visible/(float)total));
            float range=Math.max(1f,h-thumbHeight);
            thumbTop=range*((float)first/(float)Math.max(1,total-visible));
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f;
            c.drawRoundRect(cx-dp(2),0,cx+dp(2),getHeight(),dp(2),dp(2),trackPaint);
            if(totalItems>visibleItems && totalItems>0) c.drawRoundRect(cx-dp(4),thumbTop,cx+dp(4),Math.min(getHeight(),thumbTop+thumbHeight),dp(5),dp(5),thumbPaint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(totalItems<=visibleItems || totalItems<=0) return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE){
                float range=Math.max(1f,getHeight()-thumbHeight);
                float y=Math.max(0f,Math.min(range,e.getY()-thumbHeight/2f));
                int maxFirst=Math.max(0,totalItems-visibleItems);
                int pos=Math.round((y/range)*maxFirst);
                listView.setSelection(pos);
                thumbTop=y; invalidate(); return true;
            }
            return e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL;
        }
    }

    private static class CpkQuickItem { String uri, name; CpkQuickItem(String u,String n){uri=u;name=n;} }

    private List<CpkQuickItem> lerListaCpk(String chave) {
        List<CpkQuickItem> out=new ArrayList<>();
        try { JSONArray a=new JSONArray(cpkPrefs.getString(chave,"[]")); for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);out.add(new CpkQuickItem(o.getString("uri"),o.optString("name","CPK")));} } catch(Exception ignored) {}
        return out;
    }
    private void salvarListaCpk(String chave,List<CpkQuickItem> itens) {
        JSONArray a=new JSONArray(); try { for(CpkQuickItem x:itens){JSONObject o=new JSONObject();o.put("uri",x.uri);o.put("name",x.name);a.put(o);} } catch(Exception ignored) {}
        cpkPrefs.edit().putString(chave,a.toString()).apply();
    }
    private boolean ehFavorito(String uri) { for(CpkQuickItem x:lerListaCpk("favorites"))if(x.uri.equals(uri))return true;return false; }
    private void atualizarEstrela() { /* A estrela fica somente no cabeçalho ACESSO RÁPIDO. */ }
    private void alternarFavoritoAtual() {
        if(selectedCpkUri==null){mostrarStatusTemporario("Abra um CPK primeiro.");return;}
        List<CpkQuickItem> f=lerListaCpk("favorites");String u=selectedCpkUri.toString();boolean remove=false;
        for(int i=f.size()-1;i>=0;i--)if(f.get(i).uri.equals(u)){f.remove(i);remove=true;}
        if(!remove)f.add(0,new CpkQuickItem(u,nomeCpkOriginal));salvarListaCpk("favorites",f);atualizarEstrela();mostrarStatusTemporario(remove?"Favorito removido.":"⭐ CPK salvo nos favoritos.");
    }
    private interface AcaoMenuCompacto { void selecionar(int indice); }

    // Menu próprio do CPK TOOL: visual escuro do aplicativo e ancoragem no topo/rodapé.
    private void mostrarMenuCompacto(String titulo, String[] opcoes, boolean topo, AcaoMenuCompacto acao) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        painel.setPadding(dp(14), dp(12), dp(14), dp(12));
        painel.setBackground(uiRound(0xff202326, 18, 0xff45494e));
        LinearLayout cabecalho = new LinearLayout(this);
        cabecalho.setOrientation(LinearLayout.HORIZONTAL);
        cabecalho.setGravity(Gravity.CENTER_VERTICAL);
        TextView cab = uiText(titulo, 14, 0xffd9a441, true);
        cab.setPadding(dp(8), dp(5), dp(8), dp(10));
        cabecalho.addView(cab, new LinearLayout.LayoutParams(0, -2, 1f));
        if ("ACESSO RÁPIDO".equals(titulo)) {
            TextView estrela = uiText(selectedCpkUri != null && ehFavorito(selectedCpkUri.toString()) ? "★" : "☆", 22,
                selectedCpkUri != null && ehFavorito(selectedCpkUri.toString()) ? 0xffffc247 : 0xffa8a9ad, true);
            estrela.setGravity(Gravity.CENTER);
            estrela.setContentDescription("Marcar CPK atual como favorito");
            estrela.setPadding(dp(8), 0, dp(8), dp(8));
            estrela.setOnClickListener(v -> {
                alternarFavoritoAtual();
                boolean favorito = selectedCpkUri != null && ehFavorito(selectedCpkUri.toString());
                estrela.setText(favorito ? "★" : "☆");
                estrela.setTextColor(favorito ? 0xffffc247 : 0xffa8a9ad);
            });
            cabecalho.addView(estrela, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        painel.addView(cabecalho, new LinearLayout.LayoutParams(-2, -2));
        for (int i = 0; i < opcoes.length; i++) {
            final int indice = i;
            TextView item = uiText(opcoes[i], 15, Color.WHITE, true);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), 0, dp(10), 0);
            item.setBackground(uiRound(0xff292d31, 12, 0xff383d42));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.setMargins(0, dp(3), 0, dp(3));
            painel.addView(item, lp);
            item.setOnClickListener(v -> { dialog.dismiss(); acao.selecionar(indice); });
        }
        dialog.setContentView(painel);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            // Os menus compactos nao escurecem nem desfocam o conteudo ao fundo.
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            int larguraConteudo = (int) Math.ceil(cab.getPaint().measureText(titulo)) + dp(48);
            for (String opcao : opcoes) {
                android.text.TextPaint paint = new android.text.TextPaint();
                paint.setTextSize(dp(15));
                larguraConteudo = Math.max(larguraConteudo, (int) Math.ceil(paint.measureText(opcao)) + dp(58));
            }
            if ("ACESSO RÁPIDO".equals(titulo)) larguraConteudo += dp(36);
            int larguraPainel = Math.min(larguraConteudo, getResources().getDisplayMetrics().widthPixels - dp(40));
            params.width = larguraPainel;
            params.height = -2;
            params.gravity = topo ? (Gravity.TOP | Gravity.RIGHT) : (Gravity.BOTTOM | Gravity.RIGHT);
            params.x = dp(12);
            params.y = topo ? dp(82) : dp(116);
            params.dimAmount = 0f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            int larguraConteudo = (int) Math.ceil(cab.getPaint().measureText(titulo)) + dp(48);
            for (String opcao : opcoes) {
                android.text.TextPaint paint = new android.text.TextPaint();
                paint.setTextSize(dp(15));
                larguraConteudo = Math.max(larguraConteudo, (int) Math.ceil(paint.measureText(opcao)) + dp(58));
            }
            if ("ACESSO RÁPIDO".equals(titulo)) larguraConteudo += dp(36);
            window.setLayout(Math.min(larguraConteudo, getResources().getDisplayMetrics().widthPixels - dp(40)), -2);
            // Reaplica a posição depois de show(): o Android pode substituir a gravidade
            // inicial do Dialog durante a exibição e deixá-lo centralizado.
            window.setGravity(topo ? (Gravity.TOP | Gravity.RIGHT)
                                   : (Gravity.BOTTOM | Gravity.RIGHT));
            android.view.WindowManager.LayoutParams pos = window.getAttributes();
            pos.gravity = topo ? (Gravity.TOP | Gravity.RIGHT)
                               : (Gravity.BOTTOM | Gravity.RIGHT);
            pos.x = dp(12);
            pos.y = topo ? dp(82) : dp(116);
            window.setAttributes(pos);
        }
    }

    private void mostrarMenuHistoricoFavoritos() {
        final String[] opcoes={"★  Favoritos","◷  Histórico"};
        mostrarMenuCompacto("ACESSO RÁPIDO", opcoes, true,
            which -> mostrarListaRapida(which==0?"favorites":"recent",which==0?"Favoritos":"Histórico"));
    }

    private void mostrarListaRapida(String chave,String titulo) {
        List<CpkQuickItem> itens=lerListaCpk(chave);
        if(itens.isEmpty()){mostrarStatusTemporario(titulo+" vazio.");return;}
        List<String> nomes=new ArrayList<>(); for(CpkQuickItem x:itens)nomes.add(x.name);
        new AlertDialog.Builder(this).setTitle(titulo).setItems(nomes.toArray(new String[0]),(d,which)->abrirCpkUri(Uri.parse(itens.get(which).uri),itens.get(which).name)).setNegativeButton("FECHAR",null).show();
    }

    private void mostrarMenuAcoesCpk() {
        final String[] opcoes=selectedCpkUri==null
            ? new String[]{"Abrir"}
            : new String[]{"Abrir","Extrair","Criar","Adicionar","Substituir","Excluir"};
        mostrarMenuCompacto("AÇÕES DO CPK", opcoes, false, which -> {
            switch(which){
                case 0: selecionarCpk(); break;
                case 1: if(selectedIds.isEmpty())selecionarDestinoZip();else selecionarDestinoSelecionados(); break;
                case 2: selecionarZip(); break;
                case 3: escolherArquivosParaAdicionar(); break;
                case 4: escolherArquivosEmLote(); break;
                case 5: excluirSelecionados(); break;
            }
        });
    }

    private void mostrarAcessoRapido() {
        List<CpkQuickItem> recentes=lerListaCpk("recent");List<CpkQuickItem> favoritos=lerListaCpk("favorites");
        List<CpkQuickItem> itens=new ArrayList<>();List<String> linhas=new ArrayList<>();
        if(!favoritos.isEmpty()){linhas.add("⭐  FAVORITOS");itens.add(null);for(CpkQuickItem x:favoritos){linhas.add("★  "+x.name);itens.add(x);}}
        if(!recentes.isEmpty()){linhas.add("◷  RECENTES");itens.add(null);for(CpkQuickItem x:recentes){linhas.add("   "+x.name);itens.add(x);}}
        linhas.add("＋  ESCOLHER OUTRO CPK");itens.add(new CpkQuickItem("__PICK__",""));
        if(linhas.size()==1){selecionarCpk();return;}
        android.widget.ArrayAdapter<String> ad=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,linhas);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Abrir CPK").setAdapter(ad,(d,which)->{CpkQuickItem x=itens.get(which);if(x==null)return;if("__PICK__".equals(x.uri)){selecionarCpk();return;}Uri u=Uri.parse(x.uri);abrirCpkUri(u,x.name);}).setNegativeButton("FECHAR",null).create();
        dlg.setOnShowListener(v->{dlg.getListView().setOnItemLongClickListener((parent,view,pos,id)->{CpkQuickItem x=itens.get(pos);if(x==null||"__PICK__".equals(x.uri))return true;List<CpkQuickItem> f=lerListaCpk("favorites");boolean found=false;for(int i=f.size()-1;i>=0;i--)if(f.get(i).uri.equals(x.uri)){f.remove(i);found=true;}if(!found)f.add(0,x);salvarListaCpk("favorites",f);mostrarStatusTemporario(found?"Favorito removido.":"Adicionado aos favoritos.");dlg.dismiss();return true;});});dlg.show();
    }
    private void registrarRecente(Uri uri,String nome) {
        List<CpkQuickItem> r=lerListaCpk("recent");String u=uri.toString();for(int i=r.size()-1;i>=0;i--)if(r.get(i).uri.equals(u))r.remove(i);r.add(0,new CpkQuickItem(u,nome));while(r.size()>12)r.remove(r.size()-1);salvarListaCpk("recent",r);
    }
    private void abrirCpkUri(Uri uri,String nome) {
        selectedCpkUri=uri;nomeCpkOriginal=nome;
        new Thread(() -> {try {File destino=new File(getCacheDir(),"arquivo_selecionado.cpk");copiarUriParaArquivo(uri,destino);selectedCpk=destino;List<EntryView> parsed=parseItoc(destino);registrarRecente(uri,nome);runOnUiThread(()->{entries.clear();entries.addAll(parsed);cpkName.setText(nome);cpkSize.setText(formatSize(destino.length()));count.setText(String.valueOf(entries.size()));atualizarEstrela();findViewById(123456).setVisibility(View.VISIBLE);filterEntries("");mostrarStatusTemporario("✅ CPK aberto.");btnListar.setEnabled(true);btnExtrair.setEnabled(true);btnReplaceBatch.setEnabled(true);btnAddBatch.setEnabled(true);btnDeleteBatch.setEnabled(true);selectedIds.clear();});}catch(Exception e){runOnUiThread(()->txtStatus.setText("❌ Erro ao abrir CPK:\n\n"+e.getMessage()));}}).start();
    }

    private void selecionarCpk() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, PICK_CPK);
    }

    private void selecionarDestinoZip() {
        if (selectedCpk == null || !selectedCpk.exists()) {
            txtStatus.setText("❌ Nenhum CPK selecionado.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        String nome = obterNomeArquivoOriginal();
        intent.putExtra(Intent.EXTRA_TITLE, nome + ".zip");
        startActivityForResult(intent, PICK_SAVE_ZIP);
    }

    private void selecionarZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, PICK_ZIP);
    }

    private void mostrarStatusTemporario(String mensagem) {
        txtStatus.setText(mensagem);
        txtStatus.removeCallbacks(limparStatusRunnable);
        txtStatus.postDelayed(limparStatusRunnable, 3000);
    }

    private final Runnable limparStatusRunnable = new Runnable() {
        @Override
        public void run() {
            txtStatus.setText("");
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode==108){
            if(Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager()) selecionarDestinoSelecionados();
            else mostrarStatusTemporario("Permissão necessária para escolher pastas.");
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();

        if (requestCode == PICK_CPK) {
            try { getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch (Exception ignored) {}
            abrirCpkUri(uri, obterNome(uri));

        } else if (requestCode == PICK_SAVE_ZIP) {
            final Uri destino = uri;
            txtStatus.setText("⏳ Extraindo CPK e criando ZIP...");
            new Thread(() -> executarExtracaoParaZip(destino)).start();

        } else if (requestCode == PICK_ZIP) {
            txtStatus.setText("⏳ Preparando reempacotamento do ZIP...");
            new Thread(() -> executarRepackZip(uri)).start();

        } else if (requestCode == PICK_SAVE_ID) {
            final EntryView e = pendingExtractEntry;
            if (e == null || selectedCpk == null || !selectedCpk.exists()) {
                txtStatus.setText("❌ Arquivo/ID não disponível.");
                return;
            }
            final Uri destino = uri;
            txtStatus.setText("⏳ Extraindo " + e.name + "...");
            new Thread(() -> extrairIdParaUri(e, destino)).start();

        } else if (requestCode == PICK_REPLACE_ID) {
            final EntryView e = pendingReplaceEntry;
            if (e == null || selectedCpk == null || !selectedCpk.exists() || selectedCpkUri == null) {
                txtStatus.setText("❌ CPK/ID não disponível.");
                return;
            }
            final Uri arquivoNovo = uri;
            txtStatus.setText("⏳ Substituindo " + e.name + "...");
            new Thread(() -> substituirId(e, arquivoNovo)).start();

        } else if(requestCode == PICK_SELECTED_FOLDER){final Uri dest=uri;final java.util.Set<String> ids=new java.util.HashSet<>(selectedIds);new Thread(()->extrairSelecionados(dest,ids)).start();
        } else if(requestCode == PICK_BATCH_ADD){
            final java.util.ArrayList<Uri> arquivos=new java.util.ArrayList<>();
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)arquivos.add(data.getClipData().getItemAt(i).getUri());}
            else if(uri!=null)arquivos.add(uri);
            if(!arquivos.isEmpty()){txtStatus.setText("⏳ Adicionando arquivos ao CPK...");new Thread(()->adicionarArquivos(arquivos)).start();}
        } else if(requestCode == PICK_BATCH_REPLACE){
            final java.util.ArrayList<Uri> arquivos=new java.util.ArrayList<>();
            if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)arquivos.add(data.getClipData().getItemAt(i).getUri());}
            else if(uri!=null)arquivos.add(uri);
            new Thread(()->substituirLote(arquivos)).start();
        } else if (requestCode == PICK_SAVE_CPK) {
            if (salvarArquivoPendente == null || !salvarArquivoPendente.exists()) {
                txtStatus.setText("❌ Arquivo CPK temporário não encontrado.");
                return;
            }

            final Uri destino = uri;
            final File arquivo = salvarArquivoPendente;
            txtStatus.setText("⏳ Salvando CPK...");

            new Thread(() -> {
                try {
                    copiarArquivoParaUri(arquivo, destino);
                    long tamanho = arquivo.length();
                    runOnUiThread(() -> mostrarStatusTemporario("✅ CPK salvo com sucesso."));
                } catch (Exception e) {
                    runOnUiThread(() -> txtStatus.setText("❌ Erro ao salvar CPK:\n\n" + e.getMessage()));
                } finally {
                    salvarArquivoPendente = null;
                }
            }).start();
        }
    }

    private void listarCpk() {
        if (selectedCpk == null || !selectedCpk.exists()) {
            txtStatus.setText("❌ Nenhum CPK selecionado.");
            return;
        }

        txtStatus.setText("⏳ Lendo arquivos do CPK...");
        new Thread(() -> {
            try {
                String resultado = executarTool("-l", selectedCpk.getAbsolutePath());
                runOnUiThread(() -> txtStatus.setText("📋 ARQUIVOS DO CPK\n\n" + resultado));
            } catch (Exception e) {
                runOnUiThread(() -> txtStatus.setText("❌ Erro ao listar CPK:\n\n" + e.getMessage()));
            }
        }).start();
    }

    private void executarExtracaoParaZip(Uri destinoUri) {
        File pastaTemp = null;
        File zipTemp = null;
        try {
            pastaTemp = new File(getCacheDir(), "cpk_extract_zip");
            apagarRecursivo(pastaTemp);
            if (!pastaTemp.mkdirs() && !pastaTemp.exists()) {
                throw new Exception("Não foi possível criar a pasta temporária.");
            }

            String resultado = executarTool("-e", selectedCpk.getAbsolutePath(), pastaTemp.getAbsolutePath());
            String nome = obterNomeArquivoOriginal();
            zipTemp = new File(getCacheDir(), nome + ".zip");
            if (zipTemp.exists()) zipTemp.delete();

            criarZipStored(pastaTemp, zipTemp);
            copiarArquivoParaUri(zipTemp, destinoUri);

            runOnUiThread(() -> mostrarStatusTemporario("✅ Extração concluída."));
        } catch (Exception e) {
            runOnUiThread(() -> txtStatus.setText("❌ Erro ao extrair CPK:\n\n" + e.getMessage()));
        } finally {
            apagarRecursivo(pastaTemp);
            apagarRecursivo(zipTemp);
        }
    }

    private void criarZipStored(File pasta, File zipFile) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            adicionarPastaAoZip(pasta, pasta, zos);
        } finally {
            zos.close();
        }
    }

    private void adicionarPastaAoZip(File raiz, File atual, ZipOutputStream zos) throws Exception {
        File[] arquivos = atual.listFiles();
        if (arquivos == null) return;

        for (File arquivo : arquivos) {
            String relativo = raiz.toURI().relativize(arquivo.toURI()).getPath();
            if (arquivo.isDirectory()) {
                adicionarPastaAoZip(raiz, arquivo, zos);
            } else {
                ZipEntry entry = new ZipEntry(relativo);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(arquivo.length());
                entry.setCompressedSize(arquivo.length());
                entry.setCrc(calcularCrc32(arquivo));
                zos.putNextEntry(entry);
                copiarArquivoParaStream(arquivo, zos);
                zos.closeEntry();
            }
        }
    }

    private void copiarArquivoParaStream(File arquivo, OutputStream out) throws Exception {
        InputStream in = new FileInputStream(arquivo);
        byte[] buffer = new byte[8192];
        int n;
        try {
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } finally {
            in.close();
        }
    }

    private long calcularCrc32(File arquivo) throws Exception {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        InputStream in = new FileInputStream(arquivo);
        byte[] buffer = new byte[8192];
        int n;
        try {
            while ((n = in.read(buffer)) != -1) crc.update(buffer, 0, n);
        } finally {
            in.close();
        }
        return crc.getValue();
    }

    private void executarRepackZip(Uri zipUri) {
        File zipTemp = null;
        File cpkTemp = null;
        try {
            String zipNome = obterNome(zipUri);
            if (zipNome == null || !zipNome.toLowerCase().endsWith(".zip")) {
                throw new Exception("Selecione um arquivo ZIP.");
            }
            String nome = zipNome.substring(0, zipNome.length() - 4);
            if (nome.isEmpty()) nome = "CPK_REPACK";

            zipTemp = new File(getCacheDir(), "repack_input.zip");
            cpkTemp = new File(getCacheDir(), nome + ".cpk");
            if (zipTemp.exists()) zipTemp.delete();
            if (cpkTemp.exists()) cpkTemp.delete();

            copiarUriParaArquivo(zipUri, zipTemp);
            repackZip(zipTemp, cpkTemp);

            final File arquivoFinal = cpkTemp;
            final String nomeFinal = nome + ".cpk";
            runOnUiThread(() -> {
                txtStatus.setText("");
                salvarCpkNoAndroid(arquivoFinal, nomeFinal);
            });
        } catch (Exception e) {
            runOnUiThread(() -> txtStatus.setText("❌ Erro ao reempacotar ZIP:\n\n" + e.getMessage()));
            if (cpkTemp != null) apagarRecursivo(cpkTemp);
        } finally {
            apagarRecursivo(zipTemp);
        }
    }

    private void repackZip(File zipFile, File outputFile) throws Exception {
        ZipFile zip = new ZipFile(zipFile);
        try {
            List<ZipEntry> entries = new ArrayList<>();
            for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry info = e.nextElement();
                if (info.isDirectory()) continue;
                String filename = new File(info.getName()).getName();
                int id = parseId(filename);
                if (id >= 0) entries.add(info);
            }

            Collections.sort(entries, Comparator.comparingInt(e -> parseId(new File(e.getName()).getName())));
            if (entries.isEmpty()) throw new Exception("Nenhum arquivo ID00000...ID99999 encontrado.");
            for (int i = 0; i < entries.size(); i++) {
                int id = parseId(new File(entries.get(i).getName()).getName());
                if (id != i) throw new Exception("IDs precisam ser consecutivos começando em ID00000.");
            }

            List<FileInfo> infos = new ArrayList<>();
            long enabled = 0;
            long contentSize = 0;
            for (ZipEntry e : entries) {
                long size = e.getSize();
                if (size < 0) throw new Exception("Tamanho desconhecido no ZIP: " + e.getName());
                if (size > 0xFFFFFFFFL) throw new Exception("Arquivo grande demais: " + e.getName());
                FileInfo fi = new FileInfo(parseId(new File(e.getName()).getName()), (int) size, e);
                infos.add(fi);
                enabled += size;
                contentSize += size + ((ALIGN - (size % ALIGN)) % ALIGN);
            }

            long contentOffset = 0x800L;
            long itocOffset = contentOffset + contentSize;

            List<Map<String,Object>> dataL = new ArrayList<>();
            List<Map<String,Object>> dataH = new ArrayList<>();
            for (FileInfo fi : infos) {
                Map<String,Object> row = new HashMap<>();
                row.put("ID", fi.id);
                row.put("FileSize", fi.size);
                row.put("ExtractSize", fi.size);
                if (fi.size <= 0xFFFF) dataL.add(row); else dataH.add(row);
            }

            byte[] innerL = utfBuild("CpkItocL", new Col[]{
                    new Col("ID","u16",0x50), new Col("FileSize","u16",0x50), new Col("ExtractSize","u16",0x50)
            }, dataL, 1);
            byte[] innerH = utfBuild("CpkItocH", new Col[]{
                    new Col("ID","u16",0x50), new Col("FileSize","u32",0x50), new Col("ExtractSize","u32",0x50)
            }, dataH, 7);

            Map<String,Object> itocRow = new HashMap<>();
            itocRow.put("FilesL", dataL.size());
            itocRow.put("FilesH", dataH.size());
            itocRow.put("DataL", innerL);
            itocRow.put("DataH", innerH);
            byte[] itocUtf = utfBuild("CpkItocInfo", new Col[]{
                    new Col("FilesL","u32",0x50), new Col("FilesH","u32",0x50), new Col("DataL","bytes",0x50), new Col("DataH","bytes",0x50)
            }, Collections.singletonList(itocRow), 7);
            long itocSize = 16L + itocUtf.length;

            Col[] headerCols = new Col[]{
                    new Col("UpdateDateTime","u64",0x10), new Col("FileSize","u64",0x10), new Col("ContentOffset","u64",0x50),
                    new Col("ContentSize","u64",0x10), new Col("TocOffset","u64",0x10), new Col("TocSize","u64",0x10),
                    new Col("EtocOffset","u64",0x10), new Col("EtocSize","u64",0x10), new Col("ItocOffset","u64",0x50),
                    new Col("ItocSize","u64",0x50), new Col("EnabledPackedSize","u64",0x50), new Col("EnabledDataSize","u64",0x50),
                    new Col("TotalDataSize","u64",0x10), new Col("Tocs","u32",0x10), new Col("Files","u32",0x50),
                    new Col("TotalFiles","u32",0x10), new Col("Directories","u32",0x10), new Col("Updates","u32",0x10),
                    new Col("Version","u16",0x50), new Col("Revision","u16",0x50), new Col("Align","u16",0x50),
                    new Col("Sorted","u16",0x50), new Col("Tvers","str",0x50), new Col("Comment","str",0x50)
            };
            Map<String,Object> h = new HashMap<>();
            h.put("UpdateDateTime",0L); h.put("FileSize",0L); h.put("ContentOffset",contentOffset); h.put("ContentSize",0L);
            h.put("TocOffset",0L); h.put("TocSize",0L); h.put("EtocOffset",0L); h.put("EtocSize",0L); h.put("ItocOffset",itocOffset);
            h.put("ItocSize",itocSize); h.put("EnabledPackedSize",enabled); h.put("EnabledDataSize",enabled); h.put("TotalDataSize",0L);
            h.put("Tocs",0); h.put("Files",infos.size()); h.put("TotalFiles",0); h.put("Directories",0); h.put("Updates",0);
            h.put("Version",2); h.put("Revision",3); h.put("Align",ALIGN); h.put("Sorted",0); h.put("Tvers",VERSION); h.put("Comment","");

            byte[] headerUtf = utfBuild("CpkHeader", headerCols, Collections.singletonList(h), 7);
            if (headerUtf.length != 504) throw new Exception("CPK header UTF inesperado: " + headerUtf.length + " bytes");

            FileOutputStream fos = new FileOutputStream(outputFile);
            try {
                byte[] cpk = new byte[0x800];
                System.arraycopy(new byte[]{'C','P','K',' '},0,cpk,0,4);
                writeLe32(cpk,4,0xFF);
                writeLe32(cpk,8,headerUtf.length);
                writeLe32(cpk,12,0);
                System.arraycopy(headerUtf,0,cpk,16,headerUtf.length);
                byte[] marker = "(c)CRI".getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(marker,0,cpk,0x800-6,6);
                fos.write(cpk);

                byte[] buffer = new byte[8192];
                for (FileInfo fi : infos) {
                    InputStream in = zip.getInputStream(fi.entry);
                    try {
                        int n;
                        while ((n=in.read(buffer))!=-1) fos.write(buffer,0,n);
                    } finally { in.close(); }
                    int pad = (int)((ALIGN - (fi.size % ALIGN)) % ALIGN);
                    if (pad > 0) fos.write(new byte[pad]);
                }

                fos.write(new byte[]{'I','T','O','C'});
                writeLe32(fos,0xFF);
                writeLe32(fos,itocUtf.length);
                writeLe32(fos,0);
                fos.write(itocUtf);
            } finally { fos.close(); }
        } finally { zip.close(); }
    }

    private static class FileInfo {
        int id, size; ZipEntry entry;
        FileInfo(int id,int size,ZipEntry entry){this.id=id;this.size=size;this.entry=entry;}
    }
    private static class Col {
        String name,type; int storage;
        Col(String n,String t,int s){name=n;type=t;storage=s;}
    }

    private byte[] utfBuild(String name, Col[] cols, List<Map<String,Object>> rows, int extraZeros) throws Exception {
        ByteArrayOutputStream strings = new ByteArrayOutputStream();
        Map<String,Integer> offsets = new HashMap<>();
        java.util.function.Function<String,Integer> addString = s -> {
            Integer old = offsets.get(s); if (old != null) return old;
            int off = strings.size(); offsets.put(s,off);
            try { strings.write(s.getBytes(StandardCharsets.UTF_8)); strings.write(0); } catch(Exception ignored) {}
            return off;
        };
        addString.apply("<NULL>");
        int tableNameOffset = addString.apply(name);
        for (Col c: cols) addString.apply(c.name);
        for (Map<String,Object> row: rows) for (Col c: cols) if (c.storage==0x50 && "str".equals(c.type)) addString.apply((String)row.get(c.name));
        for(int i=0;i<extraZeros;i++) strings.write(0);

        ByteArrayOutputStream coldata = new ByteArrayOutputStream();
        ByteArrayOutputStream rowdata = new ByteArrayOutputStream();
        ByteArrayOutputStream bindata = new ByteArrayOutputStream();
        for (Col c: cols) {
            coldata.write(c.storage | typeNum(c.type)); writeBe32(coldata, offsets.get(c.name));
        }
        for (Map<String,Object> row: rows) for (Col c: cols) if(c.storage==0x50) {
            Object v=row.get(c.name);
            switch(c.type){
                case "u16": writeBe16(rowdata, ((Number)v).intValue()); break;
                case "u32": writeBe32(rowdata, ((Number)v).longValue()); break;
                case "u64": writeBe64(rowdata, ((Number)v).longValue()); break;
                case "str": writeBe32(rowdata, offsets.get((String)v)); break;
                case "bytes": int off=bindata.size(); byte[] b=(byte[])v; bindata.write(b); writeBe32(rowdata,off); writeBe32(rowdata,b.length); break;
            }
        }
        int rowLength=0; for(Col c:cols) if(c.storage==0x50) rowLength+=typeSize(c.type);
        int rowsOffset=24+coldata.size(); int stringOffset=rowsOffset+rowdata.size(); int dataOffset=stringOffset+strings.size(); int tableSize=dataOffset+bindata.size();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); out.write(new byte[]{'@','U','T','F'}); writeBe32(out,tableSize); writeBe32(out,rowsOffset); writeBe32(out,stringOffset); writeBe32(out,dataOffset); writeBe32(out,tableNameOffset); writeBe16(out,cols.length); writeBe16(out,rowLength); writeBe32(out,rows.size()); out.write(coldata.toByteArray()); out.write(rowdata.toByteArray()); out.write(strings.toByteArray()); out.write(bindata.toByteArray()); return out.toByteArray();
    }
    private int typeNum(String t){switch(t){case"u16":return 2;case"u32":return 4;case"u64":return 6;case"str":return 10;default:return 11;}}
    private int typeSize(String t){switch(t){case"u16":return 2;case"u32":return 4;case"u64":return 8;case"str":return 4;default:return 8;}}
    private void writeBe16(OutputStream o,int v)throws Exception{o.write((v>>>8)&255);o.write(v&255);}
    private void writeBe32(OutputStream o,long v)throws Exception{o.write((int)(v>>>24)&255);o.write((int)(v>>>16)&255);o.write((int)(v>>>8)&255);o.write((int)v&255);}
    private void writeBe64(OutputStream o,long v)throws Exception{for(int i=7;i>=0;i--)o.write((int)(v>>>(8*i))&255);}
    private void writeLe32(byte[] a,int p,long v){a[p]=(byte)v;a[p+1]=(byte)(v>>>8);a[p+2]=(byte)(v>>>16);a[p+3]=(byte)(v>>>24);}
    private void writeLe32(OutputStream o,long v)throws Exception{o.write((int)v&255);o.write((int)(v>>>8)&255);o.write((int)(v>>>16)&255);o.write((int)(v>>>24)&255);}

    private int parseId(String name) {
        if (name == null) return -1;
        name = new File(name).getName();
        int dot = name.indexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        if (name.length() != 7 || !name.startsWith("ID")) return -1;
        try { return Integer.parseInt(name.substring(2)); } catch(Exception e) { return -1; }
    }

    private void salvarCpkNoAndroid(File arquivo, String nomeArquivo) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, nomeArquivo);
        salvarArquivoPendente = arquivo;
        startActivityForResult(intent, PICK_SAVE_CPK);
    }

    private String executarTool(String... argumentos) throws Exception {
        String nativeDir = getApplicationInfo().nativeLibraryDir;
        File tool = new File(nativeDir, "libcpk-tool.so");
        File linker = new File("/system/bin/linker64");
        if (!tool.exists()) throw new Exception("cpk-tool não encontrado em:\n" + tool.getAbsolutePath());
        if (!linker.exists()) throw new Exception("linker64 não encontrado em:\n" + linker.getAbsolutePath());
        List<String> comando = new ArrayList<>(); comando.add(linker.getAbsolutePath()); comando.add(tool.getAbsolutePath());
        Collections.addAll(comando, argumentos);
        ProcessBuilder pb = new ProcessBuilder(comando); pb.redirectErrorStream(true); pb.environment().put("LD_LIBRARY_PATH", nativeDir);
        Process process=pb.start(); BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream())); StringBuilder output=new StringBuilder(); String linha;
        while((linha=reader.readLine())!=null) output.append(linha).append("\n");
        int codigo=process.waitFor(); if(codigo!=0) throw new Exception("CPK Tool retornou código "+codigo+"\n\n"+output); return output.toString();
    }

    private void copiarUriParaArquivo(Uri uri, File destino) throws Exception {
        InputStream in=getContentResolver().openInputStream(uri); if(in==null) throw new Exception("Não foi possível abrir o arquivo.");
        FileOutputStream out=new FileOutputStream(destino); copiarStream(in,out); out.close(); in.close();
    }
    private void copiarArquivoParaUri(File arquivo, Uri destino) throws Exception {
        InputStream in=new FileInputStream(arquivo); OutputStream out=getContentResolver().openOutputStream(destino); if(out==null){in.close();throw new Exception("Não foi possível criar o arquivo.");} copiarStream(in,out); out.close(); in.close();
    }
    private void copiarStream(InputStream in, OutputStream out) throws Exception { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) out.write(b,0,n); out.flush(); }

    private String obterNome(Uri uri) {
        Cursor cursor=getContentResolver().query(uri,null,null,null,null);
        if(cursor!=null){try{int index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(index>=0&&cursor.moveToFirst())return cursor.getString(index);}finally{cursor.close();}}
        return "arquivo";
    }
    private String obterNomeArquivoOriginal() {
        String n = nomeCpkOriginal;
        if (n == null || n.trim().isEmpty()) n = "CPK";
        n = n.trim();
        if (n.toLowerCase().endsWith(".cpk"))
            n = n.substring(0, n.length() - 4);
        if (n.isEmpty()) n = "CPK";
        return n;
    }
    // Leitor ITOC adicionado à base original. Não altera extração/reempacotamento existentes.
    private List<EntryView> parseItoc(File file) throws Exception {
        RandomAccessFile r=new RandomAccessFile(file,"r"); try { UtfInfo h=readUtf(r,16); Map<String,Object> hr=h.rows.get(0); long content=asLong(hr.get("ContentOffset")); long align=asLong(hr.get("Align")); if(align<=0)align=512; long off=asLong(hr.get("ItocOffset")); if(off<=0)return new ArrayList<>(); r.seek(off); byte[] tag=new byte[4];r.readFully(tag);if(!"ITOC".equals(new String(tag,"US-ASCII")))return new ArrayList<>(); UtfInfo it=readUtf(r,off+16); Map<String,Object> row=it.rows.get(0); ArrayList<EntryView> out=new ArrayList<>(); addData(r,it,row.get("DataL"),out); addData(r,it,row.get("DataH"),out); Collections.sort(out,(x,y)->Integer.compare(idNum(x.name),idNum(y.name))); long pos=content; for(EntryView e:out){e.offset=pos; pos += e.packed; long rem=pos%align; if(rem!=0)pos += align-rem;} return out; } finally {r.close();}
    }
    private void addData(RandomAccessFile r,UtfInfo parent,Object obj,List<EntryView> out)throws Exception{if(!(obj instanceof long[]))return;long[]d=(long[])obj;if(d[1]<=0)return;UtfInfo t=readUtf(r,parent.dataBase+d[0]);for(Map<String,Object> row:t.rows){long id=asLong(row.get("ID")),fs=asLong(row.get("FileSize")),es=asLong(row.get("ExtractSize"));out.add(new EntryView(String.format(Locale.US,"ID%05d",id),fs,es));}}
    private int idNum(String s){try{return Integer.parseInt(s.substring(2));}catch(Exception e){return Integer.MAX_VALUE;}}
    private static class UtfInfo{long dataBase;List<Map<String,Object>> rows=new ArrayList<>();} private static class UtfCol{String n;int s,t;Object c;UtfCol(String n,int s,int t,Object c){this.n=n;this.s=s;this.t=t;this.c=c;}}
    private UtfInfo readUtf(RandomAccessFile r,long base)throws Exception{r.seek(base);byte[]m=new byte[4];r.readFully(m);if(!"@UTF".equals(new String(m,"US-ASCII")))throw new IOException("@UTF inválido");readU32(r);long rows=base+8+readU32(r),strings=base+8+readU32(r),data=base+8+readU32(r);readU32(r);int nc=readU16(r),rw=readU16(r);long nr=readU32(r),cp=r.getFilePointer();ArrayList<UtfCol>cs=new ArrayList<>();for(int i=0;i<nc;i++){r.seek(cp);int f=r.readUnsignedByte();long no=readU32(r);cp=r.getFilePointer();String n=readCString(r,strings+no);int st=f&0xF0,ty=f&15;Object c=null;if(st==0x30){r.seek(cp);c=readVal(r,ty,strings);cp=r.getFilePointer();}cs.add(new UtfCol(n,st,ty,c));}UtfInfo z=new UtfInfo();z.dataBase=data;for(int i=0;i<nr;i++){long p=rows+(long)i*rw;LinkedHashMap<String,Object>q=new LinkedHashMap<>();for(UtfCol c:cs){Object v;if(c.s==0x50){r.seek(p);v=readVal(r,c.t,strings);p=r.getFilePointer();}else if(c.s==0x30)v=c.c;else v=0L;q.put(c.n,v);}z.rows.add(q);}return z;}
    private Object readVal(RandomAccessFile r,int t,long strings)throws Exception{switch(t){case 0:return(long)r.readUnsignedByte();case 1:return(long)r.readByte();case 2:return(long)r.readUnsignedShort();case 3:return(long)r.readShort();case 4:return readU32(r);case 5:return(long)r.readInt();case 6:case 7:return r.readLong();case 8:return(double)r.readFloat();case 10:{long o=readU32(r);return readCString(r,strings+o);}case 11:return new long[]{readU32(r),readU32(r)};default:throw new IOException("Tipo UTF "+t);}}
    private long readU32(RandomAccessFile r)throws Exception{return((long)r.readUnsignedByte()<<24)|((long)r.readUnsignedByte()<<16)|((long)r.readUnsignedByte()<<8)|r.readUnsignedByte();} private int readU16(RandomAccessFile r)throws Exception{return(r.readUnsignedByte()<<8)|r.readUnsignedByte();}
    private String readCString(RandomAccessFile r,long p)throws Exception{long o=r.getFilePointer();r.seek(p);ByteArrayOutputStream b=new ByteArrayOutputStream();int x;while((x=r.read())>0)b.write(x);r.seek(o);return new String(b.toByteArray(),"UTF-8");} private long asLong(Object o){return o instanceof Number?((Number)o).longValue():0;}

    private void mostrarSelecao(){
        if(selectedIds.isEmpty())mostrarStatusTemporario("Seleção encerrada.");
        else txtStatus.setText(selectedIds.size()+" arquivo(s) selecionado(s).");
    }
    @Override public void onBackPressed(){if(!selectedIds.isEmpty()){selectedIds.clear();adapter.notifyDataSetChanged();mostrarSelecao();}else super.onBackPressed();}
    private void selecionarDestinoSelecionados(){
        if(selectedIds.isEmpty()){mostrarStatusTemporario("Selecione arquivos primeiro.");return;}
        if(Build.VERSION.SDK_INT>=30 && !Environment.isExternalStorageManager()){
            new android.app.AlertDialog.Builder(this).setTitle("Acesso aos arquivos")
                .setMessage("Autorize o acesso aos arquivos para escolher pastas como Download e extrair vários BINs diretamente nelas.")
                .setPositiveButton("AUTORIZAR",(d,w)->{
                    Intent i=new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:"+getPackageName()));
                    try{startActivityForResult(i,108);}catch(Exception ex){startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
                }).setNegativeButton("CANCELAR",null).show();
            return;
        }
        mostrarNavegadorPastas(Environment.getExternalStorageDirectory());
    }
    private void mostrarNavegadorPastas(File pasta){
        File root=Environment.getExternalStorageDirectory();
        try{
            String raiz=root.getCanonicalPath(), atual=pasta.getCanonicalPath();
            if(!atual.equals(raiz) && !atual.startsWith(raiz+File.separator))pasta=root;
        }catch(IOException ex){pasta=root;}
        final File destino=pasta;
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(16),dp(16),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF202124);bg.setCornerRadius(dp(18));
        panel.setBackground(bg);
        TextView heading=uiText("Escolher pasta",19,Color.WHITE,true);
        panel.addView(heading);
        TextView path=uiText(destino.equals(root)?"Armazenamento":destino.getAbsolutePath().replace(root.getAbsolutePath(),"Armazenamento"),12,0xFFAAAAAA,false);
        path.setPadding(0,dp(5),0,dp(10));panel.addView(path);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,0,1);
        panel.addView(scroll,scrollParams);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        actions.setPadding(0,dp(10),0,0);
        TextView cancel=uiText("CANCELAR",13,0xFFB8C7DA,true);
        cancel.setPadding(dp(12),dp(12),dp(12),dp(12));actions.addView(cancel);
        TextView extract=uiText("EXTRAIR AQUI",13,0xFF93C5FD,true);
        extract.setPadding(dp(12),dp(12),dp(4),dp(12));actions.addView(extract);
        panel.addView(actions);
        android.app.Dialog dialog=new android.app.Dialog(this);
        dialog.getWindow();dialog.setContentView(panel);
        android.view.Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(42),
                (int)(getResources().getDisplayMetrics().heightPixels*0.68f));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=0.38f;window.setAttributes(attrs);}
        cancel.setOnClickListener(v->dialog.dismiss());
        final File[] currentFolder=new File[]{destino};
        extract.setOnClickListener(v->{dialog.dismiss();java.util.Set<String> ids=new java.util.HashSet<>(selectedIds);
            txtStatus.setText("⏳ Extraindo arquivos...");new Thread(()->extrairSelecionadosDireto(currentFolder[0],ids)).start();});
        preencherPastas(root,currentFolder,path,content,scroll);
        dialog.show();
        if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(42),
            (int)(getResources().getDisplayMetrics().heightPixels*0.68f));
    }
    private void preencherPastas(File root,File[] currentFolder,TextView path,LinearLayout content,android.widget.ScrollView scroll){
        File destino=currentFolder[0];
        path.setText(destino.equals(root)?"Armazenamento":destino.getAbsolutePath().replace(root.getAbsolutePath(),"Armazenamento"));
        content.removeAllViews();
        if(!destino.equals(root)){
            TextView up=uiText("‹    Voltar",15,0xFF93C5FD,false);
            up.setPadding(dp(10),dp(11),dp(8),dp(11));content.addView(up);
            up.setOnClickListener(v->{currentFolder[0]=destino.getParentFile();preencherPastas(root,currentFolder,path,content,scroll);});
        }
        File[] dirs=destino.listFiles(f->f.isDirectory() && f.canRead() && !f.getName().startsWith("."));
        java.util.ArrayList<File> folders=new java.util.ArrayList<>();
        if(dirs!=null)Collections.addAll(folders,dirs);
        Collections.sort(folders,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        for(File folder:folders){
            TextView item=uiText("📁   "+folder.getName(),15,0xFFF0F0F0,false);
            item.setSingleLine(true);item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setPadding(dp(10),dp(11),dp(8),dp(11));content.addView(item);
            item.setOnClickListener(v->{currentFolder[0]=folder;preencherPastas(root,currentFolder,path,content,scroll);});
        }
        scroll.scrollTo(0,0);
    }
    private void extrairSelecionadosDireto(File pasta,java.util.Set<String> ids){
        int total=0;
        try(RandomAccessFile raf=new RandomAccessFile(selectedCpk,"r")){
            if(!pasta.isDirectory()||!pasta.canWrite())throw new IOException("Pasta sem permissão de escrita.");
            byte[] buf=new byte[65536];
            for(EntryView e:entries){if(!ids.contains(e.name))continue;
                File target=new File(pasta,e.name+".bin");
                try(FileOutputStream out=new FileOutputStream(target)){
                    raf.seek(e.offset);long remaining=e.packed;
                    while(remaining>0){int n=raf.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new IOException("CPK truncado");out.write(buf,0,n);remaining-=n;}
                }
                total++;
            }
            final int n=total;
            runOnUiThread(()->{selectedIds.clear();adapter.notifyDataSetChanged();mostrarStatusTemporario("✅ "+n+" arquivo(s) extraído(s) em "+pasta.getAbsolutePath());});
        }catch(Exception ex){final int n=total;runOnUiThread(()->txtStatus.setText("❌ Extraídos: "+n+". Erro: "+ex.getMessage()));}
    }
    private void extrairSelecionadosDownload(java.util.Set<String> ids){
        int total=0;
        try(RandomAccessFile raf=new RandomAccessFile(selectedCpk,"r")){
            if(Build.VERSION.SDK_INT<29)throw new IOException("Download direto requer Android 10 ou superior.");
            byte[] buf=new byte[65536];
            for(EntryView e:entries){if(!ids.contains(e.name))continue;
                ContentValues cv=new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME,e.name+".bin");
                cv.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/"+obterNomeArquivoOriginal()+"/");
                cv.put(MediaStore.MediaColumns.IS_PENDING,1);
                Uri file=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
                if(file==null)throw new IOException("Não foi possível criar "+e.name);
                boolean success=false;
                try(OutputStream out=getContentResolver().openOutputStream(file,"w")){
                    if(out==null)throw new IOException("Não foi possível gravar "+e.name);
                    raf.seek(e.offset);long remaining=e.packed;
                    while(remaining>0){int n=raf.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new IOException("CPK truncado");out.write(buf,0,n);remaining-=n;}
                    out.flush();success=true;
                }finally{
                    if(success){ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(file,ready,null,null);}
                    else getContentResolver().delete(file,null,null);
                }
                total++;
            }
            final int n=total;runOnUiThread(()->{selectedIds.clear();adapter.notifyDataSetChanged();mostrarStatusTemporario("✅ "+n+" arquivo(s) extraído(s) em Download/"+obterNomeArquivoOriginal()+".");});
        }catch(Exception ex){final int n=total;runOnUiThread(()->txtStatus.setText("❌ Extraídos: "+n+". Erro: "+ex.getMessage()));}
    }
    private Uri encontrarOuCriarSubpasta(Uri parent, String nome) throws Exception {
        Uri filhos=DocumentsContract.buildChildDocumentsUriUsingTree(parent,DocumentsContract.getDocumentId(parent));
        try(Cursor c=getContentResolver().query(filhos,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
            if(c!=null)while(c.moveToNext()){
                if(nome.equals(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)))
                    return DocumentsContract.buildDocumentUriUsingTree(parent,c.getString(0));
            }
        }
        Uri criada=DocumentsContract.createDocument(getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,nome);
        if(criada==null)throw new IOException("Não foi possível criar a pasta "+nome);
        return criada;
    }
    private void extrairSelecionados(Uri pasta, java.util.Set<String> ids){
        int total=0;
        try(RandomAccessFile raf=new RandomAccessFile(selectedCpk,"r")){
            String treeId=DocumentsContract.getTreeDocumentId(pasta);
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(pasta,treeId);
            Uri subpasta=encontrarOuCriarSubpasta(parent,obterNomeArquivoOriginal());
            byte[] buf=new byte[65536];
            for(EntryView e:entries){if(!ids.contains(e.name))continue;
                Uri file=DocumentsContract.createDocument(getContentResolver(),subpasta,"application/octet-stream",e.name+".bin");
                if(file==null)throw new IOException("Não foi possível criar "+e.name);
                try(OutputStream out=getContentResolver().openOutputStream(file,"w")){
                    if(out==null)throw new IOException("Não foi possível gravar "+e.name);
                    raf.seek(e.offset);long remaining=e.packed;
                    while(remaining>0){int n=raf.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new IOException("CPK truncado");out.write(buf,0,n);remaining-=n;}
                    out.flush();
                }
                total++;
            }
            final int n=total;runOnUiThread(()->{selectedIds.clear();adapter.notifyDataSetChanged();mostrarStatusTemporario("✅ "+n+" arquivo(s) extraído(s) em "+obterNomeArquivoOriginal()+".");});
        }catch(Exception ex){final int n=total;runOnUiThread(()->txtStatus.setText("❌ Extraídos: "+n+". Erro: "+ex.getMessage()));}
    }
    private void escolherArquivosEmLote(){
        if(selectedCpk==null){mostrarStatusTemporario("Abra um CPK primeiro.");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_BATCH_REPLACE);
    }
    private void escolherArquivosParaAdicionar(){
        if(selectedCpk==null || !selectedCpk.exists())return;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,PICK_BATCH_ADD);
    }
    private void adicionarArquivos(List<Uri> novos){
        File zipTmp=new File(getCacheDir(),"cpk_adicionar_entrada.zip");
        File cpkTmp=new File(getCacheDir(),"cpk_adicionado.cpk");
        try{
            if(novos.isEmpty())return;
            List<EntryView> originais=new ArrayList<>(entries);
            Collections.sort(originais,(a,b)->Integer.compare(idNum(a.name),idNum(b.name)));
            for(int i=0;i<originais.size();i++){
                if(idNum(originais.get(i).name)!=i)throw new IOException("CPK contém IDs não consecutivos; adição automática indisponível.");
            }
            int ultimoId=originais.isEmpty()?-1:idNum(originais.get(originais.size()-1).name);
            if((long)ultimoId+novos.size()>65535L)throw new IOException("Limite de IDs do formato ITOC excedido.");
            try(RandomAccessFile src=new RandomAccessFile(selectedCpk,"r");ZipOutputStream zout=new ZipOutputStream(new FileOutputStream(zipTmp))){
                byte[] buf=new byte[65536];
                for(EntryView e:originais){
                    zout.putNextEntry(new ZipEntry(e.name+".bin"));
                    src.seek(e.offset);long remain=e.packed;
                    while(remain>0){int n=src.read(buf,0,(int)Math.min(buf.length,remain));if(n<0)throw new IOException("CPK truncado");zout.write(buf,0,n);remain-=n;}
                    zout.closeEntry();
                }
                for(int i=0;i<novos.size();i++){
                    String id=String.format(Locale.US,"ID%05d",ultimoId+1+i);
                    zout.putNextEntry(new ZipEntry(id+".bin"));
                    try(InputStream in=getContentResolver().openInputStream(novos.get(i))){if(in==null)throw new IOException("Arquivo indisponível");int n;while((n=in.read(buf))!=-1)zout.write(buf,0,n);}
                    zout.closeEntry();
                }
            }
            repackZip(zipTmp,cpkTmp);
            List<EntryView> parsed=parseItoc(cpkTmp);
            if(parsed.size()!=originais.size()+novos.size())throw new IOException("Falha ao validar quantidade de IDs.");
            // Releitura integral antes de modificar o arquivo escolhido.
            for(int i=0;i<parsed.size();i++){
                if(idNum(parsed.get(i).name)!=i)throw new IOException("ID inválido no CPK reconstruído.");
            }
            if(selectedCpkUri==null)throw new IOException("CPK original não disponível para gravação.");
            // Guarda uma cópia de recuperação em cache antes de sobrescrever o URI.
            File backup=new File(getCacheDir(),"cpk_antes_adicionar_backup.cpk");
            try(FileInputStream in=new FileInputStream(selectedCpk);FileOutputStream out=new FileOutputStream(backup)){copiarStream(in,out);}
            try{
                copiarArquivoParaUri(cpkTmp,selectedCpkUri);
            }catch(Exception writeError){
                // O provedor pode não garantir substituição atômica; tenta restaurar.
                try{copiarArquivoParaUri(backup,selectedCpkUri);}catch(Exception restoreError){
                    throw new IOException("Falha na gravação e na restauração. Backup: "+backup.getAbsolutePath(),writeError);
                }
                throw new IOException("Falha ao gravar CPK; original restaurado.",writeError);
            }
            File novoCache=new File(getCacheDir(),"arquivo_selecionado_novo.cpk");
            try(FileInputStream in=new FileInputStream(cpkTmp);FileOutputStream out=new FileOutputStream(novoCache)){copiarStream(in,out);}
            selectedCpk=novoCache;
            final int quantidade=novos.size();
            runOnUiThread(()->{
                entries.clear();entries.addAll(parsed);
                selectedIds.clear();filterEntries(search.getText().toString());
                cpkSize.setText(formatSize(novoCache.length()));count.setText(String.valueOf(entries.size()));
                mostrarStatusTemporario("✅ "+quantidade+" arquivo(s) adicionado(s) ao CPK.");
            });
        }catch(Exception ex){final String msg=ex.getMessage();runOnUiThread(()->txtStatus.setText("❌ Erro ao adicionar: "+msg));cpkTmp.delete();}
        finally{zipTmp.delete();}
    }
    private void excluirSelecionados(){
        if(selectedCpk==null || selectedCpkUri==null || selectedIds.isEmpty()){
            mostrarStatusTemporario("Selecione arquivos primeiro.");return;
        }
        final java.util.Set<String> apagar=new java.util.HashSet<>(selectedIds);
        btnDeleteBatch.setEnabled(false);
        new Thread(()->{
            File zipTmp=new File(getCacheDir(),"cpk_excluir_entrada.zip");
            File cpkTmp=new File(getCacheDir(),"cpk_excluir_resultado.cpk");
            try{
                List<EntryView> restantes=new ArrayList<>(entries);
                restantes.removeIf(e->apagar.contains(e.name));
                Collections.sort(restantes,(a,b)->Integer.compare(idNum(a.name),idNum(b.name)));
                if(restantes.isEmpty())throw new IOException("Não é possível criar CPK sem arquivos.");
                try(RandomAccessFile src=new RandomAccessFile(selectedCpk,"r");ZipOutputStream zout=new ZipOutputStream(new FileOutputStream(zipTmp))){
                    byte[] buf=new byte[65536];int next=0;
                    for(EntryView e:restantes){
                        zout.putNextEntry(new ZipEntry(String.format(Locale.US,"ID%05d.bin",next++)));
                        src.seek(e.offset);long remain=e.packed;
                        while(remain>0){int n=src.read(buf,0,(int)Math.min(buf.length,remain));if(n<0)throw new IOException("CPK truncado");zout.write(buf,0,n);remain-=n;}
                        zout.closeEntry();
                    }
                }
                repackZip(zipTmp,cpkTmp);
                List<EntryView> parsed=parseItoc(cpkTmp);
                if(parsed.size()!=restantes.size())throw new IOException("Quantidade de arquivos inválida após exclusão.");
                for(int i=0;i<parsed.size();i++)if(idNum(parsed.get(i).name)!=i)throw new IOException("Sequência de IDs inválida.");
                File backup=new File(getCacheDir(),"cpk_antes_excluir_backup.cpk");
                try(FileInputStream in=new FileInputStream(selectedCpk);FileOutputStream out=new FileOutputStream(backup)){copiarStream(in,out);}
                try{copiarArquivoParaUri(cpkTmp,selectedCpkUri);}
                catch(Exception writeError){
                    try{copiarArquivoParaUri(backup,selectedCpkUri);}catch(Exception restoreError){throw new IOException("Gravação e restauração falharam. Backup: "+backup.getAbsolutePath(),writeError);}
                    throw new IOException("Gravação falhou; original restaurado.",writeError);
                }
                File cache=new File(getCacheDir(),"arquivo_selecionado_exclusao.cpk");
                try(FileInputStream in=new FileInputStream(cpkTmp);FileOutputStream out=new FileOutputStream(cache)){copiarStream(in,out);}
                selectedCpk=cache;int n=apagar.size();
                runOnUiThread(()->{entries.clear();entries.addAll(parsed);selectedIds.clear();filterEntries(search.getText().toString());cpkSize.setText(formatSize(cache.length()));count.setText(String.valueOf(entries.size()));mostrarStatusTemporario("✅ "+n+" arquivo(s) excluído(s). IDs reorganizados.");});
            }catch(Exception ex){String msg=ex.getMessage();runOnUiThread(()->mostrarStatusTemporario("❌ Erro ao excluir: "+msg));}
            finally{zipTmp.delete();cpkTmp.delete();runOnUiThread(()->btnDeleteBatch.setEnabled(true));}
        }).start();
    }
    private void substituirLote(List<Uri> arquivos){
        int ok=0,ignorados=0;java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(Uri uri:arquivos){try{
            String nome=obterNome(uri);String base=nome==null?"":nome.trim();int dot=base.indexOf('.');if(dot>=0)base=base.substring(0,dot);int id=parseId(base.toUpperCase(Locale.US));EntryView found=null;
            if(id>=0)for(EntryView e:entries)if(idNum(e.name)==id){found=e;break;}
            if(found==null||!seen.add(found.name)){ignorados++;continue;}
            // Reaproveita o substituidor original, que atualiza o ITOC e o CPK em disco.
            if(substituirId(found,uri))ok++;else ignorados++;
        }catch(Exception ex){ignorados++;}}
        final int a=ok,b=ignorados;
        runOnUiThread(()->mostrarStatusTemporario("Lote processado: "+a+" substituído(s), "+b+" ignorado(s)."));
    }
    private void showEntryMenu(EntryView e) {
        final android.app.Dialog d = new android.app.Dialog(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(14),dp(18),dp(14)); box.setBackground(uiRound(0xff202124,18,0xff3a3c40));
        TextView title=uiText(e.name,16,Color.WHITE,true); title.setGravity(android.view.Gravity.CENTER); box.addView(title,uiLp(-1,-2,0,8));
        Button ex=uiButton("Extrair"); box.addView(ex,uiLp(-1,50,0,6));
        Button sub=uiButton("Substituir"); box.addView(sub,uiLp(-1,50,0,0));
        ex.setOnClickListener(v->{d.dismiss(); escolherDestinoId(e);});
        sub.setOnClickListener(v->{d.dismiss(); escolherArquivoSubstituto(e);});
        d.setContentView(box); if(d.getWindow()!=null)d.getWindow().setBackgroundDrawableResource(android.R.color.transparent); d.show();
    }
    private void escolherDestinoId(EntryView e){
        pendingExtractEntry=e;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream"); i.putExtra(Intent.EXTRA_TITLE,e.name); startActivityForResult(i,PICK_SAVE_ID);
    }
    private void extrairIdParaUri(EntryView e,Uri destino){
        try(RandomAccessFile in=new RandomAccessFile(selectedCpk,"r"); OutputStream out=getContentResolver().openOutputStream(destino,"w")){
            if(out==null)throw new IOException("Não foi possível abrir o arquivo de destino.");
            in.seek(e.offset); long faltam=e.packed; byte[] buf=new byte[65536];
            while(faltam>0){int n=in.read(buf,0,(int)Math.min(buf.length,faltam)); if(n<0)throw new IOException("Fim inesperado do CPK."); out.write(buf,0,n); faltam-=n;} out.flush();
            runOnUiThread(()->mostrarStatusTemporario("✅ "+e.name+" extraído — "+e.packed+" bytes"));
        }catch(Exception ex){runOnUiThread(()->txtStatus.setText("❌ Erro ao extrair "+e.name+":\n"+ex.getMessage()));}
    }
    private void escolherArquivoSubstituto(EntryView e){
        pendingReplaceEntry=e;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        startActivityForResult(i,PICK_REPLACE_ID);
    }

    private long alignUp(long v,long a){ long r=v%a; return r==0?v:v+(a-r); }

    private boolean substituirId(EntryView e, Uri arquivoNovo){
        File novoTmp=new File(getCacheDir(),"cpk_substituto.bin");
        File outTmp=new File(getCacheDir(),"cpk_modificado.cpk");
        try{
            copiarUriParaArquivo(arquivoNovo,novoTmp);
            long novoTam=novoTmp.length();
            long oldAligned=alignUp(e.packed,ALIGN), newAligned=alignUp(novoTam,ALIGN);
            long deltaAligned=newAligned-oldAligned;
            long deltaData=novoTam-e.packed;

            try(RandomAccessFile src=new RandomAccessFile(selectedCpk,"r"); FileOutputStream out=new FileOutputStream(outTmp); FileInputStream novo=new FileInputStream(novoTmp)){
                copiarFaixa(src,out,0,e.offset);
                copiarStream(novo,out);
                long pad=newAligned-novoTam; byte[] zeros=new byte[8192];
                while(pad>0){int n=(int)Math.min(zeros.length,pad);out.write(zeros,0,n);pad-=n;}
                long oldNext=e.offset+oldAligned;
                copiarFaixa(src,out,oldNext,src.length()-oldNext);
            }

            // Atualiza cabeçalho CPK e ITOC no arquivo reconstruído.
            try(RandomAccessFile rw=new RandomAccessFile(outTmp,"rw")){
                UtfPatch header=locateUtf(rw,16);
                long oldItoc=header.getLong(rw,"ItocOffset");
                patchNumeric(rw,header,"ItocOffset",oldItoc+deltaAligned);
                long eps=header.getLong(rw,"EnabledPackedSize");
                long eds=header.getLong(rw,"EnabledDataSize");
                if(eps>=0) patchNumeric(rw,header,"EnabledPackedSize",eps+deltaData);
                if(eds>=0) patchNumeric(rw,header,"EnabledDataSize",eds+deltaData);

                long newItoc=oldItoc+deltaAligned;
                rw.seek(newItoc); byte[] tag=new byte[4]; rw.readFully(tag);
                if(!"ITOC".equals(new String(tag,"US-ASCII"))) throw new IOException("ITOC não encontrado após reconstrução.");
                UtfInfo it=readUtf(rw,newItoc+16); Map<String,Object> top=it.rows.get(0);
                boolean ok=patchItocData(rw,it,top.get("DataL"),idNum(e.name),novoTam);
                if(!ok) ok=patchItocData(rw,it,top.get("DataH"),idNum(e.name),novoTam);
                if(!ok) throw new IOException("ID não encontrado no ITOC.");
            }

            // Só sobrescreve o CPK escolhido depois que toda a reconstrução terminou sem erro.
            copiarArquivoParaUri(outTmp,selectedCpkUri);
            File cacheNovo=new File(getCacheDir(),"arquivo_selecionado_novo.cpk");
            try(FileInputStream in=new FileInputStream(outTmp);FileOutputStream out=new FileOutputStream(cacheNovo)){copiarStream(in,out);}
            selectedCpk=cacheNovo;
            List<EntryView> parsed=parseItoc(selectedCpk);
            entries.clear();entries.addAll(parsed);
            runOnUiThread(()->{
                filterEntries(search.getText().toString());
                cpkSize.setText(formatSize(selectedCpk.length()));count.setText(String.valueOf(entries.size()));
                mostrarStatusTemporario("✅ "+e.name+" substituído — "+novoTam+" bytes");
            });
            return true;
        }catch(Exception ex){runOnUiThread(()->txtStatus.setText("❌ Erro ao substituir "+e.name+":\n"+ex.getMessage()));return false;}
        finally{novoTmp.delete();outTmp.delete();}
    }

    private void copiarFaixa(RandomAccessFile in,OutputStream out,long inicio,long tamanho)throws Exception{
        in.seek(inicio);byte[] b=new byte[65536];long faltam=tamanho;
        while(faltam>0){int n=in.read(b,0,(int)Math.min(b.length,faltam));if(n<0)throw new IOException("Fim inesperado do CPK.");out.write(b,0,n);faltam-=n;}
    }

    private static class PatchCell{long pos;int type;PatchCell(long p,int t){pos=p;type=t;}}
    private static class UtfPatch{
        Map<String,PatchCell> cells=new HashMap<>();
        long getLong(RandomAccessFile r,String n)throws Exception{PatchCell c=cells.get(n);if(c==null)return -1;r.seek(c.pos);return readNumericAt(r,c.type);}
    }
    private UtfPatch locateUtf(RandomAccessFile r,long base)throws Exception{
        r.seek(base);byte[]m=new byte[4];r.readFully(m);if(!"@UTF".equals(new String(m,"US-ASCII")))throw new IOException("@UTF inválido");
        readU32(r);long rows=base+8+readU32(r),strings=base+8+readU32(r);readU32(r);readU32(r);int nc=readU16(r),rw=readU16(r);long nr=readU32(r),cp=r.getFilePointer();
        class PC{String n;int st,t;long constPos;PC(String n,int st,int t,long p){this.n=n;this.st=st;this.t=t;this.constPos=p;}}
        ArrayList<PC> cs=new ArrayList<>();
        for(int i=0;i<nc;i++){r.seek(cp);int f=r.readUnsignedByte();long no=readU32(r);cp=r.getFilePointer();String n=readCString(r,strings+no);int st=f&0xF0,t=f&15;long cpos=-1;if(st==0x30){cpos=cp;r.seek(cp);readVal(r,t,strings);cp=r.getFilePointer();}cs.add(new PC(n,st,t,cpos));}
        UtfPatch z=new UtfPatch();if(nr<1)return z;long p=rows;
        for(PC c:cs){if(c.st==0x50){z.cells.put(c.n,new PatchCell(p,c.t));p+=typeSize(c.t);}else if(c.st==0x30)z.cells.put(c.n,new PatchCell(c.constPos,c.t));}
        return z;
    }
    private boolean patchItocData(RandomAccessFile r,UtfInfo parent,Object obj,int target,long novoTam)throws Exception{
        if(!(obj instanceof long[]))return false;long[]d=(long[])obj;if(d[1]<=0)return false;long base=parent.dataBase+d[0];
        r.seek(base);byte[]m=new byte[4];r.readFully(m);if(!"@UTF".equals(new String(m,"US-ASCII")))return false;
        readU32(r);long rows=base+8+readU32(r),strings=base+8+readU32(r);readU32(r);readU32(r);int nc=readU16(r),rw=readU16(r);long nr=readU32(r),cp=r.getFilePointer();
        class C{String n;int st,t;Object cv;C(String n,int st,int t,Object cv){this.n=n;this.st=st;this.t=t;this.cv=cv;}}
        ArrayList<C> cs=new ArrayList<>();
        for(int i=0;i<nc;i++){r.seek(cp);int f=r.readUnsignedByte();long no=readU32(r);cp=r.getFilePointer();String n=readCString(r,strings+no);int st=f&0xF0,t=f&15;Object cv=null;if(st==0x30){r.seek(cp);cv=readVal(r,t,strings);cp=r.getFilePointer();}cs.add(new C(n,st,t,cv));}
        for(int row=0;row<nr;row++){
            long pos=rows+(long)row*rw;long id=-1;PatchCell fs=null,es=null;
            for(C c:cs){if(c.st==0x50){long cell=pos;Object v; r.seek(pos);v=readVal(r,c.t,strings);pos=r.getFilePointer();if("ID".equals(c.n)&&v instanceof Number)id=((Number)v).longValue();if("FileSize".equals(c.n))fs=new PatchCell(cell,c.t);if("ExtractSize".equals(c.n))es=new PatchCell(cell,c.t);}else if(c.st==0x30&&"ID".equals(c.n)&&c.cv instanceof Number)id=((Number)c.cv).longValue();}
            if(id==target){if(fs==null||es==null)throw new IOException("Campos de tamanho do ID não editáveis.");writeNumericAt(r,fs.pos,fs.type,novoTam);writeNumericAt(r,es.pos,es.type,novoTam);return true;}
        }
        return false;
    }
    private int typeSize(int t)throws IOException{switch(t){case 0:case 1:return 1;case 2:case 3:return 2;case 4:case 5:case 8:case 10:return 4;case 6:case 7:case 11:return 8;default:throw new IOException("Tipo UTF "+t);}}
    private static long readNumericAt(RandomAccessFile r,int t)throws Exception{switch(t){case 0:return r.readUnsignedByte();case 1:return r.readByte();case 2:return r.readUnsignedShort();case 3:return r.readShort();case 4:return ((long)r.readUnsignedByte()<<24)|((long)r.readUnsignedByte()<<16)|((long)r.readUnsignedByte()<<8)|r.readUnsignedByte();case 5:return r.readInt();case 6:case 7:return r.readLong();default:return -1;}}
    private void patchNumeric(RandomAccessFile r,UtfPatch u,String n,long v)throws Exception{PatchCell c=u.cells.get(n);if(c!=null)writeNumericAt(r,c.pos,c.type,v);}
    private void writeNumericAt(RandomAccessFile r,long p,int t,long v)throws Exception{r.seek(p);switch(t){case 0:case 1:r.writeByte((int)v);break;case 2:case 3:r.writeShort((int)v);break;case 4:case 5:r.writeInt((int)v);break;case 6:case 7:r.writeLong(v);break;default:throw new IOException("Campo numérico UTF não suportado: "+t);}}

    private void filterEntries(String q){filtered.clear();String n=q==null?"":q.trim().toLowerCase(Locale.ROOT);for(EntryView e:entries)if(e.name.toLowerCase(Locale.ROOT).contains(n))filtered.add(e);adapter.notifyDataSetChanged();}
    private class EntryAdapter extends BaseAdapter{public int getCount(){return filtered.size();}public Object getItem(int p){return filtered.get(p);}public long getItemId(int p){return p;}public View getView(int p,View cv,ViewGroup parent){LinearLayout row;if(cv instanceof LinearLayout)row=(LinearLayout)cv;else{row=new LinearLayout(CpkActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(7),dp(12),dp(7));row.setBackground(uiRound(0xff1b1d1f,18,0xff34363a));row.setTag(new TextView[]{uiCell(15,1.5f),uiCell(14,1f),uiCell(14,1f)});for(TextView v:(TextView[])row.getTag())row.addView(v);}EntryView e=filtered.get(p);TextView[]v=(TextView[])row.getTag();v[0].setText(e.name);v[1].setText(formatSize(e.packed));v[2].setText(formatSize(e.original));row.setBackground(uiRound(selectedIds.contains(e.name)?0xff244d68:0xff1b1d1f,18,selectedIds.contains(e.name)?0xff4d9acb:0xff34363a));row.setLayoutParams(new AbsListView.LayoutParams(-1,dp(62)));return row;}}
    private LinearLayout summaryCell(String l){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setGravity(Gravity.CENTER_VERTICAL);x.addView(uiText(l,11,0xff9b9da2,true));TextView v=uiText("-",14,Color.WHITE,true);v.setSingleLine(true);x.addView(v);return x;} private TextView uiCol(String s,float w){TextView t=uiText(s,11,0xff9b9da2,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setLines(2);t.setLayoutParams(new LinearLayout.LayoutParams(0,-1,w));return t;} private TextView uiCell(int sp,float w){TextView t=uiText("",sp,Color.WHITE,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setSingleLine(true);t.setLayoutParams(new LinearLayout.LayoutParams(0,-1,w));return t;}
    private TextView uiText(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;} private Button uiButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(uiRound(0xff242629,18,0xff393b40));return b;} private GradientDrawable uiRound(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;} private LinearLayout.LayoutParams uiLp(int w,int h,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w==-1?-1:dp(w),h==-2?-2:dp(h));p.setMargins(0,dp(top),0,dp(bottom));return p;} private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);} private String formatSize(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.getDefault(),"%.1f kB",n/1024.0);return String.format(Locale.getDefault(),"%.2f MB",n/1048576.0);}

    private void apagarRecursivo(File arquivo){if(arquivo==null||!arquivo.exists())return;if(arquivo.isDirectory()){File[] f=arquivo.listFiles();if(f!=null)for(File x:f)apagarRecursivo(x);}arquivo.delete();}
}
