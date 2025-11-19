package com.qi.smbshare.util

import com.qi.smbshare.data.model.SMBConfig
import org.json.JSONObject

/**
 * 将 SMBConfig 序列化为 JSON 字符串，便于存入数据库或通过 Intent 传递
 */
fun SMBConfig.toJsonString(): String {
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("serverAddress", serverAddress)
        put("port", port)
        put("shareName", shareName)
        put("username", username)
        put("password", password)
        put("isAnonymous", isAnonymous)
    }.toString()
}

/**
 * 从 JSON 字符串还原 SMBConfig，如果解析失败返回 null
 */
fun String.toSMBConfigOrNull(): SMBConfig? {
    return try {
        val json = JSONObject(this)
        SMBConfig(
            id = json.optString("id"),
            name = json.optString("name"),
            serverAddress = json.getString("serverAddress"),
            port = json.optInt("port", 445),
            shareName = json.getString("shareName"),
            username = json.optString("username", ""),
            password = json.optString("password", ""),
            isAnonymous = json.optBoolean("isAnonymous", false)
        )
    } catch (e: Exception) {
        null
    }
}
