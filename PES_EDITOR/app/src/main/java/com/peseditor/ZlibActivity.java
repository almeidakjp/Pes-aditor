package com.peseditor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

public class ZlibActivity extends BaseActivity {
    private static final int PICK_BIN=101, PICK_REPLACEMENT=102, PERMISSION=104;
    private TextView status, fileLabel, sizeLabel, countLabel;
    private Button exportButton, importButton;
    private Uri binUri;
    private File binFile;
    private String binName;
    private byte[] original;
    private ArrayList<Block> blocks;
    private int selectedBlock=0;
    private boolean busy=false;
    
    private static class Block { int start,end,used; byte[] plain; Block(int s,int e,int u,byte[] p){start=s;end=e;used=u;plain=p;} }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private android.graphics.drawable.GradientDrawable rounded(int fill,int radius,int stroke){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;
    }
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setTypeface(null,1);b.setPadding(dp(3),0,dp(3),0);b.setBackground(rounded(0xff242629,18,0xff393b40));return b;}
    private LinearLayout cell(String label){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setGravity(Gravity.CENTER_VERTICAL);c.addView(text(label,11,0xff9b9da2,true));TextView v=text("-",14,Color.WHITE,true);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(v);return c;}
    private void updateSummary(){
        fileLabel.setText(binName==null?"-":binName);
        sizeLabel.setText(original==null?"-":String.format(java.util.Locale.getDefault(),"%.1f KB",original.length/1024.0));
        countLabel.setText(blocks==null?"-":Integer.toString(blocks.size()));
        exportButton.setEnabled(original!=null&&!busy);importButton.setEnabled(original!=null&&!busy);
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(24),dp(18),dp(14));root.setBackgroundColor(0xff101112);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("ZLIB TOOL",22,Color.WHITE,true);title.setSingleLine(true);top.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
        Button open=button("ABRIR");top.addView(open,new LinearLayout.LayoutParams(dp(66),dp(44)));
        exportButton=button("EXPORTAR");LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(70),dp(44));ep.leftMargin=dp(6);top.addView(exportButton,ep);
        importButton=button("IMPORTAR");LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(70),dp(44));ip.leftMargin=dp(6);top.addView(importButton,ip);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(52));tp.bottomMargin=dp(10);root.addView(top,tp);
        LinearLayout summary=new LinearLayout(this);summary.setPadding(dp(12),dp(10),dp(12),dp(10));summary.setBackground(rounded(0xff191b1d,16,0xff2c2f33));
        LinearLayout c1=cell("ARQUIVO:");fileLabel=(TextView)c1.getChildAt(1);summary.addView(c1,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout c2=cell("TAMANHO:");sizeLabel=(TextView)c2.getChildAt(1);summary.addView(c2,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout c3=cell("BLOCOS:");countLabel=(TextView)c3.getChildAt(1);summary.addView(c3,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.bottomMargin=dp(10);root.addView(summary,sp);
        LinearLayout empty=new LinearLayout(this);root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
        status=text("Abra um BIN para visualizar os blocos.",13,0xffa8a9ad,false);status.setPadding(dp(4),dp(8),dp(4),dp(2));root.addView(status);
        setContentView(root);updateSummary();
        open.setOnClickListener(v->{if(!busy)pick(PICK_BIN);});
        exportButton.setOnClickListener(v->{if(ready()&&ensureStorage())work(()->exportBlocks());});
        importButton.setOnClickListener(v->{if(ready()&&ensureStorage())pick(PICK_REPLACEMENT);});
    }
    private boolean ready(){if(busy)return false;if(original==null||blocks==null){status.setText("Selecione primeiro um BIN válido.");return false;}return true;}
    private void pick(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");if(request==PICK_REPLACEMENT)i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,request);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==PICK_BIN){
            Uri uri=data.getData();if(uri==null)return;
            binUri=uri;binName=nameOf(uri);binFile=resolveFile(uri);
            work(()->{byte[] bytes=read(uri);ArrayList<Block> found=parse(bytes);
                runOnUiThread(()->{original=bytes;blocks=found;status.setText(binName+" • "+found.size()+" blocos encontrados");updateSummary();});});
        }else if(request==PICK_REPLACEMENT){
            if(!ready())return;
            ArrayList<Uri> chosen=new ArrayList<>();
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++)
                    chosen.add(data.getClipData().getItemAt(i).getUri());
            }else if(data.getData()!=null)chosen.add(data.getData());
            if(chosen.isEmpty()){message("Nenhum arquivo selecionado.");return;}
            work(()->importBlocks(chosen));
        }
    }
    private boolean ensureStorage(){
        if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()){
            status.setText("Autorize uma vez o acesso a todos os arquivos nas configurações do Android.");
            try{Intent i=new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()));startActivityForResult(i,PERMISSION);}catch(Exception e){startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
            return false;
        }
        if(binFile==null||!binFile.isFile()){
            status.setText("Não foi possível localizar o BIN no armazenamento. Selecione um BIN em Download ou outra pasta interna acessível.");return false;
        }
        return true;
    }
    private File resolveFile(Uri uri){
        try{
            if("file".equals(uri.getScheme()))return new File(uri.getPath());
            String id=DocumentsContract.getDocumentId(uri);
            if(id.startsWith("primary:"))return new File(Environment.getExternalStorageDirectory(),id.substring(8));
            if(id.startsWith("raw:"))return new File(id.substring(4));
            if(id.startsWith("home:"))return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),id.substring(5));
            // Alguns seletores fornecem um identificador numérico do MediaStore.
            if(id.matches("[0-9]+")){
                try(Cursor c=getContentResolver().query(uri,new String[]{"_data"},null,null,null)){
                    if(c!=null&&c.moveToFirst()&&!c.isNull(0))return new File(c.getString(0));
                }
            }
        }catch(Exception ignored){}
        try(Cursor c=getContentResolver().query(uri,new String[]{"_data"},null,null,null)){
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))return new File(c.getString(0));
        }catch(Exception ignored){}
        return null;
    }
    private String nameOf(Uri uri){String result="arquivo.bin";try(Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())result=c.getString(0);}catch(Exception ignored){}return result;}
    private byte[] read(Uri uri)throws Exception{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("Não foi possível abrir o arquivo");byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}}
    private static int u32(byte[] d,int o){return ByteBuffer.wrap(d,o,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    private static ArrayList<Block> parse(byte[] d)throws Exception{if(d.length<112)throw new IOException("BIN muito pequeno");long count=Integer.toUnsignedLong(u32(d,32));if(count<1||count>1024||40+4*count>d.length)throw new IOException("Tabela inválida");int[] starts=new int[(int)count];for(int i=0;i<count;i++){long v=Integer.toUnsignedLong(u32(d,40+i*4))+64;if(v>=d.length||v>Integer.MAX_VALUE)throw new IOException("Offset inválido");starts[i]=(int)v;if(i>0&&starts[i]<=starts[i-1])throw new IOException("Offsets fora de ordem");}if(starts[0]<40+4*count)throw new IOException("Cabeçalho inválido");ArrayList<Block> result=new ArrayList<>();for(int i=0;i<count;i++){int s=starts[i],e=i+1<count?starts[i+1]:d.length;Inflater inf=new Inflater();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int used;try{inf.setInput(d,s,e-s);while(!inf.finished()){int n=inf.inflate(buf);if(n>0){out.write(buf,0,n);if(out.size()>100*1024*1024)throw new IOException("Bloco excede 100 MB");}else if(inf.needsInput()||inf.needsDictionary())throw new IOException("Bloco incompleto: "+i);else throw new IOException("Descompactação parou: "+i);}used=(e-s)-inf.getRemaining();}catch(DataFormatException ex){throw new IOException("zlib inválido no bloco "+i,ex);}finally{inf.end();}result.add(new Block(s,e,used,out.toByteArray()));}return result;}
    private void exportBlocks()throws Exception{
        if(binFile==null||!binFile.isFile())throw new IOException("BIN não encontrado");
        File parent=binFile.getParentFile();
        for(int i=0;i<blocks.size();i++){
            File target=new File(parent,binName+"_"+String.format(java.util.Locale.US,"%03d",i));
            replaceFileSafely(target,blocks.get(i).plain);
        }
        message("EXPORT concluído: "+blocks.size()+" arquivos atualizados na pasta do BIN");
    }
    private void writeFile(File file,byte[] data)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){out.write(data);out.getFD().sync();}
    }
    // Grava em arquivo temporário antes de substituir o destino, evitando truncamento parcial.
    private void replaceFileSafely(File target,byte[] data)throws Exception{
        File tmp=File.createTempFile(".zlib_export_", ".tmp",target.getParentFile());
        try{
            writeFile(tmp,data);
            try{
                java.nio.file.Files.move(tmp.toPath(),target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }catch(java.nio.file.AtomicMoveNotSupportedException ex){
                java.nio.file.Files.move(tmp.toPath(),target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{if(tmp.exists())tmp.delete();}
    }
    private static byte[] compress(byte[] plain)throws Exception{
        Deflater def=new Deflater(9);def.setInput(plain);def.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];
        try{while(!def.finished()){int n=def.deflate(buf);
            if(n==0)throw new IOException("Falha na compressão");out.write(buf,0,n);}}
        finally{def.end();}
        return out.toByteArray();
    }
    private void importBlocks(ArrayList<Uri> uris)throws Exception{
        // Valida TODOS os arquivos antes de tocar no BIN.
        TreeMap<Integer,byte[]> replacements=new TreeMap<>();
        String prefix=binName+"_";
        for(Uri uri:uris){
            String name=nameOf(uri);
            if(!name.startsWith(prefix)||!name.substring(prefix.length()).matches("[0-9]{3}"))
                throw new IOException("Nome inválido: "+name+" (esperado "+binName+"_000)");
            int index=Integer.parseInt(name.substring(prefix.length()));
            if(index<0||index>=blocks.size())throw new IOException("Bloco inexistente: "+name);
            if(replacements.containsKey(index))throw new IOException("Bloco repetido: "+name);
            byte[] plain=read(uri);
            if(plain.length!=blocks.get(index).plain.length)
                throw new IOException("Tamanho diferente em "+name+": "+plain.length+" / "+blocks.get(index).plain.length);
            replacements.put(index,plain);
        }
        if(replacements.isEmpty())throw new IOException("Nenhum bloco selecionado");
        TreeMap<Integer,byte[]> compressed=new TreeMap<>();
        for(Map.Entry<Integer,byte[]> entry:replacements.entrySet())
            compressed.put(entry.getKey(),compress(entry.getValue()));
        ByteArrayOutputStream rebuilt=new ByteArrayOutputStream();
        rebuilt.write(original,0,blocks.get(0).start);
        int[] newStarts=new int[blocks.size()];
        for(int i=0;i<blocks.size();i++){
            Block part=blocks.get(i);newStarts[i]=rebuilt.size();
            if(compressed.containsKey(i)){
                rebuilt.write(compressed.get(i));
                rebuilt.write(original,part.start+part.used,part.end-part.start-part.used);
            }else rebuilt.write(original,part.start,part.end-part.start);
        }
        byte[] output=rebuilt.toByteArray();
        for(int i=0;i<newStarts.length;i++){
            int offset=newStarts[i]-64,pos=40+4*i;
            output[pos]=(byte)offset;output[pos+1]=(byte)(offset>>>8);
            output[pos+2]=(byte)(offset>>>16);output[pos+3]=(byte)(offset>>>24);
        }
        ArrayList<Block> check=parse(output);
        for(int i=0;i<blocks.size();i++){
            byte[] expected=replacements.containsKey(i)?replacements.get(i):blocks.get(i).plain;
            if(!Arrays.equals(check.get(i).plain,expected))
                throw new IOException("Verificação falhou no bloco "+i);
        }
        if(binFile==null||!binFile.isFile())throw new IOException("BIN original não encontrado");
        byte[] current=java.nio.file.Files.readAllBytes(binFile.toPath());
        if(!Arrays.equals(current,original))
            throw new IOException("O BIN mudou desde ABRIR. Abra novamente antes de importar.");
        File backup=new File(binFile.getParentFile(),binName+".bak");
        if(!backup.exists())replaceFileSafely(backup,current);
        try{
            replaceFileSafely(binFile,output);
            if(!Arrays.equals(java.nio.file.Files.readAllBytes(binFile.toPath()),output))
                throw new IOException("Verificação de gravação falhou");
        }catch(Exception ex){
            try{replaceFileSafely(binFile,current);}
            catch(Exception rollback){throw new IOException("Falha ao restaurar BIN! Backup: "+backup.getName(),ex);}
            throw ex;
        }
        original=output;blocks=check;
        message("IMPORT concluído: "+replacements.size()+" bloco(s). Backup: "+backup.getName());
    }
    private interface Job{void run()throws Exception;}
    private void work(Job job){busy=true;status.setText("Processando...");updateSummary();new Thread(()->{try{job.run();}catch(Exception ex){message("ERRO: "+ex.getMessage());}finally{runOnUiThread(()->{busy=false;updateSummary();});}}).start();}
    private void message(String msg){runOnUiThread(()->status.setText(msg));}
}
