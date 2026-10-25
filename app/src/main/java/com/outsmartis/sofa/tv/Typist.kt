package com.outsmartis.sofa.tv

/**
 * Turns an edit in the phone's text box into what the TV understands. The TV's IME channel
 * only inserts at the cursor, so an edit becomes Backspaces for what changed at the end plus
 * an insert of the new tail (that also covers autocorrect and swipe typing). Without the
 * IME channel, characters go as single key presses.
 */
data class TypingPlan(val deletes: Int, val insert: String, val keys: List<Int>, val keep: String)

object Typist {
    fun plan(old: String, new: String, imeSupported: Boolean): TypingPlan {
        val common = old.commonPrefixWith(new).length
        val tail = new.substring(common)
        return if (imeSupported) TypingPlan(old.length - common, tail, emptyList(), new.take(common))
        else TypingPlan(old.length - common, "", tail.mapNotNull(::keyFor), new.take(common))
    }

    /** Key code for characters a TV keyboard field takes as plain key presses. */
    fun keyFor(c: Char): Int? = when (c) {
        in 'a'..'z' -> 29 + (c - 'a')
        in 'A'..'Z' -> 29 + (c - 'A')
        in '0'..'9' -> Keys.NUM_0 + (c - '0')
        ' ' -> 62
        '.' -> 56
        ',' -> 55
        '-' -> 69
        '@' -> 77
        '/' -> 76
        '\n' -> Keys.ENTER
        else -> null
    }
}
