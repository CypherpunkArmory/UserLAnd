package tech.ula.model

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import android.database.sqlite.SQLiteDatabase
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // ktlint-disable no-wildcard-imports
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.ula.androidTestHelpers.blockingObserve
import tech.ula.model.entities.Filesystem
import tech.ula.model.entities.Session
import tech.ula.model.repositories.* // ktlint-disable no-wildcard-imports
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    private val migrationHelper = MigrationTestHelper()

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

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

        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migration1To2(), Migration2To3(), Migration3To4(), Migration4To5())
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        val db = helper.createDatabase(TEST_DB, 2)

        insertVersion2Filesystem(1, "fs", db)
        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 3, true, Migration2To3())

        val migratedDb = getMigratedDatabase()

        // Added isAppsFilesystem field.
        val fs = migratedDb.filesystemDao().getFilesystemByName("fs")
        assertFalse(fs.isAppsFilesystem)

        // Removed filesystem name index
        val fs2 = Filesystem(2, "fs")
        runBlocking { migratedDb.filesystemDao().insertFilesystem(fs2) }
        val filesystems = migratedDb.filesystemDao().getAllFilesystems().blockingObserve()!!
        assertEquals(2, filesystems.size)

        // Remove session name index and added isAppsSession field.
        val session1 = Session(1, name = "test", filesystemId = 1, filesystemName = "fs")
        val session2 = Session(2, name = "test", filesystemId = 1, filesystemName = "fs", isAppsSession = true)
        runBlocking {
            migratedDb.sessionDao().insertSession(session1)
            migratedDb.sessionDao().insertSession(session2)
        }

        val sessions = migratedDb.sessionDao().getAllSessions().blockingObserve()!!
        assertEquals(2, sessions.size)
        assertTrue(sessions.contains(session1))
        assertTrue(sessions.contains(session2))

        val returnedSession1 = sessions[0]
        val returnedSession2 = sessions[1]
        assertEquals(session1, returnedSession1)
        assertEquals(session2, returnedSession2)
        assertFalse(returnedSession1.isAppsSession)
        assertTrue(returnedSession2.isAppsSession)
        assertEquals(fs.defaultVncPassword, "userland")
        assertEquals(session1.vncPassword, "")
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4() {
        helper.createDatabase(TEST_DB, 3)

        helper.runMigrationsAndValidate(TEST_DB, 4, true, Migration3To4())
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        helper.createDatabase(TEST_DB, 4)

        helper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4To5())
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        helper.createDatabase(TEST_DB, 5)

        helper.runMigrationsAndValidate(TEST_DB, 6, true, Migration5To6())
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        helper.createDatabase(TEST_DB, 6)

        helper.runMigrationsAndValidate(TEST_DB, 7, true, Migration6To7())
    }

    private fun getMigratedDatabase(): UlaDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, UlaDatabase::class.java, TEST_DB)
                .addMigrations(
                        Migration1To2(),
                        Migration2To3(),
                        Migration3To4(),
                        Migration4To5(),
                        Migration5To6(),
                        Migration6To7())
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

    private fun insertVersion2Filesystem(id: Long, name: String, db: SupportSQLiteDatabase) {
        val filesystem = migrationHelper.getVersion2Filesystem(id, name)
        db.insert("filesystem", SQLiteDatabase.CONFLICT_REPLACE, filesystem)
    }
}