package com.cashtable.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地数据库（本地模式）：schema 与后端 cashtable.db 完全一致，
 * 便于全功能模式激活时将本地库直接上传云端接管。
 */
class LocalDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_USERS)
        db.execSQL(CREATE_CATEGORIES)
        db.execSQL(CREATE_TRANSACTIONS)
        db.execSQL(CREATE_TXN_DEDUP_INDEX)
        db.execSQL(CREATE_CATEGORY_RULES)
        db.execSQL(CREATE_CATEGORY_RULES_TEMP)
        db.execSQL(CREATE_IMPORT_RECORDS)
        seedCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 本地库：升级时重建（简单策略；正式数据迁移后续按需补充）
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS categories")
        db.execSQL("DROP TABLE IF EXISTS transactions")
        db.execSQL("DROP TABLE IF EXISTS category_rules")
        db.execSQL("DROP TABLE IF EXISTS category_rules_temp")
        db.execSQL("DROP TABLE IF EXISTS import_records")
        onCreate(db)
    }

    /** 初始分类（与后端 seed.py 一致）。 */
    private fun seedCategories(db: SQLiteDatabase) {
        val expense = listOf(
            "三餐", "零食", "衣服", "交通", "旅游", "孩子", "宠物", "水电网燃气",
            "日用品", "家具家电", "住房", "人情往来", "健身运动", "医疗", "娱乐", "其他",
        )
        val income = listOf("退款", "工资", "其他")
        expense.forEachIndexed { i, name ->
            db.insert("categories", null, ContentValues().apply {
                put("name", name); put("kind", "expense"); put("sort", i + 1)
            })
        }
        income.forEachIndexed { i, name ->
            db.insert("categories", null, ContentValues().apply {
                put("name", name); put("kind", "income"); put("sort", i + 1)
            })
        }
    }

    companion object {
        const val DB_NAME = "cashtable.db"
        const val DB_VERSION = 1

        private const val CREATE_USERS = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""

        private const val CREATE_CATEGORIES = """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                parent_id INTEGER REFERENCES categories(id),
                kind TEXT NOT NULL CHECK (kind IN ('expense', 'income')),
                icon TEXT NOT NULL DEFAULT '',
                sort INTEGER NOT NULL DEFAULT 0
            )"""

        private const val CREATE_TRANSACTIONS = """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount REAL NOT NULL,
                category_id INTEGER REFERENCES categories(id),
                happened_at TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                created_by INTEGER REFERENCES users(id),
                content TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT 'manual',
                external_id TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""

        private const val CREATE_TXN_DEDUP_INDEX = """
            CREATE UNIQUE INDEX idx_txn_dedup ON transactions (external_id)
            WHERE external_id <> ''"""

        private const val CREATE_CATEGORY_RULES = """
            CREATE TABLE category_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                keyword TEXT NOT NULL,
                category_id INTEGER NOT NULL REFERENCES categories(id),
                source TEXT NOT NULL DEFAULT 'manual',
                usage_count INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""

        private const val CREATE_CATEGORY_RULES_TEMP = """
            CREATE TABLE category_rules_temp (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                keyword TEXT NOT NULL,
                category_id INTEGER NOT NULL REFERENCES categories(id),
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""

        private const val CREATE_IMPORT_RECORDS = """
            CREATE TABLE import_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'done',
                total INTEGER NOT NULL DEFAULT 0,
                imported INTEGER NOT NULL DEFAULT 0,
                skipped INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
    }
}
