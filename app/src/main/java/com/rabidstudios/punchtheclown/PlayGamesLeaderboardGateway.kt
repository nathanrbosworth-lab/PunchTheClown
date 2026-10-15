package com.rabidstudios.punchtheclown

import android.app.Activity
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.PageDirection
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer
import com.google.android.gms.games.leaderboard.LeaderboardVariant

/**
 * Google Play Games Services v2 authentication boundary.
 *
 * The SDK's PlayGamesInitProvider performs normal startup initialization.
 * Play Console resources are now configured for the Punch the Clown project.
 * Authentication remains isolated behind this gateway so gameplay continues
 * to work even when Play Games Services is unavailable.
 */
class PlayGamesLeaderboardGateway(
    private val activity: Activity
) : LeaderboardGateway {

    override val isConfigured: Boolean =
        activity.getString(R.string.app_id).trim() != UNCONFIGURED_PROJECT_ID

    @Volatile
    override var authState: LeaderboardAuthState =
        if (isConfigured) LeaderboardAuthState.CHECKING else LeaderboardAuthState.UNCONFIGURED
        private set

    @Volatile
    override var lastAuthDiagnostic: String? = null
        private set

    override fun refreshAuthentication(
        onResult: (LeaderboardAuthState) -> Unit
    ) {
        if (!isConfigured) {
            publish(LeaderboardAuthState.UNCONFIGURED, onResult)
            return
        }

        publish(LeaderboardAuthState.CHECKING) {}
        val client = runCatching { PlayGames.getGamesSignInClient(activity) }
            .getOrElse { error ->
                recordAuthFailure("Create sign-in client", error)
                publish(LeaderboardAuthState.ERROR, onResult)
                return
            }

        client.isAuthenticated()
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    recordAuthFailure("Authentication check", task.exception)
                }
                val state = when {
                    !task.isSuccessful -> LeaderboardAuthState.ERROR
                    task.result.isAuthenticated -> LeaderboardAuthState.AUTHENTICATED
                    else -> LeaderboardAuthState.SIGNED_OUT
                }
                if (state == LeaderboardAuthState.AUTHENTICATED) lastAuthDiagnostic = null
                publish(state, onResult)
            }
    }

    override fun requestSignIn(
        onResult: (LeaderboardAuthState) -> Unit
    ) {
        if (!isConfigured) {
            publish(LeaderboardAuthState.UNCONFIGURED, onResult)
            return
        }

        lastAuthDiagnostic = null
        publish(LeaderboardAuthState.CHECKING) {}
        val client = runCatching { PlayGames.getGamesSignInClient(activity) }
            .getOrElse { error ->
                recordAuthFailure("Create sign-in client", error)
                publish(LeaderboardAuthState.ERROR, onResult)
                return
            }

        client.signIn()
            .addOnCompleteListener { task ->
                val state = when {
                    !task.isSuccessful -> {
                        recordAuthFailure("Interactive sign-in", task.exception)
                        LeaderboardAuthState.ERROR
                    }
                    task.result.isAuthenticated -> {
                        lastAuthDiagnostic = null
                        LeaderboardAuthState.AUTHENTICATED
                    }
                    else -> {
                        recordAuthFailure("Interactive sign-in", null)
                        LeaderboardAuthState.SIGNED_OUT
                    }
                }
                publish(state, onResult)
            }
    }

    override fun submitScore(
        mode: GameMode,
        score: Long,
        onResult: (Boolean) -> Unit
    ) {
        if (!isConfigured || authState != LeaderboardAuthState.AUTHENTICATED || score <= 0L) {
            onResult(false)
            return
        }

        val leaderboardId = leaderboardId(mode)

        val client = runCatching { PlayGames.getLeaderboardsClient(activity) }
            .getOrElse {
                onResult(false)
                return
            }

        client.submitScoreImmediate(leaderboardId, score)
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    override fun loadSnapshot(
        mode: GameMode,
        forceReload: Boolean,
        onResult: (LeaderboardSnapshot?, Throwable?) -> Unit
    ) {
        if (!isConfigured || authState != LeaderboardAuthState.AUTHENTICATED) {
            onResult(null, IllegalStateException("Play Games is not authenticated"))
            return
        }

        val leaderboardId = leaderboardId(mode)
        val leaderboardsClient = runCatching { PlayGames.getLeaderboardsClient(activity) }
            .getOrElse {
                onResult(null, it)
                return
            }
        val playersClient = runCatching { PlayGames.getPlayersClient(activity) }
            .getOrElse {
                onResult(null, it)
                return
            }

        playersClient.getCurrentPlayer(forceReload)
            .addOnCompleteListener { playerTask ->
                val currentPlayerId = if (playerTask.isSuccessful) {
                    playerTask.result.get()?.playerId
                } else {
                    null
                }

                loadTop50(
                    client = leaderboardsClient,
                    leaderboardId = leaderboardId,
                    currentPlayerId = currentPlayerId,
                    forceReload = forceReload
                ) { topScores, topError ->
                    if (topError != null) {
                        onResult(null, topError)
                        return@loadTop50
                    }

                    leaderboardsClient.loadCurrentPlayerLeaderboardScore(
                        leaderboardId,
                        LeaderboardVariant.TIME_SPAN_ALL_TIME,
                        LeaderboardVariant.COLLECTION_PUBLIC
                    ).addOnCompleteListener { playerScoreTask ->
                        val playerScore = if (playerScoreTask.isSuccessful) {
                            playerScoreTask.result.get()?.let {
                                copyScore(it, currentPlayerId, forceCurrentPlayer = true)
                            }
                        } else {
                            null
                        }

                        leaderboardsClient.loadPlayerCenteredScores(
                            leaderboardId,
                            LeaderboardVariant.TIME_SPAN_ALL_TIME,
                            LeaderboardVariant.COLLECTION_PUBLIC,
                            11,
                            forceReload
                        ).addOnCompleteListener { nearbyTask ->
                            if (!nearbyTask.isSuccessful) {
                                onResult(null, nearbyTask.exception ?: IllegalStateException("Unable to load nearby scores"))
                                return@addOnCompleteListener
                            }

                            val nearbyResult = nearbyTask.result.get()
                            if (nearbyResult == null) {
                                onResult(null, IllegalStateException("No nearby leaderboard data returned"))
                                return@addOnCompleteListener
                            }

                            val nearby = copyScores(
                                nearbyResult.scores,
                                currentPlayerId
                            )
                            nearbyResult.release()

                            onResult(
                                LeaderboardSnapshot(
                                    mode = mode,
                                    top50 = topScores,
                                    playerScore = playerScore,
                                    nearbyScores = nearby
                                ),
                                null
                            )
                        }
                    }
                }
            }
    }

    private fun loadTop50(
        client: LeaderboardsClient,
        leaderboardId: String,
        currentPlayerId: String?,
        forceReload: Boolean,
        onResult: (List<RankedScore>, Throwable?) -> Unit
    ) {
        client.loadTopScores(
            leaderboardId,
            LeaderboardVariant.TIME_SPAN_ALL_TIME,
            LeaderboardVariant.COLLECTION_PUBLIC,
            25,
            forceReload
        ).addOnCompleteListener { firstTask ->
            if (!firstTask.isSuccessful) {
                onResult(emptyList(), firstTask.exception ?: IllegalStateException("Unable to load top scores"))
                return@addOnCompleteListener
            }

            val firstResult = firstTask.result.get()
            if (firstResult == null) {
                onResult(emptyList(), IllegalStateException("No leaderboard data returned"))
                return@addOnCompleteListener
            }

            val firstBuffer = firstResult.scores
            val firstPage = copyScores(firstBuffer, currentPlayerId)

            if (firstBuffer.count < 25) {
                firstResult.release()
                onResult(firstPage.take(50), null)
                return@addOnCompleteListener
            }

            client.loadMoreScores(
                firstBuffer,
                25,
                PageDirection.NEXT
            ).addOnCompleteListener { secondTask ->
                firstResult.release()

                if (!secondTask.isSuccessful) {
                    onResult(firstPage.take(50), null)
                    return@addOnCompleteListener
                }

                val secondResult = secondTask.result.get()
                if (secondResult == null) {
                    onResult(firstPage.take(50), null)
                    return@addOnCompleteListener
                }

                val secondPage = copyScores(secondResult.scores, currentPlayerId)
                secondResult.release()

                val merged = (firstPage + secondPage)
                    .distinctBy { Triple(it.rank, it.displayName, it.score) }
                    .sortedBy { it.rank }
                    .take(50)

                onResult(merged, null)
            }
        }
    }

    private fun copyScores(
        buffer: LeaderboardScoreBuffer,
        currentPlayerId: String?
    ): List<RankedScore> {
        val result = ArrayList<RankedScore>(buffer.count)
        for (index in 0 until buffer.count) {
            result += copyScore(buffer[index], currentPlayerId)
        }
        return result
    }

    private fun copyScore(
        score: LeaderboardScore,
        currentPlayerId: String?,
        forceCurrentPlayer: Boolean = false
    ): RankedScore {
        val scoreHolderId = score.scoreHolder?.playerId
        return RankedScore(
            rank = score.rank,
            displayRank = score.displayRank,
            displayName = score.scoreHolderDisplayName,
            score = score.rawScore,
            formattedScore = score.displayScore,
            isCurrentPlayer = forceCurrentPlayer ||
                (currentPlayerId != null && scoreHolderId == currentPlayerId)
        )
    }

    private fun leaderboardId(mode: GameMode): String = when (mode) {
        GameMode.PUNCH -> activity.getString(R.string.leaderboard_punch_the_clown_high_score)
        GameMode.BEANING -> activity.getString(R.string.leaderboard_beaning_the_clowns__high_score)
    }

    private fun recordAuthFailure(stage: String, error: Throwable?) {
        val statusCode = (error as? ApiException)?.statusCode
        val kind = when {
            statusCode != null -> "Google API status code $statusCode (${error?.javaClass?.simpleName ?: "ApiException"})"
            error != null -> error.javaClass.simpleName
            else -> "Result: not authenticated (no exception)"
        }
        val message = error?.message?.lineSequence()?.firstOrNull()?.take(120)
        val diagnostic = "$stage: $kind" +
            if (message.isNullOrBlank()) "" else "\n$message"
        lastAuthDiagnostic = diagnostic
        if (error != null) {
            Log.e(LOG_TAG, diagnostic, error)
        } else {
            Log.w(LOG_TAG, diagnostic)
        }
    }

    private fun publish(
        state: LeaderboardAuthState,
        callback: (LeaderboardAuthState) -> Unit
    ) {
        authState = state
        callback(state)
    }

    companion object {
        private const val LOG_TAG = "PunchPlayGames"
        const val UNCONFIGURED_PROJECT_ID = "0000000000"
    }
}
