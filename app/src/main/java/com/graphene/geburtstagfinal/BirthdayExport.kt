package com.graphene.geburtstagfinal

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object BirthdayExport {

    fun toJsonString(entries: List<BirthdayEntry>): String {
        val arr = JSONArray()

        entries.forEach { e ->
            val obj = JSONObject()
            obj.put("fullName", e.fullName)
            obj.put("birthDate", e.birthDate.toString()) // yyyy-MM-dd
            arr.put(obj)
        }

        val root = JSONObject()
        root.put("version", 1)
        root.put("entries", arr)
        return root.toString(2)
    }

    fun fromJsonString(json: String): List<BirthdayEntry> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("entries")

        val out = mutableListOf<BirthdayEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)

            val name = obj.getString("fullName")
            val date = LocalDate.parse(obj.getString("birthDate"))

            out += BirthdayEntry(
                id = System.currentTimeMillis() + i, // neue IDs für Import
                fullName = name,
                birthDate = date
            )
        }
        return out
    }
}