package com.example.midad

import android.app.Application
import androidx.room.Room
import com.example.midad.core.PostingService
import com.example.midad.data.local.MidadDatabase
import com.example.midad.data.local.RoomErpStore

/**
 * التطبيق المنفرد — كل البيانات على الجهاز، بلا خادم ولا ربط بأي مستودع.
 * الطبقة جاهزة لاحقاً لإضافة مزامنة بلا إعادة كتابة.
 */
class MidadApp : Application() {

    lateinit var db: MidadDatabase
        private set

    lateinit var store: RoomErpStore
        private set

    lateinit var posting: PostingService
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, MidadDatabase::class.java, "midad.db")
            .fallbackToDestructiveMigration()
            .build()
        store = RoomErpStore(db.erpDao())
        posting = PostingService(store)
    }

    companion object {
        /** منشأة واحدة في النسخة المنفردة — تُوسَّع لاحقاً بتعدد المنشآت */
        const val COMPANY_ID = "co-main"
    }
}
