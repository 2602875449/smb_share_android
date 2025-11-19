package com.qi.smbshare.util

import android.content.Context
import android.widget.Toast

object FToastUtil {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}

