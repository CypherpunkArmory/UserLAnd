package tech.ula.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory
import android.arch.persistence.room.Room
import android.arch.persistence.room.testing.MigrationTestHelper
import android.database.sqlite.SQLiteDatabase
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.model.repositories.UlaDatabase
import tech.ula.model.repositories.Migration1To2
import tech.ula.model.repositories.Migration2To3
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    private val migrationHelper = MigrationHelper()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        UlaDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory())

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        val db = helper.createDatabase(TEST_DB, 1)

        insertVersion1Filesystem(1, "firstFs", db)
        insertVersion1Session(1, "firstSession", db)

        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2())

        val migratedDb = getMigratedDatabase()
        val fs = migratedDb.filesystemDao().getFilesystemByName("firstFs")
        val session = migratedDb.sessionDao().getSessionByName("firstSession")

        assertFalse(fs.isDownloaded)
        assertFalse(session.isExtracted)
        assert(session.lastUpdated == 0L)
        assert(session.bindings == "")
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val db = helper.createDatabase(TEST_DB, 2)

        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration2To3())
    }

    private fun getMigratedDatabase(): UlaDatabase {
        val db = Room.databaseBuilder(InstrumentationRegistry.getTargetContext(),
                UlaDatabase::class.java, TEST_DB)
                .addMigrations(Migration1To2(), Migration2To3())
                .build()

        helper.closeWhenFinished(db)
        return db
    }

    private fun insertVersion1Filesystem(id: Long, name: String, db: SupportSQLiteDatabase) {
        val filesystem = migrationHelper.getVersion1Filesystem(id, name)
        db.insert("filesystem", SQLiteDatabase.CONFLICT_REPLACE, filesystem)
    }

    private fun insertVersion1Session(id: Long, name: String, db: SupportSQLiteDatabase) {
        val session = migrationHelper.getVersion1Session(id, name)
        db.insert("session", SQLiteDatabase.CONFLICT_REPLACE, session)
    }
}