package io.github.jls97.boveda.core.otp

/** RFC 4648 base32, the encoding of 2FA secret keys. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Encodes without padding, the way secret keys are shown to people. */
    fun encode(data: ByteArray): String {
        val output = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                output.append(ALPHABET[(buffer ushr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) output.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return output.toString()
    }

    /**
     * Decodes a key as authenticator apps do: ignoring case, spaces, hyphens and the trailing `=`
     * padding, and dropping the bits left over at the end. Null if any other character appears.
     */
    fun decode(text: CharSequence): ByteArray? {
        val output = ByteArray(text.length * 5 / 8)
        var size = 0
        var buffer = 0
        var bits = 0
        var padding = false
        for (char in text) {
            if (char == ' ' || char == '-') continue
            if (char == '=') {
                padding = true
                continue
            }
            // Nothing but padding may follow the padding.
            if (padding) return null
            val value = ALPHABET.indexOf(char.uppercaseChar())
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                output[size++] = (buffer ushr (bits - 8)).toByte()
                bits -= 8
            }
        }
        return output.copyOf(size).also { output.fill(0) }
    }
}
