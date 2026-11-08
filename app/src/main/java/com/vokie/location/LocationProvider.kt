package com.vokie.location

import kotlinx.coroutines.flow.StateFlow

/** Android GPS/Fused implementation is intentionally outside this domain milestone. */
interface LocationProvider { val location: StateFlow<LocationMetadata> }
