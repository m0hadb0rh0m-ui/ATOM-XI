package com.atom.aix
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

internal data class ChatLine(val role: String, val text: String)
internal class AtomDatabase(context: Context): SQLiteOpenHelper(context,"atom.db",null,1) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT)")
  db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER,role TEXT,text TEXT)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 fun createSession():Long = writableDatabase.insertOrThrow("sessions",null,ContentValues().apply{put("title","محادثة جديدة")})
 fun latestSessionId():Long? = readableDatabase.rawQuery("SELECT id FROM sessions ORDER BY id DESC LIMIT 1",null).use{if(it.moveToFirst()) it.getLong(0) else null}
 fun sessions():List<Pair<Long,String>> = readableDatabase.rawQuery("SELECT id,title FROM sessions ORDER BY id DESC",null).use{c->buildList{while(c.moveToNext()) add(c.getLong(0) to c.getString(1))}}
 fun load(id:Long):List<ChatLine> = readableDatabase.rawQuery("SELECT role,text FROM messages WHERE session_id=? ORDER BY id",arrayOf(id.toString())).use{c->buildList{while(c.moveToNext()) add(ChatLine(c.getString(0),c.getString(1)))}}
 fun add(id:Long,line:ChatLine){writableDatabase.insertOrThrow("messages",null,ContentValues().apply{put("session_id",id);put("role",line.role);put("text",line.text)})}
}
