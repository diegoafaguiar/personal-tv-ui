package io.github.diegoafaguiar.sintonia.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** O que o celular pede e só a tela (MainActivity) sabe fazer. */
sealed class RemoteCommand {
    data class OpenApp(val packageName: String) : RemoteCommand()
    data class Search(val query: String) : RemoteCommand()
    /** Tecla do direcional (KeyEvent.KEYCODE_*), entregue à janela da home. */
    data class Key(val keyCode: Int) : RemoteCommand()
    data object Home : RemoteCommand()
    data object LiveTv : RemoteCommand()
}

/**
 * Ponte entre o servidor (threads de rede) e a Activity. Sem Activity viva, os comandos
 * esperam no buffer até ela coletar (o launcher é o HOME: o sistema o recria).
 */
object RemoteBus {
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<RemoteCommand> = _commands
    fun send(cmd: RemoteCommand) = _commands.tryEmit(cmd)

    /** A home está na frente (MainActivity entre onResume e onPause): setas e OK vão para ela. */
    @Volatile var foreground: Boolean = false
}
