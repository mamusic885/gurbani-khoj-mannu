package com.example.util

import android.content.Context
import com.example.data.SggsDatabase
import com.example.db.Bookmark

data class ResolvedBookmarkDisplay(
    val title: String,
    val subtitle: String? = null,
    val badgeText: String
)

object GurbaniUtils {

    fun convertToGurmukhiNumeral(number: Int): String {
        val gurmukhiDigits = charArrayOf('੦', '੧', '੨', '੩', '੪', '੫', '੬', '੭', '੮', '੯')
        val sb = StringBuilder()
        val str = number.toString()
        for (ch in str) {
            if (ch in '0'..'9') {
                sb.append(gurmukhiDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun convertGurmukhiNumeralToDecimal(gurmukhi: String): Int? {
        val map = mapOf('੦' to '0', '੧' to '1', '੨' to '2', '੩' to '3', '੪' to '4', '੫' to '5', '੬' to '6', '੭' to '7', '੮' to '8', '੯' to '9')
        val asciiStr = gurmukhi.map { map[it] ?: it }.joinToString("")
        return asciiStr.toIntOrNull()
    }

    fun isInternalId(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("sggs_") ||
               lower.startsWith("nitnem_") ||
               lower.endsWith(".json") ||
               lower.contains("sggs_shabad_") ||
               lower.contains("sggs_ang_") ||
               lower.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) ||
               lower.matches(Regex("^[a-zA-Z0-9_-]{20,}$"))
    }

    fun cleanUserFriendlyTitle(text: String): String {
        var result = text
        result = result.replace(Regex("sggs_shabad_\\d+"), "")
            .replace("sggs_shabad_", "")
            .replace(Regex("sggs_ang_\\d+"), "")
            .replace("sggs_ang_", "")
            .replace("sggs_", "")
            .replace("nitnem_", "")
            .replace(".json", "")
            .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "")
        result = result.replace("_", " ").trim()
        if (isInternalId(result)) return ""
        return result
    }

    fun getNitnemBaniTitle(fileName: String, baniName: String): String? {
        val fn = fileName.lowercase().trim()
        val bn = baniName.lowercase().trim()

        return when {
            fn.contains("japji") || bn.contains("japji") || bn.contains("ਜਪੁਜੀ") -> "ਜਪੁਜੀ ਸਾਹਿਬ"
            fn.contains("jaap") || bn.contains("jaap") || bn.contains("ਜਾਪੁ") -> "ਜਾਪੁ ਸਾਹਿਬ"
            fn.contains("tav_prasad") || fn.contains("savaiye") || bn.contains("ਸਵੱਯੇ") || bn.contains("tav prasad") -> "ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ"
            fn.contains("chaupai") || bn.contains("chaupai") || bn.contains("ਚੌਪਈ") -> "ਚੌਪਈ ਸਾਹਿਬ"
            fn.contains("anand") || bn.contains("anand") || bn.contains("ਅਨੰਦ") -> "ਅਨੰਦ ਸਾਹਿਬ"
            fn.contains("rehras") || fn.contains("rehraas") || bn.contains("rehras") || bn.contains("rehraas") || bn.contains("ਰਹਿਰਾਸ") || bn.contains("ਰਹਰਾਸ") -> "ਰਹਿਰਾਸ ਸਾਹਿਬ"
            fn.contains("ardas") || bn.contains("ardas") || bn.contains("ਅਰਦਾਸ") -> "ਅਰਦਾਸ"
            fn.contains("kirtan_sohila") || fn.contains("sohila") || bn.contains("sohila") || bn.contains("ਕੀਰਤਨ ਸੋਹਿਲਾ") || bn.contains("ਸੋਹਿਲਾ") -> "ਕੀਰਤਨ ਸੋਹਿਲਾ"
            fn.contains("aarti") || bn.contains("aarti") || bn.contains("ਆਰਤੀ") -> "ਆਰਤੀ"
            fn.contains("asa_di_vaar") || bn.contains("asa di vaar") || bn.contains("ਆਸਾ ਦੀ ਵਾਰ") -> "ਆਸਾ ਦੀ ਵਾਰ"
            fn.contains("sukhmani") || bn.contains("sukhmani") || bn.contains("ਸੁਖਮਨੀ") -> "ਸ੍ਰੀ ਸੁਖਮਨੀ ਸਾਹਿਬ"
            else -> null
        }
    }

    fun resolveBookmarkDisplayFast(bookmark: Bookmark): ResolvedBookmarkDisplay {
        val fileName = bookmark.fileName.lowercase().trim()
        val baniName = bookmark.baniName.trim()

        val isSggs = fileName.startsWith("sggs_") ||
                     baniName.startsWith("sggs_") ||
                     baniName.contains("ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ") ||
                     baniName.contains("SGGS") ||
                     fileName.contains("shabad_") ||
                     fileName.contains("ang_")

        if (isSggs) {
            val cleanBani = cleanUserFriendlyTitle(baniName)
            val title = if (cleanBani.isNotBlank() && !isInternalId(cleanBani)) cleanBani else bookmark.verseLine
            val subtitle = if (baniName.contains("ਅੰਗ") || baniName.contains("ਰਾਗੁ")) cleanBani else "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ"
            return ResolvedBookmarkDisplay(
                title = title,
                subtitle = subtitle,
                badgeText = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ"
            )
        }

        val nitnemTitle = getNitnemBaniTitle(fileName, baniName)
        if (nitnemTitle != null) {
            return ResolvedBookmarkDisplay(
                title = nitnemTitle,
                subtitle = null,
                badgeText = "ਨਿਤਨੇਮ ਸਾਹਿਬ"
            )
        }

        val cleanTitle = cleanUserFriendlyTitle(if (baniName.isNotBlank() && !isInternalId(baniName)) baniName else fileName)
        return ResolvedBookmarkDisplay(
            title = if (cleanTitle.isNotBlank()) cleanTitle else "ਗੁਰਬਾਣੀ ਬੁੱਕਮਾਰਕ",
            subtitle = null,
            badgeText = if (cleanTitle.isNotBlank()) cleanTitle else "ਗੁਰਬਾਣੀ"
        )
    }

    fun resolveBookmarkDisplay(bookmark: Bookmark, context: Context): ResolvedBookmarkDisplay {
        val fileName = bookmark.fileName.lowercase().trim()
        val baniName = bookmark.baniName.trim()

        val isSggs = fileName.startsWith("sggs_") ||
                     baniName.startsWith("sggs_") ||
                     baniName.contains("ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ") ||
                     baniName.contains("SGGS") ||
                     fileName.contains("shabad_") ||
                     fileName.contains("ang_")

        if (isSggs) {
            var mukhvak = ""
            var raag = ""
            var angNum: Int? = null

            val shabadId = when {
                fileName.startsWith("sggs_shabad_") -> fileName.removePrefix("sggs_shabad_")
                baniName.startsWith("sggs_shabad_") -> baniName.removePrefix("sggs_shabad_")
                else -> null
            }

            val angFromFileNameOrBani = when {
                fileName.startsWith("sggs_ang_") -> fileName.removePrefix("sggs_ang_").toIntOrNull()
                baniName.contains("ਅੰਗ") -> {
                    val digits = baniName.substringAfter("ਅੰਗ").takeWhile { it.isDigit() || it in '੦'..'੯' }
                    digits.toIntOrNull() ?: convertGurmukhiNumeralToDecimal(digits)
                }
                else -> null
            }

            try {
                val sggsDb = SggsDatabase.getInstance(context)
                if (shabadId != null) {
                    val lines = sggsDb.getShabadByShabadId(shabadId)
                    if (lines.isNotEmpty()) {
                        val firstLine = lines.first().line
                        mukhvak = convertGurbaniAkharToUnicode(firstLine.gurmukhi)
                        angNum = firstLine.source_page
                        raag = firstLine.raag
                    }
                } else if (angFromFileNameOrBani != null) {
                    val lines = sggsDb.searchByAng(angFromFileNameOrBani)
                    if (lines.isNotEmpty()) {
                        val firstLine = lines.first().line
                        mukhvak = convertGurbaniAkharToUnicode(firstLine.gurmukhi)
                        angNum = firstLine.source_page
                        if (firstLine.raag.isNotEmpty()) {
                            raag = firstLine.raag
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (mukhvak.isBlank()) {
                mukhvak = bookmark.verseLine
            }
            if (angNum == null) {
                angNum = angFromFileNameOrBani
            }

            val title = cleanUserFriendlyTitle(mukhvak)
            val subtitleParts = mutableListOf<String>()
            if (raag.isNotBlank()) {
                val cleanRaag = cleanUserFriendlyTitle(raag)
                if (cleanRaag.isNotBlank()) {
                    subtitleParts.add(if (cleanRaag.startsWith("ਰਾਗੁ") || cleanRaag.startsWith("Raag")) cleanRaag else "ਰਾਗੁ $cleanRaag")
                }
            }
            if (angNum != null && angNum > 0) {
                subtitleParts.add("ਅੰਗ ${convertToGurmukhiNumeral(angNum)}")
            }

            val subtitle = if (subtitleParts.isNotEmpty()) {
                subtitleParts.joinToString(" • ")
            } else {
                "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ"
            }

            return ResolvedBookmarkDisplay(
                title = if (title.isNotBlank()) title else bookmark.verseLine,
                subtitle = subtitle,
                badgeText = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ"
            )
        }

        val nitnemTitle = getNitnemBaniTitle(fileName, baniName)
        if (nitnemTitle != null) {
            return ResolvedBookmarkDisplay(
                title = nitnemTitle,
                subtitle = null,
                badgeText = "ਨਿਤਨੇਮ ਸਾਹਿਬ"
            )
        }

        val cleanTitle = cleanUserFriendlyTitle(if (baniName.isNotBlank() && !isInternalId(baniName)) baniName else fileName)
        return ResolvedBookmarkDisplay(
            title = if (cleanTitle.isNotBlank()) cleanTitle else "ਗੁਰਬਾਣੀ ਬੁੱਕਮਾਰਕ",
            subtitle = null,
            badgeText = if (cleanTitle.isNotBlank()) cleanTitle else "ਗੁਰਬਾਣੀ"
        )
    }
}

fun convertGurbaniAkharToUnicode(text: String): String {
    if (text.isEmpty()) return text

    if (text.any { it in '\u0A00'..'\u0A7F' }) {
        var res = text
            .replace("R", "੍ਰ")
            .replace("®", "੍ਰ")
            .replace("Ø", "")
            .replace("ˆ", "")
            .replace("ø", "")
            .replace("0", "")
        res = res.replace("ਿ੍ਰ", "੍ਰਿ").replace("ਿR", "੍ਰਿ")
        return normalizeGurmukhiCombiningMarks(res)
    }

    var processedText = text
    if (processedText.startsWith("] ")) {
        processedText = processedText.substring(2)
    } else if (processedText.startsWith("]")) {
        processedText = processedText.substring(1)
    }

    processedText = processedText.replace("Ø", "").replace("ˆ", "").replace("ø", "")

    val mappingPairs = mapOf(
        "<>" to "ੴ",
        "A`" to "ਅੰ", "Aw" to "ਆ", "ie" to "ਇ", "eI" to "ਈ", "au" to "ਉ", "aU" to "ਊ",
        "ey" to "ਏ", "AY" to "ਐ", "AO" to "ਔ", "Eu" to "ਉ",
        "mÚ" to "ਮੵ", "mÂ" to "ਮੵ"
    )

    val mappingSingles = mapOf(
        "a" to "ੳ", "A" to "ਅ", "e" to "ੲ", "E" to "ਓ", "s" to "ਸ", "S" to "ਸ਼", "h" to "ਹ",
        "k" to "ਕ", "K" to "ਖ", "g" to "ਗ", "G" to "ਘ", "c" to "ਚ", "C" to "ਛ", "j" to "ਜ", "J" to "ਝ",
        "t" to "ਟ", "T" to "ਠ", "f" to "ਡ", "F" to "ਢ", "x" to "ਣ", "q" to "ਤ", "Q" to "ਥ",
        "d" to "ਦ", "D" to "ਧ", "n" to "ਨ", "p" to "ਪ", "P" to "ਫ", "b" to "ਬ", "B" to "ਭ", "m" to "ਮ",
        "r" to "ਰ", "l" to "ਲ", "v" to "ਵ", "R" to "੍ਰ", "®" to "੍ਰ", "V" to "ੜ",
        "z" to "ਜ਼", "X" to "ਖ਼", "L" to "ਲ਼", "H" to "੍ਹ", "Z" to "ਗ਼", "&" to "ਫ਼",
        "w" to "ਾ", "W" to "ਾਂ", "I" to "ੀ", "u" to "ੁ", "U" to "ੂ", "y" to "ੇ", "Y" to "ੈ", "o" to "ੋ", "O" to "ੌ",
        "M" to "ੰ", "N" to "ੰ", "µ" to "ੰ", "`" to "ੱ", "˜" to "ੱ", "¤" to "ੱ",
        "^" to "ੵ", "~" to "ੵ", "´" to "ੵ", "Î" to "ੵ", "î" to "ੵ",
        "@" to "ੑ", "_" to "਼",
        "ç" to "੍ਛ", "œ" to "੍ਵ", "Í" to "੍ਵ", "†" to "੍ਟ", "¨" to "੍ਰ", "ü" to "ੁ", "ï" to "ਿ",
        "[" to "।", "]" to "॥",
        "0" to "੦", "1" to "੧", "2" to "੨", "3" to "੩", "4" to "੪", "5" to "੫", "6" to "੬", "7" to "੭", "8" to "੮", "9" to "੯",
        "₁" to "੧", "₂" to "੨", "₃" to "੩", "₄" to "੪", "₅" to "੫", "₆" to "੬", "₈" to "੮"
    )

    val words = processedText.split(" ")
    val resWords = mutableListOf<String>()
    for (w in words) {
        if (w.isEmpty()) {
            resWords.add("")
            continue
        }
        val sb = StringBuilder()
        var i = 0
        while (i < w.length) {
            val ch = w[i]
            if (ch == ';' || ch == '.') {
                i++
                continue
            }

            var pairFound = false
            if (i + 1 < w.length) {
                val pair = w.substring(i, i + 2)
                if (mappingPairs.containsKey(pair)) {
                    sb.append(mappingPairs[pair])
                    i += 2
                    pairFound = true
                }
            }
            if (pairFound) continue

            if (ch == 'i') {
                i++
                if (i < w.length) {
                    var nextSubStr = w[i].toString()
                    if (i + 1 < w.length && mappingPairs.containsKey(w.substring(i, i + 2))) {
                        nextSubStr = w.substring(i, i + 2)
                    }

                    var extraSubscript = ""
                    val nextEndIdx = i + nextSubStr.length
                    if (nextEndIdx < w.length && w[nextEndIdx] in listOf('R', '®', 'H', '^', '~', '@', '´', 'Î', 'î', 'ç', 'œ', 'Í', '†', '¨')) {
                        extraSubscript = mappingSingles[w[nextEndIdx].toString()] ?: ""
                    }

                    val convertedNext = mappingPairs[nextSubStr] ?: mappingSingles[nextSubStr] ?: nextSubStr
                    sb.append(convertedNext)
                    if (extraSubscript.isNotEmpty()) {
                        sb.append(extraSubscript)
                    }
                    sb.append('ਿ')
                    i += nextSubStr.length + (if (extraSubscript.isNotEmpty()) 1 else 0)
                } else {
                    sb.append('ਿ')
                }
            } else if (mappingSingles.containsKey(ch.toString())) {
                sb.append(mappingSingles[ch.toString()])
                i++
            } else {
                if (ch !in 'a'..'z' && ch !in 'A'..'Z') {
                    sb.append(ch)
                }
                i++
            }
        }
        resWords.add(sb.toString())
    }

    var result = resWords.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    result = result
        .replace("R", "੍ਰ")
        .replace("®", "੍ਰ")
        .replace("Ø", "")
        .replace("ˆ", "")
        .replace("ø", "")
        .replace("0", "")
        .replace("ਿ੍ਰ", "੍ਰਿ")
        .replace("ਅਿ", "ਇ")

    return normalizeGurmukhiCombiningMarks(result)
}

fun normalizeGurmukhiCombiningMarks(text: String): String {
    if (text.isEmpty()) return text
    val chars = text.toCharArray().toMutableList()
    val vowels = setOf('\u0A3E', '\u0A3F', '\u0A40', '\u0A41', '\u0A42', '\u0A47', '\u0A48', '\u0A4B', '\u0A4C')
    val nasals = setOf('\u0A02', '\u0A70', '\u0A71')

    var idx = 0
    var maxOps = chars.size * 5
    while (idx < chars.size - 1 && maxOps-- > 0) {
        if (chars[idx] in nasals && chars[idx + 1] in vowels) {
            val temp = chars[idx]
            chars[idx] = chars[idx + 1]
            chars[idx + 1] = temp
            idx = maxOf(0, idx - 1)
        } else {
            idx++
        }
    }
    return String(chars.toCharArray())
}
