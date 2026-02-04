package app.lawnchair.icons

import android.app.ActivityThread
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.android.launcher3.icons.BaseIconFactory.DEFAULT_WRAPPER_BACKGROUND
import com.android.launcher3.util.ComponentKey
import org.json.JSONObject

private const val SHARED_PREFERENCES_KEY: String = "com.android.launcher3.prefs"

val Context.prefs: SharedPreferences get() = applicationContext.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

fun shouldWrapAdaptive(context: Context) = context.prefs.getBoolean("prefs_wrapAdaptive", false)
fun Context.shouldTransparentBGIcons(): Boolean = prefs.getBoolean("prefs_transparentIconBackground", false)
fun Context.shouldShadowBGIcons(): Boolean = prefs.getBoolean("pref_shadowBGIcons", true)

fun Context.isThemedIconsEnabled(): Boolean = prefs.getBoolean("themed_icons", false)
fun Context.shouldTintIconPackBackgrounds(): Boolean = prefs.getBoolean("tint_icon_pack_backgrounds", false)

val prefsNoContext: SharedPreferences get() = ActivityThread.currentApplication()
    .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

private fun getCustomAppNameMap(): Map<ComponentKey, String> {
    val prefs = prefsNoContext

    val customLabel = prefs.getString("pref_appNameMap", "{}")
    if (customLabel.isNullOrEmpty()) return emptyMap()

    val map = mutableMapOf<ComponentKey, String>()
    val obj = JSONObject(customLabel)
    obj.keys().forEach {
        val componentKey = ComponentKey.fromString(it)
        if (componentKey != null) {
            map[componentKey] = obj.getString(it)
        }
    }
    return map
}

fun getCustomAppNameForComponent(info: LauncherActivityInfo): CharSequence? {
    val key = ComponentKey(info.componentName, info.user)
    val customLabel = getCustomAppNameMap()[key]
    if (!customLabel.isNullOrEmpty()) {
        return customLabel
    }
    return info.label
}


fun getWrapperBackgroundColor(context: Context, icon: Drawable): Int {
    val lightness = context.prefs.getFloat("pref_coloredBackgroundLightness", 0.9f)
    val palette = Palette.Builder(drawableToBitmap(icon)).generate()
    val dominantColor = palette.getDominantColor(DEFAULT_WRAPPER_BACKGROUND)
    return setLightness(dominantColor, lightness)
}

private fun setLightness(color: Int, lightness: Float): Int {
    if (color == DEFAULT_WRAPPER_BACKGROUND) {
        return color
    }
    val outHsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, outHsl)
    outHsl[2] = lightness
    return ColorUtils.HSLToColor(outHsl)
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }

    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
