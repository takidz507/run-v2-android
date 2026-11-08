# Run v2 Android (Kotlin + Jetpack Compose) — Implementation Notes (v2.1.0)

## 1. ملخص التحديثات المنفذة في النسخة 2.1.0 (بصراحة تامة)

تم تحديث المشروع بالكامل (`com.takidz.runv2`، 19 ملف كود مصدري Kotlin + إعدادات Gradle وGitHub Actions) لتنفيذ المهام الأربع المطلوبة بالترتيب:

### المهمة 0: السيرفر الجديد (`unredacted.org`)
- **ما تم فعلاً في الكود:**
  - تحديث `RunConfig.kt`:
    - `HOMESERVER_BASE_URL = "https://matrix.unredacted.org"`
    - `SERVER_DOMAIN = "unredacted.org"`
  - إبقاء إخفاء السيرفر والبروتوكول مطبقاً بالكامل في واجهة المستخدم (المستخدم يرى `@username` و`Run account` فقط، ولا يظهر `unredacted.org` أو `Matrix` في الواجهة).
  - الإبقاء على التسجيل والدخول القياسي بالطريقة العادية (`/_matrix/client/v3/register` مع `m.login.dummy` UIAA و`/_matrix/client/v3/login` بـ `m.login.password` — اسم مستخدم وكلمة سر فقط، بلا MAS وبلا موافقة مشرف).
  - مسارات المزامنة (`/_matrix/client/v3/sync`)، إنشاء الغرف (`/_matrix/client/v3/createRoom`)، الانضمام (`/_matrix/client/v3/join/{roomIdOrAlias}`)، والبحث عن الأصدقاء والغرف العامة (`/_matrix/client/v3/user_directory/search` و`/_matrix/client/v3/publicRooms`) متصلة كلها بـ `https://matrix.unredacted.org`.

### المهمة 1: التشفير الحقيقي للرسائل من الطرف إلى الطرف (Olm/Megolm) المتوافق مع Element
- **ما تم فعلاً في الكود:**
  - إضافة المكتبة الرسمية المعتمدة لتشفير Matrix على أندرويد في `app/build.gradle.kts`:
    - `implementation("org.matrix.android:olm-sdk:3.2.11")` (مكتبة `libolm` الرسمية من Matrix مع الـ JNI bindings لـ Kotlin/Java، الموجودة على Maven Central).
    - إضافة قواعد ProGuard في `app/proguard-rules.pro` (`-keep class org.matrix.olm.** { *; }`).
  - إنشاء ملف **`OlmCryptoManager.kt`** (`app/src/main/java/com/takidz/runv2/data/OlmCryptoManager.kt`) الذي ينفذ بروتوكول Olm/Megolm القياسي المعتمد في Matrix والمتوافق مع **Element**:
    1. **مفاتيح الجهاز (`OlmAccount`) وتوقيع Canonical JSON (`OlmUtility`)**:
       - توليد وحفظ حساب `OlmAccount` محلياً داخل `EncryptedSharedPreferences` (`SecureSessionStore.kt`).
       - استخراج مفاتيح الهوية القياسية للجهاز (`curve25519` و`ed25519`) وتوليد المفاتيح أحادية الاستخدام (`signed_curve25519`).
       - تنفيذ محول **Matrix Canonical JSON** المطابق للمواصفة الرسمية (ترتيب المفاتيح أبجدياً بترميز UTF-8 بدون مسافات زائدة وحذف `signatures` و`unsigned` قبل التوقيع) وتوقيعه بمفتاح `ed25519` عبر `OlmAccount.signMessage(...)`.
       - رفع مفاتيح الجهاز والمفاتيح أحادية الاستخدام تلقائياً عبر `POST /_matrix/client/v3/keys/upload` عند الدخول أو التسجيل وخلال `/sync` عندما ينخفض رصيد `device_one_time_keys_count`.
    2. **التحقق من الأجهزة (`Device Verification`)**:
       - جلب مفاتيح أجهزة الأعضاء عبر `POST /_matrix/client/v3/keys/query` عند تغير `device_lists.changed` أو قبل إرسال رسالة مشفرة أو عند فتح شاشة تفاصيل الغرفة.
       - التحقق رياضياً من توقيع `ed25519` لكل جهاز ولـ `signed_curve25519` عبر `OlmUtility.verifyEd25519Signature(...)`.
       - عرض بصمة المفتاح (`Ed25519 Fingerprint` مقسمة إلى مجموعات من 4 محارف) في شاشة الحساب (`AccountScreen`) وفي شاشة تفاصيل الغرفة (`RoomInfoScreen`) مع زر لتوثيق الجهاز يدوياً (`توثيق الجهاز` / `موثّق ✓`).
    3. **تبادل مفاتيح الغرف عبر Olm (`m.olm.v1.curve25519-aes-sha2`)**:
       - قبل إرسال رسالة في غرفة مشفرة، يتحقق `MatrixApiClient` من الأجهزة التي لم تستلم مفتاح جلسة Megolm الحالي (`m.room_key`)، ويطلب مفاتيح `signed_curve25519` للأجهزة الجديدة عبر `POST /_matrix/client/v3/keys/claim`، وينشئ جلسة `OlmSession` (`initOutboundSession`)، ثم يشفر حدث `m.room_key` ويرسله عبر `PUT /_matrix/client/v3/sendToDevice/m.room.encrypted/{txnId}`.
       - عند استقبال أحداث `to_device` في `/sync` (سواء من تطبيق **Element** أو من جهاز **Run** آخر)، يقوم `OlmCryptoManager.processIncomingToDeviceEvents(...)` بفك تشفير رسائل Olm (`PRE_KEY` من النوع `0` أو الرسائل العادية من النوع `1`)، ويستخرج `m.room_key` أو `m.forwarded_room_key`، وينشئ `OlmInboundGroupSession` ويحفظه محلياً قبل معالجة رسائل الغرفة.
    4. **تشفير وفك تشفير رسائل الغرف عبر Megolm (`m.megolm.v1.aes-sha2`)**:
       - الرسائل في الغرف المشفرة تُشفر عبر `OlmOutboundGroupSession.encryptMessage(...)` لبنية حدث `m.room.message` القياسية، وتُرسل كحدث `m.room.encrypted` بالحقول القياسية (`algorithm: "m.megolm.v1.aes-sha2"`, `sender_key`, `ciphertext`, `session_id`, `device_id`) بحيث يفك تطبيق **Element** تشفيرها مباشرة.
       - الرسائل المشفرة الواردة من **Element** أو **Run** تُفك عبر `OlmInboundGroupSession.decryptMessage(ciphertext)`.
    5. **ربط النسخة الاحتياطية برمز PIN (من 6 أرقام) بمفاتيح Megolm الحقيقية**:
       - تم حذف تشفير AES المخصص للرسائل نهائياً من `PinKeyBackupManager.kt`.
       - الآن يقوم `PinKeyBackupManager.kt` بتصدير جميع جلسات Megolm الحقيقية عبر الدالة القياسية `OlmInboundGroupSession.export(firstKnownIndex)`، ثم يشفر حزمة المفاتيح المصدرة باستخدام الرمز السري المكون من 6 أرقام (`PBKDF2WithHmacSHA256` بـ 120,000 دورة + `AES-256-GCM`) ويرفعها إلى Account Data (`m.runv2.key_backup.v2`).
       - عند إدخال رمز PIN على هاتف جديد، يفك `PinKeyBackupManager` تشفير النسخة الاحتياطية ويستورد جميع مفاتيح الجلسات عبر `OlmInboundGroupSession.importSession(exportedSessionKey)`.
    6. **تفعيل التشفير افتراضياً في الغرف الجديدة والصدق التام في الواجهة**:
       - عند إنشاء غرفة جماعية (`createGroupRoom`) أو محادثة خاصة (`createOrOpenDirectChat`)، يتم إرسال حدث الحالة `m.room.encryption` (`algorithm: "m.megolm.v1.aes-sha2"`) في `initial_state` افتراضياً.
       - يتحقق التطبيق فعلياً من حالة الغرفة عبر `/_matrix/client/v3/rooms/{roomId}/state/m.room.encryption`.
       - إذا كانت الغرفة غير مشفرة أو لم تقبل التشفير، تعرض الواجهة في `ChatScreen` و`RoomInfoScreen` بصراحة: `⚠️ هذه الغرفة غير مشفرة — الرسائل فيها تُرسل بدون تشفير طرف-لطرف`، وتُرسل الرسائل كـ `m.room.message` مع `isEncrypted = false` بدون علامة القفل `🔒`.

### المهمة 2: المكالمات (تبقى كما هي + فحص TURN على السيرفر الجديد)
- **ما تم فعلاً في الكود:**
  - لم يتم تغيير كود محرك المكالمات `WebRtcCallManager.kt` إطلاقاً (يبقى WebRTC 1-to-1، 480p افتراضي + توفير البيانات 240p + صوت فقط).
  - بعد تسجيل الدخول وعند بدء المكالمة، يستعلم التطبيق من `https://matrix.unredacted.org/_matrix/client/v3/voip/turnServer` عبر `fetchTurnCredentials()`.
  - إذا لم يرجع السيرفر عناوين TURN صالحة (`uris` فارغة أو خطأ)، يعرض التطبيق تنبيهاً صادقاً في تبويب المكالمات: `"تنبيه صادق: السيرفر لا يوفر بيانات TURN عبر /voip/turnServer حالياً (تعتمد المكالمات على STUN المباشر)"`.

### المهمة 3: رسائل الخطأ الصادقة (`mapErrorToArabic`)
- **ما تم فعلاً في الكود:**
  - تم تحديث `mapErrorToArabic(rawResponse, httpCode, endpointPath)` في `MatrixApiClient.kt` ليفحص كلاً من `errcode`، نص `error` الوارد من السيرفر، `httpCode`، ومسار الطلب `endpointPath`:
    - عندما يرجع التسجيل (`/_matrix/client/v3/register`) خطأ 403 `M_FORBIDDEN` مع `"Registration has been disabled"` (أو أي رفض تسجيل 403)، يعرض التطبيق بالضبط: **`"التسجيل مغلق على هذا السيرفر"`**، وليس `"اسم المستخدم أو كلمة المرور غير صحيحة"`.
    - خطأ `"اسم المستخدم أو كلمة المرور غير صحيحة"` يظهر فقط عندما يفشل مسار `/login` بـ 403 `M_FORBIDDEN`.
    - بقية الأخطاء (`M_USER_DEACTIVATED`, `M_USER_IN_USE`, `M_INVALID_USERNAME`, `M_EXCLUSIVE`, `M_WEAK_PASSWORD`, `M_UNKNOWN_TOKEN`, `M_ROOM_IN_USE`, `M_BAD_JSON`, `M_NOT_FOUND`, `M_LIMIT_EXCEEDED`, `M_TOO_LARGE`, وأخطاء الصلاحيات 403 في الغرف) تعرض السبب الحقيقي الدقيق بلا تضليل.

---

## 2. ما لم يتم / حدود التنفيذ الحالية (بصراحة)

1. **التحقق التفاعلي بالرموز التعبيرية (Interactive SAS Emoji Verification)**:
   - التحقق من الأجهزة المنفذ حالياً يعتمد على التحقق الرياضي التلقائي من تواقيع `ed25519` (`OlmUtility.verifyEd25519Signature`) + مقارنة بصمة المفتاح (`Ed25519 Fingerprint`) يدوياً وتوثيق الجهاز بضغطة زر في شاشة تفاصيل الغرفة، ولم يتم إضافة جلسة التفاوض التفاعلية بالـ Emoji (`m.key.verification.sas`) في هذا الإصدار.
2. **تدوير مفاتيح Megolm عند مغادرة عضو**:
   - جلسة `OlmOutboundGroupSession` تتجدد تلقائياً بعد كل 200 رسالة أو عند عدم وجود جلسة سابقة، ولكن لم يتم ربط إعادة توليد الجلسة الفورية تلقائياً عند كل حدث مغادرة (`leave`/`ban`) في الغرف الجماعية الكبيرة.

---

## 3. ما لم يُختبر حياً بعد (بصراحة تامة)

بما أن بيئة البناء الحالية معزولة عن الإنترنت الخارجي ولا تحتوي على هاتف أندرويد حقيقي متصل:
1. **لم يتم بناء ملف APK محلياً داخل هذه البيئة المعزولة**: ملفات Gradle و`.github/workflows/build.yml` مجهزة للبناء الكامل على GitHub Actions (حيث تتوفر مستودعات Google وMaven Central لتحميل `org.matrix.android:olm-sdk:3.2.11`).
2. **لم يتم إجراء اتصال حي مباشر من هذه البيئة على `https://matrix.unredacted.org`**:
   - لم نختبر حياً ما إذا كان سيرفر `unredacted.org` يفتح التسجيل العام حالياً أم يرجع 403 `Registration has been disabled` (وفي حال إرجاعه 403 سيظهر للمستخدم فوراً النص الصادق: `"التسجيل مغلق على هذا السيرفر"`).
   - لم نختبر حياً ما إذا كان `https://matrix.unredacted.org/_matrix/client/v3/voip/turnServer` يرجع عناوين TURN مفعلة أم قائمة فارغة (وسيكتشف التطبيق ذلك تلقائياً فور الدخول ويعرض النتيجة بصراحة في تبويب المكالمات).
3. **لم يتم إجراء تجربة حية متبادلة (Live Cross-Client Test) بين هاتف يشغل Run وهاتف يشغل Element على `unredacted.org` بعد**: الكود يلتزم حرفياً بمواصفة Matrix Olm (`m.olm.v1.curve25519-aes-sha2`) وMegolm (`m.megolm.v1.aes-sha2`) عبر مكتبة `olm-sdk` الرسمية، ويحتاج لتجربته حياً بعد بناء الـ APK في GitHub Actions.
