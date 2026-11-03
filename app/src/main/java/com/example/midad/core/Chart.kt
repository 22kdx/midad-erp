package com.example.midad.core

/**
 * دليل الحسابات الافتراضي (قالب اليمن).
 * الأكواد مركزية هنا — لا تُكتب نصاً في الشاشات أبداً (خطأ التطبيق الحالي: "1000"/"4000" مكررة في ٤٠ موضعاً).
 */
object Chart {

    object Acc {
        const val CASH = "1101"
        const val BANK = "1102"
        const val AR = "1103"
        const val CHEQUES = "1104"
        const val CUSTODY = "1105"
        const val RETENTION = "1106"
        const val INVENTORY = "1201"
        const val FIXED_ASSET = "1601"
        const val ACCUM_DEP = "1602"
        const val AP = "2101"
        const val PAYROLL_PAYABLE = "2103"
        const val VAT_OUT = "2201"
        const val VAT_IN = "2202"
        const val CHEQUES_OUT = "2203"
        const val DEPOSITS = "2301"
        const val CAPITAL = "3101"
        const val RETAINED = "3102"
        const val SALES = "4101"
        const val SALES_RETURN = "4102"
        const val SALES_DISCOUNT = "4103"
        const val COUNT_GAIN = "4104"
        const val ROUNDING = "4105"
        const val COGS = "5101"
        const val SALARY_EXP = "5102"
        const val DEP_EXP = "5103"
        const val RENT_EXP = "5104"
        const val COUNT_LOSS = "5105"
        const val BOUNCED_CHEQUE = "5106"
    }

    data class Def(
        val code: String,
        val name: String,
        val type: AccountType,
        val parentCode: String? = null,
        val isGroup: Boolean = false,
    )

    val YEMEN: List<Def> = listOf(
        Def("1", "الأصول", AccountType.ASSET, isGroup = true),
        Def("11", "الأصول المتداولة", AccountType.ASSET, "1", isGroup = true),
        Def(Acc.CASH, "الصندوق الرئيسي", AccountType.ASSET, "11"),
        Def(Acc.BANK, "البنك", AccountType.ASSET, "11"),
        Def(Acc.AR, "ذمم العملاء", AccountType.ASSET, "11"),
        Def(Acc.CHEQUES, "شيكات تحت التحصيل", AccountType.ASSET, "11"),
        Def(Acc.CUSTODY, "سلف وعُهد موظفين", AccountType.ASSET, "11"),
        Def(Acc.RETENTION, "محتجزات لدى العملاء", AccountType.ASSET, "11"),
        Def(Acc.INVENTORY, "المخزون", AccountType.ASSET, "11"),
        Def("16", "الأصول الثابتة", AccountType.ASSET, "1", isGroup = true),
        Def(Acc.FIXED_ASSET, "أصول ثابتة - تكلفة", AccountType.ASSET, "16"),
        Def(Acc.ACCUM_DEP, "مجمع الإهلاك", AccountType.ASSET, "16"),
        Def("2", "الالتزامات", AccountType.LIABILITY, isGroup = true),
        Def(Acc.AP, "ذمم الموردين", AccountType.LIABILITY, "2"),
        Def(Acc.PAYROLL_PAYABLE, "رواتب مستحقة", AccountType.LIABILITY, "2"),
        Def(Acc.VAT_OUT, "ضريبة المخرجات", AccountType.LIABILITY, "2"),
        Def(Acc.VAT_IN, "ضريبة المدخلات", AccountType.LIABILITY, "2"),
        Def(Acc.CHEQUES_OUT, "شيكات صادرة", AccountType.LIABILITY, "2"),
        Def(Acc.DEPOSITS, "أمانات وتأمينات", AccountType.LIABILITY, "2"),
        Def("3", "حقوق الملكية", AccountType.EQUITY, isGroup = true),
        Def(Acc.CAPITAL, "رأس المال", AccountType.EQUITY, "3"),
        Def(Acc.RETAINED, "أرباح مبقاة", AccountType.EQUITY, "3"),
        Def("4", "الإيرادات", AccountType.REVENUE, isGroup = true),
        Def(Acc.SALES, "إيراد المبيعات", AccountType.REVENUE, "4"),
        Def(Acc.SALES_RETURN, "مردودات المبيعات", AccountType.REVENUE, "4"),
        Def(Acc.SALES_DISCOUNT, "خصم مسموح به", AccountType.REVENUE, "4"),
        Def(Acc.COUNT_GAIN, "إيراد فروق جرد", AccountType.REVENUE, "4"),
        Def(Acc.ROUNDING, "فروق تقريب", AccountType.REVENUE, "4"),
        Def("5", "المصروفات", AccountType.EXPENSE, isGroup = true),
        Def(Acc.COGS, "تكلفة المبيعات", AccountType.EXPENSE, "5"),
        Def(Acc.SALARY_EXP, "مصروف رواتب", AccountType.EXPENSE, "5"),
        Def(Acc.DEP_EXP, "مصروف إهلاك", AccountType.EXPENSE, "5"),
        Def(Acc.RENT_EXP, "مصروف إيجار", AccountType.EXPENSE, "5"),
        Def(Acc.COUNT_LOSS, "مصروف فروق جرد", AccountType.EXPENSE, "5"),
        Def(Acc.BOUNCED_CHEQUE, "مصروف شيك مرتجع", AccountType.EXPENSE, "5"),
    )
}
