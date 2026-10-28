package com.nuvio.tv.updater

enum class UpdateChannel(val storedValue: String) {
    STABLE("stable");

    companion object {
        fun fromStoredValue(value: String?): UpdateChannel? = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        }

        @Suppress("UNUSED_PARAMETER")
        fun defaultForVersion(versionName: String): UpdateChannel = STABLE
    }
}
