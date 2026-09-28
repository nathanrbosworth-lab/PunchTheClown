package com.rabidstudios.punchtheclown

import android.app.Activity
import com.google.android.gms.games.PlayGames

/**
 * Google Play Games Services v2 authentication boundary.
 *
 * The SDK's PlayGamesInitProvider performs normal startup initialization.
 * Until Play Console resources replace the placeholder project ID, this class
 * deliberately avoids creating Play Games clients so M3 gameplay stays fully
 * usable in development builds.
 */
class PlayGamesLeaderboardGateway(
    private val activity: Activity
) : LeaderboardGateway {

    override val isConfigured: Boolean =
        activity.getString(R.string.game_services_project_id).trim() != UNCONFIGURED_PROJECT_ID

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
