package ani.dantotsu.connections.anilist.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForumThreadsResponse(
    @SerialName("data")
    val data: Data
) : java.io.Serializable {
    @Serializable
    data class Data(
        @SerialName("Page")
        val page: ThreadPage? = null,
        @SerialName("Thread")
        val thread: ForumThread? = null
    ) : java.io.Serializable
}

@Serializable
data class ThreadCommentsResponse(
    @SerialName("data")
    val data: Data
) : java.io.Serializable {
    @Serializable
    data class Data(
        @SerialName("Page")
        val page: ThreadCommentsPage
    ) : java.io.Serializable
}

@Serializable
data class ThreadPage(
    @SerialName("pageInfo")
    val pageInfo: PageInfo? = null,
    @SerialName("threads")
    val threads: List<ForumThread> = emptyList()
) : java.io.Serializable

@Serializable
data class ThreadCommentsPage(
    @SerialName("pageInfo")
    val pageInfo: PageInfo? = null,
    @SerialName("threadComments")
    val threadComments: List<ThreadComment> = emptyList()
) : java.io.Serializable

@Serializable
data class ForumThread(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("userId")
    val userId: Int? = null,
    @SerialName("replyCount")
    var replyCount: Int? = null,
    @SerialName("viewCount")
    val viewCount: Int? = null,
    @SerialName("isLocked")
    val isLocked: Boolean? = null,
    @SerialName("isSticky")
    val isSticky: Boolean? = null,
    @SerialName("isSubscribed")
    var isSubscribed: Boolean? = null,
    @SerialName("isLiked")
    var isLiked: Boolean? = null,
    @SerialName("likeCount")
    var likeCount: Int? = null,
    @SerialName("createdAt")
    val createdAt: Int? = null,
    @SerialName("updatedAt")
    val updatedAt: Int? = null,
    @SerialName("siteUrl")
    val siteUrl: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("categories")
    val categories: List<ThreadCategory>? = null,
    @SerialName("mediaCategories")
    val mediaCategories: List<Media>? = null
) : java.io.Serializable

@Serializable
data class ThreadCategory(
    @SerialName("id")
    val id: Int,
    @SerialName("name")
    val name: String
) : java.io.Serializable

@Serializable
data class ThreadComment(
    @SerialName("id")
    val id: Int,
    @SerialName("userId")
    val userId: Int? = null,
    @SerialName("threadId")
    val threadId: Int? = null,
    @SerialName("comment")
    val comment: String? = null,
    @SerialName("isLocked")
    val isLocked: Boolean? = null,
    @SerialName("isLiked")
    var isLiked: Boolean? = null,
    @SerialName("likeCount")
    var likeCount: Int? = null,
    @SerialName("createdAt")
    val createdAt: Int? = null,
    @SerialName("updatedAt")
    val updatedAt: Int? = null,
    @SerialName("siteUrl")
    val siteUrl: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("childComments")
    val childComments: List<ThreadComment>? = null
) : java.io.Serializable
