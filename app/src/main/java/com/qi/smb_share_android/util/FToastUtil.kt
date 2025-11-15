package com.qi.smb_share_android.util

import android.content.Context
import android.widget.Toast

object FToastUtil {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}

