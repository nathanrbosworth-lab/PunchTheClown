package com.rabidstudios.punchtheclown

import android.app.Activity
import com.google.android.gms.games.PlayGames

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

    override fun refreshAuthentication(
        onResult: (LeaderboardAuthState) -> Unit
    ) {
        if (!isConfigured) {
            publish(LeaderboardAuthState.UNCONFIGURED, onResult)
            return
        }

        publish(LeaderboardAuthState.CHECKING) {}
        val client = runCatching { PlayGames.getGamesSignInClient(activity) }
            .getOrElse {
                publish(LeaderboardAuthState.ERROR, onResult)
                return
            }

        client.isAuthenticated()
            .addOnCompleteListener { task ->
                val state = when {
                    !task.isSuccessful -> LeaderboardAuthState.ERROR
                    task.result.isAuthenticated -> LeaderboardAuthState.AUTHENTICATED
                    else -> LeaderboardAuthState.SIGNED_OUT
                }
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

        publish(LeaderboardAuthState.CHECKING) {}
        val client = runCatching { PlayGames.getGamesSignInClient(activity) }
            .getOrElse {
                publish(LeaderboardAuthState.ERROR, onResult)
                return
            }

        client.signIn()
            .addOnCompleteListener { task ->
                val state = when {
                    !task.isSuccessful -> LeaderboardAuthState.ERROR
                    task.result.isAuthenticated -> LeaderboardAuthState.AUTHENTICATED
                    else -> LeaderboardAuthState.SIGNED_OUT
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

        val leaderboardId = when (mode) {
            GameMode.PUNCH -> activity.getString(R.string.leaderboard_punch_the_clown_high_score)
            GameMode.BEANING -> activity.getString(R.string.leaderboard_beaning_the_clowns__high_score)
        }

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

    private fun publish(
        state: LeaderboardAuthState,
        callback: (LeaderboardAuthState) -> Unit
    ) {
        authState = state
        callback(state)
    }

    companion object {
        const val UNCONFIGURED_PROJECT_ID = "0000000000"
    }
}
