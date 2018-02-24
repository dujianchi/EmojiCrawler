package crawler

import org.jsoup.Jsoup
import java.io.File
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Executors

val excutor = Executors.newFixedThreadPool(4)
val list = ArrayList<Emoji>()
lateinit var string: StringBuilder

fun main(args: Array<String>) {
    getAllEmojis()
    string = StringBuilder("\"")
    list.forEachIndexed { index, emoji ->
        excutor.execute { getDetailEmoji(emoji); }
    }
}

//遍历所有1.0的emoji
fun getAllEmojis() {
    val mainUrlDoc = Jsoup.connect("https://emojipedia.org/emoji-1.0/").get()
    val ulElements = mainUrlDoc?.getElementsByTag("ul")
    val emojiListElement = ulElements?.get(2)
    val emojiElements = emojiListElement?.getElementsByTag("li")
    list.clear()
    emojiElements?.forEach { e ->
        val href = e?.getElementsByTag("a")?.first()?.attr("abs:href")
        val text = e?.getElementsByClass("emoji")?.text()
        list.add(Emoji(text, href))
    }
}

//获取表情详情页里面的内容
fun getDetailEmoji(emoji: Emoji?) {
    val specificEmoji = Jsoup.connect(emoji?.href)?.get()
    val apple = specificEmoji?.select("html body div.container div.content article section.vendor-list ul li div.vendor-container.vendor-rollout-target div.vendor-image img")
            ?.first()
    val lowerCase = StringUtil.string2Unicode(emoji?.name)?.toLowerCase()
    val url = apple?.attr("src")
    val name = lowerCase?.replace('\\', '_')
    saveImage(name, url)
    saveName(lowerCase)
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
    FileUtil.save(inputStream, file)
    try {
        inputStream?.close()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    println("down $imageUrl to $file already")
}

//保存名称到文本文件
fun saveName(name: String?) {
    string.append(name).append('"').append(',').append('"')
    val file = File("emoji/emojis.txt")
    file.deleteOnExit()
    FileUtil.save(string.toString().byteInputStream(), file)
}