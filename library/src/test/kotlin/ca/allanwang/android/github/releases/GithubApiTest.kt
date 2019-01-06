package ca.allanwang.android.github.releases

import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull

class GithubApiTest {

    /**
     * Do not test this often! The api is not associated with a token, so the rate limit is very low
     */
    @Ignore("Do not test often")
    @Test
    fun basicApiTest() {
        runBlocking {
            val release = GithubApi.noAuthApi.getLatestRelease("AllanWang", "KAU").await()
            assertNotNull(release)
            println(release)
        }
    }
}