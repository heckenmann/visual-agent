package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.image.RgbaPngEncoder

/**
 * Renders extracted PDF page text into a toolkit-neutral PNG preview.
 */
object PdfPagePreviewRenderer {
    /**
     * Builds a readable PNG page preview from extracted text.
     *
     * @param title Source document title
     * @param page One-based page number
     * @param text Extracted page text
     * @return PNG bytes
     */
    fun render(
        title: String,
        page: Int,
        text: String,
    ): ByteArray {
        val pixels = IntArray(WIDTH * HEIGHT) { BACKGROUND }
        fillRect(pixels, 0, 0, WIDTH, 86, HEADER)
        drawText(pixels, "PDF PAGE $page", 38, 30, 4, ACCENT)
        drawText(pixels, title.take(52), 38, 108, 3, INK)
        val body =
            text
                .ifBlank { "No extractable text was found on this PDF page." }
                .replace(Regex("\\s+"), " ")
        wrap(body, maxCharsPerLine = 82)
            .take(58)
            .forEachIndexed { index, line ->
                drawText(pixels, line, 42, 170 + (index * 24), 3, INK)
            }
        return RgbaPngEncoder.encodeArgb(WIDTH, HEIGHT, pixels)
    }

    private fun wrap(
        text: String,
        maxCharsPerLine: Int,
    ): List<String> {
        val words = text.split(' ').filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isNotEmpty() && current.length + 1 + word.length > maxCharsPerLine) {
                lines += current.toString()
                current = StringBuilder()
            }
            if (word.length > maxCharsPerLine) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                word.chunked(maxCharsPerLine).forEach(lines::add)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun drawText(
        pixels: IntArray,
        text: String,
        x: Int,
        y: Int,
        scale: Int,
        color: Int,
    ) {
        var cursor = x
        text.uppercase().forEach { char ->
            if (char == '\t') {
                cursor += CHAR_ADVANCE * scale * 4
            } else {
                drawChar(pixels, char, cursor, y, scale, color)
                cursor += CHAR_ADVANCE * scale
            }
        }
    }

    private fun drawChar(
        pixels: IntArray,
        char: Char,
        x: Int,
        y: Int,
        scale: Int,
        color: Int,
    ) {
        val glyph = FONT[char] ?: FONT['?'].orEmpty()
        glyph.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, marker ->
                if (marker == '#') {
                    fillRect(
                        pixels = pixels,
                        x = x + (columnIndex * scale),
                        y = y + (rowIndex * scale),
                        width = scale,
                        height = scale,
                        color = color,
                    )
                }
            }
        }
    }

    private fun fillRect(
        pixels: IntArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        val left = x.coerceIn(0, WIDTH)
        val top = y.coerceIn(0, HEIGHT)
        val right = (x + width).coerceIn(left, WIDTH)
        val bottom = (y + height).coerceIn(top, HEIGHT)
        for (py in top until bottom) {
            for (px in left until right) {
                pixels[py * WIDTH + px] = color
            }
        }
    }

    private const val WIDTH = 1200
    private const val HEIGHT = 1600
    private const val BACKGROUND = 0xFFF8F8F2.toInt()
    private const val HEADER = 0xFF282A36.toInt()
    private const val INK = 0xFF282A36.toInt()
    private const val ACCENT = 0xFFFF79C6.toInt()
    private const val CHAR_ADVANCE = 6

    private val FONT =
        mapOf(
            ' ' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "....."),
            '?' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."),
            '.' to listOf(".....", ".....", ".....", ".....", ".....", "..#..", "..#.."),
            ',' to listOf(".....", ".....", ".....", ".....", "..#..", "..#..", ".#..."),
            ':' to listOf(".....", "..#..", "..#..", ".....", "..#..", "..#..", "....."),
            ';' to listOf(".....", "..#..", "..#..", ".....", "..#..", "..#..", ".#..."),
            '-' to listOf(".....", ".....", ".....", ".###.", ".....", ".....", "....."),
            '_' to listOf(".....", ".....", ".....", ".....", ".....", ".....", "#####"),
            '/' to listOf("....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."),
            '\\' to listOf("#....", ".#...", ".#...", "..#..", "...#.", "...#.", "....#"),
            '(' to listOf("...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#."),
            ')' to listOf(".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#..."),
            '[' to listOf(".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###."),
            ']' to listOf(".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###."),
            '+' to listOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
            '=' to listOf(".....", ".....", "#####", ".....", "#####", ".....", "....."),
            '#' to listOf(".#.#.", "#####", ".#.#.", ".#.#.", "#####", ".#.#.", "....."),
            '%' to listOf("##..#", "##.#.", "...#.", "..#..", ".#...", "#.##.", "#..##"),
            '&' to listOf(".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"),
            '\'' to listOf("..#..", "..#..", ".#...", ".....", ".....", ".....", "....."),
            '"' to listOf(".#.#.", ".#.#.", ".....", ".....", ".....", ".....", "....."),
            '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to listOf("####.", "....#", "...#.", "..##.", "....#", "#...#", ".###."),
            '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to listOf(".###.", "#...#", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "#...#", ".###."),
            'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'B' to listOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
            'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
            'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
            'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
            'F' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
            'G' to listOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."),
            'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
            'J' to listOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
            'K' to listOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
            'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
            'M' to listOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
            'N' to listOf("#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"),
            'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
            'Q' to listOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
            'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
            'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
            'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
            'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
            'W' to listOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
            'X' to listOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
            'Y' to listOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
            'Z' to listOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        )
}
