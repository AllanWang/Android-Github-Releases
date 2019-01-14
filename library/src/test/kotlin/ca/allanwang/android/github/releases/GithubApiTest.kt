package ca.allanwang.android.github.releases

import ca.allanwang.kit.retrofit.RetrofitApiConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull

class GithubApiTest {

    val api = GithubApi.create {
        addCoroutineAdapter = true

        clientBuilder = RetrofitApiConfig.loggingInterceptor()
    }

    /**
     * Do not test this often! The api is not associated with a token, so the rate limit is very low
     */
    @Ignore("Do not test often")
    @Test
    fun basicApiTest() {
        runBlocking {
            val release = api.getLatestRelease("AllanWang", "Frost-for-Facebook").await()
            assertNotNull(release)
            println(release)
        }
    }
}