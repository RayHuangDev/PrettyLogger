package com.ray.library.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.ray.library.pretty_logger.PrettyLogger
import com.ray.library.pretty_logger.Printer
import com.ray.library.prettylogger.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PrettyLogger.add(PrettyLogger.LOW_PRIORITY, Printer.Builder().build())
        PrettyLogger.i("yoyo","pretty logger test")
        PrettyLogger.w("yoyo","pretty logger test")
        PrettyLogger.d("yoyo","pretty logger test")
        PrettyLogger.e("yoyo","pretty logger test")
        PrettyLogger.wtf("yoyo","pretty logger test")

    }
}
