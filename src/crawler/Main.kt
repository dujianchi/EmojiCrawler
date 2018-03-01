package crawler

import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

// ---------------------------------------------
val BUFFER_SIZE = 1024;
//预设2中风格
val APPALE_STYLE = 0
val GOOGLE_STYLE = 1;

//风格
val STYLE = GOOGLE_STYLE
//线程数
val THREADS = 16
//需要停止的那个表情，结束标志
val STOP_ONE = "\uD83E\uDD13"
// ---------------------------------------------

val excutor = Executors.newFixedThreadPool(THREADS)
val emoji_list = ArrayList<Emoji>()
val name_list = ArrayList<String>()
lateinit var tmp_name: StringBuilder

fun main(args: Array<String>) {
    getAllEmojis()
    tmp_name = StringBuilder("\"")
    emoji_list.forEachIndexed { index, emoji ->
        excutor.execute { getDetailEmoji(emoji); }
    }
}

//遍历所有1.0的emoji
fun getAllEmojis() {
    val mainUrlDoc = Jsoup.connect("https://emojipedia.org/emoji-1.0/").get()
    val ulElements = mainUrlDoc?.getElementsByTag("ul")
    val emojiListElement = ulElements?.get(2)
    val emojiElements = emojiListElement?.getElementsByTag("li")
    emoji_list.clear()
    name_list.clear()
    run breaking@ {
        //这是设置到某个值时可以直接break的
        emojiElements?.forEach { e ->
            val href = e?.getElementsByTag("a")?.first()?.attr("abs:href")
            val text = e?.getElementsByClass("emoji")?.text()
            emoji_list.add(Emoji(text, href))
            if (STOP_ONE.equals(text)) return@breaking//这是设置到某个值时可以直接break的
        }
    }
}

//获取表情详情页里面的内容
fun getDetailEmoji(emoji: Emoji?) {
    val specificEmoji = Jsoup.connect(emoji?.href)?.get()
    val apple = specificEmoji?.select("html body div.container div.content article section.vendor-list ul li div.vendor-container.vendor-rollout-target div.vendor-image img")
            ?.get(STYLE)
    val lowerCase = StringUtil.string2Unicode(emoji?.name)?.toLowerCase()
    val url = apple?.attr("src")
    val name = lowerCase?.replace('\\', '_')
    saveImage(name, url)
    saveName(lowerCase)
    if (lowerCase != null) name_list.add(lowerCase)
    if (name_list.size == emoji_list.size) {
        val regex = generateRegex(name_list.sorted())
        val file = File("emoji/regex.txt")
        streamToFile(regex.byteInputStream(), file)
    }
}

//保存图片
fun saveImage(name: String?, imageUrl: String?) {
    val file = File("emoji/$name.png")
    if (file.exists()) {
        file.delete()
    } else if (!file.parentFile.exists()) {
        file.parentFile.mkdirs()
    }
    val connect = URL(imageUrl).openConnection() as? HttpURLConnection
    val inputStream = connect?.inputStream
    streamToFile(inputStream, file)
    if (connect?.contentLength?.toLong() == file.length()) {
        println("down $imageUrl to $file already")
    } else {
        println("file seem not down right, re-download")
        saveImage(name, imageUrl)
    }
}

//保存名称到文本文件
fun saveName(name: String?) {
    tmp_name.append(name).append('"').append(',').append('"')
    val file = File("emoji/emojis.txt")
    file.deleteOnExit()
    val byteInputStream = tmp_name.toString().byteInputStream()
    streamToFile(byteInputStream, file)
}

fun streamToFile(inputStream: InputStream?, file: File?) {
    val outputStream = FileOutputStream(file)
    inputStream?.copyTo(outputStream, BUFFER_SIZE)
    try {
        inputStream?.close()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    try {
        outputStream.close()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

fun generateRegex(emojis: List<String>): String {
    val map = linkedMapOf<String, Int>()
    emojis.forEach {
        var c = 0
        val hex = it.split("\\u")
        for (index in 1 until hex.size){
            c += Integer.parseInt(hex[index], 16)
        }
        map.put(it, c)
    }
    val regex = StringBuilder().append('[')
    var index = 0
    var lastCode = 0
    map.forEach { name, code ->
        val append = name
        if (index++ == 0) {
            regex.append(append)
        } else {
            if (code - lastCode == 1) {
                val lastIndexOf_ = regex.lastIndexOf('-')
                val lastIndexOf1 = regex.lastIndexOf('|')
                if (lastIndexOf_ == regex.length - 1) {
                    regex.append(append)
                } else if (lastIndexOf_ == -1 || lastIndexOf1 >= lastIndexOf_) {
                    regex.append('-').append(append)
                } else {
                    regex.replace(lastIndexOf_ + 1, regex.length, "$append")
                }
            } else {
                regex.append('|').append(append)
            }
        }
        lastCode = code
    }
    regex.append(']')
    println(regex)
    return regex.toString()
}