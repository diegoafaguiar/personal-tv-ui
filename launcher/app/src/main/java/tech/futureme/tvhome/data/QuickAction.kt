package tech.futureme.tvhome.data

import android.content.Intent
import android.provider.Settings

/** Botões rápidos da home. Cada um abre uma tela de Configurações padrão do Android TV. */
enum class QuickAction(val label: String, val action: String) {
    BLUETOOTH("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    REDE("Rede", Settings.ACTION_WIFI_SETTINGS),
    SOM("Som", Settings.ACTION_SOUND_SETTINGS),
    IMAGEM("Imagem", Settings.ACTION_DISPLAY_SETTINGS),
    CONFIG("Configurações", Settings.ACTION_SETTINGS);

    fun intent(): Intent = Intent(action)

    companion object {
        val DEFAULT = setOf(BLUETOOTH, REDE, SOM, CONFIG)
    }
}
