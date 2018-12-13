package com.ray.library.pretty_logger

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Ray Huang
 * @since 2018/12/13
 */
class PrettyLogger {

    companion object {
        // Message priority
        val NON_DEBUG_PRIORITY = 0
        val HIGH_PRIORITY = 1
        val NORMAL_PRIORITY = 2
        val LOW_PRIORITY = 3

        // Message level
        val VERBOSE = 2
        val DEBUG = 3
        val INFO = 4
        val WARN = 5
        val ERROR = 6
        val ASSERT = 7
        val WTF = 8

        private val printers = ConcurrentHashMap<Int, Printer>()

        @JvmStatic
        fun add(priority: Int, printer: Printer) {
            printers.put(priority, printer)
        }

        @JvmStatic
        fun v(tag: String, message: String) {
            v(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun v(tag: String, message: String, priority: Int) {
            log(priority, VERBOSE, tag, message)
        }

        @JvmStatic
        fun d(tag: String, message: String) {
            d(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun d(tag: String, message: String, priority: Int) {
            log(priority, DEBUG, tag, message)
        }

        @JvmStatic
        fun i(tag: String, message: String) {
            i(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun i(tag: String, message: String, priority: Int) {
            log(priority, INFO, tag, message)
        }

        @JvmStatic
        fun w(tag: String, message: String) {
            w(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun w(tag: String, message: String, priority: Int) {
            log(priority, WARN, tag, message)
        }

        @JvmStatic
        fun e(tag: String, message: String) {
            e(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun e(tag: String, message: String, priority: Int) {
            log(priority, ERROR, tag, message)
        }

        @JvmStatic
        fun wtf(tag: String, message: String) {
            wtf(tag, message, HIGH_PRIORITY)
        }

        @JvmStatic
        fun wtf(tag: String, message: String, priority: Int) {
            log(priority, WTF, tag, message)
        }

        private fun log(priority: Int, verbose: Int, tag: String, message: String) {
            for (key in printers.keys) {
                if (priority <= key) {
                    printers[key]!!.log(verbose, tag, message)
                }
            }
        }
    }
}