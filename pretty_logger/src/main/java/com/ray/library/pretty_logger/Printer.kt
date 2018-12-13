package com.ray.library.pretty_logger

import android.content.ContentValues.TAG
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.Thread.currentThread
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

/**
 * @author Ray Huang
 * @since 2018/12/13
 *
 */
class Printer {
    private val LOG_PREFIX = "/PrettyLogs"

    /**
     * Android's max limit for a log entry is ~4076 bytes,
     * so 4000 bytes is used as chunk size since default charset
     * is UTF-8
     */
    private val CHUNK_SIZE = 4000

    /**
     * The minimum stack trace index, starts at this class after two native calls.
     *
     */
    private val MIN_STACK_OFFSET = 2
    private val JSON_INDENT = 2

    /**
     * Drawing toolbox
     */
    private val TOP_LEFT_CORNER = '┌'
    private val BOTTOM_LEFT_CORNER = '└'
    private val MIDDLE_CORNER = '├'
    private val HORIZONTAL_LINE = '│'
    private val SPACE = ' '
    private val DOUBLE_DIVIDER = "────────────────────────────────────────────────────────"
    private val SINGLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄"
    private val TOP_BORDER = TOP_LEFT_CORNER + DOUBLE_DIVIDER + DOUBLE_DIVIDER
    private val BOTTOM_BORDER = BOTTOM_LEFT_CORNER + DOUBLE_DIVIDER + DOUBLE_DIVIDER
    private val MIDDLE_BORDER = MIDDLE_CORNER + SINGLE_DIVIDER + SINGLE_DIVIDER
    private val NEW_LINE = "\n"
    // Title
    private val THREAD_TITLE = "Thread: "
    private val MESSAGE_TITLE = "Message: "

    private val MAX_LINE_LENGTH = MIDDLE_BORDER.length - 11

    private val showThreadInfo: Boolean
    private val methodOffset: Int
    private val methodCount: Int
    private val context: Context?
    private val prefix: String?
    private val logFileSize: Long
    private val contentBuilder = StringBuilder()
    companion object {
        private val DEFAULT_LOG_FILE_SIZE = 100L * 1024L * 1024L
    }

    constructor(
        showThreadInfo: Boolean, methodOffset: Int, methodCount: Int, context: Context?,
        prefix: String?, logFileSize: Long
    ){
        this.showThreadInfo = showThreadInfo
        this.methodOffset = methodOffset
        this.methodCount = methodCount
        val weakReference = WeakReference(context)
        this.context = weakReference.get()
        this.prefix = prefix
        this.logFileSize = logFileSize
    }

    /* Avoiding multi-thread competition */
    @Synchronized
    internal fun log(verbose: Int, tag: String, message: String?) {
        var msg = message
        if (msg == null) {
            msg = "NULL"
        }

        logHeader(verbose, tag)
        logMethod(verbose, tag)
        logMessage(verbose, tag, msg)
    }

    private fun logHeader(verbose: Int, tag: String) {
        if (showThreadInfo) {
            contentBuilder.append(TOP_BORDER).append(NEW_LINE)
            logContent(verbose, tag, getContent())
            contentBuilder.append(HORIZONTAL_LINE)
                .append(SPACE)
                .append(THREAD_TITLE)
                .append(currentThread().name)
                .append(NEW_LINE)
            logContent(verbose, tag, getContent())
        } else {
            contentBuilder.append(TOP_BORDER).append(NEW_LINE)
            logContent(verbose, tag, getContent())
        }
    }

    private fun logMethod(verbose: Int, tag: String) {
        val trace = Thread.currentThread().stackTrace
        val stackOffset = getStackOffset(trace) + methodOffset
        // corresponding method count with the current stack may exceeds the stack trace. Trims the count
        var methodCount = this.methodCount
        if (methodCount + stackOffset > trace.size) {
            methodCount = trace.size - stackOffset - 1
        }

        if (methodCount > 0 && showThreadInfo) {
            contentBuilder.append(MIDDLE_BORDER).append(NEW_LINE)
            logContent(verbose, tag, getContent())
        }

        var space = 1
        for (i in methodCount downTo 1) {
            val stackIndex = i + stackOffset
            if (stackIndex >= trace.size) {
                continue
            }

            contentBuilder.append(HORIZONTAL_LINE)
            for (s in 0 until space) {
                contentBuilder.append(SPACE)
            }

            contentBuilder.append(getSimpleClassName(trace[stackIndex].className))
                .append('.')
                .append(trace[stackIndex].methodName)
                .append(SPACE)
                .append('(')
                .append(trace[stackIndex].fileName)
                .append(':')
                .append(trace[stackIndex].lineNumber)
                .append(')')
                .append(NEW_LINE)
            logContent(verbose, tag, getContent())
            space += 2
        }

        if (methodCount > 0) {
            contentBuilder.append(MIDDLE_BORDER).append(NEW_LINE)
            logContent(verbose, tag, getContent())
        }
    }

    private fun logMessage(verbose: Int, tag: String, message: String) {
        var msg = message
        contentBuilder.append(HORIZONTAL_LINE).append(SPACE).append(MESSAGE_TITLE).append(NEW_LINE)
        logContent(verbose, tag, getContent())
        // Check JSON
        if (msg.startsWith("{")) {
            try {
                val jsonObject = JSONObject(msg)
                msg = jsonObject.toString(JSON_INDENT)
            } catch (e: JSONException) {
            }

        } else if (msg.startsWith("[")) {
            try {
                val jsonArray = JSONArray(msg)
                msg = jsonArray.toString(JSON_INDENT)
            } catch (e: JSONException) {
            }

        }

        // Check XML
        try {
            val source = StreamSource(StringReader(msg))
            val result = StreamResult(StringWriter())
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.transform(source, result)
            msg = result.writer.toString().replaceFirst(">".toRegex(), ">\n")
        } catch (e: TransformerException) {
        }

        val messages = msg.split(NEW_LINE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (msg in messages) {
            substringMessage(verbose, tag, msg)
        }

        contentBuilder.append(BOTTOM_BORDER).append(NEW_LINE)
        logContent(verbose, tag, getContent())
    }

    private fun substringMessage(verbose: Int, tag: String, message: String) {
        val messageLength = message.length
        if (messageLength > MAX_LINE_LENGTH) {
            var startIndex = 0
            var endIndex = MAX_LINE_LENGTH
            while (endIndex <= messageLength) {
                logChunk(verbose, tag, message.substring(startIndex, endIndex))
                if (endIndex == messageLength) {
                    break
                }

                startIndex = endIndex
                endIndex = if (endIndex + MAX_LINE_LENGTH < messageLength) endIndex + MAX_LINE_LENGTH else messageLength
            }
        } else {
            logChunk(verbose, tag, message)
        }
    }

    private fun logChunk(verbose: Int, tag: String, message: String) {
        contentBuilder.append(HORIZONTAL_LINE)
        val subSpace = MESSAGE_TITLE.length
        for (s in 0..subSpace) {
            contentBuilder.append(SPACE)
        }

        contentBuilder.append(message).append(NEW_LINE)
        logContent(verbose, tag, getContent())
    }

    /**
     * Determines the starting index of the stack trace, after method calls made by this class.
     *
     * @param trace the stack trace
     * @return the stack offset
     */
    private fun getStackOffset(trace: Array<StackTraceElement>): Int {
        var i = MIN_STACK_OFFSET
        while (i < trace.size) {
            val e = trace[i]
            val name = e.className
            if (name != Printer::class.java.name && name != PrettyLogger::class.java.name) {
                return --i
            }
            i++
        }

        return -1
    }

    private fun getSimpleClassName(name: String): String {
        val lastIndex = name.lastIndexOf("")
        return name.substring(lastIndex + 1)
    }

    private fun getContent(): String {
        val content = contentBuilder.toString()
        contentBuilder.setLength(0)
        return content
    }

    private fun logContent(verbose: Int, tag: String, message: String) {
        log2Chat(verbose, tag, message)
        log2File(message)
    }

    private fun log2Chat(verbose: Int, tag: String, message: String) {
        when (verbose) {
            PrettyLogger.VERBOSE -> Log.v(tag, message)

            PrettyLogger.DEBUG -> Log.d(tag, message)

            PrettyLogger.INFO -> Log.i(tag, message)

            PrettyLogger.WARN -> Log.w(tag, message)

            PrettyLogger.ERROR -> Log.e(tag, message)

            PrettyLogger.WTF -> Log.w(tag, message)

            else -> {
            }
        }
    }

    private fun log2File(decorate: String) {
        if (context == null) {
            // Ignored
            return
        }

        try {
            val direct = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + LOG_PREFIX
            )

            if (!direct.exists()) {
                direct.mkdir()
            }

            val fileNameTimeStamp = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val logTimeStamp = SimpleDateFormat("E MMM dd yyyy 'at' HH:mm:ss:sss", Locale.getDefault()).format(
                Date()
            )
            val fileName = "$prefix$fileNameTimeStamp.txt"
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
                        + LOG_PREFIX
                        + File.separator
                        + fileName
            )
            file.createNewFile()
            if (file.exists()) {
                val fileOutputStream: OutputStream
                fileOutputStream = FileOutputStream(file, file.length() < logFileSize)
                fileOutputStream.write("$logTimeStamp: $decorate".toByteArray(charset("UTF-8")))
                fileOutputStream.close()
                // Scan file
                MediaScannerConnection.scanFile(context, arrayOf(file.toString()), null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while logging into file : $e")
        }

    }

    class Builder {

        private var showThreadInfo: Boolean = false
        private var methodOffset: Int = 0
        private var methodCount: Int = 0
        private var context: Context? = null
        private var prefix: String? = null
        private var logFileSize: Long = 0

        init {
            this.showThreadInfo = true
            this.methodOffset = 0
            this.methodCount = 2
            this.context = null
            this.prefix = ""
            this.logFileSize = DEFAULT_LOG_FILE_SIZE
        }

        fun showThreadInfo(showThreadInfo: Boolean): Builder {
            this.showThreadInfo = showThreadInfo
            return this
        }

        fun setMethodOffset(methodOffset: Int): Builder {
            this.methodOffset = methodOffset
            return this
        }

        fun setMethodCount(methodCount: Int): Builder {
            this.methodCount = methodCount
            return this
        }

        /**
         * Saves the log to the file
         *
         * @param context context
         * @param prefix file name prefix
         */
        fun log2File(context: Context, prefix: String): Builder {
            this.context = context
            this.prefix = prefix
            return this
        }

        /**
         * Save the log to the file
         *
         * @param context context
         * @param prefix file name prefix
         * @param logFileSize file size (bytes)
         */
        fun log2File(context: Context, prefix: String, logFileSize: Long): Builder {
            this.context = context
            this.prefix = prefix
            this.logFileSize = logFileSize
            return this
        }

        fun build(): Printer {
            return Printer(showThreadInfo, methodOffset, methodCount, context, prefix, logFileSize)
        }
    }
}