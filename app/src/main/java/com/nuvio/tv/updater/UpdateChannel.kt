package com.nuvio.tv.updater

enum class UpdateChannel(val storedValue: String) {
    STABLE("stable");

    companion object {
        fun fromStoredValue(value: String?): UpdateChannel? =
            STABLE.takeIf { it.storedValue.equals(value, ignoreCase = true) }

        fun defaultForVersion(versionName: String): UpdateChannel = STABLE
    }
}
