package tech.ula.model

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory
import android.arch.persistence.room.Room
import android.arch.persistence.room.testing.MigrationTestHelper
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory())

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        val db = helper.createDatabase(TEST_DB, 1)

        insertVersion1Filesystem(1, "firstFs", db)
        insertVersion1Session(1, "firstSession", db)

        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2())

        val migratedDb = getMigratedDatabase1To2()
        val fs = migratedDb.filesystemDao().getFilesystemByName("firstFs")
        val session = migratedDb.sessionDao().getSessionByName("firstSession")

        assertFalse(fs.isDownloaded)
        assertFalse(session.isExtracted)
        assert(session.lastUpdated == 0L)
        assert(session.bindings == "")
    }

    private fun getMigratedDatabase1To2(): AppDatabase {
        val db = Room.databaseBuilder(InstrumentationRegistry.getTargetContext(),
                AppDatabase::class.java, TEST_DB)
                .addMigrations(Migration1To2())
                .build()

        helper.closeWhenFinished(db)
        return db
    }

    private fun insertVersion1Filesystem(id: Long, name: String, db: SupportSQLiteDatabase) {
        val filesystemValues = ContentValues()
        filesystemValues.put("id", id)
        filesystemValues.put("name", name)
        filesystemValues.put("distributionType", "dummy")
        filesystemValues.put("archType", "dummy")
        filesystemValues.put("defaultUsername", "dummy")
        filesystemValues.put("defaultPassword", "dummy")
        filesystemValues.put("location", "dummy")
        filesystemValues.put("dateCreated", "dummy")
        filesystemValues.put("realRoot", 0)
        db.insert("filesystem", SQLiteDatabase.CONFLICT_REPLACE, filesystemValues)
    }

    private fun insertVersion1Session(id: Long, name: String, db: SupportSQLiteDatabase) {
        val sessionValues = ContentValues()
        sessionValues.put("id", id)
        sessionValues.put("name", name)
        sessionValues.put("filesystemId", 1)
        sessionValues.put("filesystemName", "firstFs")
        sessionValues.put("active", 0)
        sessionValues.put("username", "dummy")
        sessionValues.put("password", "dummy")
        sessionValues.put("geometry", "dummy")
        sessionValues.put("serviceType", "dummy")
        sessionValues.put("clientType", "dummy")
        sessionValues.put("port", 0)
        sessionValues.put("pid", 0)
        sessionValues.put("startupScript", "dummy")
        sessionValues.put("runAtDeviceStartup", 0)
        sessionValues.put("initialCommand", "dummy")
        db.insert("session", SQLiteDatabase.CONFLICT_REPLACE, sessionValues)
    }
}