package app.filmmerkezi.tv.probe;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** No AndroidX/Kotlin runtime references: can inspect a shrunk APK with its real signing certificate. */
public final class PreservationProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        new Thread(() -> {
            Bundle result=new Bundle();
            try {
                Context c=getTargetContext(); JSONObject snapshot=new JSONObject();
                for(String name:Arrays.asList("film-library.db","film-series.db","film-playback-preferences.db")) {
                    File path=c.getDatabasePath(name);JSONObject tables=new JSONObject();snapshot.put(name,tables);
                    if(path.exists())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(path.getPath(),null,SQLiteDatabase.OPEN_READONLY);
                        Cursor cursor=db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' ORDER BY name",null)) {
                        while(cursor.moveToNext()) {
                            String table=cursor.getString(0);if(!table.matches("[a-zA-Z_]+"))throw new IllegalStateException("Unexpected table");
                            List<String> values=new ArrayList<>();
                            try(Cursor r=db.rawQuery("SELECT * FROM "+table,null)) {
                                while(r.moveToNext()) {
                                    List<String> fields=new ArrayList<>();for(int i=0;i<r.getColumnCount();i++)fields.add(r.isNull(i)?"":r.getString(i));
                                    values.add(join("|",fields));
                                }
                            }
                            Collections.sort(values);tables.put(table,new JSONObject().put("rows",values.size()).put("digest",digest(join("\n",values))));
                        }
                    }
                }
                for(String name:Arrays.asList("film-settings","interface-preferences")) {
                    List<String> entries=new ArrayList<>();
                    for(Map.Entry<String,?> e:new TreeMap<>(c.getSharedPreferences(name,0).getAll()).entrySet()) {
                        Object v=e.getValue();String value=String.valueOf(v);
                        if(v instanceof Set<?>) {List<String> sorted=new ArrayList<>();for(Object item:(Set<?>)v)sorted.add(String.valueOf(item));Collections.sort(sorted);value=join(", ",sorted);}
                        entries.add(e.getKey()+"="+value);
                    }
                    snapshot.put(name,digest(join("\n",entries)));
                }
                File before=new File(c.getExternalFilesDir(null),"settings110-upgrade-before.json");
                if("before".equals(args.getString("phase")))write(before,snapshot.toString(2));
                else {
                    JSONObject previous=new JSONObject(read(before));
                    write(new File(c.getExternalFilesDir(null),"settings110-upgrade-after.json"),snapshot.toString(2));
                    if(!previous.toString().equals(snapshot.toString()))throw new AssertionError("Stored rows/preferences changed");
                }
                result.putString("preservation","PASS");finish(-1,result);
            }catch(Throwable e){result.putString("preservation","FAIL");result.putString("failure",e.toString());finish(1,result);}
        },"PreservationProbe").start();
    }
    private static String digest(String text)throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();
    }
    private static String join(String separator,List<String> values){StringBuilder out=new StringBuilder();for(String value:values){if(out.length()>0)out.append(separator);out.append(value);}return out.toString();}
    private static void write(File file,String value)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));}}
    private static String read(File file)throws IOException{try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
}
