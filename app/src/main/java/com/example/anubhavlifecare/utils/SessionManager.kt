package com.example.anubhavlifecare.utils

import android.content.Context
import com.example.anubhavlifecare.data.model.UserPermissions

object SessionManager {
    private const val PREFS_NAME = "aktiv_admin_session"
    private const val KEY_USER_KEY = "user_key"
    private const val KEY_USERID = "userid"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_CAN_SALES = "can_view_sales"
    private const val KEY_CAN_BOOK = "can_book"
    private const val KEY_CAN_EDIT = "can_edit_booking"
    private const val KEY_CAN_CANCEL = "can_cancel_booking"
    private const val KEY_CAN_CONFIRM = "can_confirm_report"
    private const val KEY_IS_ADMIN = "is_admin"

    fun save(
        context: Context,
        userKey: Int,
        userid: String,
        username: String,
        role: String = "staff",
        permissions: UserPermissions? = null,
    ) {
        val p = permissions ?: UserPermissions()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_USER_KEY, userKey)
            .putString(KEY_USERID, userid)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role)
            .putBoolean(KEY_IS_ADMIN, p.isAdmin)
            .putBoolean(KEY_CAN_SALES, p.canViewSales)
            .putBoolean(KEY_CAN_BOOK, p.canBook)
            .putBoolean(KEY_CAN_EDIT, p.canEditBooking)
            .putBoolean(KEY_CAN_CANCEL, p.canCancelBooking)
            .putBoolean(KEY_CAN_CONFIRM, p.canConfirmReport)
            .apply()
    }

    private fun flag(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(key, false)

    fun getRole(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ROLE, "staff").orEmpty()

    fun isAdmin(context: Context) = flag(context, KEY_IS_ADMIN)
    fun canViewSales(context: Context) = flag(context, KEY_CAN_SALES)
    fun canBook(context: Context) = flag(context, KEY_CAN_BOOK)
    fun canEditBooking(context: Context) = flag(context, KEY_CAN_EDIT)
    fun canCancelBooking(context: Context) = flag(context, KEY_CAN_CANCEL)
    fun canConfirmReport(context: Context) = flag(context, KEY_CAN_CONFIRM)

    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_USER_KEY)

    fun getUserKey(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_USER_KEY)) prefs.getInt(KEY_USER_KEY, -1) else null
    }

    fun getUserid(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERID, null)

    fun getUsername(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
