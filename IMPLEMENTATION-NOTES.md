# Run v2 Android (Kotlin 2.0.0) — IMPLEMENTATION-NOTES

## 1. نظرة عامة على البنية الهندسية

تم بناء النسخة الحقيقية من تطبيق **Run v2 Android** (`com.takidz.runv2`) بلغة **Kotlin** وواجهة **Jetpack Compose** وفق التصميم المعتمد **Design B** (عمود واحد نظيف على طريقة Telegram، ثيم أساسي **Warm Night** `#171310` مع ذهبي كهرماني `#E8A33D`، وثيم فاتح مكمل **Warm Paper** `#FBF6EE`، وتبويبات سفلية ثابتة: `Rooms` / `Calls` / `Account`، وأيقونة النسخة الثانية المعتمدة بحرف `R`).

تلتزم هذه النسخة التزامًا صارمًا بقاعدة التكليف وملاحظات فحص الكود (موس):
1. **صفر بيانات تجريبية أو وهمية (`PlaceholderData`)**: لا توجد أي غرف مسبقة مثل `Section D` أو `Study group` أو `Amine`. عند تسجيل الدخول بحساب جديد، تظهر قائمة الغرف فارغة تمامًا حتى ينشئ المستخدم غرفة أو ينضم إليها فعليًا.
2. **إخفاء السيرفر والبروتوكول تمامًا عن الواجهة**: العنوان `https://matrix.tchncs.de` والنطاق `tchncs.de` محصوران داخليًا في الكائن `RunConfig.kt` فقط، ولا يظهر في أي شاشة أو رسالة خطأ أي ذكر لـ `Matrix` أو `tchncs.de` أو `server` أو `protocol`. يرى المستخدم فقط `@username` وعبارة `Run account`.
3. **عدم ادّعاء تشفير E2E غير مكتمل (`isEncrypted = false` افتراضيًا)**:
   - تُرسل الرسائل افتراضيًا عبر حدث Matrix القياسي `m.room.message` (`encryptWithRoomKey = false`)، وبما أن بروتوكول تبادل المفاتيح المتقاطع `Olm/Megolm` الكامل غير مدمج في هذه النسخة، فلا ندّعي وجود تشفير طرف-لطرف (E2EE) كامل للرسائل.
   - تم ضبط الحقل `isEncrypted` ليكون `false` افتراضيًا في جميع النماذج (`Models.kt`)، وفي `MatrixApiClient.kt` (سواء في `sync` عبر `roomEncryptedCache[roomId] ?: false`، أو في `sendRoomMessage` حيث يأخذ قيمة `encryptWithRoomKey` الفعلية أي `false`، أو في `createGroupRoom`)، وكذلك في `RunViewModel.kt` داخل `joinRoomByCodeOrId` (join fallback) و `openOrStartDirectChatWithUser`، مع إزالة أي عبارة في الواجهة توحي بأن الغرفة أو الرسالة مشفّرة بينما هي نص عادي.

## 2. حالة التنفيذ ملفًا بملف (File-by-File Status)

| الملف | الوظيفة وما هو منفّذ برمجيًا فيه |
| :--- | :--- |
| `app/src/main/java/com/takidz/runv2/data/RunConfig.kt` | الثوابت الداخلية للسيرفر (`https://matrix.tchncs.de`) ودوال التحويل بين المعرّف الظاهر للمستخدم (`@username`) والمعرّف الداخلي، وتحويل أكواد الغرف وروابط الدعوة `run://join/<code>` و `run://friend/@username`. |
| `app/src/main/java/com/takidz/runv2/data/Models.kt` | نماذج البيانات الموحدة المتطابقة عبر كل الطبقات (`RunAccount`, `RoomSummary`, `ChatMessage`, `RoomMember`, `UserSearchResult`, `PublicRoomResult`, `FriendRequestItem`, `CallLogEntry`, `ActiveCallState`, `TurnCredentials`, `SignalingEvent`) مع ضبط `isEncrypted = false` افتراضيًا. |
| `app/src/main/java/com/takidz/runv2/data/SecureSessionStore.kt` | تخزين مشفّر للجلسة عبر `EncryptedSharedPreferences` (`AES256_SIV` للمفاتيح و `AES256_GCM` للقيم). يحفظ رمز الجلسة `access_token`، ومعرّف الجهاز، وخزنة PIN، وإعدادات جودة المكالمة، وسجل المكالمات دون حفظ كلمة المرور بنص صريح أبدًا. |
| `app/src/main/java/com/takidz/runv2/data/PinKeyBackupManager.kt` | إدارة رمز PIN المكون من 6 أرقام واشتقاق مفتاح التشفير عبر `PBKDF2WithHmacSHA256` (بـ 120,000 دورة) مع تشفير الخزنة بـ `AES/GCM/NoPadding` (256-bit) لرفعها واستعادتها من بيانات الحساب على السيرفر (`m.runv2.key_backup.v2`). |
| `app/src/main/java/com/takidz/runv2/data/MatrixApiClient.kt` | الطبقة الشبكية الكاملة (`OkHttp` + `Coroutines`): تسجيل الدخول (`/v3/login`)، إنشاء حساب باسم مستخدم وكلمة مرور فقط (`/v3/register` مع دعم `m.login.dummy`)، المزامنة المستمرة (`/v3/sync`)، جلب سجل الرسائل (`/messages`)، إرسال الرسائل النصية القياسية (`m.room.message` مع `isEncrypted = false` افتراضيًا)، إنشاء غرف جماعية بكود دائم (`/v3/createRoom`)، الانضمام للغرف بالمعرّف أو الكود (`/v3/join/{roomIdOrAlias}`)، جلب الأعضاء (`/members`)، البحث عن الأصدقاء بالاسم المعروض و `@username` (`/v3/user_directory/search` + `/v3/profile`)، البحث في الغرف العامة بالاسم والكود (`/v3/publicRooms` + `/v3/directory/room`)، الحضور (`/v3/presence`)، وجلب بيانات TURN للمكالمات (`/v3/voip/turnServer`). |
| `app/src/main/java/com/takidz/runv2/webrtc/WebRtcCallManager.kt` | طبقة مكالمات الفيديو والصوت الفردية (1-إلى-1) عبر `org.webrtc` (`PeerConnectionFactory`, `Camera2Enumerator`, `SurfaceViewRenderer`, `AudioManager`) باستخدام بيانات `TURN` القادمة من السيرفر، مع تطبيق إعدادات الجودة (`480p` افتراضي، أو `Data saver 240p`، أو `Audio only`)، وكتم الميكروفون، وتشغيل/إيقاف الكاميرا، ومكبر الصوت، وإنهاء الاتصال. |
| `app/src/main/java/com/takidz/runv2/ui/RunViewModel.kt` | يربط شاشات الواجهة بطبقة `MatrixApiClient` و `WebRtcCallManager` و `PinKeyBackupManager`، ويدير حلقة المزامنة (`startSyncLoop`)، والروابط العميقة `run://join/<code>`، وإشارات المكالمات (`m.call.invite`, `m.call.answer`, `m.call.candidates`, `m.call.hangup`)، مع ضبط `isEncrypted = false` في جميع حالات الـ fallback. |
| `app/src/main/java/com/takidz/runv2/ui/theme/Theme.kt` | ألوان وخطوط **Design B**: ثيم **Warm Night** (`#171310` مع `#E8A33D`) وثيم **Warm Paper** (`#FBF6EE`)، وخط رسائل صغير ونظيف (`13.5sp`). |
| `app/src/main/java/com/takidz/runv2/ui/screens/*.kt` | شاشات `LoginScreen`, `PinScreen`, `RoomsScreen`, `SearchScreen`, `CreateRoomScreen`, `ChatScreen`, `RoomInfoScreen`, و `CallsAndAccountScreens` خالية تمامًا من أي ادّعاء بتشفير E2EE غير مفعّل ومن أي بيانات تجريبية. |
| `app/src/main/java/com/takidz/runv2/MainActivity.kt` | نقطة الدخول الرئيسية، تستقبل الروابط العميقة `run://join/<code>` و `run://friend/@username`، وتطلب صلاحيات الكاميرا والميكروفون عند بدء المكالمة، وتعرض التبويبات السفلية الثابتة `Rooms` / `Calls` / `Account`. |

## 3. حالة التشفير (E2EE) وإصلاح ملاحظتي الفحص

1. **إرسال الرسائل وعدم ادّعاء Olm/Megolm**:
   - تُرسل الرسائل عبر `sendRoomMessage` بالقيمة الافتراضية `encryptWithRoomKey = false` (حدث `m.room.message` قياسي).
   - لم يتم تضمين محرك `Olm/Megolm` الكامل في هذه النسخة، ولذلك **تم ترك تشفير الرسائل الطرف-لطرف (E2EE) خارج ادعاءات هذه النسخة تمامًا** سواء في الكود أو في نصوص الواجهة أو في التوثيق.
2. **تصحيح قيمة `isEncrypted` الافتراضية**:
   - تم تغيير القيمة الافتراضية للحقل `isEncrypted` إلى `false` في `Models.kt`، وفي `MatrixApiClient.kt` (سواء في `sync` عبر `roomEncryptedCache[roomId] ?: false`، أو عند قراءة `m.room.message`، أو عند إرسال الرسالة `isEncrypted = encryptWithRoomKey`، أو عند إنشاء غرفة)، وفي `RunViewModel.kt` داخل `joinRoomByCodeOrId` (join fallback) و `openOrStartDirectChatWithUser`. بذلك لا تظهر أي غرفة أو رسالة على أنها مشفّرة وهي غير مشفّرة.

## 4. فقرة الاختبار الصريحة المنفصلة (Explicit Testing Report)

التزامًا الصارم بقاعدة التسليم («لا تكتب "يعمل" عن أي شيء لم يُجرَّب»):

- **ما تم فحصه والتحقق منه برمجيًا فقط (Static & Cross-File Verification)**:
  - مراجعة شاملة لتطابق الدوال والنماذج والأنواع والاستدعاءات عبر جميع ملفات المشروع.
  - التأكد من خلو المشروع من أي `PlaceholderData` أو أسماء غرف/أشخاص وهمية (`Section D`, `Study group`, `Amine`).
  - التأكد من إخفاء عنوان السيرفر (`https://matrix.tchncs.de`) داخل `RunConfig.kt` فقط.
  - التأكد من ضبط `isEncrypted = false` افتراضيًا في كل مكان وعدم ادعاء تشفير E2E.
- **ما لم يُجرَّب حيًّا بعد (Not Tested Live Yet)**:
  - **لم يتم بناء ملف الـ APK بعد** (بانتظار الدفع إلى GitHub وتشغيل GitHub Actions).
  - **لم يتم تجربة أي اتصال حي على السيرفر الحقيقي بعد**: لا تسجيل دخول حي، لا إرسال رسائل حي، لا بحث أو إضافة صديق حية، لا فتح رابط دعوة `run://join/<code>` حي، ولا مكالمة WebRTC حية بين جهازين. كل هذه الوظائف مكتوبة برمجيًا ومربوطة بنقاط النهاية الحقيقية في الكود، لكنها لم تُختبَر ميدانيًا بعد.
