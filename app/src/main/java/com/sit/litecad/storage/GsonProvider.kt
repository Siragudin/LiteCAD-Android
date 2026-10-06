package com.sit.litecad.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object GsonProvider {
    val instance: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()
}