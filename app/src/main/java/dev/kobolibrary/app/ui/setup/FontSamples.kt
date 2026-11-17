package dev.kobolibrary.app.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import dev.kobolibrary.app.reader.ReaderFont

/**
 * "Aa" in each of the fonts the reader brings with it, as outlines taken from the font files (the reader's
 * own are web fonts, which the page can use but the app's own screens can't): for the reading setup's font
 * tiles. 1000 units to the letters' size, from the baseline up (so above it is negative).
 */
internal class FontSample(val width: Float, val capHeight: Float, private val data: String) {
    val path: Path by lazy { PathParser().parsePathString(data).toPath() }
}

internal object FontSamples {
    fun of(font: ReaderFont): FontSample? = when (font) {
        ReaderFont.LITERATA -> literata
        ReaderFont.GARAMOND -> garamond
        ReaderFont.HYPERLEGIBLE -> hyperlegible
        ReaderFont.DYSLEXIC -> dyslexic
        else -> null
    }

    private val literata = FontSample(
        1272f, 701f,
        "M15 0V-47L62 -55Q80 -59 88 -68Q97 -78 108 -109L319 -701H424L633 -108Q643 -83 650 -73Q656 -63 677 -58L718 -49V0H452V-47L509 -54Q529 -57 534 -68" +
        "Q538 -79 529 -104L485 -231H218L175 -108Q168 -88 170 -74Q172 -59 198 -55L255 -46V0ZM237 -287H467L355 -618H351ZM925 14Q862 14 824 -23Q785 -60 785 -121" +
        "Q785 -176 812 -210Q840 -244 903 -262Q966 -279 1072 -284V-353Q1072 -394 1060 -420Q1049 -446 1026 -458Q1002 -471 964 -471Q937 -471 910 -462" +
        "Q884 -454 871 -441Q881 -426 887 -415Q893 -404 896 -396Q898 -389 898 -384Q898 -365 882 -352Q867 -339 844 -339Q817 -339 802 -352Q787 -366 787 -391" +
        "Q787 -426 814 -456Q840 -485 884 -503Q927 -521 978 -521Q1041 -521 1082 -502Q1124 -482 1144 -442Q1165 -402 1165 -341V-99Q1165 -77 1176 -66" +
        "Q1188 -55 1210 -55Q1221 -55 1232 -57Q1243 -59 1257 -63L1267 -26Q1249 -9 1223 2Q1197 14 1175 14Q1132 14 1108 -8Q1085 -31 1080 -75Q1061 -45 1038 -26" +
        "Q1015 -6 987 4Q959 14 925 14ZM963 -54Q994 -54 1018 -68Q1043 -82 1058 -106Q1072 -130 1072 -160V-233Q1008 -233 966 -222Q923 -211 902 -188" +
        "Q882 -166 882 -132Q882 -96 904 -75Q926 -54 963 -54Z",
    )

    private val garamond = FontSample(
        1091f, 650f,
        "M27 5Q14 5 6 2Q-2 -2 -2 -8Q-2 -15 6 -20Q15 -26 31 -31Q57 -39 79 -55Q101 -71 117 -108Q145 -172 168 -228Q192 -284 213 -338Q234 -392 256 -450" +
        "Q279 -509 304 -578Q309 -592 313 -606Q317 -620 318 -636Q327 -639 336 -642Q345 -645 354 -651Q363 -657 366 -669Q370 -681 373 -685Q374 -686 376 -686" +
        "Q377 -686 378 -684Q380 -680 382 -674Q383 -668 385 -662Q409 -586 435 -507Q461 -428 486 -354Q510 -281 530 -222Q551 -164 563 -128Q577 -88 593 -68" +
        "Q609 -48 626 -40Q644 -32 664 -28Q681 -25 689 -18Q697 -12 697 -5Q697 1 689 3Q681 5 668 5Q654 5 630 4Q606 2 579 0Q552 -1 529 -1Q510 -1 490 -1" +
        "Q470 -1 453 0Q436 0 424 0Q411 0 403 -3Q395 -6 395 -12Q395 -19 405 -24Q415 -29 428 -31Q461 -36 475 -48Q489 -60 489 -76Q489 -91 482 -120" +
        "Q476 -148 467 -180Q458 -213 450 -238Q448 -246 442 -248Q437 -249 423 -249H234Q230 -249 226 -246Q221 -242 217 -231Q214 -221 207 -200Q200 -178 193 -154" +
        "Q186 -130 181 -111Q176 -92 176 -87Q176 -65 193 -50Q210 -36 234 -30Q267 -21 267 -10Q267 -4 259 -2Q251 1 238 1Q225 1 200 0Q175 -1 147 -1Q114 -1 83 2" +
        "Q52 5 27 5ZM261 -282H416Q430 -282 430 -287Q430 -289 430 -292Q429 -294 428 -297L350 -541Q344 -559 341 -559Q339 -559 333 -542L242 -297Q240 -291 240 -289" +
        "Q240 -284 246 -283Q251 -282 261 -282ZM809 14Q791 14 772 0Q754 -14 742 -34Q729 -54 729 -72Q729 -104 746 -122Q763 -140 800 -155L902 -197" +
        "Q922 -205 926 -211Q929 -217 930 -237L932 -301Q933 -327 918 -346Q902 -365 873 -365Q858 -365 843 -360Q828 -355 818 -347Q812 -343 810 -335" +
        "Q808 -327 808 -318Q808 -314 808 -309Q809 -304 809 -300Q809 -295 796 -286Q784 -278 768 -272Q753 -265 743 -265Q738 -265 735 -268Q732 -270 732 -274" +
        "Q732 -290 742 -308Q753 -327 772 -343Q795 -364 822 -380Q849 -396 875 -405Q901 -414 921 -414Q957 -414 980 -388Q1004 -363 1003 -325L998 -85" +
        "Q997 -64 1008 -51Q1018 -38 1034 -38Q1059 -38 1071 -49Q1076 -54 1079 -54Q1084 -54 1087 -51Q1090 -48 1090 -43Q1090 -32 1079 -21Q1063 -5 1044 4" +
        "Q1026 14 1010 14Q964 14 937 -38H934Q901 -11 871 2Q841 14 809 14ZM856 -34Q876 -34 890 -40Q904 -47 915 -58Q920 -63 924 -72Q927 -80 928 -99L930 -148" +
        "Q931 -159 929 -164Q927 -169 921 -169Q919 -169 914 -168Q909 -167 901 -164Q848 -145 828 -124Q807 -104 807 -87Q807 -61 822 -48Q838 -34 856 -34Z",
    )

    private val hyperlegible = FontSample(
        1152f, 668f,
        "M10 0 261 -669H366L616 0H516L465 -146H161L110 0ZM190 -227H436L313 -574ZM835 12Q788 12 749 -5Q710 -22 687 -54Q664 -86 664 -131Q664 -190 706 -224" +
        "Q747 -258 822 -276Q898 -295 999 -306Q1001 -369 974 -403Q947 -437 887 -437Q842 -437 814 -416Q785 -396 777 -360L696 -384Q716 -441 768 -472" +
        "Q819 -504 886 -504Q989 -504 1036 -456Q1084 -407 1084 -303V-187Q1084 -125 1089 -80Q1094 -35 1101 0H1024L1004 -68Q982 -31 941 -10Q900 12 835 12ZM853 -55" +
        "Q892 -55 927 -69Q962 -83 984 -116Q1006 -150 1006 -208V-239Q930 -231 874 -220Q818 -208 788 -188Q757 -167 757 -133Q757 -98 782 -76Q808 -55 853 -55Z",
    )

    private val dyslexic = FontSample(
        1956f, 850f,
        "M190 1H341L384 -135H755L787 1H946L622 -849H515ZM417 -266 567 -711 720 -266ZM1513 14C1593 14 1665 -17 1703 -82V0H1803L1746 -547H1675L1677 -489" +
        "C1641 -547 1586 -569 1507 -558C1450 -550 1334 -519 1297 -312C1259 -100 1356 14 1513 14ZM1501 -138C1383 -138 1359 -197 1371 -286" +
        "C1390 -424 1472 -474 1528 -474C1595 -474 1669 -451 1669 -299C1669 -250 1662 -138 1501 -138Z",
    )

}

/**
 * [sample]'s letters in [color], in the middle of the space: every font at the same size, as the page would
 * set them, so one that's drawn smaller or wider than the others looks it here too.
 */
@Composable
internal fun FontSampleLetters(sample: FontSample, color: Color, modifier: Modifier = Modifier) {
    val path = remember(sample) { sample.path }
    Canvas(modifier) {
        // The letters' size is most of the height; a wide font is brought in to fit across.
        val k = minOf(size.height * 0.76f, size.width * 1000f / sample.width) / 1000f
        val left = (size.width - sample.width * k) / 2f
        val base = size.height * 0.5f + 350f * k
        translate(left, base) {
            scale(k, k, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}
