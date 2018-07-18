package tech.ula.model

import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory
import android.arch.persistence.room.Room
import android.arch.persistence.room.testing.MigrationTestHelper
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.*

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

//        val oldFilesystemColumns = "id, name, distributionType, archType, defaultUsername, defaultPassword, location, dateCreated, realRoot"
//        val oldFilesystemValues = "1, 'firstFs', 'dummy', 'dummy', 'dummy', 'dummy', 'dummy', '${Date(0L)}', 0"
//
//        val oldSessionColumns = "id, name, filesystemId, filesystemName, active, username, password, geometry, serviceType, clientType, port, pid, startupScript, runAtDeviceStartup, initialCommand"
//        val oldSessionValues = "1, 'firstSession', 1, 'firstFs', 0, 'dummy', 'dummy', 'dummy', 'dummy', 'dummy', 0, 0, 'dummy', 0, 'dummy'"
//
//        db.execSQL("INSERT INTO filesystem ($oldFilesystemColumns) VALUES ($oldFilesystemValues)")
//        db.execSQL("INSERT INTO session ($oldSessionColumns) VALUES ($oldSessionValues)")

        db.execSQL("INSERT INTO filesystem (id, name, distributionType, archType, defaultUsername, defaultPassword, location, dateCreated, realRoot) VALUES (1, 'firstFs', 'dummy', 'dummy', 'dummy', 'dummy', 'dummy', '${Date(0L)}', 0)")

        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2())

        val migratedDb = getMigratedDatabase1To2()
        val fs = migratedDb.filesystemDao().getFilesystemByName("firstFs")
        val fss = migratedDb.filesystemDao().getAllFilesystems().value
//        val session = migratedDb.sessionDao().getSessionByName("firstSession")

        assertFalse(fs.isDownloaded)
//        assertFalse(session.isExtracted)
//        assert(session.lastUpdated == 0L)
    }

    private fun getMigratedDatabase1To2(): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(),
                AppDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(Migration1To2())
                .build()

        helper.closeWhenFinished(db)
        return db
    }
}