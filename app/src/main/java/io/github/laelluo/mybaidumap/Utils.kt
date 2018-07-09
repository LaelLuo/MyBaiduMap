package io.github.laelluo.mybaidumap

import android.content.Context
import android.content.SharedPreferences
import android.support.design.widget.Snackbar
import android.view.View

fun Context.getSharedPreferences(name: String) = getSharedPreferences(name, Context.MODE_PRIVATE)!!

fun SharedPreferences.use(function: SharedPreferences.Editor.() -> Unit) {
    val edit = edit()
    try {
        edit.function()
    } finally {
        edit.apply()
    }
}

fun Context.setData(function: SharedPreferences.Editor.() -> Unit) = getSharedPreferences("date").use(function)
fun Context.getData() = getSharedPreferences("date")
fun View.snack(string: String, type: Int = Snackbar.LENGTH_LONG) = Snackbar.make(this, string, type).show()