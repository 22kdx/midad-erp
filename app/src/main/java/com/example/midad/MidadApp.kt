package com.example.midad

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
            .addMigrations(MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
        store = RoomErpStore(db.erpDao())
        posting = PostingService(store)
        // تذكيرات محلية يومية — تُجدول مرة واحدة وتُلغى من الإعدادات
        runCatching { com.example.midad.notify.Reminders.schedule(this) }
    }

    companion object {
        /** ترقية غير هدّامة تحفظ السجلات السابقة من Room v5. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE company ADD COLUMN functionalCurrencyDecimals INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE company ADD COLUMN defaultTaxRatePct TEXT NOT NULL DEFAULT '0'")
                db.execSQL("ALTER TABLE company ADD COLUMN pricesTaxInclusive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE company ADD COLUMN outputTaxAccountId TEXT")
                db.execSQL("ALTER TABLE company ADD COLUMN inputTaxAccountId TEXT")
                db.execSQL("CREATE TABLE app_setting_new (companyId TEXT NOT NULL, settingKey TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(companyId, settingKey))")
                db.execSQL("INSERT INTO app_setting_new (companyId, settingKey, value) SELECT 'co-main', settingKey, value FROM app_setting")
                db.execSQL("DROP TABLE app_setting")
                db.execSQL("ALTER TABLE app_setting_new RENAME TO app_setting")
                db.execSQL("CREATE TABLE IF NOT EXISTS company_currency (companyId TEXT NOT NULL, code TEXT NOT NULL, decimalPlaces INTEGER NOT NULL, PRIMARY KEY(companyId, code))")
                db.execSQL("CREATE TABLE IF NOT EXISTS fx_rate (id TEXT NOT NULL PRIMARY KEY, companyId TEXT NOT NULL, currency TEXT NOT NULL, rateMicros INTEGER NOT NULL, date TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fx_rate_companyId_currency_date ON fx_rate(companyId, currency, date)")
                db.execSQL("CREATE TABLE IF NOT EXISTS cash_account (id TEXT NOT NULL PRIMARY KEY, companyId TEXT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, accountNumber TEXT NOT NULL, currency TEXT NOT NULL, openingBalanceMinor INTEGER NOT NULL, ledgerAccountId TEXT NOT NULL, isActive INTEGER NOT NULL, defaultReceipt INTEGER NOT NULL, defaultPayment INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_account_companyId_isActive ON cash_account(companyId, isActive)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_account_companyId_ledgerAccountId ON cash_account(companyId, ledgerAccountId)")
            }
        }

        /** منشأة واحدة في النسخة المنفردة — تُوسَّع لاحقاً بتعدد المنشآت */
        const val COMPANY_ID = "co-main"
    }
}
