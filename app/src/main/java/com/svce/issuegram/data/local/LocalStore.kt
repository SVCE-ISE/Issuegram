package com.svce.issuegram.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Accessed on Dispatchers.IO; SQLite transactions make queue persistence atomic. */
class LocalStore(context: Context) : SQLiteOpenHelper(context, "issuegram-v2.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cache (key TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY NOT NULL, user_id TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, media_path TEXT, failed INTEGER NOT NULL DEFAULT 0, error TEXT, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX outbox_user ON outbox(user_id, created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun cached(key: String): String? = readableDatabase.rawQuery("SELECT payload FROM cache WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun cache(key: String, payload: String) { writableDatabase.insertWithOnConflict("cache", null, ContentValues().apply { put("key", key); put("payload", payload) }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun enqueue(entry: OutboxEntry) {
        writableDatabase.insertOrThrow("outbox", null, ContentValues().apply {
            put("id", entry.id); put("user_id", entry.userId); put("kind", entry.kind); put("payload", entry.payload)
            put("media_path", entry.mediaPath); put("created_at", System.currentTimeMillis())
        })
    }
    fun entries(userId: String): List<OutboxEntry> = readableDatabase.rawQuery(
        "SELECT id,user_id,kind,payload,media_path,failed,error FROM outbox WHERE user_id=? ORDER BY created_at,id", arrayOf(userId)).use { cursor ->
        buildList { while (cursor.moveToNext()) add(OutboxEntry(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5) != 0, cursor.getString(6))) }
    }
    fun completed(id: String) { writableDatabase.delete("outbox", "id=?", arrayOf(id)) }
    fun failed(id: String, error: String) { writableDatabase.update("outbox", ContentValues().apply { put("failed", 1); put("error", error) }, "id=?", arrayOf(id)) }
    fun retry(userId: String) { writableDatabase.update("outbox", ContentValues().apply { put("failed", 0); putNull("error") }, "user_id=?", arrayOf(userId)) }
}

data class OutboxEntry(val id: String, val userId: String, val kind: String, val payload: String, val mediaPath: String? = null, val failed: Boolean = false, val error: String? = null)
