package com.huli.multithread

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.lang.Thread.sleep
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import kotlin.Exception
import kotlin.collections.ArrayList

/**
 * [Entru]
 * @author  : 1552980328
 * @since   : 0.1
 * @date    : 2020/5/18
 * @time    : 16:37
 **/

fun main(args: Array<String>) {
    println()
    println("==============")
    println("   程序开始")
    println("==============")
    println()

    var sourceUrl = ""
    var targetFile = ""
    var threadNo = 1

    var pointer = 0
    while (pointer != args.size) {
        when (args[pointer]) {
            "-j" -> {
                if (args[pointer + 1].toInt() < 1) {
                    println("错误： 线程数量必须 >= 1")
                    return
                }
                threadNo = args[pointer + 1].toInt()
                pointer++
            }
            "-u" -> {
                pointer++
                sourceUrl = args[pointer]
            }
            "-f" -> {
                pointer++
                targetFile = args[pointer]
            }
        }
        pointer++
    }

    if (sourceUrl.isEmpty()) {
        println("错误: 下载链接不可为空")
        println("使用方法: -u <链接>")
        return
    }

    if (targetFile.isEmpty()) {
        println("错误: 保存路径不可为空")
        println("使用方法: -f <路径>")
        return
    }

    val request = Request.Builder().url(args[0])
        .addHeader(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36"
        )
        .url(sourceUrl)
        .get()
        .build()

    // Request body
    print("连接中...")
    val body = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
        .newCall(request)
        .execute()
        .body
    if (body == null) {
        print("下载失败: 空返回")
        return
    }

    // Create threads
    val threadList = ArrayList<MultiThread>()
    println("启动线程中...")
    for (i in 1 .. threadNo) {
        threadList.add(MultiThread(targetFile, i).startThread())
    }

    // Call for creating buffering file
    for (i in 1 .. threadNo) {
        threadList.forEach { thread ->
            thread.threadsStartComplete = true
        }
    }

    while (checkFileCreateComplete(threadList)) {
        sleep(100)
    }

    // Distributing streaming length per thread
    val eachLength = body.contentLength().toInt() / threadNo
    if (body.contentLength() % threadNo.toLong() == 0L) {
        for ((i, j) in threadList.withIndex()) {
            j.setStream(body.byteStream(), eachLength * i, eachLength * (i + 1))
        }
    } else {
        var start = eachLength + (body.contentLength().toInt() % threadNo)
        threadList.first().setStream(body.byteStream(), 0, start)
        for (i in 2 .. threadNo) {
            threadList[i].setStream(body.byteStream(), start, start + eachLength)
            start+=eachLength
        }
    }

    // Start streaming
    var time = System.currentTimeMillis()
    println("启动下载: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time))
    threadList.forEach { thread ->
        thread.startStreaming = true
    }
    println("下载中...")
    while (!checkStreamingComplete(threadList)) {
        sleep(100)
    }
    println("下载完成: 用时" + (System.currentTimeMillis() - time).run {
        "${this / 60L}分${this % 60}秒"
    })

    // Close connection
    body.byteStream().close()
    body.close()

    println("合并文件中...")
    time = System.currentTimeMillis()
    val file = File(targetFile)
    for (i in 1 .. threadNo) {
        file.appendBytes(File(targetFile + i).readBytes())
    }
    println("文件合并完成: 用时" + (System.currentTimeMillis() - time).run {
        "${this / 60L}分${this % 60}秒"
    })

    println("删除缓存文件中...")
    for (i in 1 .. threadNo) {
        File(targetFile + i).delete()
    }
    println("缓存文件删除完成")
    println()
    println("==============")
    println("   退出程序")
    println("==============")
    println()
}

fun checkStreamingComplete(threads: ArrayList<MultiThread>): Boolean {
    threads.forEach { thread ->
        if (!thread.streamingComplete)
            return false
    }
    return true
}

fun checkFileCreateComplete(threads: ArrayList<MultiThread>): Boolean {
    threads.forEach { thread ->
        if (!thread.fileCreateComplete) {
            return false
        }
    }
    return true
}

class MultiThread(fileName: String, streamId: Int): java.lang.Thread() {

    private lateinit var stream: InputStream

    /**
     * Signal flags
     **/
    var threadsStartComplete = false
    var fileCreateComplete = false
    var streamingComplete = false

    /**
     * Indication flags
     **/
    var startStreaming = false

    /**
     * buffering file
     **/
    private var file = File(fileName + streamId)

    private var buffer = ByteArray(1024)
    private var start = 0
    private var end = 0

    override fun run() {
        while (!threadsStartComplete) {
            try {
                sleep(1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Create buffer file
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        fileCreateComplete = true

        // Wait for start
        while (!startStreaming) {
            try {
                sleep(1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Streaming buffer
        var bufferSize = 1024
        file.outputStream().run {
            bufferedWriter().run {
                do {
                    if (end - start < 1024) {
                        bufferSize = (end - start) % 1024
                    }

                    stream.read(buffer, start, bufferSize)
                    write(buffer)

                    start+=bufferSize
                } while (start != end)

                flush()
            }
            flush()
        }

        streamingComplete = true
    }

    fun startThread(): MultiThread {
        start()
        return this
    }

    fun setStream(stream: InputStream, start: Int, end: Int) {
        this.stream = stream
        this.start = start
        this.end = end
    }

}
