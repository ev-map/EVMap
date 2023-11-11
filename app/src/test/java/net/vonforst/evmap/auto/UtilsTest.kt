package net.vonforst.evmap.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun testPaginate() {
        var (nSingle, nFirst, nOther, nLast) = listOf(6, 5, 4, 5)
        for (i in 0..30) {
            paginateTest(i, nSingle, nFirst, nOther, nLast)
        }
        nSingle = 4; nFirst = 4; nOther = 6; nLast = 6
        for (i in 0..30) {
            paginateTest(i, nSingle, nFirst, nOther, nLast)
        }
    }

    private fun paginateTest(
        i: Int,
        nSingle: Int,
        nFirst: Int,
        nOther: Int,
        nLast: Int
    ) {
        val list = (0..i).toList()
        val paginated = list.paginate(nSingle, nFirst, nOther, nLast)
        assertEquals(list, paginated.flatten())
        assert(paginated.all { it.isNotEmpty() })
        if (paginated.size == 1) {
            assert(paginated.first().size <= nSingle)
        } else {
            assert(paginated.first().size == nFirst)
            for (j in 1 until paginated.size - 1) {
                assert(paginated[j].size == nOther)
            }
            assert(paginated.last().size <= nLast)
        }
    }
}