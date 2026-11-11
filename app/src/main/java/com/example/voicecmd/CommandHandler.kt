package com.example.voicecmd

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يحوّل النص المنطوق إلى أمر ويُنفّذه، ويُرجع جملة الرد.
 * لإضافة أمر جديد: أضف سطراً في route() ودالة تنفّذه.
 */
class CommandHandler(private val ctx: Context) {

    private var torchOn = false

    // أسماء عربية شائعة -> اسم التطبيق كما يظهر على الهاتف
    private val aliases = mapOf(
        "واتساب" to "whatsapp", "يوتيوب" to "youtube", "فيسبوك" to "facebook",
        "انستغرام" to "instagram", "انستجرام" to "instagram", "تيك توك" to "tiktok",
        "تلغرام" to "telegram", "تليجرام" to "telegram", "كروم" to "chrome",
        "جيميل" to "gmail", "خرائط" to "maps", "الاعدادات" to "settings",
        "سناب" to "snapchat", "الصور" to "photos", "الحاسبه" to "calculator",
        "الساعه" to "clock"
    ).mapKeys { normalize(it.key) }

    fun handle(raw: String): String {
        val original = toAsciiDigits(raw.trim())
        val t = normalize(original)
        return try {
            route(original, t)
        } catch (e: ActivityNotFoundException) {
            "لا يوجد تطبيق يدعم هذا الأمر"
        } catch (e: SecurityException) {
            "الصلاحية مطلوبة لتنفيذ هذا الأمر"
        } catch (e: Exception) {
            "تعذر تنفيذ الأمر"
        }
    }

    private fun route(original: String, t: String): String = when {
        has(t, "ماذا تستطيع", "مساعده", "الاوامر") ->
            "أستطيع فتح التطبيقات والكاميرا، الاتصال، الكشاف، الصوت، البحث، الاتجاهات، المؤقت، الوقت والبطارية"

        has(t, "كشاف", "فلاش", "مصباح") ->
            torch(!has(t, "اطفي", "اغلق", "وقف", "طفي"))

        has(t, "صوت") && has(t, "اكتم", "كتم") -> {
            audio(AudioManager.ADJUST_MUTE); "تم كتم الصوت"
        }
        has(t, "صوت") && has(t, "ارفع", "زيد", "زد") -> {
            audio(AudioManager.ADJUST_RAISE); "تم رفع الصوت"
        }
        has(t, "صوت") && has(t, "اخفض", "خفض", "نقص") -> {
            audio(AudioManager.ADJUST_LOWER); "تم خفض الصوت"
        }

        has(t, "كاميرا") -> {
            go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); "تم فتح الكاميرا"
        }
        has(t, "واي فاي", "وايفاي", "wifi") -> {
            go(Intent(Settings.ACTION_WIFI_SETTINGS)); "إعدادات الواي فاي"
        }
        has(t, "بلوتوث") -> {
            go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "إعدادات البلوتوث"
        }

        has(t, "كم الساعه", "كم الوقت", "الوقت الان") -> {
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "الساعة الآن $now"
        }
        has(t, "بطاريه") -> {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            "نسبة البطارية ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} بالمئة"
        }

        has(t, "موقت", "منبه") -> timer(t)

        has(t, "موسيقي", "اغاني", "اغنيه") -> {
            go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
            "تم فتح الموسيقى"
        }

        has(t, "اتصل", "كلم") -> call(original)

        has(t, "ابحث عن", "دور علي", "بحث عن") -> search(t)

        has(t, "خذني", "اتجاهات", "وجهني", "طريق الي") -> navigate(t)

        has(t, "افتح", "شغل") -> openApp(t)

        else -> "لم أفهم الأمر: $original"
    }

    // ---------- تنفيذ الأوامر ----------

    private fun torch(on: Boolean): String {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "لا يوجد فلاش في هذا الجهاز"
        cm.setTorchMode(id, on)
        torchOn = on
        return if (on) "تم تشغيل الكشاف" else "تم إطفاء الكشاف"
    }

    private fun audio(direction: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun timer(t: String): String {
        val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: return "قل مثلاً: مؤقت 5 دقائق"
        val seconds = if (has(t, "ثانيه", "ثواني")) n else if (has(t, "ساعه", "ساعات")) n * 3600 else n * 60
        go(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        })
        return "تم ضبط المؤقت"
    }

    private fun call(original: String): String {
        val target = Regex("(?:اتصل|كلم|كلّم)\\s*(.*)").find(original)
            ?.groupValues?.get(1)?.trim().orEmpty()
        if (target.isBlank()) {
            go(Intent(Intent.ACTION_DIAL)); return "تم فتح الاتصال"
        }
        // رقم مباشر
        val digits = target.filter { it.isDigit() || it == '+' }
        if (digits.length >= 5 && target.none { it.isLetter() }) {
            go(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", digits, null)))
            return "جاري الاتصال بالرقم $digits"
        }
        // اسم من جهات الاتصال (نجرّب بدون حرف "ب" الملتصق)
        val candidates = listOf(target, target.removePrefix("ب").trim()).distinct()
        for (name in candidates) {
            val number = lookupNumber(name)
            if (number != null) {
                go(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
                return "جاري الاتصال بـ $name"
            }
        }
        return "لم أجد \"$target\" في جهات الاتصال"
    }

    private fun lookupNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    private fun search(t: String): String {
        val q = Regex("(?:ابحث عن|دور علي|بحث عن)\\s*(.+)").find(t)?.groupValues?.get(1)?.trim()
            ?: return "ماذا تريد أن أبحث عنه؟"
        go(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q))
        return "جاري البحث عن $q"
    }

    private fun navigate(t: String): String {
        val place = Regex("(?:خذني|اتجاهات|وجهني|طريق)\\s*(?:الي|الى)?\\s*(.+)").find(t)
            ?.groupValues?.get(1)?.trim()
            ?: return "إلى أين تريد الذهاب؟"
        go(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place))))
        return "جاري التوجيه إلى $place"
    }

    private fun openApp(t: String): String {
        val name = Regex("(?:افتح|شغل)\\s*(?:تطبيق)?\\s*(.+)").find(t)?.groupValues?.get(1)?.trim()
            ?: return "أي تطبيق تريد؟"
        val key = normalize(aliases[name] ?: name)
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val match = pm.queryIntentActivities(launcher, 0).firstOrNull {
            normalize(it.loadLabel(pm).toString()).contains(key)
        } ?: return "لم أجد تطبيق $name"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "تعذر فتح $name"
        go(intent)
        return "تم فتح $name"
    }

    // ---------- أدوات مساعدة ----------

    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun has(t: String, vararg keys: String) = keys.any { t.contains(normalize(it)) }

    private fun toAsciiDigits(s: String): String = buildString {
        for (c in s) append(if (c in '٠'..'٩') ('0' + (c - '٠')) else c)
    }

    companion object {
        /** توحيد الحروف العربية وإزالة التشكيل ليسهل المطابقة */
        fun normalize(s: String): String = s.lowercase()
            .replace(Regex("[\u064B-\u0652\u0640]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace('ة', 'ه')
            .replace('ئ', 'ي').replace('ؤ', 'و')
    }
}
