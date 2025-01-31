package nl.blauw.pipplayer

import android.content.Context

object FileUtils {
    fun writeToFile(context: Context, path: String, content: String) {
    
        // 앱 전용 디렉토리에 파일 생성 및 데이터 저장
        try {
            context.openFileOutput(path, Context.MODE_PRIVATE).use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            println("File saved successfully to: ${context.filesDir}/$path")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to save file.")
        }
    }
    
    fun readFromFile(context: Context, path: String): String? {
        return try {
            context.openFileInput(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}