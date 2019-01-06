package ca.allanwang.android.github.releases

import ca.allanwang.kit.retrofit.RetrofitApiConfig
import ca.allanwang.kit.retrofit.createRetrofitApi
import kotlinx.coroutines.Deferred
import retrofit2.http.GET
import retrofit2.http.Path

interface GithubApi {

    // See https://developer.github.com/v3/repos/releases/#get-the-latest-release
    @GET("repos/{owner}/{repo}/releases/latest")
    fun getLatestRelease(@Path("owner") owner: String, @Path("repo") repo: String): Deferred<GithubRelease>

    companion object {
        @JvmField
        val noAuthApi = createRetrofitApi<GithubApi>("https://api.github.com") {
            addCoroutineAdapter = true

            clientBuilder = {
                if (BuildConfig.DEBUG)
                    RetrofitApiConfig.loggingInterceptor()(it)
            }
        }
    }
}

data class GithubRelease(
    val id: Int,
    val url: String,
    val html_url: String,
    val tag_name: String,
    val target_commitish: String,
    val name: String,
    val body: String,
    val created_at: String,
    val published_at: String,
    val author: GithubAuthor,
    val assets: List<GithubAsset>
)

data class GithubAuthor(
    val id: Int,
    val url: String,
    val html_url: String,
    val login: String,
    val avatar_url: String
)

data class GithubAsset(
    val id: Int,
    val url: String,
    val name: String,
    val label: String,
    val content_type: String,
    val size: Int,
    val download_count: Int,
    val created_at: String,
    val updated_at: String,
    val uploader: GithubAuthor
)