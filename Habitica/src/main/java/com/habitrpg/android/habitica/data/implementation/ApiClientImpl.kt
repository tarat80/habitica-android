package com.habitrpg.android.habitica.data.implementation

import android.content.Context
import com.google.gson.JsonSyntaxException
import com.habitrpg.android.habitica.BuildConfig
import com.habitrpg.android.habitica.HabiticaBaseApplication
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.api.ApiService
import com.habitrpg.android.habitica.api.GSonFactoryCreator
import com.habitrpg.android.habitica.data.ApiClient
import com.habitrpg.android.habitica.helpers.Analytics
import com.habitrpg.android.habitica.helpers.NotificationsManager
import com.habitrpg.android.habitica.models.Achievement
import com.habitrpg.android.habitica.models.ContentResult
import com.habitrpg.android.habitica.models.LeaveChallengeBody
import com.habitrpg.android.habitica.models.Tag
import com.habitrpg.android.habitica.models.TeamPlan
import com.habitrpg.android.habitica.models.WorldState
import com.habitrpg.android.habitica.models.inventory.Equipment
import com.habitrpg.android.habitica.models.inventory.Quest
import com.habitrpg.android.habitica.models.invitations.InviteResponse
import com.habitrpg.android.habitica.models.members.Member
import com.habitrpg.android.habitica.models.responses.BulkTaskScoringData
import com.habitrpg.android.habitica.models.responses.BuyResponse
import com.habitrpg.android.habitica.models.responses.PostChatMessageResult
import com.habitrpg.android.habitica.models.responses.SkillResponse
import com.habitrpg.android.habitica.models.responses.UnlockResponse
import com.habitrpg.android.habitica.models.shops.Shop
import com.habitrpg.android.habitica.models.shops.ShopItem
import com.habitrpg.android.habitica.models.social.Challenge
import com.habitrpg.android.habitica.models.social.ChatMessage
import com.habitrpg.android.habitica.models.social.FindUsernameResult
import com.habitrpg.android.habitica.models.social.Group
import com.habitrpg.android.habitica.models.social.InboxConversation
import com.habitrpg.android.habitica.models.tasks.Task
import com.habitrpg.android.habitica.models.tasks.TaskList
import com.habitrpg.android.habitica.models.user.Items
import com.habitrpg.android.habitica.models.user.Stats
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.common.habitica.api.HostConfig
import com.habitrpg.common.habitica.api.Server
import com.habitrpg.common.habitica.models.HabitResponse
import com.habitrpg.common.habitica.models.PurchaseValidationRequest
import com.habitrpg.common.habitica.models.PurchaseValidationResult
import com.habitrpg.common.habitica.models.auth.UserAuth
import com.habitrpg.common.habitica.models.auth.UserAuthResponse
import com.habitrpg.common.habitica.models.auth.UserAuthSocial
import com.habitrpg.common.habitica.models.auth.UserAuthSocialTokens
import com.habitrpg.shared.habitica.models.responses.ErrorResponse
import com.habitrpg.shared.habitica.models.responses.FeedResponse
import com.habitrpg.shared.habitica.models.responses.Status
import com.habitrpg.shared.habitica.models.responses.TaskDirectionData
import com.habitrpg.shared.habitica.models.responses.VerifyUsernameResponse
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class ApiClientImpl(
    private val converter: Converter.Factory,
    override val hostConfig: HostConfig,
    val notificationsManager: NotificationsManager,
    private val dialogs: ConnectionProblemDialogs,
    private val okhttpWrapper: OkhttpWrapper
) : ApiClient {
    private lateinit var retrofitAdapter: Retrofit

    private lateinit var apiService: ApiService

    private fun <T> processResponse(habitResponse: HabitResponse<T>): T? {
        habitResponse.notifications?.let {
            notificationsManager.setNotifications(it)
        }
        return habitResponse.data
    }

    private suspend fun <T> process(apiCall: suspend () -> HabitResponse<T>): T? {
        try {
            return processResponse(apiCall())
        } catch (throwable: Throwable) {
            accept(throwable)
        }
        return null
    }

    override var languageCode: String? = null
    private var lastAPICallURL: String? = null

    init {
        buildRetrofit()
    }

    private fun buildRetrofit() {
        val client =okhttpWrapper.getOkhttpClient(hostConfig)
        val server = Server(this.hostConfig.address)
        retrofitAdapter =
            Retrofit.Builder()
                .client(client)
                .baseUrl(server.toString())
                .addConverterFactory(converter)
                .build()
        this.apiService = retrofitAdapter.create(ApiService::class.java)
    }

    override fun updateServerUrl(newAddress: String?) {
        newAddress?.let {
            hostConfig.address = newAddress
            buildRetrofit()
        }
    }

    override suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): UserAuthResponse? {
        val auth = UserAuth(
            username = username,
            password = password,
            confirmPassword = confirmPassword,
            email = email
        )
        return process { this.apiService.registerUser(auth) }
    }

    override suspend fun connectUser(
        username: String,
        password: String,
    ): UserAuthResponse? {
        val auth = UserAuth(
            username = username,
            password = password
        )
        return process { this.apiService.connectLocal(auth) }
    }

    override suspend fun connectSocial(
        network: String,
        userId: String,
        accessToken: String,
    ): UserAuthResponse? {
        var auth = UserAuthSocial()
        auth = auth.copy(network = network)
        val authResponse = UserAuthSocialTokens()
        authResponse.client_id = userId
        authResponse.access_token = accessToken
        auth = auth.copy(authResponse = authResponse)

        return process { this.apiService.connectSocial(auth) }
    }

    override suspend fun disconnectSocial(network: String): Void? {
        return process { this.apiService.disconnectSocial(network) }
    }

    fun accept(throwable: Throwable) {
        val throwableClass = throwable.javaClass
        if (SocketTimeoutException::class.java.isAssignableFrom(throwableClass)) {
            return
        }

        var isUserInputCall = false
        if (SocketException::class.java.isAssignableFrom(throwableClass) ||
            SSLException::class.java.isAssignableFrom(throwableClass)
        ) {
            dialogs.showConnectionProblemDialog(R.string.internal_error_api, isUserInputCall)
        } else if (throwableClass == SocketTimeoutException::class.java || UnknownHostException::class.java == throwableClass || IOException::class.java == throwableClass) {
            dialogs.showConnectionProblemDialog(
                R.string.network_error_no_network_body,
                isUserInputCall,
            )
        } else if (HttpException::class.java.isAssignableFrom(throwable.javaClass)) {
            val error = throwable as HttpException
            val res = getErrorResponse(error)
            val status = error.code()
            val requestUrl = error.response()?.raw()?.request?.url
            val path = requestUrl?.encodedPath?.removePrefix("/api/v4") ?: ""
            isUserInputCall =
                when {
                    path.startsWith("/groups") && path.endsWith("invite") -> true
                    else -> false
                }

            if (res.message != null && res.message == "RECEIPT_ALREADY_USED") {
                return
            }
            if (requestUrl?.toString()?.endsWith("/user/push-devices") == true) {
                // workaround for an error that sometimes displays that the user already has this push device
                return
            }

            if (status in 400..499) {
                if (res.displayMessage.isNotEmpty()) {
                    dialogs.showConnectionProblemDialog("", res.displayMessage, isUserInputCall)
                } else if (status == 401) {
                    dialogs.showConnectionProblemDialog(
                        R.string.authentication_error_title,
                        R.string.authentication_error_body,
                        isUserInputCall,
                    )
                }
            } else if (status in 500..599) {
                dialogs.showConnectionProblemDialog(R.string.internal_error_api, isUserInputCall)
            } else {
                dialogs.showConnectionProblemDialog(R.string.internal_error_api, isUserInputCall)
            }
        } else if (JsonSyntaxException::class.java.isAssignableFrom(throwableClass)) {
            Analytics.logError("Json Error: " + lastAPICallURL + ",  " + throwable.message)
        } else {
            Analytics.logException(throwable)
        }
    }

    override suspend fun updateMember(
        memberID: String,
        updateData: Map<String, Map<String, Boolean>>,
    ): Member {
        val temp = process { apiService.updateUser(memberID, updateData) }
        return temp ?: Member()
    }

    override fun getErrorResponse(throwable: HttpException): ErrorResponse {
        val errorResponse = throwable.response()?.errorBody() ?: return ErrorResponse()
        val errorConverter =
            converter
                .responseBodyConverter(ErrorResponse::class.java, arrayOfNulls(0), retrofitAdapter)
        return try {
            errorConverter?.convert(errorResponse) as ErrorResponse
        } catch (e: JsonSyntaxException) {
            Analytics.logError("Json Error: " + lastAPICallURL + ",  " + e.message)
            ErrorResponse()
        } catch (e: IOException) {
            Analytics.logError("Json Error: " + lastAPICallURL + ",  " + e.message)
            ErrorResponse()
        }
    }

    override suspend fun retrieveUser(withTasks: Boolean): User? {
        val user = process { apiService.getUser() }
        val tasks = getTasks()
        user?.tasks = tasks
        return user
    }

    override suspend fun retrieveInboxMessages(
        uuid: String,
        page: Int,
    ): List<ChatMessage> {
        return process { apiService.getInboxMessages(uuid, page) } ?: listOf()
    }

    override suspend fun retrieveInboxConversations(): List<InboxConversation> {
        return process { apiService.getInboxConversations() } ?: listOf()
    }

    override fun hasAuthenticationKeys(): Boolean {
        return this.hostConfig.userID.isNotEmpty() && hostConfig.apiKey.isNotEmpty()
    }



    /*
     This function is used with Observer.compose to reuse transformers across the application.
     See here for more info: http://blog.danlew.net/2015/03/02/dont-break-the-chain/
     */

    override fun updateAuthenticationCredentials(
        userID: String?,
        apiToken: String?,
    ) {
        this.hostConfig.userID = userID ?: ""
        this.hostConfig.apiKey = apiToken ?: ""
        Analytics.setUserID(hostConfig.userID)
    }

    override suspend fun getStatus(): Status {
        return process { apiService.getStatus() } ?: Status()
    }

    override suspend fun syncUserStats(): User {
        return process { apiService.syncUserStats() } ?: User()
    }

    override suspend fun reportChallenge(
        challengeid: String,
        updateData: Map<String, String>,
    ): Void? {
        return process { apiService.reportChallenge(challengeid, updateData) }
    }

    override suspend fun getContent(language: String?): ContentResult? {
        return process { apiService.getContent(language ?: this.languageCode) }
    }

    override suspend fun updateUser(updateDictionary: Map<String, Any?>): User? {
        return process { apiService.updateUser(updateDictionary) }
    }

    override suspend fun registrationLanguage(registrationLanguage: String): User {
        val temp = process { apiService.registrationLanguage(registrationLanguage) }
        return temp ?: User()
    }

    override suspend fun retrieveInAppRewards(): List<ShopItem> {
        return process { apiService.retrieveInAppRewards() } ?: listOf()
    }

    override suspend fun equipItem(
        type: String,
        itemKey: String,
    ): Items? {
        return process { apiService.equipItem(type, itemKey) }
    }

    override suspend fun buyItem(
        itemKey: String,
        purchaseQuantity: Int,
    ): BuyResponse? {
        return process { apiService.buyItem(itemKey, mapOf(Pair("quantity", purchaseQuantity))) }
    }

    override suspend fun unlinkAllTasks(
        challengeID: String?,
        keepOption: String,
    ): Void? {
        return process { apiService.unlinkAllTasks(challengeID, keepOption) }
    }

    override suspend fun blockMember(userID: String): List<String> {
        return process { apiService.blockMember(userID) } ?: listOf()
    }

    override suspend fun purchaseItem(
        type: String,
        itemKey: String,
        purchaseQuantity: Int,
    ): Void? {
        return process {
            apiService.purchaseItem(
                type,
                itemKey,
                mapOf(Pair("quantity", purchaseQuantity)),
            )
        }
    }

    private val lastSubscribeCall: Date? = null

    override suspend fun validateSubscription(request: PurchaseValidationRequest): Any? {
        return if (lastSubscribeCall == null || Date().time - lastSubscribeCall.time > 60000) {
            process { apiService.validateSubscription(request) }
        } else {
            null
        }
    }

    override suspend fun getHallMember(userId: String): Member {
        return process { apiService.getHallMember(userId) } ?: Member()
    }

    override suspend fun validateNoRenewSubscription(request: PurchaseValidationRequest): Any? {
        return process { apiService.validateNoRenewSubscription(request) }
    }

    override suspend fun cancelSubscription(): Void? {
        return processResponse(apiService.cancelSubscription())
    }

    override suspend fun purchaseHourglassItem(
        type: String,
        itemKey: String,
    ): Void? {
        return process { apiService.purchaseHourglassItem(type, itemKey) }
    }

    override suspend fun purchaseMysterySet(itemKey: String): Void? {
        return process { apiService.purchaseMysterySet(itemKey) }
    }

    override suspend fun purchaseQuest(key: String): Void? {
        return process { apiService.purchaseQuest(key) }
    }

    override suspend fun purchaseSpecialSpell(key: String): Void? {
        return process { apiService.purchaseSpecialSpell(key) }
    }

    override suspend fun sellItem(
        itemType: String,
        itemKey: String,
    ): User? {
        return process { apiService.sellItem(itemType, itemKey) }
    }

    override suspend fun feedPet(
        petKey: String,
        foodKey: String,
    ): FeedResponse? {
        val response = apiService.feedPet(petKey, foodKey)
        response.data?.message = response.message
        return process { response }
    }

    override suspend fun hatchPet(
        eggKey: String,
        hatchingPotionKey: String,
    ): Items? {
        return process { apiService.hatchPet(eggKey, hatchingPotionKey) }
    }

    override suspend fun getTasks(): TaskList {
        return process { apiService.getTasks() } ?: TaskList()
    }

    override suspend fun getTasks(type: String): TaskList {
        return process { apiService.getTasks(type) } ?: TaskList()
    }

    override suspend fun getTasks(
        type: String,
        dueDate: String,
    ): TaskList {
        return process { apiService.getTasks(type, dueDate) } ?: TaskList()
    }

//    override suspend fun reorderTags(type: String, dueDate: String): {
//        return process { apiService.getTasks(type, dueDate) }
//    }

    override suspend fun unlockPath(path: String): UnlockResponse {
        val temp = process { apiService.unlockPath(path) }
        return temp ?: UnlockResponse()
    }

    override suspend fun getTask(id: String): Task {
        val temp = process { apiService.getTask(id) }
        return temp ?: Task()
    }

    override suspend fun postTaskDirection(
        id: String,
        direction: String,
    ): TaskDirectionData? {
        return process { apiService.postTaskDirection(id, direction) }
    }

    override suspend fun bulkScoreTasks(data: List<Map<String, String>>): BulkTaskScoringData? {
        return process { apiService.bulkScoreTasks(data) }
    }

    override suspend fun postTaskNewPosition(
        id: String,
        position: Int,
    ): List<String> {
        return process { apiService.postTaskNewPosition(id, position) } ?: listOf()
    }

    override suspend fun scoreChecklistItem(
        taskId: String,
        itemId: String,
    ): Task? {
        return process { apiService.scoreChecklistItem(taskId, itemId) }
    }

    override suspend fun createTask(item: Task): Task? {
        return process { apiService.createTask(item) }
    }

    override suspend fun createGroupTask(
        groupId: String,
        item: Task,
    ): Task? {
        return process { apiService.createGroupTask(groupId, item) }
    }

    override suspend fun createTasks(tasks: List<Task>): List<Task> {
        return process { apiService.createTasks(tasks) } ?: listOf()
    }

    override suspend fun updateTask(
        id: String,
        item: Task,
    ): Task? {
        return process { apiService.updateTask(id, item) }
    }

    override suspend fun deleteTask(id: String): Void? {
        return process { apiService.deleteTask(id) }
    }

    override suspend fun createTag(tag: Tag): Tag? {
        return process { apiService.createTag(tag) }
    }

    override suspend fun updateTag(
        id: String,
        tag: Tag,
    ): Tag? {
        return process { apiService.updateTag(id, tag) }
    }

    override suspend fun deleteTag(id: String): Void? {
        return process { apiService.deleteTag(id) }
    }

    override suspend fun sleep(): Boolean? = process { apiService.sleep() }

    override suspend fun revive(): Items? = process { apiService.revive() }

    override suspend fun useSkill(
        skillName: String,
        targetType: String,
        targetId: String,
    ): SkillResponse? {
        return process { apiService.useSkill(skillName, targetType, targetId) }
    }

    override suspend fun useSkill(
        skillName: String,
        targetType: String,
    ): SkillResponse? {
        return process { apiService.useSkill(skillName, targetType) }
    }

    override suspend fun changeClass(className: String?): User? {
        return process {
            if (className != null) {
                apiService.changeClass(className)
            } else {
                apiService.changeClass()
            }
        }
    }

    override suspend fun disableClasses(): User? = process { apiService.disableClasses() }

    override suspend fun markPrivateMessagesRead() {
        apiService.markPrivateMessagesRead()
    }

    override suspend fun listGroups(type: String): List<Group> {
        return process { apiService.listGroups(type) } ?: listOf()
    }

    override suspend fun getGroup(groupId: String): Group? {
        return processResponse(apiService.getGroup(groupId))
    }

    override suspend fun createGroup(group: Group): Group? {
        return processResponse(apiService.createGroup(group))
    }

    override suspend fun updateGroup(
        id: String,
        item: Group,
    ): Group? {
        return processResponse(apiService.updateGroup(id, item))
    }

    override suspend fun removeMemberFromGroup(
        groupID: String,
        userID: String,
    ): Void? {
        return processResponse(apiService.removeMemberFromGroup(groupID, userID))
    }

    override suspend fun listGroupChat(groupId: String): List<ChatMessage> {
        return processResponse(apiService.listGroupChat(groupId)) ?: listOf()
    }

    override suspend fun joinGroup(groupId: String): Group? {
        return processResponse(apiService.joinGroup(groupId))
    }

    override suspend fun leaveGroup(
        groupId: String,
        keepChallenges: String,
    ): Void? {
        return processResponse(apiService.leaveGroup(groupId, keepChallenges))
    }

    override suspend fun postGroupChat(
        groupId: String,
        message: Map<String, String>,
    ): PostChatMessageResult? {
        return process { apiService.postGroupChat(groupId, message) }
    }

    override suspend fun deleteMessage(
        groupId: String,
        messageId: String,
    ): Void? {
        return process { apiService.deleteMessage(groupId, messageId) }
    }

    override suspend fun deleteInboxMessage(id: String): Void? {
        return process { apiService.deleteInboxMessage(id) }
    }

    override suspend fun getGroupMembers(
        groupId: String,
        includeAllPublicFields: Boolean?,
    ): List<Member> {
        return processResponse(
            apiService.getGroupMembers
                (groupId, includeAllPublicFields)
        ) ?: listOf()
    }

    override suspend fun getGroupMembers(
        groupId: String,
        includeAllPublicFields: Boolean?,
        lastId: String,
    ): List<Member> {
        return processResponse(
            apiService.getGroupMembers
                (groupId, includeAllPublicFields, lastId)
        ) ?: listOf()
    }

    override suspend fun likeMessage(
        groupId: String,
        mid: String,
    ): ChatMessage? {
        return process { apiService.likeMessage(groupId, mid) }
    }

    override suspend fun reportMember(
        mid: String,
        data: Map<String, String>,
    ): Void? {
        return process { apiService.reportMember(mid, data) }
    }

    override suspend fun flagMessage(
        groupId: String,
        mid: String,
        data: MutableMap<String, String>,
    ): Void? {
        return process { apiService.flagMessage(groupId, mid, data) }
    }

    override suspend fun flagInboxMessage(
        mid: String,
        data: MutableMap<String, String>,
    ): Void? {
        return process { apiService.flagInboxMessage(mid, data) }
    }

    override suspend fun seenMessages(groupId: String): Void? {
        return process { apiService.seenMessages(groupId) }
    }

    override suspend fun inviteToGroup(
        groupId: String,
        inviteData: Map<String, Any>,
    ): List<InviteResponse> {
        return process {
            apiService
                .inviteToGroup(groupId, inviteData)
        } ?: listOf()
    }

    override suspend fun rejectGroupInvite(groupId: String): Void? {
        return process { apiService.rejectGroupInvite(groupId) }
    }

    override suspend fun getGroupInvites(
        groupId: String,
        includeAllPublicFields: Boolean?,
    ): List<Member> {
        return process {
            apiService
                .getGroupInvites(
                    groupId,
                    includeAllPublicFields
                )
        } ?: listOf()
    }

    override suspend fun acceptQuest(groupId: String): Void? {
        return process { apiService.acceptQuest(groupId) }
    }

    override suspend fun rejectQuest(groupId: String): Void? {
        return process { apiService.rejectQuest(groupId) }
    }

    override suspend fun cancelQuest(groupId: String): Void? {
        return process { apiService.cancelQuest(groupId) }
    }

    override suspend fun forceStartQuest(
        groupId: String,
        group: Group,
    ): Quest? {
        return process { apiService.forceStartQuest(groupId, group) }
    }

    override suspend fun inviteToQuest(
        groupId: String,
        questKey: String,
    ): Quest? {
        return process { apiService.inviteToQuest(groupId, questKey) }
    }

    override suspend fun abortQuest(groupId: String): Quest? {
        return process { apiService.abortQuest(groupId) }
    }

    override suspend fun leaveQuest(groupId: String): Void? {
        return process { apiService.leaveQuest(groupId) }
    }

    private val lastPurchaseValidation: Date? = null

    override suspend fun validatePurchase(request: PurchaseValidationRequest): PurchaseValidationResult? {
        // make sure a purchase attempt doesn't happen
        return if (lastPurchaseValidation == null || Date().time - lastPurchaseValidation.time > 5000) {
            return process { apiService.validatePurchase(request) }
        } else {
            null
        }
    }

    override suspend fun changeCustomDayStart(updateObject: Map<String, Any>): Void? {
        return process { apiService.changeCustomDayStart(updateObject) }
    }

    override suspend fun markTaskNeedsWork(
        taskID: String,
        userID: String,
    ): Task? {
        return process { apiService.markTaskNeedsWork(taskID, userID) }
    }

    override suspend fun retrievePartySeekingUsers(page: Int): List<Member>{
        return process { apiService.retrievePartySeekingUsers(page) } ?: listOf()
    }

    override suspend fun getMember(memberId: String) =
        processResponse(apiService.getMember(memberId))

    override suspend fun getMemberWithUsername(username: String) =
        processResponse(apiService.getMemberWithUsername(username))

    override suspend fun getMemberAchievements(memberId: String): List<Achievement> {
        return process { apiService.getMemberAchievements(memberId, languageCode) } ?: listOf()
    }

    override suspend fun findUsernames(
        username: String,
        context: String?,
        id: String?,
    ): List<FindUsernameResult> {
        return process {
            apiService
                .findUsernames(username, context, id)
        } ?: listOf()
    }

    override suspend fun postPrivateMessage(messageDetails: Map<String, String>): PostChatMessageResult? {
        return process { apiService.postPrivateMessage(messageDetails) }
    }

    override suspend fun retrieveShopIventory(identifier: String): Shop? {
        return process { apiService.retrieveShopInventory(identifier, languageCode) }
    }

    override suspend fun addPushDevice(pushDeviceData: Map<String, String>): List<Void> {
        return process { apiService.addPushDevice(pushDeviceData) } ?: listOf()
    }

    override suspend fun deletePushDevice(regId: String): List<Void> {
        return process { apiService.deletePushDevice(regId) } ?: listOf()
    }

    override suspend fun getUserChallenges(
        page: Int,
        memberOnly: Boolean,
    ): List<Challenge> {
        val temp = if (memberOnly) {
            process { apiService.getUserChallenges(page, memberOnly) }
        } else {
            process { apiService.getUserChallenges(page) }
        }
        return temp ?: listOf()
    }

    override suspend fun getChallengeTasks(challengeId: String): TaskList? {
        return process { apiService.getChallengeTasks(challengeId) }
    }

    override suspend fun getChallenge(challengeId: String): Challenge? {
        return process { apiService.getChallenge(challengeId) }
    }

    override suspend fun joinChallenge(challengeId: String): Challenge? {
        return process { apiService.joinChallenge(challengeId) }
    }

    override suspend fun leaveChallenge(
        challengeId: String,
        body: LeaveChallengeBody,
    ): Void? {
        return process { apiService.leaveChallenge(challengeId, body) }
    }

    override suspend fun createChallenge(challenge: Challenge): Challenge? {
        return process { apiService.createChallenge(challenge) }
    }

    override suspend fun createChallengeTasks(
        challengeId: String,
        tasks: List<Task>,
    ): List<Task> {
        return process {
            apiService
                .createChallengeTasks(challengeId, tasks)
        } ?: listOf()
    }

    override suspend fun createChallengeTask(
        challengeId: String,
        task: Task,
    ): Task? {
        return process { apiService.createChallengeTask(challengeId, task) }
    }

    override suspend fun updateChallenge(challenge: Challenge): Challenge? {
        return process { apiService.updateChallenge(challenge.id ?: "", challenge) }
    }

    override suspend fun deleteChallenge(challengeId: String): Void? {
        return process { apiService.deleteChallenge(challengeId) }
    }

    override suspend fun debugAddTenGems(): Void? {
        return process { apiService.debugAddTenGems() }
    }

    override suspend fun getNews(): List<Any> {
        return process { apiService.getNews() }?: listOf()
    }

    override suspend fun readNotification(notificationId: String): List<Any>{
        return process { apiService.readNotification(notificationId) }?: listOf()
    }

    override suspend fun readNotifications(notificationIds: Map<String, List<String>>): List<Any>{
        return process { apiService.readNotifications(notificationIds) }?: listOf()
    }

    override suspend fun seeNotifications(notificationIds: Map<String, List<String>>): List<Any>{
        return process { apiService.seeNotifications(notificationIds) }?: listOf()
    }

    override suspend fun openMysteryItem(): Equipment? {
        return process { apiService.openMysteryItem() }
    }

    override suspend fun runCron(): Void? {
        return process { apiService.runCron() }
    }

    override suspend fun reroll(): User? = process { apiService.reroll() }

    override suspend fun resetAccount(password: String): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["password"] = password
        return process { apiService.resetAccount(updateObject) }
    }

    override suspend fun deleteAccount(password: String): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["password"] = password
        return process { apiService.deleteAccount(updateObject) }
    }

    override suspend fun togglePinnedItem(
        pinType: String,
        path: String,
    ): Void? {
        return process { apiService.togglePinnedItem(pinType, path) }
    }

    override suspend fun sendPasswordResetEmail(email: String): Void? {
        val data = HashMap<String, String>()
        data["email"] = email
        return process { apiService.sendPasswordResetEmail(data) }
    }

    override suspend fun updateLoginName(
        newLoginName: String,
        password: String,
    ): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["username"] = newLoginName
        updateObject["password"] = password
        return process { apiService.updateLoginName(updateObject) }
    }

    override suspend fun updateUsername(newLoginName: String): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["username"] = newLoginName
        return process { apiService.updateLoginName(updateObject) }
    }

    override suspend fun verifyUsername(username: String): VerifyUsernameResponse? {
        val updateObject = HashMap<String, String>()
        updateObject["username"] = username
        return process { this.apiService.verifyUsername(updateObject) }
    }

    override suspend fun updateEmail(
        newEmail: String,
        password: String,
    ): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["newEmail"] = newEmail
        if (password.isNotBlank()) {
            updateObject["password"] = password
        }
        return process { apiService.updateEmail(updateObject) }
    }

    override suspend fun updatePassword(
        oldPassword: String,
        newPassword: String,
        newPasswordConfirmation: String,
    ): Void? {
        val updateObject = HashMap<String, String>()
        updateObject["password"] = oldPassword
        updateObject["newPassword"] = newPassword
        updateObject["confirmPassword"] = newPasswordConfirmation
        return process { apiService.updatePassword(updateObject) }
    }

    override suspend fun allocatePoint(stat: String): Stats? {
        return process { apiService.allocatePoint(stat) }
    }

    override suspend fun transferGems(
        giftedID: String,
        amount: Int,
    ): Void? {
        return process {
            apiService.transferGems(
                mapOf(
                    Pair("toUserId", giftedID),
                    Pair("gemAmount", amount),
                ),
            )
        }
    }

    override suspend fun getTeamPlans(): List<TeamPlan> {
        return process { apiService.getTeamPlans() }?: listOf()
    }

    override suspend fun getTeamPlanTasks(teamID: String): TaskList {
        return processResponse(apiService.getTeamPlanTasks(teamID))?: TaskList()
    }

    override suspend fun assignToTask(
        taskId: String,
        ids: List<String>,
    ): Task? {
        return process { apiService.assignToTask(taskId, ids) }
    }

    override suspend fun unassignFromTask(
        taskId: String,
        userID: String,
    ): Task? {
        return process { apiService.unassignFromTask(taskId, userID) }
    }

    override suspend fun bulkAllocatePoints(
        strength: Int,
        intelligence: Int,
        constitution: Int,
        perception: Int,
    ): Stats? {
        val body = HashMap<String, Map<String, Int>>()
        val stats = HashMap<String, Int>()
        stats["str"] = strength
        stats["int"] = intelligence
        stats["con"] = constitution
        stats["per"] = perception
        body["stats"] = stats
        return process { apiService.bulkAllocatePoints(body) }
    }

    override suspend fun retrieveMarketGear(): Shop? {
        return process { apiService.retrieveMarketGear(languageCode) }
    }

    override suspend fun getWorldState(): WorldState {
        val temp = process { apiService.worldState() }
        return temp ?: WorldState()
    }

    companion object {
        fun createGsonFactory(): GsonConverterFactory {
            return GSonFactoryCreator.create()
        }
    }
}
