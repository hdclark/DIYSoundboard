package com.hdclark.diysoundboard

import java.util.UUID

data class SoundButton(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val audioFileName: String = ""
) {
    val isEmpty: Boolean get() = label.isEmpty() && audioFileName.isEmpty()
}
