
package android.util
import java.util.Base64 as JBase64
object Base64 {
    const val NO_WRAP = 0
    @JvmStatic fun encodeToString(input: ByteArray, flags: Int): String = JBase64.getEncoder().encodeToString(input)
    @JvmStatic fun decode(str: String, flags: Int): ByteArray = JBase64.getDecoder().decode(str)
}
