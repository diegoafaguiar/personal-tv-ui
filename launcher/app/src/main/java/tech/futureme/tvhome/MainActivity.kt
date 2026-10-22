package tech.futureme.tvhome

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.ui.App
import tech.futureme.tvhome.ui.TvHomeTheme

/** Declarada pelo TvProvider, não pelo SDK: por isso a string e não Manifest.permission. */
const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvHomeTheme {
                App(
                    vm = vm,
                    onOpenItem = ::openItem,
                    onOpenApp = { vm.recordRecent(it.packageName, it.label, null); startSafely(it.launchIntent) },
                    onOpenRecent = ::openRecent,
                    onOpenChannel = ::openChannel,
                    onOpenInput = ::openInput,
                    onQuick = { startSafely(it.intent()) },
                    onRequestPermission = { askPermission.launch(READ_TV_LISTINGS) },
                )
            }
        }
        if (ContextCompat.checkSelfPermission(this, READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            askPermission.launch(READ_TV_LISTINGS)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()   // voltou de um app: o Watch Next pode ter mudado
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.refresh()
    }

    /** O botão Input do controle abre a TV ao vivo / menu de fontes mesmo com a home na frente. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            val first = vm.content.value.inputs.firstOrNull()
            if (first != null) { openInput(first); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openRecent(entry: tech.futureme.tvhome.data.RecentEntry) {
        vm.recordRecent(entry.packageName, entry.title, entry.intentUri)
        val uri = entry.intentUri
        if (uri.isNullOrBlank()) { openApp(entry.packageName); return }
        try { startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { openApp(entry.packageName) }
    }

    /** Sintoniza: VIEW no canal (quem atende é o app de TV ao vivo); sem quem atenda, abre o app de TV. */
    private fun openChannel(ch: tech.futureme.tvhome.data.TunerChannel) {
        val view = Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUri(ch.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (view.resolveActivity(packageManager) != null) startSafely(view)
        else { Toast.makeText(this, "Abrindo a TV ao vivo; mude para o canal ${ch.number}", Toast.LENGTH_SHORT).show(); openApp(vm.settings.value.liveTvPackage) }
    }

    private fun openItem(item: MediaItem) {
        vm.recordRecent(item.packageName, item.title, item.intentUri)
        val uri = item.intentUri
        if (uri.isNullOrBlank()) { openApp(item.packageName); return }
        try {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("TvHome", "intent_uri inválido para ${item.title}: $uri", e)
            openApp(item.packageName)
        }
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) Toast.makeText(this, "App não encontrado: $packageName", Toast.LENGTH_SHORT).show()
        else startSafely(intent)
    }

    private fun openInput(input: InputEntry) {
        val intent = vm.appsRepo.inputIntent(input, vm.settings.value.liveTvPackage)
        if (intent == null) Toast.makeText(this, "Não achei o app de TV ao vivo", Toast.LENGTH_SHORT).show()
        else startSafely(intent)
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("TvHome", "Não consegui abrir $intent", e)
            Toast.makeText(this, "Não consegui abrir", Toast.LENGTH_SHORT).show()
        }
    }
}
