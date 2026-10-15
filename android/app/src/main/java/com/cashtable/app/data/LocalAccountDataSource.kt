package com.cashtable.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

/** 本地数据源（本地模式）：直接操作 App 自带 sqlite（schema 同后端）。 */
class LocalAccountDataSource(context: Context) {

    private val dbHelper = LocalDbHelper(context.applicationContext)

    // ---------- 记账人 ----------

    fun loadUsers(): List<UserDto> = dbHelper.readableDatabase.use { db ->
        db.rawQuery("SELECT id, name, created_at FROM users ORDER BY id", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(UserDto(id = c.getLong(0).toInt(), name = c.getString(1), created_at = c.getString(2)))
                }
            }
        }
    }

    fun createUser(name: String): UserDto = dbHelper.writableDatabase.use { db ->
        val id = db.insertOrThrow(
            "users", null,
            ContentValues().apply { put("name", name.trim()) },
        )
        val created = db.rawQuery(
            "SELECT id, name, created_at FROM users WHERE id = ?", arrayOf(id.toString()),
        ).use { c ->
            c.moveToFirst()
            UserDto(id = c.getLong(0).toInt(), name = c.getString(1), created_at = c.getString(2))
        }
        created
    }

    fun deleteUser(userId: Int) {
        dbHelper.writableDatabase.use { db ->
            db.execSQL("UPDATE transactions SET created_by = NULL WHERE created_by = ?", arrayOf(userId))
            db.delete("users", "id = ?", arrayOf(userId.toString()))
        }
    }

    // ---------- 分类 ----------

    fun loadCategories(kind: String? = null): List<CategoryDto> = dbHelper.readableDatabase.use { db ->
        val sql = buildString {
            append("SELECT id, name, parent_id, kind, icon, sort FROM categories")
            if (kind != null) append(" WHERE kind = ?")
            append(" ORDER BY sort, id")
        }
        val args = if (kind != null) arrayOf(kind) else null
        db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(CategoryDto(
                        id = c.getLong(0).toInt(),
                        name = c.getString(1),
                        parent_id = c.getColumnValue(2)?.let { (it as Long).toInt() },
                        kind = c.getString(3),
                        icon = c.getString(4),
                        sort = c.getInt(5),
                    ))
                }
            }
        }
    }

    fun createCategory(name: String, kind: String, icon: String, parentId: Int?): CategoryDto =
        dbHelper.writableDatabase.use { db ->
            val maxSort = db.rawQuery(
                "SELECT COALESCE(MAX(sort), 0) FROM categories WHERE kind = ?",
                arrayOf(kind),
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            val id = db.insertOrThrow(
                "categories", null,
                ContentValues().apply {
                    put("name", name.trim())
                    put("kind", kind)
                    put("icon", icon)
                    put("sort", maxSort + 1)
                    if (parentId != null) put("parent_id", parentId)
                },
            )
            CategoryDto(
                id = id.toInt(), name = name.trim(), parent_id = parentId,
                kind = kind, icon = icon, sort = maxSort + 1,
            )
        }

    fun updateCategory(id: Int, name: String) {
        dbHelper.writableDatabase.use { db ->
            db.execSQL("UPDATE categories SET name = ? WHERE id = ?", arrayOf<Any?>(name.trim(), id))
        }
    }

    /** 删除分类：一级分类级联删其全部二级分类；关联账单的 category_id 置空（保留账单）。 */
    fun deleteCategory(id: Int) {
        dbHelper.writableDatabase.use { db ->
            val childIds = mutableListOf<Long>()
            db.rawQuery(
                "SELECT id FROM categories WHERE parent_id = ?", arrayOf(id.toString())
            ).use { c -> while (c.moveToNext()) childIds.add(c.getLong(0)) }
            val allIds = childIds + id
            val placeholders = allIds.joinToString(",") { "?" }
            val args = allIds.map { it.toString() }.toTypedArray()
            db.execSQL("UPDATE transactions SET category_id = NULL WHERE category_id IN ($placeholders)", args)
            db.execSQL("DELETE FROM categories WHERE id IN ($placeholders)", args)
        }
    }

    // ---------- 账单 ----------

    fun loadTransactions(
        month: String?,
        categoryId: Int?,
        keyword: String?,
        createdBy: Int?,
        page: Int,
        pageSize: Int,
    ): TransactionListDto = dbHelper.readableDatabase.use { db ->
        val (where, args) = buildWhere(month, categoryId, keyword, createdBy)
        val total = db.rawQuery("SELECT COUNT(*) FROM transactions t$where", args).use {
            it.moveToFirst(); it.getInt(0)
        }
        val offset = (page - 1) * pageSize
        val items = db.rawQuery(
            "SELECT t.id, t.amount, t.category_id, c.name, t.happened_at, t.created_at, " +
                "t.created_by, t.content, t.source, t.external_id FROM transactions t " +
                "LEFT JOIN categories c ON c.id = t.category_id$where " +
                "ORDER BY t.happened_at DESC LIMIT $pageSize OFFSET $offset",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(TransactionDto(
                        id = c.getLong(0).toInt(),
                        amount = c.getDouble(1),
                        category_id = c.getColumnValue(2)?.let { (it as Long).toInt() },
                        category_name = c.getColumnValue(3) as? String,
                        happened_at = c.getString(4),
                        created_at = c.getString(5),
                        created_by = c.getColumnValue(6)?.let { (it as Long).toInt() },
                        content = c.getString(7),
                        source = c.getString(8),
                        external_id = c.getString(9),
                    ))
                }
            }
        }
        TransactionListDto(total = total, items = items)
    }

    fun createTransaction(
        amount: Double, categoryId: Int?, happenedAt: String, content: String,
        createdBy: Int?, source: String, externalId: String,
    ): TransactionDto = dbHelper.writableDatabase.use { db ->
        val id = db.insertOrThrow(
            "transactions", null,
            ContentValues().apply {
                put("amount", amount)
                if (categoryId != null) put("category_id", categoryId)
                put("happened_at", happenedAt)
                if (createdBy != null) put("created_by", createdBy)
                put("content", content.trim())
                put("source", source)
                put("external_id", externalId)
            },
        )
        TransactionDto(
            id = id.toInt(), amount = amount, category_id = categoryId,
            category_name = categoryId?.let { cid -> categoryName(db, cid) },
            happened_at = happenedAt, created_at = nowLocal(), created_by = createdBy,
            content = content.trim(), source = source, external_id = externalId,
        )
    }

    fun updateTransaction(
        id: Int, amount: Double, categoryId: Int?, happenedAt: String, content: String,
    ) {
        dbHelper.writableDatabase.use { db ->
            db.update(
                "transactions",
                ContentValues().apply {
                    put("amount", amount)
                    if (categoryId != null) put("category_id", categoryId) else putNull("category_id")
                    put("happened_at", happenedAt)
                    put("content", content.trim())
                    put("updated_at", nowLocal())
                },
                "id = ?", arrayOf(id.toString()),
            )
        }
    }

    fun deleteTransaction(id: Int) {
        dbHelper.writableDatabase.use { db ->
            db.delete("transactions", "id = ?", arrayOf(id.toString()))
        }
    }

    // ---------- 统计 ----------

    fun loadMonthlyStats(year: String): List<MonthlyStatDto> = dbHelper.readableDatabase.use { db ->
        db.rawQuery(
            "SELECT substr(happened_at, 1, 7) AS month, " +
                "CASE WHEN amount < 0 THEN 'expense' ELSE 'income' END AS kind, " +
                "SUM(amount) AS total FROM transactions " +
                "WHERE happened_at LIKE ? GROUP BY month, kind",
            arrayOf("$year%"),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MonthlyStatDto(month = c.getString(0), kind = c.getString(1), total = c.getDouble(2)))
                }
            }
        }
    }

    /** 导出区间内账单 CSV（关联记账人名与分类名），与后端 /api/statistics/export/ 表头一致。 */
    fun exportTransactions(start: String, end: String): String = dbHelper.readableDatabase.use { db ->
        db.rawQuery(
            "SELECT t.happened_at, t.amount, c.name, u.name, t.content, t.source, t.id, t.created_at " +
                "FROM transactions t " +
                "LEFT JOIN categories c ON c.id = t.category_id " +
                "LEFT JOIN users u ON u.id = t.created_by " +
                "WHERE t.happened_at >= ? AND t.happened_at <= ? " +
                "ORDER BY t.happened_at ASC",
            arrayOf("$start 00:00:00", "$end 23:59:59"),
        ).use { c ->
            buildString {
                append('\uFEFF') // UTF-8 BOM：Excel 打开不乱码
                appendLine("日期,类型,金额,分类,记账人,内容,来源,记录ID,创建时间")
                while (c.moveToNext()) {
                    val amount = c.getDouble(1)
                    append(
                        "${csv(c.getString(0))},${if (amount > 0) "收入" else "支出"}," +
                            "${"%.2f".format(amount)},${csv(c.getString(2) ?: "")}," +
                            "${csv(c.getString(3) ?: "")},${csv(c.getString(4) ?: "")}," +
                            "${csv(c.getString(5) ?: "")},${c.getLong(6)},${csv(c.getString(7) ?: "")}\n"
                    )
                }
            }
        }
    }

    // ---------- 本地库文件（全功能模式激活上传用） ----------

    /** 本地库文件路径（未关闭连接时需先 close）。 */
    fun dbPath(): String = dbHelper.readableDatabase.path

    /** 关闭数据库（上传/切换模式前调用，确保文件完整落盘）。 */
    fun close() {
        dbHelper.close()
    }

    // ---------- 工具 ----------

    private fun buildWhere(
        month: String?, categoryId: Int?, keyword: String?, createdBy: Int?,
    ): Pair<String, Array<String>> {
        val conds = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (month != null) { conds += "t.happened_at LIKE ?"; args += "$month%" }
        if (categoryId != null) { conds += "t.category_id = ?"; args += categoryId.toString() }
        if (!keyword.isNullOrBlank()) { conds += "t.content LIKE ?"; args += "%$keyword%" }
        if (createdBy != null) { conds += "t.created_by = ?"; args += createdBy.toString() }
        val where = if (conds.isEmpty()) "" else " WHERE " + conds.joinToString(" AND ")
        return where to args.toTypedArray()
    }

    private fun categoryName(db: android.database.sqlite.SQLiteDatabase, id: Int): String? =
        db.rawQuery("SELECT name FROM categories WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun nowLocal(): String {
        dbHelper.readableDatabase.use { db ->
            return db.rawQuery("SELECT datetime('now', 'localtime')", null).use {
                it.moveToFirst(); it.getString(0)
            }
        }
    }

    /** CSV 字段转义：含逗号/引号/换行时用引号包裹，内部引号翻倍。 */
    private fun csv(field: String): String =
        if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    private fun Cursor.getColumnValue(index: Int): Any? =
        if (isNull(index)) null else when {
            getType(index) == android.database.Cursor.FIELD_TYPE_INTEGER -> getLong(index)
            else -> getString(index)
        }
}
