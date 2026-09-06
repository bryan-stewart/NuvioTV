package com.nuvio.tv.data.simkl

internal fun testSimklAuthorization(
    token: String = "token",
    profileId: String = "1",
    generation: Long = 0L
) = SimklAuthorization(
    scope = SimklAuthScope(profileId = profileId, generation = generation),
    accessToken = token
)
