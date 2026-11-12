package com.atom.aix
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

internal data class ChatLine(val role: String, val text: String)
internal class AtomDatabase(context: Context): SQLiteOpenHelper(context,"atom.db",null,2) {
 override fun onCreate(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT,pinned INTEGER NOT NULL DEFAULT 0)")
  db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT,session_id INTEGER,role TEXT,text TEXT)")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){if(oldVersion<2)db.execSQL("ALTER TABLE sessions ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")}
 fun createSession():Long = writableDatabase.insertOrThrow("sessions",null,ContentValues().apply{put("title","محادثة جديدة")})
 fun latestSessionId():Long? = readableDatabase.rawQuery("SELECT id FROM sessions ORDER BY id DESC LIMIT 1",null).use{if(it.moveToFirst()) it.getLong(0) else null}
 fun sessions():List<Pair<Long,String>> = readableDatabase.rawQuery("SELECT id,title FROM sessions ORDER BY pinned DESC,id DESC",null).use{c->buildList{while(c.moveToNext()) add(c.getLong(0) to c.getString(1))}}
 fun renameSession(id:Long,title:String){require(title.isNotBlank());writableDatabase.update("sessions",ContentValues().apply{put("title",title.take(100))},"id=?",arrayOf(id.toString()))}
 fun togglePin(id:Long){writableDatabase.execSQL("UPDATE sessions SET pinned=CASE pinned WHEN 0 THEN 1 ELSE 0 END WHERE id=?",arrayOf(id))}
 fun isPinned(id:Long):Boolean=readableDatabase.rawQuery("SELECT pinned FROM sessions WHERE id=?",arrayOf(id.toString())).use{it.moveToFirst()&&it.getInt(0)==1}
 fun searchSessions(query:String):List<Pair<Long,String>> = readableDatabase.rawQuery("SELECT DISTINCT s.id,s.title FROM sessions s LEFT JOIN messages m ON m.session_id=s.id WHERE s.title LIKE ? OR m.text LIKE ? ORDER BY s.pinned DESC,s.id DESC",arrayOf("%$query%","%$query%")).use{c->buildList{while(c.moveToNext())add(c.getLong(0) to c.getString(1))}}
 fun messageCount():Int=readableDatabase.rawQuery("SELECT COUNT(*) FROM messages",null).use{if(it.moveToFirst())it.getInt(0) else 0}
 fun userMessageCount():Int=readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE role='user'",null).use{if(it.moveToFirst())it.getInt(0) else 0}
 fun load(id:Long):List<ChatLine> = readableDatabase.rawQuery("SELECT role,text FROM messages WHERE session_id=? ORDER BY id",arrayOf(id.toString())).use{c->buildList{while(c.moveToNext()) add(ChatLine(c.getString(0),c.getString(1)))}}
 fun clearAll(){writableDatabase.execSQL("DELETE FROM messages");writableDatabase.execSQL("DELETE FROM sessions")}
 fun deleteSession(id:Long){writableDatabase.delete("messages","session_id=?",arrayOf(id.toString()));writableDatabase.delete("sessions","id=?",arrayOf(id.toString()))}
 fun add(id:Long,line:ChatLine){writableDatabase.insertOrThrow("messages",null,ContentValues().apply{put("session_id",id);put("role",line.role);put("text",line.text)})}
}
