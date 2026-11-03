# المساهمة والتشغيل

## الاختبارات (بلا Android SDK)
```bash
./build.sh          # ١٥٢ اختبار نواة · يجب أن تكون كلها ناجحة
```

## بناء التطبيق
```bash
./gradlew assembleDebug     # يحتاج Android SDK 35 و JDK 17
./gradlew installDebug      # على جهاز موصول
```
أو افتح المجلد في Android Studio (Ladybug أو أحدث) واضغط Run.

## قواعد لا تُخترق
1. الشاشة لا تبني قيداً — كل ترحيل عبر `PostingService` ثم `JournalKernel.validate`.
2. المُرحَّل لا يُعدَّل ولا يُحذف — التصحيح بقيد عكسي.
3. النقود `Long` بالوحدة الصغرى، ولا كسور عائمة في المحاسبة.
4. كل جدول يحمل `companyId`.
5. كل ميزة نواة جديدة تُرفَق باختبار في `TestRunner.kt` قبل الدمج.
