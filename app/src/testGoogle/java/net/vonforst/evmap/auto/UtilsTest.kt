package net.vonforst.evmap.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun testPaginate() {
        val nSingle = 6
        val nFirst = 5
        val nOther = 4
        val nLast = 5
        for (i in 0..30) {
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
}