package com.vokie.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VokieDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VokieDatabase::class.java,
    )

    private val migrations = arrayOf(
        VokieDatabase.MIGRATION_1_2,
        VokieDatabase.MIGRATION_2_3,
        VokieDatabase.MIGRATION_3_4,
        VokieDatabase.MIGRATION_4_5,
        VokieDatabase.MIGRATION_5_6,
        VokieDatabase.MIGRATION_6_7,
        VokieDatabase.MIGRATION_7_8,
        VokieDatabase.MIGRATION_8_9,
        VokieDatabase.MIGRATION_9_10,
    )

    @Test
    fun migrate7To8PreservesLocalMessageAndCreatesCloudTables() {
        val name = "migration-7-8"
        helper.createDatabase(name, 7).apply {
            insertLegacyMessage(this, "local-v7")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 8, true, VokieDatabase.MIGRATION_7_8)
        assertEquals(1, count(db, "messages", "id = 'local-v7'"))
        assertTable(db, "cloud_user")
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrate8To9MovesLegacyTokenToMigrationField() {
        val name = "migration-8-9"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO cloud_user (id, email, jwtToken) VALUES (1, 'migration@example.test', 'header.payload.signature')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 9, true, VokieDatabase.MIGRATION_8_9)
        val cursor = db.query("SELECT legacyJwtToken FROM cloud_user WHERE id = 1")
        cursor.use {
            // jwtToken is deliberately removed by 8→9; the value is retained only
            // in the migration field until SecureCredentialStore transfers it.
            assertTrueValue(it.moveToFirst())
            assertEquals("header.payload.signature", it.getString(it.getColumnIndexOrThrow("legacyJwtToken")))
        }
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrate9To10PreservesLegacyFieldAndAddsLocalCommunicationTables() {
        val name = "migration-9-10"
        helper.createDatabase(name, 9).apply {
            execSQL("INSERT INTO cloud_user (id, email, legacyJwtToken) VALUES (1, 'migration@example.test', 'legacy-token')")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 10, true, VokieDatabase.MIGRATION_9_10)
        assertTable(db, "trusted_peers")
        assertTable(db, "pair_requests")
        assertTable(db, "conversations")
        assertTable(db, "message_deliveries")
        val cursor = db.query("SELECT legacyJwtToken FROM cloud_user WHERE id = 1")
        cursor.use {
            assertTrueValue(it.moveToFirst())
            assertEquals("legacy-token", it.getString(0))
        }
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun direct7To10PreservesLocalMessageAndCreatesAllLaterTables() {
        val name = "migration-7-10"
        helper.createDatabase(name, 7).apply {
            insertLegacyMessage(this, "direct-v7")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 10, true, *migrations.drop(6).toTypedArray())
        assertEquals(1, count(db, "messages", "id = 'direct-v7'"))
        assertTable(db, "trusted_peers")
        db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun direct8To10PreservesLegacyCredentialUntilApplicationKeystoreTransfer() {
        val name = "migration-8-10"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO cloud_user (id, email, jwtToken) VALUES (1, 'direct@example.test', 'legacy-direct-token')")
            insertLegacyMessage(this, "direct-v8")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 10, true, *migrations.drop(7).toTypedArray())
        assertEquals(1, count(db, "messages", "id = 'direct-v8'"))
        val cursor = db.query("SELECT legacyJwtToken FROM cloud_user WHERE id = 1")
        cursor.use {
            assertTrueValue(it.moveToFirst())
            assertEquals("legacy-direct-token", it.getString(0))
        }
        db.close()
        context.deleteDatabase(name)
    }

    private fun insertLegacyMessage(db: SupportSQLiteDatabase, id: String) {
        db.execSQL("""
            INSERT INTO messages (id, senderId, receiverId, timestamp, text, language, messageType,
                deliveryState, transport, hopCount, retryCount, requiresAck, lastError,
                sequenceNumber, ttlMs, priority, checksum, nextRetryAt, semanticMetadata,
                translatedText, translatedLanguage)
            VALUES (?, 'sender', 'receiver', 1000, 'local text', 'EN', 'TEXT',
                'QUEUED', NULL, 0, 0, 1, NULL, 0, 300000, 0, 0, NULL, NULL, NULL, NULL)
        """.trimIndent(), arrayOf(id))
    }

    private fun assertTable(db: SupportSQLiteDatabase, table: String) {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
        cursor.use { assertTrueValue(it.moveToFirst()) }
    }

    private fun count(db: SupportSQLiteDatabase, table: String, where: String?): Int {
        val sql = "SELECT COUNT(*) FROM $table" + if (where == null) "" else " WHERE $where"
        val cursor = db.query(sql)
        cursor.use { assertTrueValue(it.moveToFirst()); return it.getInt(0) }
    }

    private fun assertTrueValue(value: Boolean) {
        org.junit.Assert.assertTrue(value)
    }
}
