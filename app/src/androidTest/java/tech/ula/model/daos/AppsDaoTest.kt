package tech.ula.model.daos

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tech.ula.model.entities.App
import tech.ula.model.repositories.UlaDatabase

@SmallTest
class AppsDaoTest {

    @get:Rule
    val instantExectutorRule = InstantTaskExecutorRule()

    private lateinit var db: UlaDatabase

    @Before
    fun initDb() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(),
                UlaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun insertApplicationAndGetByName() {
        val inserted = App(name = DEFAULT_NAME)

        db.appsDao().insertApp(inserted)
        val retrieved = db.appsDao().getAppByName(DEFAULT_NAME)

        assertNotNull(retrieved)
        assertEquals(inserted, retrieved)
    }

    companion object {
        val DEFAULT_NAME = "test"
    }
}