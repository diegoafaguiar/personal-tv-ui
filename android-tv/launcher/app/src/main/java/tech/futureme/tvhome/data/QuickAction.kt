package tech.futureme.tvhome.data

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** Botões rápidos do menu nativo (só ícones). Cada um abre uma tela de Configurações do Android TV. */
enum class QuickAction(val label: String, val action: String, val icon: ImageVector) {
    REDE("Rede", Settings.ACTION_WIFI_SETTINGS, Icons.Rounded.Wifi),
    BLUETOOTH("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, Icons.Rounded.Bluetooth),
    SOM("Som", Settings.ACTION_SOUND_SETTINGS, Icons.Rounded.VolumeUp),
    IMAGEM("Imagem", Settings.ACTION_DISPLAY_SETTINGS, Icons.Rounded.Tune),
    CONFIG("Configurações", Settings.ACTION_SETTINGS, Icons.Rounded.Settings);

    fun intent(): Intent = Intent(action)

    companion object {
        val DEFAULT = setOf(REDE, BLUETOOTH, SOM, IMAGEM, CONFIG)
    }
}
